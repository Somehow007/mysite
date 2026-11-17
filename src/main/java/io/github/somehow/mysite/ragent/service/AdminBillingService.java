package io.github.somehow.mysite.ragent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.somehow.mysite.ragent.billing.BillingProbe;
import io.github.somehow.mysite.ragent.config.RagProperties;
import io.github.somehow.mysite.ragent.dao.entity.LlmBalanceSnapshotDO;
import io.github.somehow.mysite.ragent.dao.mapper.LlmBalanceSnapshotMapper;
import io.github.somehow.mysite.ragent.dto.LlmBillingDTO;
import io.github.somehow.mysite.ragent.llm.LlmRuntimeConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 官方账单对账：对每个已启用且有探针的 provider 拉官方余额/配额，
 * 每次结果落 t_llm_balance_snapshot；balance 类按币种算与上一快照的差值。
 */
@Slf4j
@Service
public class AdminBillingService {

    private final RagProperties ragProperties;
    private final LlmRuntimeConfigService runtimeConfig;
    private final List<BillingProbe> probes;
    private final LlmBalanceSnapshotMapper snapshotMapper;
    private final ObjectMapper om;

    public AdminBillingService(RagProperties ragProperties,
                               LlmRuntimeConfigService runtimeConfig,
                               List<BillingProbe> probes,
                               LlmBalanceSnapshotMapper snapshotMapper,
                               ObjectMapper om) {
        this.ragProperties = ragProperties;
        this.runtimeConfig = runtimeConfig;
        this.probes = probes;
        this.snapshotMapper = snapshotMapper;
        this.om = om;
    }

    public LlmBillingDTO query() {
        Map<String, BillingProbe> byProvider = new LinkedHashMap<>();
        for (BillingProbe p : probes) {
            byProvider.put(p.provider(), p);
        }
        LlmBillingDTO dto = new LlmBillingDTO();
        dto.setProbedAt(LocalDateTime.now());
        List<LlmBillingDTO.ProviderBilling> out = new ArrayList<>();
        RagProperties.LLMProperties llm = ragProperties.getLlm();
        if (llm != null && llm.getProviders() != null) {
            for (Map.Entry<String, RagProperties.Provider> e : llm.getProviders().entrySet()) {
                RagProperties.Provider p = e.getValue();
                if (p == null || !p.isEnabled()) {
                    continue;
                }
                BillingProbe probe = byProvider.get(e.getKey());
                if (probe == null) {
                    continue; // 无官方账单接口的 provider（ollama 等）直接跳过
                }
                // 用运行时生效配置（含后台覆盖 + 解密后的 apiKey）
                RagProperties.Provider live;
                try {
                    live = runtimeConfig.requireProvider(e.getKey());
                } catch (Exception ex) {
                    live = p;
                }
                out.add(probeOne(probe, e.getKey(), live));
            }
        }
        dto.setProviders(out);
        return dto;
    }

    private LlmBillingDTO.ProviderBilling probeOne(BillingProbe probe, String name,
                                                   RagProperties.Provider live) {
        LlmBillingDTO.ProviderBilling pb = new LlmBillingDTO.ProviderBilling();
        pb.setProvider(name);
        BillingProbe.BillingProbeResult r;
        try {
            r = probe.probe(live);
        } catch (Exception e) {
            log.warn("billing probe {} threw: {}", name, e.getMessage());
            r = BillingProbe.BillingProbeResult.fail(0, "probe error: " + e.getMessage());
        }
        pb.setOk(r.ok());
        pb.setLatencyMs(r.latencyMs());
        pb.setMessage(r.message());
        pb.setData(r.data());
        persistSnapshot(name, r);
        if (r.ok() && r.data() != null && "balance".equals(r.data().get("kind"))) {
            pb.setDelta(computeDelta(name, r.data()));
        }
        return pb;
    }

    private void persistSnapshot(String provider, BillingProbe.BillingProbeResult r) {
        try {
            LlmBalanceSnapshotDO row = new LlmBalanceSnapshotDO();
            row.setProvider(provider);
            row.setOk(r.ok());
            row.setLatencyMs((int) r.latencyMs());
            row.setPayload(r.data() != null ? om.writeValueAsString(r.data()) : r.message());
            snapshotMapper.insert(row);
        } catch (Exception e) {
            log.warn("persist billing snapshot failed for {}: {}", provider, e.getMessage());
        }
    }

    /** 余额差值：最新快照 vs 上一份成功快照，按币种算 total 下降额（赠金先扣，Δtotal=实花）。 */
    private LlmBillingDTO.Delta computeDelta(String provider, Map<String, Object> current) {
        try {
            List<LlmBalanceSnapshotDO> history = snapshotMapper.selectList(
                    new LambdaQueryWrapper<LlmBalanceSnapshotDO>()
                            .eq(LlmBalanceSnapshotDO::getProvider, provider)
                            .eq(LlmBalanceSnapshotDO::getOk, true)
                            .orderByDesc(LlmBalanceSnapshotDO::getCreateTime)
                            .orderByDesc(LlmBalanceSnapshotDO::getId)
                            .last("LIMIT 2"));
            // history[0] 是刚插入的本次，history[1] 是上一份
            if (history == null || history.size() < 2) {
                return null;
            }
            JsonNode prev = om.readTree(history.get(1).getPayload());
            JsonNode curr = om.valueToTree(current);
            Map<String, String> prevTotals = totalsByCurrency(prev);
            Map<String, String> currTotals = totalsByCurrency(curr);
            if (prevTotals.isEmpty() || currTotals.isEmpty()) {
                return null;
            }
            LlmBillingDTO.Delta d = new LlmBillingDTO.Delta();
            d.setFromSnapshotId(history.get(1).getId());
            d.setFromTime(history.get(1).getCreateTime());
            d.setFromTotal(prevTotals);
            d.setToTotal(currTotals);
            Map<String, String> spend = new LinkedHashMap<>();
            for (Map.Entry<String, String> e : currTotals.entrySet()) {
                String prevV = prevTotals.get(e.getKey());
                if (prevV == null) {
                    continue; // 新增币种无法算差值
                }
                BigDecimal diff = new BigDecimal(prevV).subtract(new BigDecimal(e.getValue()));
                if (diff.signum() < 0) {
                    diff = BigDecimal.ZERO; // 充值导致余额上升时不计负花费
                }
                spend.put(e.getKey(), diff.stripTrailingZeros().toPlainString());
            }
            d.setPeriodSpend(spend);
            return d;
        } catch (Exception e) {
            log.warn("compute billing delta failed for {}: {}", provider, e.getMessage());
            return null;
        }
    }

    private Map<String, String> totalsByCurrency(JsonNode root) {
        Map<String, String> out = new LinkedHashMap<>();
        for (JsonNode b : root.path("balances")) {
            String cur = b.path("currency").asText(null);
            String total = b.path("totalBalance").asText(null);
            if (StringUtils.hasText(cur) && StringUtils.hasText(total)) {
                out.put(cur, total);
            }
        }
        return out;
    }
}
