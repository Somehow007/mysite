package io.github.somehow.mysite.ragent.ingestion;

import io.github.somehow.mysite.ragent.config.RagProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Markdown 文档分块器 —— 基于 Ragent 架构的 block-aware 实现。
 *
 * 核心改进（v3）：
 *   1. 标题不产 chunk — Heading 只维护层级路径，注入到后续 chunk 上下文
 *   2. embeddingText 分离 — 展示用 content（原始 MD），向量化用 embeddingText（去噪/转 KV）
 *   3. 代码块原子保护 — 围栏代码块绝不跨 chunk 切分
 *   4. 最小 chunk 阈值 — 低于 minChars 的碎片合并到前一个
 *   5. 表格双重表示 — content=MD 表格, embeddingText=key-value
 *   6. 图片块 — ![](url) 用 alt 文本嵌入
 *
 * 分块流程：
 *   Markdown → parseBlocks() → buildProtoChunks() → packChunks() → materialize() → List<Chunk>
 */
@Component
public class MarkdownChunker implements DocumentChunker {

    private final RagProperties properties;

    public MarkdownChunker(RagProperties properties) {
        this.properties = properties;
    }

    @Override
    public List<Chunk> chunk(String markdownContent, Long docId, Long kbId) {
        int chunkSize = properties.getChunk().getSize();            // 800
        int overlap = properties.getChunk().getOverlap();           // 100
        int maxPerDoc = properties.getChunk().getMaxChunksPerDoc(); // 50
        int minChars = properties.getChunk().getMinChars();         // 400

        // 1. 去 frontmatter → 解析为 Block 列表
        String cleanContent = removeFrontmatter(markdownContent);
        List<Block> blocks = parseBlocks(cleanContent);
        if (blocks.isEmpty()) return List.of();

        // 2. 拆分超大段落（单个 ParagraphBlock 可能 > chunkSize）
        blocks = splitLargeParagraphs(blocks, chunkSize);

        // 3. 构建 proto-chunks（贪心填充，尊重原子性）
        List<ProtoChunk> proto = buildProtoChunks(blocks, chunkSize);

        // 3. ChunkPacker：块级 overlap + minChars 合并
        List<ProtoChunk> packed = packChunks(proto, overlap, minChars);

        // 4. 物化为最终的 Chunk 记录
        List<Chunk> chunks = new ArrayList<>();
        int index = 0;
        for (ProtoChunk pc : packed) {
            if (chunks.size() >= maxPerDoc) break;
            String prefix = pc.headingPath().isEmpty() ? "" : pc.headingPath() + "\n\n";
            String emb = pc.embeddingText();
            String embText = (emb == null || emb.isEmpty()) ? null : prefix + emb;
            chunks.add(new Chunk(docId, kbId, index++, prefix + pc.displayContent(), embText));
        }
        return chunks;
    }

    // ================================================================
    // Phase 1: Block 解析
    // ================================================================

    /**
     * 逐行扫描 Markdown，识别五种 Block 类型。
     * 代码块检测（围栏匹配）优先级最高，标题/表格/图片次之，其余归为段落。
     */
    private List<Block> parseBlocks(String content) {
        List<Block> blocks = new ArrayList<>();
        String[] lines = content.split("\n", -1);
        StringBuilder paraBuf = new StringBuilder();
        int i = 0;

        while (i < lines.length) {
            String line = lines[i];

            // --- 围栏代码块：``` 或 ~~~ ---
            String fenceLang = getFenceInfo(line);
            if (fenceLang != null) {
                flushParagraph(paraBuf, blocks);
                i = consumeFencedCode(lines, i, fenceLang, blocks);
                continue;
            }

            // --- 标题行 ---
            int hLevel = getHeadingLevel(line);
            if (hLevel > 0) {
                flushParagraph(paraBuf, blocks);
                blocks.add(new HeadingBlock(hLevel, stripHeadingMarkers(line)));
                i++;
                continue;
            }

            // --- 表格：先看当前行和下一行是否组成 |...| + |---| ---
            if (isTableStart(lines, i)) {
                flushParagraph(paraBuf, blocks);
                i = consumeTable(lines, i, blocks);
                continue;
            }

            // --- 独立图片行：![alt](url) ---
            ImageInfo img = parseImageLine(line.trim());
            if (img != null) {
                flushParagraph(paraBuf, blocks);
                blocks.add(new ImageBlock(img.alt, img.url, line.trim()));
                i++;
                continue;
            }

            // --- 普通段落 ---
            paraBuf.append(line).append("\n");
            i++;
        }
        flushParagraph(paraBuf, blocks);
        return blocks;
    }

