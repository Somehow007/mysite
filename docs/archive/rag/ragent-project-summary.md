# Ragent AI 项目技术总结

> 生成日期：2026-07-12
> 用途：供 AI 阅读后结合目标网站项目生成集成方案

---

## 1. 项目概览

Ragent 是一个**企业级 Agentic RAG（检索增强生成）平台**，覆盖从文档入库到智能问答的完整链路。项目名源自 "RAG + Agent"，定位为"后端程序员转型 AI 工程师的第一站"。

- **GitHub**: https://github.com/nageoffer/ragent
- **许可证**: Apache 2.0
- **代码规模**: 后端 Java ~40,000 行（430 个文件），前端 TypeScript/React ~18,000 行
- **数据库表**: 20 张业务表
- **前端页面**: 22 个页面/组件

---

## 2. 模块架构

项目采用 **Maven 多模块 + 前后端分离** 架构，依赖方向：`bootstrap` → `infra-ai` → `framework`

### 2.1 模块一览

| 模块 | 路径 | 文件数 | 职责 |
|------|------|--------|------|
| **framework** | `framework/` | 37 个 Java 文件 | 共享基础设施，与业务无关的通用能力 |
| **infra-ai** | `infra-ai/` | 47 个 Java 文件 | AI 基础设施抽象层，屏蔽模型供应商差异 |
| **bootstrap** | `bootstrap/` | 430 个 Java 文件 | 主应用，包含全部业务逻辑 |
| **mcp-server** | `mcp-server/` | 5 个 Java 文件 | 独立部署的 MCP Server 进程 |
| **frontend** | `frontend/` | TypeScript/React | SPA 管理后台 + 用户聊天界面 |

### 2.2 framework 层 —— 基础设施

```
framework/
├── context/          # 用户上下文 (UserContext)、登录用户 (LoginUser)
├── convention/       # 统一类型：ChatMessage, ChatRequest, Result, RetrievedChunk
├── database/         # MyBatis-Plus 自动填充 (MyMetaObjectHandler)
├── distributedid/    # Snowflake 分布式 ID
├── errorcode/        # 错误码体系 (IErrorCode, BaseErrorCode)
├── exception/        # 三级异常体系 (ClientException, ServiceException, RemoteException)
├── idempotent/       # 双维度幂等 (IdempotentSubmit / IdempotentConsume)
├── mq/               # RocketMQ 生产者适配 + 事务消息
├── trace/            # 链路追踪上下文 (RagTraceContext, RagTraceNode)
└── web/              # 全局异常处理 (GlobalExceptionHandler)、SSE 发送器 (SseEmitterSender)
```

**核心特点**：
- 三级异常体系（Client/Service/Remote），统一拦截器处理
- 双维度幂等：接口幂等（@IdempotentSubmit 防重复提交）+ 消费幂等（@IdempotentConsume 防重复消费）
- Snowflake 分布式 ID，workId 自动初始化
- `SseEmitterSender` 线程安全的 SSE 发送封装
- `RagTraceContext` 基于 TTL（TransmittableThreadLocal）跨线程透传

### 2.3 infra-ai 层 —— AI 基础设施

```
infra-ai/
├── chat/             # LLM Chat 客户端 (ChatClient 接口 + 4 种实现)
│   ├── ChatClient.java              — 统一聊天接口
│   ├── AbstractOpenAIStyleChatClient.java  — OpenAI 兼容模板基类
│   ├── SiliconFlowChatClient.java   — 硅基流动
│   ├── OllamaChatClient.java        — 本地 Ollama
│   ├── BaiLianChatClient.java       — 阿里百炼 (DashScope)
│   ├── AIHubMixChatClient.java      — AIHubMix 中转
│   ├── RoutingLLMService.java       — 路由 + 健康感知分发
│   ├── LLMService.java              — 外观接口
│   ├── ProbeStreamBridge.java       — 首包探测桥接（模型切换透明）
│   └── StreamAsyncExecutor.java     — 流式异步执行
├── embedding/        # Embedding 客户端 (EmbeddingClient 接口 + 3 种实现)
│   ├── RoutingEmbeddingService.java — 路由分发
│   └── EmbeddingService.java        — 外观接口
├── rerank/           # Rerank 客户端 (RerankClient 接口 + 2 种实现)
│   ├── BaiLianRerankClient.java     — 阿里百炼 Rerank
│   ├── NoopRerankClient.java        — 无操作（降级）
│   ├── RoutingRerankService.java
│   └── RerankService.java
├── model/            # 模型路由核心
│   ├── ModelRoutingExecutor.java    — 带熔断的 Fallback 执行器
│   ├── ModelHealthStore.java        — 三态熔断器 (CLOSED/OPEN/HALF_OPEN)
│   ├── ModelSelector.java           — 按优先级排序候选
│   └── ModelTarget.java             — 目标模型 DTO
├── vlm/              # 视觉语言模型 (VLM) 用于图片理解
├── token/            # Token 计数服务
├── enums/            # ModelProvider, ModelCapability
└── http/             # HTTP 辅助工具
```

