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
 * 百炼用量配额：GET https://dashscope.aliyuncs.com/api/v1/quotas（Bearer DashScope Key）。
 * 响应 {code:"Success", data:{daily_spend, monthly_spend, tokens_used, requests_used,
 * billing_period, models[]}}。字段缺失时保留 raw 供排查，不做强假设。
 */
@Component
public class BailianBillingProbe implements BillingProbe {

    private static final String QUOTAS_URL = "https://dashscope.aliyuncs.com/api/v1/quotas";

    private final ObjectMapper om;

    public BailianBillingProbe(ObjectMapper om) {
        this.om = om;
    }

    @Override
    public String provider() {
        return "bailian";
    }

    @Override
    public BillingProbeResult probe(RagProperties.Provider p) {
        if (!StringUtils.hasText(p.getApiKey())) {
            return BillingProbeResult.fail(0, "API Key 未配置");
        }
        long t0 = System.currentTimeMillis();
        try {
            JsonNode root = BillingHttpSupport.getJson(QUOTAS_URL, p.getApiKey(), Duration.ofSeconds(10), om);
            long latency = System.currentTimeMillis() - t0;
            String code = root.path("code").asText("");
            if (!code.isEmpty() && !"Success".equalsIgnoreCase(code)) {
                return BillingProbeResult.fail(latency, "API code=" + code);
            }
            JsonNode data = root.path("data").isMissingNode() ? root : root.path("data");
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("kind", "quota");
            copyIfPresent(data, out, "daily_spend", "dailySpend");
            copyIfPresent(data, out, "monthly_spend", "monthlySpend");
            copyIfPresent(data, out, "tokens_used", "tokensUsed");
            copyIfPresent(data, out, "requests_used", "requestsUsed");
            if (data.has("billing_period")) {
                out.put("billingPeriod", data.path("billing_period"));
            }
            List<Map<String, Object>> models = new ArrayList<>();
            for (JsonNode m : data.path("models")) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("name", m.path("name").asText(m.path("model").asText(null)));
                row.put("used", m.path("used").asText(null));
                row.put("limit", m.path("limit").asText(null));
                models.add(row);
            }
            out.put("models", models);
            // 未识别字段原样带上（截断防超大），便于接口口径变化时排查
            out.put("raw", om.writeValueAsString(root).length() > 8000
                    ? root.toString().substring(0, 8000) : root);
            return BillingProbeResult.ok(latency, out);
        } catch (ProbeException e) {
            return BillingProbeResult.fail(System.currentTimeMillis() - t0, e.getMessage());
        } catch (Exception e) {
            return BillingProbeResult.fail(System.currentTimeMillis() - t0, "parse failed: " + e.getMessage());
        }
    }

    private static void copyIfPresent(JsonNode src, Map<String, Object> dst, String from, String to) {
        JsonNode v = src.path(from);
        if (!v.isMissingNode() && !v.isNull()) {
            dst.put(to, v.isNumber() ? v.numberValue() : v.asText());
        }
    }
}
