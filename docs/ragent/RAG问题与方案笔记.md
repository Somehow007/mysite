# RAG 问题与方案笔记

> 给自己看的持续笔记：RAG 子系统「遇到什么问题 → 怎么定位 → 怎么权衡 → 怎么落地」。
> 用途：面试表达 + 理解业务 + 方案评判。不是设计文档，是问题复盘。
>
> **维护约定**
> - 每个问题一个条目，编号 `RAG-00X`；状态：`候选 / 分析中 / 待实施 / 已实施 / 已否决`
> - 每条必须带代码证据（文件 + 符号名；行号会漂移，符号名为准）
> - 面试话术用第一人称讲「问题 → 定位 → 权衡 → 结果」，不堆术语
> - 摸底总清单（6 个探讨点）见 `.trellis/tasks/11-17-rag-discussion-points/prd.md`

---

## 0. 入库链路速查（讲故事的主线）

```
ArticleServiceImpl 发布/更新文章
  → publishEvent(ArticleCreatedEvent / ArticleUpdatedEvent)   // Spring Event 解耦，blog 不感知 RAG
  → ArticleEventListener (@EventListener / @TransactionalEventListener)
  → KnowledgeDocumentService.syncArticle(article)             // @Async，公开性门 → 遍历所有 enabled KB
      → syncArticle(article, kbId) × K                        // 删旧档 → PENDING → 分块 → embedBatch → 写 chunk/vector → READY
```

关键文件：

| 环节 | 文件 | 要点 |
|---|---|---|
| 事件源 | `service/impl/ArticleServiceImpl.java` | 任何字段更新都发 `ArticleUpdatedEvent`；置顶路径刻意不发 |
| 监听 | `ragent/ingestion/ArticleEventListener.java` | 删除事件用 `AFTER_COMMIT`，避免事务回滚向量已删 |
| 编排 | `ragent/service/KnowledgeDocumentService.java` | 状态机 `PENDING→CHUNKING→READY|FAILED`，全程 try/catch 兜底 |
| 分块 | `ragent/chunking/MarkdownChunker.java` | block-aware：标题只维护路径、代码块原子、表格转 KV、display/embedding 文本分离 |
| 向量化 | `ragent/llm/embedding/BaiLianEmbeddingService.java` | 全局单例，模型/维度全局统一，`embedBatch` 按 10 条/批调 `/embeddings` |
| 存储 | `ragent/vector/PgvectorVectorStore.java` | pgvector 余弦 `<=>` + HNSW |

一句话链路：**文章正文 → Markdown 分块（≤50 块）→ embedding API → PG chunk/vector 表，按 KB 各存一份**。

---

## 1. 问题条目

### RAG-001 同一文章向量化 N 次：KB 数 = embedding 计费倍数

**状态**：已实施（A=`9ced7b4` 三段式复用、B=`941d7d7` content_hash 门禁，两个 commit）；**实施权威规格**见 `.trellis/tasks/archive/2026-11/11-19-rag-ingest-dedup/prd.md`｜**对应摸底点**：①

#### 现象

一篇文章发布/更新时，`syncArticle(article)` 遍历**所有 enabled KB**，每个 KB 独立走一遍「分块 → embedBatch → 写库」。K 个知识库 = 同一篇文章被向量化 K 遍。

#### 根因（代码证据）

- `KnowledgeDocumentService.syncArticle(ArticleDO)`：查 enabled KB 列表后 `for` 循环逐 KB 调双参 `syncArticle`（`KnowledgeDocumentService.java`）。
- 各 KB 的分块结果**逐字节相同**：`MarkdownChunker` 只读全局 `RagProperties`（size=800/overlap=100/minChars=400/maxPerDoc=50），而 `t_knowledge_base` 表里的 `chunk_size / chunk_overlap / chunking_mode` 三个字段建了但**从未被管线消费**——是死字段。
- 各 KB 的向量**完全相同**：embedding 服务是全局单例（模型/维度全局统一），`embeddingText` 相同 → 向量相同；K 份副本只差 `doc_id / chunk_id / kb_id` 外键。
- 顺带发现：双参 `syncArticle` 上的 `@Async` 在这里是**自调用**，被 Spring 代理绕过——实际整个 KB 循环跑在同一个异步任务里串行执行。线程模型没变（仍是一个 async 任务），但说明「每 KB 一个异步任务」的直觉是错的。

