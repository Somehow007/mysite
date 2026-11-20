# Ragent 项目分块策略详解

> 基于 Ragent 项目实际代码分析，生成日期：2026-07-21

---

## 一、分块策略体系

### 1.1 两种分块模式

```java
// bootstrap/.../core/chunk/ChunkingMode.java
public enum ChunkingMode {

    FIXED_SIZE("fixed_size", "固定大小", true) {
        // 默认：chunkSize=512, overlapSize=128
        // 参数类型：FixedSizeOptions
    },

    STRUCTURE_AWARE("structure_aware", "语义感知（Markdown友好）", true) {
        // 默认：targetChars=1400, maxChars=1800, minChars=600, overlapChars=0
        // 参数类型：TextBoundaryOptions
    };
}
```

每种模式绑定自己的类型安全参数，通过 Sealed 接口 `ChunkingOptions` 限制：

```java
// ChunkingOptions.java — Sealed 接口，只允许两种实现
public sealed interface ChunkingOptions
    permits FixedSizeOptions, TextBoundaryOptions { }

// FixedSizeOptions: chunkSize + overlapSize
// TextBoundaryOptions: targetChars + maxChars + minChars + overlapChars
```

### 1.2 策略工厂

```java
// ChunkingStrategyFactory.java
@Component
public class ChunkingStrategyFactory {
    private final EnumMap<ChunkingMode, ChunkingStrategy> registry;

    // Spring 构造注入所有 ChunkingStrategy Bean，自动注册
    public ChunkingStrategyFactory(List<ChunkingStrategy> strategies) {
        strategies.forEach(s -> registry.put(s.getType(), s));
    }
}
```

---

## 二、统一入口：StructuredChunkingService

```java
// bootstrap/.../core/chunk/StructuredChunkingService.java
// 整个分块系统的单一真相源

public List<VectorChunk> chunk(
        List<Block> blocks,        // 解析器产出的结构化 Block（可为空）
        String fallbackText,       // 纯文本兜底
        ChunkingMode mode,         // legacy 文本策略类型
        ChunkingOptions options,   // 分块参数
        Integer rowsPerChunk) {    // 表格每 chunk 最大行数

    // 路由逻辑（三条路径）：
    if (isWholeDocument(options)) {                     // ① chunkSize=-1 → 不分块
        return wholeDocumentChunk(blocks, fallbackText);
    }
    if (blocks != null && !blocks.isEmpty()) {          // ② 有 Block → block-aware 分发
        return blockAwareChunkerDispatcher.dispatch(blocks, toBlockChunkConfig(...));
    }
    if (StringUtils.hasText(fallbackText)) {            // ③ 纯文本 → legacy 文本策略
        return chunkingStrategyFactory.requireStrategy(mode).chunk(fallbackText, options);
    }
    return List.of();
}
```

**关键参数转换**：从 legacy `ChunkingOptions` 派生出 `BlockChunkConfig`，使得 block-aware 和文本策略共用同一组体量参数：

```java
private BlockChunkConfig toBlockChunkConfig(ChunkingOptions options, Integer rowsPerChunk) {
    int maxChars = firstPositive(cfg, "chunkSize", "targetChars", "maxChars");  // 默认 512
    int overlap = firstNonNegative(cfg, "overlapSize", "overlapChars");         // 默认 64
    return new BlockChunkConfig(maxChars, overlap, rowsPerChunk, 15, 10);
}
```

Block-aware 默认参数：
| 参数 | 默认值 | 说明 |
|------|--------|------|
| maxChars | 512 | 单块字符上限 |
| overlapChars | 64 | 块间重叠 |
| rowsPerChunk | 50 | 表格行数硬上限 |
| maxListItems | 15 | 短列表原子阈值 |
| listItemsPerChunk | 10 | 长列表每块项数 |

---

## 三、Legacy 文本策略（纯文本路径）

### 3.1 FixedSizeTextChunker

```
工作流程：
  文本预处理（修复 URL 断行、合并 CJK 软换行）
    → 按 chunkSize 间隔切分，overlapSize 重叠
    → 边界智能调整（最多回溯 overlap 字符）：
       ① 优先对齐换行符
       ② 退而求其次对齐中文句号（。！？）
       ③ 最后对齐英文标点（. ! ?），排除 URL 域名中的点
```

