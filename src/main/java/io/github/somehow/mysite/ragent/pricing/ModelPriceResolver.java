package io.github.somehow.mysite.ragent.pricing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.somehow.mysite.ragent.dao.entity.LlmHolidayDO;
import io.github.somehow.mysite.ragent.dao.entity.LlmModelPriceDO;
import io.github.somehow.mysite.ragent.dao.mapper.LlmHolidayMapper;
import io.github.somehow.mysite.ragent.dao.mapper.LlmModelPriceMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 模型计价解析：provider+model → 生效价卡 → 阶梯 → 峰谷 → 缓存价 → ResolvedPrice。
 * 全表常驻内存快照（价卡极少），TTL 30s + 管理端写后 evict。
 * 时段一律按 UTC 判定（peak_windows 里的 HH:mm 是 UTC），节假日按 Asia/Shanghai 日期。
 */
@Slf4j
@Component
public class ModelPriceResolver {

    private static final long TTL_MS = 30_000;
    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final BigDecimal ONE = BigDecimal.ONE;

    private final LlmModelPriceMapper priceMapper;
    private final LlmHolidayMapper holidayMapper;
    private final ObjectMapper objectMapper;
    private final AtomicReference<Snapshot> snapshot = new AtomicReference<>();

    public ModelPriceResolver(LlmModelPriceMapper priceMapper, LlmHolidayMapper holidayMapper,
                              ObjectMapper objectMapper) {
        this.priceMapper = priceMapper;
        this.holidayMapper = holidayMapper;
        this.objectMapper = objectMapper;
    }

    public void evict() {
        snapshot.set(null);
    }

    /** 解析某时刻某模型的有效单价；无生效价卡返回 null。 */
    public ResolvedPrice resolve(String provider, String model, LocalDateTime at, int promptTokens) {
        if (provider == null || model == null || at == null) {
            return null;
        }
        Snapshot snap = snapshot();
        List<LlmModelPriceDO> versions = snap.cards().get(key(provider, model));
        if (versions == null) {
            return null;
        }
        LlmModelPriceDO card = pickVersion(versions, at);
        if (card == null) {
            return null;
        }
        return compute(card, snap, at, promptTokens);
    }

    // ── 快照 ──

    private record Snapshot(Map<String, List<LlmModelPriceDO>> cards, Set<LocalDate> holidays, long loadedAt) {
    }

    private Snapshot snapshot() {
        Snapshot s = snapshot.get();
        if (s == null || System.currentTimeMillis() - s.loadedAt() > TTL_MS) {
            synchronized (this) {
                s = snapshot.get();
                if (s == null || System.currentTimeMillis() - s.loadedAt() > TTL_MS) {
                    s = load();
                    snapshot.set(s);
                }
            }
        }
        return s;
    }

    private Snapshot load() {
        Map<String, List<LlmModelPriceDO>> cards = new HashMap<>();
        try {
            for (LlmModelPriceDO row : priceMapper.selectList(null)) {
                cards.computeIfAbsent(key(row.getProvider(), row.getModel()), k -> new ArrayList<>()).add(row);
            }
            for (List<LlmModelPriceDO> list : cards.values()) {
                list.sort(Comparator.comparing(LlmModelPriceDO::getEffectiveFrom).reversed());
            }
        } catch (Exception e) {
            log.warn("[price] load cards failed, keep stale snapshot: {}", e.getMessage());
            Snapshot stale = snapshot.get();
            if (stale != null) {
                return new Snapshot(stale.cards(), stale.holidays(), System.currentTimeMillis());
            }
        }
        Set<LocalDate> holidays = new HashSet<>();
        try {
            for (LlmHolidayDO h : holidayMapper.selectList(null)) {
                holidays.add(h.getHolidayDate());
            }
        } catch (Exception e) {
            log.warn("[price] load holidays failed: {}", e.getMessage());
        }
        return new Snapshot(cards, holidays, System.currentTimeMillis());
    }

    static String key(String provider, String model) {
        return provider + "|" + model;
    }

    /** versions 按 effective_from 降序，取第一条覆盖 at 的。 */
    static LlmModelPriceDO pickVersion(List<LlmModelPriceDO> versions, LocalDateTime at) {
        for (LlmModelPriceDO v : versions) {
            if (v.getEffectiveFrom() != null && v.getEffectiveFrom().isAfter(at)) {
                continue;
            }
            if (v.getEffectiveTo() != null && !at.isBefore(v.getEffectiveTo())) {
                continue;
            }
            return v;
        }
        return null;
    }

    // ── 计价 ──

    private ResolvedPrice compute(LlmModelPriceDO card, Snapshot snap, LocalDateTime at, int promptTokens) {
        JsonNode tiers = parseJson(card.getTiers());
        JsonNode tier = selectTier(tiers, promptTokens);

        BigDecimal input = field(tier, card.getInputPrice(), "input_price");
        BigDecimal output = field(tier, card.getOutputPrice(), "output_price");
        BigDecimal cacheRead = cachePrice(tier, card, "cache_read_price", "cache_read_factor", input);
        BigDecimal cacheWrite = cachePrice(tier, card, "cache_write_price", "cache_write_factor", input);

        boolean peak = isPeak(card, snap, at);
        BigDecimal factor = peak ? ONE : nvl(card.getOffPeakFactor(), ONE);
        if (factor.compareTo(ONE) != 0) {
            input = input.multiply(factor);
            output = output.multiply(factor);
            cacheRead = cacheRead.multiply(factor);
            cacheWrite = cacheWrite.multiply(factor);
        }
        return ResolvedPrice.of(card, peak, input, cacheRead, cacheWrite, output);
    }

