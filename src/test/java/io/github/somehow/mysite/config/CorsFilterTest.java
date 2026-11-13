package io.github.somehow.mysite.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CorsFilter 白名单契约测试。
 *
 * <p>修复后行为：仅在 Origin 命中 {@code cors.allowed-origins} 时回显并附带
 * credentials；非白名单 Origin 不放任何 CORS 头；配置 {@code *} 时发
 * {@code ACAO: *} 但不发 credentials（规范不允许 * + credentials 组合）。</p>
 */
class CorsFilterTest {

    private CorsFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        filter = new CorsFilter();
        request = new MockHttpServletRequest();
        request.setMethod("GET");
        request.setRequestURI("/v1/articles");
        response = new MockHttpServletResponse();
    }

    private void configure(String allowedOrigins) {
        ReflectionTestUtils.setField(filter, "allowedOriginsConfig", allowedOrigins);
        filter.initAllowedOrigins();
    }

    private void doFilter() throws Exception {
        filter.doFilter(request, response, (req, res) -> { });
    }

    @Test
    void whitelistedOrigin_shouldEchoWithCredentials() throws Exception {
        configure("http://localhost:5173,http://localhost:3000");
        request.addHeader("Origin", "http://localhost:5173");

        doFilter();

        assertEquals("http://localhost:5173",
                response.getHeader("Access-Control-Allow-Origin"));
        assertEquals("true", response.getHeader("Access-Control-Allow-Credentials"));
        assertEquals("Origin", response.getHeader("Vary"));
    }

    @Test
    void nonWhitelistedOrigin_shouldEmitNoCorsHeaders() throws Exception {
        configure("http://localhost:5173");
        request.addHeader("Origin", "https://evil.example");

        doFilter();

        assertNull(response.getHeader("Access-Control-Allow-Origin"));
        assertNull(response.getHeader("Access-Control-Allow-Credentials"));
    }

    @Test
    void wildcardConfig_shouldEmitStarWithoutCredentials() throws Exception {
        configure("*");
        request.addHeader("Origin", "https://anywhere.example");

        doFilter();

        // 生产同源反代不需要凭证；* + credentials 是规范无效组合，不再发
        assertEquals("*", response.getHeader("Access-Control-Allow-Origin"));
        assertNull(response.getHeader("Access-Control-Allow-Credentials"));
    }

    @Test
    void noOrigin_shouldEmitNoCorsHeaders() throws Exception {
        configure("http://localhost:5173");

        doFilter();

        assertNull(response.getHeader("Access-Control-Allow-Origin"));
        assertNull(response.getHeader("Access-Control-Allow-Credentials"));
    }

    @Test
    void optionsPreflight_shouldShortCircuit200() throws Exception {
        configure("http://localhost:5173");
        request.setMethod("OPTIONS");
        request.addHeader("Origin", "http://localhost:5173");

        boolean[] chainCalled = {false};
        FilterChain chain = (req, res) -> chainCalled[0] = true;
        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        assertFalse(chainCalled[0]);
        assertEquals("http://localhost:5173",
                response.getHeader("Access-Control-Allow-Origin"));
    }

    @Test
    void emptyConfig_shouldDenyAllOrigins() throws Exception {
        configure("");
        request.addHeader("Origin", "http://localhost:5173");

        doFilter();

        assertNull(response.getHeader("Access-Control-Allow-Origin"));
    }
}