**核心特点**：
- 单一接口 + 多实现模式：`ChatClient`、`EmbeddingClient`、`RerankClient`
- `AbstractOpenAIStyleChatClient` 模板基类：实现 SSE 解析、请求构建，子类只需配置 URL/Header
- **三态熔断器** (`ModelHealthStore`)：CLOSED→OPEN→HALF_OPEN，每个模型独立健康状态，失败 N 次自动熔断，冷却后放行探测
- **首包探测** (`ProbeStreamBridge`)：缓冲初始 SSE 事件，模型切换时用户无感知
- **优先级降级链**：配置 `priority` 字段，优先使用健康的高优模型

### 2.4 bootstrap 层 —— 业务逻辑

```
bootstrap/
├── RagentApplication.java           # Spring Boot 入口 (端口 9090)
├── rag/                             # ★ RAG 核心（最大的包）
│   ├── controller/                  # REST 接口（10 个 Controller）
│   ├── service/
│   │   ├── pipeline/                # StreamChatPipeline（8 阶段管道）
│   │   ├── ratelimit/               # ChatQueueLimiter 分布式排队限流
│   │   ├── handler/                 # StreamTaskManager SSE 任务管理
│   │   └── impl/                    # 各类实现
│   ├── core/
│   │   ├── intent/                  # 意图识别（树形多级分类 + LLM 评分）
│   │   ├── rewrite/                 # 查询改写（同义词标准化 + LLM 改写拆分）
│   │   ├── retrieve/                # 检索引擎（多通道并行 + 后处理链）
│   │   ├── guidance/                # 歧义引导（低置信度时主动澄清）
│   │   ├── prompt/                  # Prompt 模板系统（StringTemplate 格式）
│   │   ├── mcp/                     # MCP 集成（客户端 + 工具注册表 + 参数提取）
│   │   ├── memory/                  # 对话记忆（滑动窗口 + 摘要压缩）
│   │   └── vector/                  # 向量存储抽象（Milvus/PgVector 双实现）
│   ├── trace/                       # 全链路追踪（AOP-based）
│   └── eval/                        # 评测框架
├── knowledge/                       # 知识库管理（CRUD、调度刷新、MQ 消费）
├── ingestion/                       # 文档摄入管道（DAG 引擎，6 节点）
│   ├── node/                        # Fetcher → Parser → Chunker → Enhancer → Enricher → Indexer
│   ├── engine/                      # IngestionEngine（条件执行 + 链式传递）
│   └── service/                     # Task/Pipeline 服务
├── core/                            # 文档解析 + 分块策略
├── user/                            # 用户认证（Sa-Token）
└── admin/                           # 管理后台仪表板
```

### 2.5 mcp-server 层 —— 独立 MCP Server

独立的 Spring Boot 应用，提供 MCP 工具能力，通过 MCP 协议被 bootstrap 调用：

```
mcp-server/
├── McpServerApplication.java
├── config/McpServerConfig.java
└── executor/
    ├── WeatherMcpExecutor.java      # 天气查询
    ├── TicketMcpExecutor.java       # 工单查询
    └── SalesMcpExecutor.java        # 销售数据查询
```

### 2.6 frontend 层 —— 前端

