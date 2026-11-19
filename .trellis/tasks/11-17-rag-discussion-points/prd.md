# RAG 模块摸底与可探讨点（讨论记录）

> 2026-11-17 RAG 模块专项摸底。只读调研，未改代码。
> 目的：沉淀「现状已确认、值得后续讨论/立项」的问题清单，供新会话快速取用。
> 每条附代码证据；行号以摸底时点为准，漂移时以符号名为准。

## 0. 快速入口（新会话定位用）

- 管道入口：`ragent/service/RagChatService.java` `doChat()`（限流→会话→改写→意图→检索→Prompt→LLM→落库）
- SSE 端点：`ragent/controller/RagChatController.java` `GET /v1/rag/chat/stream`
- 检索：`ragent/core/RetrievalEngine.java` + `ragent/vector/PgvectorVectorStore.java`（余弦 `<=>`，HNSW）
- 入库：`ragent/ingestion/ArticleEventListener.java` → `ragent/service/KnowledgeDocumentService.java`（`@Async`，公开性门 published=1 且 visibility∈{null,0}）
- LLM 路由：`ragent/llm/RoutingLLMService.java`（优先级链+熔断，首 token 前才可降级）
- 运行时配置：`ragent/llm/LlmRuntimeConfigService.java`（PG `t_llm_provider_setting` 覆盖 yaml，Key `enc:v1` 加密；换 embedding 模型/维度→清全库向量+文档标 FAILED）
- 计费：`ragent/usage/`（UsageContext+LlmUsageRecorder）+ `ragent/pricing/`（价卡版本化）+ `ragent/billing/`（官方余额/配额探针）
- 前端：`mysite-frontend/src/api/rag.ts`（唯一 SSE fetch）、`composables/useChat.ts`、`useChatLauncher.ts`、`components/chat/ChatWidget.vue`；入口 `HomeAskPanel`/`AskArticleCard`/`SelectionPopover`
- 管理端：`/v1/admin/ai/**`（`AdminAiController`），前端 `AiManageView.vue`（logs/dashboard/prices/providers 四页签）
- PG 表 12 张：知识库 4（kb/document/chunk/vector）+ 会话 2 + intent 1 + 计费 5（usage/provider_setting/model_price/holiday/balance_snapshot）
- 近期演进：11-16 会话归属切 user_id 两阶段已归档（`archive/2026-11/11-16-rag-conversation-security`）；11-17 计费看板 P1–P4 已落地（`11-17-ai-cost-accuracy`，P5 可选项未做）

## 1. 可探讨点

### ① 同一文章向量化 N 次（KB 数 = embedding 计费倍数）

`KnowledgeDocumentService.syncArticle(ArticleDO)` 遍历所有 enabled KB，逐 KB 独立 `chunker.chunk` + `embeddingService.embedBatch`——同一文章内容被向量化 N 遍，向量本体相同仅 `kb_id` 不同，embedding 费用线性乘 KB 数。
证据：`KnowledgeDocumentService.java` `syncArticle(article)` 循环 + `syncArticle(article, kbId)` 内 chunk/embed 段。
现状缓解：个人博客 KB 实际启用数少，浪费有限。
可探讨：embedding 结果按 chunk 文本复用（vectors 表仍按 kb_id 展开）、或 chunk/vector 表结构去重。

### ② ReadingContext 全文注入 system prompt

文章页 `articleId` → `ArticleReadingContextService.load` 返回**全文** `ReadingContext`；`PromptTemplate.appendReadingContextSection` 把 `reading.content()` 整段拼进 system prompt，与检索片段并存时上下文很长。`maxTokens=2048` 只限输出，输入侧 token 成本不受控。
证据：`ArticleReadingContextService.java` `load()`；`PromptTemplate.java` `appendReadingContextSection`；`RagChatService.java` general/intentAware 两条路径都注入。
可探讨：截断（前 N 字/按标题结构取段）、先走检索命中片段替代全文、或对长文做轻量摘要。

### ③ `t_rag_intent` 字段半闲置