### 3.2 StructureAwareTextChunker

```
三阶段算法：

阶段1: segmentToBlocks()
  线性扫描文本，识别五种块类型：
    HEADING   = 匹配 ^#{1,6}\s+
    CODE      = ``` ... ``` 围栏
    ATOMIC    = ![]() / []() 整行
    PARA      = 空行分隔的其他文本

阶段2: packBlocksToChunks()
  贪心打包：累计字符直到接近 maxChars
  若当前 chunk < minChars，允许"忍一次超限"多吞一个 Block
  最后一个小 chunk 合并到前一个

阶段3: materialize() + overlap
  物化为 VectorChunk
  如果 overlap > 0，复制上一 chunk 尾部到下一 chunk 开头
```

---

## 四、Block-Aware 路径（结构化文档分块）

### 4.1 整体架构

当解析器产出了结构化的 `List<Block>`（包含 `ParagraphBlock`、`TableBlock`、`ImageBlock`、`CodeBlock`、`ListBlock`、`HeadingBlock`），走这条路径。

```
BlockAwareChunkerDispatcher.dispatch(blocks, config)
  │
  ├─ 遍历每个 Block：
  │   ├─ HeadingBlock → HeadingHandler.update(outlinePath)   // ★ 不产 chunk，只维护层级
  │   ├─ ParagraphBlock → ParagraphChunker.chunk()
  │   ├─ TableBlock → TableChunker.chunk()
  │   ├─ ImageBlock → ImageChunker.chunk()
  │   ├─ CodeBlock → CodeChunker.chunk()
  │   └─ ListBlock → ListChunker.chunk()
  │
  └─ ChunkPacker.pack(result)    // ★ 后处理：贪心合并 + 块级重叠
```

### 4.2 HeadingHandler：标题层级维护

```
不产生 chunk，只维护 outlinePath 注入到后续 chunk 的上下文中

H1 "A"     → ["A"]
  H2 "B"   → ["A", "B"]
  H2 "C"   → ["A", "C"]    （替换同级兄弟）
H1 "D"     → ["D"]          （重置到顶）
  H3 "E"   → ["D", "E"]    （H2 缺失，填补空白）
```

### 4.3 各类型 Chunker 详解

#### ParagraphChunker — 段落分块

按 `maxChars` 切分，步长 `step = maxChars - overlap`。简单字符级切分，不跨标题。

#### TableChunker — 表格分块（★ 最精彩的设计）

```java
// 每个 chunk 都包含完整表头
// 双重表示：

.content(markdown)         // 给人/LLM 看的：| 姓名 | 部门 | 工资 |
                            //                | 张三 | 技术 | 1.5w |

.embeddingText(kvText)     // 给向量模型看的：
                            //   姓名: 张三; 部门: 技术; 工资: 15000

.sectionContext(ctx)       // 表身份信息：
                            //   sheet=员工表; caption=2024年; headers=姓名, 部门, 工资
```

**为什么需要双重表示？**

Markdown 表格的列名与值靠**位置对齐**来表达关系，但 embedding 模型读不懂位置信息。改用 `列名: 值` 的 key-value 格式，把语义关系写进字面，sparse/dense 检索均更优。

`sectionContext` 随每个 chunk 嵌入，属于 **contextual chunking**——切碎的表格行也能识别它属于哪张表、哪几列。

Cell 清洗逻辑：
- 竖线 `|` 转义为 `\|` — 防止被误判为列分隔符
- 换行符替换为 `<br>` — 裸 `\n` 会从中间截断表格行

切割逻辑：
```
按 maxChars 体量预算累加行:
  groupCost += rowCost
  if size >= maxRows (硬上限) OR (非空 && groupCost + rowCost > budget)
    → 切块（flush 当前 group）
    → 开启新 group（表头随新 chunk 复制）
单行超预算时保持整行原子，自成一块
```

#### CodeChunker — 代码块（原子）

