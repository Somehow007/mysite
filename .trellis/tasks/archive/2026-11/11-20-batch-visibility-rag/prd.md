# 批量改可见性不发 RAG 事件 → 私有文章向量残留可被检索

日期：2026-11-20 ｜ 状态：**已实现并验证**（verify quick 绿，待部署）

## 问题

`ArticleServiceImpl.batchUpdateVisibility`（`ArticleServiceImpl.java:273`，入口 `PUT /v1/articles/visibility/batch`）
批量更新 `visibility` 后只刷了 ES 索引和文章详情缓存，**没有发布任何 RAG 事件**。

RAG 侧的设计是「公开性门」前置：`KnowledgeDocumentService.syncArticle(article)`（1 参，事件路径）
开头判断 `isPubliclySyncable`（`published==1 && visibility∈{null,0}`），
不满足 → `removeArticle(articleId)` 清掉所有 KB 的向量/分块/文档（`KnowledgeDocumentService.java:85-91`）。
注释明确写了意图：「取消发布或转私有时走 removeArticle 清掉已入库向量，否则旧分块仍可经 AI 问答泄漏非公开内容」。

批量路径不发事件 → 门禁永远不触发：

- **公开→私有**：向量残留 → 私有内容仍可被**任意登录用户**（USER 10次/h）经 KB 检索命中，
  RAG 检索链路（`RetrievalEngine`/`PgvectorVectorStore`）本身**不做可见性过滤**（按设计，门在入库侧）→ **隐私泄漏**。
- **私有→公开**：若此前是经单篇 `updateArticle` 转私有的，向量已被清掉 → 批量转公开后文章**静默缺席知识库**，
  直到下一次内容编辑触发 `ArticleUpdatedEvent` 才补回（效果缺失，非泄漏）。

单篇 `updateArticle`（`:200` 发 `ArticleUpdatedEvent`）和 `deleteArticle`（`:254` 发 `ArticleDeletedEvent`）均正常。
`updateArticleTop` 刻意不发（置顶不影响可检索性，注释已说明）。排查确认全仓仅这两个入口改 visibility，
无其他漏发事件的路径。

## 方案演进：事件机制 + 软隐藏（最终定稿）

### 事件侧（方案 B，审核通过）

新增 `ArticleVisibilityChangedEvent`（携带 `List<ArticleDO>`）+
`ArticleEventListener.handleVisibilityChanged` 用 `@TransactionalEventListener(AFTER_COMMIT, fallbackExecution=true)`
——与 `ArticleDeletedEvent` 同模式，事务提交后才动 PG，消除 commit 前派发竞态。
`batchUpdateVisibility` 循环置值后 publish 一次批量事件；全部已是目标状态时提前 return。

### RAG 侧：物理删除 → 软隐藏（用户拍板，成本/性能/效果均更优）

原设计走 `syncArticle` 公开性门 → `removeArticle` **物理删除**向量+分块+文档。
用户质疑「删掉可惜」，排查发现关键事实：**检索 SQL 本就 `WHERE d.status='READY'`**
（`PgvectorVectorStore`），状态非 READY 即对检索不可见——无需删除。

定稿为**软隐藏**：新增文档状态 `HIDDEN`，向量/分块/明文全部保留：

- **转私有/草稿**：`hideArticle(articleId)` → `docMapper.hideArticleDocs(sourceRef)`
  一条 `@Update`：`READY/PENDING/CHUNKING → HIDDEN`（FAILED 不动，本就不可检索且避免半截向量误翻回）。
- **恢复公开 + 内容未变**：hash 门禁 `READY|HIDDEN + hash 匹配` → `updateById` 翻回 READY，**零 embedding**。
  草稿↔发布反复切换同样免费（白赚的场景）。
- **恢复公开 + 内容已变**：hash 不匹配 → 正常重建（删旧档→建档→embed），与删除方案同价。
- **真删除文章**：仍走 `removeArticle` 硬删，不受影响。
- **入库竞态兜底**：Phase C 原 `updateById(READY)` 会无条件覆盖 HIDDEN →
  改为 `docMapper.markReadyUnlessHidden(id)` 条件 UPDATE（`status<>'HIDDEN'`），
  入库期间被隐藏的 doc 保持 HIDDEN，向量照写但不可见。

代价（已接受）：`t_knowledge_chunk.content` 明文仍留 PG（检索不可见）；新状态值需前端/文档同步；
hide 与在途 ingest 存在秒级窄竞态（向量写完但状态保持 HIDDEN，方向安全）。

### 否决方案（存档）