#### 成本量化

`embedBatch` 按 10 条/批调 HTTP。单篇文章 C 块（≤50），K 个 KB：

- 现状：K × ceil(C/10) 次 embedding 调用，usage 表记 K×C 条虚增记录
- 优化后：ceil(C/10) 次，向量仍按 KB 落 K 份副本

K=3、C=50 时 15 次 HTTP → 5 次。博客规模绝对费用不高，但延迟和计费看板的失真是实打实的。

**关键判断**：向量副本 K 份**不是浪费**——检索按 `kbIds` 过滤、删除按 `docId` 走，KB 级隔离是语义需要。要省的是 embedding API 调用和 chunk 计算，不是存储。

#### 方案

**A. 单次同步内复用（先做）**

1. 公开性检查 → 启用 KB 列表 → 对需要重建的 KB 先删旧档、建 `PENDING` 文档行（文档行是 per-KB 的，先建好拿各自 docId）
2. 分块一次、embedding 一次；embedding 失败 → 重建组所有文档标 `FAILED`（保留重试入口）
3. 逐 KB 写 chunk/vector 并标 `READY`；单 KB 写库失败只影响该 KB
4. `vectorStore.insert` 改批量：现在循环里**每 chunk 调一次 insert**（C 次 PG 往返/KB），应收集 `List<VectorEntry>` 一次插入
5. `t_llm_usage` 记真实的共享调用次数；`document_id` 仅展示语义，可挂首个文档或留空

实施洞察：`DocumentChunker.Chunk` 的 `docId/kbId` 字段在写库时**根本没被消费**（`chunkDO` 用的是 `doc.getId()/kb.getId()`），所以「分块一次」不需要改 chunker 签名，按 index/content/embeddingText 物化即可。

**B. `content_hash` 门禁（后做，需 DB 变更）**

- `t_knowledge_document` 加 `content_hash` 列；`RagentSchemaMigration` 幂等 DDL + `docker/init/ragent-schema.sql` 同步改
- hash 覆盖：正文 content + chunker 配置指纹（size/overlap/minChars/maxChunksPerDoc + 版本号）+ embedding model + dimension——防止改配置后 hash 仍匹配导致错误跳过
- 跳过条件：`status=READY` 且 hash 匹配 → 只更新 `doc.title`（文章标题不进 embedding）；`FAILED/PENDING/CHUNKING/NULL hash` 一律重建
- hash 门**只挂单参事件路径**；双参管理入口（`syncKb`/`reprocessDoc`/`addArticles`）保持强制重建——管理操作的语义就是「我要重建」
- 取消发布走 `removeArticle` 清光，重新发布时无旧档自然重建，行为不变

#### 明确不做

- **持久化 embedding cache**（按文本 hash 存向量）：复杂度高、博客规模收益有限
- **chunk 级 diff**（只重建变更的块）：对齐/索引重排问题复杂，投入产出比差
- **chunk/vector 表去 KB 化共享**：牵涉检索过滤、状态隔离、删除语义、存量迁移，现阶段保留各 KB 副本更稳

#### 取舍与行业对照

- Dify/FastGPT 是「数据源显式绑定 KB」；本项目博客场景简化为「全量同步所有启用 KB」，注释里写明了这个取舍
- 文档 RAG 的常见优化梯度：单次任务内去重 → 内容指纹门禁 → embedding cache → chunk 级增量。本项目停在前两级是合理的
- 这不是「性能优化」，是**计费正确性**问题——`t_llm_usage` 是花钱看板，虚增记录会污染成本核算

#### 面试话术

> 「文章入库我发现一个计费问题：每篇文章会同步到所有启用的知识库，每个库各做一遍 embedding——向量本体其实完全一样，只是外键不同。我把它拆成两层解决：一是单次同步内复用，分块和 embedding 只做一次，各 KB 只落向量副本；二是给文档加内容指纹（正文 + 分块配置 + embedding 模型），没变就跳过。向量副本保留是因为检索要按知识库隔离，省的是 API 调用不是存储。」

#### 实施注意

