package io.github.somehow.mysite.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.somehow.mysite.dao.entity.UserFollowDO;
import org.apache.ibatis.annotations.Param;

public interface UserFollowMapper extends BaseMapper<UserFollowDO> {

    int incrementFollowCount(@Param("followerId") Long followerId, @Param("followeeId") Long followeeId, @Param("incrementNum") Integer incrementNum);

    int decrementFollowCount(@Param("followerId") Long followerId, @Param("followeeId") Long followeeId, @Param("decrementNum") Integer decrementNum);
}