    private void flushParagraph(StringBuilder buf, List<Block> blocks) {
        String text = buf.toString().trim();
        buf.setLength(0);
        if (!text.isEmpty()) {
            blocks.add(new ParagraphBlock(text));
        }
    }

    /** 消费围栏代码块：吞入从当前行到闭合围栏的所有行 */
    private int consumeFencedCode(String[] lines, int start, String fenceLang, List<Block> blocks) {
        StringBuilder fullText = new StringBuilder();
        String openLine = lines[start];
        fullText.append(openLine).append("\n");

        StringBuilder rawCode = new StringBuilder();  // 不含围栏的纯代码
        int i = start + 1;
        while (i < lines.length && getFenceInfo(lines[i]) == null) {
            fullText.append(lines[i]).append("\n");
            rawCode.append(lines[i]).append("\n");
            i++;
        }
        if (i < lines.length) {
            fullText.append(lines[i]).append("\n"); // 闭合围栏
            i++;
        }
        String lang = fenceLang.isEmpty() ? "" : fenceLang;
        blocks.add(new CodeBlock(lang, rawCode.toString().stripTrailing(),
            fullText.toString().stripTrailing()));
        return i;
    }

    /** 检测表格：当前行是 |...| 且下一行是 |---| 分隔行 */
    private boolean isTableStart(String[] lines, int i) {
        if (i + 1 >= lines.length) return false;
        return lines[i].trim().matches("^\\|.+\\|$")
            && lines[i + 1].trim().matches("^[|\\s\\-:]+$");  // |---|:---| 等分隔行
    }

    /** 消费表格：表头 + 分隔行 + 所有数据行 */
    private int consumeTable(String[] lines, int start, List<Block> blocks) {
        List<String> headers = parseTableRow(lines[start]);
        int i = start + 2; // 跳过头行和分隔行

        List<List<String>> rows = new ArrayList<>();
        while (i < lines.length && lines[i].trim().matches("^\\|.+\\|$")
            && !isTableStart(lines, i)) { // 非嵌套表格
            rows.add(parseTableRow(lines[i]));
            i++;
        }

        // 重建原始 MD 表格文本
        StringBuilder orig = new StringBuilder();
        orig.append(lines[start]).append("\n");
        orig.append(lines[start + 1]).append("\n");
        for (int r = start + 2; r < i; r++) {
            orig.append(lines[r]).append("\n");
        }

        blocks.add(new TableBlock(headers, rows, orig.toString().stripTrailing()));
        return i;
    }

    /** 解析表格行：| a | b | c | → [a, b, c] */
    private List<String> parseTableRow(String line) {
        String trimmed = line.trim();
        // 去掉首尾 |
        if (trimmed.startsWith("|")) trimmed = trimmed.substring(1);
        if (trimmed.endsWith("|")) trimmed = trimmed.substring(0, trimmed.length() - 1);
        String[] cells = trimmed.split("\\|");
        List<String> result = new ArrayList<>();
        for (String cell : cells) {
            result.add(cell.trim());
        }
        return result;
    }

    // ================================================================
    // Phase 2: 构建 ProtoChunk
    // ================================================================

