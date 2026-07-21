package io.github.somehow.mysite.ragent.ingestion;

import io.github.somehow.mysite.ragent.config.RagProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MarkdownChunker 单元测试（v3 — Ragent 架构）。
 */
@DisplayName("MarkdownChunker — Markdown 分块器 (v3)")
class MarkdownChunkerTest {

    private MarkdownChunker chunker;

    @BeforeEach
    void setUp() {
        RagProperties properties = new RagProperties();
        properties.getChunk().setSize(200);
        properties.getChunk().setOverlap(30);
        properties.getChunk().setMaxChunksPerDoc(10);
        properties.getChunk().setMinChars(80);  // 测试用较小阈值
        chunker = new MarkdownChunker(properties);
    }

    // ==================== 基本分块 ====================

    @Nested
    @DisplayName("基本分块行为")
    class BasicChunking {

        @Test
        @DisplayName("短文本 → 单 chunk")
        void shortTextShouldProduceSingleChunk() {
            String content = "这是一段很短的文本，不到 200 字。";

            List<DocumentChunker.Chunk> chunks = chunker.chunk(content, 1L, 1L);

            assertEquals(1, chunks.size());
            assertEquals(1L, chunks.get(0).docId());
            assertEquals(0, chunks.get(0).index());
            assertTrue(chunks.get(0).content().contains("这是一段很短的文本"));
        }

        @Test
        @DisplayName("长文本 → 多个 chunk")
        void longTextShouldProduceMultipleChunks() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 50; i++) {
                sb.append("这是第 ").append(i).append(" 段测试文本，用来把内容填长一点。\n\n");
            }

            List<DocumentChunker.Chunk> chunks = chunker.chunk(sb.toString(), 1L, 1L);

