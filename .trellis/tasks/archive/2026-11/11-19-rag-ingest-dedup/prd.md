# RAG 入库去重与内容门禁（摸底点①④ 立项）

> 2026-11-19 立项。来源：`.trellis/tasks/11-17-rag-discussion-points/prd.md` 点①④；
> 业务笔记：`docs/ragent/RAG问题与方案笔记.md` RAG-001/RAG-002（面试向，本文档为实施权威规格）。
> 状态：**设计已两轮复审确认，待实施**。拆两个 commit：A=同步内复用+向量批量写，B=content_hash 门禁。

## 1. 问题

### ① 同文向量化 ×KB 数（摸底点①）

`KnowledgeDocumentService.syncArticle(ArticleDO)` 遍历所有 enabled KB，逐 KB 独立
`chunker.chunk` + `embeddingService.embedBatch`。同一文章在 K 个 KB 的 chunk 文本、
embedding 文本、向量本体**逐比特相同**（chunk 配置全局、embedding 全局单例），
仅 `doc_id/chunk_id/kb_id` 外键不同 → embedding 计费 = K × 单篇 token 量，
`t_llm_usage` 记 K×C 条 EMBED（虚增，污染成本看板）。

### ④ 任意字段更新触发全量重建（摸底点④）

`ArticleServiceImpl.updateArticle` 对**任何字段**更新都发 `ArticleUpdatedEvent`——
改标题/标签/封面/分类/摘要都触发 K×C 次重建，正文没变也一样。
置顶路径已刻意不发事件（`ArticleServiceImpl` ~L344 注释），说明粒度问题已被部分意识到。

## 2. 现状代码事实（证据，行号会漂移以符号为准）

- `MarkdownChunker.chunk()` 只读 `RagProperties.chunk`（yaml：size=800/overlap=100/
  minChars=400/maxChunksPerDoc=50）；`t_knowledge_base.chunk_size/chunk_overlap/chunking_mode`
  三列是**死字段**（仅 `KnowledgeBaseService.toDTO` 回显，从不进管线）。
- embedding 全局单例 `BaiLianEmbeddingService`，`currentModel()` 返回运行时模型；
  `embeddingModelName(kb)` 恒等于运行时模型 → 各 KB 向量必然相同。
- `DocumentChunker.Chunk.docId/kbId` 在写库时**不被消费**（`chunkDO` 用 `doc.getId()/kb.getId()`）
  → 「分块一次」不需要改 chunker 签名。
- `syncArticle(article)` 内调 `this.syncArticle(article, kbId)`：**自调用绕过 @Async 代理**，
  K 个 KB 本就在同一异步线程串行 → 重构不改线程模型。
- `VectorStore.insert(List<VectorEntry>)` 接受 list 且内部 JDBC batch；现状每 chunk 调一次
  `List.of(entry)` → 每 KB 2C 次 PG 往返，可顺手批量化。
- `t_llm_usage.document_id` 仅透传 `LlmUsageLogDTO` 展示，无按文档聚合消费点。
- 2-arg `syncArticle` 调用方：`KnowledgeDocumentController.addArticles`（逐文章）/
  `syncKb`（逐 doc）/`reprocessDoc`（FAILED/READY 重处理）——均为**显式重建语义**。
- `KnowledgeDocumentService` 当前未注入 `RagProperties`（构造器 6 参）。

## 3. 最终设计 v2（已逐分支推演）

### 3.1 调用面（不变）

| 入口 | 调用方 | 语义 |
|---|---|---|
| `syncArticle(article)` 1-arg `@Async` | `ArticleEventListener`（created/updated） | 事件路径：**hash 门生效** |
| `syncArticle(article, kbId)` 2-arg `@Async` | `addArticles` / `syncKb` / `reprocessDoc` | 显式路径：**永远强制重建**，天然绕过 hash 门 |
| `removeArticle(articleId)` | deleted 事件 + 1-arg 公开性门 | 不动 |

