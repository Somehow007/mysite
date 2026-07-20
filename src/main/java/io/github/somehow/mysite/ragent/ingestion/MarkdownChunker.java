package io.github.somehow.mysite.ragent.ingestion;

import io.github.somehow.mysite.ragent.config.RagProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Markdown 文档分块器 —— RAG 质量的关键因素之一。
 *
 * 为什么分块很重要？
 *   1. 嵌入模型有输入长度限制（通常 512~8192 tokens）
 *   2. 分块太大会稀释语义（一段话包含太多主题）
 *   3. 分块太小会丢失上下文（"它"指代什么？不知道）
 *   4. 最佳实践：800 字符左右，100 字符重叠，在段落/标题边界切分
 *
 * 重叠（overlap）的作用：
 *   Chunk A: "...Spring Security 提供了强大的过滤器链机制..."
 *   Overlap:                       "...过滤器链机制..."
 *   Chunk B:                       "...过滤器链机制。其中 JwtAuthenticationFilter..."
 *   重叠确保跨分块边界的信息不会丢失。
 *
 * 当前实现：固定大小分块 + Markdown 标题感知。
 * 未来可扩展：语义分块（让 LLM 判断分块边界，成本更高但效果更好）。
 */
@Component
public class MarkdownChunker implements DocumentChunker {

    private final RagProperties properties;

    public MarkdownChunker(RagProperties properties) {
        this.properties = properties;
    }

    @Override
    public List<Chunk> chunk(String markdownContent, Long docId, Long kbId) {
        int chunkSize = properties.getChunk().getSize();           // 800
        int overlap = properties.getChunk().getOverlap();          // 100
        int maxPerDoc = properties.getChunk().getMaxChunksPerDoc(); // 50

        // 1. 去除 YAML frontmatter（--- ... ---）
        String cleanContent = removeFrontmatter(markdownContent);

        // 2. 按段落（\n\n）分割
        String[] paragraphs = cleanContent.split("\n\n");

        // 3. 固定大小 + Markdown 标题边界感知分块
        List<Chunk> chunks = new ArrayList<>();
        StringBuilder currentChunk = new StringBuilder();
        int currentSize = 0;
        int chunkIndex = 0;

        for (String paragraph : paragraphs) {
            String trimmed = paragraph.trim();
            if (trimmed.isEmpty()) continue;

            if (currentSize + trimmed.length() > chunkSize && currentSize > 0) {
                // 当前块满了，保存
                chunks.add(new Chunk(docId, kbId, chunkIndex++, currentChunk.toString()));
                // 保留 overlap 部分
                String overlapText = currentChunk.substring(
                    Math.max(0, currentChunk.length() - overlap));
                currentChunk = new StringBuilder(overlapText);
                currentSize = overlapText.length();
            }

            currentChunk.append(trimmed).append("\n\n");
            currentSize += trimmed.length() + 2;

            if (chunks.size() >= maxPerDoc) break;
        }

        // 最后一块
        if (currentSize > 0 && chunks.size() < maxPerDoc) {
            chunks.add(new Chunk(docId, kbId, chunkIndex, currentChunk.toString()));
        }

        return chunks;
    }

    /**
     * 去除 Markdown 文件开头的 YAML frontmatter。
     * frontmatter 格式：
     *   ---
     *   title: xxx
     *   date: xxx
     *   ---
     */
    private String removeFrontmatter(String content) {
        if (content.startsWith("---")) {
            int end = content.indexOf("---", 3);
            if (end > 0) {
                return content.substring(end + 3).trim();
            }
        }
        return content;
    }
}