- **A：复用 `ArticleUpdatedEvent`** —— `@EventListener` commit 前派发有回滚竞态，弃。
- **C：检索侧可见性过滤** —— 需 schema 冗余+热路径 join，破坏「门在入库侧」设计，弃。
- **D：Service 直接调 RAG** —— 破坏模块解耦，弃。
- **物理删除（原默认）** —— 功能正确但每次翻转都重付 embedding 成本，软隐藏全面占优。

## 实施清单

| 层 | 改动 |
|---|---|
| `ArticleVisibilityChangedEvent`（新） | `ragent/ingestion/`，携带 `List<ArticleDO>` |
| `ArticleEventListener` | +`handleVisibilityChanged` AFTER_COMMIT 监听，逐篇 `syncArticle` |
| `ArticleServiceImpl.batchUpdateVisibility` | 同值提前 return + publish 批量事件 |
| `KnowledgeDocumentService` | 公开性门 `removeArticle`→`hideArticle`；hash 门加 HIDDEN 翻回分支；Phase C 改 `markReadyUnlessHidden` 条件翻转 |
| `KnowledgeDocumentMapper` | +`markReadyUnlessHidden`/`hideArticleDocs` 两条 `@Update`（替代 LambdaUpdateWrapper——显式 SQL 可测，集成测试 JDBC stub 可行） |
| `KnowledgeDocumentController` | `syncKb`/`reprocessDoc` 非公开分支 `removeArticle`→`hideArticle`；reprocessDoc 把可见性检查提到删向量之前 |
| 前端 | `DocStatus` union + `KnowledgeManageView` 加 HIDDEN 筛选项「已隐藏」 |
| 文档 | `ragent-schema.sql` 状态注释、`ragent.mdc`、`CLAUDE.md` 状态机描述同步 |

## 验证

- `./mvnw test -Dtest=KnowledgeDocumentServiceTest,ArticleServiceImplVisibilityTest`：**35/35 绿**
  （新增 HideArticle 3 例、HIDDEN 翻回 3 例、事件发布断言；公开性门断言改为验证 `hideArticleDocs`）
- `./mvnw test -Dtest=Phase2EndToEndTest,Phase3IntegrationTest`（真实 PG+embedding）：**15/15 绿**，
  Phase C 条件翻转在真实库落 READY 验证通过
- `./scripts/verify.sh` quick：**VERIFY OK**（hermetic 全量 + 前端 type-check）

## 遗留观察项（本次不做，记录备查）

- `ArticleCreatedEvent`/`ArticleUpdatedEvent` 的 `@EventListener` 同样存在 commit 前派发竞态；
  是否统一改 `AFTER_COMMIT` 值得单独评估。
- `ArticleBatchVisibilityReqDTO.ids` 无 `@Size` 上限；ragAsyncExecutor 队列 100，
  极端大批量公开化可能溢出被拒。现实中后台多选为页级，风险低，可后续加 `@Size(max=100)`。
- **存量脏数据**：上线前若已有「批量转私有但向量仍 READY」的文章，软隐藏后首次
  `ArticleVisibilityChangedEvent`/单篇编辑会补隐藏；更稳妥是部署后跑一次 SQL 核对
  `t_knowledge_document`（status='READY'）vs `t_article`（published/visibility）清残留。
- HIDDEN 文档明文 chunk 留存是软隐藏的固有代价；如未来需要彻底清除，
  可加一个「彻底删除私有文章 RAG 数据」的运维端点（当前无此需求）。

## Resolution

2026-11-20 实现完成并上线：

- commit `f9f86c2`（17 文件，+412/-45），已 push 并经 `server-deploy.sh` 部署（版本 f9f86c2，健康检查全过）
- 事件走 AFTER_COMMIT 批量事件，RAG 侧按用户拍板改软隐藏（HIDDEN）
- `syncArticle` 公开性门对非公开文章调 `hideArticle`（跨 KB 状态置 HIDDEN，向量保留）；
  hash 门禁放行 `READY|HIDDEN + hash 匹配` 翻回 READY（零 embedding）；
  Phase C 条件翻转 `markReadyUnlessHidden` 防入库期被隐藏覆盖
- verify quick 全绿（hermetic + 前端 type-check）；Phase2/3 集成测试 15/15（真实 PG 验证 READY 落库）
- **存量清理已执行**（部署后 PG↔MySQL 交叉核对发现）：
  - 10 篇私有文章（visibility=1）的 READY 文档 → `UPDATE status='HIDDEN'`（UPDATE 10）
  - 11 篇已删除文章（del_flag=1）+ 1 条孤儿 PENDING（source_ref=5）→
    向量 36 + 分块 36 + 文档 12 物理清除（单事务）
  - 清理后 PG 侧 ARTICLE 文档：READY 29 / HIDDEN 10，无残留

遗留：Created/Updated 事件 commit 前派发竞态、ids 无 @Size 上限（见上文备查）。
