package io.github.somehow.mysite.ragent.core.intent;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.somehow.mysite.ragent.dao.entity.IntentDO;
import io.github.somehow.mysite.ragent.dao.mapper.IntentMapper;
import io.github.somehow.mysite.ragent.llm.LLMService;
import io.github.somehow.mysite.ragent.llm.model.ChatRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("IntentClassifier — 三类模式分类")
class IntentClassifierTest {

    private IntentMapper intentMapper;
    private LLMService classificationLLM;
    private IntentClassifier classifier;
    private List<IntentDO> intents;

    @BeforeEach
    void setUp() {
        intentMapper = mock(IntentMapper.class);
        classificationLLM = mock(LLMService.class);
        classifier = new IntentClassifier(intentMapper, classificationLLM, new ObjectMapper());
        intents = List.of(metaIntent(), retrievalIntent(), chatIntent());
        when(intentMapper.listEnabled()).thenReturn(intents);
    }

    @Nested
    @DisplayName("关键词快路径")
    class KeywordFastPath {

        @Test
        @DisplayName("唯一命中 META 时跳过 LLM")
        void uniqueMetaKeywordSkipsLlm() {
            IntentResult result = classifier.classify("知识库有多少篇文章？", List.of(), true);

            assertTrue(result.isKbMeta());
            assertEquals(0.95, result.getConfidence());
            assertTrue(result.getReason().startsWith("keyword:"));
            verify(classificationLLM, never()).chat(any());
        }

        @Test
        @DisplayName("唯一命中 CHAT 时跳过 LLM")
        void uniqueChatKeywordSkipsLlm() {
            IntentResult result = classifier.classify("你好", List.of(), false);

            assertTrue(result.isChat());
            assertEquals(0.95, result.getConfidence());
            verify(classificationLLM, never()).chat(any());
        }

        @Test
        @DisplayName("问候后面跟着目录问题：CHAT 不命中，META 快路径")
        void greetingPlusCatalogUsesMetaFastPath() {
            IntentResult result = classifier.classify("你好，知识库有多少篇文章", List.of(), true);

            assertTrue(result.isKbMeta());
            verify(classificationLLM, never()).chat(any());
        }

        @Test
        @DisplayName("谢谢后面跟着技术问题，不走闲聊快路径")
        void thanksPlusTechQuestionGoesToLlm() {
            when(classificationLLM.chat(any())).thenReturn(
                "{\"intentId\":5,\"confidence\":0.9,\"reason\":\"技术内容\",\"needsGuidance\":false}");

            IntentResult result = classifier.classify("谢谢，JWT 怎么配置？", List.of(), true);

            assertTrue(result.isKbRetrieval());
            verify(classificationLLM).chat(any());
        }

        @Test
        @DisplayName("「作者」出现在技术问题里，不走目录快路径")
        void authorInTechnicalQuestionGoesToLlm() {
            when(classificationLLM.chat(any())).thenReturn(
                "{\"intentId\":5,\"confidence\":0.86,\"reason\":\"正文\",\"needsGuidance\":false}");

            IntentResult result = classifier.classify("作者在文中怎么约定 Bean 名称？", List.of(), true);

            assertTrue(result.isKbRetrieval());
            verify(classificationLLM).chat(any());
        }

        @Test
        @DisplayName("「有哪些文章讲 JWT」是内容检索，不是目录")
        void whichArticlesAboutTopicGoesToLlm() {
            when(classificationLLM.chat(any())).thenReturn(
                "{\"intentId\":5,\"confidence\":0.9,\"reason\":\"按主题找文章\",\"needsGuidance\":false}");

            IntentResult result = classifier.classify("有哪些文章讲 JWT？", List.of(), true);

            assertTrue(result.isKbRetrieval());
            verify(classificationLLM).chat(any());
        }

        @Test
        @DisplayName("谁上传了文章仍走目录快路径")
        void whoUploadedStillMeta() {
            IntentResult result = classifier.classify("谁最后上传了文章？", List.of(), true);

            assertTrue(result.isKbMeta());
            verify(classificationLLM, never()).chat(any());
        }

        @Test
        @DisplayName("内容问题无关键词命中，走 LLM")
        void contentQuestionGoesToLlm() {
            when(classificationLLM.chat(any())).thenReturn(
                "{\"intentId\":5,\"confidence\":0.88,\"reason\":\"技术内容\",\"needsGuidance\":false}");

            IntentResult result = classifier.classify("JWT 过滤器怎么配置？", List.of(), true);

            assertTrue(result.isKbRetrieval());
            verify(classificationLLM).chat(any());
        }
    }

