package io.github.somehow.mysite.security;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.somehow.mysite.dao.entity.UserDO;
import io.github.somehow.mysite.dao.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * 加载用户认证和授权所需的信息
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {
    private final UserMapper userMapper;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserDO userDO = userMapper.selectOne(Wrappers.lambdaQuery(UserDO.class)
                .eq(UserDO::getUsername, username)
                .eq(UserDO::getDelFlag, 0));
        if (Objects.isNull(userDO)) {
            throw new UsernameNotFoundException("用户不存在: " + username);
        }
        return new SecurityUserDetails(userDO);
    }
}