```
frontend/src/
├── pages/
│   ├── ChatPage.tsx                 # 核心聊天界面（SSE 流式渲染）
│   ├── LoginPage.tsx                # 登录
│   └── admin/
│       ├── dashboard/               # 仪表板（统计概览）
│       ├── knowledge/               # 知识库、文档、分块管理
│       ├── intent-tree/             # 意图树编辑（拖拽式）
│       ├── ingestion/               # 摄入管道监控
│       ├── traces/                  # 全链路追踪查看
│       ├── settings/                # 系统设置
│       ├── users/                   # 用户管理
│       ├── sample-questions/        # 示例问题管理
│       └── query-term-mapping/      # 查询词映射
├── components/
│   ├── chat/                        # 聊天组件（消息渲染、Markdown/代码/图片）
│   ├── admin/                       # 管理后台通用组件
│   ├── ui/                          # shadcn/ui 基础组件
│   └── layout/                      # 布局组件
├── services/                        # API 调用层（13 个 Service 模块）
├── stores/                          # Zustand 状态管理
└── hooks/                           # 自定义 Hooks
```

---

## 3. 具体技术栈（含版本号）

### 3.1 后端技术栈

| 类别 | 技术 | 版本 | 说明 |
|------|------|------|------|
| **运行环境** | Java | 17 | LTS 版本 |
| **基础框架** | Spring Boot | 3.5.7 | 最新稳定版 |
| **ORM** | MyBatis-Plus | 3.5.14 | Spring Boot 3 专用版 |
| **数据库** | PostgreSQL | — | 关系型数据 + pgvector 扩展 |
| **向量数据库** | Milvus | SDK 2.6.6 | 可选，通过 `rag.vector.type=milvus` 启用 |
| **向量扩展** | pgvector | 0.1.6 | 可选，通过 `rag.vector.type=pg` 启用 |
| **搜索引擎** | Elasticsearch | — | 可选，通过 `rag.keyword.type=es` 启用关键词检索 |
| **缓存** | Redis (Redisson) | 4.0.0 | 意图树缓存、信号量、分布式锁、队列 |
| **消息队列** | RocketMQ | Spring Boot Starter 2.3.5 | 文档分块、知识库清理、反馈收集 |
| **文档解析** | Apache Tika | 3.2.3 | PDF/Word/PPT/Markdown 等格式解析 |
| **Markdown 解析** | CommonMark | 0.22.0 | Markdown AST 结构化解析 |
| **SVG 处理** | Apache Batik | 1.18 | SVG 转码 |
| **对象存储** | S3 (AWS SDK) | 2.40.2 | RustFS/MinIO 兼容 |
| **PDF 解析** | MinerU SaaS API | v4 | 外部 PDF/Word/PPT 解析服务（包含表格、公式） |
| **认证** | Sa-Token | 1.43.0 | 登录认证 + 权限控制 |
| **线程池上下文** | TransmittableThreadLocal | 2.14.5 | 跨线程池透传用户/链路上下文 |
| **HTTP 客户端** | OkHttp | 4.12.0 | 模型 API 调用 |
| **工具库** | Hutool | 5.8.37 | Java 工具集 |
| **MCP SDK** | mcp + mcp-json-jackson2 | 1.1.2 | Model Context Protocol 官方 Java SDK |
| **SSE** | Spring SseEmitter | Spring 内置 | 流式推送 |
| **代码格式化** | Spotless Maven Plugin | 2.22.1 | License Header 检查 |
| **测试** | Mockito | 5.20.0 | 单元测试 Mock |
| **Maven** | Maven Wrapper | — | 构建工具 |

### 3.2 前端技术栈