### 3.2 方法结构

```
syncArticle(article)                        // @Async，事件路径
  ├─ 公开性门（不满足 → removeArticle(articleId)，原逻辑不变）
  ├─ enabled KBs（空 → warn + return，原逻辑）
  ├─ hash 门（新增）
  │    docMapper.findBySource("ARTICLE", ref) 一次 → kbId→doc Map
  │    for each enabled KB:
  │      doc != null && status=READY && contentHash 匹配
  │        → skip；若 doc.title ≠ article.title → updateById 刷新标题
  │      否则 → rebuild 名单
  │    rebuild 空 → log.info + return（零 API 调用）
  └─ ingestArticle(article, rebuildKbs)

syncArticle(article, kbId)                  // @Async，显式路径
  ├─ 公开性门 + kb null 检查（原逻辑前置，selectById 移出 try 无影响——此前也无 usage 记录）
  └─ ingestArticle(article, List.of(kb))    // 不过 hash 门

ingestArticle(article, List<KnowledgeBaseDO> kbs)   // private 共享核心，三段式
  Phase A（逐 KB try/catch 隔离）:
    findBySourceRef(kbId, ARTICLE, ref) → 删旧 vector/chunk/doc →
    插新 doc（PENDING + contentHash + title）→ 收集 (kb, doc) 对
    单 KB 失败 → 剔除该 KB 继续（对齐现状 per-KB 隔离）
  Phase B（一次）:
    chunker.chunk(content, 首docId, 首kbId) 一次（docId/kbId 仅占位，文本不消费）
    → 各 doc updateById 置 CHUNKING + chunkCount + charCount
    → embeddingText 经 LinkedHashMap<text,index> 去重 → embedBatch(uniqTexts) → 按 index 扩回
    chunk/embed 任一异常 → 所有已建档 doc 标 FAILED + failReason
  Phase C（逐 KB try/catch 隔离）:
    逐 chunk 插 KnowledgeChunkDO（新 id）→ 收集 VectorEntry →
    vectorStore.insert(该 KB 全部 entries) 一次批量 →
    kb.embeddingModel 对齐（usedModel 与现逻辑一致）→ doc READY
    单 KB 写库失败 → 仅该 doc FAILED，其他 KB 照常
  finally: UsageContext.close()
```

内部用 `private record KbDocPair(KnowledgeBaseDO kb, KnowledgeDocumentDO doc)` 承载 Phase A 产物。

### 3.3 失败语义（与现状逐条对齐）

| 失败点 | 行为 |
|---|---|
| Phase A 某 KB 删/插异常 | 该 KB 剔除继续；其他 KB 不受影响 |
| chunk/embed 异常（Phase B） | 所有已建档 doc 标 `FAILED`+fail_reason（doc 先于 embed 创建 → 失败可见、可 `reprocessDoc` 重试） |
| Phase C 某 KB 写库异常 | 仅该 doc FAILED；孤儿 chunk 行下次同步 `deleteByDocId` 自愈（同现状） |
| embedBatch 内部 10 条/批中途失败 | 整体抛异常 → 同 Phase B 失败路径（同现状粒度） |

### 3.4 content_hash 规格（钉死输入）

```
contentHash = sha256( article.content
                    | CHUNKER_VERSION                                    // MarkdownChunker 新增 public 常量，算法变更手动 bump（当前 "md-v3"）
                    | rag.chunk.size | rag.chunk.overlap
                    | rag.chunk.minChars | rag.chunk.maxChunksPerDoc     // 需注入 RagProperties
                    | embeddingService.currentModel()
                    | embeddingService.currentDimension() )               // EmbeddingService 新增 default 方法
```

- JDK `MessageDigest` SHA-256 hex（不用引依赖）；输入用 `\u0001` 分隔防拼接歧义。
- `contentHash(article)` 设 **package-private**，测试同包可取期望值。
- **跳过条件**：`status=READY && hash 相等`。`FAILED/PENDING/CHUNKING/NULL hash`（存量）一律重建
  ——存量 doc 首次事件同步后自愈回填。