    /**
     * 将超过 chunkSize 的大段落按 \n\n 拆分为多个 ParagraphBlock。
     * 这确保 buildProtoChunks 中的贪心填充不会遇到单体超大 block。
     */
    private List<Block> splitLargeParagraphs(List<Block> blocks, int maxSize) {
        List<Block> result = new ArrayList<>();
        for (Block block : blocks) {
            if (block instanceof ParagraphBlock) {
                String text = ((ParagraphBlock) block).text();
                if (text.length() > maxSize) {
                    // 按 \n\n 拆分
                    String[] subParas = text.split("\n\n");
                    for (String sub : subParas) {
                        String trimmed = sub.trim();
                        if (!trimmed.isEmpty()) {
                            result.add(new ParagraphBlock(trimmed));
                        }
                    }
                } else {
                    result.add(block);
                }
            } else {
                result.add(block);
            }
        }
        return result;
    }

    /**
     * 遍历 Block 列表，贪心填充 proto-chunks。
     * Heading 只更新路径，不参与 chunk 内容。
     * CodeBlock/TableBlock 为原子类型，独占 chunk。
     * ParagraphBlock/ImageBlock 可合并到同一 chunk。
     */
    private List<ProtoChunk> buildProtoChunks(List<Block> blocks, int maxChars) {
        List<ProtoChunk> result = new ArrayList<>();
        List<Block> buf = new ArrayList<>();
        int bufLen = 0;
        String headingPath = "";

        Deque<String> headingStack = new ArrayDeque<>();
        Deque<Integer> levelStack = new ArrayDeque<>();

        for (Block block : blocks) {
            if (block instanceof HeadingBlock) {
                HeadingBlock hb = (HeadingBlock) block;
                // 标题 → flush buffer，更新路径
                if (!buf.isEmpty()) {
                    result.add(materializeProto(buf, headingPath));
                    buf = new ArrayList<>();
                    bufLen = 0;
                }
                while (!levelStack.isEmpty() && levelStack.peekLast() >= hb.level()) {
                    levelStack.pollLast();
                    headingStack.pollLast();
                }
                levelStack.addLast(hb.level());
                headingStack.addLast(hb.title());
                headingPath = joinPath(headingStack);
                continue;
            }

            if (block instanceof CodeBlock || block instanceof TableBlock) {
                // 原子类型 → flush buffer → 独占 chunk
                if (!buf.isEmpty()) {
                    result.add(materializeProto(buf, headingPath));
                    buf = new ArrayList<>();
                    bufLen = 0;
                }
                result.add(materializeProto(List.of(block), headingPath));
                continue;
            }

            // ParagraphBlock / ImageBlock → 可合并
            int blockLen = blockLength(block);
            if (bufLen + blockLen > maxChars && !buf.isEmpty()) {
                result.add(materializeProto(buf, headingPath));
                buf = new ArrayList<>();
                bufLen = 0;
            }
            buf.add(block);
            bufLen += blockLen;
        }

        if (!buf.isEmpty()) {
            result.add(materializeProto(buf, headingPath));
        }
        return result;
    }

