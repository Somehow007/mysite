# MySite RAG + AI 交互集成实施方案

> 基于 [ragent-integration-design.md](./ragent-integration-design.md) 架构设计 +
> [ragent-project-summary.md](./ragent-project-summary.md) Ragent 学习笔记 +
> 当前 mysite 代码库实际结构，制定的详细落地计划。
>
> 编写日期：2026-07-12
> 最近修订：2026-07-17 —— 对照仓库现状全面修订：Phase 0 已完成部分如实标注；
> 修复若干设计问题（路由自注入、对话记忆不闭环、sources 事件无生产者、embedding
> 维度跨供应商冲突、百炼 rerank 接口格式、双数据源绑定坑等），详见 3.5 节"关键设计决策"。

---

## 目录

1. [学习目标与集成原则](#一学习目标与集成原则)
2. [当前代码库状态盘点](#二当前代码库状态盘点)
3. [整体架构与数据流](#三整体架构与数据流)
4. [Phase 0：开发环境准备（第 1-2 天）](#phase-0开发环境准备)
5. [Phase 1：LLM 抽象层 —— 理解 Agent 的"大脑"（第 3-5 天）](#phase-1llm-抽象层--理解-agent-的大脑)
6. [Phase 2：向量存储与文档摄取 —— 理解 RAG 的"记忆"（第 6-9 天）](#phase-2向量存储与文档摄取--理解-rag-的记忆)
7. [Phase 3：RAG 问答核心链路 —— 理解 Agent 的"思考"（第 10-14 天）](#phase-3rag-问答核心链路--理解-agent-的思考)
8. [Phase 4：前端聊天组件 —— 理解 Agent 的"脸面"（第 15-18 天）](#phase-4前端聊天组件--理解-agent-的脸面)
9. [Phase 5：知识库管理与部署（第 19-22 天）](#phase-5知识库管理与部署)
10. [学习检查清单（面试可用）](#六学习检查清单面试可用)
11. [后续扩展路线](#七后续扩展路线)
12. [关键文件对照表](#八关键文件对照表)

---

## 一、学习目标与集成原则

### 1.1 这次集成你要学会什么

| 学习目标 | 对应模块 | 核心理解 |
|----------|---------|---------|
| **LLM 调用抽象** | Phase 1 `llm/` 包 | 多供应商统一接口、OpenAI 兼容协议、SSE 流式解析 |
| **断路器模式** | Phase 1 `CircuitBreaker` | 三态熔断（CLOSED→OPEN→HALF_OPEN）、故障隔离、降级策略 |
| **向量嵌入** | Phase 2 `vector/` 包 | embedding 原理、pgvector 使用、HNSW 索引、相似度检索 |
| **文档分块策略** | Phase 2 `ingestion/` 包 | Markdown 感知分块、chunk overlap、embedding 批处理 |
| **RAG 问答管道** | Phase 3 `core/` 包 | 检索→重排序→上下文组装→生成 完整链路 |
| **对话记忆管理** | Phase 3 `memory/` 包 | 滑动窗口、摘要压缩、上下文窗口管理 |
| **SSE 流式输出** | Phase 4 前端 | EventSource API、流式渲染、打字机效果 |
| **双数据源** | Phase 2 config | Spring Boot 多数据源、MyBatis-Plus 分包扫描 |

### 1.2 集成原则

1. **最小侵入**：RAG 功能全部放在新包 `ragent/` 下，不动现有 Blog 代码
2. **先跑通再优化**：每个 Phase 做到"可运行、可验证"，不追求完美
3. **理解每一行代码**：不复制粘贴 Ragent，自己写一遍，加注释说明"为什么"
4. **博客规模**：不做企业级过度设计，但保留扩展点（接口抽象）
5. **外部 API 优先**：开发阶段先用百炼免费额度，Ollama 作为可选项

---

## 二、当前代码库状态盘点

### 2.1 已有的基础设施（可直接复用）

```
✅ Spring Boot 3.5.7 + Java 17          → 已升级完成（pom.xml，2026-07 核对）
✅ MyBatis-Plus 3.5.14                   → 已升级：mybatis-plus-spring-boot3-starter
                                           + mybatis-plus-jsqlparser（3.5.9+ 分页插件
                                           拆分为独立模块，必须单独引入，否则分页失效）
✅ MySQL 8.4 (docker)                    → 博客数据不动，新增 PG 存向量
✅ Redis 7 (docker)                      → 会话缓存、限流计数、断路器状态存储
✅ JWT + Spring Security                 → RAG 端点复用现有认证体系
✅ WebFlux (已引入依赖)                   → SSE 流式响应直接用
✅ Elasticsearch (条件启用)               → 后期可选开启关键词检索通道
✅ TransmittableThreadLocal (已引入)      → 异步上下文透传
✅ PostgreSQL 驱动 + pgvector 0.1.6       → 均已引入 pom.xml
✅ Hutool 5.8.27                         → 工具类
✅ Vue 3 + TypeScript + Tailwind CSS     → 聊天组件技术栈一致
✅ marked 18 / katex / pinia             → 前端 package.json 已有，无需新增
✅ Vite proxy /v1/* → localhost:8081     → 新 API 自动代理
```

### 2.2 需要新增的

```
🔧 PostgreSQL + pgvector 容器           → 已加入 docker-compose.yml ✅（ollama 段已注释预留）
🔧 双数据源配置                          → PrimaryDataSourceConfig（新增，主库 @Primary）
                                           + ragent/config/RagentDataSourceConfig（骨架已建，待实现）
                                           ⚠ config/ 下另有一个误建的同名空壳，Phase 2 时删除
🔧 LLM 调用层 (WebClient)               → ragent/llm/ 包（骨架已建 ✅：7 个接口/类，实现待写）
🔧 断路器                                → CircuitBreaker.java（骨架已建，待按 1.2.1 实现）
🔧 向量存储抽象 + pgvector 实现          → ragent/vector/ 包
🔧 文档分块器                            → ragent/ingestion/ 包
🔧 RAG 问答服务 + 限流器                 → ragent/service/ 包（含 ChatRateLimiter，见 3.7）
🔧 SSE 流式 Controller                  → ragent/controller/ 包
🔧 前端聊天浮窗组件                      → components/chat/ 包
🔧 前端聊天 composable                  → composables/useChat.ts
```

### 2.3 现有代码中需要小幅修改的地方

| 文件 | 修改内容 | 影响范围 |
|------|---------|---------|
| `pom.xml` | ✅ 已完成：Spring Boot 3.5.7、MyBatis-Plus 3.5.14（boot3 starter + jsqlparser）、pgvector 0.1.6 | 全局 |
| `docker/docker-compose.yml` | ✅ 已完成：postgres 服务（ollama 段已注释预留） | 基础设施 |
| `application.yaml` | 部分完成：`rag.datasource` 已配；llm providers 需按 Step 0.4 修订（删 first-packet-timeout、embedding 锁定说明、新增 rate-limit 段） | 配置 |
| `MysiteApplication.java` | `@MapperScan` 增加 `sqlSessionFactoryRef`（双数据源消歧，见 2.1） | 启动类 |
| `config/RagentDataSourceConfig.java` | 删除误建空壳（正确位置在 `ragent/config/`） | 清理 |
| `config/PrimaryDataSourceConfig.java` | 新增：主数据源显式声明 + `@Primary`（见 2.1） | 数据源 |
| `WebSecurityConfig.java` | `/v1/rag/chat/stream` 加入 permitAll 列表 | 安全配置 |
| `ArticleServiceImpl.java` | 发布/更新文章后发布 Spring Event | 事件发布 |
| `app/router/index.ts` | 添加 `/dashboard/knowledge` 路由（Phase 5） | 前端路由 |
| `DefaultLayout.vue` | 嵌入 `<ChatWidget />` 浮窗组件 | 全局布局 |

---

## 三、整体架构与数据流

### 3.1 集成后的包结构

```
src/main/java/io/github/somehow/mysite/
│
├── config/                    # ★ 修改：删除误建的 RagentDataSourceConfig 空壳，
│                              #        新增 PrimaryDataSourceConfig（主库 @Primary）
├── controller/                # 不变
├── service/                   # ★ 微小修改：ArticleServiceImpl 发事件
├── dao/                       # 不变（Blog 实体 + Mapper）
├── dto/                       # 不变
├── security/                  # ★ 修改：WebSecurityConfig permitAll 加 RAG 端点
├── commons/                   # 不变
├── elasticsearch/             # 不变
│
└── ragent/                    # ★★★ 全部新增 ★★★
    ├── config/
    │   ├── RagentDataSourceConfig.java      # PG 数据源 + MyBatis-Plus 配置
    │   └── RagAsyncConfig.java              # @Async 线程池配置
    │
    ├── llm/                                  # LLM 抽象层
    │   ├── LLMService.java                   # 统一聊天接口
    │   ├── LLMProvider.java                  # 供应商标记接口（防路由自注入，见 1.2.5）
    │   ├── EmbeddingService.java             # 统一嵌入接口（锁定百炼，不降级，见 1.2.7）
    │   ├── RerankService.java               # 统一重排序接口（百炼原生格式，见 3.2）
    │   ├── RoutingLLMService.java           # 模型路由器
    │   ├── CircuitBreaker.java              # 三态断路器
    │   ├── ChatRequest.java                 # 聊天请求 DTO（含 ChatMessage）
    │   ├── ChatEvent.java                   # SSE 事件模型（meta/sources/content/error/done）
    │   └── provider/
    │       ├── AbstractOpenAIProvider.java   # OpenAI 兼容基类
    │       ├── BaiLianProvider.java          # 阿里百炼
    │       ├── SiliconFlowProvider.java      # 硅基流动
    │       ├── AIHubMixProvider.java         # AIHubMix
    │       └── OllamaProvider.java           # 本地 Ollama
    │
    ├── vector/                               # 向量存储层
    │   ├── VectorStore.java                 # 向量存储接口
    │   └── PgvectorVectorStore.java         # pgvector 实现
    │
    ├── ingestion/                             # 文档摄取
    │   ├── DocumentChunker.java             # 分块器接口
    │   ├── MarkdownChunker.java             # Markdown 感知分块实现
    │   └── ArticleEventListener.java        # 文章事件监听器
    │
    ├── core/                                  # RAG 核心引擎
    │   ├── retrieval/
    │   │   └── RetrievalEngine.java         # 检索引擎（向量检索 → Rerank）
    │   ├── memory/
    │   │   └── ConversationManager.java     # 对话记忆管理
    │   ├── rewrite/
    │   │   └── QueryRewriter.java           # 查询重写（可选）
    │   └── prompt/
    │       └── PromptTemplate.java          # 提示词模板
    │
    ├── service/                               # RAG 业务服务
    │   ├── RagChatService.java              # RAG 问答核心服务
    │   ├── ChatRateLimiter.java             # 成本保护：IP 限流 + 长度上限（见 3.7）
    │   ├── KnowledgeBaseService.java        # 知识库管理
    │   └── KnowledgeDocumentService.java    # 文档管理
    │
    ├── controller/                            # RAG REST 控制器
    │   ├── RagChatController.java           # SSE 流式聊天端点
    │   ├── KnowledgeBaseController.java     # 知识库 CRUD
    │   └── KnowledgeDocumentController.java # 文档管理
    │
    ├── dao/                                   # RAG 数据层
    │   ├── entity/
    │   │   ├── KnowledgeBaseDO.java
    │   │   ├── KnowledgeDocumentDO.java
    │   │   ├── KnowledgeChunkDO.java
    │   │   ├── KnowledgeVectorDO.java
    │   │   ├── ConversationDO.java
    │   │   └── ConversationMessageDO.java
    │   └── mapper/
    │       ├── KnowledgeBaseMapper.java
    │       ├── KnowledgeDocumentMapper.java
    │       ├── KnowledgeChunkMapper.java
    │       ├── KnowledgeVectorMapper.java
    │       ├── ConversationMapper.java
    │       └── ConversationMessageMapper.java
    │
    └── dto/                                   # RAG 请求/响应 DTO
        ├── ChatStreamRequest.java
        ├── ChatMessageDTO.java
        ├── KnowledgeBaseDTO.java
        └── SourceChunkDTO.java
```

### 3.2 数据流全景（一次完整 RAG 问答）

```
用户在博客页面打开聊天浮窗
        │
        ▼
┌──────────────────────────────────────────────────────────┐
│ ChatWidget.vue                                            │
│ ┌──────────────────────────────────────────────────────┐ │
│ │ 用户输入："你写的这篇关于 Spring Security 的文章，    │ │
│ │             JWT 过滤器是怎么配置的？"                 │ │
│ └──────────────────────────────────────────────────────┘ │
│         │                                                 │
│         │ EventSource GET /v1/rag/chat/stream            │
│         │ ?q=...&conversationId=...&visitorId=...         │
└─────────┼─────────────────────────────────────────────────┘
          │
          ▼
┌──────────────────────────────────────────────────────────┐
│ RagChatController.chatStream()                            │
│   ├── 1. 从请求取 visitorId + 客户端 IP（限流用）          │
│   └── 2. 调用 ragChatService.chat(q, convId, visitorId, ip)│
└─────────┬────────────────────────────────────────────────┘
          │
          ▼
┌──────────────────────────────────────────────────────────┐
│ RagChatService.chat()                                     │
│                                                           │
│   Step 1: 加载对话记忆                                    │
│   ┌──────────────────────────────────────┐               │
│   │ ConversationManager.loadHistory()    │               │
│   │ └── PG t_conversation_message        │               │
│   │     查询最近 6 轮对话                  │               │
│   └──────────────────────────────────────┘               │
│         │                                                 │
│   Step 2: 查询向量化（把问题变成向量）                      │
│   ┌──────────────────────────────────────┐               │
│   │ EmbeddingService.embed(question)     │               │
│   │ └── 百炼 text-embedding-v4           │               │
│   │     → float[1024]                     │               │
│   └──────────────────────────────────────┘               │
│         │                                                 │
│   Step 3: 向量检索                                        │
│   ┌──────────────────────────────────────┐               │
│   │ RetrievalEngine.retrieve()           │               │
│   │ ├── PgvectorVectorStore.search(      │               │
│   │ │     embedding, topK=10)            │               │
│   │ │   └── PostgreSQL:                  │               │
│   │ │       SELECT ... FROM               │               │
│   │ │       t_knowledge_vector           │               │
│   │ │       ORDER BY embedding            │               │
│   │ │       <=> query_vector             │               │
│   │ │       LIMIT 10                     │               │
│   │ │                                    │               │
│   │ └── RerankService.rerank(           │               │
│   │       question, top10 → top5)       │               │
│   │     └── 百炼 gte-rerank              │               │
│   └──────────────────────────────────────┘               │
│         │                                                 │
│   Step 4: 组装 Prompt                                     │
│   ┌──────────────────────────────────────┐               │
│   │ PromptTemplate.build(                │               │
│   │   context = [检索到的5个chunk文本],    │               │
│   │   history = [最近6轮对话],            │               │
│   │   question = "JWT过滤器怎么配置的？"   │               │
│   │ )                                    │               │
│   │                                      │               │
│   │ 输出：                               │               │
│   │ "你是 mysite 博客的 AI 助手。         │               │
│   │  基于以下博客文章内容回答用户问题：    │               │
│   │                                      │               │
│   │  [来源1] 来自文章《Spring Security    │               │
│   │  实战》...                           │               │
│   │  [来源2] 来自文章《JWT认证方案》...    │               │
│   │  ...                                 │               │
│   │                                      │               │
│   │  对话历史：...                        │               │
│   │                                      │               │
│   │  用户问题：JWT过滤器是怎么配置的？     │               │
│   │                                      │               │
│   │  要求：基于提供的文章内容回答，        │               │
│   │  如果内容不足以回答请诚实说明。"       │               │
│   └──────────────────────────────────────┘               │
│         │                                                 │
│   Step 5: 流式生成 + SSE 推送                             │
│   ┌──────────────────────────────────────┐               │
│   │ RoutingLLMService.chatStream(prompt) │               │
│   │ ├── 尝试 百炼 qwen3-max (P1)         │               │
│   │ │   ├── 断路器检查: CLOSED ✓         │               │
│   │ │   ├── POST .../chat/completions    │               │
│   │ │   │   {"model":"qwen3-max",        │               │
│   │ │   │    "stream":true,              │               │
│   │ │   │    "messages":[...]}           │               │
│   │ │   └── SSE 流式解析 → Flux<String>  │               │
│   │ │                                    │               │
│   │ └── 如果百炼挂了：                    │               │
│   │     ├── SiliconFlow (P2)             │               │
│   │     └── AIHubMix (P3)                │               │
│   │     （型号以实施时实际可用为准）        │               │
│   └──────────────────────────────────────┘               │
│         │                                                 │
│   Step 6: 保存对话记录                                    │
│   ┌──────────────────────────────────────┐               │
│   │ ConversationManager.saveMessage()    │               │
│   │ └── PG t_conversation_message        │               │
│   └──────────────────────────────────────┘               │
│                                                           │
│   返回: SseEmitter (Spring MVC) / Flux<ServerSentEvent>   │
└─────────┬─────────────────────────────────────────────────┘
          │
          ▼  SSE 事件流：data: {"type":"meta","conversationId":123}
                        data: {"type":"sources","sources":[...]}
                        data: {"type":"content","delta":"J"}
                        data: {"type":"content","delta":"WT"}
                        data: {"type":"content","delta":"过滤器"}
                        ...
                        data: {"type":"done"}
┌──────────────────────────────────────────────────────────┐
│ ChatStreamWriter.vue                                      │
│   ├── 逐 token 渲染（打字机效果）                           │
│   ├── Markdown 实时渲染（代码高亮 + KaTeX 数学公式）        │
│   └── 完成后展示引用来源（[来源1] 来自文章《...》）         │
└──────────────────────────────────────────────────────────┘
```

### 3.3 数据库新增表概览（PostgreSQL，6 张表）

所有 RAG 相关表存在 PostgreSQL（不是 MySQL），通过第二数据源访问：

| 表名 | 用途 | 关键字段 |
|------|------|---------|
| `t_knowledge_base` | 知识库定义 | name, collection_name, embedding_model, chunk_size |
| `t_knowledge_document` | 文档记录 | kb_id, title, source_type(ARTICLE/UPLOAD), status, fail_reason, chunk_count |
| `t_knowledge_chunk` | 文档分块 | doc_id, chunk_index, content, char_count |
| `t_knowledge_vector` | 向量数据 | chunk_id, embedding vector(1024), HNSW 索引 |
| `t_conversation` | 对话会话 | user_id(可空), visitor_id, title, message_count |
| `t_conversation_message` | 对话消息 | conversation_id, role(USER/ASSISTANT), content, sources(JSONB) |

### 3.4 数据库新增表概览（MySQL，0 张表）

**RAG 功能不新增任何 MySQL 表**。对话记录、知识库元数据全部存在 PostgreSQL 中。

**为什么连对话也存 PG？** 保持 RAG 模块的数据独立性 — 如果未来要拆成独立服务，PG 数据是自包含的。

### 3.5 关键设计决策（2026-07-17 修订时明确）

以下几条是修订时发现原方案会导致启动失败 / 功能不闭环 / 成本失控的地方，
先在这里给结论，后文各 Phase 有对应落地细节：

1. **Embedding 不做多供应商降级**：向量检索要求查询向量与入库向量**同模型、同维度**。
   各供应商 embedding 维度不同（百炼 text-embedding-v4 = 1024，text-embedding-3-small = 1536），
   降级后往 `vector(1024)` 列里插直接报错。因此 embedding 锁定百炼 text-embedding-v4；
   更换 embedding 模型 = 全量重建向量，属于运维操作而非运行时降级。
2. **流式降级边界**：只有"**尚未输出任何 token**"时才允许降级到下一个供应商；
   一旦已有 token 输出，失败直接给前端发 error 事件
   （否则用户会看到两段拼接的回答）。见 1.2.6。
3. **匿名会话 + visitorId**：聊天端点 permitAll（读者无需登录即可提问），
   会话用前端生成并存于 localStorage 的 visitorId（UUID）归属，
   后端校验会话归属防 IDOR；登录用户后续可再绑定 user_id。见 3.3、3.5、Phase 4。
4. **成本保护必须前置**：permitAll + 付费 LLM API 意味着任何人都能烧你的额度。
   IP 限流（Redis 计数）+ 问题长度上限是 Phase 3 的必做项，不是优化项。见 3.7。
5. **Rerank 不套用 OpenAI 兼容基类**：百炼 gte-rerank 走 DashScope 原生接口
   （compatible-mode 不含 rerank），请求/响应格式不同，需单独实现
   BaiLianRerankProvider；rerank 固定使用主供应商，不做跨供应商降级。见 3.2。
6. **sources / conversationId 走正式 SSE 事件**：`RagChatService` 返回
   `Flux<ChatEvent>`（meta/sources/content/error/done 五种事件），而不是裸 token 流，
   否则"引用来源"和"会话 ID 回传"无处安放。见 3.5、3.6。

---

## Phase 0：开发环境准备

> **目标**：基础设施就绪，Spring Boot 升级完成，编译通过。
> **预计时间**：1-2 天（业余时间）
> **验收标准**：`./mvnw compile` + `./mvnw test` 全部通过
>
> **进度（2026-07-17 核对）**：
> - Step 0.1 ✅ 已完成（pom.xml 已是 Spring Boot 3.5.7 + MyBatis-Plus 3.5.14）
> - Step 0.2 postgres 服务 ✅ 已加入 docker-compose.yml（ollama 段已注释预留）；schema SQL 待按本文创建
> - Step 0.3 ✅ 已完成（pgvector 0.1.6 已在 pom.xml）
> - Step 0.4 `rag.datasource` 段 ✅ 已配；llm providers 段需按本文修订

### Step 0.1：升级 Spring Boot → 3.5.7 ✅ 已完成

**文件**：`pom.xml`（现状已到位，无需再改）

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.5.7</version>
</parent>
```

**已同步调整的依赖**（历史记录，供追溯）：

| 依赖 | 调整 | 说明 |
|------|------|------|
| `mybatis-plus-spring-boot3-starter` | 升级到 3.5.14 | 注意 artifact 名：Spring Boot 3 必须用 `mybatis-plus-spring-boot3-starter`，`mybatis-plus-boot-starter` 是 Boot 2 的 |
| `mybatis-plus-jsqlparser` | 3.5.14，单独引入 | **3.5.9+ 起分页插件（JSqlParser）拆成独立模块**，不单独引入则 `PaginationInnerInterceptor` 类找不到、分页静默失效。`DataBaseConfiguration` 里配了分页插件，这个依赖不能少 |
| `knife4j-openapi3-jakarta-spring-boot-starter` | 4.5.0 不动 | 与 Spring Boot 3.5.x 兼容；升级后打开 /doc.html 验证过一次 |
| `mysql-connector-j` / `postgresql` | managed | Spring Boot 管理版本 |

**验证命令**：
```bash
./mvnw compile   # 必须通过
./mvnw test      # 必须通过
```

> 注：升级带来的兼容问题（MyBatis-Plus 旧版、JSqlParser 拆分）已在升级时处理完毕，
> 此处仅保留验证命令作为回归基线。

### Step 0.2：Docker 环境新增 PostgreSQL + pgvector（compose ✅ 已含，schema 待建）

**文件**：`docker/docker-compose.yml` —— postgres 服务已在其中（ollama 段已注释预留，
需要本地模型兜底时取消注释即可）。配置参考：

```yaml
  # ============ RAG 新增 ============
  postgres:
    container_name: mysite-pgvector
    image: pgvector/pgvector:pg17
    restart: unless-stopped
    ports:
      - "5432:5432"
    environment:
      POSTGRES_USER: ragent
      POSTGRES_PASSWORD: ${RAGENT_PG_PASSWORD:-ragent123}
      POSTGRES_DB: ragent
    volumes:
      - ./pgvector_data:/var/lib/postgresql/data
      - ./init/ragent-schema.sql:/docker-entrypoint-initdb.d/01-ragent-schema.sql
    deploy:
      resources:
        limits:
          memory: 512M
    networks:
      - mysite-network

  # Ollama —— 可选，仅当你需要本地模型兜底时启用
  ollama:
    container_name: mysite-ollama
    image: ollama/ollama:latest
    restart: unless-stopped
    ports:
      - "11434:11434"
    volumes:
      - ./ollama_data:/root/.ollama
    environment:
      - OLLAMA_KEEP_ALIVE=24h
      - OLLAMA_HOST=0.0.0.0
    deploy:
      resources:
        limits:
          memory: 8G
    networks:
      - mysite-network
    profiles:
      - ollama    # 默认不启动，需要时加 --profile ollama
```

**创建初始化 SQL**：`docker/init/ragent-schema.sql`

```sql
-- pgvector 扩展
CREATE EXTENSION IF NOT EXISTS vector;

-- 知识库
CREATE TABLE IF NOT EXISTS t_knowledge_base (
    id BIGINT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    collection_name VARCHAR(100) NOT NULL UNIQUE,
    embedding_model VARCHAR(100) DEFAULT 'text-embedding-v4',
    embedding_dimension INT DEFAULT 1024,
    chunk_size INT DEFAULT 800,
    chunk_overlap INT DEFAULT 100,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 文档
CREATE TABLE IF NOT EXISTS t_knowledge_document (
    id BIGINT PRIMARY KEY,
    kb_id BIGINT NOT NULL REFERENCES t_knowledge_base(id),
    title VARCHAR(500) NOT NULL,
    source_type VARCHAR(20) NOT NULL,
    source_ref VARCHAR(500),
    file_type VARCHAR(20),
    status VARCHAR(20) DEFAULT 'PENDING',   -- PENDING/CHUNKING/READY/FAILED
    fail_reason TEXT,                        -- 摄取失败原因（embedding API 挂了就记录在这里）
    chunk_count INT DEFAULT 0,
    char_count INT DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    -- 同一来源只对应一份文档，防止并发的创建/更新事件插入重复记录
    CONSTRAINT uk_doc_source UNIQUE (kb_id, source_type, source_ref)
);

-- 分块
CREATE TABLE IF NOT EXISTS t_knowledge_chunk (
    id BIGINT PRIMARY KEY,
    doc_id BIGINT NOT NULL REFERENCES t_knowledge_document(id),
    kb_id BIGINT NOT NULL REFERENCES t_knowledge_base(id),
    chunk_index INT NOT NULL,
    content TEXT NOT NULL,
    char_count INT DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 向量
CREATE TABLE IF NOT EXISTS t_knowledge_vector (
    id BIGINT PRIMARY KEY,
    chunk_id BIGINT NOT NULL REFERENCES t_knowledge_chunk(id),
    kb_id BIGINT NOT NULL REFERENCES t_knowledge_base(id),
    embedding vector(1024),
    model VARCHAR(100),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- HNSW 索引（cosine 距离）
CREATE INDEX IF NOT EXISTS idx_vector_embedding ON t_knowledge_vector
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);

-- 对话会话
CREATE TABLE IF NOT EXISTS t_conversation (
    id BIGINT PRIMARY KEY,
    user_id BIGINT,                          -- 可空：匿名聊天不强制登录（见 3.5 节决策 3）
    visitor_id VARCHAR(64),                  -- 匿名访客标识（前端 localStorage UUID），防 IDOR
    title VARCHAR(200),
    message_count INT DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    -- 注意：PG 没有 MySQL 的 ON UPDATE CURRENT_TIMESTAMP，
    -- update_time 由应用层（MyBatis-Plus 填充或 service 显式 set）维护
);

-- 对话消息
CREATE TABLE IF NOT EXISTS t_conversation_message (
    id BIGINT PRIMARY KEY,
    conversation_id BIGINT NOT NULL REFERENCES t_conversation(id),
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    sources JSONB,
    token_count INT DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 索引
CREATE INDEX IF NOT EXISTS idx_chunk_doc_id ON t_knowledge_chunk(doc_id);
CREATE INDEX IF NOT EXISTS idx_chunk_kb_id ON t_knowledge_chunk(kb_id);
CREATE INDEX IF NOT EXISTS idx_vector_chunk_id ON t_knowledge_vector(chunk_id);
CREATE INDEX IF NOT EXISTS idx_vector_kb_id ON t_knowledge_vector(kb_id);
CREATE INDEX IF NOT EXISTS idx_conv_user_id ON t_conversation(user_id);
CREATE INDEX IF NOT EXISTS idx_conv_visitor_id ON t_conversation(visitor_id);
CREATE INDEX IF NOT EXISTS idx_conv_msg_conv_id ON t_conversation_message(conversation_id);
```

**启动并验证**：
```bash
# 启动 PG
docker compose -f docker/docker-compose.yml up -d postgres

# 验证 pgvector 扩展
docker exec mysite-pgvector psql -U ragent -d ragent -c "SELECT * FROM pg_extension WHERE extname='vector';"

# (可选) 启动 Ollama
docker compose -f docker/docker-compose.yml --profile ollama up -d ollama
docker exec mysite-ollama ollama pull qwen3:8b
```

### Step 0.3：引入 pgvector JDBC 依赖 ✅ 已完成

**文件**：`pom.xml`（已存在，无需再改）：

```xml
<!-- pgvector JDBC 支持 (MyBatis-Plus 原生 SQL 操作向量) -->
<dependency>
    <groupId>com.pgvector</groupId>
    <artifactId>pgvector</artifactId>
    <version>0.1.6</version>
</dependency>
```

> **说明**：这个依赖提供了 `PGvector` 类型，使得 MyBatis-Plus 可以正确处理 `vector` 列类型。向量操作（插入、检索）全部通过 Mapper 的原生 SQL 完成。

### Step 0.4：配置 application.yaml

**文件**：`src/main/resources/application.yaml`，末尾新增：

```yaml
# ============ RAG 配置 ============
rag:
  # PostgreSQL 数据源（RAG 专用）
  datasource:
    driver-class-name: org.postgresql.Driver
    url: jdbc:postgresql://localhost:5432/ragent
    username: ragent
    password: ${RAGENT_PG_PASSWORD:ragent123}
    hikari:
      maximum-pool-size: 5
      minimum-idle: 1

  # LLM 供应商配置（chat 按优先级降级；embedding/rerank 锁定百炼，见 3.5 节决策 1/5）
  llm:
    providers:
      # P1: 阿里百炼 —— 国内首选，延迟低，中文能力强
      # chat + embedding + rerank 都走它
      bailian:
        enabled: true
        priority: 1
        base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
        api-key: ${BAILIAN_API_KEY:}
        chat-model: qwen3-max
        embedding-model: text-embedding-v4   # 1024 维，与 PG vector(1024) 绑定；更换 = 全量重建向量
        rerank-model: gte-rerank             # 走 DashScope 原生接口，非 OpenAI 兼容（见 3.2）
        chat-timeout: 120s
        embedding-timeout: 30s

      # P2: SiliconFlow —— 备选，模型选择丰富
      # 降级供应商只接管 chat，故意不配 embedding-model：
      # 维度与百炼不一致（如 bge-large-zh 也是 1024 但语义空间不同），
      # 查询与入库的向量必须出自同一模型，embedding 降级没有意义（见 3.5 节决策 1）
      siliconflow:
        enabled: false
        priority: 2
        base-url: https://api.siliconflow.cn/v1
        api-key: ${SILICONFLOW_API_KEY:}
        chat-model: glm-4.7        # 型号以实施时实际可用为准
        chat-timeout: 120s

      # P3: AIHubMix —— 国际模型代理
      aihubmix:
        enabled: false
        priority: 3
        base-url: https://aihubmix.com/v1
        api-key: ${AIHUBMIX_API_KEY:}
        chat-model: gpt-5.4        # 型号以实施时实际可用为准
        chat-timeout: 120s

      # P9: 本地 Ollama —— 最低优先级兜底（仅 chat）
      ollama:
        enabled: false
        priority: 9
        base-url: http://localhost:11434/v1
        api-key: ""
        chat-model: qwen3:8b
        chat-timeout: 180s

    # 断路器全局参数
    circuit-breaker:
      failure-threshold: 2       # 连续失败 N 次 → 熔断
      cooldown-seconds: 30       # 熔断后冷却 N 秒 → 半开

  # 分块参数
  chunk:
    size: 800
    overlap: 100
    max-chunks-per-doc: 50

  # 检索参数
  retrieval:
    top-k: 10
    rerank-top-k: 5
    score-threshold: 0.3

  # 对话记忆
  memory:
    keep-turns: 6
    summary-turns: 10
    summary-enabled: false   # Phase 3 先不做摘要，保持简单

  # 成本保护（必做项，见 3.5 节决策 4 与 3.7 节）：
  # 聊天端点 permitAll，不限流任何人都能烧你的付费 API 额度
  rate-limit:
    max-per-hour: 20           # 每 IP 每小时最多提问次数
    max-question-length: 500   # 单次问题最大字符数

  # 异步线程池
  async:
    core-pool-size: 2
    max-pool-size: 4
    queue-capacity: 100
```

### Step 0.5：`RagProperties` 配置类（被 Phase 1/2/3 共享）

后续很多组件都会读取 `rag.*` 配置（断路器参数、供应商列表、限流阈值等）。
建一个统一的 `@ConfigurationProperties` 类收口，避免各组件自己解析 yaml。

```java
package io.github.somehow.mysite.ragent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "rag")
public class RagProperties {

    private DatasourceProperties datasource = new DatasourceProperties();
    private LlmProperties llm = new LlmProperties();
    private ChunkProperties chunk = new ChunkProperties();
    private RetrievalProperties retrieval = new RetrievalProperties();
    private MemoryProperties memory = new MemoryProperties();
    private AsyncProperties async = new AsyncProperties();
    private RateLimitProperties rateLimit = new RateLimitProperties();

    public boolean isProviderEnabled(String name) {
        Provider p = llm.getProviders().get(name);
        return p != null && p.isEnabled();
    }

    public int getProviderPriority(String name) {
        Provider p = llm.getProviders().get(name);
        return p != null ? p.getPriority() : Integer.MAX_VALUE;
    }

    public CircuitBreakerProperties getCircuitBreaker() {
        return llm.getCircuitBreaker();
    }

    @Data public static class DatasourceProperties {
        private String driverClassName;
        private String url;
        private String username;
        private String password;
    }

    @Data public static class LlmProperties {
        private Map<String, Provider> providers = new HashMap<>();
        private CircuitBreakerProperties circuitBreaker = new CircuitBreakerProperties();
    }

    @Data public static class Provider {
        private boolean enabled;
        private int priority;
        private String baseUrl;
        private String apiKey;
        private String chatModel;
        private String embeddingModel;
        private String rerankModel;
        private Duration chatTimeout = Duration.ofSeconds(120);
        private Duration embeddingTimeout = Duration.ofSeconds(30);
    }

    @Data public static class CircuitBreakerProperties {
        private int failureThreshold = 2;
        private long cooldownSeconds = 30;
    }

    @Data public static class ChunkProperties {
        private int size = 800;
        private int overlap = 100;
        private int maxChunksPerDoc = 50;
    }

    @Data public static class RetrievalProperties {
        private int topK = 10;
        private int rerankTopK = 5;
        private double scoreThreshold = 0.3;
    }

    @Data public static class MemoryProperties {
        private int keepTurns = 6;
        private int summaryTurns = 10;
        private boolean summaryEnabled = false;
    }

    @Data public static class AsyncProperties {
        private int corePoolSize = 2;
        private int maxPoolSize = 4;
        private int queueCapacity = 100;
    }

    @Data public static class RateLimitProperties {
        private int maxPerHour = 20;
        private int maxQuestionLength = 500;
    }
}
```

> 提示：`spring-boot-configuration-processor` 由 `spring-boot-starter-parent` 自带，
> 写好这个类后可以在 IDE 里享受到 yaml 自动补全。

### Step 0.6：获取百炼 API Key

1. 访问 [阿里云百炼控制台](https://bailian.console.aliyun.com/)
2. 开通百炼服务（有免费额度）
3. 创建 API Key
4. 设置环境变量：
```bash
export BAILIAN_API_KEY="sk-xxxxxxxx"
```

### Phase 0 验收清单

- [x] `./mvnw compile` 通过
- [x] `./mvnw test` 通过
- [x] postgres 服务已加入 docker-compose.yml（ollama 段注释预留）
- [ ] `docker/init/ragent-schema.sql` 按本文（含 visitor_id / fail_reason / 唯一约束）创建
- [ ] `docker compose up -d postgres` 成功
- [ ] `docker exec mysite-pgvector psql -U ragent -d ragent -c "\dt"` 显示 6 张表
- [ ] 创建 `RagProperties.java` 与 `application.yaml` 结构对应
- [ ] `BAILIAN_API_KEY` 环境变量已设置
- [ ] application.yaml 按 Step 0.4 修订（删 first-packet-timeout、加 rate-limit 段、非百炼供应商删 embedding-model）
- [ ] 打 git tag: `git tag pre-ragent-integration`

---

## Phase 1：LLM 抽象层 —— 理解 Agent 的"大脑"

> **目标**：实现多供应商 LLM 调用层 + 断路器，能通过测试调用百炼 API 并拿到回复。
> **预计时间**：3-5 天
> **学习重点**：OpenAI 兼容协议、SSE 流式解析、断路器模式、WebClient 非阻塞调用
> **验收标准**：写一个 main 方法或单元测试，能调用百炼 `qwen3-max` 做一次对话并打印回复。
>
> **进度（2026-07-17 核对）**：`ragent/llm/` 骨架已建（LLMService / EmbeddingService /
> RerankService / ChatRequest / ChatResponse / CircuitBreaker / RoutingLLMService 均为空壳），
> `ragent/config/` 两个配置类为空壳，`KnowledgeBaseDO` 已建。按本节实现填充即可
> （ChatResponse 不需要，可删；ChatMessage 并入 ChatRequest.java）。

### 1.1 理解：为什么需要抽象层？

```
你的代码（RagChatService）
        │
        │  只依赖接口，不关心具体是谁
        ▼
   LLMService 接口
        │
        │  RoutingLLMService 决定用谁
        ▼
   ┌────┴────┬─────────┬──────────┬─────────┐
   │ 百炼     │ SiliconFlow │ AIHubMix │ Ollama  │
   │ (P1)    │ (P2)       │ (P3)     │ (P9)    │
   └─────────┴────────────┴──────────┴─────────┘

好处：
1. 换模型不换代码 — 只需改配置
2. 故障自动切换 — 百炼挂了自动用 SiliconFlow
3. 方便测试 — Mock LLMService 即可
```

### 1.2 动手实现

#### 1.2.1 断路器 `CircuitBreaker.java`

**为什么先写这个？** 因为它是模型路由的基础 — 每个供应商都有一个独立的断路器来跟踪健康状态。

**要理解的概念**：
- **三态模型**：CLOSED（正常）→ OPEN（熔断）→ HALF_OPEN（探测）→ CLOSED（恢复）
- **滑动窗口计数**：记录最近 N 次调用的成功/失败
- **原子操作**：用 `AtomicReference` 保证并发安全

```java
package io.github.somehow.mysite.ragent.llm;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 三态断路器 —— Ragent 最精华的设计之一，独立提取到博客项目。
 *
 * 状态转换逻辑：
 *   CLOSED ──连续失败≥threshold──▶ OPEN
 *   OPEN   ──冷却时间到──────────▶ HALF_OPEN
 *   HALF_OPEN ──探测成功────────▶ CLOSED
 *   HALF_OPEN ──探测失败────────▶ OPEN (重新冷却)
 *
 * 学习要点：
 *   1. 为什么用三态而不是两态？HALF_OPEN 避免了"冷却后立即全量请求
 *      再次打垮服务"的问题 —— 只放一个请求去探测
 *   2. 为什么用 AtomicReference 而不是 synchronized？
 *      断路器操作是轻量级的读-改-写，CAS 比锁更高效
 */
public class CircuitBreaker {

    /** public：路由器的健康检查（getHealthStatus）需要向外部暴露这个类型 */
    public enum State { CLOSED, OPEN, HALF_OPEN }

    private final String name;
    private final int failureThreshold;      // 连续失败 N 次 → 熔断
    private final long cooldownMillis;       // 冷却时间（毫秒）
    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private volatile long openedAt = 0;      // 进入 OPEN 状态的时间戳

    public CircuitBreaker(String name, int failureThreshold, long cooldownSeconds) {
        this.name = name;
        this.failureThreshold = failureThreshold;
        this.cooldownMillis = cooldownSeconds * 1000;
    }

    /**
     * 调用前检查：是否允许请求通过？
     * CLOSED → true
     * OPEN + 冷却中 → false
     * OPEN + 冷却完 → 转为 HALF_OPEN，放行一个探测请求
     * HALF_OPEN → false（只放行一个）
     */
    public boolean allowRequest() {
        State current = state.get();
        if (current == State.CLOSED) {
            return true;
        }
        if (current == State.OPEN) {
            // 检查是否冷却完毕
            if (System.currentTimeMillis() - openedAt >= cooldownMillis) {
                // CAS 保证只有一个线程能从 OPEN 转到 HALF_OPEN
                if (state.compareAndSet(State.OPEN, State.HALF_OPEN)) {
                    return true; // 这个线程获得探测权
                }
            }
            return false;
        }
        // HALF_OPEN：已有探测请求在外面，其他请求不通过
        return false;
    }

    /** 调用成功后回调 */
    public void recordSuccess() {
        consecutiveFailures.set(0);
        state.set(State.CLOSED);  // HALF_OPEN 探测成功 → 恢复
    }

    /** 调用失败后回调 */
    public void recordFailure() {
        // HALF_OPEN 状态下的失败：探测没通过，直接重新熔断并重新计时
        if (state.get() == State.HALF_OPEN) {
            state.set(State.OPEN);
            openedAt = System.currentTimeMillis();
            return;
        }
        int failures = consecutiveFailures.incrementAndGet();
        if (failures >= failureThreshold) {
            state.set(State.OPEN);
            openedAt = System.currentTimeMillis();
        }
    }

    public boolean isOpen() {
        return state.get() == State.OPEN;
    }

    public State getState() {
        return state.get();
    }
}
```

#### 1.2.2 LLM 抽象接口 `LLMService.java`

```java
package io.github.somehow.mysite.ragent.llm;

import reactor.core.publisher.Flux;
import java.util.List;

/**
 * 统一聊天接口 —— 所有 LLM 供应商都实现这个接口。
 *
 * 为什么用 Flux<String> 而不是 String？
 *   流式生成（Server-Sent Events）：LLM 是一个 token 一个 token 生成的。
 *   用 Flux 可以让每个 token 立刻推送到前端，用户看到打字机效果，
 *   而不是等 30 秒全部生成完才显示。
 */
public interface LLMService {

    /**
     * 流式聊天（最常用）
     * @return 每个元素是一个 token（可能是 1 个或多个字符）
     */
    Flux<String> chatStream(ChatRequest request);

    /**
     * 同步聊天（用于非流式场景，如摘要生成、查询重写）
     */
    String chat(ChatRequest request);
}
```

#### 1.2.3 ChatRequest / ChatMessage 数据类

```java
package io.github.somehow.mysite.ragent.llm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequest {
    private String model;            // 模型名（如 qwen3-max）
    private List<ChatMessage> messages;
    private double temperature;      // 0.0~2.0，创作性越高越大
    private int maxTokens;           // 最大输出 token 数

    /** 快捷构造：单条用户消息 */
    public static ChatRequest of(String model, String userMessage) {
        return ChatRequest.builder()
            .model(model)
            .messages(List.of(ChatMessage.user(userMessage)))
            .temperature(0.7)
            .maxTokens(2048)
            .build();
    }
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {   // 必须 public：core/memory、core/prompt、service 包都要引用它
    private String role;     // "system" / "user" / "assistant"
    private String content;

    public static ChatMessage system(String content) {
        return ChatMessage.builder().role("system").content(content).build();
    }
    public static ChatMessage user(String content) {
        return ChatMessage.builder().role("user").content(content).build();
    }
    public static ChatMessage assistant(String content) {
        return ChatMessage.builder().role("assistant").content(content).build();
    }
}
```

#### 1.2.4 OpenAI 兼容协议解析 —— `AbstractOpenAIProvider.java`

```java
package io.github.somehow.mysite.ragent.llm.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.somehow.mysite.ragent.llm.ChatRequest;
import io.github.somehow.mysite.ragent.llm.LLMService;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.Map;

/**
 * OpenAI 兼容协议基类 —— 学习价值最高的一个类。
 *
 * 几乎所有国产大模型（百炼、SiliconFlow、DeepSeek 等）都兼容 OpenAI 的 API 格式。
 * 这意味着：学会对接一个，就等于学会对接所有。
 *
 * OpenAI Chat Completions API 格式（关键知识点）：
 *
 * 请求：
 *   POST {baseUrl}/chat/completions
 *   Header: Authorization: Bearer {apiKey}
 *   Body: {
 *     "model": "qwen3-max",
 *     "messages": [
 *       {"role": "system", "content": "你是..."},
 *       {"role": "user", "content": "你好"}
 *     ],
 *     "stream": true,          ← 开启流式输出
 *     "temperature": 0.7
 *   }
 *
 * 响应（stream=true 时，SSE 格式）：
 *   data: {"choices":[{"delta":{"content":"你"},"index":0}]}
 *   data: {"choices":[{"delta":{"content":"好"},"index":0}]}
 *   data: {"choices":[{"delta":{"content":"！"},"index":0}]}
 *   data: {"choices":[{"delta":{},"finish_reason":"stop","index":0}]}
 *   data: [DONE]
 *
 * 响应（stream=false 时，JSON 格式）：
 *   {"choices":[{"message":{"content":"你好！有什么可以帮助你的？"}}]}
 *
 * 当前流行的所有国产模型 API 几乎都遵循这个格式：
 *   - 阿里百炼 DashScope
 *   - SiliconFlow (硅基流动)
 *   - DeepSeek
 *   - 智谱 GLM
 *   - Moonshot (月之暗面)
 *   - 零一万物
 *   - Ollama (本地)
 *   ... 等等
 */
public abstract class AbstractOpenAIProvider implements LLMService {

    protected final WebClient webClient;
    protected final String apiKey;
    protected final String model;
    protected final Duration timeout;
    protected final ObjectMapper objectMapper;

    public AbstractOpenAIProvider(String baseUrl, String apiKey, String model,
                                   Duration timeout, ObjectMapper objectMapper) {
        this.apiKey = apiKey;
        this.model = model;
        this.timeout = timeout;
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder()
            .baseUrl(baseUrl)
            .defaultHeader("Authorization", "Bearer " + apiKey)
            .defaultHeader("Content-Type", "application/json")
            .build();
    }

    @Override
    public Flux<String> chatStream(ChatRequest request) {
        // 使用实际配置的 model（子类可覆盖），而非 request 中的
        ChatRequest actualRequest = ChatRequest.builder()
            .model(this.model)
            .messages(request.getMessages())
            .temperature(request.getTemperature())
            .maxTokens(request.getMaxTokens())
            .build();

        return webClient.post()
            .uri("/chat/completions")
            .bodyValue(Map.of(
                "model", this.model,
                "messages", actualRequest.getMessages(),
                "stream", true,
                "temperature", actualRequest.getTemperature(),
                "max_tokens", actualRequest.getMaxTokens()
            ))
            .retrieve()
            .bodyToFlux(String.class)
            .timeout(this.timeout)
            .takeUntil(line -> line.contains("[DONE]"))  // SSE 结束标记
            .filter(line -> !line.equals("[DONE]"))
            .filter(line -> line.startsWith("data: "))
            .map(line -> line.substring(6))  // 去掉 "data: " 前缀
            .map(this::extractDeltaContent)
            .filter(content -> content != null && !content.isEmpty());
    }

    @Override
    public String chat(ChatRequest request) {
        // 非流式版本：收集所有 token 拼接成完整结果
        return chatStream(request)
            .collectList()
            .map(tokens -> String.join("", tokens))
            .block(timeout);
    }

    /**
     * 从 SSE data 行中提取 delta.content。
     * JSON 结构：{"choices":[{"delta":{"content":"你好"},"index":0}]}
     *
     * 为什么要解析这个结构？这是理解 LLM API 交互的核心 ——
     * 掌握这个 JSON 格式就等于掌握了所有 OpenAI 兼容 API 的对话方式。
     */
    protected String extractDeltaContent(String jsonData) {
        try {
            JsonNode root = objectMapper.readTree(jsonData);
            JsonNode choices = root.get("choices");
            if (choices != null && choices.isArray() && choices.size() > 0) {
                JsonNode delta = choices.get(0).get("delta");
                if (delta != null) {
                    JsonNode content = delta.get("content");
                    if (content != null && !content.isNull()) {
                        return content.asText();
                    }
                }
            }
        } catch (Exception e) {
            // 解析失败静默跳过（可能是非标准格式的 SSE 行）
        }
        return "";
    }
}
```

#### 1.2.5 `LLMProvider` 标记接口与各供应商实现

**为什么需要这个标记接口？** 下一节的 `RoutingLLMService` 本身也 `implements LLMService`，
如果它的构造函数注入 `List<LLMService>`，Spring 会把路由器**自己也放进这个 List**，
形成循环依赖，启动直接失败。让真正的供应商实现 `LLMProvider`，路由器只注入
`List<LLMProvider>`，自注入问题就没了。

```java
package io.github.somehow.mysite.ragent.llm;

/**
 * 供应商标记接口 —— 只有"真正的 LLM 供应商实现"才实现它。
 * RoutingLLMService 注入 List<LLMProvider> 而不是 List<LLMService>，
 * 避免把自己（也是 LLMService）注入进来造成循环依赖。
 */
public interface LLMProvider extends LLMService {

    /** 供应商标识，与配置 rag.llm.providers.{name} 对应，如 "bailian" */
    String getName();
}
```

各供应商实现（只需提供 baseUrl + apiKey + model + name）：

```java
// === 百炼（阿里云）—— 国内首选 ===
// baseUrl 格式：https://dashscope.aliyuncs.com/compatible-mode/v1
// 文档：https://help.aliyun.com/zh/model-studio/
@Component
public class BaiLianProvider extends AbstractOpenAIProvider implements LLMProvider {
    public BaiLianProvider(@Value("${rag.llm.providers.bailian.base-url}") String baseUrl,
                           @Value("${rag.llm.providers.bailian.api-key}") String apiKey,
                           @Value("${rag.llm.providers.bailian.chat-model}") String model,
                           @Value("${rag.llm.providers.bailian.chat-timeout}") Duration timeout,
                           ObjectMapper objectMapper) {
        super(baseUrl, apiKey, model, timeout, objectMapper);
    }

    @Override
    public String getName() {
        return "bailian";
    }
}

// === SiliconFlow（硅基流动）—— 备选（仅 chat，见 3.5 节决策 1）===
// baseUrl：https://api.siliconflow.cn/v1
// 文档：https://docs.siliconflow.cn/
@Component
public class SiliconFlowProvider extends AbstractOpenAIProvider implements LLMProvider {
    // ... 同上模式，getName() 返回 "siliconflow"
}

// === AIHubMix —— 国际模型中转 ===
// baseUrl：https://aihubmix.com/v1
@Component
public class AIHubMixProvider extends AbstractOpenAIProvider implements LLMProvider {
    // ... 同上模式，getName() 返回 "aihubmix"
}

// === Ollama 本地 —— 兜底 ===
// baseUrl：http://localhost:11434/v1
// 特点：不需要 apiKey（传空字符串即可）
// 文档：https://ollama.com/
@Component
public class OllamaProvider extends AbstractOpenAIProvider implements LLMProvider {
    // ... 同上模式，getName() 返回 "ollama"
}
```

#### 1.2.6 模型路由器 `RoutingLLMService.java`

```java
package io.github.somehow.mysite.ragent.llm;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 模型路由器 —— Ragent 整体设计的核心。
 *
 * 职责：
 *   1. 按优先级排序所有已启用的供应商
 *   2. 遍历尝试，跳过被熔断的
 *   3. 调用成功 → 记录成功 + 返回结果
 *   4. 调用失败 → 记录失败 + 尝试下一个
 *   5. 全部失败 → 抛出异常
 *
 * 这个类的本质是"责任链 + 策略模式"：每个 Provider 是一个策略，
 * 路由器按优先级串联成降级链。
 *
 * 两个关键设计点（2026-07-17 修订）：
 *   1. 注入 List<LLMProvider> 而不是 List<LLMService> —— 本类自身也是
 *      LLMService，注入后者会把自己装进 List，形成循环依赖（见 1.2.5）。
 *   2. 流式降级边界 —— 只在"尚未输出任何 token"时才允许降级；
 *      一旦已经吐过 token，失败必须直接报错给前端，
 *      否则用户会看到两段拼起来的回答（见 3.5 节决策 2）。
 */
@Service
public class RoutingLLMService implements LLMService {

    private final Map<String, CircuitBreaker> breakers = new ConcurrentHashMap<>();
    private final List<LLMProvider> sortedProviders;  // 启动时排序，运行时不变

    public RoutingLLMService(List<LLMProvider> allProviders, RagProperties properties) {
        // Spring 注入所有 LLMProvider 实现（不含本类自身，见 1.2.5 标记接口）
        this.sortedProviders = allProviders.stream()
            .filter(p -> properties.isProviderEnabled(p.getName()))
            .sorted(Comparator.comparingInt(p -> properties.getProviderPriority(p.getName())))
            .toList();
        // 为每个启用的供应商创建独立断路器
        int threshold = properties.getCircuitBreaker().getFailureThreshold();
        long cooldown = properties.getCircuitBreaker().getCooldownSeconds();
        sortedProviders.forEach(p ->
            breakers.put(p.getName(), new CircuitBreaker(p.getName(), threshold, cooldown)));
    }

    @Override
    public Flux<String> chatStream(ChatRequest request) {
        return attempt(sortedProviders.iterator(), request);
    }

    /**
     * 递归尝试供应商链。
     *
     * 成功/失败的记录时机：
     *   - recordSuccess：流完整结束（doOnComplete）才算成功。
     *     不能在"发出请求"时就算成功 —— 连接成功但流中途挂掉也应记失败。
     *   - recordFailure：onErrorResume 时记录。
     *
     * 降级边界（重点）：
     *   emitted 标记当前供应商是否已经输出过 token。
     *   - 未输出就失败 → 用户还什么都没看到，可以安全降级到下一个供应商
     *   - 已输出后失败 → 不能降级（否则前端收到两段拼接的回答），直接报错
     */
    private Flux<String> attempt(Iterator<LLMProvider> it, ChatRequest request) {
        if (!it.hasNext()) {
            return Flux.error(new RuntimeException("All LLM providers failed"));
        }
        LLMProvider provider = it.next();
        CircuitBreaker cb = breakers.get(provider.getName());
        if (cb != null && !cb.allowRequest()) {
            return attempt(it, request);  // 熔断中，跳过
        }

        AtomicBoolean emitted = new AtomicBoolean(false);
        return provider.chatStream(request)
            .doOnNext(token -> emitted.set(true))
            .doOnComplete(cb::recordSuccess)
            .onErrorResume(e -> {
                cb.recordFailure();
                if (emitted.get()) {
                    // 已经吐过 token：不能降级，直接失败
                    return Flux.error(e);
                }
                // 尚未输出：降级到下一个供应商
                return attempt(it, request);
            });
    }

    @Override
    public String chat(ChatRequest request) {
        return chatStream(request)
            .collectList()
            .map(tokens -> String.join("", tokens))
            .block(Duration.ofSeconds(120));
    }

    /** 获取各供应商健康状态（用于 /actuator 健康检查或 Dashboard） */
    public Map<String, String> getHealthStatus() {
        Map<String, String> status = new LinkedHashMap<>();
        for (LLMProvider provider : sortedProviders) {
            status.put(provider.getName(), breakers.get(provider.getName()).getState().name());
        }
        return status;
    }
}
```

#### 1.2.7 嵌入服务 `EmbeddingService.java`

```java
package io.github.somehow.mysite.ragent.llm;

import java.util.List;

/**
 * 嵌入服务 —— 把文本变成向量。
 *
 * 什么是 Embedding？
 *   把一段文本（如 "今天天气真好"）输入嵌入模型，输出一个浮点数数组
 *   （如 [0.12, -0.34, 0.56, ..., 0.78]），这个数组叫"嵌入向量"（Embedding Vector）。
 *
 * 向量有什么用？
 *   语义相近的文本，它们的向量在空间中距离也很近。
 *   例如："今天天气真好" 和 "今天阳光明媚" 的向量距离很近（cosine 距离 ~0.1），
 *   而 "今天天气真好" 和 "数据库索引优化" 的向量距离很远（cosine 距离 ~0.9）。
 *
 *   这就是 RAG 的核心原理：把问题和文档都变成向量，
 *   通过向量距离（而不是关键词匹配）找到最相关的文档。
 */
public interface EmbeddingService {

    /**
     * 单条文本嵌入
     * @param text 待嵌入的文本
     * @return 向量，如 float[1024]
     */
    float[] embed(String text);

    /**
     * 批量嵌入（用于批量索引入库，一次 API 调用处理多条文本）
     * @param texts 待嵌入的文本列表
     * @return 向量列表，每个向量对应一条输入文本
     */
    List<float[]> embedBatch(List<String> texts);
}
```

**两个约束（2026-07-17 修订，见 3.5 节决策 1）**：

1. **Embedding 不做多供应商降级**：查询向量与入库向量必须同模型、同维度，
   降级到别的供应商（维度/语义空间不同）只会得到错误结果或直接报维度不匹配。
   全系统锁定百炼 `text-embedding-v4`（1024 维），实现类做成单 bean
   （如 `BaiLianEmbeddingService implements EmbeddingService`）。
2. **`embedBatch` 必须分批调用**：百炼 embedding 接口单次调用有批量上限
   （text-embedding-v4 为 10 条/次，实施时以官方文档为准），而 `max-chunks-per-doc: 50`，
   整批 50 条一次发会被拒绝。实现里按上限切成多个子批顺序调用、合并结果。

**嵌入服务的实现**走 OpenAI 兼容的 embeddings 端点（注意：`AbstractOpenAIProvider`
实现的是 `LLMService`，embedding 需要独立的实现类，不能直接复用它）——
POST `{baseUrl}/embeddings`，请求体：
```json
{"model": "text-embedding-v4", "input": "今天天气真好"}
```
响应体：
```json
{"data":[{"embedding":[0.12, -0.34, 0.56, ...], "index":0}]}
```

### 1.3 Phase 1 验证方式

写一个单元测试（或 main 方法）：

```java
@Test
void testBaiLianChat() {
    // 1. 创建 Provider
    BaiLianProvider provider = new BaiLianProvider(
        "https://dashscope.aliyuncs.com/compatible-mode/v1",
        System.getenv("BAILIAN_API_KEY"),
        "qwen3-max",
        Duration.ofSeconds(120),
        new ObjectMapper()
    );

    // 2. 发送请求
    ChatRequest request = ChatRequest.builder()
        .messages(List.of(
            ChatMessage.system("你是一个有帮助的助手"),
            ChatMessage.user("请用三句话介绍 Spring Boot")
        ))
        .temperature(0.7)
        .build();

    // 3. 流式接收并打印
    String fullResponse = provider.chatStream(request)
        .doOnNext(token -> System.out.print(token))  // 逐字打印
        .collectList()
        .map(tokens -> String.join("", tokens))
        .block(Duration.ofSeconds(120));

    Assertions.assertNotNull(fullResponse);
    Assertions.assertTrue(fullResponse.length() > 10);
    System.out.println("\n\n✅ 百炼 API 调用成功！");
}
```

### Phase 1 验收清单

- [ ] `CircuitBreaker` 的三个状态切换逻辑正确（单元测试覆盖，含 HALF_OPEN 探测失败重回 OPEN）
- [ ] `AbstractOpenAIProvider.chatStream()` 能正确解析 SSE 事件流
- [ ] 百炼 API 调用成功，流式输出正常
- [ ] `RoutingLLMService` 按优先级降级逻辑正确
- [ ] 应用启动无循环依赖（`RoutingLLMService` 注入 `List<LLMProvider>`，不含自身）
- [ ] 降级边界正确：模拟"首 token 前失败"会降级；模拟"流中途失败"不降级、直接报错
- [ ] `EmbeddingService.embed()` 能返回正确的向量维度（1024）
- [ ] `EmbeddingService.embedBatch()` 超过单批上限（10 条）时分批调用且结果顺序正确

---

## Phase 2：向量存储与文档摄取 —— 理解 RAG 的"记忆"

> **目标**：博客文章自动向量化入库，能通过向量相似度检索到相关文章片段。
> **预计时间**：4-6 天
> **学习重点**：pgvector 使用、HNSW 索引原理、Markdown 分块策略、Spring Event 解耦
> **验收标准**：发布一篇文章后，PG 中能看到对应的 chunks 和 vectors。调用检索 API 能找到相关片段。

### 2.1 双数据源配置 `PrimaryDataSourceConfig` + `RagentDataSourceConfig`

**理解：为什么需要两个数据源？**

```
MySite Application
├── Primary DataSource (MySQL)     ← 所有现有 Mapper 都指向这个
│   ├── BasePackages: io.github.somehow.mysite.dao.mapper
│   └── 管理：用户、文章、评论等博客数据
│
└── Secondary DataSource (PG)      ← RAG 专用
    ├── BasePackages: io.github.somehow.mysite.ragent.dao.mapper
    └── 管理：知识库、向量、对话记录
```

**现状与三个必须处理的坑（2026-07-17 修订）**：

1. **主数据源目前是 Spring Boot 自动配置的**，没有 `@Primary`。RAG 数据源一加入，
   容器里就有两个 `DataSource`，MyBatis-Plus 自动配置等 byType 注入点会报
   `NoUniqueBeanDefinitionException`。→ 必须显式声明主数据源并加 `@Primary`
   （声明后 Boot 的 DataSource 自动配置会因 `@ConditionalOnMissingBean` 退避）。
2. **现有 `@MapperScan` 在 `MysiteApplication.java:8`**（不在 `DataBaseConfiguration`——
   那个类只配了分页插件和字段填充），且没有指定 `sqlSessionFactoryRef`。
   两个 `SqlSessionFactory` 并存时按类型查找会冲突。
   → 主扫描显式指定 `sqlSessionFactoryRef = "sqlSessionFactory"`
   （这是 MyBatis-Plus 自动配置的 bean 名），RAG 扫描指定 `ragentSqlSessionFactory`。
3. **`DataSourceBuilder` + `@ConfigurationProperties` 直接绑 `url` 会静默失败**：
   Hikari 的 URL 属性名是 `jdbcUrl`，`hikari.*` 子属性也绑不上。
   → 用 `DataSourceProperties` 模式（Boot 官方推荐的多数据源写法），
   `initializeDataSourceBuilder()` 会自动处理 `url → jdbcUrl` 的转换，
   连接池细项用第二个 `@ConfigurationProperties("xxx.hikari")` 绑定。

**改动 1：`MysiteApplication.java`**

```java
// 修改前：@MapperScan("io.github.somehow.mysite.dao.mapper")
// 修改后：显式绑定主 SqlSessionFactory（MyBatis-Plus 自动配置的 bean 名就是 sqlSessionFactory）
@MapperScan(basePackages = "io.github.somehow.mysite.dao.mapper",
            sqlSessionFactoryRef = "sqlSessionFactory")
```

**改动 2：删除 `config/RagentDataSourceConfig.java` 空壳**（误建的重复类，
正确位置在 `ragent/config/` 下）。

**改动 3：新增 `config/PrimaryDataSourceConfig.java`**

```java
package io.github.somehow.mysite.config;

import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * 主数据源（MySQL，博客数据）显式声明。
 *
 * 为什么需要这个类？
 *   RAG 的 PG 数据源加入后容器里有两个 DataSource，而主数据源原来靠
 *   Spring Boot 自动配置、没有 @Primary，所有 byType 注入点都会报
 *   NoUniqueBeanDefinitionException。显式声明 + @Primary 后：
 *   - Boot 的 DataSource 自动配置退避（@ConditionalOnMissingBean）
 *   - MyBatis-Plus 自动配置的 sqlSessionFactory 仍会注入这个 @Primary 主库
 *   - spring.datasource.hikari.* 连接池配置继续生效
 */
@Configuration
public class PrimaryDataSourceConfig {

    @Primary
    @Bean
    @ConfigurationProperties("spring.datasource.hikari")  // 保留 yaml 里已有的 hikari 细项
    public DataSource dataSource(DataSourceProperties properties) {
        // DataSourceProperties 由 Boot 自动注册（绑定 spring.datasource.*），
        // initializeDataSourceBuilder() 自动处理 url → jdbcUrl 的转换
        return properties.initializeDataSourceBuilder().build();
    }
}
```

**改动 4：实现 `ragent/config/RagentDataSourceConfig.java`**（当前为空壳）

```java
package io.github.somehow.mysite.ragent.config;

import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

/**
 * RAG 专用数据源配置（PostgreSQL + pgvector）。
 *
 * 关键知识点：Spring Boot 多数据源
 *   1. 主数据源见 PrimaryDataSourceConfig（@Primary）
 *   2. 第二数据源需要自己的 DataSource、SqlSessionFactory、TransactionManager
 *   3. @MapperScan 通过 basePackages 把不同包的 Mapper 绑定到不同数据源
 *   4. 用 DataSourceProperties 而不是直接 @ConfigurationProperties 绑 DataSource ——
 *      Hikari 的 URL 属性名是 jdbcUrl，直接绑 url 会静默失败、启动才报错
 *
 * 注意：手工构建的 ragentSqlSessionFactory 不会自动带上主库 DataBaseConfiguration 里的
 * MybatisPlusInterceptor（分页插件）和 MetaObjectHandler（字段自动填充）。
 * RAG 实体保持简单：不做逻辑删除，create_time/update_time 在代码里显式 set。
 */
@Configuration
@MapperScan(
    basePackages = "io.github.somehow.mysite.ragent.dao.mapper",
    sqlSessionFactoryRef = "ragentSqlSessionFactory"
)
public class RagentDataSourceConfig {

    @Bean
    @ConfigurationProperties("rag.datasource")
    public DataSourceProperties ragentDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @ConfigurationProperties("rag.datasource.hikari")
    public DataSource ragentDataSource(
            @Qualifier("ragentDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().build();
    }

    @Bean
    public SqlSessionFactory ragentSqlSessionFactory(
            @Qualifier("ragentDataSource") DataSource dataSource) throws Exception {
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        // 实体扫描
        factory.setTypeAliasesPackage("io.github.somehow.mysite.ragent.dao.entity");
        return factory.getObject();
    }

    @Bean
    public PlatformTransactionManager ragentTransactionManager(
            @Qualifier("ragentDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
}
```

### 2.2 向量存储 `PgvectorVectorStore.java`

```java
package io.github.somehow.mysite.ragent.vector;

import java.util.List;

/**
 * 向量存储接口。
 *
 * 为什么需要抽象？
 *   当前用 pgvector，但未来如果博客规模变大，可以切换到 Milvus
 *   而不需要改任何业务代码（依赖倒置原则）。
 */
public interface VectorStore {

    /**
     * 插入向量 + 元数据
     * @param vectors 要插入的向量列表
     */
    void insert(List<VectorEntry> vectors);

    /**
     * 向量相似度检索
     * @param queryEmbedding 查询向量
     * @param topK 返回 top K 个最相似的
     * @param kbId 限定知识库（null = 全库检索）。
     *             现在传 null 即可，但签名先留好 —— 多知识库是近期规划（见 7.1），
     *             届时再加这个参数就是 breaking change 了
     * @return 检索结果，按相似度降序排列
     */
    List<SearchResult> search(float[] queryEmbedding, int topK, Long kbId);

    /**
     * 删除指定知识库的所有向量
     */
    void deleteByKbId(Long kbId);

    /**
     * 删除指定文档的所有向量
     */
    void deleteByDocId(Long docId);

    // === 内嵌数据类 ===

    record VectorEntry(
        Long chunkId,
        Long kbId,
        float[] embedding,
        String model
    ) {}

    record SearchResult(
        Long chunkId,
        Long docId,
        String docTitle,
        String content,
        float score,          // cosine 相似度，越接近 1 越相似
        Long kbId
    ) {}
}
```

```java
package io.github.somehow.mysite.ragent.vector;

import org.springframework.stereotype.Component;
import java.sql.*;
import java.util.*;

/**
 * pgvector 实现 —— 用 JDBC 原生 SQL 操作向量。
 *
 * 为什么不用 MyBatis-Plus 直接操作？
 *   pgvector 的 vector 类型是一个自定义 JDBC 类型（PGvector），
 *   MyBatis-Plus 不原生支持，需要用 PGvector 库提供的 getValue/setValue。
 *
 * 核心 SQL：
 *   -- 插入向量
 *   INSERT INTO t_knowledge_vector (id, chunk_id, kb_id, embedding, model)
 *   VALUES (?, ?, ?, ?::vector, ?)
 *
 *   -- 余弦相似度检索（<=> 是 pgvector 提供的余弦距离运算符）
 *   SELECT v.id, v.chunk_id, c.content, c.doc_id, d.title,
 *          1 - (v.embedding <=> ?::vector) AS similarity
 *   FROM t_knowledge_vector v
 *   JOIN t_knowledge_chunk c ON v.chunk_id = c.id
 *   JOIN t_knowledge_document d ON c.doc_id = d.id
 *   ORDER BY v.embedding <=> ?::vector
 *   LIMIT ?
 *
 * HNSW 索引原理（面试可能问到）：
 *   HNSW = Hierarchical Navigable Small World
 *   多层图结构，上层稀疏下层密集，搜索时从上层快速定位区域，
 *   再在下层精确查找。时间复杂度 O(log N)，ANN（近似最近邻）领域
 *   最主流的算法之一，pgvector、Milvus、Weaviate 都在用。
 */
@Component
public class PgvectorVectorStore implements VectorStore {

    private final DataSource dataSource;

    public PgvectorVectorStore(@Qualifier("ragentDataSource") DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void insert(List<VectorEntry> entries) {
        String sql = """
            INSERT INTO t_knowledge_vector (id, chunk_id, kb_id, embedding, model)
            VALUES (?, ?, ?, ?::vector, ?)
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (VectorEntry entry : entries) {
                // 主键用 MyBatis-Plus 雪花算法生成（建表无自增序列，与全库 ASSIGN_ID 策略一致）
                ps.setLong(1, com.baomidou.mybatisplus.core.toolkit.IdWorker.getId());
                ps.setLong(2, entry.chunkId());
                ps.setLong(3, entry.kbId());
                ps.setObject(4, new PGvector(entry.embedding()));
                ps.setString(5, entry.model());
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to insert vectors", e);
        }
    }

    @Override
    public List<SearchResult> search(float[] queryEmbedding, int topK, Long kbId) {
        String sql = """
            SELECT
                v.chunk_id,
                c.doc_id,
                d.title AS doc_title,
                c.content,
                1 - (v.embedding <=> ?::vector) AS similarity,
                v.kb_id
            FROM t_knowledge_vector v
            JOIN t_knowledge_chunk c ON v.chunk_id = c.id
            JOIN t_knowledge_document d ON c.doc_id = d.id
            WHERE d.status = 'READY'
              AND (?::bigint IS NULL OR v.kb_id = ?::bigint)
            ORDER BY v.embedding <=> ?::vector
            LIMIT ?
            """;
        List<SearchResult> results = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            PGvector queryVec = new PGvector(queryEmbedding);
            ps.setObject(1, queryVec);
            // kbId 为 null 时不过滤（全库检索），否则限定知识库
            if (kbId == null) {
                ps.setNull(2, Types.BIGINT);
                ps.setNull(3, Types.BIGINT);
            } else {
                ps.setLong(2, kbId);
                ps.setLong(3, kbId);
            }
            ps.setObject(4, queryVec);  // ORDER BY 也用到
            ps.setInt(5, topK);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(new SearchResult(
                        rs.getLong("chunk_id"),
                        rs.getLong("doc_id"),
                        rs.getString("doc_title"),
                        rs.getString("content"),
                        rs.getFloat("similarity"),
                        rs.getLong("kb_id")
                    ));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Vector search failed", e);
        }
        return results;
    }

    // ... deleteByKbId, deleteByDocId 等实现
}
```

### 2.3 文档分块策略 `MarkdownChunker.java`

```java
package io.github.somehow.mysite.ragent.ingestion;

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

    public List<Chunk> chunk(String markdownContent, Long docId, Long kbId) {
        int chunkSize = properties.getChunk().getSize();       // 800
        int overlap = properties.getChunk().getOverlap();      // 100
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
```

### 2.4 文章事件监听 `ArticleEventListener.java`

```java
package io.github.somehow.mysite.ragent.ingestion;

import io.github.somehow.mysite.dao.entity.ArticleDO;
import io.github.somehow.mysite.ragent.service.KnowledgeDocumentService;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 监听 ArticleCreatedEvent / ArticleUpdatedEvent，自动触发文档向量化。
 *
 * 为什么用 Spring Event 而不是直接在 ArticleService 里调？
 *   1. 解耦：Blog 模块不需要知道 RAG 模块的存在
 *   2. 异步：文档向量化需要调用外部 API（嵌入模型），不能让文章发布变慢
 *   3. 可测试：可以独立测试 RAG 模块，Mock Event 即可
 */
@Component
public class ArticleEventListener {

    private final KnowledgeDocumentService knowledgeDocumentService;

    // 不在这一层加 @Async：异步在 KnowledgeDocumentService.syncArticle 上做，
    // 双重 @Async 没有收益，还会让异常栈和线程模型变复杂
    @EventListener
    public void handleArticleCreated(ArticleCreatedEvent event) {
        knowledgeDocumentService.syncArticle(event.getArticle());
    }

    @EventListener
    public void handleArticleUpdated(ArticleUpdatedEvent event) {
        knowledgeDocumentService.syncArticle(event.getArticle());
    }
}
```

**还需要在 ArticleServiceImpl 中发布事件**（这是对现有代码唯一的功能性修改）：

```java
// ArticleServiceImpl.java 中新增
@Autowired
private ApplicationEventPublisher eventPublisher;

public ArticleDO saveOrUpdate(ArticleDO article) {
    // ... 现有保存逻辑 ...
    articleMapper.insert(article);

    // ★ 新增：发布文章创建事件，触发 RAG 索引
    eventPublisher.publishEvent(new ArticleCreatedEvent(article));

    return article;
}
```

### 2.5 文档向量化全流程 `KnowledgeDocumentService.java`

```java
package io.github.somehow.mysite.ragent.service;

import io.github.somehow.mysite.dao.entity.ArticleDO;
import io.github.somehow.mysite.ragent.dao.entity.*;
import io.github.somehow.mysite.ragent.dao.mapper.*;
import io.github.somehow.mysite.ragent.ingestion.DocumentChunker;
import io.github.somehow.mysite.ragent.llm.EmbeddingService;
import io.github.somehow.mysite.ragent.vector.VectorStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class KnowledgeDocumentService {

    /**
     * 文章 → 知识库同步（异步执行，不阻塞文章发布）。
     *
     * 失败处理（2026-07-17 修订补充）：
     *   原方案全程无 try/catch —— embedding API 一旦失败，文档记录会永远停在
     *   CHUNKING 状态，排查时无从知晓原因。现在捕获所有异常：标记 FAILED +
     *   记录 fail_reason，Dashboard 文档列表可直观看到失败并手动重试
     *   （重试 = 再次调用本方法，开头的"删旧档"逻辑保证幂等，
     *   建表 SQL 里的 uk_doc_source 唯一约束兜底并发重复插入）。
     */
    @Async("ragAsyncExecutor")
    public void syncArticle(ArticleDO article) {
        KnowledgeDocumentDO doc = null;
        try {
            // 1. 找到默认知识库（如果没有就创建一个）
            KnowledgeBaseDO kb = getOrCreateDefaultKb();

            // 2. 检查是否已有该文章的文档记录 → 有则删除旧的向量/分块/文档
            KnowledgeDocumentDO existingDoc = docMapper.findBySourceRef(
                kb.getId(), "ARTICLE", article.getId().toString());
            if (existingDoc != null) {
                vectorStore.deleteByDocId(existingDoc.getId());
                chunkMapper.deleteByDocId(existingDoc.getId());
                docMapper.deleteById(existingDoc.getId());
            }

            // 3. 创建新文档记录
            doc = new KnowledgeDocumentDO();
            doc.setKbId(kb.getId());
            doc.setTitle(article.getTitle());
            doc.setSourceType("ARTICLE");
            doc.setSourceRef(article.getId().toString());
            doc.setFileType("MD");
            doc.setStatus("PENDING");
            docMapper.insert(doc);

            // 4. 分块
            String content = article.getContent();
            List<Chunk> chunks = chunker.chunk(content, doc.getId(), kb.getId());
            doc.setChunkCount(chunks.size());
            doc.setCharCount(content.length());
            doc.setStatus("CHUNKING");
            docMapper.updateById(doc);

            // 5. 批量嵌入（内部按供应商上限分批，见 1.2.7）
            List<String> chunkTexts = chunks.stream().map(Chunk::content).toList();
            List<float[]> embeddings = embeddingService.embedBatch(chunkTexts);

            // 6. 存储 chunks + vectors
            for (int i = 0; i < chunks.size(); i++) {
                Chunk c = chunks.get(i);
                KnowledgeChunkDO chunkDO = new KnowledgeChunkDO();
                chunkDO.setDocId(doc.getId());
                chunkDO.setKbId(kb.getId());
                chunkDO.setChunkIndex(c.index());
                chunkDO.setContent(c.content());
                chunkDO.setCharCount(c.content().length());
                chunkMapper.insert(chunkDO);

                vectorStore.insert(List.of(new VectorStore.VectorEntry(
                    chunkDO.getId(), kb.getId(), embeddings.get(i), kb.getEmbeddingModel()
                )));
            }

            // 7. 标记完成
            doc.setStatus("READY");
            docMapper.updateById(doc);
        } catch (Exception e) {
            // 任何一步失败：标记 FAILED + 记录原因，等下次同步或人工重试
            log.error("文章向量化失败, articleId={}", article.getId(), e);
            if (doc != null && doc.getId() != null) {
                doc.setStatus("FAILED");
                doc.setFailReason(e.getClass().getSimpleName() + ": " + e.getMessage());
                docMapper.updateById(doc);
            }
        }
    }
}
```

### 2.6 Phase 2 验证方式

```java
@Test
void testArticleChunkingAndVectorization() {
    // 1. 创建一篇文章（模拟 Markdown 内容）
    ArticleDO article = new ArticleDO();
    article.setId(100L);
    article.setTitle("Spring Security JWT 认证配置指南");
    article.setContent("""
        ## JWT 过滤器配置
        
        Spring Security 中的 JWT 认证主要通过 OncePerRequestFilter 实现...
        
        ### 核心配置步骤
        
        1. 创建 JwtAuthenticationFilter 继承 OncePerRequestFilter
        2. 在 SecurityFilterChain 中注册过滤器
        3. 配置 permitAll 和 authenticated 路径
        ...
        """);

    // 2. 触发同步
    articleEventListener.handleArticleCreated(
        new ArticleCreatedEvent(article));

    // 3. 等待异步处理完成
    //    建议用 Awaitility（await().atMost(10, SECONDS).until(...)）代替 Thread.sleep，
    //    CI 上 5 秒不一定够，轮询断言更稳
    Thread.sleep(5000);

    // 4. 验证：数据库中应该有 chunks 和 vectors
    List<KnowledgeChunkDO> chunks = chunkMapper.selectByDocId(docId);
    Assertions.assertTrue(chunks.size() > 0);

    // 5. 验证：向量检索能找到相关片段（kbId 传 null = 全库检索）
    float[] queryEmbedding = embeddingService.embed("JWT过滤器怎么配置的？");
    List<SearchResult> results = vectorStore.search(queryEmbedding, 3, null);
    Assertions.assertTrue(results.size() > 0);
    Assertions.assertTrue(results.get(0).content().contains("JWT"));
}
```

### Phase 2 验收清单

- [ ] 双数据源配置正确：主库 `@Primary`、两个 `@MapperScan` 各显式指定 `sqlSessionFactoryRef`，启动无 `NoUniqueBeanDefinitionException`
- [ ] 删除 `config/RagentDataSourceConfig.java` 误建空壳（保留 `ragent/config/` 下的实现）
- [ ] `MarkdownChunker` 分块：frontmatter 正确去除，overlap 保留
- [ ] `PgvectorVectorStore.insert()` 向量写入成功（ID 用 `IdWorker.getId()` 雪花生成）
- [ ] `PgvectorVectorStore.search()` 相似度检索正确（相似内容排前面；kbId 传 null 全库、传值限定）
- [ ] `ArticleEventListener` 异步执行不阻塞文章发布（@Async 只在 syncArticle 一层）
- [ ] 完整链路：发布文章 → 自动分块 → 向量化入库
- [ ] 失败路径：人为配错 embedding API key 后发布文章，文档状态变为 FAILED 且 fail_reason 有值
- [ ] 重复发布/更新同一文章不产生重复文档（uk_doc_source 唯一约束生效）

---

## Phase 3：RAG 问答核心链路 —— 理解 Agent 的"思考"

> **目标**：实现完整的 RAG 问答管道，用户提问 → 检索 → 重排序 → 生成 → SSE 流式返回。
> **预计时间**：5-8 天
> **学习重点**：RAG 完整链路、Prompt Engineering、Rerank 精排、SSE 协议
> **验收标准**：通过 curl 或 Postman 发问题，流式收到 AI 回复，回复引用了博客文章内容。

### 3.1 检索引擎 `RetrievalEngine.java`

```java
package io.github.somehow.mysite.ragent.core.retrieval;

import io.github.somehow.mysite.ragent.llm.EmbeddingService;
import io.github.somehow.mysite.ragent.llm.RerankService;
import io.github.somehow.mysite.ragent.vector.VectorStore;
import io.github.somehow.mysite.ragent.vector.VectorStore.SearchResult;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 检索引擎：向量检索 + Rerank 精排（两阶段检索）。
 *
 * 为什么需要两阶段？
 *   Stage 1 - 向量检索（粗排）：从全库中找到 Top 10 候选
 *     - 优点：速度快（HNSW 索引 O(log N)）
 *     - 缺点：纯向量相似度不够精确，可能混入语义相近但不相关的内容
 *
 *   Stage 2 - Rerank（精排）：用专门的 Rerank 模型对 Top 10 重排序
 *     - Rerank 模型比 Embedding 模型更"聪明"：它同时看问题和文档，
 *       判断"这篇文档是否能回答这个问题"，而不仅仅是"这两段文本是否相似"
 *     - 输入：(question, doc1), (question, doc2), ...
 *     - 输出：每个 pair 的相关性分数，取 Top 5
 *     - 成本比直接向量检索高，但只在候选集上跑，所以可控
 *
 * 典型场景：
 *   用户问："JWT 过滤器怎么配置？"
 *   向量检索可能返回：Top 1 = "JWT 简介"（语义相似但没回答"怎么配置"）
 *   Rerank 会纠正：把 "JWT 过滤器配置步骤" 提到 Top 1
 */
@Component
public class RetrievalEngine {

    private final VectorStore vectorStore;
    private final EmbeddingService embeddingService;
    private final RerankService rerankService;
    private final RagProperties properties;

    /**
     * 检索相关文档片段
     * @param question 用户问题（原始文本）
     * @param topK 最终返回多少个片段
     * @return 检索结果，按相关性降序
     */
    public List<SearchResult> retrieve(String question, int topK) {
        // Stage 1: 向量检索 Top K（kbId 传 null = 全库；多知识库时传入目标 kbId）
        float[] queryEmbedding = embeddingService.embed(question);
        List<SearchResult> candidates = vectorStore.search(
            queryEmbedding,
            properties.getRetrieval().getTopK(),  // 默认 10
            null
        );

        // 过滤低分结果
        candidates = candidates.stream()
            .filter(r -> r.score() >= properties.getRetrieval().getScoreThreshold())
            .toList();

        if (candidates.isEmpty()) {
            return List.of();
        }

        // Stage 2: Rerank 精排（如果配置了 Rerank 服务）
        if (rerankService != null && candidates.size() > topK) {
            candidates = rerankService.rerank(question, candidates, topK);
        } else if (candidates.size() > topK) {
            candidates = candidates.subList(0, topK);
        }

        return candidates;
    }
}
```

### 3.2 Rerank 服务 `RerankService.java`

```java
package io.github.somehow.mysite.ragent.llm;

import io.github.somehow.mysite.ragent.vector.VectorStore.SearchResult;
import java.util.List;

/**
 * 重排序服务 —— 用专门的 Rerank 模型对检索结果精排。
 *
 * ⚠️ 格式修正（2026-07-17）：Rerank **不是** OpenAI 兼容协议的一部分，
 * 各供应商格式不同，不能套用 AbstractOpenAIProvider：
 *
 * 百炼 gte-rerank（本项目选用，走 DashScope 原生接口，compatible-mode 不含 rerank）：
 *   POST https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank
 *   Header: Authorization: Bearer {apiKey}
 *   Body: {
 *     "model": "gte-rerank",
 *     "input": {
 *       "query": "JWT 过滤器怎么配置？",
 *       "documents": ["文档片段1", "文档片段2", ...]
 *     },
 *     "parameters": {"top_n": 5, "return_documents": true}
 *   }
 *   响应：{"output": {"results": [{"index": 3, "relevance_score": 0.95, "document": {...}}, ...]}}
 *
 * 对照：SiliconFlow / Cohere / Jina 是另一种扁平格式（POST /v1/rerank，
 * body 为 {"model","query","documents","top_n"}），两者不要混用。
 *
 * 结论：实现 BaiLianRerankProvider 单独处理百炼格式；rerank 固定使用
 * 主供应商（百炼），不做跨供应商降级 —— 降级供应商没配 rerank 模型时
 * 退化为直接用向量检索的 Top K（RetrievalEngine 里已有这个兜底分支）。
 */
public interface RerankService {
    List<SearchResult> rerank(String query, List<SearchResult> candidates, int topN);
}
```

### 3.3 对话记忆 `ConversationManager.java`

```java
package io.github.somehow.mysite.ragent.core.memory;

import com.alibaba.fastjson2.JSON;
import io.github.somehow.mysite.ragent.dao.entity.ConversationDO;
import io.github.somehow.mysite.ragent.dao.entity.ConversationMessageDO;
import io.github.somehow.mysite.ragent.dao.mapper.ConversationMapper;
import io.github.somehow.mysite.ragent.dao.mapper.ConversationMessageMapper;
import io.github.somehow.mysite.ragent.dto.SourceChunkDTO;
import io.github.somehow.mysite.ragent.llm.ChatMessage;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 对话记忆管理器。
 *
 * 记忆策略（渐进式）：
 *   Phase 3 实现：
 *     - 滑动窗口：保留最近 N 轮（默认 6 轮 = 12 条消息）
 *     - 超出窗口的消息不加载
 *
 *   Phase 4 可加（后期优化）：
 *     - 摘要压缩：当消息超过阈值时，让 LLM 把早期对话总结成一段话
 *       例如："用户之前问了关于 Spring Security 的问题，我建议他使用 JWT 认证方案..."
 *       这段话作为 system message 注入，节省 token。
 */
@Component
public class ConversationManager {

    private final ConversationMapper conversationMapper;
    private final ConversationMessageMapper messageMapper;
    private final RagProperties properties;

    /**
     * 加载对话历史（最近 N 轮）。
     * @return ChatMessage 列表，可直接放入 LLM 请求的 messages 数组中
     */
    public List<ChatMessage> loadHistory(Long conversationId) {
        if (conversationId == null) return List.of();

        int keepTurns = properties.getMemory().getKeepTurns(); // 6
        List<ConversationMessageDO> recentMessages = messageMapper
            .selectRecentByConversationId(conversationId, keepTurns * 2);

        return recentMessages.stream()
            // 注意：selectRecentByConversationId 按时间倒序取最近 N 条，
            // 装入 messages 前必须翻正为时间正序，否则 LLM 看到的对话是反的
            .sorted(Comparator.comparing(ConversationMessageDO::getCreateTime))
            .map(msg -> {
                if ("USER".equals(msg.getRole())) {
                    return ChatMessage.user(msg.getContent());
                } else {
                    return ChatMessage.assistant(msg.getContent());
                }
            })
            .toList();
    }

    /**
     * 获取或创建会话（2026-07-17 修订新增 —— 原方案只有 loadHistory，
     * 会话创建 / 消息落库 / conversationId 回传整条链路缺失，
     * 对话记忆实际上跑不起来）。
     *
     * @param conversationId 前端带来的会话 ID（null = 新会话）
     * @param visitorId      匿名访客标识（前端 localStorage UUID，见 3.5 节决策 3）
     * @param firstQuestion  新会话用它生成标题
     */
    public ConversationDO getOrCreateConversation(Long conversationId, String visitorId, String firstQuestion) {
        if (conversationId != null) {
            ConversationDO existing = conversationMapper.selectById(conversationId);
            // 防 IDOR：会话必须属于这个 visitor，否则视为新会话
            if (existing != null && Objects.equals(existing.getVisitorId(), visitorId)) {
                return existing;
            }
        }
        ConversationDO conv = new ConversationDO();
        conv.setVisitorId(visitorId);
        conv.setTitle(firstQuestion.length() > 20
            ? firstQuestion.substring(0, 20) + "…" : firstQuestion);
        conversationMapper.insert(conv);
        return conv;
    }

    /**
     * 保存一轮问答（流式生成完成后调用）。
     * 注意：这是阻塞 JDBC，应在 boundedElastic / ragAsyncExecutor 上执行，
     * 不要占用 reactor 的 event-loop 线程。
     */
    public void saveExchange(Long conversationId, String question,
                             String answer, List<SourceChunkDTO> sources) {
        ConversationMessageDO userMsg = new ConversationMessageDO();
        userMsg.setConversationId(conversationId);
        userMsg.setRole("USER");
        userMsg.setContent(question);
        messageMapper.insert(userMsg);

        ConversationMessageDO assistantMsg = new ConversationMessageDO();
        assistantMsg.setConversationId(conversationId);
        assistantMsg.setRole("ASSISTANT");
        assistantMsg.setContent(answer);
        assistantMsg.setSourcesJson(JSON.toJSONString(sources));  // sources 列是 JSONB
        messageMapper.insert(assistantMsg);

        // 更新会话的消息数与 update_time（PG 没有 ON UPDATE，应用层维护）
        conversationMapper.touchMessageCount(conversationId, 2);
    }
}
```

### 3.4 Prompt 模板 `PromptTemplate.java`

```java
package io.github.somehow.mysite.ragent.core.prompt;

import io.github.somehow.mysite.ragent.llm.ChatMessage;
import io.github.somehow.mysite.ragent.vector.VectorStore.SearchResult;

import java.util.List;

/**
 * Prompt 模板 —— RAG 质量的关键因素之二（第一是检索质量）。
 *
 * Prompt 写得好不好，直接决定 AI 回答的质量。这里参考了 Ragent 的
 * StringTemplate 设计，但简化为 Java String.format() 风格。
 *
 * Prompt 设计原则（面试常考）：
 *   1. 角色设定：明确告诉 AI 它是谁（你是一个博客助手）
 *   2. 知识边界：明确信息来源（只根据提供的文章内容回答）
 *   3. 诚实原则：不知道就说不知道，不要编造
 *   4. 引用要求：要求 AI 在回答中引用来源
 *   5. 格式规范：要求 Markdown 格式、代码高亮等
 */
@Component
public class PromptTemplate {

    /**
     * 构建 RAG 问答的完整 Prompt
     */
    public List<ChatMessage> buildRagPrompt(
            String question,
            List<SearchResult> retrievedContext,
            List<ChatMessage> history) {

        // 1. System Prompt：角色设定 + 知识边界
        String systemPrompt = """
            你是"somehow 的博客"的 AI 助手。你的职责是帮助读者理解博客中的技术内容。

            ## 知识来源
            你只能基于下面提供的博客文章片段来回答问题。每个片段都标注了来源文章。

            ## 重要规则
            1. 如果提供的内容足以回答问题，请详细、准确地回答，并在文中引用来源
               （例如："根据《Spring Security 实战》一文..."）。
            2. 如果提供的片段不足以回答，请诚实地说"博客中暂时没有涉及这个问题的文章"，
               不要编造信息。
            3. 如果你引用了具体代码或配置，务必标注来自哪篇文章。
            4. 使用 Markdown 格式，代码块标注语言。
            5. 回答要友好但专业，面向懂技术的读者。

            ## 提供的博客内容
            %s
            """.formatted(formatContext(retrievedContext));

        // 2. 组装 messages
        List<ChatMessage> messages = new java.util.ArrayList<>();
        messages.add(ChatMessage.system(systemPrompt));

        // 3. 加入对话历史（如果有）
        messages.addAll(history);

        // 4. 用户当前问题
        messages.add(ChatMessage.user(question));

        return messages;
    }

    /**
     * 格式化检索上下文，让 LLM 知道每段内容的来源。
     */
    private String formatContext(List<SearchResult> results) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < results.size(); i++) {
            SearchResult r = results.get(i);
            sb.append("---\n");
            sb.append("[来源%d] 文章《%s》（相关性: %.2f）\n\n".formatted(
                i + 1, r.docTitle(), r.score()));
            sb.append(r.content()).append("\n\n");
        }
        return sb.toString();
    }

    /**
     * 无检索结果时的 Prompt（通用聊天模式）
     */
    public List<ChatMessage> buildGeneralPrompt(String question, List<ChatMessage> history) {
        String systemPrompt = """
            你是"somehow 的博客"的 AI 助手。用户可以和你聊天或询问技术问题。
            如果用户询问博客相关的内容而你无法回答，建议他们查看博客文章。
            保持友好、专业的语气。
            """;
        List<ChatMessage> messages = new java.util.ArrayList<>();
        messages.add(ChatMessage.system(systemPrompt));
        messages.addAll(history);
        messages.add(ChatMessage.user(question));
        return messages;
    }
}
```

### 3.5 SSE 事件模型 `ChatEvent.java` + RAG 问答核心服务 `RagChatService.java`

**为什么需要 ChatEvent（2026-07-17 修订）？** 原方案 `RagChatService.chat()` 返回
`Flux<String>`（纯 token 流），但数据流图和前端约定了 `sources` 事件、多轮对话需要
回传 `conversationId`——纯 token 流里这些都无处安放（"sources 事件没有生产者"）。
改为返回 `Flux<ChatEvent>`，统一前后端协议：

```java
package io.github.somehow.mysite.ragent.llm;

import io.github.somehow.mysite.ragent.dto.SourceChunkDTO;
import java.util.List;

/**
 * SSE 事件模型 —— RagChatService 与前端之间的统一协议。
 *
 * 事件序列（一次问答）：
 *   meta    ×1 → 流开始时发，携带 conversationId（新会话由后端创建，
 * *            前端存下来，后续轮次带上它才有"记忆"）
 *   sources ×1 → 检索到的引用来源（无检索结果时为空数组），在 content 之前发
 *   content ×N → 每个 token 一条
 *   done    ×1 → 正常结束
 *   error   ×1 → 出错时发（替代裸断开，前端可以展示友好提示）
 */
public record ChatEvent(
    String type,                    // meta / sources / content / done / error
    String delta,                   // content 事件的 token
    List<SourceChunkDTO> sources,   // sources 事件的引用来源
    Long conversationId,            // meta 事件的会话 ID
    String message                  // error 事件的错误信息
) {
    public static ChatEvent meta(Long conversationId) {
        return new ChatEvent("meta", null, null, conversationId, null);
    }
    public static ChatEvent sources(List<SourceChunkDTO> sources) {
        return new ChatEvent("sources", null, sources, null, null);
    }
    public static ChatEvent content(String delta) {
        return new ChatEvent("content", delta, null, null, null);
    }
    public static ChatEvent done() {
        return new ChatEvent("done", null, null, null, null);
    }
    public static ChatEvent error(String message) {
        return new ChatEvent("error", null, null, null, message);
    }
}
```

```java
package io.github.somehow.mysite.ragent.service;

import io.github.somehow.mysite.ragent.core.memory.ConversationManager;
import io.github.somehow.mysite.ragent.core.prompt.PromptTemplate;
import io.github.somehow.mysite.ragent.core.retrieval.RetrievalEngine;
import io.github.somehow.mysite.ragent.dao.entity.ConversationDO;
import io.github.somehow.mysite.ragent.dto.SourceChunkDTO;
import io.github.somehow.mysite.ragent.llm.*;
import io.github.somehow.mysite.ragent.vector.VectorStore.SearchResult;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.List;

/**
 * RAG 问答核心服务 —— 整个 RAG 系统的"大脑皮层"。
 *
 * 这里组装了 RAG 的完整链路，每一步都是一个可以独立替换的策略。
 * 理解了这段代码，你就理解了当前 90% 的 RAG 系统的运行原理。
 *
 * 完整链路（复习）：
 *   0. 限流检查（成本保护，见 3.7）
 *   1. 获取或创建会话（visitorId 归属）
 *   2. 加载对话记忆（滑动窗口）
 *   3. 向量检索（问题 → embedding → pgvector cosine 检索）
 *   4. Rerank 精排（可选）
 *   5. 组装 Prompt（检索上下文 + 对话历史 + 用户问题）
 *   6. LLM 流式生成（多供应商路由 + 断路器）
 *   7. 完成后保存问答记录
 */
@Service
public class RagChatService {

    private final RetrievalEngine retrievalEngine;
    private final ConversationManager conversationManager;
    private final PromptTemplate promptTemplate;
    private final RoutingLLMService routingLLMService;
    private final ChatRateLimiter rateLimiter;

    /**
     * RAG 流式问答 —— 核心入口。
     *
     * @param question       用户问题
     * @param conversationId 对话 ID（null = 新对话）
     * @param visitorId      匿名访客标识（前端 localStorage UUID）
     * @param clientIp       客户端 IP（限流用）
     * @return ChatEvent 事件流（meta → sources → content×N → done）
     */
    public Flux<ChatEvent> chat(String question, Long conversationId,
                                String visitorId, String clientIp) {
        // Step 0: 成本保护 —— 超频/超长直接拒绝（抛出后由 Controller 转成 error 事件）
        rateLimiter.check(clientIp, question);

        // Step 1: 获取或创建会话（防 IDOR，见 ConversationManager）
        ConversationDO conversation = conversationManager
            .getOrCreateConversation(conversationId, visitorId, question);
        Long convId = conversation.getId();

        // Step 2: 加载对话历史
        List<ChatMessage> history = conversationManager.loadHistory(convId);

        // Step 3: 向量检索
        List<SearchResult> retrievedChunks = retrievalEngine.retrieve(question, 5);
        List<SourceChunkDTO> sources = retrievedChunks.stream()
            .map(r -> new SourceChunkDTO(r.docTitle(), r.content(), r.score()))
            .toList();

        // Step 4: 按是否有检索结果选择 Prompt 策略
        List<ChatMessage> messages = retrievedChunks.isEmpty()
            ? promptTemplate.buildGeneralPrompt(question, history)   // 无匹配：通用聊天
            : promptTemplate.buildRagPrompt(question, retrievedChunks, history); // 有匹配：RAG

        // Step 5: 调用 LLM 流式生成（多供应商路由，自动降级）
        ChatRequest request = ChatRequest.builder()
            .messages(messages)
            .temperature(0.7)
            .maxTokens(2048)
            .build();

        // Step 6: 组装事件流 + 完成后落库
        StringBuilder fullAnswer = new StringBuilder();
        return Flux.concat(
                Flux.just(ChatEvent.meta(convId), ChatEvent.sources(sources)),
                routingLLMService.chatStream(request).map(ChatEvent::content),
                Flux.just(ChatEvent.done())
            )
            .doOnNext(e -> {
                if ("content".equals(e.type())) {
                    fullAnswer.append(e.delta());
                }
            })
            // 落库是阻塞 JDBC，切到 boundedElastic，不占 event-loop
            .publishOn(Schedulers.boundedElastic())
            .doOnComplete(() -> conversationManager.saveExchange(
                convId, question, fullAnswer.toString(), sources))
            // 统一兜底：任何异常（含全部供应商失败）转成 error 事件，而不是裸断开
            .onErrorResume(e -> Flux.just(ChatEvent.error("AI 服务暂时不可用，请稍后再试")));
    }
}
```

### 3.6 SSE 控制器 `RagChatController.java`

```java
package io.github.somehow.mysite.ragent.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.somehow.mysite.ragent.llm.ChatEvent;
import io.github.somehow.mysite.ragent.service.RagChatService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.Executor;

/**
 * RAG 聊天 SSE 端点。
 *
 * SSE (Server-Sent Events) 协议基础：
 *   1. 请求头：Accept: text/event-stream
 *   2. 响应头：Content-Type: text/event-stream
 *   3. 数据格式（每个事件一行 JSON，与前端 useChat 的解析一一对应）：
 *      data: {"type":"meta","conversationId":123}\n\n
 *      data: {"type":"sources","sources":[...]}\n\n
 *      data: {"type":"content","delta":"你好"}\n\n
 *      data: {"type":"done"}\n\n
 *
 * Spring MVC 通过 SseEmitter 支持 SSE（Spring 6 / Boot 3 原生支持）。
 * 也可以用 WebFlux 的 Flux<ServerSentEvent>，但这需要整个 Controller
 * 返回 Reactive 类型。这里选用 SseEmitter 是因为项目主要用 Spring MVC。
 */
@RestController
@RequestMapping("/v1/rag")
public class RagChatController {

    private final RagChatService ragChatService;
    private final Executor ragExecutor;
    private final ObjectMapper objectMapper;

    /**
     * SSE 流式聊天端点。
     *
     * GET /v1/rag/chat/stream?q=JWT怎么配置的？&conversationId=123&visitorId=uuid
     *
     * 注意（2026-07-17 修订）：
     *   - 事件序列化用 ObjectMapper，不再手工拼 JSON 字符串 —— 原方案的
     *     escapeJson() 只转义了 4 种字符，遇到其他控制字符会产出非法 JSON
     *   - visitorId：匿名会话归属标识（见 3.5 节决策 3）
     */
    @GetMapping("/chat/stream")
    public SseEmitter chatStream(
            @RequestParam("q") String question,
            @RequestParam(value = "conversationId", required = false) Long conversationId,
            @RequestParam(value = "visitorId", required = false) String visitorId,
            HttpServletRequest httpRequest) {

        SseEmitter emitter = new SseEmitter(120_000L);  // 120 秒超时
        String clientIp = resolveClientIp(httpRequest);

        ragExecutor.execute(() -> {
            try {
                ragChatService.chat(question, conversationId, visitorId, clientIp)
                    .subscribe(
                        event -> sendEvent(emitter, event),
                        // service 已兜底成 error 事件，这里理论上走不到；
                        // 若真走到，再补一条 error 事件并关闭连接
                        error -> sendEvent(emitter, ChatEvent.error("AI 服务异常")),
                        // 正常结束由 done/error 事件内部调用 emitter.complete()
                        () -> {}
                    );
            } catch (Exception e) {
                // 同步阶段异常（如限流拒绝在订阅前抛出）
                sendEvent(emitter, ChatEvent.error(e.getMessage()));
            }
        });

        return emitter;
    }

    /** 序列化事件并推送；客户端断开时静默结束（IOException 属正常断连） */
    private void sendEvent(SseEmitter emitter, ChatEvent event) {
        try {
            emitter.send(SseEmitter.event()
                .data(objectMapper.writeValueAsString(event), MediaType.APPLICATION_JSON));
            if ("done".equals(event.type()) || "error".equals(event.type())) {
                emitter.complete();
            }
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
    }

    /** 取真实客户端 IP：优先 X-Forwarded-For（Nginx 反代后会带，见 5.2） */
    private String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
```

### 3.7 Spring Security 白名单调整 + 成本保护（限流）

**文件**：`WebSecurityConfig.java`

在 `permitAll` 列表中添加 RAG 聊天端点：
```java
// RAG 端点
"/v1/rag/chat/stream"     // 任何人都可以聊天（匿名会话，visitorId 归属）
```

其他 RAG 端点按需配置：
```java
"/v1/rag/knowledge-bases"        // 任何人可查看知识库列表
"/v1/rag/conversations/**"       // 登录用户可管理自己的对话
"/v1/rag/knowledge-base/**"      // DEVELOPER 管理知识库
```

**匿名聊天 = 付费 API 敞口，限流是必做项（见 3.5 节决策 4）**：
`permitAll` 意味着任何人都能直接刷这个端点，百炼免费额度烧完就是按 token 计费。
实现一个基于 Redis 的简单限流器（Redis 是现成基础设施）：

```java
package io.github.somehow.mysite.ragent.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 聊天限流器 —— 成本保护的第一道闸。
 *
 * 策略（够用就好，不上令牌桶）：
 *   1. IP 维度计数：key = rag:chat:rl:{ip}，INCR 后第一次设置 1 小时过期，
 *      超过 rag.rate-limit.max-per-hour（默认 20）拒绝
 *   2. 问题长度上限：rag.rate-limit.max-question-length（默认 500 字符），
 *      超长直接拒绝（长问题 = 高 token 消耗）
 *
 * 注意：限流检查要在 RagChatService.chat() 的最开头（Step 0），
 * 在调用任何 LLM API 之前拦截。
 */
@Component
public class ChatRateLimiter {

    private final StringRedisTemplate redisTemplate;
    private final RagProperties properties;

    public void check(String clientIp, String question) {
        int maxPerHour = properties.getRateLimit().getMaxPerHour();
        int maxLength = properties.getRateLimit().getMaxQuestionLength();

        if (question != null && question.length() > maxLength) {
            throw new RateLimitExceededException(
                "问题太长了，请精简到 " + maxLength + " 字以内");
        }

        String key = "rag:chat:rl:" + clientIp;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, Duration.ofHours(1));
        }
        if (count != null && count > maxPerHour) {
            throw new RateLimitExceededException(
                "提问太频繁了，每小时最多 " + maxPerHour + " 次，请稍后再试");
        }
    }

    /** 自定义异常：Controller 捕获后转成 ChatEvent.error 推给前端 */
    public static class RateLimitExceededException extends RuntimeException {
        public RateLimitExceededException(String message) {
            super(message);
        }
    }
}
```

### 3.8 Phase 3 验证方式

```bash
# 用 curl 测试 SSE 流式聊天（终端直接看 token 流）
curl -N -X GET \
  "http://localhost:8081/v1/rag/chat/stream?q=JWT过滤器是怎么配置的？&visitorId=test-uuid-001" \
  -H "Accept: text/event-stream"

# 预期输出（逐行推送）：
# data: {"type":"meta","conversationId":123,...}
# data: {"type":"sources","sources":[{"title":"Spring Security 实战...",...}],...}
# data: {"type":"content","delta":"在",...}
# data: {"type":"content","delta":"Spring",...}
# ...
# data: {"type":"done",...}
#
# 多轮记忆验证：带上 meta 事件返回的 conversationId 再问一个指代性问题
# （如"它的配置类叫什么？"），AI 应能理解"它"指代上文内容
```

```java
@Test
void testRagChatFullPipeline() {
    // 端到端测试：限流 → 建会话 → 检索 → Rerank → 生成 → 落库
    String question = "JWT 过滤器怎么配置？";
    Flux<ChatEvent> stream = ragChatService.chat(question, null, "test-visitor", "127.0.0.1");

    List<ChatEvent> events = stream.collectList().block(Duration.ofSeconds(120));

    // 事件序列校验：meta 在最前、done 在最后、content 在中间
    Assertions.assertEquals("meta", events.get(0).type());
    Assertions.assertNotNull(events.get(0).conversationId());   // 新会话已创建
    Assertions.assertEquals("done", events.get(events.size() - 1).type());

    String fullResponse = events.stream()
        .filter(e -> "content".equals(e.type()))
        .map(ChatEvent::delta)
        .reduce("", String::concat);
    Assertions.assertTrue(fullResponse.length() > 50);
    // 回答中应包含博客文章中的内容
    Assertions.assertTrue(
        fullResponse.contains("JwtAuthenticationFilter") ||
        fullResponse.contains("OncePerRequestFilter")
    );

    // 落库校验：用返回的 conversationId 能查到这一问一答
    List<ConversationMessageDO> messages = messageMapper
        .selectRecentByConversationId(events.get(0).conversationId(), 2);
    Assertions.assertEquals(2, messages.size());
}
```

### Phase 3 验收清单

- [ ] `RetrievalEngine.retrieve()` 检索结果包含相关文章片段（`VectorStore.search` 已加 kbId 参数）
- [ ] `RerankService.rerank()` 使用百炼原生接口格式（不是 OpenAI `/rerank` 格式）
- [ ] 未配置 Rerank 时自动退化为向量检索 Top K
- [ ] `ConversationManager.loadHistory()` 正确加载最近 6 轮对话，且消息顺序为正序
- [ ] `ConversationManager.getOrCreateConversation()` 创建新会话并校验 visitorId 归属
- [ ] `ConversationManager.saveExchange()` 流式生成结束后正确保存一问一答
- [ ] `PromptTemplate.buildRagPrompt()` 生成的 Prompt 格式正确
- [ ] `RagChatService.chat()` 返回 `Flux<ChatEvent>`，事件序列为 meta → sources → content×N → done
- [ ] `RagChatService.chat()` 在 LLM 前完成限流检查（超频/超长直接拒绝）
- [ ] `RagChatController` 用 ObjectMapper 序列化事件（不再手工 escapeJson）
- [ ] `curl /v1/rag/chat/stream` 能收到流式 SSE 响应，包含 meta 和 sources 事件
- [ ] 有检索结果时 AI 回答引用了博客文章
- [ ] 无检索结果时 AI 回复友好（不编造）
- [ ] 多轮对话能正确引用历史（conversationId 回传后再次提问，AI 保持上下文）
- [ ] 流式降级边界正确：百炼挂掉 → SiliconFlow 兜底；一旦已输出 token 后失败不降级

---

## Phase 4：前端聊天组件 —— 理解 Agent 的"脸面"

> **目标**：在博客页面右下角添加 AI 聊天浮窗，用户可以随时提问。
> **预计时间**：4-6 天
> **学习重点**：EventSource API、流式渲染（打字机效果）、Markdown 实时渲染
> **验收标准**：打开博客 → 点击右下角聊天按钮 → 输入问题 → 看到打字机效果的 AI 回复。

### 4.1 前端文件新增清单

```
mysite-frontend/src/
├── api/
│   └── rag.ts                        # ★ 新增：RAG SSE API 封装
├── composables/
│   └── useChat.ts                    # ★ 新增：聊天核心逻辑
├── components/chat/
│   ├── ChatWidget.vue                # ★ 新增：聊天浮窗容器
│   ├── ChatMessage.vue               # ★ 新增：单条消息气泡
│   ├── ChatInput.vue                 # ★ 新增：输入框
│   ├── ChatStreamWriter.vue          # ★ 新增：流式输出 + Markdown 渲染
│   └── ChatSources.vue               # ★ 新增：引用来源展示
└── app/router/index.ts               # Phase 5 修改：添加 /dashboard/knowledge 路由
```

### 4.1.1 前端依赖新增

虽然 `marked` 和 `katex` 已经在 `package.json`，但 `dompurify` 还没有：

```bash
cd mysite-frontend
npm install dompurify
# dompurify 3.x 自带 TypeScript 类型声明，无需额外 @types
```

**为什么必须加 DOMPurify？** `ChatStreamWriter.vue` 用 `v-html` 渲染 LLM 输出，
如果不消毒，LLM 内容里的 HTML/Script 会作为代码执行（也可能通过 prompt injection
把用户输入回显成脚本）。`marked` 可以配置禁用 HTML，但最稳妥的方式是再用
DOMPurify 过一遍。

### 4.2 核心文件实现

#### `api/rag.ts` —— SSE EventSource 封装

```typescript
// 理解：SSE 不能通过普通的 fetch/axios 实现，
// 因为 fetch 需要等整个响应完成才返回，而 SSE 是持续推送的。
// JavaScript 提供了专门的 EventSource API 来接收 SSE 事件流。

export interface ChatStreamCallbacks {
  onToken: (token: string) => void;
  onSources: (sources: SourceChunk[]) => void;
  onMeta: (conversationId: number) => void;   // 新增：后端创建/确认会话 ID 后回传
  onDone: () => void;
  onError: (error: Error) => void;
}

export interface SourceChunk {
  title: string;
  content: string;
  score: number;
}

/** 匿名访客标识：localStorage 持久化，用于会话归属校验 */
const VISITOR_ID_KEY = 'rag-visitor-id';

export function getVisitorId(): string {
  let id = localStorage.getItem(VISITOR_ID_KEY);
  if (!id) {
    id = crypto.randomUUID ? crypto.randomUUID() : `${Date.now()}-${Math.random()}`;
    localStorage.setItem(VISITOR_ID_KEY, id);
  }
  return id;
}

export function createChatStream(
  question: string,
  conversationId: number | null,
  callbacks: ChatStreamCallbacks
): AbortController {
  const params = new URLSearchParams({ q: question });
  if (conversationId) params.set('conversationId', String(conversationId));
  params.set('visitorId', getVisitorId());    // 匿名会话归属（见 3.5 节决策 3）

  const url = `/v1/rag/chat/stream?${params.toString()}`;

  // 关键：使用 fetch + ReadableStream 而不是 EventSource
  // 原因：EventSource 不支持自定义请求头，且只支持 GET；fetch 更灵活。
  //
  // 注意：这里的 fetch 不会走 axios 拦截器，如果你以后要改成"必须登录"，
  // 需要手动把 token 加到 headers 里；目前后端是 permitAll，所以不带 token。
  const controller = new AbortController();

  fetch(url, {
    method: 'GET',
    headers: {
      'Accept': 'text/event-stream',
    },
    signal: controller.signal,
  })
    .then(async (response) => {
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}: ${response.statusText}`);
      }

      const reader = response.body!.getReader();
      const decoder = new TextDecoder();
      let buffer = '';

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        buffer += decoder.decode(value, { stream: true });
        const lines = buffer.split('\n');
        buffer = lines.pop() || '';  // 不完整的行放回 buffer

        for (const line of lines) {
          if (line.startsWith('data: ')) {
            const dataStr = line.substring(6).trim();

            try {
              const data = JSON.parse(dataStr);
              if (data.type === 'meta') {
                callbacks.onMeta(data.conversationId);
              } else if (data.type === 'content') {
                callbacks.onToken(data.delta);
              } else if (data.type === 'sources') {
                callbacks.onSources(data.sources);
              } else if (data.type === 'done') {
                callbacks.onDone();
                return;
              } else if (data.type === 'error') {
                callbacks.onError(new Error(data.message || 'AI 服务异常'));
                return;
              }
            } catch {
              // 解析失败，跳过（可能是心跳包或空行）
            }
          }
        }
      }

      // 流正常结束但后端没有发 done（防御性兜底）
      callbacks.onDone();
    })
    .catch((error) => {
      if (error.name !== 'AbortError') {
        callbacks.onError(error);
      }
    });

  return controller;
}
```

#### `useChat.ts` —— 核心逻辑 composable

```typescript
// composables/useChat.ts
// 理解：Vue 3 Composition API 的最佳实践是把状态逻辑抽成 composable。
// useChat 封装了：发送消息、接收流式回复、管理对话历史、
// 取消生成、自动滚动到底部。

import { ref, nextTick } from 'vue';
import { createChatStream, getVisitorId, type SourceChunk } from '@/api/rag';

export function useChat() {
  const messages = ref<ChatMessage[]>([]);
  const isStreaming = ref(false);
  const currentAssistantMessage = ref('');
  const currentSources = ref<SourceChunk[]>([]);
  const conversationId = ref<number | null>(null);   // 后端回传的会话 ID，多轮记忆关键
  let abortController: AbortController | null = null;

  async function sendMessage(question: string) {
    // 1. 添加用户消息
    messages.value.push({ role: 'user', content: question, sources: [] });

    // 2. 准备 assistant 消息占位
    isStreaming.value = true;
    currentAssistantMessage.value = '';
    currentSources.value = [];

    // 3. 发起 SSE 请求（带上当前 conversationId，后端会校验 visitorId 归属）
    abortController = createChatStream(question, conversationId.value, {
      onMeta(id: number) {
        conversationId.value = id;
      },
      onToken(token: string) {
        currentAssistantMessage.value += token;
      },
      onSources(sources: SourceChunk[]) {
        currentSources.value = sources;
      },
      onDone() {
        // 流式完成，把当前 assistant 消息固化到消息列表
        messages.value.push({
          role: 'assistant',
          content: currentAssistantMessage.value,
          sources: currentSources.value,
        });
        currentAssistantMessage.value = '';
        isStreaming.value = false;
      },
      onError(error: Error) {
        console.error('Chat stream error:', error);
        isStreaming.value = false;
        messages.value.push({
          role: 'assistant',
          content: '抱歉，请求出错了：' + error.message,
          sources: [],
        });
      },
    });
  }

  function cancelGeneration() {
    abortController?.abort();
    isStreaming.value = false;
    // 保留已生成的部分内容
    if (currentAssistantMessage.value) {
      messages.value.push({
        role: 'assistant',
        content: currentAssistantMessage.value + ' [已取消]',
        sources: currentSources.value,
      });
    }
    currentAssistantMessage.value = '';
  }

  function clearMessages() {
    messages.value = [];
    conversationId.value = null;
  }

  return {
    messages,
    isStreaming,
    currentAssistantMessage,
    currentSources,
    conversationId,
    sendMessage,
    cancelGeneration,
    clearMessages,
  };
}

interface ChatMessage {
  role: 'user' | 'assistant';
  content: string;
  sources: SourceChunk[];
}
```

#### `ChatWidget.vue` —— 浮窗容器

```vue
<!-- components/chat/ChatWidget.vue -->
<!--
  聊天浮窗组件 —— 博客右下角的 AI 助手入口。

  交互设计：
  - 默认显示一个圆形悬浮按钮（带有 AI 图标）
  - 点击按钮展开聊天面板（300px 宽，400px 高）
  - 面板内有消息列表 + 输入框
  - 点击面板外或关闭按钮收起面板
-->
<template>
  <div class="chat-widget">
    <!-- 悬浮按钮 -->
    <button
      v-if="!isOpen"
      @click="openChat"
      class="chat-fab"
      title="AI 助手"
    >
      <svg><!-- 聊天气泡图标 --></svg>
    </button>

    <!-- 聊天面板 -->
    <Transition name="chat-slide">
      <div v-if="isOpen" class="chat-panel">
        <!-- 头部 -->
        <div class="chat-header">
          <h3>AI 助手</h3>
          <button @click="closeChat">✕</button>
        </div>

        <!-- 消息列表 -->
        <div class="chat-messages" ref="messagesContainer">
          <ChatMessage
            v-for="(msg, idx) in chat.messages.value"
            :key="idx"
            :message="msg"
          />

          <!-- 流式输出中的消息（打字机效果） -->
          <ChatStreamWriter
            v-if="chat.isStreaming.value"
            :content="chat.currentAssistantMessage.value"
            :sources="chat.currentSources.value"
          />

          <!-- 空状态 -->
          <div v-if="chat.messages.value.length === 0 && !chat.isStreaming.value"
               class="chat-empty">
            <p>👋 你好！我是博客的 AI 助手。</p>
            <p>你可以问我关于博客文章的任何问题。</p>
            <div class="suggested-questions">
              <button @click="askQuestion(q)" v-for="q in suggestedQuestions" :key="q">
                {{ q }}
              </button>
            </div>
          </div>
        </div>

        <!-- 输入框 -->
        <ChatInput
          @send="chat.sendMessage"
          @cancel="chat.cancelGeneration()"
          :isStreaming="chat.isStreaming.value"
        />
      </div>
    </Transition>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, nextTick } from 'vue';
import { useChat } from '@/composables/useChat';
import ChatMessage from './ChatMessage.vue';
import ChatStreamWriter from './ChatStreamWriter.vue';
import ChatInput from './ChatInput.vue';

const chat = useChat();
const isOpen = ref(false);
const messagesContainer = ref<HTMLElement>();

const suggestedQuestions = [
  '博客里有哪些关于 Redis 的文章？',
  'Spring Security 怎么配置 JWT？',
  '介绍一下你的博客技术栈',
];

function openChat() { isOpen.value = true; }
function closeChat() { isOpen.value = false; }

function askQuestion(q: string) {
  chat.sendMessage(q);
}

// 自动滚动到底部
watch(
  () => [chat.messages.value.length, chat.currentAssistantMessage.value],
  async () => {
    await nextTick();
    if (messagesContainer.value) {
      messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight;
    }
  },
  { deep: true }
);
</script>
```

#### `ChatStreamWriter.vue` —— 流式渲染 + Markdown

```vue
<!-- components/chat/ChatStreamWriter.vue
     理解：流式渲染的核心难点 —— 不完整的 Markdown。

     LLM 生成的 token 是一个字一个字过来的，比如：
       Token 1: "##"
       Token 2: " Spring"
       Token 3: " Security"
       Token 4: " 配置\n\n"
       Token 5: "在 Spring"
       ...

     在 token 4 到达之前，"## Spring Security" 是不完整的 Markdown 标题，
     如果每来一个 token 就重新完整渲染，会频繁闪烁。
     解决方案：
       1. 累积 token（在当前组件中维护一个 buffer）
       2. 用 marked 渲染（不严格模式，容忍不完整的语法）
       3. 渲染结果实时更新（Vue 响应式自动 diff DOM）
-->
<template>
  <div class="chat-message assistant">
    <div class="message-content markdown-body" v-html="renderedContent"></div>
    <ChatSources v-if="sources.length > 0" :sources="sources" />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { marked } from 'marked';
import DOMPurify from 'dompurify';   // 新增：防止 LLM 输出中的 HTML/Script 注入
import ChatSources from './ChatSources.vue';
import type { SourceChunk } from '@/api/rag';

const props = defineProps<{
  content: string;
  sources: SourceChunk[];
}>();

const renderedContent = computed(() => {
  if (!props.content) return '<span class="cursor-blink">▊</span>';
  const html = marked.parse(props.content, { breaks: true }) as string;
  // 先用 marked 渲染 Markdown，再过 DOMPurify 消毒，最后加闪烁光标
  return DOMPurify.sanitize(html) + '<span class="cursor-blink">▊</span>';
});
</script>

<style scoped>
/* 闪烁光标 */
.cursor-blink {
  animation: blink 1s infinite;
}
@keyframes blink {
  0%, 50% { opacity: 1; }
  51%, 100% { opacity: 0; }
}
</style>
```

### 4.3 嵌入到布局中

**文件**：`app/layouts/DefaultLayout.vue`

```vue
<template>
  <AppHeader />
  <main>
    <router-view />
  </main>
  <AppFooter />
  <BackToTop />
  <!-- ★ 新增：全局聊天浮窗 -->
  <ChatWidget />
</template>

<script setup lang="ts">
import ChatWidget from '@/components/chat/ChatWidget.vue';
// ... 其他 imports
</script>
```

### 4.4 Phase 4 验证方式

```bash
# 前端开发服务器
cd mysite-frontend
npm run dev

# 打开浏览器 http://localhost:5173
# 1. 看到右下角 AI 助手悬浮按钮
# 2. 点击按钮 → 弹出聊天面板
# 3. 输入一个问题（如"JWT过滤器怎么配置？"）
# 4. 看到打字机效果的回答 + 引用来源
```

```bash
# 类型检查 + 构建
npx vue-tsc --noEmit --pretty   # 必须通过
npx vite build                   # 必须通过
```

### Phase 4 验收清单

- [ ] 博客页面右下角显示 AI 聊天浮窗按钮
- [ ] 点击展开聊天面板，动画流畅
- [ ] 发送消息后看到流式打字机效果
- [ ] Markdown 正确渲染（代码块、表格）
- [ ] `ChatStreamWriter` 使用 DOMPurify 消毒 LLM 输出（可通过构造 prompt 让 LLM 回显 `<script>` 标签测试）
- [ ] 引用来源正确展示（文章标题 + 相似度）
- [ ] 可以取消正在生成的回复
- [ ] 首条 SSE `meta` 事件返回 conversationId，后续轮次带上它
- [ ] 多轮对话中 AI 能基于上下文回答指代性问题（验证"记忆"真的生效）
- [ ] 浏览器 localStorage 中存在 `rag-visitor-id`
- [ ] `npx vue-tsc --noEmit` 通过
- [ ] `npx vite build` 通过

---

## Phase 5：知识库管理与部署

> **目标**：Dashboard 中添加知识库管理页面，生产环境部署。
> **预计时间**：3-5 天
> **验收标准**：管理员可以在 Dashboard 中管理知识库，生产环境 AI 聊天可用。

### 5.1 Dashboard 知识库管理

#### 后端 API

```java
// KnowledgeBaseController.java
@RestController
@RequestMapping("/v1/rag/knowledge-bases")
public class KnowledgeBaseController {

    // GET    /v1/rag/knowledge-bases             → 知识库列表
    // POST   /v1/rag/knowledge-bases             → 创建知识库 (DEVELOPER)
    // PUT    /v1/rag/knowledge-bases/{id}        → 更新知识库 (DEVELOPER)
    // DELETE /v1/rag/knowledge-bases/{id}        → 删除知识库 (DEVELOPER)
    // POST   /v1/rag/knowledge-bases/{id}/docs   → 上传文档
    // POST   /v1/rag/knowledge-bases/{id}/sync   → 批量同步已有文章
    // GET    /v1/rag/knowledge-bases/{id}/docs   → 文档列表
    // DELETE /v1/rag/knowledge-bases/{id}/docs/{docId} → 删除文档
}
```

#### 前端页面

```
mysite-frontend/src/views/dashboard/
└── KnowledgeManageView.vue     # ★ 新增：知识库管理页面
    ├── 知识库列表（名称、文档数、向量数、状态）
    ├── 创建/编辑知识库对话框
    ├── 文档列表（标题、来源、分块数、状态，含 FAILED 原因）
    ├── 上传 Markdown 文件         # Phase 5 先只做 Markdown
    └── 批量同步已有文章按钮

> PDF 文件上传支持放到后续扩展路线（7.1 节），Phase 5 先不做 ——
> PDF 解析需要额外依赖（如 Apache PDFBox / Tika）和版面分析，容易拖长交付周期。
```

#### 路由注册

```typescript
// app/router/index.ts
{
  path: '/dashboard/knowledge',
  name: 'KnowledgeManage',
  component: () => import('@/views/dashboard/KnowledgeManageView.vue'),
  meta: { requiresAuth: true, requiresDeveloper: true }
}
```

### 5.2 Nginx 配置调整（生产环境）

**文件**：`deploy/nginx/mysite.conf`

```nginx
# SSE 长连接支持 —— RAG 流式聊天需要
location /v1/rag/chat/stream {
    proxy_pass http://127.0.0.1:8081;
    proxy_http_version 1.1;
    proxy_buffering off;       # ★ 关键：关闭代理缓冲，token 才能实时到达浏览器
    proxy_cache off;           # ★ 不缓存 SSE 流
    gzip off;                  # ★ 关键：gzip 会缓冲响应，SSE 必须关闭
    proxy_read_timeout 120s;   # 120 秒长连接超时
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header Host $host;
    # 说明：不需要 proxy_set_header Connection ''（那是 WebSocket 升级用的）
    # 后端也可以加响应头 X-Accel-Buffering: no 双保险
}
```

> 注意：`X-Forwarded-For` 让后端能拿到真实客户端 IP 做限流（见 3.6 resolveClientIp）。
> 如果 Nginx 前面还有 CDN，需按实际层级取第 N 段。

### 5.3 部署脚本更新

**文件**：`deploy/deploy.sh`

```bash
# 新增：检查 PostgreSQL 是否运行
if ! docker ps | grep -q mysite-pgvector; then
    echo "Starting PostgreSQL..."
    cd docker && docker compose up -d postgres
fi

# 启动时执行数据库迁移（如果有新增表）
# docker exec mysite-pgvector psql -U ragent -d ragent -f /docker-entrypoint-initdb.d/01-ragent-schema.sql
```

### 5.4 环境变量配置

在服务器上创建 `.env` 文件：

```bash
# deploy/config/.env (不要提交到 git)
export BAILIAN_API_KEY="sk-xxxxxxxx"
# export SILICONFLOW_API_KEY="sk-xxxxxxxx"   # 可选
export RAGENT_PG_PASSWORD="your-secure-password"
```

### Phase 5 验收清单

- [ ] Dashboard 知识库管理页面可用
- [ ] 能上传 Markdown 文档并自动向量化（PDF 支持见后续扩展）
- [ ] 批量同步已有文章功能正常
- [ ] 摄取失败的文档在列表中显示 FAILED 状态和 fail_reason
- [ ] Nginx SSE 代理正常工作（生产环境流式聊天不卡顿）
- [ ] 部署脚本正确启动 PG 容器
- [ ] 环境变量正确注入
- [ ] 真实客户端 IP 能被后端识别（限流基于真实 IP，而不是 Nginx 容器 IP）

---

## 六、学习检查清单（面试可用）

在完成全部 Phase 之后，你应该能清晰回答以下问题：

### 基础概念
- [ ] **什么是 RAG？** 为什么需要 RAG 而不是直接让 LLM 回答？
- [ ] **什么是 Embedding？** 向量的维度（1024）代表什么？
- [ ] **什么是 Token？** Token 和字符/汉字的换算关系？
- [ ] **什么是 SSE？** 和 WebSocket 有什么区别？什么时候用哪个？

### 检索链路
- [ ] **为什么要分块？** 800 字符 + 100 overlap 是怎么定的？
- [ ] **为什么需要两阶段检索？** 向量检索（粗排）+ Rerank（精排）各自解决什么问题？
- [ ] **HNSW 索引原理？** 和暴力搜索（flat）比有什么优势？
- [ ] **Cosine 距离 vs 欧氏距离？** 为什么向量检索通常用 cosine？

### 模型层
- [ ] **OpenAI 兼容协议**是什么？为什么那么多国产模型都兼容？
- [ ] **三态断路器**（CLOSED/OPEN/HALF_OPEN）的设计动机？HALF_OPEN 为什么必要？
- [ ] **多供应商降级链**的设计逻辑？为什么百炼排第一，Ollama 排最后？
- [ ] **为什么 Embedding 不做多供应商降级？** 向量检索对模型/维度一致性的要求
- [ ] **流式降级边界**是什么？为什么已有 token 输出后就不能再降级到别的供应商？

### Prompt 与对话
- [ ] **Prompt 设计的核心原则？** 角色设定、知识边界、诚实原则、引用要求
- [ ] **对话记忆的滑动窗口**是什么？为什么是 6 轮而不是更多？
- [ ] **Token 爆炸**是什么？如何通过摘要压缩解决？

### 工程实践
- [ ] **双数据源**在 Spring Boot 中怎么配？`@MapperScan` 的两个参数？
- [ ] **Spring Event + @Async** 怎么实现业务解耦？
- [ ] **Spring Security + JWT** 怎么和 RAG 端点集成？

---

## 七、后续扩展路线

完成 Phase 0-5 后，你的博客已经具备基本的 AI 交互能力。以下是可以继续探索的方向：

### 7.1 近期（1-2 周可完成）

1. **对话摘要压缩** —— 开启 `rag.memory.summary-enabled=true`，当对话超过 10 轮时自动压缩早期对话
2. **查询重写** —— 对复杂问题先用 LLM 拆分成子问题再检索，提高召回率
3. **多知识库** —— 为"技术博客"和"读书笔记"建不同知识库，通过意图识别自动路由
4. **对话分享** —— 生成对话分享链接（类似 ChatGPT 的 Share 功能）
5. **PDF 文件上传与解析** —— 引入 Apache PDFBox / Apache Tika 或更先进的版面分析工具，
   支持上传 PDF 并提取文本向量化；在此之前 Dashboard 只支持 Markdown（Phase 5 已明确）

### 7.2 中期（1-3 个月）

5. **Elasticsearch 关键词检索** —— 开启 `elasticsearch.enabled=true`，添加 BM25 关键词通道，与向量检索做 RRF 融合
6. **RAG 质量评估** —— 建立评测集（100 个问题 + 标准答案），量化评估每次改动的效果
7. **用户反馈循环** —— 点赞/点踩 → 存储到数据库 → 定期分析错误案例 → 优化 Prompt 和分块策略
8. **Ollama 兜底** —— 如果服务器有 GPU 资源，启用本地 qwen3:8b 作为额外兜底层

### 7.3 长期（3 个月以上）

9. **MCP 工具集成** —— 引入 MCP 协议，让 AI 助手能调用外部工具（查天气、查 GitHub Star 数、发邮件等）
10. **意图树系统** —— 参考 Ragent 的意图识别设计，根据用户问题类型走不同的处理流程
11. **多模态支持** —— 让 AI 能理解博客中的图片（VLM 视觉语言模型）
12. **微调 Rerank 模型** —— 积累足够用户反馈数据后，微调 Rerank 模型提升检索精度

---

## 八、关键文件对照表

在实施过程中，这张表帮你快速定位需要修改/新建的文件：

### 新建文件（按 Phase 排列）（2026-07-17 修订：补全标记接口/事件模型/限流器/主数据源等）

| Phase | 文件 | 说明 |
|-------|------|------|
| P0 | `docker/init/ragent-schema.sql` | PG 建表脚本（含 visitor_id / fail_reason / 唯一约束） |
| P0 | `ragent/config/RagProperties.java` | @ConfigurationProperties 配置类（封装 rag.*） |
| P1 | `ragent/llm/CircuitBreaker.java` | 三态断路器 |
| P1 | `ragent/llm/ChatRequest.java` | 聊天请求 DTO（含 ChatMessage） |
| P1 | `ragent/llm/ChatEvent.java` | SSE 事件模型（meta/sources/content/error/done） |
| P1 | `ragent/llm/LLMService.java` | 统一聊天接口 |
| P1 | `ragent/llm/LLMProvider.java` | 供应商标记接口（防路由自注入） |
| P1 | `ragent/llm/EmbeddingService.java` | 统一嵌入接口（锁定百炼，不降级） |
| P1 | `ragent/llm/RerankService.java` | 重排序接口（百炼原生格式，见 3.2） |
| P1 | `ragent/llm/RoutingLLMService.java` | 模型路由器（含流式降级边界） |
| P1 | `ragent/llm/provider/AbstractOpenAIProvider.java` | OpenAI 兼容基类 |
| P1 | `ragent/llm/provider/BaiLianProvider.java` | 百炼 chat 实现 |
| P1 | `ragent/llm/provider/BaiLianEmbeddingService.java` | 百炼 embedding 实现（锁定单供应商） |
| P1 | `ragent/llm/provider/BaiLianRerankProvider.java` | 百炼 rerank 原生接口实现 |
| P1 | `ragent/llm/provider/SiliconFlowProvider.java` | SiliconFlow chat 实现（仅 chat，不接管 embedding） |
| P1 | `ragent/llm/provider/AIHubMixProvider.java` | AIHubMix chat 实现（仅 chat） |
| P1 | `ragent/llm/provider/OllamaProvider.java` | Ollama chat 实现（仅 chat） |
| P2 | `config/PrimaryDataSourceConfig.java` | 主数据源 @Primary |
| P2 | `ragent/config/RagentDataSourceConfig.java` | PG 数据源（DataSourceProperties 模式） |
| P2 | `ragent/config/RagAsyncConfig.java` | 异步线程池 |
| P2 | `ragent/vector/VectorStore.java` | 向量存储接口（search 含 kbId 参数） |
| P2 | `ragent/vector/PgvectorVectorStore.java` | pgvector 实现 |
| P2 | `ragent/ingestion/DocumentChunker.java` | 分块接口 |
| P2 | `ragent/ingestion/MarkdownChunker.java` | Markdown 分块 |
| P2 | `ragent/ingestion/ArticleEventListener.java` | 文章事件监听 |
| P2 | `ragent/dao/entity/*.java` | 6 个 RAG 实体类 |
| P2 | `ragent/dao/mapper/*.java` | 6 个 RAG Mapper 接口 |
| P3 | `ragent/core/retrieval/RetrievalEngine.java` | 检索引擎 |
| P3 | `ragent/core/memory/ConversationManager.java` | 对话记忆 |
| P3 | `ragent/core/rewrite/QueryRewriter.java` | 查询重写（可选） |
| P3 | `ragent/core/prompt/PromptTemplate.java` | Prompt 模板 |
| P3 | `ragent/service/RagChatService.java` | RAG 核心服务 |
| P3 | `ragent/service/ChatRateLimiter.java` | IP 限流 + 长度上限（见 3.7） |
| P3 | `ragent/service/KnowledgeBaseService.java` | 知识库服务 |
| P3 | `ragent/controller/RagChatController.java` | SSE 聊天端点 |
| P3 | `ragent/dto/*.java` | RAG DTO 类 |
| P4 | `api/rag.ts` | 前端 SSE API（visitorId / meta 事件） |
| P4 | `composables/useChat.ts` | 聊天逻辑 |
| P4 | `components/chat/ChatWidget.vue` | 聊天浮窗 |
| P4 | `components/chat/ChatMessage.vue` | 消息气泡 |
| P4 | `components/chat/ChatInput.vue` | 输入框 |
| P4 | `components/chat/ChatStreamWriter.vue` | 流式渲染 |
| P4 | `components/chat/ChatSources.vue` | 引用来源 |
| P5 | `ragent/controller/KnowledgeBaseController.java` | 知识库 CRUD |
| P5 | `ragent/controller/KnowledgeDocumentController.java` | 文档管理 |
| P5 | `views/dashboard/KnowledgeManageView.vue` | 管理页面 |

### 修改文件

| 文件 | Phase | 修改内容 |
|------|-------|---------|
| `pom.xml` | P0 | ✅ 已完成：Spring Boot 3.5.7、MP 3.5.14（boot3 starter + jsqlparser）、pgvector 0.1.6 |
| `docker/docker-compose.yml` | P0 | ✅ 已完成：postgres 服务（ollama 段注释预留） |
| `application.yaml` | P0 | 部分完成：rag.datasource 已配；llm providers / rate-limit 按 Step 0.4 修订 |
| `MysiteApplication.java` | P2 | `@MapperScan` 加 `sqlSessionFactoryRef = "sqlSessionFactory"` |
| `config/RagentDataSourceConfig.java` | P2 | 删除误建空壳（正确位置在 `ragent/config/`） |
| `config/DataBaseConfiguration.java` | P2 | 保留分页插件；注意 RAG 手工建 SqlSessionFactory 不会自动继承它 |
| `WebSecurityConfig.java` | P3 | `/v1/rag/chat/stream` 加入 permitAll |
| `ArticleServiceImpl.java` | P2 | 发布 Spring Event |
| `DefaultLayout.vue` | P4 | 嵌入 `<ChatWidget />` |
| `app/router/index.ts` | P5 | 添加 `/dashboard/knowledge` 路由 |
| `deploy/nginx/mysite.conf` | P5 | SSE 长连接配置 |
| `deploy/deploy.sh` | P5 | 启动 PG 检查 |

---

## 附录：学习资源推荐

### 阅读顺序（按优先级）

1. **OpenAI Chat Completions API 文档** — 理解 LLM API 的标准协议
   https://platform.openai.com/docs/api-reference/chat

2. **pgvector 官方文档** — 理解向量类型、HNSW 索引、相似度运算符
   https://github.com/pgvector/pgvector

3. **Spring SSE 文档** — 理解 SseEmitter 的生命周期
   https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-async.html

4. **Ragent 源码（精读以下文件）** — 理解企业级 RAG 的设计模式
   - `infra-ai/.../ModelHealthStore.java` — 断路器实现
   - `infra-ai/.../AbstractOpenAIStyleChatClient.java` — OpenAI 兼容层
   - `infra-ai/.../RoutingLLMService.java` — 模型路由
   - `bootstrap/.../retrieve/` — 多通道检索引擎
   - `bootstrap/.../pipeline/StreamChatPipeline.java` — 8 阶段管道

5. **LlamaIndex / LangChain 文档（对比学习）** — 理解框架设计思路
   https://docs.llamaindex.ai/
   重点看他们的 RAG 管道设计，对比你自己实现的有何不同。

### 面试准备建议

如果你在面试中说"我在个人博客上实现了一个完整的 RAG 系统"，面试官可能会问：

1. "你的 RAG 系统架构是什么样的？" → 回答 Phase 3 的完整链路图
2. "怎么保证检索质量的？" → 讲两阶段检索（向量 + Rerank）+ Prompt 设计
3. "怎么处理 LLM 调用失败？" → 讲三态断路器 + 多供应商降级
4. "为什么不用 LangChain？" → 讲"学习目的 + 博客规模不需要框架的复杂度"
5. "你做的和直接调 ChatGPT API 有什么区别？" → 讲 RAG 的核心价值：基于私有知识回答，而非 LLM 的通用知识
