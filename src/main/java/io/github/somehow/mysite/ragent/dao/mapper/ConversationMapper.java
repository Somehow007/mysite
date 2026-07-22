package io.github.somehow.mysite.ragent.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.somehow.mysite.ragent.dao.entity.ConversationDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ConversationMapper extends BaseMapper<ConversationDO> {

    @Select("SELECT * FROM t_conversation WHERE visitor_id = #{visitorId} ORDER BY update_time DESC")
    java.util.List<ConversationDO> selectByVisitorId(@Param("visitorId") String visitorId);

    @Select("SELECT * FROM t_conversation WHERE user_id = #{userId} ORDER BY update_time DESC")
    java.util.List<ConversationDO> selectByUserId(@Param("userId") Long userId);

    /**
     * 更新消息计数和更新时间（PG 没有 ON UPDATE，应用层维护）。
     * delta > 0 时递增，避免 SELECT-then-UPDATE 的竞态窗口
     */
    @Update("UPDATE t_conversation SET message_count = message_count + #{delta}, " +
            "update_time = CURRENT_TIMESTAMP WHERE id = #{conversationId}")
    int touchMessageCount(@Param("conversationId") Long conversationId, @Param("delta") int delta);
}
