package io.github.somehow.mysite.commons.context;

import io.github.somehow.mysite.commons.enums.UserRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserInfoDTO {

    private String userId;

    private String name;

    @Builder.Default
    private UserRole role = UserRole.USER;
}
