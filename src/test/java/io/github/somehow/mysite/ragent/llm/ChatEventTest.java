package io.github.somehow.mysite.ragent.llm;

import io.github.somehow.mysite.ragent.dto.SourceChunkDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ChatEvent 单元测试。
 *
 * 验证 5 种事件工厂方法的正确性，以及 JSON 序列化/反序列化兼容。
 */
@DisplayName("ChatEvent — SSE 事件模型")
class ChatEventTest {

    // ==================== 工厂方法 ====================

    @Nested
    @DisplayName("工厂方法")
    class FactoryMethods {

        @Test
        @DisplayName("meta → type=meta, conversationId=非空, 其他字段为 null")
        void metaEventShouldHaveCorrectFields() {
            ChatEvent event = ChatEvent.meta(12345L);

            assertEquals("meta", event.type());
            assertEquals(12345L, event.conversationId());
            assertNull(event.delta());
            assertNull(event.sources());
            assertNull(event.message());
        }

        @Test
        @DisplayName("sources → type=sources, sources=非空列表")
        void sourcesEventShouldHaveCorrectFields() {
            List<SourceChunkDTO> sources = List.of(
                new SourceChunkDTO("文章A", "内容片段", 0.95f),
                new SourceChunkDTO("文章B", "另一片段", 0.82f)
            );

            ChatEvent event = ChatEvent.sources(sources);

            assertEquals("sources", event.type());
            assertEquals(2, event.sources().size());
            assertEquals("文章A", event.sources().get(0).getTitle());
            assertEquals(0.95f, event.sources().get(0).getScore(), 0.001f);
            assertNull(event.delta());
            assertNull(event.conversationId());
        }

        @Test
        @DisplayName("sources 空列表 → type=sources, sources=[]")
        void sourcesEventWithEmptyList() {
            ChatEvent event = ChatEvent.sources(List.of());

            assertEquals("sources", event.type());
            assertNotNull(event.sources());
            assertTrue(event.sources().isEmpty());
        }

        @Test
        @DisplayName("content → type=content, delta=非空")
        void contentEventShouldHaveCorrectFields() {
            ChatEvent event = ChatEvent.content("你好");

            assertEquals("content", event.type());
            assertEquals("你好", event.delta());
            assertNull(event.sources());
            assertNull(event.conversationId());
        }

        @Test
        @DisplayName("content 空字符串 → 允许（某些 token 可能是空）")
        void contentEventWithEmptyDelta() {
            ChatEvent event = ChatEvent.content("");

            assertEquals("content", event.type());
            assertEquals("", event.delta());
        }

        @Test
        @DisplayName("done → type=done, 其他字段为 null")
        void doneEventShouldHaveCorrectFields() {
            ChatEvent event = ChatEvent.done();

            assertEquals("done", event.type());
            assertNull(event.delta());
            assertNull(event.sources());
            assertNull(event.conversationId());
            assertNull(event.message());
        }

        @Test
        @DisplayName("error → type=error, message=非空")
        void errorEventShouldHaveCorrectFields() {
            ChatEvent event = ChatEvent.error("AI 服务暂时不可用");

            assertEquals("error", event.type());
            assertEquals("AI 服务暂时不可用", event.message());
            assertNull(event.delta());
            assertNull(event.sources());
            assertNull(event.conversationId());
        }
    }

    // ==================== 序列化兼容性 ====================

    @Nested
    @DisplayName("JSON 序列化兼容")
    class JsonCompatibility {

        @Test
        @DisplayName("所有事件类型都能被正确反序列化")
        void allEventTypesShouldDeserialize() throws Exception {
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                new com.fasterxml.jackson.databind.ObjectMapper();

            // meta
            String metaJson = mapper.writeValueAsString(ChatEvent.meta(999L));
            ChatEvent parsedMeta = mapper.readValue(metaJson, ChatEvent.class);
            assertEquals("meta", parsedMeta.type());
            assertEquals(999L, parsedMeta.conversationId());

            // content
            String contentJson = mapper.writeValueAsString(ChatEvent.content("Hello"));
            ChatEvent parsedContent = mapper.readValue(contentJson, ChatEvent.class);
            assertEquals("content", parsedContent.type());
            assertEquals("Hello", parsedContent.delta());

            // done
            String doneJson = mapper.writeValueAsString(ChatEvent.done());
            ChatEvent parsedDone = mapper.readValue(doneJson, ChatEvent.class);
            assertEquals("done", parsedDone.type());

            // error
            String errorJson = mapper.writeValueAsString(ChatEvent.error("出错了"));
            ChatEvent parsedError = mapper.readValue(errorJson, ChatEvent.class);
            assertEquals("error", parsedError.type());
            assertEquals("出错了", parsedError.message());
        }

        @Test
        @DisplayName("content 事件 JSON 不含 sources/conversationId 字段")
        void contentEventJsonShouldBeCompact() throws Exception {
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                new com.fasterxml.jackson.databind.ObjectMapper();

            String json = mapper.writeValueAsString(ChatEvent.content("Hi"));

            assertTrue(json.contains("\"type\":\"content\""));
            assertTrue(json.contains("\"delta\":\"Hi\""));
            // null 字段应该被序列化为 null
            assertTrue(json.contains("\"sources\":null"));
            assertTrue(json.contains("\"conversationId\":null"));
        }
    }
}