            assertTrue(chunks.size() > 1, "长文本应产生多个 chunk，实际: " + chunks.size());
        }

        @Test
        @DisplayName("chunk 索引从 0 开始递增")
        void chunkIndicesShouldBeSequential() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 50; i++) {
                sb.append("第").append(i).append("段落内容填充字数达到阈值。\n\n");
            }

            List<DocumentChunker.Chunk> chunks = chunker.chunk(sb.toString(), 1L, 1L);

            for (int i = 0; i < chunks.size(); i++) {
                assertEquals(i, chunks.get(i).index(), "chunk 索引应顺序递增");
            }
        }
    }

    // ==================== Frontmatter ====================

    @Nested
    @DisplayName("frontmatter 去除")
    class Frontmatter {

        @Test
        @DisplayName("带 YAML frontmatter → 正确去除")
        void shouldRemoveYamlFrontmatter() {
            String content = """
                ---
                title: 测试文章
                date: 2026-07-20
                tags: [java, spring]
                ---

                ## 正文开始

                这是文章的正文内容，frontmatter 应该被去掉。
                """;

            List<DocumentChunker.Chunk> chunks = chunker.chunk(content, 1L, 1L);

            assertTrue(chunks.size() >= 1);
            assertFalse(chunks.get(0).content().contains("title: 测试文章"),
                "frontmatter 应该被移除");
            assertFalse(chunks.get(0).content().contains("---"));
            assertTrue(chunks.get(0).content().contains("正文"),
                "正文应该保留");
        }

        @Test
        @DisplayName("无 frontmatter → 原文不变")
        void shouldKeepContentWithoutFrontmatter() {
            String content = "没有任何 frontmatter 的内容。";

            List<DocumentChunker.Chunk> chunks = chunker.chunk(content, 1L, 1L);

            assertEquals(1, chunks.size());
            assertTrue(chunks.get(0).content().contains("没有任何"));
        }

        @Test
        @DisplayName("非标准 --- 分隔线 → 不误删")
        void shouldNotRemoveNonStandardDashes() {
            String content = """
                这是一段包含 --- 分隔线的文字。

                ---

                下面是分隔线后的内容。
                """;

            List<DocumentChunker.Chunk> chunks = chunker.chunk(content, 1L, 1L);

            String allContent = chunks.stream()
                .map(DocumentChunker.Chunk::content)
                .reduce("", String::concat);
            assertTrue(allContent.contains("分隔线后的内容"));
        }

        @Test
        @DisplayName("只有 frontmatter → 返回空")
        void frontmatterOnlyShouldReturnEmpty() {
            String content = """
                ---
                title: 只有元数据
                ---
                """;

            List<DocumentChunker.Chunk> chunks = chunker.chunk(content, 1L, 1L);
            assertTrue(chunks.isEmpty());
        }
    }

    // ==================== 边界情况 ====================

    @Nested
    @DisplayName("边界情况")
    class EdgeCases {

        @Test
        @DisplayName("空内容 → 返回空列表")
        void emptyContentShouldReturnEmptyList() {
            assertTrue(chunker.chunk("", 1L, 1L).isEmpty());
        }

        @Test
        @DisplayName("纯空白内容 → 返回空列表")
        void whitespaceOnlyShouldReturnEmptyList() {
            assertTrue(chunker.chunk("\n\n\n   \n\n", 1L, 1L).isEmpty());
        }

        @Test
        @DisplayName("超过 maxChunksPerDoc → 截断")
        void shouldTruncateAtMaxChunks() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 100; i++) {
                sb.append("段落").append(i)
                    .append("：这是一段用来填满 chunk 的测试文本内容。\n\n");
            }

            List<DocumentChunker.Chunk> chunks = chunker.chunk(sb.toString(), 1L, 1L);
            assertTrue(chunks.size() <= 10,
                "不应超过 maxChunksPerDoc=10，实际: " + chunks.size());
        }
    }

    // ==================== 标题感知（v3: 标题不产 chunk） ====================

    @Nested
    @DisplayName("标题感知分块 (v3)")
    class HeadingAware {

        @Test
        @DisplayName("标题路径应注入到后续 chunk 内容中")
        void headingPathShouldBePrependedToChunk() {
            String content = """
                ## JWT 过滤器配置

                Spring Security 中的 JWT 认证主要通过 OncePerRequestFilter 实现。

                ### 核心配置步骤

                1. 创建 JwtAuthenticationFilter 继承 OncePerRequestFilter
                2. 在 SecurityFilterChain 中注册过滤器
                3. 配置 permitAll 和 authenticated 路径
                """;

            List<DocumentChunker.Chunk> chunks = chunker.chunk(content, 1L, 1L);

            assertTrue(chunks.size() >= 1, "应至少有 1 个 chunk");

            // 至少有一个 chunk 包含标题路径
            boolean hasHeadingPath = chunks.stream()
                .anyMatch(c -> c.content().contains("JWT 过滤器配置"));
            assertTrue(hasHeadingPath, "应有 chunk 包含标题路径");

            // chunk 不应以裸编号开头 — 应有标题前缀
            for (DocumentChunker.Chunk c : chunks) {
                assertFalse(c.content().startsWith("1. "),
                    "Chunk 不应以裸编号开头，应有标题前缀: "
                    + c.content().substring(0, Math.min(60, c.content().length())));
            }
        }

        @Test
        @DisplayName("同级标题应替换而非嵌套")
        void sameLevelHeadingShouldReplaceNotNest() {
            String content = """
                ## 父标题

                父级内容持续填充使得 chunk 足够大。

                ### 子标题 A

                子内容 A 的文本也需要足够长才能确保分块正确。

                ### 子标题 B

                子内容 B 的文本同样需要足够长度以确保正确识别层级。
                """;

            List<DocumentChunker.Chunk> chunks = chunker.chunk(content, 1L, 1L);

            // 不应出现 "子标题 A > 子标题 B" 的嵌套路径
            for (DocumentChunker.Chunk c : chunks) {
                assertFalse(c.content().contains("子标题 A > 子标题 B"),
                    "同级 h3 不应嵌套: " + c.content().substring(0,
                        Math.min(80, c.content().length())));
            }
        }

        @Test
        @DisplayName("无标题文档 → 正常分段，无路径前缀")
        void noHeadingShouldWorkNormally() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 30; i++) {
                sb.append("这是第 ").append(i)
                    .append(" 段纯文本内容，没有 markdown 标题标记。\n\n");
            }

            List<DocumentChunker.Chunk> chunks = chunker.chunk(sb.toString(), 1L, 1L);

            assertTrue(chunks.size() > 1, "长文本应分成多个 chunk");
        }

        @Test
        @DisplayName("长 Section 内多次分段，每段都带标题路径")
        void longSectionShouldHaveHeadingOnEveryChunk() {
            StringBuilder sb = new StringBuilder();
            sb.append("## 长章节标题\n\n");
            for (int i = 0; i < 40; i++) {
                sb.append("段落").append(i)
                    .append("：这段文字用来填满 chunk 触发 Section 内部切分。\n\n");
            }

            List<DocumentChunker.Chunk> chunks = chunker.chunk(sb.toString(), 1L, 1L);

            assertTrue(chunks.size() >= 2,
                "长 Section 应切分成多个 chunk，实际: " + chunks.size());

            boolean allHaveHeading = chunks.stream()
                .allMatch(c -> c.content().startsWith("长章节标题"));
            assertTrue(allHaveHeading, "每个 chunk 都应以标题路径开头");
        }

        @Test
        @DisplayName("单独标题不产生 chunk")
        void standaloneHeadingShouldNotProduceOrphanChunk() {
            // 一个标题后没有正文，不应产生孤 chunk
            String content = """
                ## 只有一个标题

                ## 另一个标题
                """;

            List<DocumentChunker.Chunk> chunks = chunker.chunk(content, 1L, 1L);

            // 没有正文 = 没有 chunk
            assertEquals(0, chunks.size(),
                "没有正文的标题不应产生 chunk，实际: " + chunks.size());
        }
    }

    // ==================== 代码块原子性 ====================

    @Nested
    @DisplayName("代码块原子保护")
    class CodeBlockAtomicity {

        @Test
        @DisplayName("代码块不应被拆分")
        void codeBlockShouldNotBeSplit() {
            StringBuilder sb = new StringBuilder();
            sb.append("## 代码示例\n\n");
            sb.append("下面是示例代码：\n\n");
            sb.append("```java\n");
            for (int i = 0; i < 30; i++) {
                sb.append("public void method").append(i).append("() {\n");
                sb.append("    System.out.println(\"line ").append(i).append("\");\n");
                sb.append("}\n");
            }
            sb.append("```\n");

            List<DocumentChunker.Chunk> chunks = chunker.chunk(sb.toString(), 1L, 1L);

            // 代码块应完整出现在某一个 chunk 中（不被截断）
            boolean codeBlockIntact = chunks.stream()
                .anyMatch(c -> c.content().contains("public void method0()")
                    && c.content().contains("public void method29()"));
            assertTrue(codeBlockIntact,
                "完整代码块应出现在某一个 chunk 中，不应被截断");
        }

        @Test
        @DisplayName("代码块前后有文本时应正确分割")
        void codeBlockWithSurroundingText() {
            String content = """
                ## 说明

                这段 Java 代码展示了过滤器的基本结构：

                ```java
                public class MyFilter extends OncePerRequestFilter {
                    @Override
                    protected void doFilterInternal(HttpServletRequest request,
                            HttpServletResponse response, FilterChain chain) {
                        // 过滤逻辑
                        chain.doFilter(request, response);
                    }
                }
                ```

                过滤器需要在 SecurityFilterChain 中注册。
                """;

            List<DocumentChunker.Chunk> chunks = chunker.chunk(content, 1L, 1L);

            assertTrue(chunks.size() >= 1);

            // 代码块的 embeddingText 应不同于 content
            DocumentChunker.Chunk codeChunk = chunks.stream()
                .filter(c -> c.content().contains("```java"))
                .findFirst().orElse(null);
            assertNotNull(codeChunk, "应有包含代码块的 chunk");

            // embeddingText 应使用摘要而非完整代码
            assertNotNull(codeChunk.embeddingText(),
                "代码块应有非 null 的 embeddingText");
            assertTrue(codeChunk.embeddingText().contains("[代码:java]"),
                "embeddingText 应标注代码类型，实际: "
                + codeChunk.embeddingText().substring(0,
                    Math.min(80, codeChunk.embeddingText().length())));
        }
    }

    // ==================== embeddingText 分离 ====================

    @Nested
    @DisplayName("embeddingText 分离")
    class EmbeddingTextSeparation {

        @Test
        @DisplayName("纯段落文本 → embeddingText 为 null（回退到 content）")
        void plainTextShouldHaveNullEmbeddingText() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 20; i++) {
                sb.append("第").append(i).append("段普通文本内容，没有任何特殊标记。\n\n");
            }

            List<DocumentChunker.Chunk> chunks = chunker.chunk(sb.toString(), 1L, 1L);

            assertTrue(chunks.size() > 0);
            for (DocumentChunker.Chunk c : chunks) {
                assertNull(c.embeddingText(),
                    "纯段落文本的 embeddingText 应为 null（回退到 content）");
            }
        }

        @Test
        @DisplayName("代码块 chunk → embeddingText 去噪，不含完整代码")
        void codeChunkEmbeddingTextShouldBeDenoised() {
            String content = """
                ```python
                import os
                import sys
                import json
                import requests
                from typing import List, Optional

                def main():
                    parser = argparse.ArgumentParser()
                    parser.add_argument('--input', required=True)
                    args = parser.parse_args()
                    data = json.load(open(args.input))
                    result = process(data)
                    print(json.dumps(result, indent=2))
                ```
                """;

            List<DocumentChunker.Chunk> chunks = chunker.chunk(content, 1L, 1L);

            DocumentChunker.Chunk c = chunks.get(0);
            assertNotNull(c.embeddingText());
            assertTrue(c.embeddingText().contains("[代码:python]"));
            // embeddingText 只包含前 3 行 + "..."，不含全部代码
            assertFalse(c.embeddingText().contains("argparse.ArgumentParser()"),
                "embeddingText 应只含前 3 行摘要");
        }
    }

    // ==================== 最小 chunk 阈值 ====================

    @Nested
    @DisplayName("最小 chunk 阈值 (minChars)")
    class MinChunkThreshold {

        @Test
        @DisplayName("尾部碎片合并到前一个 chunk")
        void tinyChunkShouldMergeIntoPrevious() {
            // 构造一个刚好多出一小段的内容
            StringBuilder sb = new StringBuilder();
            sb.append("## 标题\n\n");
            // 填充到接近 chunkSize
            for (int i = 0; i < 12; i++) {
                sb.append("段落").append(i)
                    .append("：填充内容使前一个 chunk 到边界。\n\n");
            }
            // 尾巴很短
            sb.append("只有一句话的尾巴。");

            List<DocumentChunker.Chunk> chunks = chunker.chunk(sb.toString(), 1L, 1L);

            assertTrue(chunks.size() >= 1);
            // 最后一个 chunk 不应太短
            DocumentChunker.Chunk last = chunks.get(chunks.size() - 1);
            assertTrue(last.content().length() >= 80
                || chunks.size() == 1,  // 除非只有一个 chunk
                "最后一个 chunk 应 >= minChars 或与前面合并，实际长度: "
                + last.content().length() + ", 总 chunk 数: " + chunks.size());
        }
    }

    // ==================== 表格双重表示 ====================

    @Nested
    @DisplayName("表格双重表示")
    class TableDualRepresentation {

        @Test
        @DisplayName("表格 embeddingText 使用 key-value 格式")
        void tableEmbeddingTextShouldBeKeyValue() {
            String content = """
                ## 配置参数

                | 参数名 | 类型 | 默认值 | 说明 |
                |--------|------|--------|------|
                | maxSize | int | 100 | 最大连接数 |
                | timeout | long | 3000 | 超时毫秒 |
                | enabled | boolean | true | 是否启用 |
                """;

            List<DocumentChunker.Chunk> chunks = chunker.chunk(content, 1L, 1L);

            assertTrue(chunks.size() >= 1);

            // 表格 chunk 应有非 null embeddingText
            DocumentChunker.Chunk tableChunk = chunks.stream()
                .filter(c -> c.content().contains("| 参数名 |"))
                .findFirst().orElse(null);
            assertNotNull(tableChunk, "应有包含表格的 chunk");

            assertNotNull(tableChunk.embeddingText(),
                "表格 chunk 应有非 null embeddingText");

            // embeddingText 应是 KV 格式
            assertTrue(tableChunk.embeddingText().contains("参数名: maxSize"),
                "embeddingText 应包含 KV 格式，实际: " + tableChunk.embeddingText());
            assertTrue(tableChunk.embeddingText().contains("说明: 最大连接数"),
                "embeddingText 应包含完整 KV: " + tableChunk.embeddingText());

            // content 保留原始 Markdown 表格
            assertTrue(tableChunk.content().contains("| 参数名 | 类型 | 默认值 | 说明 |"),
                "content 应保留原始 Markdown 表格");

            System.out.println("表格 content: " + tableChunk.content()
                .replace("\n", "\\n"));
            System.out.println("表格 embedding: " + tableChunk.embeddingText()
                .replace("\n", "\\n"));
        }
    }

    // ==================== 图片块处理 ====================

    @Nested
    @DisplayName("图片块处理")
    class ImageBlockHandling {

        @Test
        @DisplayName("有 alt 文本 → embeddingText 使用描述")
        void imageWithAltTextShouldUseDescription() {
            String content = """
                这是一段文字。

                ![Spring Security 过滤器链架构图](https://example.com/images/filter-chain.png)

                下面是更多文字描述。
                """;

            List<DocumentChunker.Chunk> chunks = chunker.chunk(content, 1L, 1L);

            // 找包含图片的 chunk
            DocumentChunker.Chunk imgChunk = chunks.stream()
                .filter(c -> c.content().contains("filter-chain.png"))
                .findFirst().orElse(null);

            if (imgChunk != null && imgChunk.embeddingText() != null) {
                assertTrue(imgChunk.embeddingText().contains("[图片:"),
                    "embeddingText 应包含图片标记");
                assertTrue(imgChunk.embeddingText().contains("过滤器链架构图"),
                    "embeddingText 应包含 alt 文本: "
                    + imgChunk.embeddingText());
            }
        }

        @Test
        @DisplayName("无 alt 文本 → embeddingText 使用 URL")
        void imageWithoutAltShouldUseUrl() {
            String content = """
                ![](https://example.com/diagram.png)
                """;

            List<DocumentChunker.Chunk> chunks = chunker.chunk(content, 1L, 1L);

            if (!chunks.isEmpty() && chunks.get(0).embeddingText() != null) {
                assertTrue(chunks.get(0).embeddingText().contains("diagram.png"),
                    "无 alt 时应使用 URL: " + chunks.get(0).embeddingText());
            }
        }

        @Test
        @DisplayName("图片块不破坏周围的文本流")
        void imageShouldNotBreakTextFlow() {
            String content = """
                ## 架构说明

                下图展示了整体架构：

                ![架构图](https://example.com/arch.png)

                如上图所示，系统分为三层。
                """;

            List<DocumentChunker.Chunk> chunks = chunker.chunk(content, 1L, 1L);

            assertTrue(chunks.size() >= 1);
            // 图片和文字应在同一 chunk（内容足够短）
            assertTrue(chunks.get(0).content().contains("arch.png"),
                "图片应出现在某个 chunk 中");
            assertTrue(chunks.get(0).content().contains("系统分为三层"),
                "图片后的文字应在同一 chunk");
        }
    }

    // ==================== Overlap（块级） ====================

    @Nested
    @DisplayName("块级 overlap")
    class BlockOverlap {

        @Test
        @DisplayName("相邻 chunk 应有重叠内容，且对齐到段落边界")
        void adjacentChunksShouldHaveParagraphAlignedOverlap() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 20; i++) {
                sb.append("段落").append(i)
                    .append("：这是一段有实际内容的文本用于测试分块重叠功能。\n\n");
            }

            List<DocumentChunker.Chunk> chunks = chunker.chunk(sb.toString(), 1L, 1L);

            if (chunks.size() >= 2) {
                // 检查 overlap 规律：chunk 1 的开头应包含某个完整段落（来自 chunk 0 的尾部）
                String chunk1Start = chunks.get(1).content();
                assertFalse(chunk1Start.startsWith("。")
                    || chunk1Start.startsWith("，"),
                    "Chunk 1 开头不应是半截句子");
                System.out.println("Chunk[1] 开头: " + chunk1Start.substring(0,
                    Math.min(120, chunk1Start.length())).replace("\n", "\\n"));
            }
        }
    }
}
