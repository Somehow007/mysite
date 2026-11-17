package io.github.somehow.mysite.ragent.billing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** 账单探针共用的极简 GET-JSON 客户端（java.net.http，无新依赖）。 */
final class BillingHttpSupport {

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private BillingHttpSupport() {
    }

    /**
     * GET url，Bearer 认证，返回解析后的 JSON。
     * @throws ProbeException http 非 2xx 或 body 非 JSON
     */
    static JsonNode getJson(String url, String apiKey, Duration timeout, ObjectMapper om) throws ProbeException {
        HttpRequest.Builder rb = HttpRequest.newBuilder(URI.create(url))
                .timeout(timeout)
                .header("Accept", "application/json");
        if (apiKey != null && !apiKey.isBlank()) {
            rb.header("Authorization", "Bearer " + apiKey);
        }
        HttpResponse<String> resp;
        try {
            resp = CLIENT.send(rb.GET().build(), HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            throw new ProbeException("request failed: " + e.getMessage());
        }
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            // 错误体可能含敏感回显，截断且不记录 body
            throw new ProbeException("HTTP " + resp.statusCode());
        }
        try {
            return om.readTree(resp.body());
        } catch (Exception e) {
            throw new ProbeException("response is not JSON");
        }
    }

    static class ProbeException extends Exception {
        ProbeException(String m) {
            super(m);
        }
    }
}