| 类别 | 技术 | 版本 | 说明 |
|------|------|------|------|
| **核心框架** | React | 18.3.1 | UI 框架 |
| **类型** | TypeScript | 5.5.4 | 类型安全 |
| **构建工具** | Vite | 5.4.3 | 快速开发构建 |
| **路由** | React Router DOM | 6.26.2 | 前端路由 |
| **状态管理** | Zustand | 4.5.5 | 轻量级状态管理 |
| **样式** | Tailwind CSS | 3.4.10 | 原子化 CSS |
| **UI 组件库** | Radix UI | 多个包 | 无样式可访问组件原语 |
| **表单** | React Hook Form | 7.71.1 | 高性能表单 |
| **校验** | Zod | 4.3.6 | Schema 声明式校验 |
| **表格** | TanStack Table (React Table) | 8.21.3 | 高性能表格 |
| **图表** | Recharts | 3.7.0 | 数据可视化 |
| **Markdown 渲染** | react-markdown + remark-gfm | 9.0.1 | Markdown 内容渲染 |
| **代码高亮** | react-syntax-highlighter | 15.5.0 | 代码块语法高亮 |
| **虚拟滚动** | react-virtuoso | 4.9.2 | 大列表虚拟滚动 |
| **HTTP 客户端** | Axios | 1.7.5 | API 请求 |
| **文件上传** | react-dropzone | 14.3.8 | 拖拽上传 |
| **日期** | date-fns | 3.6.0 | 日期工具 |
| **Toast** | Sonner | 1.5.0 | 通知提示 |
| **图标** | Lucide React | 0.453.0 | 图标库 |
| **Lint** | ESLint | 8.57.0 | 代码检查 |
| **格式化** | Prettier | 3.3.3 | 代码格式化 |

### 3.3 基础设施

| 组件 | 用途 | 默认端口 |
|------|------|----------|
| PostgreSQL | 关系型数据 + 向量 (pgvector) | 5432 |
| Redis | 缓存、分布式锁、信号量、Pub/Sub | 6379 |
| Milvus | 向量检索引擎（可选） | 19530 |
| Elasticsearch | 关键词检索引擎（可选） | 9200 |
| RocketMQ | 异步任务消息队列 | 9876 |
| RustFS/MinIO | S3 对象存储 | 9000 |
| Ollama | 本地模型运行（可选） | 11434 |

---

## 4. 核心业务流程

### 4.1 RAG 对话管道（StreamChatPipeline）

一次完整的用户提问经过 **8 个阶段 + 3 个短路分支**：

```
用户问题
  │
  ├── Stage 1: loadMemory()         — 加载对话历史 + 摘要压缩
  ├── Stage 2: rewriteQuery()       — 同义词标准化 + LLM 改写拆分（可生成多个子问题）
  ├── Stage 3: resolveIntents()     — 树形意图分类（LLM 评分，每个子问题最多 3 个意图）
  │
  ├── [短路1] handleGuidance()      — 意图歧义时生成引导提示，让用户选择明确方向
  ├── [短路2] handleSystemOnly()    — 纯对话意图（打招呼等）直接系统回复
  │
  ├── Stage 6: retrieve()           — 多通道并行检索（KB 向量检索 + MCP 工具调用）
  │
  ├── [短路3] handleEmptyRetrieval()— 检索为空时返回友好提示
  │
  └── Stage 8: streamRagResponse()  — Prompt 组装 + LLM 流式生成 → SSE 推送给前端
```

### 4.2 意图识别系统

**树形三级分类**：DOMAIN（领域）→ CATEGORY（分类）→ TOPIC（主题）

核心特性：
- **三种意图类型**：`KB`（知识库检索）、`MCP`（工具调用）、`SYSTEM`（系统对话）
- **叶子节点分类**：只有叶子节点参与 LLM 分类，每个叶子节点绑定特定的 Collection/MCP Tool/自定义 Prompt
- **LLM 评分机制**：LLM 给所有叶子节点打分，过滤 `score < 0.35`，每子问题最多取 3 个
- **Redis 缓存**：意图树结构缓存 7 天，减少 DB 查询
- **歧义引导**：当 top-2 意图分数比 ≥ 0.8 时，生成引导选项让用户明确意图
- **自定义能力**：每个意图节点可配置专属 topK、Prompt 片段、完整 Prompt 模板、MCP 参数提取模板

### 4.3 多通道检索引擎