    /** 将一组 Block 物化为 ProtoChunk（display + embedding 分离） */
    private ProtoChunk materializeProto(List<Block> blocks, String headingPath) {
        StringBuilder display = new StringBuilder();
        StringBuilder embed = new StringBuilder();

        for (Block block : blocks) {
            if (block instanceof ParagraphBlock) {
                ParagraphBlock pb = (ParagraphBlock) block;
                display.append(pb.text()).append("\n\n");
                // 纯段落不单独生成 embeddingText — 由调用方回退到 content
            } else if (block instanceof CodeBlock) {
                CodeBlock cb = (CodeBlock) block;
                display.append(cb.originalText()).append("\n\n");
                embed.append("[").append(cb.language().isEmpty() ? "代码" : "代码:" + cb.language())
                    .append("]\n").append(summarizeCode(cb.code())).append("\n\n");
            } else if (block instanceof TableBlock) {
                TableBlock tb = (TableBlock) block;
                display.append(tb.originalText()).append("\n\n");
                embed.append(buildTableKv(tb)).append("\n\n");
            } else if (block instanceof ImageBlock) {
                ImageBlock ib = (ImageBlock) block;
                display.append("![").append(ib.alt()).append("](")
                    .append(ib.url()).append(")\n\n");
                if (!ib.alt().isEmpty()) {
                    embed.append("[图片: ").append(ib.alt()).append("]\n\n");
                } else {
                    embed.append("[图片: ").append(ib.url()).append("]\n\n");
                }
            } else if (block instanceof HeadingBlock) {
                HeadingBlock hb = (HeadingBlock) block;
                // 理论上不会出现在 block 列表里，此处兜底
                display.append("#".repeat(hb.level())).append(" ")
                    .append(hb.title()).append("\n\n");
            }
        }

        return new ProtoChunk(
            display.toString().stripTrailing(),
            embed.toString().stripTrailing(),
            headingPath
        );
    }

