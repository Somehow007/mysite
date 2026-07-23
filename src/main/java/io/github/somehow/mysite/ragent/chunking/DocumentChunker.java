package io.github.somehow.mysite.ragent.chunking;

import java.util.List;

/**
 * 文档分块器接口。
 *
 * 为什么需要抽象？
 *   当前实现是固定大小 + Markdown 标题感知分块，但未来可以扩展为：
 *   - 语义分块（让 LLM 判断分块边界，成本更高但效果更好）
 *   - 代码感知分块（AST 级别切分）
 *   - 多模态分块（图文混合）
 *   接口抽象让分块策略可以独立演进，不影响下游的向量化和检索。
 */
public interface DocumentChunker {

    /**
     * 将文档内容切分为多个块。
     *
     * @param markdownContent 原始 Markdown 内容
     * @param docId           文档 ID
     * @param kbId            所属知识库 ID
     * @return 分块列表，按文档中的出现顺序排列
     */
    List<Chunk> chunk(String markdownContent, Long docId, Long kbId);

    /**
     * 分块结果。
     *
     * @param docId         所属文档 ID
     * @param kbId          所属知识库 ID
     * @param index         分块序号（从 0 开始）
     * @param content       展示/LLM 用的文本（原始 Markdown 格式）
     * @param embeddingText 向量化专用文本，为 null 时回退到 content
     *                      （Ragent 模式：代码块去噪、表格转 KV、描述替代 URL）
     */
    record Chunk(
        Long docId,
        Long kbId,
        int index,
        String content,
        String embeddingText
    ) {}
}
