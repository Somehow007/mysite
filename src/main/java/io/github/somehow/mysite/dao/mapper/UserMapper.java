package io.github.somehow.mysite.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import io.github.somehow.mysite.dao.entity.UserDO;
import io.github.somehow.mysite.dto.resp.user.UserPageQueryFollowRespDTO;
import org.apache.ibatis.annotations.Param;

/**
 * 用户信息数据库持久层
 */
public interface UserMapper extends BaseMapper<UserDO> {

    /**
     * 分页统计用户信息；
     * 可分；
     * 1. 管理员获取所有用户
     * 2. 用户获取关注的用户
     * 3. 用户获取粉丝用户
     *
     */
//    IPage<UserPageQueryRespDTO> pageUserResults(UserPageQueryReqDTO requestParam);

    /**
     * 分页查询粉丝信息
     *
     * @param page 分页参数
     * @param id 用户ID
     */
    IPage<UserPageQueryFollowRespDTO> pageFollowersResult(IPage<UserPageQueryFollowRespDTO> page, @Param("id") String id);

    /**
     * 分页查询关注的人信息
     *
     * @param page 分页参数
     * @param id 用户ID
     */
    IPage<UserPageQueryFollowRespDTO> pageFollowingsResult(IPage<UserPageQueryFollowRespDTO> page, @Param("id") String id);
}