    private String summarizeCode(String code) {
        String[] lines = code.split("\n");
        int preview = Math.min(3, lines.length);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < preview; i++) {
            sb.append(lines[i]);
            if (i < preview - 1) sb.append("\n");
        }
        if (lines.length > preview) sb.append("\n...");
        return sb.toString();
    }

    private String buildTableKv(TableBlock tb) {
        StringBuilder sb = new StringBuilder();
        for (List<String> row : tb.rows()) {
            for (int c = 0; c < Math.min(tb.headers().size(), row.size()); c++) {
                if (c > 0) sb.append("; ");
                sb.append(tb.headers().get(c)).append(": ").append(row.get(c));
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private int blockLength(Block block) {
        if (block instanceof ParagraphBlock) {
            return ((ParagraphBlock) block).text().length();
        } else if (block instanceof ImageBlock) {
            return ((ImageBlock) block).originalLine().length();
        }
        return 0;
    }

    // ================================================================
    // Phase 3: ChunkPacker — overlap + minChars 合并
    // ================================================================

    private List<ProtoChunk> packChunks(List<ProtoChunk> proto, int overlap, int minChars) {
        if (proto.isEmpty()) return List.of();
        if (proto.size() == 1) return proto; // 单 chunk，无需 overlap

        List<ProtoChunk> result = new ArrayList<>();
        result.add(proto.get(0));

        for (int i = 1; i < proto.size(); i++) {
            ProtoChunk prev = proto.get(i - 1);
            ProtoChunk curr = proto.get(i);

            String overlapDisplay = extractSmartOverlap(prev.displayContent(), overlap);
            String overlapEmbed = extractSmartOverlap(prev.embeddingText(), overlap);

            if (!overlapDisplay.isEmpty()) {
                result.add(new ProtoChunk(
                    overlapDisplay + "\n\n" + curr.displayContent(),
                    overlapEmbed.isEmpty() ? curr.embeddingText()
                        : overlapEmbed + "\n\n" + curr.embeddingText(),
                    curr.headingPath()
                ));
            } else {
                result.add(curr);
            }
        }

        // minChars 合并
        return mergeSmallChunks(result, minChars);
    }

    private List<ProtoChunk> mergeSmallChunks(List<ProtoChunk> proto, int minChars) {
        if (proto.size() <= 1) return new ArrayList<>(proto);

        List<ProtoChunk> merged = new ArrayList<>();
        merged.add(proto.get(0));

        for (int i = 1; i < proto.size(); i++) {
            ProtoChunk curr = proto.get(i);
            if (curr.displayContent().length() < minChars) {
                // 合并到前一个
                ProtoChunk last = merged.remove(merged.size() - 1);
                merged.add(new ProtoChunk(
                    last.displayContent() + "\n\n" + curr.displayContent(),
                    last.embeddingText() + "\n\n" + curr.embeddingText(),
                    last.headingPath()  // 保留前一个的 heading path
                ));
            } else {
                merged.add(curr);
            }
        }
        return merged;
    }

    // ================================================================
    // 工具方法
    // ================================================================

    /** 获取围栏信息：```java → "java"，``` → ""，非围栏 → null */
    private String getFenceInfo(String line) {
        String trimmed = line.trim();
        if (trimmed.startsWith("```")) {
            return trimmed.substring(3).trim();
        }
        if (trimmed.startsWith("~~~")) {
            return trimmed.substring(3).trim();
        }
        return null;
    }

    /** Markdown 标题级别：1~6，非标题 → 0 */
    private int getHeadingLevel(String line) {
        if (line.startsWith("# ")) return 1;
        if (line.startsWith("## ")) return 2;
        if (line.startsWith("### ")) return 3;
        if (line.startsWith("#### ")) return 4;
        if (line.startsWith("##### ")) return 5;
        if (line.startsWith("###### ")) return 6;
        return 0;
    }

    private String stripHeadingMarkers(String headingLine) {
        return headingLine.replaceFirst("^#+\\s*", "");
    }

    private String joinPath(Deque<String> stack) {
        return stack.isEmpty() ? "" : String.join(" > ", stack);
    }

    /** 解析独立图片行：![alt](url) → ImageInfo，非图片 → null */
    private ImageInfo parseImageLine(String line) {
        if (line.matches("^!\\[.*?\\]\\(.*?\\)$")) {
            int altStart = line.indexOf('[') + 1;
            int altEnd = line.indexOf(']');
            int urlStart = line.indexOf('(') + 1;
            int urlEnd = line.lastIndexOf(')');
            return new ImageInfo(
                line.substring(altStart, altEnd),
                line.substring(urlStart, urlEnd)
            );
        }
        return null;
    }

    /**
     * 智能重叠提取：从文本末尾取 overlap 长度，对齐到段落边界。
     */
    private String extractSmartOverlap(String text, int overlapChars) {
        if (text == null || text.isEmpty() || overlapChars <= 0) return "";
        if (text.length() <= overlapChars) return text;

        int startPos = Math.max(0, text.length() - overlapChars);
        int boundary = text.indexOf("\n\n", startPos);
        if (boundary >= 0 && boundary < text.length() - 2) {
            return text.substring(boundary + 2);
        }
        int prevBoundary = text.lastIndexOf("\n\n", startPos);
        if (prevBoundary >= 0) {
            return text.substring(prevBoundary + 2);
        }
        return text.substring(startPos);
    }

    /** 去除 YAML frontmatter */
    private String removeFrontmatter(String content) {
        if (content.startsWith("---")) {
            int end = content.indexOf("---", 3);
            if (end > 0) {
                return content.substring(end + 3).trim();
            }
        }
        return content;
    }

    // ================================================================
    // 内部类型
    // ================================================================

    /** Block 类型（内部 sealed 层次） */
    private sealed interface Block permits
        ParagraphBlock, HeadingBlock, CodeBlock, TableBlock, ImageBlock {}

    private record ParagraphBlock(String text) implements Block {}
    private record HeadingBlock(int level, String title) implements Block {}
    private record CodeBlock(String language, String code, String originalText) implements Block {}
    private record TableBlock(List<String> headers, List<List<String>> rows, String originalText) implements Block {}
    private record ImageBlock(String alt, String url, String originalLine) implements Block {}

    /** 图片解析中间结果 */
    private record ImageInfo(String alt, String url) {}

    /**
     * 中间 chunk 表示：已分离 display 和 embedding 文本，等待 Packer 后处理。
     */
    private record ProtoChunk(String displayContent, String embeddingText, String headingPath) {}
}
