package io.github.somehow.mysite.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import io.github.somehow.mysite.dao.entity.UserDO;
import io.github.somehow.mysite.dto.resp.user.UserPageQueryFollowRespDTO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface UserMapper extends BaseMapper<UserDO> {

    IPage<UserPageQueryFollowRespDTO> pageFollowersResult(IPage<UserPageQueryFollowRespDTO> page, @Param("id") String id);

    IPage<UserPageQueryFollowRespDTO> pageFollowingsResult(IPage<UserPageQueryFollowRespDTO> page, @Param("id") String id);

    List<UserDO> selectByUsernameLike(@Param("username") String username);

    default UserDO selectOneByUsername(String username) {
        return selectOne(com.baomidou.mybatisplus.core.toolkit.Wrappers.lambdaQuery(UserDO.class)
                .eq(UserDO::getUsername, username)
                .eq(UserDO::getDelFlag, 0));
    }
}