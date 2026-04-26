package io.github.somehow.mysite.dto.resp.auth;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LoginRespDTO {

    private String accessToken;
    private String refreshToken;
    private long expiresIn;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;
    private String username;
    private String role;
}
