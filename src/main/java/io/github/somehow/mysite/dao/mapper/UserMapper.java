package io.github.somehow.mysite.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import io.github.somehow.mysite.dao.entity.UserDO;
import io.github.somehow.mysite.dto.resp.user.UserPageQueryFollowRespDTO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

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
    
    /**
     * 根据用户名搜索用户
     * 
     * @param username 用户名（模糊搜索）
     * @return 匹配的用户列表
     */
    List<UserDO> selectByUsernameLike(@Param("username") String username);

    default UserDO selectOneByUsername(String username) {
        return selectOne(com.baomidou.mybatisplus.core.toolkit.Wrappers.lambdaQuery(UserDO.class)
                .eq(UserDO::getUsername, username)
                .eq(UserDO::getDelFlag, 0));
    }
}