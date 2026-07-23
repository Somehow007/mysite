package io.github.somehow.mysite.commons.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum UserRole {

    /** 系统管理员 —— 所有权限，AI 无限制 */
    ADMIN("ROLE_ADMIN", "系统管理员"),

    /** 创作者 —— 可发布/编辑自己的文章，AI 20次/小时 */
    CREATOR("ROLE_CREATOR", "创作者"),

    /** 普通用户 —— 浏览、评论，AI 10次/小时 */
    USER("ROLE_USER", "普通用户"),

    /**
     * @deprecated 旧角色，已合并到 ADMIN。保留以兼容 DB 旧数据 + 旧 JWT token。
     * 不要在新代码中分配此角色。
     */
    @Deprecated
    DEVELOPER("ROLE_ADMIN", "系统管理员(旧)");

    private final String authority;
    private final String description;

    public static UserRole fromAuthority(String authority) {
        for (UserRole role : values()) {
            if (role.authority.equals(authority)) {
                return role;
            }
        }
        // 兼容旧 token 中的 ROLE_DEVELOPER → ADMIN
        if ("ROLE_DEVELOPER".equals(authority)) {
            return ADMIN;
        }
        return USER;
    }
}
