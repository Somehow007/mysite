package io.github.somehow.mysite.ragent.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.somehow.mysite.ragent.dao.entity.ConversationDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ConversationMapper extends BaseMapper<ConversationDO> {

    @Select("SELECT * FROM t_conversation WHERE visitor_id = #{visitorId} ORDER BY update_time DESC")
    List<ConversationDO> selectByVisitorId(@Param("visitorId") String visitorId);

    @Select("SELECT * FROM t_conversation WHERE user_id = #{userId} ORDER BY update_time DESC")
    List<ConversationDO> selectByUserId(@Param("userId") Long userId);

    /**
     * 当前用户可见的会话：已认领到账号的（user_id 匹配）+ 未认领存量中
     * visitor_id 匹配的兜底。任一参数为 null 时对应分支自然不命中。
     */
    @Select("SELECT * FROM t_conversation WHERE user_id = #{userId} " +
            "OR (user_id IS NULL AND visitor_id = #{visitorId}) ORDER BY update_time DESC")
    List<ConversationDO> selectVisibleTo(@Param("userId") Long userId, @Param("visitorId") String visitorId);

    /**
     * 存量会话认领：仅当 user_id 仍为空时写入（并发下先到先得，后到 0 行）。
     */
    @Update("UPDATE t_conversation SET user_id = #{userId} WHERE id = #{id} AND user_id IS NULL")
    int claimUserId(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * 重命名：只动 title，不碰 update_time——改名不是新活动，不应改变历史排序。
     */
    @Update("UPDATE t_conversation SET title = #{title} WHERE id = #{id}")
    int renameTitle(@Param("id") Long id, @Param("title") String title);

    /**
     * 更新消息计数和更新时间（PG 没有 ON UPDATE，应用层维护）。
     * delta > 0 时递增，避免 SELECT-then-UPDATE 的竞态窗口
     */
    @Update("UPDATE t_conversation SET message_count = message_count + #{delta}, " +
            "update_time = CURRENT_TIMESTAMP WHERE id = #{conversationId}")
    int touchMessageCount(@Param("conversationId") Long conversationId, @Param("delta") int delta);
}
