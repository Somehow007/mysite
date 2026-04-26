package io.github.somehow.mysite.security;

import io.github.somehow.mysite.dao.entity.UserDO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@RequiredArgsConstructor
public class SecurityUserDetails implements UserDetails {

    private final UserDO userDO;

    public UserDO getUserDO() {
        return userDO;
    }

    public Long getUserId() {
        return userDO.getId();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (userDO.getRole() != null) {
            return List.of(new SimpleGrantedAuthority(userDO.getRole().getAuthority()));
        }
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Override
    public String getPassword() {
        return userDO.getPassword();
    }

    @Override
    public String getUsername() {
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
