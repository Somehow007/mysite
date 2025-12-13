package io.github.somehow.mysite.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import io.github.somehow.mysite.dao.entity.UserDO;
import io.github.somehow.mysite.dto.req.user.UserFollowReqDTO;
import io.github.somehow.mysite.dto.resp.user.UserPageQueryFollowRespDTO;
import io.github.somehow.mysite.dto.req.user.UserPageQueryReqDTO;
import io.github.somehow.mysite.dto.req.user.UserRegistryReqDTO;
import io.github.somehow.mysite.dto.req.user.UserUpdateReqDTO;
import io.github.somehow.mysite.dto.resp.user.UserSearchRespDTO;
import io.github.somehow.mysite.dto.resp.user.UserSelectRespDTO;

/**
 * 用户业务逻辑层
 */
public interface UserService extends IService<UserDO> {

    /**
     * 用户注册
     *
     * @param requestParam 请求参数
     */
    void registry(UserRegistryReqDTO requestParam);

    /**
     * 根据id获取用户信息
     *
     * @param id 用户id
     */
    UserSelectRespDTO selectUserById(String id);

    /**
     * 更新用户信息
     *
     * @param requestParam 请求参数
     */
    void updateUser(UserUpdateReqDTO requestParam);

    /**
     * 关注或取消关注用户
     *
     * @param requestParam 请求参数
     */
    void followUser(UserFollowReqDTO requestParam);

    /**
     * 分页查询粉丝信息
     *
     * @param id 用户Id
     */
    IPage<UserPageQueryFollowRespDTO> selectFollowers(String id);

    /**
     * 分页查询关注的用户信息
     *
     * @param id 用户Id
     */
    IPage<UserPageQueryFollowRespDTO> selectFollowings(String id);
    
    /**
     * 分页搜索用户
     *
     * @param requestParam 请求参数
     */
    IPage<UserSearchRespDTO> pageQueryUser(UserPageQueryReqDTO requestParam);
}