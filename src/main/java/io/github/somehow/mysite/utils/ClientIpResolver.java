package io.github.somehow.mysite.utils;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 客户端 IP 解析：Nginx 反代后 X-Forwarded-For 携带原始 IP，取首个；
 * 无 XFF 时回退 X-Real-IP，最后 remoteAddr。
 */
public final class ClientIpResolver {

    private ClientIpResolver() {
    }

    public static String resolve(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip)) {
            return ip.split(",")[0].trim();
        }
        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip)) {
            return ip;
        }
        return request.getRemoteAddr();
    }
}