    /** tier 字段优先，缺省回退卡级字段。 */
    private static BigDecimal field(JsonNode tier, BigDecimal cardValue, String jsonKey) {
        if (tier != null && tier.hasNonNull(jsonKey)) {
            return tier.get(jsonKey).decimalValue();
        }
        return nvl(cardValue, BigDecimal.ZERO);
    }

    /**
     * 缓存价回退：
     * 命中阶梯档  tier 显式价 > tier factor×档输入价 > 卡级 factor×档输入价 > 卡级显式价；
     * 未命中阶梯  卡级显式价 > 卡级 factor×输入价。
     * 卡级显式价是首档扁平价，套用到高阶梯档会系统性低估，故档命中时排在费率之后；
     * factor 是费率跨档有效。双缺省：cache_read 视为与输入同价，cache_write 免费。
     */
    private BigDecimal cachePrice(JsonNode tier, LlmModelPriceDO card,
                                  String priceKey, String factorKey, BigDecimal effectiveInput) {
        boolean isRead = priceKey.equals("cache_read_price");
        BigDecimal cardExplicit = isRead ? card.getCacheReadPrice() : card.getCacheWritePrice();
        BigDecimal cardFactor = isRead ? card.getCacheReadFactor() : card.getCacheWriteFactor();
        if (tier != null) {
            if (tier.hasNonNull(priceKey)) {
                return tier.get(priceKey).decimalValue();
            }
            if (tier.hasNonNull(factorKey)) {
                return tier.get(factorKey).decimalValue().multiply(effectiveInput);
            }
            if (cardFactor != null) {
                return cardFactor.multiply(effectiveInput);
            }
            if (cardExplicit != null) {
                return cardExplicit;
            }
        } else {
            if (cardExplicit != null) {
                return cardExplicit;
            }
            if (cardFactor != null) {
                return cardFactor.multiply(effectiveInput);
            }
        }
        return isRead ? effectiveInput : BigDecimal.ZERO;
    }

    /** tiers JSON：按 max_input_tokens 升序，选第一个 promptTokens <= max 的档；超出全部档位用卡级价。 */
    private JsonNode selectTier(JsonNode tiers, int promptTokens) {
        if (tiers == null || !tiers.isArray() || tiers.isEmpty()) {
            return null;
        }
        List<JsonNode> sorted = new ArrayList<>();
        tiers.forEach(sorted::add);
        sorted.sort(Comparator.comparingLong(t -> t.path("max_input_tokens").asLong(Long.MAX_VALUE)));
        for (JsonNode t : sorted) {
            long max = t.path("max_input_tokens").asLong(Long.MAX_VALUE);
            if (promptTokens <= max) {
                return t;
            }
        }
        return null;
    }

    // ── 峰谷判定 ──

    boolean isPeak(LlmModelPriceDO card, Snapshot snap, LocalDateTime at) {
        JsonNode windows = parseJson(card.getPeakWindows());
        if (windows == null || !windows.isArray() || windows.isEmpty()) {
            return true; // 无峰谷配置 = 全天标准价
        }
        ZonedDateTime utc = at.atZone(ZoneId.systemDefault()).withZoneSameInstant(UTC);
        int dow = utc.getDayOfWeek().getValue(); // ISO 1=Mon..7=Sun
        LocalTime time = utc.toLocalTime();
        boolean holiday = Boolean.TRUE.equals(card.getExcludeHolidays())
                && snap.holidays().contains(utc.withZoneSameInstant(SHANGHAI).toLocalDate());
        if (holiday) {
            return false; // 法定节假日全天谷时
        }
        for (JsonNode w : windows) {
            if (!w.isObject()) {
                continue;
            }
            LocalTime start = parseHm(w.path("start").asText(null));
            LocalTime end = parseHm(w.path("end").asText(null));
            if (start == null || end == null || !matchesDay(w.path("days"), dow)) {
                continue;
            }
            boolean inWindow = start.isBefore(end)
                    ? !time.isBefore(start) && time.isBefore(end)
                    : !time.isBefore(start) || time.isBefore(end); // 跨零点窗口
            if (inWindow) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesDay(JsonNode days, int dow) {
        if (days == null || !days.isArray() || days.isEmpty()) {
            return true; // 未指定 = 每天
        }
        for (JsonNode d : days) {
            if (d.asInt(-1) == dow) {
                return true;
            }
        }
        return false;
    }

    private static LocalTime parseHm(String hm) {
        if (hm == null || hm.length() < 4) {
            return null;
        }
        try {
            return LocalTime.parse(hm.length() == 5 ? hm : hm + ":00");
        } catch (Exception e) {
            return null;
        }
    }

    private JsonNode parseJson(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(text);
        } catch (Exception e) {
            return null;
        }
    }

    private static BigDecimal nvl(BigDecimal v, BigDecimal def) {
        return v != null ? v : def;
    }
}
