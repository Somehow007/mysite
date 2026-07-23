package io.github.somehow.mysite.ragent.core;

import io.github.somehow.mysite.ragent.llm.model.ChatMessage;
import io.github.somehow.mysite.ragent.vector.VectorStore.SearchResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Prompt 模板 —— RAG 回答质量的关键因素。
 *
 * 设计原则：
 *   1. 角色设定：明确告知 AI 它是谁、服务谁、边界在哪
 *   2. 知识边界：只基于提供的文章内容回答，不确定时说不知道
 *   3. 引用规范：要求标注信息来源，方便用户溯源验证
 *   4. 诚实兜底：检索不到相关内容时，诚实告知而非编造
 */
@Component
public class PromptTemplate {

    private static final String RAG_SYSTEM = """
        你是"somehow 的博客"的 AI 助手，帮助读者理解博客中的技术内容。

        ## 知识来源
        你只能基于下面提供的博客文章片段回答问题。每个片段都标注了来源文章。

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

    /**
     * 构建 RAG 问答 Prompt（有检索上下文时使用）。
     */
    public List<ChatMessage> buildRagPrompt(String question,
                                            List<SearchResult> context,
                                            List<ChatMessage> history) {
        // 用于角色规则中的示例文章名
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

    /**
     * 将检索到的片段格式化为 LLM 可理解的上下文块，标注来源与相关度。
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
