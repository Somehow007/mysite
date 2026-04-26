package io.github.somehow.mysite.commons.context;

import com.alibaba.ttl.TransmittableThreadLocal;
import io.github.somehow.mysite.commons.enums.UserRole;

import java.util.Optional;

public final class UserContext {

    private static final ThreadLocal<UserInfoDTO> USER_THREAD_LOCAL = new TransmittableThreadLocal<>();

    public static void setUser(UserInfoDTO user) {
        USER_THREAD_LOCAL.set(user);
    }

    public static String getUserId() {
        UserInfoDTO userInfoDTO = USER_THREAD_LOCAL.get();
        return Optional.ofNullable(userInfoDTO).map(UserInfoDTO::getUserId).orElse(null);
    }

    public static UserRole getRole() {
        UserInfoDTO userInfoDTO = USER_THREAD_LOCAL.get();
        return Optional.ofNullable(userInfoDTO).map(UserInfoDTO::getRole).orElse(null);
    }

    public static boolean isDeveloper() {
        return UserRole.DEVELOPER.equals(getRole());
    }

    public static void removeUser() {
        USER_THREAD_LOCAL.remove();
    }
}