```
RetrievalEngine
  └── MultiChannelRetrievalEngine（并行执行 3 种 SearchChannel）
       ├── IntentDirectedSearchChannel     — 基于意图定向检索特定 Collection
       ├── VectorGlobalSearchChannel       — 全局跨 Collection 向量检索（低置信度补充）
       ├── KeywordSearchChannel            — ES 关键词检索（可选）
       └── 后处理链（顺序执行）
            ├── DeduplicationPostProcessor  — 按 ID 去重，保留最高分
            ├── FusionPostProcessor         — RRF（Reciprocal Rank Fusion）融合多路结果
            └── RerankPostProcessor         — Rerank 模型精排
```

配置关键参数：
- `defaultTopK`: 10（全局默认）
- `confidenceThreshold`: 0.6（低于此值触发全局检索兜底）
- `singleIntentSupplementThreshold`: 0.8（低于此值全局补充）
- `rerankCandidateLimit`: 50（RRF 融合后送入 Rerank 的候选上限）

### 4.4 模型路由与容错

- **多模型候选池**：配置多个模型（qwen-plus, qwen3-local, qwen3-max, glm-4.7, gpt-5.4），按 `priority` 优先级排序
- **三态熔断器**：`failureThreshold=2`，连续失败 2 次进入 OPEN（熔断），`openDurationMs=30000`（30 秒冷却），HALF_OPEN 放行一个探测请求
- **首包探测**：`ProbeStreamBridge` 缓冲初始事件，模型切换时客户端无感知
- **三种能力独立路由**：Chat、Embedding、Rerank 各有独立候选列表和路由

### 4.5 MCP 集成

```
MCP 意图 → LLM 参数提取（基于工具 JSON Schema）→ McpClientToolExecutor
  → McpSyncClient.callTool() → 远程 MCP Server → 格式化结果 → 注入 Prompt
```

- MCP 协议 1.1.2
- 远程 MCP Server 独立部署
- 参数提取通过 LLM 完成（非模板匹配）
- 支持自定义参数提取 Prompt 模板

### 4.6 对话记忆

- **滑动窗口**：`historyKeepTurns=4`，保留最近 4 轮对话
- **自动摘要压缩**：当消息数超过 5 轮时触发 LLM 摘要生成，摘要以 system message 注入
- **并行加载**：历史消息 + 历史摘要并行查询，减少延迟
- **会话自动命名**：LLM 生成会话标题（最长 30 字符）

### 4.7 文档摄入管道

可编排的 **6 节点 DAG 管道**：

```
Fetcher（获取文档）→ Parser（解析为文本）→ Chunker（分块）
  → Enhancer（LLM 增强，提取实体/关键词/摘要）
  → Enricher（补充元数据）
  → Indexer（向量化 + 写入向量库 + 关键词索引）
```

- 每个节点的配置存储在数据库，支持条件执行和输出链式传递
- 每个任务/节点有独立执行日志
- 通过 RocketMQ 消费文档处理任务（`KnowledgeDocumentChunkConsumer`）
- 支持定时刷新（`ScheduleRefreshProcessor`）

### 4.8 文档分块策略

- **策略模式**：`ChunkingStrategyFactory` 根据模式选择策略
- 多种模式：固定大小、文本边界（段落/句子）
- 块嵌入服务：`ChunkEmbeddingService` 批量向量化

### 4.9 分布式排队限流

**ChatQueueLimiter** — 基于 Redis 的分布式公平排队限流：

```
请求到达 → Redis ZSET 入队（按时间戳排序）
  → Lua 脚本原子判断：在队头 + 信号量有空 → 出队获得许可
  → 等待超时（maxWaitSeconds=15）自动踢出
  → Redis Pub/Sub 跨实例通知唤醒
  → SSE 推送排队状态给前端
```

并发控制：`maxConcurrent=10`，许可证 `leaseSeconds=30` 防死锁

### 4.10 全链路追踪

- **AOP 驱动**：`@RagTraceNode` 注解自动记录方法级的耗时、输入输出、异常
- **TTL 上下文透传**：`RagTraceContext` 通过 `TransmittableThreadLocal` 跨线程传播
- **数据库存储**：`t_rag_trace_run` + `t_rag_trace_node` 两张表
- **前端可视化**：管理后台可查看每次请求的完整调用链路

---

## 5. 设计思路

### 5.1 整体设计哲学