```java
// 代码块绝不拆分，保证语法完整性
// 渲染为 markdown fenced code block：
//   ```python
//   def foo():
//       pass
//   ```
```

#### ImageChunker — 图片块（原子）

```java
// 每张图一个 chunk
// content: ![caption](url) + 可选的描述文本
// embeddingText: 只用描述文本（无 URL 噪音），null 则 fallback 到 content
// assets: 附加 AssetRef，支持多模态检索
```

#### ListChunker — 列表分块

```java
// 短列表（项数 ≤ maxListItems，默认 15）：原子，一个 chunk
// 长列表：按 listItemsPerChunk（默认 10）分组
// 有序列表渲染时保持增量编号
```

### 4.4 ChunkPacker：后处理打包器 ★

```java
// blockaware/ChunkPacker.java
// 各类型 Chunker 只负责"单个 Block 内"的切分 → 天然"只拆不并"
// ChunkPacker 做跨 Block 的贪心合并，使块大小真正贴合预算

合并规则：
  可合并类型：PARAGRAPH, LIST, IMAGE    ← 文本流 + 图片引用
  原子类型：  TABLE, CODE               ← 保持结构完整，不参与合并

贪心算法：
  buffer 累积可合并块
  遇到原子块 / 加入新块会超 maxChars → flush buffer → 原子块单独落块
  flush 时做块级重叠（block-level overlap）：
    用上一块尾部若干个完整块（累计不超过 overlapChars）作为下一块的起点
    重叠计入 maxChars 预算，保证块体量不超上限

合并产物：
  content:     块内容用 "\n\n" 拼接
  outlinePath: 合并块的共同祖先路径（最长公共前缀）
  blockType:   同质保留 / 异质归为 PARAGRAPH
  sourceBlockIds: 去重并集
  assets:      去重并集
```

---

## 五、VectorChunk：统一分块 DTO

```java
// core/chunk/VectorChunk.java
public class VectorChunk {
    String chunkId;            // Snowflake 分布式 ID
    int index;                 // 文档内序号
    String content;            // 展示/LLM 用的文本
    String embeddingText;      // 向量化专用文本（可为 null，fallback 到 content）
    float[] embedding;         // 向量（@JsonIgnore）
    String blockType;          // HEADING/PARAGRAPH/TABLE/IMAGE/CODE/LIST/FORMULA/DOCUMENT
    List<String> outlinePath;  // 标题路径
    List<String> sourceBlockIds; // 追溯解析器 Block
    List<AssetRef> assets;     // 图片等资源引用
    String sectionContext;     // 表身份等上下文
}
```

---

## 六、ChunkEmbeddingService：向量化

```java
// core/chunk/ChunkEmbeddingService.java
public void embed(List<VectorChunk> chunks, String embeddingModel) {
    // 1. 跳过已有 embedding 的 chunk（幂等）
    // 2. 提取文本：优先 embeddingText → fallback content
    // 3. 调用 EmbeddingService.embedBatch(texts, modelId)
    // 4. List<Float> → float[] → VectorChunk.setEmbedding()
}
```

---

## 七、摄入管道中的分块位置

```
FetcherNode   → ParserNode   → EnhancerNode  → ChunkerNode → EnricherNode → IndexerNode
 (获取文档)      (解析为Block)   (LLM增强文本)   (★分块+向量化)  (补充元数据)    (写入向量库)
```

ChunkerNode 配置默认值：
```java
// 如果管道的 JSON 配置没指定：
strategy    = STRUCTURE_AWARE
chunkSize   = 512
overlapSize = 128
```

---

## 八、完整分块流程图

```
                         StructuredChunkingService（统一入口）
                                    │
          ┌─────────────────────────┼──────────────────────────┐
          ▼                         ▼                           ▼
    chunkSize=-1              blocks 非空                   纯文本 fallback
  整篇合成单 chunk      BlockAwareChunkerDispatcher        ChunkingStrategyFactory
  blockType="DOCUMENT"              │                       ├─ FixedSizeTextChunker
                                    │                       └─ StructureAwareTextChunker
                    ┌───────────────┼───────────────┐
                    ▼               ▼               ▼
            ParagraphChunker   TableChunker    ImageChunker
            CodeChunker        ListChunker     HeadingHandler
                    │               │               │    (只维护 outlinePath，不产 chunk)
                    └───────────────┴───────────────┘
                                    │
                              ChunkPacker
                       （贪心合并 → 块级重叠 → 多块融合）
                                    │
                              VectorChunk[]
                                    │
                        ChunkEmbeddingService
                  （批量调用 Embedding API → float[]）
                                    │
                              IndexerNode
                     （写入 Milvus/PgVector + ES关键词索引）