- 两个 commit 分开：先 A（复用+向量批量写），再 B（migration+hash 门+测试）
- `KnowledgeDocumentServiceTest` 的 embedding 调用次数断言要改；新增 hash 门测试（READY 同 hash 跳过 / FAILED 必重建 / NULL hash 重建 / 配置变更指纹变）
- 并发更新竞态是存量问题（两个事件并发跑同一文章），A+B 不加剧；如要根治可加 articleId 级锁，非必需

---

### RAG-002 文章任意字段更新都触发全量重建

**状态**：已实施（随 RAG-001-B 同 commit `941d7d7`：hash 门禁在消费端兜底，正文未变即跳过重建）｜**对应摸底点**：④

#### 现象

`ArticleServiceImpl.updateArticle` 对**任何字段**更新都 `publishEvent(ArticleUpdatedEvent)`——改标题、标签、封面、分类、摘要都会触发 K×C 次 embedding 重建，哪怕正文一个字没动。代码里唯一的例外是置顶路径（注释明写「避免触发 RAG 重向量化」），说明作者已意识到事件粒度过粗，但只堵了一个口子。

#### 根因

事件语义是「文章变了」，消费端语义是「重建向量」——中间缺一层「内容变了没有」的判断。生产者按字段发细分事件（ContentChanged / MetaChanged）看似优雅，但 `updateArticle` 是 wrapper 批量更新，diff 字段成本高且枚举会随 schema 漂移、容易漏；**消费端自己算指纹更可靠**——「不要信任生产者，消费端幂等兜底」是更稳的边界设计。

#### 方案

- **主方案**：RAG-001-B 的 `content_hash` 门禁天然解决——正文没变 → hash 匹配 → 跳过重建、只更新标题。一次设计吃掉两个浪费源
- **备选**：事件细分（只在 `content` 变化时发 UpdatedEvent）——可作为后续优化，但不作为依赖
- **防抖**：编辑器连续保存合并触发——博客编辑频率低，优先级最低

#### 面试话术

> 「更新事件一开始是『文章有变更就重建』，后来发现改个标题也要全量向量化。我没有去改事件粒度——生产者发什么事件、消费端信不信是两回事——而是在消费端加了内容指纹，正文没变就跳过，标题顺手更新。这个模式叫消费端幂等，比依赖生产者发对事件更抗漂移。」

---

## 2. 候选池（摸底已列、未深挖）

以下来自 11-17 摸底 prd，值得后续单独评估：

| # | 问题 | 一句话现状 | 初步判断 |
|---|---|---|---|
| ② | ReadingContext 全文注入 system prompt | 文章页全文进 prompt，输入 token 不受控（`maxTokens` 只限输出） | 值得做：截断/先走检索片段 |
| ③ | `t_rag_intent` 字段半闲置 | `targetKbId`/`needsGuidance`/`kb_id` 透传不消费，2 条旧种子 disabled | 清理或启用，工程债 |
| ⑤ | 限流按 IP 不按 user | Redis key 是 clientIp，NAT 误伤、换 IP 可绕过 | 值得做：user_id 主维度 + IP 兜底 |
| ⑥ | 历史会话不回 sources | DB 存了 sources JSONB 但 getMessages 不解析，前端硬编码 `[]` | 低成本高体验：解析回传即可 |

顺带观察（暂不立项）：`multiRetrieve` 子查询顺序执行（embedding API 并发限制）；换 embedding 模型/维度 = 清全库向量 + 文档标 FAILED 的重操作；SSE emitter 120s 超时 vs `maxTokens=2048`。

---

## Resolution

- 2026-11-19 建档。RAG-001 方案经两轮复审（A 复用 + B hash 门禁，B 修正了 hash 组成与跳过条件）；RAG-002 与 001-B 共享解法。待用户确认后按「A → B」拆 commit 实施。
- 2026-11-19 实施完成：`9ced7b4`（方案 A 三段式+向量批量写）+ `941d7d7`（方案 B content_hash 门禁+schema 双写），`verify.sh quick` 绿、KnowledgeDocumentServiceTest 22/22。RAG-001/RAG-002 同步标记已实施；任务档案归档 `.trellis/tasks/archive/2026-11/11-19-rag-ingest-dedup/`。
