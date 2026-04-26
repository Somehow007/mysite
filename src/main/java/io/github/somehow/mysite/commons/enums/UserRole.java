package io.github.somehow.mysite.commons.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum UserRole {

    DEVELOPER("ROLE_DEVELOPER", "开发者"),
    USER("ROLE_USER", "普通用户");

    private final String authority;
    private final String description;

    public static UserRole fromAuthority(String authority) {
        for (UserRole role : values()) {
            if (role.authority.equals(authority)) {
                return role;
            }
        }
        return USER;
    }
}
