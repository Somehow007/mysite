package io.github.somehow.mysite.service;

import io.github.somehow.mysite.dto.req.auth.LoginReqDTO;
import io.github.somehow.mysite.dto.req.auth.RefreshTokenReqDTO;
import io.github.somehow.mysite.dto.req.auth.RegisterReqDTO;
import io.github.somehow.mysite.dto.resp.auth.LoginRespDTO;

public interface AuthService {

    LoginRespDTO login(LoginReqDTO requestParam);

    void logout(Long userId);

    LoginRespDTO refreshToken(RefreshTokenReqDTO requestParam);

    void register(RegisterReqDTO requestParam);
}
