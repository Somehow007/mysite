package io.github.somehow.mysite.ragent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.somehow.mysite.commons.framework.exception.ClientException;
import io.github.somehow.mysite.ragent.dao.entity.LlmHolidayDO;
import io.github.somehow.mysite.ragent.dao.entity.LlmModelPriceDO;
import io.github.somehow.mysite.ragent.dao.mapper.LlmHolidayMapper;
import io.github.somehow.mysite.ragent.dao.mapper.LlmModelPriceMapper;
import io.github.somehow.mysite.ragent.dto.LlmHolidayDTO;
import io.github.somehow.mysite.ragent.dto.LlmHolidaysUpdateRequest;
import io.github.somehow.mysite.ragent.dto.LlmModelPriceDTO;
import io.github.somehow.mysite.ragent.dto.LlmModelPriceUpsertRequest;
import io.github.somehow.mysite.ragent.pricing.ModelPriceResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 价卡与节假日管理。价卡版本化：修订=闭合旧版 effective_to + 插新版，历史不覆盖。
 */
@Service
@RequiredArgsConstructor
public class AdminPricingService {

    private final LlmModelPriceMapper priceMapper;
    private final LlmHolidayMapper holidayMapper;
    private final ModelPriceResolver resolver;
    private final ObjectMapper objectMapper;

    public List<LlmModelPriceDTO> listPrices() {
        List<LlmModelPriceDO> rows = priceMapper.selectList(new LambdaQueryWrapper<LlmModelPriceDO>()
                .orderByAsc(LlmModelPriceDO::getProvider)
                .orderByAsc(LlmModelPriceDO::getModel)
                .orderByDesc(LlmModelPriceDO::getEffectiveFrom));
        LocalDateTime now = LocalDateTime.now();
        List<LlmModelPriceDTO> out = new ArrayList<>(rows.size());
        for (LlmModelPriceDO row : rows) {
            LlmModelPriceDTO dto = new LlmModelPriceDTO();
            BeanUtils.copyProperties(row, dto);
            dto.setCurrent(isCurrent(row, now));
            out.add(dto);
        }
        return out;
    }

    public List<LlmModelPriceDTO> createPrice(LlmModelPriceUpsertRequest req) {
        LlmModelPriceDO row = newRow(req);
        LocalDateTime effectiveFrom = req.getEffectiveFrom() != null ? req.getEffectiveFrom() : LocalDateTime.now();
        row.setEffectiveFrom(effectiveFrom);
        validateJson(row.getPeakWindows(), "peakWindows");
        validateJson(row.getTiers(), "tiers");
        assertNoDuplicateVersion(row.getProvider(), row.getModel(), effectiveFrom);
        assertNoOverlappingVersion(row.getProvider(), row.getModel(), effectiveFrom);
        priceMapper.insert(row);
        resolver.evict();
        return listPrices();
    }

    /**
     * 版本化修订：把该卡（及其后续未闭合版本）的 effective_to 收到新生效点，
     * 再按请求字段插入新版。历史版本原样保留。
     */
    @Transactional
    public List<LlmModelPriceDTO> revisePrice(Long id, LlmModelPriceUpsertRequest req) {
        LlmModelPriceDO old = priceMapper.selectById(id);
        if (old == null) {
            throw new ClientException("价卡不存在: " + id);
        }
        // 修订不允许换主体：provider/model 缺省沿用旧卡，显式给不同值则报错
        if (StringUtils.hasText(req.getProvider()) && !req.getProvider().equals(old.getProvider())) {
            throw new ClientException("修订不能更换 provider（" + old.getProvider() + " → " + req.getProvider() + "），请新建价卡");
        }
        if (StringUtils.hasText(req.getModel()) && !req.getModel().equals(old.getModel())) {
            throw new ClientException("修订不能更换 model（" + old.getModel() + " → " + req.getModel() + "），请新建价卡");
        }
        if (!StringUtils.hasText(req.getProvider())) {
            req.setProvider(old.getProvider());
        }
        if (!StringUtils.hasText(req.getModel())) {
            req.setModel(old.getModel());
        }
        LocalDateTime newFrom = req.getEffectiveFrom() != null ? req.getEffectiveFrom() : LocalDateTime.now();
        if (old.getEffectiveFrom() != null && !newFrom.isAfter(old.getEffectiveFrom())) {
            throw new ClientException("新版本生效时间必须晚于旧版本生效时间（" + old.getEffectiveFrom() + "）");
        }
        LlmModelPriceDO row = newRow(req);
        validateJson(row.getPeakWindows(), "peakWindows");
        validateJson(row.getTiers(), "tiers");

        // 链尾在闭合前捕获：新版继承原 effective_to（通常 null=当前有效）
        LocalDateTime chainTail = old.getEffectiveTo();
        if (chainTail != null && !newFrom.isBefore(chainTail)) {
            throw new ClientException("新版本生效时间必须早于版本链结束时间（" + chainTail + "），否则新版为空窗口");
        }
        assertNoDuplicateVersion(row.getProvider(), row.getModel(), newFrom);
        // 闭合旧版：effective_to 取 min(原 effective_to, newFrom)；原本已闭合的保持不变
        if (chainTail == null || newFrom.isBefore(chainTail)) {
            old.setEffectiveTo(newFrom);
            priceMapper.updateById(old);
        }
        row.setEffectiveFrom(newFrom);
        row.setEffectiveTo(chainTail);
        row.setSource("MANUAL");
        priceMapper.insert(row);
        resolver.evict();
        return listPrices();
    }