- model/dimension 进 hash 的理由：yaml 直改 embedding 配置 + 重启**不走**
  `onGlobalEmbeddingChanged`（那只在后台 `LlmRuntimeConfigService.update` 里标 FAILED），
  不进 hash 的话被编辑过的文章会永远跳过、旧向量残留。
- `EmbeddingService` 新增 `default Integer currentDimension() { return null; }`，
  `BaiLianEmbeddingService` override 返回 `dimensions` 字段（与 `currentModel()` 同款模式）。
- `KnowledgeDocumentDO` 加 `contentHash` 字段 + `@JsonIgnore`（防漏进 `listDocs` 响应）；
  camelCase→snake_case 由 MyBatis-Plus 下划线映射自动处理。

### 3.5 schema 变更（两处同步）

- `docker/init/ragent-schema.sql`：`t_knowledge_document` 加 `content_hash VARCHAR(64)`
- `RagentSchemaMigration.afterPropertiesSet`：`ALTER TABLE t_knowledge_document
  ADD COLUMN IF NOT EXISTS content_hash VARCHAR(64)`（幂等，旧卷平滑升级）

### 3.6 测试改动清单

`KnowledgeDocumentServiceTest`（hermetic，mock 全部依赖）：

- 构造器 6→7 参（加 `RagProperties`，复用测试里已建的 props 实例）
- 现有 stub `findBySourceRef` 保留有效（Phase A 仍用它）；`findBySource` 不 stub 时
  Mockito 返回空 List → 全走重建，老用例不破
- 新增用例：
  - 多 KB（stub `selectList` 返回 2 个 KB）→ `embedBatch times(1)`、两 doc 均 READY、
    `vectorStore.insert` 每 KB 一次（list 批量）
  - READY doc + hash 匹配 → `embedBatch never()`、title 不同时被刷新、其他字段不动
  - READY doc + hash 不匹配 → 重建
  - FAILED doc + hash 匹配 → 仍重建（status 门）
  - 存量 NULL hash → 重建
  - embed 失败 → rebuild 组所有 doc FAILED
- `Phase2EndToEndTest` / `Phase3IntegrationTest`（needs-infra）：实现时核对是否有按 KB
  计 embed 次数的断言，同步修正；不阻塞 quick verify 但要保持语义正确

### 3.7 commit 拆分（AGENTS §3 一个 commit 一个关注点）

1. **commit 1（方案 A）**：`ingestArticle` 三段式重构 + 向量批量插 + `RagProperties` 注入
   （构造器先加参但 hash 未启用，或 A 内先不引 props——实现时选改动最小路径）
   + `KnowledgeDocumentServiceTest` 断言更新
2. **commit 2（方案 B）**：`content_hash` 列（migration + init sql + DO 字段）+
   `CHUNKER_VERSION` + `EmbeddingService.currentDimension()` + hash 门（仅 1-arg）
   + skip 路径 title 刷新 + 新增门禁测试

可选 commit 3：`articleId` 级同步锁（`ConcurrentHashMap` 锁桶）根治并发 update 竞态——
存量问题非本次必需，单独评估。

### 3.8 顺带收益（本次一并兑现）

- `vectorStore.insert` 每 KB 一次批量（原每 chunk 一次）→ 每 KB PG 往返 C→1
- `t_llm_usage` EMBED 记录 K→1 条，**计费回归真实**；`UsageContext.documentId` 记首个
  doc id（2-arg 单 KB 时归因精确；1-arg 多 KB 时仅展示字段，无聚合消费点）
- `traceId` 一个同步任务共用（原每 KB 一个），追踪更完整

### 3.9 明确不做

- 不改 chunk/vector 存储结构：per-KB doc/chunk/vector 拷贝保留（检索 `kb_id` 过滤、
  per-KB 状态隔离、按 docId 删除都依赖它；1024 维 float ≈ 4KB/条，存储冗余可忽略）