1. **面向接口编程**：所有扩展点都是接口 + Spring Bean 自动发现，新增功能只需加实现类
2. **分层解耦**：framework（通用能力）→ infra-ai（AI 抽象）→ bootstrap（业务编排），改模型不碰业务，改业务不动基础设施
3. **策略 > 配置**：检索通道、后处理器、模型路由都用策略模式，而非 if-else 或硬编码
4. **工程化优先**：不只让 RAG 跑通，更考虑限流、熔断、可观测性、上下文透传等生产级需求
5. **不依赖 LangChain/Spring AI**：自研轻量级抽象层，避免框架版本升级带来的破坏性变更

### 5.2 设计模式一览

| 设计模式 | 应用场景 | 解决的问题 |
|----------|----------|-----------|
| **策略模式** | SearchChannel、PostProcessor、McpToolExecutor | 检索通道/后处理器/工具可插拔替换 |
| **工厂模式** | IntentTreeFactory、ChunkingStrategyFactory | 复杂对象创建逻辑集中管理 |
| **注册表模式** | McpToolRegistry、IntentNodeRegistry | 组件自动发现和注册 |
| **模板方法** | IngestionNode 基类、AbstractOpenAIStyleChatClient | 统一执行流程，子类只关注核心逻辑 |
| **装饰器模式** | ProbeBufferingCallback | 不修改原有回调，增加首包探测能力 |
| **责任链模式** | 后处理器链、模型降级链 | 多个步骤顺序串联，灵活组合 |
| **外观模式** | LLMService、EmbeddingService | 隐藏底层客户端复杂性，提供统一入口 |
| **观察者模式** | StreamCallback | 流式事件的异步通知 |
| **AOP** | @RagTraceNode、@ChatRateLimit | 追踪/限流与业务代码解耦 |

### 5.3 扩展点设计

所有扩展点遵循统一约定：**实现接口 + 注册为 Spring Bean = 自动生效**，无需修改配置或框架代码。

| 扩展点 | 接口 | 说明 |
|--------|------|------|
| 新增检索通道 | `SearchChannel` | 实现 getName/isEnabled/search/getType |
| 新增后处理器 | `SearchResultPostProcessor` | 实现 process 方法，按 order 顺序执行 |
| 新增 MCP 工具 | `McpToolExecutor` | 实现 getToolDefinition/execute |
| 新增入库节点 | `IngestionNode` | 实现 getNodeType/execute |
| 新增模型供应商 | `ChatClient` / `EmbeddingClient` | 继承 AbstractOpenAIStyleChatClient |
| 新增意图分类器 | `IntentClassifier` | 替换默认 LLM 分类器 |
| 新增记忆存储 | `ConversationMemoryStore` | 替换 JDBC 实现 |

---

## 6. 核心亮点（简历可写）

### 6.1 业务价值亮点

1. **完整的 RAG 全链路闭环**：从文档上传解析→分块→向量化→意图识别→多路检索→重排序→流式生成→用户反馈，覆盖知识管理到智能问答的完整链路

2. **意图驱动的自适应检索**：构建树形三级意图识别体系（领域→分类→主题），自动将用户问题路由至对应知识库或业务工具，歧义时主动引导用户澄清，而非盲目检索

3. **多通道并行检索引擎**：向量检索 + 意图定向 + 关键词检索三通道并行，RRF 融合 + Rerank 精排，兼顾召回率与精准度；支持全局跨知识库检索兜底

4. **MCP 协议业务集成**：将外部业务系统（天气、工单、销售等）通过 MCP 协议封装为可调用的工具，LLM 自动参数提取与调用，实现检索与工具无缝融合

5. **深度思考模式**：支持用户可选的深度推理模式，使用支持 Thinking 的模型生成更高质量的回答

### 6.2 工程能力亮点

6. **多模型路由与三态熔断器**：独立实现 CLOSED→OPEN→HALF_OPEN 三态熔断机制，每个模型独立健康状态管理。失败达到阈值自动熔断，冷却后放行探测，配合优先级降级链，模型故障对用户透明

7. **首包探测与透明故障转移**：通过 `ProbeStreamBridge` 缓冲流式响应初始事件，模型切换时客户端无感知，不中断正在输出的内容