- `IntentResult.targetKbId` 注释自承「历史字段，管道不按它选库」；`needsGuidance` 注释「本轮不消费」——分类器解析并透传但管道不消费。
- `t_rag_intent.kb_id` 同理透传不消费；`priority` 仅用于 `IntentMapper.listEnabled` 的 `ORDER BY priority DESC`（决定进 prompt 的候选顺序）。
- 旧领域意图 id=2/3（读书笔记/学习笔记）`enabled=false` 保留在种子里。
证据：`IntentResult.java` 字段注释；`IntentClassifier.java` 解析段；`IntentMapper.java:19`；`ragent-schema.sql` / `RagentSchemaMigration` 种子。
可探讨：清理死字段/死种子，或真正启用（如意图定向 KB 检索——目前检索范围 100% 由用户 kbIds 决定）。

### ④ 文章每次编辑 = 全量重建向量

`ArticleUpdatedEvent` → `syncArticle` → 删旧向量/分块/文档 → 重新 chunk + `embedBatch` × 每个 KB。无 debounce、无内容 diff——只改错别字也全量重建，编辑频繁时 embedding 费用线性放大。
证据：`ArticleEventListener.java` `handleArticleUpdated`；`KnowledgeDocumentService.java` `syncArticle` 幂等删旧段。
可探讨：content hash 比对跳过未变、编辑防抖合并、或只重建变更 chunk（需 chunk 级 diff，复杂度高）。

### ⑤ 限流维度是 IP 不是 user

`ChatRateLimiter` Redis key = `rag:chat:rl:{clientIp}`：同 IP 多账号共享额度；NAT/校园网/公司网络可能误伤；换 IP/代理可绕过。角色阈值本身按 role 区分，但计数桶只有 IP。
证据：`ChatRateLimiter.java` `check()`。
可探讨：以 `user_id` 为主维度、IP 兜底（或双维度取严）；注意匿名已下线，登录态 userId 必有。

### ⑥ 历史会话不回 sources（出处卡丢失）

`ConversationManager.saveExchange` 把 sources JSONB 存进 `t_conversation_message.sources`，但 `GET /conversations/{id}/messages` 只回 `{role, content}`；前端 `switchConversation` 重建消息时 `sources: []` 硬编码。切回历史对话后出处卡不恢复。
证据：`RagChatController.java` `getMessages`；`ConversationManager.java` `saveExchange`；`useChat.ts` `switchConversation`。
可探讨：getMessages 解析 sources JSONB 回传（`SourceChunkDTO` 结构已在 DB 里，解析成本低）；注意 Snowflake ID 序列化与历史数据兼容。

## 2. 顺带观察（未列为正式探讨点）

- `docs/MAP.md`、`CLAUDE.md` 对 RAG 的记载已部分漂移：无 billing/pricing/usage 模块、PG 表数不符、意图三类模式未写明，以代码为准。
- `multiRetrieve` 子查询检索是**顺序**执行（注释：embedding API 并发限制），延迟 = 子查询数 × 单次检索。
- `PromptTemplate.formatContextWithKbName` 每请求内 `computeIfAbsent` 查 KB 名，跨请求无缓存（kb 数少时无感）。
- SSE emitter 超时 120s；单次 `maxTokens=2048`；每轮聊天最坏 LLM 调用 = REWRITE+CLASSIFY+EMBED(×子查询)+RERANK+CHAT 五段，均计入 `t_llm_usage`。
- `LlmRuntimeConfigService.update` 换 embedding 模型/维度是**重操作**：`migrateEmbeddingDimension` 或 `deleteAll` 清向量 + 所有文档 FAILED，需手动 `/sync` 重建——后台操作需提示成本。

## Resolution

- 2026-11-19 | ①④ 立项 → `.trellis/tasks/11-19-rag-ingest-dedup/prd.md`（方案 A 同步内复用 + B content_hash 门禁，设计两轮复审确认，拆两 commit 待实施）；②③⑤⑥ 未立项，候选池见 `docs/ragent/RAG问题与方案笔记.md` §2。
