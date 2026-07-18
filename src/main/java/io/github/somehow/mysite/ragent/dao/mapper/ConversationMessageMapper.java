package io.github.somehow.mysite.ragent.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.somehow.mysite.ragent.dao.entity.ConversationMessageDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ConversationMessageMapper extends BaseMapper<ConversationMessageDO> {

    @Select("SELECT * FROM t_conversation_message WHERE conversation_id = #{conversationId} ORDER BY create_time ASC")
    List<ConversationMessageDO> selectByConversationId(@Param("conversationId") Long conversationId);

    @Select("SELECT * FROM t_conversation_message WHERE conversation_id = #{conversationId} ORDER BY create_time DESC LIMIT #{limit}")
    List<ConversationMessageDO> selectRecent(@Param("conversationId") Long conversationId, @Param("limit") int limit);
}
