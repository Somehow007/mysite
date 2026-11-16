package io.github.somehow.mysite.ragent.core;

import com.alibaba.fastjson2.JSON;
import io.github.somehow.mysite.commons.framework.errorcode.ErrorCode;
import io.github.somehow.mysite.commons.framework.exception.ClientException;
import io.github.somehow.mysite.ragent.config.RagProperties;
import io.github.somehow.mysite.ragent.dao.entity.ConversationDO;
import io.github.somehow.mysite.ragent.dao.entity.ConversationMessageDO;
import io.github.somehow.mysite.ragent.dao.mapper.ConversationMapper;
import io.github.somehow.mysite.ragent.dao.mapper.ConversationMessageMapper;
import io.github.somehow.mysite.ragent.dto.SourceChunkDTO;
import io.github.somehow.mysite.ragent.llm.model.ChatMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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
 *
 * 会话归属模型（user_id 主、visitor_id 兜底）：
 *      - user_id 非空 → 归属只看账号，visitorId 不再是凭证（已认领会话冒用 visitorId 无效）
 *      - user_id 为空 → 存量会话，visitor_id 匹配兜底；续聊时自动 claim 到当前账号
 */
@Component
@RequiredArgsConstructor
public class ConversationManager {

    /** 会话标题列宽（t_conversation.title VARCHAR(200)） */
    private static final int TITLE_MAX_LENGTH = 200;

    private final ConversationMapper conversationMapper;
    private final ConversationMessageMapper messageMapper;
    private final RagProperties properties;

    /**
     * 会话归属判定（单一事实源，读/写路径共用）。
     *
     * @param conv      会话
     * @param userId    当前登录用户 ID（可为 null，匿名兜底分支仍要求 visitorId 非空匹配）
     * @param visitorId 浏览器 UUID 凭证
     */
    public static boolean isOwner(ConversationDO conv, Long userId, String visitorId) {
        if (conv.getUserId() != null) {
            // 已认领：只认账号。visitor_id 可空/可被共享浏览器复用，不再作为凭证
            return userId != null && conv.getUserId().equals(userId);
        }
        // 未认领存量：visitor_id 兜底，双方都须非空才匹配（visitor_id 列可空）
        return visitorId != null && !visitorId.isBlank()
                && Objects.equals(conv.getVisitorId(), visitorId);
    }

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
     * @param visitorId          会话归属标识（前端 localStorage UUID）
     * @param userId             登录用户 ID（写入 user_id，存量会话续聊时认领）
     * @param firstQuestion      新会话用它生成标题
     */
    public ConversationDO getOrCreateConversation(Long conversationId, String visitorId,
                                                  Long userId, String firstQuestion) {
        if (conversationId != null) {
            ConversationDO existing = conversationMapper.selectById(conversationId);
            // 防 IDOR：会话必须归属当前用户（user_id 主 / visitor_id 兜底），否则视为新会话
            if (existing != null && isOwner(existing, userId, visitorId)) {
                // 存量会话续聊即认领：user_id 落定后 visitorId 不再是凭证。
                // claimUserId 带 user_id IS NULL 条件，并发下先到先得。
                if (existing.getUserId() == null && userId != null) {
                    conversationMapper.claimUserId(existing.getId(), userId);
                    existing.setUserId(userId);
                }
                return existing;
            }
        }
        ConversationDO conv = new ConversationDO();
        conv.setUserId(userId);
        conv.setVisitorId(visitorId);
        conv.setTitle(autoTitle(firstQuestion));
        conversationMapper.insert(conv);
        return conv;
    }

    /**
     * 取归属校验后的会话；不存在或非本人 → 抛「会话不存在」（不区分两种情形，
     * 不向调用方泄露会话是否存在）。写路径（重命名/删除）专用。
     */
    public ConversationDO requireOwnedConversation(Long conversationId, Long userId, String visitorId) {
        ConversationDO conv = conversationId == null ? null : conversationMapper.selectById(conversationId);
        if (conv == null || !isOwner(conv, userId, visitorId)) {
            throw new ClientException(ErrorCode.RAG_CONVERSATION_NOT_FOUND);
        }
        return conv;
    }

    /**
     * 重命名会话（归属校验在 requireOwnedConversation 内完成）。
     * 标题 trim 后为空 → 参数错误；超长 → 截断至列宽。
     */
    public void renameConversation(Long conversationId, Long userId, String visitorId, String title) {
        requireOwnedConversation(conversationId, userId, visitorId);
        String trimmed = title == null ? "" : title.trim();
        if (trimmed.isEmpty()) {
            throw new ClientException(ErrorCode.RAG_CONVERSATION_TITLE_REQUIRED);
        }
        if (trimmed.length() > TITLE_MAX_LENGTH) {
            throw new ClientException(ErrorCode.RAG_CONVERSATION_TITLE_TOO_LONG);
        }
        conversationMapper.renameTitle(conversationId, trimmed);
    }

    /**
     * 删除会话及其全部消息。同事务：t_conversation_message.conversation_id
     * 外键无级联，必须先删消息再删会话。
     */
    @Transactional("ragentTransactionManager")
    public void deleteConversation(Long conversationId, Long userId, String visitorId) {
        requireOwnedConversation(conversationId, userId, visitorId);
        messageMapper.deleteByConversationId(conversationId);
        conversationMapper.deleteById(conversationId);
    }

    /** 新会话自动标题：首问前 20 字 + 省略号（沿用原有口径） */
    private static String autoTitle(String question) {
        if (question == null) return "";
        return question.length() > 20 ? question.substring(0, 20) + "…" : question;
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
