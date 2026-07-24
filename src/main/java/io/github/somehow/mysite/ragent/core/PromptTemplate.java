package io.github.somehow.mysite.ragent.core;

import io.github.somehow.mysite.ragent.config.RagProperties;
import io.github.somehow.mysite.ragent.core.intent.IntentResult;
import io.github.somehow.mysite.ragent.llm.model.ChatMessage;
import io.github.somehow.mysite.ragent.vector.VectorStore.SearchResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Prompt 模板 —— RAG 回答质量的关键因素。
 *
 * <h3>设计原则</h3>
 * <ol>
 *   <li>角色设定：明确告知 AI 它是谁、服务谁、边界在哪</li>
 *   <li>知识边界：只基于提供的文章内容回答，不确定时说不知道</li>
 *   <li>引用规范：要求标注信息来源，方便用户溯源验证</li>
 *   <li>诚实兜底：检索不到相关内容时，诚实告知而非编造</li>
 *   <li>意图感知（Phase 6）：不同意图注入不同的 system prompt 片段</li>
 * </ol>
 */
@Component
@RequiredArgsConstructor
public class PromptTemplate {

    private final RagProperties properties;

    private static final String RAG_SYSTEM = """
        你是"somehow 的博客"的 AI 助手，帮助读者理解博客中的技术内容。

        ## 知识来源
        你只能基于下面提供的博客文章片段回答问题。每个片段都标注了来源文章和所属知识库。

        ## 重要规则
        1. 如果提供的内容足以回答问题，请详细、准确地回答，并在文中引用来源\\
        （例如："根据《%s》一文..."）。
        2. 如果提供的片段不足以回答，请诚实地说"博客中暂时没有涉及这个问题的文章"，\\
        不要编造信息。
        3. 如果你引用了具体代码或配置，务必标注来自哪篇文章。
        4. 使用 Markdown 格式，代码块标注语言。
        5. 回答要友好但专业，面向懂技术的读者。

        ## 提供的博客内容
        %s
        """;

    private static final String GENERAL_SYSTEM = """
        你是"somehow 的博客"的 AI 助手。用户可以和你聊天或询问技术问题。
        如果用户询问博客相关的内容而你无法回答，建议他们去博客上查看相关文章。
        保持友好、专业的语气，使用 Markdown 格式回复。
        """;

    // ── Phase 3: 基础 Prompt ──

    /**
     * 构建 RAG 问答 Prompt（有检索上下文时使用）。
     */
    public List<ChatMessage> buildRagPrompt(String question,
                                            List<SearchResult> context,
                                            List<ChatMessage> history) {
        String exampleTitle = context.isEmpty() ? "xxx" : context.get(0).docTitle();
        String systemPrompt = RAG_SYSTEM.formatted(exampleTitle, formatContext(context));

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(systemPrompt));
        messages.addAll(history);
        messages.add(ChatMessage.user(question));
        return messages;
    }

    /**
     * 构建通用聊天 Prompt（无检索结果时使用）。
     */
    public List<ChatMessage> buildGeneralPrompt(String question, List<ChatMessage> history) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(GENERAL_SYSTEM));
        messages.addAll(history);
        messages.add(ChatMessage.user(question));
        return messages;
    }

    // ── Phase 6: 意图感知 Prompt ──

    /**
     * 意图感知的 Prompt —— 让 LLM 知道它在哪个"角色模式"下工作。
     *
     * @param question 用户问题
     * @param context  检索结果（空 = 无上下文 RAG）
     * @param history  对话历史
     * @param intent   意图分类结果（含 customPromptFragment）
     * @return 完整的 messages 列表，可直接放入 ChatRequest
     */
    public List<ChatMessage> buildIntentAwarePrompt(
            String question,
            List<SearchResult> context,
            List<ChatMessage> history,
            IntentResult intent) {

        String systemPrompt = buildSystemPrompt(context, intent);
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(systemPrompt));
        messages.addAll(history);
        messages.add(ChatMessage.user(question));
        return messages;
    }

    /**
     * 组装 system prompt：基础 persona + 意图专属片段 + 检索上下文。
     */
    private String buildSystemPrompt(List<SearchResult> context, IntentResult intent) {
        StringBuilder sb = new StringBuilder();

        // 基础 persona
        sb.append("""
            你是"somehow 的博客"的 AI 助手。你的职责是帮助读者理解和导航博客内容。

            ## 核心规则
            1. 只能基于提供的博客文章片段回答问题
            2. 不知道就说不知道，不要编造
            3. 回答中标明信息来源（文章名 + 所属知识库）
            4. 使用 Markdown 格式，代码块标注语言
            """);

        // 意图专属 Prompt 片段
        if (intent != null && intent.getCustomPromptFragment() != null
                && !intent.getCustomPromptFragment().isBlank()) {
            sb.append("\n## 当前角色\n");
            sb.append(intent.getCustomPromptFragment());
        }

        // 检索上下文
        if (context.isEmpty()) {
            sb.append("\n## 检索结果\n（未找到相关内容，请诚实告知用户）");
        } else {
            sb.append("\n## 提供的博客内容\n");
            sb.append(formatContextWithKbName(context));
        }

        return sb.toString();
    }

    /**
     * 格式化检索结果为 LLM 可理解的上下文块（标注 KB 名称）。
     * <p>
     * 与 Phase 3 的 {@link #formatContext} 的区别：
     * 每条来源前面加 "【知识库名】《文章标题》"，让用户和 LLM 都知道来源属于哪个 KB。
     */
    String formatContextWithKbName(List<SearchResult> results) {
        Map<Long, String> kbNameCache = properties.getKbNameCache();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < results.size(); i++) {
            SearchResult r = results.get(i);
            String kbName = kbNameCache.getOrDefault(r.kbId(), "博客");
            sb.append("---\n");
            sb.append("[来源%d] 【%s】《%s》（相关性: %.2f）\n\n".formatted(
                i + 1, kbName, r.docTitle(), r.score()));
            sb.append(r.content()).append("\n\n");
        }
        return sb.toString();
    }

    /**
     * 将检索到的片段格式化为 LLM 可理解的上下文块（Phase 3 版本，无 KB 名称标注）。
     */
    private String formatContext(List<SearchResult> results) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < results.size(); i++) {
            SearchResult r = results.get(i);
            sb.append("---\n");
            sb.append("[来源%d] 文章《%s》（相关度: %.2f）\n\n".formatted(
                i + 1, r.docTitle(), r.score()));
            sb.append(r.content()).append("\n\n");
        }
        return sb.toString();
    }
}
