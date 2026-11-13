package io.github.somehow.mysite.service.impl;

import io.github.somehow.mysite.commons.framework.errorcode.ErrorCode;
import io.github.somehow.mysite.commons.framework.exception.ClientException;
import io.github.somehow.mysite.config.ImageUploadConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * SSRF 重定向逐跳校验 characterization（R5 修复）。
 *
 * <p>钉住：302 到回环/内网地址必须在发送前被 checkSsrf 拦下；
 * 合法的外部重定向（http→https、同站相对路径、CDN）仍允许，最多 5 跳。</p>
 */
class ImageServiceImplSsrfTest {

    private ImageServiceImpl service;
    private HttpClient httpClient;

    private static final String PUBLIC_HOST = "8.8.8.8";

    @BeforeEach
    void setUp() {
        httpClient = mock(HttpClient.class);
        service = new ImageServiceImpl(new ImageUploadConfig(), mock(StringRedisTemplate.class));
    }

    @SuppressWarnings("unchecked")
    private HttpResponse<Object> response(int status, String location) {
        HttpResponse<Object> resp = mock(HttpResponse.class);
        when(resp.statusCode()).thenReturn(status);
        if (location != null) {
            when(resp.headers()).thenReturn(
                    HttpHeaders.of(Map.of("Location", List.of(location)), (k, v) -> true));
        } else {
            when(resp.headers()).thenReturn(HttpHeaders.of(Map.of(), (k, v) -> true));
        }
        return resp;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void stubSend(HttpResponse<Object>... responses) throws Exception {
        HttpResponse[] raw = responses;
        doReturn(raw[0], java.util.Arrays.copyOfRange(raw, 1, raw.length))
                .when(httpClient).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    private URI publicUrl(String path) {
        return URI.create("http://" + PUBLIC_HOST + path);
    }

    @Test
    void directResponse_shouldReturnImmediately() throws Exception {
        stubSend(response(200, null));

        HttpResponse<?> resp = service.sendFollowingRedirects(
                httpClient, publicUrl("/a.png"), "GET", HttpResponse.BodyHandlers.ofByteArray());

        assertEquals(200, resp.statusCode());
        verify(httpClient, times(1)).send(any(), any());
    }

    @Test
    void redirectToLoopback_shouldBlockBeforeSend() throws Exception {
        stubSend(response(302, "http://127.0.0.1/x.png"));

        ClientException ex = assertThrows(ClientException.class, () ->
                service.sendFollowingRedirects(
                        httpClient, publicUrl("/a.png"), "GET", HttpResponse.BodyHandlers.ofByteArray()));

        assertEquals(ErrorCode.IMAGE_SSRF_BLOCKED.code(), ex.getErrorCode());
        verify(httpClient, times(1)).send(any(), any()); // 第二跳未发出
    }

    @Test
    void redirectToLinkLocal_shouldBlockBeforeSend() throws Exception {
        // 169.254.169.254 云元数据端点——典型 SSRF 目标
        stubSend(response(302, "http://169.254.169.254/latest/meta-data"));

        ClientException ex = assertThrows(ClientException.class, () ->
                service.sendFollowingRedirects(
                        httpClient, publicUrl("/a.png"), "GET", HttpResponse.BodyHandlers.ofByteArray()));

        assertEquals(ErrorCode.IMAGE_SSRF_BLOCKED.code(), ex.getErrorCode());
        verify(httpClient, times(1)).send(any(), any());
    }

    @Test
    void redirectToSiteLocal_shouldBlockBeforeSend() throws Exception {
        stubSend(response(302, "http://192.168.1.1/internal.png"));

        ClientException ex = assertThrows(ClientException.class, () ->
                service.sendFollowingRedirects(
                        httpClient, publicUrl("/a.png"), "GET", HttpResponse.BodyHandlers.ofByteArray()));

        assertEquals(ErrorCode.IMAGE_SSRF_BLOCKED.code(), ex.getErrorCode());
        verify(httpClient, times(1)).send(any(), any());
    }

    @Test
    void externalRedirect_shouldFollow() throws Exception {
        stubSend(response(301, "http://9.9.9.9/b.png"), response(200, null));

        HttpResponse<?> resp = service.sendFollowingRedirects(
                httpClient, publicUrl("/a.png"), "GET", HttpResponse.BodyHandlers.ofByteArray());

        assertEquals(200, resp.statusCode());
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient, times(2)).send(captor.capture(), any());
        assertEquals(URI.create("http://9.9.9.9/b.png"), captor.getAllValues().get(1).uri());
    }

    @Test
    void relativeRedirect_shouldResolveOnSameHost() throws Exception {
        stubSend(response(301, "/img/moved.png"), response(200, null));

        service.sendFollowingRedirects(
                httpClient, publicUrl("/a.png"), "GET", HttpResponse.BodyHandlers.ofByteArray());

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient, times(2)).send(captor.capture(), any());
        assertEquals(URI.create("http://" + PUBLIC_HOST + "/img/moved.png"),
                captor.getAllValues().get(1).uri());
    }

    @Test
    void redirectWithoutLocation_shouldThrowUnreachable() throws Exception {
        stubSend(response(302, null));

        ClientException ex = assertThrows(ClientException.class, () ->
                service.sendFollowingRedirects(
                        httpClient, publicUrl("/a.png"), "GET", HttpResponse.BodyHandlers.ofByteArray()));

        assertEquals(ErrorCode.IMAGE_URL_UNREACHABLE.code(), ex.getErrorCode());
    }

    @Test
    void tooManyRedirects_shouldThrowUnreachable() throws Exception {
        // 6 次 302（上限 5）→ 放弃
        HttpResponse<Object> hop = response(301, "http://9.9.9.9/loop.png");
        stubSend(hop, hop, hop, hop, hop, hop);

        ClientException ex = assertThrows(ClientException.class, () ->
                service.sendFollowingRedirects(
                        httpClient, publicUrl("/a.png"), "GET", HttpResponse.BodyHandlers.ofByteArray()));

        assertEquals(ErrorCode.IMAGE_URL_UNREACHABLE.code(), ex.getErrorCode());
        verify(httpClient, times(6)).send(any(), any());
    }

    @Test
    void redirectToNonHttpScheme_shouldThrowInvalid() throws Exception {
        stubSend(response(302, "ftp://9.9.9.9/x.png"));

        ClientException ex = assertThrows(ClientException.class, () ->
                service.sendFollowingRedirects(
                        httpClient, publicUrl("/a.png"), "GET", HttpResponse.BodyHandlers.ofByteArray()));

        assertEquals(ErrorCode.IMAGE_URL_INVALID.code(), ex.getErrorCode());
        verify(httpClient, times(1)).send(any(), any());
    }
}
