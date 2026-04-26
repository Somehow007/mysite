package io.github.somehow.mysite.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
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
            throw new ClientException("用户名或密码错误");
        }
        if (!passwordEncoder.matches(requestParam.getPassword(), userDO.getPassword())) {
            throw new ClientException("用户名或密码错误");
        }
        if (userDO.getStatus() != null && userDO.getStatus() == 0) {
            throw new ClientException("账户已被禁用，请联系管理员");
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
            throw new ClientException("无效的刷新令牌");
        }

        Long userId = jwtUtil.getUserIdFromToken(refreshToken);
        String username = jwtUtil.getUsernameFromToken(refreshToken);

        UserDO userDO = userMapper.selectById(userId);
        if (Objects.isNull(userDO) || userDO.getDelFlag() == 1) {
            throw new ClientException("用户不存在");
        }
        if (userDO.getStatus() != null && userDO.getStatus() == 0) {
            throw new ClientException("账户已被禁用，请联系管理员");
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
            throw new ClientException("注册失败，请检查您是否成功填写用户名或密码");
        }
        if (StrUtil.isBlank(requestParam.getPhoneNumber())) {
            throw new ClientException("请填写您的手机号");
        }
        if (StrUtil.isBlank(requestParam.getRealName())) {
            throw new ClientException("请填写您的真实姓名");
        }
        if (StrUtil.isBlank(requestParam.getEmail())) {
            throw new ClientException("请填写您的邮箱");
        }

        UserDO userDO = BeanUtil.toBean(requestParam, UserDO.class);
        userDO.setId(IdUtil.getSnowflakeNextId());
        userDO.setPassword(passwordEncoder.encode(requestParam.getPassword()));
        try {
            userMapper.insert(userDO);
            userIndexService.indexUser(userDO);
        } catch (DuplicateKeyException ex) {
            throw new ClientException("注册失败，该用户名已创建");
        }
    }
}
