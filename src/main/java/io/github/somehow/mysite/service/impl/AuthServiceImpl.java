package io.github.somehow.mysite.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import io.github.somehow.mysite.commons.framework.exception.ClientException;
import io.github.somehow.mysite.config.JwtProperties;
import io.github.somehow.mysite.dao.entity.UserDO;
import io.github.somehow.mysite.dao.mapper.UserEsRepository;
import io.github.somehow.mysite.dao.mapper.UserMapper;
import io.github.somehow.mysite.dto.req.auth.LoginReqDTO;
import io.github.somehow.mysite.dto.req.auth.RefreshTokenReqDTO;
import io.github.somehow.mysite.dto.req.auth.RegisterReqDTO;
import io.github.somehow.mysite.dto.resp.auth.LoginRespDTO;
import io.github.somehow.mysite.elasticsearch.UserDocument;
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
    private final UserEsRepository userEsRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final JwtProperties jwtProperties;

    @Override
    public LoginRespDTO login(LoginReqDTO requestParam) {
        UserDO userDO = userMapper.selectOneByUsername(requestParam.getUsername());
        if (Objects.isNull(userDO) || userDO.getDelFlag() == 1) {
            throw new ClientException("用户名或密码错误");
        }
        if (!passwordEncoder.matches(requestParam.getPassword(), userDO.getPassword())) {
            throw new ClientException("用户名或密码错误");
        }

        String accessToken = jwtUtil.generateAccessToken(userDO.getId(), userDO.getUsername());
        String refreshToken = jwtUtil.generateRefreshToken(userDO.getId(), userDO.getUsername());

        return LoginRespDTO.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(jwtProperties.getAccessTokenExpiration() / 1000)
                .userId(userDO.getId())
                .username(userDO.getUsername())
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

        String newAccessToken = jwtUtil.generateAccessToken(userId, username);
        String newRefreshToken = jwtUtil.generateRefreshToken(userId, username);

        return LoginRespDTO.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .expiresIn(jwtProperties.getAccessTokenExpiration() / 1000)
                .userId(userId)
                .username(username)
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

        UserDO userDO = BeanUtil.toBean(requestParam, UserDO.class);
        userDO.setId(IdUtil.getSnowflakeNextId());
        userDO.setPassword(passwordEncoder.encode(requestParam.getPassword()));
        try {
            userMapper.insert(userDO);
            UserDocument userDocument = UserDocument.builder()
                    .id(userDO.getId().toString())
                    .username(userDO.getUsername())
                    .realName(userDO.getRealName())
                    .sex(userDO.getSex())
                    .followingCount(userDO.getFollowingCount())
                    .followerCount(userDO.getFollowerCount())
                    .createTime(userDO.getCreateTime())
                    .build();
            userEsRepository.save(userDocument);
        } catch (DuplicateKeyException ex) {
            throw new ClientException("注册失败，该用户名已创建");
        }
    }
}
