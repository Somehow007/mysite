package io.github.somehow.mysite.service;

import io.github.somehow.mysite.dao.entity.UserDO;

public interface UserSyncService {

    void syncUser(UserDO user);

    void deleteUser(Long userId);

    void syncAllUsers();

    boolean isEnabled();
}
