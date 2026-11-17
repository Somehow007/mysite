package io.github.somehow.mysite.ragent.billing;

import io.github.somehow.mysite.ragent.config.RagProperties;

/**
 * 官方账单探针：按 provider 拉取官方口径的余额/用量。
 * 返回的 data 会原样落 t_llm_balance_snapshot.payload，密钥不进 data、不进日志。
 */
public interface BillingProbe {

    /** 对应 RagProperties.providers 的 key，如 "deepseek" / "bailian"。 */
    String provider();

    /**
     * 拉取官方账单数据。
     * @return ok=true 时 data 为规范化字段（余额/配额），ok=false 时 message 为原因
     */
    BillingProbeResult probe(RagProperties.Provider provider);

    record BillingProbeResult(boolean ok, long latencyMs, String message,
                              java.util.Map<String, Object> data) {
        public static BillingProbeResult ok(long latencyMs, java.util.Map<String, Object> data) {
            return new BillingProbeResult(true, latencyMs, null, data);
        }

        public static BillingProbeResult fail(long latencyMs, String message) {
            return new BillingProbeResult(false, latencyMs, message, null);
        }
    }
}
