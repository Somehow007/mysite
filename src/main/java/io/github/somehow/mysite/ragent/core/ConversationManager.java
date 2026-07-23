package io.github.somehow.mysite.ragent.core;

import com.alibaba.fastjson2.JSON;
import io.github.somehow.mysite.ragent.config.RagProperties;
import io.github.somehow.mysite.ragent.dao.entity.ConversationDO;
import io.github.somehow.mysite.ragent.dao.entity.ConversationMessageDO;
import io.github.somehow.mysite.ragent.dao.mapper.ConversationMapper;
import io.github.somehow.mysite.ragent.dao.mapper.ConversationMessageMapper;
import io.github.somehow.mysite.ragent.dto.SourceChunkDTO;
import io.github.somehow.mysite.ragent.llm.model.ChatMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 对话记忆管理
 *
 * 记忆策略（渐进式）：
 *  Phase 3 实现：
 *      - 滑动窗口：保留最近 N 轮（默认 6 轮 = 12 条消息）
 *      - 超出窗口不加载
 *  Phase 4 可加（后期优化）：
 *      - 摘要压缩：当消息超过阈值时，让 LLM 把早期对话总结成一段话
 *        例如：“用户之前问了关于 Spring Security 的问题，我建议他使用 JWT 认证方案...”
 *        这段话作为 system message 注入，节省 token。
 */
@Component
@RequiredArgsConstructor
public class ConversationManager {

    private final ConversationMapper conversationMapper;
    private final ConversationMessageMapper messageMapper;
    private final RagProperties properties;

    /**
     * 加载对话历史（最近 N 轮）
     *
     * @param conversationId    会话 ID
     * @return  ChatMessage 列表，可直接放入 LLM 请求的 messages 数组中
     */
    public List<ChatMessage> loadHistory(Long conversationId) {
        if (conversationId == null) return List.of();

        int keepTurns = properties.getMemory().getKeepTurns();  // 6
        List<ConversationMessageDO> recentMessages = messageMapper
                .selectRecent(conversationId, keepTurns * 2);

        return recentMessages.stream()
                // 注意：selectRecent 按时间倒序取最近 N 条，
                // 装入 messages 前必须翻正为时间正序，否则 LLM 看到的对话是反的
                .sorted(Comparator.comparing(ConversationMessageDO::getCreateTime))
                .map(msg -> {
                    if ("USER".equals(msg.getRole())) {
                        return ChatMessage.user(msg.getContent());
                    } else {
                        return ChatMessage.assistant(msg.getContent());
                    }
                })
                .toList();
    }


    /**
     * 获取或创建会话
     *
     * @param conversationId     前端带来的会话 ID（null = 新会话）
     * @param visitorId          匿名访客标识（前端 localStorage UUID）
     * @param firstQuestion      新会话用它生成标题
     */
    public ConversationDO getOrCreateConversation(Long conversationId, String visitorId, String firstQuestion) {
        if (conversationId != null) {
            ConversationDO existing = conversationMapper.selectById(conversationId);
            // 防 IDOR：会话必须属于这个 visitor，否则视为新会话
            if (existing != null && Objects.equals(existing.getVisitorId(), visitorId)) {
                return existing;
            }
        }
        ConversationDO conv = new ConversationDO();
        conv.setVisitorId(visitorId);
        conv.setTitle(firstQuestion.length() > 20 ? firstQuestion.substring(0, 20) + "…" : firstQuestion);
        conversationMapper.insert(conv);
        return conv;
    }

    /**
     * 保存一轮问答（流式生成完成后调用）
     * 注意：这是阻塞 JDBC，应在 boundedElastic / ragAsyncExecutor 上执行，
     * 不要占用 reactor 的 event-loop 线程。
     */
    public void saveExchange(Long conversationId, String question,
                             String answer, List<SourceChunkDTO> sources) {
        ConversationMessageDO userMsg = new ConversationMessageDO();
        userMsg.setConversationId(conversationId);
        userMsg.setRole("USER");
        userMsg.setContent(question);
        messageMapper.insert(userMsg);

        ConversationMessageDO assistantMsg = new ConversationMessageDO();
        assistantMsg.setConversationId(conversationId);
        assistantMsg.setRole("ASSISTANT");
        assistantMsg.setContent(answer);
        assistantMsg.setSources(JSON.toJSONString(sources));
        messageMapper.insert(assistantMsg);

        // 更新会话的消息数与 update_time（PG 没有 ON UPDATE，应用层维护）
        conversationMapper.touchMessageCount(conversationId, 2);
    }

}
