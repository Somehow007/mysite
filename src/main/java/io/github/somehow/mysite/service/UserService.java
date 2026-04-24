package io.github.somehow.mysite.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import io.github.somehow.mysite.dao.entity.UserDO;
import io.github.somehow.mysite.dto.req.auth.ChangePasswordReqDTO;
import io.github.somehow.mysite.dto.req.user.UserFollowReqDTO;
import io.github.somehow.mysite.dto.req.user.UserPageQueryReqDTO;
import io.github.somehow.mysite.dto.req.user.UserUpdateReqDTO;
import io.github.somehow.mysite.dto.resp.user.UserPageQueryFollowRespDTO;
import io.github.somehow.mysite.dto.resp.user.UserSearchRespDTO;
import io.github.somehow.mysite.dto.resp.user.UserSelectRespDTO;

public interface UserService extends IService<UserDO> {

    UserSelectRespDTO selectUserById(String id);
    void updateUser(UserUpdateReqDTO requestParam);
    void changePassword(Long userId, ChangePasswordReqDTO requestParam);
    void followUser(UserFollowReqDTO requestParam);
    IPage<UserPageQueryFollowRespDTO> selectFollowers(String id, long current, long size);
    IPage<UserPageQueryFollowRespDTO> selectFollowings(String id, long current, long size);
    IPage<UserSearchRespDTO> pageQueryUser(UserPageQueryReqDTO requestParam);
}