```

---

## 九、关键设计亮点

1. **单一真相源**：`StructuredChunkingService` 同时服务于摄入管道和知识文档管理，两处不再各写各的判断逻辑

2. **表格双重表示**：`content`（Markdown 展示）vs `embeddingText`（key-value 嵌入）是 Ragent 对 embedding 模型局限性最深刻的理解

3. **ChunkPacker 后处理**：各 Chunker "只拆不并"，Packer 统一做贪心合并 + 块级重叠 → 分离关注点，各 Chunker 职责单一

4. **Sealed 接口 + 枚举工厂**：ChunkingOptions 用 Java 17 sealed 限制子类型，每种模式绑定自己的类型安全参数，避免 `Map<String, Object>` 的类型混乱

5. **Block 类型多态分派**：通过 `instanceof` 模式匹配按 Block 具体类型路由到专属 Chunker，新增 Block 类型只需加一个 Chunker 实现

6. **标题不产 chunk **：HeadingBlock 只更新 `outlinePath` 注入后续 chunk 上下文，避免在标题处产生无意义的小 chunk

7. **块级重叠 vs 字符级重叠**：Block-aware 路径在 ChunkPacker 中做块级重叠（复制完整块，而不是切碎段落），保持了语义单元的完整性

---

## 十、文件索引

```
bootstrap/src/main/java/com/nageoffer/ai/ragent/
├── core/chunk/
│   ├── ChunkingStrategy.java              — 文本分块策略接口
│   ├── ChunkingStrategyFactory.java       — 策略工厂（Spring 收集 + EnumMap 注册）
│   ├── ChunkingMode.java                  — 分块模式枚举（FIXED_SIZE / STRUCTURE_AWARE）
│   ├── ChunkingOptions.java               — 参数 Sealed 接口
│   ├── FixedSizeOptions.java              — 固定大小参数
│   ├── TextBoundaryOptions.java           — 语义感知参数
│   ├── StructuredChunkingService.java     — ★ 统一入口
│   ├── ChunkEmbeddingService.java         — 批量向量化服务
│   ├── VectorChunk.java                   — 分块 DTO
│   ├── strategy/
│   │   ├── FixedSizeTextChunker.java      — 固定大小文本分块器
│   │   └── StructureAwareTextChunker.java — 结构感知文本分块器
│   └── blockaware/
│       ├── BlockAwareChunkerDispatcher.java — ★ Block 分派调度器
│       ├── BlockChunker.java                — Block 分块器泛型接口
│       ├── BlockChunkConfig.java            — 分块配置 record
│       ├── ChunkContext.java                — 分块上下文（outlinePath + config + index）
│       ├── ChunkPacker.java                 — ★ 后处理打包器
│       ├── HeadingHandler.java              — 标题层级管理
│       ├── ParagraphChunker.java            — 段落分块器
│       ├── TableChunker.java                — ★ 表格分块器（双重表示）
│       ├── ImageChunker.java                — 图片分块器
│       ├── CodeChunker.java                 — 代码分块器
│       └── ListChunker.java                 — 列表分块器
│
├── ingestion/
│   ├── node/ChunkerNode.java              — 摄入管道分块节点
│   ├── node/IndexerNode.java              — 索引写入节点（消费 chunk）
│   ├── domain/settings/ChunkerSettings.java — ChunkerNode 配置
│   ├── domain/context/IngestionContext.java — 管道上下文（传递 chunks）
│   └── engine/IngestionEngine.java        — 管道 DAG 引擎
│
└── knowledge/
    └── service/impl/
        ├── KnowledgeChunkServiceImpl.java  — 手动 chunk CRUD
        └── KnowledgeDocumentServiceImpl.java — 文档处理调用 chunk
```