8. **分布式公平排队限流**：基于 Redis ZSET + Pub/Sub + Lua 脚本实现跨实例排队限流。请求按时间戳入队，队头才能获得许可，信号量防死锁，超时自动踢出，全流程 SSE 推送排队状态

9. **8 个专用线程池 + TTL 上下文透传**：按工作负载特征配置 8 个独立线程池（MCP、检索、意图分类、记忆摘要等），队列类型和拒绝策略各不相同。所有线程池用 `TtlExecutors` 包装，确保用户上下文和链路追踪信息跨线程不丢失

10. **AOP 全链路追踪系统**：基于 `@RagTraceNode` 注解 + TTL + 数据库存储的自研链路追踪，记录每个阶段耗时、输入输出、异常信息，提供完整的请求级可观测性

11. **可编排的文档摄入 DAG 管道**：6 节点管道（获取→解析→分块→增强→丰富→索引），节点配置存储在数据库，支持条件执行和输出链式传递。通过 RocketMQ 异步消费处理任务

12. **架构扩展性设计**：7 个主要扩展点全部基于接口 + Spring Bean 自动发现，新增检索通道/MCP 工具/后处理器等只需加实现类，零配置

### 6.3 代码质量问题

13. **企业级代码规模与规范**：后端 430 个 Java 文件 ~40,000 行，前端 ~18,000 行 TypeScript。Maven 多模块分层架构，framework/infra-ai/bootstrap 三层职责清晰。20 张业务表覆盖会话、消息、知识库、文档、分块、意图树、入库管道、链路追踪、用户等完整业务域

14. **对话记忆管理**：滑动窗口 + 自动摘要压缩机制。短对话保留全量历史，长对话自动触发 LLM 摘要生成，标题自动命名，避免 Token 爆炸

15. **安全的 SSE 流式推送**：流式输出全程 SSE 推送，支持客户端取消订阅（`StreamCancellationHandle`），MCP 工具调用场景自动调整温度参数（0.3）以减少幻觉

---

## 7. API 端点概览

| Controller | 端点前缀 | 主要功能 |
|-----------|----------|---------|
| `RAGChatController` | `/chat` | SSE 流式对话（核心入口） |
| `ConversationController` | `/conversation` | 会话 CRUD |
| `MessageFeedbackController` | `/feedback` | 回答点赞/点踩 |
| `KnowledgeBaseController` | `/kb` | 知识库 CRUD |
| `KnowledgeDocumentController` | `/kb/docs` | 文档管理 |
| `KnowledgeChunkController` | `/kb/chunks` | 分块查看 |
| `IntentTreeController` | `/intent` | 意图树管理 |
| `IngestionTaskController` | `/ingestion` | 摄入任务管理 |
| `RagTraceController` | `/trace` | 链路追踪查询 |
| `RAGSettingsController` | `/settings` | 系统设置 |
| `SampleQuestionController` | `/sample-questions` | 示例问题管理 |
| `QueryTermMappingController` | `/term-mapping` | 同义词映射管理 |
| `DashboardController` | `/dashboard` | 仪表板统计 |
| `AuthController` | `/auth` | 登录认证 |

---

## 8. 关键配置项

项目主配置：`bootstrap/src/main/resources/application.yaml`

核心可配置维度：
- **向量存储**：`rag.vector.type=pg|milvus`
- **关键词检索**：`rag.keyword.type=none|es`
- **检索通道开关**：每个通道独立 `enabled` + 阈值调节
- **模型候选池**：`ai.chat.candidates` / `ai.embedding.candidates` / `ai.rerank.candidates` 各自配置
- **熔断参数**：`ai.selection.failureThreshold=2, openDurationMs=30000`
- **限流参数**：`rag.rateLimit.global.*`（并发数、等待时间、轮询间隔）
- **记忆参数**：`rag.memory.*`（保留轮次、摘要阈值）
- **MCP Server**：`rag.mcp.servers[].name + url`
- **并发信号量**：`rag.semaphore.documentUpload.*`（上传并发控制）

---

## 9. 开发与运行流程

### 9.1 本地启动

