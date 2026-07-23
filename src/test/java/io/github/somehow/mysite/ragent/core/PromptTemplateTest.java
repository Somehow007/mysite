package io.github.somehow.mysite.ragent.core;

import io.github.somehow.mysite.ragent.llm.model.ChatMessage;
import io.github.somehow.mysite.ragent.vector.VectorStore.SearchResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PromptTemplate — Prompt 模板")
class PromptTemplateTest {

    private final PromptTemplate template = new PromptTemplate();

    @Nested
    @DisplayName("RAG Prompt（有检索上下文）")
    class RagPrompt {

        @Test
        @DisplayName("应包含 system + user 消息")
        void shouldIncludeSystemAndUserMessage() {
            List<SearchResult> context = List.of(
                new SearchResult(1L, 100L, "Spring Security 实战",
                    "JWT 过滤器配置步骤...", 0.85f, 1L),
                new SearchResult(2L, 100L, "Spring Security 实战",
                    "OncePerRequestFilter 是...", 0.72f, 1L)
            );

            List<ChatMessage> messages = template.buildRagPrompt(
                "JWT 怎么配置？", context, List.of());

            assertEquals(2, messages.size());
            assertEquals("system", messages.get(0).getRole());
            assertEquals("user", messages.get(1).getRole());
            assertEquals("JWT 怎么配置？", messages.get(1).getContent());
        }

        @Test
        @DisplayName("system prompt 应包含来源标注和文章标题")
        void systemPromptShouldContainSources() {
            List<SearchResult> context = List.of(
                new SearchResult(1L, 100L, "Spring Security 实战",
                    "JWT 过滤器配置步骤...", 0.85f, 1L)
            );

            List<ChatMessage> messages = template.buildRagPrompt(
                "JWT 怎么配置？", context, List.of());

            String sys = messages.get(0).getContent();
            assertTrue(sys.contains("Spring Security 实战"),
                "system prompt 应包含文章标题");
            assertTrue(sys.contains("JWT 过滤器配置步骤"),
                "system prompt 应包含检索内容");
            assertTrue(sys.contains("[来源1]"),
                "system prompt 应标注来源编号");
            assertTrue(sys.contains("相关度: 0.85"),
                "system prompt 应包含相关度分数");
        }

        @Test
        @DisplayName("对话历史应插入 system 和 user 之间")
        void historyShouldBeBetweenSystemAndUser() {
            List<ChatMessage> history = List.of(
                ChatMessage.user("上一个问题"),
                ChatMessage.assistant("上一个回答")
            );

            List<ChatMessage> messages = template.buildRagPrompt(
                "新问题", List.of(), history);

            assertEquals(4, messages.size());
            assertEquals("system", messages.get(0).getRole());
            assertEquals("user", messages.get(1).getRole());
            assertEquals("上一个问题", messages.get(1).getContent());
            assertEquals("assistant", messages.get(2).getRole());
            assertEquals("user", messages.get(3).getRole());
            assertEquals("新问题", messages.get(3).getContent());
        }
    }

    @Nested
    @DisplayName("通用 Prompt（无检索结果）")
    class GeneralPrompt {

        @Test
        @DisplayName("应简洁不标注来源")
        void shouldBeConciseAndNotReferenceSources() {
            List<ChatMessage> messages = template.buildGeneralPrompt(
                "今天天气怎么样？", List.of());

            assertEquals(2, messages.size());
            assertEquals("system", messages.get(0).getRole());
            assertFalse(messages.get(0).getContent().contains("[来源"),
                "通用模式不应包含来源标注");
            assertTrue(messages.get(0).getContent().contains("博客"),
                "应提及博客身份");
        }

        @Test
        @DisplayName("空上下文 → RAG Prompt 仍然可生成（使用示例标题）")
        void emptyContextShouldStillGeneratePrompt() {
            List<ChatMessage> messages = template.buildRagPrompt(
                "测试问题", List.of(), List.of());

            assertEquals(2, messages.size());
            assertEquals("system", messages.get(0).getRole());
            assertTrue(messages.get(0).getContent().contains("xxx"),
                "空上下文时应使用占位标题");
        }
    }
}
