package io.github.somehow.mysite.ragent.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.somehow.mysite.ragent.dao.entity.ConversationDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ConversationMapper extends BaseMapper<ConversationDO> {

    @Select("SELECT * FROM t_conversation WHERE visitor_id = #{visitorId} ORDER BY update_time DESC")
    java.util.List<ConversationDO> selectByVisitorId(@Param("visitorId") String visitorId);

    @Select("SELECT * FROM t_conversation WHERE user_id = #{userId} ORDER BY update_time DESC")
    java.util.List<ConversationDO> selectByUserId(@Param("userId") Long userId);
}
