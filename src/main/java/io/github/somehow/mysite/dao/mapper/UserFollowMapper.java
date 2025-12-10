package io.github.somehow.mysite.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.somehow.mysite.dao.entity.UserFollowDO;
import org.apache.ibatis.annotations.Param;

/**
 * 用户关注信息数据库持久层
 */
public interface UserFollowMapper extends BaseMapper<UserFollowDO> {

    /**
     * 分别增加相应的粉丝数和关注人数
     *
     * @param followerId 关注者id
     * @param followeeId 被关注者id
     * @param incrementNum 增加数量
     * @return 影响的行数，为0则失败
     */
    int incrementFollowCount(@Param("followerId") Long followerId, @Param("followeeId") Long followeeId, @Param("incrementNum") Integer incrementNum);

    /**
     * 分别减少相应的粉丝数和关注人数
     *
     * @param followerId 取消关注者id
     * @param followeeId 被取消关注者id
     * @param decrementNum 减少数量
     * @return 影响的行数，为0则失败
     */
    int decrementFollowCount(@Param("followerId") Long followerId, @Param("followeeId") Long followeeId, @Param("decrementNum") Integer decrementNum);
}