    /** 停用当前版本：effective_to=now，历史保留。 */
    public List<LlmModelPriceDTO> retirePrice(Long id) {
        LlmModelPriceDO row = priceMapper.selectById(id);
        if (row == null) {
            throw new ClientException("价卡不存在: " + id);
        }
        if (row.getEffectiveTo() == null) {
            LocalDateTime now = LocalDateTime.now();
            // 未来生效版本直接闭合为空窗口（effective_to=effective_from），避免 from>to 死行
            row.setEffectiveTo(row.getEffectiveFrom() != null && row.getEffectiveFrom().isAfter(now)
                    ? row.getEffectiveFrom() : now);
            priceMapper.updateById(row);
            resolver.evict();
        }
        return listPrices();
    }

    public List<LlmHolidayDTO> listHolidays() {
        return holidayMapper.selectList(new LambdaQueryWrapper<LlmHolidayDO>()
                        .orderByAsc(LlmHolidayDO::getHolidayDate))
                .stream().map(this::toHolidayDto).toList();
    }

    @Transactional
    public List<LlmHolidayDTO> replaceHolidays(LlmHolidaysUpdateRequest req) {
        List<LlmHolidayDTO> items = req.getItems() != null ? req.getItems() : List.of();
        holidayMapper.delete(null);
        Set<LocalDate> seen = new HashSet<>();
        for (LlmHolidayDTO item : items) {
            if (item.getHolidayDate() == null) {
                throw new ClientException("节假日日期不能为空");
            }
            if (!seen.add(item.getHolidayDate())) {
                throw new ClientException("节假日日期重复: " + item.getHolidayDate());
            }
            LlmHolidayDO row = new LlmHolidayDO();
            row.setHolidayDate(item.getHolidayDate());
            row.setName(item.getName());
            holidayMapper.insert(row);
        }
        resolver.evict();
        return listHolidays();
    }

    // ── helpers ──

    private LlmModelPriceDO newRow(LlmModelPriceUpsertRequest req) {
        if (!StringUtils.hasText(req.getProvider()) || !StringUtils.hasText(req.getModel())) {
            throw new ClientException("provider 和 model 必填");
        }
        if (!StringUtils.hasText(req.getCurrency())) {
            throw new ClientException("currency 必填（如 CNY / USD）");
        }
        if (req.getInputPrice() == null || req.getOutputPrice() == null) {
            throw new ClientException("inputPrice / outputPrice 必填（每 1M token）");
        }
        LlmModelPriceDO row = new LlmModelPriceDO();
        BeanUtils.copyProperties(req, row);
        if (!StringUtils.hasText(row.getUnit())) {
            row.setUnit("PER_MILLION_TOKENS");
        }
        LocalDateTime now = LocalDateTime.now();
        row.setCreateTime(now);
        row.setUpdateTime(now);
        return row;
    }

    private void validateJson(String json, String field) {
        if (!StringUtils.hasText(json)) {
            return;
        }
        try {
            if (!objectMapper.readTree(json).isArray()) {
                throw new ClientException(field + " 必须是 JSON 数组");
            }
        } catch (ClientException e) {
            throw e;
        } catch (Exception e) {
            throw new ClientException(field + " 不是合法 JSON: " + e.getMessage());
        }
    }

    /** 同 provider+model+effective_from 已存在版本时报业务错，避免唯一键冲突落 500。 */
    private void assertNoDuplicateVersion(String provider, String model, LocalDateTime effectiveFrom) {
        Long dup = priceMapper.selectCount(new LambdaQueryWrapper<LlmModelPriceDO>()
                .eq(LlmModelPriceDO::getProvider, provider)
                .eq(LlmModelPriceDO::getModel, model)
                .eq(LlmModelPriceDO::getEffectiveFrom, effectiveFrom));
        if (dup != null && dup > 0) {
            throw new ClientException("该模型在 " + effectiveFrom + " 已存在价卡版本");
        }
    }

    /** 新建版本窗口为 [effectiveFrom, ∞)，与同模型任何仍在生效的版本重叠时报错，引导走「修订」。 */
    private void assertNoOverlappingVersion(String provider, String model, LocalDateTime effectiveFrom) {
        Long overlap = priceMapper.selectCount(new LambdaQueryWrapper<LlmModelPriceDO>()
                .eq(LlmModelPriceDO::getProvider, provider)
                .eq(LlmModelPriceDO::getModel, model)
                .and(w -> w.isNull(LlmModelPriceDO::getEffectiveTo)
                        .or().gt(LlmModelPriceDO::getEffectiveTo, effectiveFrom)
                        .apply("effective_from < effective_to")));
        if (overlap != null && overlap > 0) {
            throw new ClientException("该模型在所选生效时间后仍有生效版本，请改用「修订」生成新版本");
        }
    }

    private static boolean isCurrent(LlmModelPriceDO row, LocalDateTime now) {
        if (row.getEffectiveFrom() != null && row.getEffectiveFrom().isAfter(now)) {
            return false;
        }
        return row.getEffectiveTo() == null || row.getEffectiveTo().isAfter(now);
    }

    private LlmHolidayDTO toHolidayDto(LlmHolidayDO row) {
        LlmHolidayDTO dto = new LlmHolidayDTO();
        dto.setHolidayDate(row.getHolidayDate());
        dto.setName(row.getName());
        return dto;
    }
}