- 不做持久化 embedding cache（跨调用去重收益边际：仅「已入库文章加入新 KB」「瞬时失败重试」）
- 不做防抖、不做 chunk 级 diff（chunker 是位置贪心+overlap，改动即全漂，ROI 差）
- 不动 `ArticleServiceImpl` 事件发布面（hash 门在消费端兜底，比生产者判字段更抗漂移）
- 2-arg 显式路径不过 hash 门——管理员/接口操作 = 明确重建意图

### 3.10 遗留观察（不进本次范围，防止以后重复发现）

- `PUT /v1/rag/knowledge-bases/{id}` 可写入死字段 `chunk_size/embedding_model` 等，
  无害（不进管线）但误导——去重安全性依赖「字段被忽略」而非「字段恰好相同」
- yaml 级 embedding 模型切换不标存量 doc FAILED（hash 只能救「被编辑过」的文章）；
  可后续加启动对账：`SELECT DISTINCT model FROM t_knowledge_vector` ≠ 当前模型 → 标 FAILED
- `PgvectorVectorStore.search` 不按 `v.model` 过滤，新旧模型向量混排风险同上
- `t_knowledge_base` 的 chunk/embedding 配置列若未来真做 per-KB 配置，
  「分块一次」假设失效 → 届时需回退逐 KB chunk（代码注释写明该不变量）

## 4. 验证计划

```bash
./mvnw test -Dtest=KnowledgeDocumentServiceTest   # 迭代主测试
./scripts/verify.sh                              # quick：hermetic 全量 + 前端 type-check
```

前端零改动；`./mvnw compile` 由 verify 覆盖。

## Resolution

2026-11-19 实施完成，两 commit 落地：

- `9ced7b4` refactor(rag)：方案 A——`ingestArticle(article, kbs)` 三段式
  （Phase A 逐 KB 建档隔离 / Phase B 分块一次+embeddingText 去重 embedBatch 一次按 index 扩回 /
  Phase C 逐 KB 写 chunk+vectorStore.insert 整批+模型对齐+READY），
  UsageContext/traceId 一次同步共用，EMBED 记录 K→1。
- `941d7d7` feat(rag)：方案 B——`t_knowledge_document.content_hash`
  （ragent-schema.sql + RagentSchemaMigration 幂等 ALTER 双写）、DO 字段 @JsonIgnore、
  `MarkdownChunker.CHUNKER_VERSION="md-v3"`、`EmbeddingService.currentDimension()` default
  +BaiLian override、1-arg 事件路径 hash 门（READY+匹配才跳，skip 顺带刷 title）、
  2-arg 显式路径不过门。

验证：`./scripts/verify.sh quick` PASS（后端 hermetic 全量 + 前端 type-check）；
`KnowledgeDocumentServiceTest` 22/22（新增 MultiKb 3 例 + ContentHashGate 8 例）。
needs-infra 的 Phase2/Phase3 仅做构造器 +RagProperties 适配，无按 KB embed 计数断言，
未实跑（需 docker+BAILIAN_API_KEY）。

实现期小补充（超出 prd 字面、已评审过的合理加固）：
- embedBatch 返回数量 ≠ uniqTexts 数时 Phase B 显式抛 IllegalStateException（扩回前防御，
  原实现在 Phase C 按下标取也会炸，只是爆点不同）。
- `markFailed` 包 try/catch：FAILED 标记写库本身失败只记日志，不拖垮其他 KB。

遗留去向：§3.10 观察项全部不处理（per-KB 配置若未来落地需回退逐 KB chunk，
不变量已写进类注释）；可选 commit 3 articleId 级同步锁未做。
部署提示：旧卷靠 RagentSchemaMigration 自动补 content_hash 列；存量 doc NULL hash
在下次文章事件时自愈重建（每篇一次性 embed 成本，可接受）。
