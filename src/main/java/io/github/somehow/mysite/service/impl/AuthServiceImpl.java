package io.github.somehow.mysite.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
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
import io.github.somehow.mysite.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final JwtProperties jwtProperties;
    private final UserIndexService userIndexService;

    @Override
    public LoginRespDTO login(LoginReqDTO requestParam) {
        UserDO userDO = userMapper.selectOneByUsername(requestParam.getUsername());
        if (Objects.isNull(userDO) || userDO.getDelFlag() == 1) {
            throw new ClientException(ErrorCode.USER_LOGIN_BAD_CREDENTIALS);
        }
        if (!passwordEncoder.matches(requestParam.getPassword(), userDO.getPassword())) {
            throw new ClientException(ErrorCode.USER_LOGIN_BAD_CREDENTIALS);
        }
        if (userDO.getStatus() != null && userDO.getStatus() == 0) {
            throw new ClientException(ErrorCode.USER_ACCOUNT_DISABLED);
        }

        String accessToken = jwtUtil.generateAccessToken(userDO.getId(), userDO.getUsername(), userDO.getRole());
        String refreshToken = jwtUtil.generateRefreshToken(userDO.getId(), userDO.getUsername(), userDO.getRole());

        return LoginRespDTO.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(jwtProperties.getAccessTokenExpiration() / 1000)
                .userId(userDO.getId())
                .username(userDO.getUsername())
                .role(userDO.getRole() != null ? userDO.getRole().name() : "USER")
                .build();
    }

    @Override
    public void logout(Long userId) {
    }

    @Override
    public LoginRespDTO refreshToken(RefreshTokenReqDTO requestParam) {
        String refreshToken = requestParam.getRefreshToken();
        if (!jwtUtil.validateToken(refreshToken) || !jwtUtil.isRefreshToken(refreshToken)) {
            throw new ClientException(ErrorCode.AUTH_REFRESH_TOKEN_INVALID);
        }

        Long userId = jwtUtil.getUserIdFromToken(refreshToken);
        String username = jwtUtil.getUsernameFromToken(refreshToken);

        UserDO userDO = userMapper.selectById(userId);
        if (Objects.isNull(userDO) || userDO.getDelFlag() == 1) {
            throw new ClientException(ErrorCode.AUTH_USER_NOT_FOUND);
        }
        if (userDO.getStatus() != null && userDO.getStatus() == 0) {
            throw new ClientException(ErrorCode.USER_ACCOUNT_DISABLED);
        }

        String newAccessToken = jwtUtil.generateAccessToken(userId, username, userDO.getRole());
        String newRefreshToken = jwtUtil.generateRefreshToken(userId, username, userDO.getRole());

        return LoginRespDTO.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .expiresIn(jwtProperties.getAccessTokenExpiration() / 1000)
                .userId(userId)
                .username(username)
                .role(userDO.getRole() != null ? userDO.getRole().name() : "USER")
                .build();
    }

    @Override
    @Transactional
    public void register(RegisterReqDTO requestParam) {
        if (StrUtil.isBlank(requestParam.getUsername()) || StrUtil.isBlank(requestParam.getPassword())) {
            throw new ClientException(ErrorCode.USER_REGISTER_USERNAME_OR_PASSWORD_BLANK);
        }
        if (StrUtil.isBlank(requestParam.getPhoneNumber())) {
            throw new ClientException(ErrorCode.USER_REGISTER_PHONE_BLANK);
        }
        if (StrUtil.isBlank(requestParam.getRealName())) {
            throw new ClientException(ErrorCode.USER_REGISTER_REAL_NAME_BLANK);
        }
        if (StrUtil.isBlank(requestParam.getEmail())) {
            throw new ClientException(ErrorCode.USER_REGISTER_EMAIL_BLANK);
        }

        UserDO userDO = BeanUtil.toBean(requestParam, UserDO.class);
        userDO.setId(IdUtil.getSnowflakeNextId());
        userDO.setPassword(passwordEncoder.encode(requestParam.getPassword()));
        userDO.setDelFlag(0);
        userDO.setFollowingCount(0);
        userDO.setFollowerCount(0);
        
        try {
            userMapper.insert(userDO);
            userIndexService.indexUser(userDO);
        } catch (DuplicateKeyException ex) {
            String message = ex.getMessage();
            if (message != null) {
                if (message.contains("uk_username")) {
                    throw new ClientException(ErrorCode.USER_REGISTER_USERNAME_EXISTS);
                } else if (message.contains("uk_phone_number")) {
                    throw new ClientException(ErrorCode.USER_REGISTER_PHONE_EXISTS);
                }
            }
            throw new ClientException(ErrorCode.USER_REGISTER_DUPLICATE);
        }
    }
}
