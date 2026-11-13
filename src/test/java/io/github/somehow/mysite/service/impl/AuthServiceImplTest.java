package io.github.somehow.mysite.service.impl;

import io.github.somehow.mysite.commons.enums.UserRole;
import io.github.somehow.mysite.commons.framework.errorcode.ErrorCode;
import io.github.somehow.mysite.commons.framework.exception.ClientException;
import io.github.somehow.mysite.config.JwtProperties;
import io.github.somehow.mysite.dao.entity.UserDO;
import io.github.somehow.mysite.dao.mapper.UserMapper;
import io.github.somehow.mysite.dto.req.auth.LoginReqDTO;
import io.github.somehow.mysite.dto.req.auth.RefreshTokenReqDTO;
import io.github.somehow.mysite.dto.req.auth.RegisterReqDTO;
import io.github.somehow.mysite.dto.resp.auth.LoginRespDTO;
import io.github.somehow.mysite.elasticsearch.service.UserIndexService;
import io.github.somehow.mysite.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 认证关键路径 characterization 测试（阶段 2 安全网）。
 * 钉的是「现状行为」：错误码映射、默认值、token 签发参数，而非业务对错。
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private JwtProperties jwtProperties;
    @Mock
    private UserIndexService userIndexService;

    @InjectMocks
    private AuthServiceImpl authService;

    private UserDO activeUser;

    @BeforeEach
    void setUp() {
        activeUser = new UserDO();
        activeUser.setId(1L);
        activeUser.setUsername("alice");
        activeUser.setPassword("hashed-pw");
        activeUser.setRole(UserRole.USER);
        activeUser.setStatus(1);
        activeUser.setDelFlag(0);
        lenient().when(jwtProperties.getAccessTokenExpiration()).thenReturn(86400000L);
    }

    // ─── login ─────────────────────────────────────────

    @Test
    void login_userNotFound_throwsBadCredentials() {
        when(userMapper.selectOneByUsername("ghost")).thenReturn(null);
        LoginReqDTO req = new LoginReqDTO();
        req.setUsername("ghost");
        req.setPassword("x");

        ClientException ex = assertThrows(ClientException.class, () -> authService.login(req));
        assertEquals(ErrorCode.USER_LOGIN_BAD_CREDENTIALS.code(), ex.getErrorCode());
    }

    @Test
    void login_deletedUser_throwsBadCredentials() {
        activeUser.setDelFlag(1);
        when(userMapper.selectOneByUsername("alice")).thenReturn(activeUser);
        LoginReqDTO req = loginReq("alice", "pw");

        ClientException ex = assertThrows(ClientException.class, () -> authService.login(req));
        assertEquals(ErrorCode.USER_LOGIN_BAD_CREDENTIALS.code(), ex.getErrorCode());
    }

    @Test
    void login_wrongPassword_throwsBadCredentials() {
        when(userMapper.selectOneByUsername("alice")).thenReturn(activeUser);
        when(passwordEncoder.matches("bad", "hashed-pw")).thenReturn(false);

        ClientException ex = assertThrows(ClientException.class, () -> authService.login(loginReq("alice", "bad")));
        assertEquals(ErrorCode.USER_LOGIN_BAD_CREDENTIALS.code(), ex.getErrorCode());
    }

    @Test
    void login_disabledUser_throwsAccountDisabled() {
        activeUser.setStatus(0);
        when(userMapper.selectOneByUsername("alice")).thenReturn(activeUser);
        when(passwordEncoder.matches("pw", "hashed-pw")).thenReturn(true);

        ClientException ex = assertThrows(ClientException.class, () -> authService.login(loginReq("alice", "pw")));
        assertEquals(ErrorCode.USER_ACCOUNT_DISABLED.code(), ex.getErrorCode());
    }

    @Test
    void login_success_returnsTokensAndExpiresInSeconds() {
        when(userMapper.selectOneByUsername("alice")).thenReturn(activeUser);
        when(passwordEncoder.matches("pw", "hashed-pw")).thenReturn(true);
        when(jwtUtil.generateAccessToken(1L, "alice", UserRole.USER)).thenReturn("access-tok");
        when(jwtUtil.generateRefreshToken(1L, "alice", UserRole.USER)).thenReturn("refresh-tok");

        LoginRespDTO resp = authService.login(loginReq("alice", "pw"));

        assertEquals("access-tok", resp.getAccessToken());
        assertEquals("refresh-tok", resp.getRefreshToken());
        assertEquals(86400L, resp.getExpiresIn());   // 毫秒 → 秒
        assertEquals(1L, resp.getUserId());
        assertEquals("alice", resp.getUsername());
        assertEquals("USER", resp.getRole());
    }

    @Test
    void login_nullRole_defaultsToUserInResponse() {
        activeUser.setRole(null);
        when(userMapper.selectOneByUsername("alice")).thenReturn(activeUser);
        when(passwordEncoder.matches("pw", "hashed-pw")).thenReturn(true);
        when(jwtUtil.generateAccessToken(any(), anyString(), any())).thenReturn("a");
        when(jwtUtil.generateRefreshToken(any(), anyString(), any())).thenReturn("r");

        LoginRespDTO resp = authService.login(loginReq("alice", "pw"));
        assertEquals("USER", resp.getRole());
    }

    // ─── refreshToken ──────────────────────────────────

    @Test
    void refreshToken_invalid_throwsRefreshTokenInvalid() {
        when(jwtUtil.validateToken("bad")).thenReturn(false);
        RefreshTokenReqDTO req = new RefreshTokenReqDTO();
        req.setRefreshToken("bad");

        ClientException ex = assertThrows(ClientException.class, () -> authService.refreshToken(req));
        assertEquals(ErrorCode.AUTH_REFRESH_TOKEN_INVALID.code(), ex.getErrorCode());
    }

    @Test
    void refreshToken_userDeleted_throwsUserNotFound() {
        when(jwtUtil.validateToken("rt")).thenReturn(true);
        when(jwtUtil.isRefreshToken("rt")).thenReturn(true);
        when(jwtUtil.getUserIdFromToken("rt")).thenReturn(9L);
        when(jwtUtil.getUsernameFromToken("rt")).thenReturn("alice");
        when(userMapper.selectById(9L)).thenReturn(null);
        RefreshTokenReqDTO req = new RefreshTokenReqDTO();
        req.setRefreshToken("rt");

        ClientException ex = assertThrows(ClientException.class, () -> authService.refreshToken(req));
        assertEquals(ErrorCode.AUTH_USER_NOT_FOUND.code(), ex.getErrorCode());
    }

    @Test
    void refreshToken_success_issuesNewPair() {
        when(jwtUtil.validateToken("rt")).thenReturn(true);
        when(jwtUtil.isRefreshToken("rt")).thenReturn(true);
        when(jwtUtil.getUserIdFromToken("rt")).thenReturn(1L);
        when(jwtUtil.getUsernameFromToken("rt")).thenReturn("alice");
        when(userMapper.selectById(1L)).thenReturn(activeUser);
        when(jwtUtil.generateAccessToken(1L, "alice", UserRole.USER)).thenReturn("new-access");
        when(jwtUtil.generateRefreshToken(1L, "alice", UserRole.USER)).thenReturn("new-refresh");
        RefreshTokenReqDTO req = new RefreshTokenReqDTO();
        req.setRefreshToken("rt");

        LoginRespDTO resp = authService.refreshToken(req);

        assertEquals("new-access", resp.getAccessToken());
        assertEquals("new-refresh", resp.getRefreshToken());
        assertEquals(1L, resp.getUserId());
    }

    // ─── register ──────────────────────────────────────

    @Test
    void register_blankUsernameOrPassword_throws() {
        RegisterReqDTO req = validRegister();
        req.setUsername(" ");
        ClientException ex = assertThrows(ClientException.class, () -> authService.register(req));
        assertEquals(ErrorCode.USER_REGISTER_USERNAME_OR_PASSWORD_BLANK.code(), ex.getErrorCode());
    }

    @Test
    void register_blankPhone_throws() {
        RegisterReqDTO req = validRegister();
        req.setPhoneNumber(null);
        ClientException ex = assertThrows(ClientException.class, () -> authService.register(req));
        assertEquals(ErrorCode.USER_REGISTER_PHONE_BLANK.code(), ex.getErrorCode());
    }

    @Test
    void register_blankEmail_throws() {
        RegisterReqDTO req = validRegister();
        req.setEmail("");
        ClientException ex = assertThrows(ClientException.class, () -> authService.register(req));
        assertEquals(ErrorCode.USER_REGISTER_EMAIL_BLANK.code(), ex.getErrorCode());
    }

    @Test
    void register_success_encodesPasswordAndSetsDefaults() {
        when(passwordEncoder.encode("raw-pw")).thenReturn("encoded-pw");
        RegisterReqDTO req = validRegister();

        authService.register(req);

        ArgumentCaptor<UserDO> captor = ArgumentCaptor.forClass(UserDO.class);
        verify(userMapper).insert(captor.capture());
        UserDO saved = captor.getValue();
        assertNotNull(saved.getId());                 // snowflake
        assertEquals("encoded-pw", saved.getPassword());
        assertEquals(0, saved.getDelFlag());
        assertEquals(0, saved.getFollowingCount());
        assertEquals(0, saved.getFollowerCount());
        assertEquals(UserRole.USER, saved.getRole()); // DO 默认，注册不接受外部角色
        verify(userIndexService).indexUser(saved);
    }

    @Test
    void register_duplicateUsername_mapsToUsernameExists() {
        when(passwordEncoder.encode(anyString())).thenReturn("h");
        doThrow(new DuplicateKeyException("Duplicate entry 'alice' for key 'uk_username'"))
                .when(userMapper).insert(any(UserDO.class));

        ClientException ex = assertThrows(ClientException.class, () -> authService.register(validRegister()));
        assertEquals(ErrorCode.USER_REGISTER_USERNAME_EXISTS.code(), ex.getErrorCode());
    }

    @Test
    void register_duplicatePhone_mapsToPhoneExists() {
        when(passwordEncoder.encode(anyString())).thenReturn("h");
        doThrow(new DuplicateKeyException("Duplicate entry '138' for key 'uk_phone_number'"))
                .when(userMapper).insert(any(UserDO.class));

        ClientException ex = assertThrows(ClientException.class, () -> authService.register(validRegister()));
        assertEquals(ErrorCode.USER_REGISTER_PHONE_EXISTS.code(), ex.getErrorCode());
    }

    @Test
    void register_unrecognizedDuplicate_mapsToGenericDuplicate() {
        when(passwordEncoder.encode(anyString())).thenReturn("h");
        doThrow(new DuplicateKeyException("some other constraint"))
                .when(userMapper).insert(any(UserDO.class));

        ClientException ex = assertThrows(ClientException.class, () -> authService.register(validRegister()));
        assertEquals(ErrorCode.USER_REGISTER_DUPLICATE.code(), ex.getErrorCode());
    }

    // ─── helpers ───────────────────────────────────────

    private LoginReqDTO loginReq(String username, String password) {
        LoginReqDTO req = new LoginReqDTO();
        req.setUsername(username);
        req.setPassword(password);
        return req;
    }

    private RegisterReqDTO validRegister() {
        RegisterReqDTO req = new RegisterReqDTO();
        req.setUsername("alice");
        req.setPassword("raw-pw");
        req.setRealName("爱丽丝");
        req.setPhoneNumber("13800138000");
        req.setEmail("a@b.com");
        return req;
    }
}
