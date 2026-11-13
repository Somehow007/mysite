package io.github.somehow.mysite.utils;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 客户端 IP 解析：Nginx 反代后 X-Forwarded-For 携带原始 IP，取首个。
 */
public final class ClientIpResolver {

    private ClientIpResolver() {
    }

    public static String resolve(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
