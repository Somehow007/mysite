package io.github.somehow.mysite.security;

import io.github.somehow.mysite.dao.entity.UserDO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * 封装用户实体
 */
@RequiredArgsConstructor
public class SecurityUserDetails implements UserDetails {

    private final UserDO userDO;

    public UserDO getUserDO() {
        return userDO;
    }

    /**
     * 获取用户ID
     */
    public Long getUserId() {
        return userDO.getId();
    }

    /**
     * 获取用户实体
     */
    public UserDO getUserDO() {
        return userDO;
    }

    /**
     * 获取用户角色权限集合
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }

    @Override
    public String getPassword() {
        return userDO.getPassword();
    }

    @Override
    public String getUsername() {
        // 用户名登陆，也可以让其为手机号登陆
        return userDO.getUsername();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
