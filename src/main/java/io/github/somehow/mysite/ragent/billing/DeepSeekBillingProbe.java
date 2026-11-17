package io.github.somehow.mysite.ragent.billing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.somehow.mysite.ragent.billing.BillingHttpSupport.ProbeException;
import io.github.somehow.mysite.ragent.config.RagProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DeepSeek 官方余额：GET {baseUrl 去掉 /v1 后缀}/user/balance。
 * 响应 {is_available, balance_infos:[{currency,total_balance,granted_balance,topped_up_balance}]}。
 * 无逐调用账单 API；余额差值由快照表反推（赠金先扣，Δtotal 即真实消耗）。
 */
@Component
public class DeepSeekBillingProbe implements BillingProbe {

    private final ObjectMapper om;

    public DeepSeekBillingProbe(ObjectMapper om) {
        this.om = om;
    }

    @Override
    public String provider() {
        return "deepseek";
    }

    @Override
    public BillingProbeResult probe(RagProperties.Provider p) {
        if (!StringUtils.hasText(p.getApiKey())) {
            return BillingProbeResult.fail(0, "API Key 未配置");
        }
        String base = p.getBaseUrl() != null ? p.getBaseUrl().trim() : "https://api.deepseek.com/v1";
        // OpenAI 兼容 base 以 /v1 结尾，余额接口在其上一层
        if (base.endsWith("/v1/")) {
            base = base.substring(0, base.length() - 4);
        } else if (base.endsWith("/v1")) {
            base = base.substring(0, base.length() - 3);
        }
        String url = base + "/user/balance";
        long t0 = System.currentTimeMillis();
        try {
            JsonNode root = BillingHttpSupport.getJson(url, p.getApiKey(), Duration.ofSeconds(10), om);
            long latency = System.currentTimeMillis() - t0;
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("kind", "balance");
            data.put("isAvailable", root.path("is_available").asBoolean(false));
            List<Map<String, Object>> balances = new ArrayList<>();
            for (JsonNode b : root.path("balance_infos")) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("currency", b.path("currency").asText(null));
                row.put("totalBalance", b.path("total_balance").asText(null));
                row.put("grantedBalance", b.path("granted_balance").asText(null));
                row.put("toppedUpBalance", b.path("topped_up_balance").asText(null));
                balances.add(row);
            }
            data.put("balances", balances);
            return BillingProbeResult.ok(latency, data);
        } catch (ProbeException e) {
            return BillingProbeResult.fail(System.currentTimeMillis() - t0, e.getMessage());
        } catch (Exception e) {
            return BillingProbeResult.fail(System.currentTimeMillis() - t0, "parse failed: " + e.getMessage());
        }
    }
}