    @Nested
    @DisplayName("LLM 分类")
    class LlmClassification {

        @Test
        @DisplayName("调用 temperature=0 且 maxTokens=256")
        void usesDeterministicChatRequest() {
            when(classificationLLM.chat(any())).thenReturn(
                "{\"intentId\":5,\"confidence\":0.8,\"reason\":\"内容\",\"needsGuidance\":false}");

            classifier.classify("Spring AOP 原理", List.of(), true);

            ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
            verify(classificationLLM).chat(captor.capture());
            assertEquals(0.0, captor.getValue().getTemperature());
            assertEquals(256, captor.getValue().getMaxTokens());
        }

        @Test
        @DisplayName("JSON 包在 markdown 或前后废话里也能解析")
        void extractsJsonFromWrappedOutput() {
            when(classificationLLM.chat(any())).thenReturn("""
                好的，分类如下：
                ```json
                {"intentId": 1, "confidence": 0.91, "reason": "问篇数", "needsGuidance": false}
                ```
                """);

            IntentResult result = classifier.classify("库里都有什么主题", List.of(), false);

            assertTrue(result.isKbMeta());
            assertEquals(0.91, result.getConfidence());
            assertEquals("问篇数", result.getReason());
        }

        @Test
        @DisplayName("未知 intentId 按是否选 KB 降级")
        void unknownIntentIdFallsBack() {
            when(classificationLLM.chat(any())).thenReturn(
                "{\"intentId\":99,\"confidence\":0.9,\"reason\":\"?\",\"needsGuidance\":false}");

            assertTrue(classifier.classify("x", List.of(), true).isKbRetrieval());
            assertTrue(classifier.classify("x", List.of(), false).isChat());
        }

        @Test
        @DisplayName("置信度低于 0.5：有 KB 走检索，无 KB 走闲聊")
        void lowConfidenceFallsBackByKbSelection() {
            when(classificationLLM.chat(any())).thenReturn(
                "{\"intentId\":1,\"confidence\":0.2,\"reason\":\"不确定\",\"needsGuidance\":true}");

            IntentResult withKb = classifier.classify("嗯", List.of(), true);
            IntentResult withoutKb = classifier.classify("嗯", List.of(), false);

            assertTrue(withKb.isKbRetrieval());
            assertEquals("fallback", withKb.getReason());
            assertTrue(withoutKb.isChat());
            assertEquals("fallback", withoutKb.getReason());
        }

        @Test
        @DisplayName("LLM 抛错时降级")
        void llmFailureFallsBack() {
            when(classificationLLM.chat(any())).thenThrow(new RuntimeException("timeout"));

            assertTrue(classifier.classify("随便问", List.of(), false).isChat());
        }

        @Test
        @DisplayName("无启用意图时降级")
        void emptyIntentsFallsBack() {
            when(intentMapper.listEnabled()).thenReturn(List.of());

            assertTrue(classifier.classify("知识库有多少篇文章", List.of(), true).isKbRetrieval());
            verify(classificationLLM, never()).chat(any());
        }
    }

    @Nested
    @DisplayName("JSON 截取")
    class JsonExtract {

        @Test
        @DisplayName("截取第一个 { 到最后一个 }")
        void slicesOuterObject() {
            String extracted = IntentClassifier.extractJson("prefix {\"intentId\":1} trailing");
            assertEquals("{\"intentId\":1}", extracted);
        }
    }

    private static IntentDO metaIntent() {
        IntentDO i = new IntentDO();
        i.setId(1L);
        i.setName("知识库统计与概览");
        i.setType("KB_META");
        i.setKeywords("[\"多少篇\",\"知识库情况\",\"知识库有哪些\",\"谁上传\",\"谁最后上传\",\"最后上传\",\"上传人\"]");
        i.setCustomPromptFragment("目录模式");
        return i;
    }

    private static IntentDO retrievalIntent() {
        IntentDO i = new IntentDO();
        i.setId(5L);
        i.setName("内容检索");
        i.setType("KB_RETRIEVAL");
        i.setKeywords("[]");
        return i;
    }

    private static IntentDO chatIntent() {
        IntentDO i = new IntentDO();
        i.setId(4L);
        i.setName("闲聊");
        i.setType("CHAT");
        i.setKeywords("[\"你好\",\"谢谢\",\"你是谁\"]");
        return i;
    }
}
