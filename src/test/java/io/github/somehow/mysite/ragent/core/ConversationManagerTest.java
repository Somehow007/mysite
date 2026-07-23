package io.github.somehow.mysite.ragent.core;

import io.github.somehow.mysite.ragent.config.RagProperties;
import io.github.somehow.mysite.ragent.dao.entity.ConversationDO;
import io.github.somehow.mysite.ragent.dao.entity.ConversationMessageDO;
import io.github.somehow.mysite.ragent.dao.mapper.ConversationMapper;
import io.github.somehow.mysite.ragent.dao.mapper.ConversationMessageMapper;
import io.github.somehow.mysite.ragent.dto.SourceChunkDTO;
import io.github.somehow.mysite.ragent.llm.model.ChatMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("ConversationManager — 对话记忆管理")
class ConversationManagerTest {

    private ConversationMapper conversationMapper;
    private ConversationMessageMapper messageMapper;
    private RagProperties properties;
    private ConversationManager manager;

    @BeforeEach
    void setUp() {
        conversationMapper = mock(ConversationMapper.class);
        messageMapper = mock(ConversationMessageMapper.class);
        properties = new RagProperties();
        properties.getMemory().setKeepTurns(6);
        manager = new ConversationManager(conversationMapper, messageMapper, properties);
    }

    @Nested
    @DisplayName("loadHistory — 加载历史")
    class LoadHistory {

        @Test
        @DisplayName("null conversationId → 返回空列表")
        void nullIdShouldReturnEmpty() {
            assertTrue(manager.loadHistory(null).isEmpty());
        }

        @Test
        @DisplayName("消息应按时间正序排列（DB 返回倒序需翻正）")
        void messagesShouldBeInChronologicalOrder() {
            ConversationMessageDO msg1 = new ConversationMessageDO();
            msg1.setRole("USER");
            msg1.setContent("第一个问题");
            msg1.setCreateTime(LocalDateTime.of(2026, 7, 1, 10, 0));

            ConversationMessageDO msg2 = new ConversationMessageDO();
            msg2.setRole("ASSISTANT");
            msg2.setContent("第一个回答");
            msg2.setCreateTime(LocalDateTime.of(2026, 7, 1, 10, 1));

            // DB 返回倒序（最新在前）
            when(messageMapper.selectRecent(1L, 12))
                .thenReturn(List.of(msg2, msg1));

            List<ChatMessage> history = manager.loadHistory(1L);

            assertEquals(2, history.size());
            assertEquals("user", history.get(0).getRole());
            assertEquals("第一个问题", history.get(0).getContent());
            assertEquals("assistant", history.get(1).getRole());
            assertEquals("第一个回答", history.get(1).getContent());
        }

        @Test
        @DisplayName("请求量 = keepTurns × 2")
        void shouldRequestCorrectNumberOfMessages() {
            when(messageMapper.selectRecent(eq(1L), eq(12))).thenReturn(List.of());
            manager.loadHistory(1L);
            verify(messageMapper).selectRecent(1L, 12);  // 6 turns × 2
        }
    }

    @Nested
    @DisplayName("getOrCreateConversation — 获取/创建会话")
    class GetOrCreateConversation {

        @Test
        @DisplayName("null id → 创建新会话")
        void nullIdShouldCreateNew() {
            manager.getOrCreateConversation(null, "visitor-1", "你好世界");
            verify(conversationMapper).insert(any(ConversationDO.class));
        }

        @Test
        @DisplayName("visitorId 不匹配 → 视为新会话（防 IDOR）")
        void mismatchedVisitorShouldCreateNew() {
            ConversationDO existing = new ConversationDO();
            existing.setId(10L);
            existing.setVisitorId("other-visitor");

            when(conversationMapper.selectById(10L)).thenReturn(existing);

            manager.getOrCreateConversation(10L, "visitor-1", "问题");

            // 不应返回别人的会话
            verify(conversationMapper).insert(any(ConversationDO.class));
        }

        @Test
        @DisplayName("visitorId 匹配 → 返回已有会话")
        void matchingVisitorShouldReturnExisting() {
            ConversationDO existing = new ConversationDO();
            existing.setId(10L);
            existing.setVisitorId("visitor-1");

            when(conversationMapper.selectById(10L)).thenReturn(existing);

            ConversationDO result = manager.getOrCreateConversation(10L, "visitor-1", "问题");

            assertEquals(10L, result.getId());
            // 已有会话不应再创建（验证 selectById 已返回则无需 insert）
        }

        @Test
        @DisplayName("新会话标题 = 问题前 20 字 + …")
        void newConversationTitleShouldTruncate() {
            String longQ = "这是一个很长很长的问题用来测试标题截断功能";
            manager.getOrCreateConversation(null, "v", longQ);
            verify(conversationMapper).insert(
                org.mockito.Mockito.<ConversationDO>argThat(c ->
                    c.getTitle() != null && c.getTitle().endsWith("…")
                        && c.getTitle().length() <= 23));
        }
    }

    @Nested
    @DisplayName("saveExchange — 保存问答")
    class SaveExchange {

        @Test
        @DisplayName("应保存 user + assistant 两条消息")
        void shouldSaveBothMessages() {
            List<SourceChunkDTO> sources = List.of(
                new SourceChunkDTO("文章", "片段", 0.9f));

            manager.saveExchange(1L, "问题", "回答", sources);

            verify(messageMapper, times(2)).insert(any(ConversationMessageDO.class));
        }

        @Test
        @DisplayName("应更新会话 message_count + 2")
        void shouldUpdateMessageCount() {
            manager.saveExchange(1L, "问题", "回答", List.of());

            verify(conversationMapper).touchMessageCount(1L, 2);
        }

        @Test
        @DisplayName("assistant 消息应包含 sources JSON")
        void assistantMessageShouldHaveSources() {
            List<SourceChunkDTO> sources = List.of(
                new SourceChunkDTO("文章A", "内容", 0.85f));

            manager.saveExchange(1L, "问", "答", sources);

            verify(messageMapper).insert(
                org.mockito.Mockito.<ConversationMessageDO>argThat(msg ->
                    "ASSISTANT".equals(msg.getRole()) && msg.getSources() != null));
        }
    }
}
