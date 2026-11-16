package io.github.somehow.mysite.ragent.core;

import io.github.somehow.mysite.commons.framework.errorcode.ErrorCode;
import io.github.somehow.mysite.commons.framework.exception.ClientException;
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
    @DisplayName("isOwner — 归属判定（user_id 主 / visitor_id 兜底）")
    class IsOwner {

        private ConversationDO conv(Long userId, String visitorId) {
            ConversationDO c = new ConversationDO();
            c.setId(1L);
            c.setUserId(userId);
            c.setVisitorId(visitorId);
            return c;
        }

        @Test
        @DisplayName("已认领会话：userId 匹配即归属，与 visitorId 无关（跨设备可见）")
        void claimedConvMatchesByUserIdOnly() {
            assertTrue(ConversationManager.isOwner(conv(42L, "v-a"), 42L, "v-b"));
            assertTrue(ConversationManager.isOwner(conv(42L, "v-a"), 42L, null));
        }

        @Test
        @DisplayName("已认领会话：visitorId 匹配但 userId 不同 → 非归属（凭证失效）")
        void claimedConvRejectsVisitorImpersonation() {
            assertFalse(ConversationManager.isOwner(conv(42L, "v-a"), 7L, "v-a"));
            assertFalse(ConversationManager.isOwner(conv(42L, "v-a"), null, "v-a"));
        }

        @Test
        @DisplayName("未认领会话：visitorId 非空匹配 → 归属")
        void unclaimedConvMatchesByVisitor() {
            assertTrue(ConversationManager.isOwner(conv(null, "v-a"), 42L, "v-a"));
            assertTrue(ConversationManager.isOwner(conv(null, "v-a"), null, "v-a"));
        }

        @Test
        @DisplayName("未认领会话：visitorId 缺失/不匹配 → 非归属")
        void unclaimedConvRejectsMissingOrWrongVisitor() {
            assertFalse(ConversationManager.isOwner(conv(null, "v-a"), 42L, null));
            assertFalse(ConversationManager.isOwner(conv(null, "v-a"), 42L, "v-b"));
            assertFalse(ConversationManager.isOwner(conv(null, null), 42L, "v-a"));
        }
    }

    @Nested
    @DisplayName("getOrCreateConversation — 获取/创建会话")
    class GetOrCreateConversation {

        @Test
        @DisplayName("null id → 创建新会话（user_id + visitor_id 双写）")
        void nullIdShouldCreateNew() {
            manager.getOrCreateConversation(null, "visitor-1", 42L, "你好世界");
            verify(conversationMapper).insert(
                org.mockito.Mockito.<ConversationDO>argThat(c ->
                    Long.valueOf(42L).equals(c.getUserId())
                        && "visitor-1".equals(c.getVisitorId())));
        }

        @Test
        @DisplayName("未认领会话 visitorId 不匹配 → 视为新会话（防 IDOR）")
        void mismatchedVisitorShouldCreateNew() {
            ConversationDO existing = new ConversationDO();
            existing.setId(10L);
            existing.setVisitorId("other-visitor");

            when(conversationMapper.selectById(10L)).thenReturn(existing);

            manager.getOrCreateConversation(10L, "visitor-1", null, "问题");

            // 不应返回别人的会话
            verify(conversationMapper).insert(any(ConversationDO.class));
        }

        @Test
        @DisplayName("未认领会话 visitorId 匹配 → 返回已有会话")
        void matchingVisitorShouldReturnExisting() {
            ConversationDO existing = new ConversationDO();
            existing.setId(10L);
            existing.setVisitorId("visitor-1");

            when(conversationMapper.selectById(10L)).thenReturn(existing);

            ConversationDO result = manager.getOrCreateConversation(10L, "visitor-1", null, "问题");

            assertEquals(10L, result.getId());
        }

        @Test
        @DisplayName("已认领会话 userId 匹配（visitorId 不同）→ 返回已有会话（跨设备）")
        void claimedConvMatchesByUserIdAcrossDevices() {
            ConversationDO existing = new ConversationDO();
            existing.setId(10L);
            existing.setUserId(42L);
            existing.setVisitorId("browser-a");

            when(conversationMapper.selectById(10L)).thenReturn(existing);

            ConversationDO result = manager.getOrCreateConversation(10L, "browser-b", 42L, "问题");

            assertEquals(10L, result.getId());
            verify(conversationMapper, never()).insert(any(ConversationDO.class));
        }

        @Test
        @DisplayName("已认领会话 visitorId 匹配但 userId 不同 → 视为新会话（冒名无效）")
        void claimedConvRejectsVisitorImpersonation() {
            ConversationDO existing = new ConversationDO();
            existing.setId(10L);
            existing.setUserId(42L);
            existing.setVisitorId("shared-browser");

            when(conversationMapper.selectById(10L)).thenReturn(existing);

            manager.getOrCreateConversation(10L, "shared-browser", 7L, "问题");

            verify(conversationMapper).insert(any(ConversationDO.class));
        }

        @Test
        @DisplayName("未认领会话续聊 + userId 非空 → lazy claim 写入 user_id")
        void unclaimedConvShouldClaimOnContinue() {
            ConversationDO existing = new ConversationDO();
            existing.setId(10L);
            existing.setVisitorId("visitor-1");

            when(conversationMapper.selectById(10L)).thenReturn(existing);

            ConversationDO result = manager.getOrCreateConversation(10L, "visitor-1", 42L, "问题");

            assertEquals(10L, result.getId());
            assertEquals(42L, result.getUserId());
            verify(conversationMapper).claimUserId(10L, 42L);
        }

        @Test
        @DisplayName("未认领会话续聊 + userId 为空 → 不 claim")
        void unclaimedConvWithoutUserIdShouldNotClaim() {
            ConversationDO existing = new ConversationDO();
            existing.setId(10L);
            existing.setVisitorId("visitor-1");

            when(conversationMapper.selectById(10L)).thenReturn(existing);

            manager.getOrCreateConversation(10L, "visitor-1", null, "问题");

            verify(conversationMapper, never()).claimUserId(any(), any());
        }

        @Test
        @DisplayName("已认领会话续聊 → 不重复 claim")
        void claimedConvShouldNotReclaim() {
            ConversationDO existing = new ConversationDO();
            existing.setId(10L);
            existing.setUserId(42L);
            existing.setVisitorId("visitor-1");

            when(conversationMapper.selectById(10L)).thenReturn(existing);

            manager.getOrCreateConversation(10L, "visitor-1", 42L, "问题");

            verify(conversationMapper, never()).claimUserId(any(), any());
        }

        @Test
        @DisplayName("新会话标题 = 问题前 20 字 + …")
        void newConversationTitleShouldTruncate() {
            String longQ = "这是一个很长很长的问题用来测试标题截断功能";
            manager.getOrCreateConversation(null, "v", null, longQ);
            verify(conversationMapper).insert(
                org.mockito.Mockito.<ConversationDO>argThat(c ->
                    c.getTitle() != null && c.getTitle().endsWith("…")
                        && c.getTitle().length() <= 23));
        }
    }

    @Nested
    @DisplayName("requireOwnedConversation / rename / delete — 写路径归属")
    class WritePath {

        private ConversationDO ownedConv() {
            ConversationDO conv = new ConversationDO();
            conv.setId(10L);
            conv.setUserId(42L);
            conv.setVisitorId("v-1");
            return conv;
        }

        @Test
        @DisplayName("requireOwned：会话不存在 → A120100")
        void requireOwned_missing_shouldThrow() {
            when(conversationMapper.selectById(10L)).thenReturn(null);
            ClientException e = assertThrows(ClientException.class,
                () -> manager.requireOwnedConversation(10L, 42L, "v-1"));
            assertEquals(ErrorCode.RAG_CONVERSATION_NOT_FOUND.code(), e.errorCode);
        }

        @Test
        @DisplayName("requireOwned：非属主 → A120100（不区分不存在）")
        void requireOwned_nonOwner_shouldThrow() {
            ConversationDO conv = new ConversationDO();
            conv.setId(10L);
            conv.setUserId(42L);   // 属于 42
            conv.setVisitorId("v-1");
            when(conversationMapper.selectById(10L)).thenReturn(conv);

            ClientException e = assertThrows(ClientException.class,
                () -> manager.requireOwnedConversation(10L, 7L, "v-1"));
            assertEquals(ErrorCode.RAG_CONVERSATION_NOT_FOUND.code(), e.errorCode);
        }

        @Test
        @DisplayName("rename：属主 + 合法标题 → renameTitle")
        void rename_owner_shouldUpdate() {
            when(conversationMapper.selectById(10L)).thenReturn(ownedConv());

            manager.renameConversation(10L, 42L, "v-1", "  新标题  ");

            verify(conversationMapper).renameTitle(10L, "新标题");
        }

        @Test
        @DisplayName("rename：空标题 → A120101")
        void rename_blankTitle_shouldThrow() {
            when(conversationMapper.selectById(10L)).thenReturn(ownedConv());

            ClientException e = assertThrows(ClientException.class,
                () -> manager.renameConversation(10L, 42L, "v-1", "   "));
            assertEquals(ErrorCode.RAG_CONVERSATION_TITLE_REQUIRED.code(), e.errorCode);
            verify(conversationMapper, never()).renameTitle(any(), any());
        }

        @Test
        @DisplayName("rename：超 200 字 → A120102")
        void rename_tooLong_shouldThrow() {
            when(conversationMapper.selectById(10L)).thenReturn(ownedConv());

            ClientException e = assertThrows(ClientException.class,
                () -> manager.renameConversation(10L, 42L, "v-1", "长".repeat(201)));
            assertEquals(ErrorCode.RAG_CONVERSATION_TITLE_TOO_LONG.code(), e.errorCode);
        }

        @Test
        @DisplayName("rename：非属主 → A120100，不写库")
        void rename_nonOwner_shouldThrow() {
            ConversationDO conv = new ConversationDO();
            conv.setId(10L);
            conv.setUserId(42L);
            conv.setVisitorId("v-1");
            when(conversationMapper.selectById(10L)).thenReturn(conv);

            assertThrows(ClientException.class,
                () -> manager.renameConversation(10L, 7L, "v-1", "新标题"));
            verify(conversationMapper, never()).renameTitle(any(), any());
        }

        @Test
        @DisplayName("delete：属主 → 先删消息再删会话")
        void delete_owner_shouldDeleteMessagesThenConv() {
            when(conversationMapper.selectById(10L)).thenReturn(ownedConv());

            manager.deleteConversation(10L, 42L, "v-1");

            var order = inOrder(messageMapper, conversationMapper);
            order.verify(messageMapper).deleteByConversationId(10L);
            order.verify(conversationMapper).deleteById(10L);
        }

        @Test
        @DisplayName("delete：非属主 → A120100，不删任何数据")
        void delete_nonOwner_shouldThrow() {
            ConversationDO conv = new ConversationDO();
            conv.setId(10L);
            conv.setUserId(42L);
            conv.setVisitorId("v-1");
            when(conversationMapper.selectById(10L)).thenReturn(conv);

            assertThrows(ClientException.class,
                () -> manager.deleteConversation(10L, 7L, "v-1"));
            verify(messageMapper, never()).deleteByConversationId(any());
            // 生产代码走 deleteById(Serializable) 重载（Long id）
            verify(conversationMapper, never()).deleteById(anyLong());
        }
    }

    @Nested
    @DisplayName("saveExchange — 保存问答")
    class SaveExchange {

        @Test
        @DisplayName("应保存 user + assistant 两条消息")
        void shouldSaveBothMessages() {
            List<SourceChunkDTO> sources = List.of(
                new SourceChunkDTO("文章", "片段", 0.9f, null, null, null));

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
                new SourceChunkDTO("文章A", "内容", 0.85f, null, null, null));

            manager.saveExchange(1L, "问", "答", sources);

            verify(messageMapper).insert(
                org.mockito.Mockito.<ConversationMessageDO>argThat(msg ->
                    "ASSISTANT".equals(msg.getRole()) && msg.getSources() != null));
        }
    }
}