```bash
# 1. 启动基础设施
docker compose -f resources/docker/lightweight/milvus-stack-2.6.6.compose.yaml up -d  # Milvus
docker compose -f resources/docker/rocketmq-stack-5.2.0.compose.yaml up -d             # RocketMQ

# 2. 确保 PostgreSQL (pgvector)、Redis、RustFS/MinIO 已运行

# 3. 编译
./mvnw compile

# 4. 启动后端
./mvnw spring-boot:run -pl bootstrap     # 主应用，端口 9090

# 5. 启动 MCP Server（可选）
./mvnw spring-boot:run -pl mcp-server    # MCP Server，独立进程

# 6. 启动前端
cd frontend && npm run dev
```

### 9.2 验证命令

```bash
# 后端
./mvnw compile    # 编译检查
./mvnw test       # 运行测试

# 前端
cd frontend
npm run lint      # ESLint
npm run build     # 生产构建
```

---

## 10. 集成到其他网站的入口点

如果要将 Ragent 的 RAG + AI 交互功能集成到另一个网站项目中，以下是最关键的接口和集成方式：

### 10.1 后端集成关键接口

1. **SSE 流式对话** — `RAGChatController` 的聊天端点，通过 `SSE` 推送流式回复，这是集成的核心
2. **会话管理** — `ConversationController` 的 CRUD 端点，管理多轮对话上下文
3. **知识库管理** — `KnowledgeBaseController` + `KnowledgeDocumentController`，管理知识库内容的 CRUD
4. **意图树管理** — `IntentTreeController`，配置意图分类体系
5. **检索 API** — `RetrievalEngine.retrieve()` 可直接调用，无需走完整管道

### 10.2 前端集成关键组件

1. **聊天组件** (`frontend/src/components/chat/`) — 支持 SSE 流式接收、Markdown 渲染、代码高亮、图片展示
2. **聊天 Service** (`frontend/src/services/chatService.ts`) — SSE EventSource 封装
3. **认证 Service** (`frontend/src/services/authService.ts`) — 登录认证集成
4. **Zustand Store** (`frontend/src/stores/`) — 会话状态、用户状态管理

### 10.3 集成策略建议

| 集成深度 | 方式 | 工作量 | 说明 |
|----------|------|--------|------|
| **浅层集成** | 单独部署 Ragent，网站通过 iframe/链接跳转 | 低 | 两个系统独立运行 |
| **中度集成** | 网站后端通过 HTTP 调用 Ragent 的 REST API | 中 | 共享用户认证，Ragent 作为 RAG 中台 |
| **深度集成** | 将 bootstrap 模块作为 Maven 依赖引入，直接调用 `StreamChatPipeline` | 高 | 完全嵌入，共享数据库和基础设施 |
| **核心组件提取** | 提取 `infra-ai` + `framework` + 核心 `core` 包到网站项目中 | 高 | 最灵活，需要处理依赖冲突 |

---

## 11. 项目目录完整结构

```
ragent/
├── pom.xml                           # 根 POM（多模块管理）
├── lombok.config                     # Lombok 配置
├── mvnw / mvnw.cmd                   # Maven Wrapper
├── README.md                         # 项目 README
├── CLAUDE.md                         # Claude Code 配置
├── LICENSE                           # Apache 2.0
├── assets/                           # 文档用图片
├── docs/                             # 架构文档
│   ├── ragent-architecture.md        # ★ 核心架构文档
│   ├── multi-channel-retrieval.md    # 检索架构
│   ├── quick-start.md                # 快速启动
│   ├── refactoring-summary.md        # 重构记录
│   └── releases/                     # 版本发布说明
├── resources/
│   ├── docker/                       # Docker Compose 配置
│   │   ├── lightweight/              # Milvus 轻量部署
│   │   └── rocketmq-stack-5.2.0.compose.yaml
│   └── format/                       # Spotless license header
├── scripts/                          # 脚本工具
├── framework/                        # Module: 基础设施
├── infra-ai/                         # Module: AI 抽象层
├── mcp-server/                       # Module: MCP Server
├── bootstrap/                        # Module: 主应用
│   └── src/main/resources/
│       ├── application.yaml          # 主配置
│       └── prompt/*.st               # Prompt 模板（StringTemplate 格式）
└── frontend/                         # React SPA
```
