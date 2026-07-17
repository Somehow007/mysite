# MySite + Ragent 集成架构设计文档

## 一、现状分析与核心差距

### 1.1 两系统对比

| 维度 | MySite (当前) | Ragent (目标) | 差距 |
|------|--------------|---------------|------|
| Spring Boot | 3.0.7 | 3.5.7 | 需升级 |
| 数据库 | MySQL 8.4 | PostgreSQL + pgvector | 需引入新 DB |
| ORM | MyBatis-Plus 3.5.7 | 未指定 | 统一用 MyBatis-Plus |
| 认证 | JWT + Spring Security | Sa-Token | 统一用现有 JWT 方案 |
| 缓存 | Redis (Lettuce) | Redis (Redisson + Sa-Token) | 现有 Redis 即可复用 |
| 搜索 | ES (可选，当前关闭) | ES (BM25 关键词) | 已有基础，需开启 |
| 消息队列 | 无 | RocketMQ | 个人博客不需要，用 @Async 替代 |
| 对象存储 | 本地文件系统 | MinIO / S3 | 博客规模本地文件系统即可 |
| 前端 | Vue 3 + TypeScript | React | 用 Vue 3 构建聊天组件 |
| AI 模型 | 无 | 百炼 / SiliconFlow / AIHubMix / Ollama | 引入多供应商路由 + 外部 API 优先 |
| 文档解析 | 无 | Tika + MinerU | 引入 Tika（Markdown 为主） |
| 向量存储 | 无 | pgvector / Milvus | 引入 pgvector |

### 1.2 关键决策原则

本集成方案遵循以下原则：

1. **最小侵入**：不破坏 MySite 现有功能，RAG 作为新增能力叠加
2. **渐进式复杂度**：先上最简可用版本，后续按需扩展
3. **外部 API 优先**：优先使用云端模型 API（成本低、质量高、零运维），本地 Ollama 仅作最低优先级兜底
4. **博客规模**：不为企业级场景过度设计，基础设施做减法

---

## 二、整体架构设计

### 2.1 集成后的系统架构

```
┌─────────────────────────────────────────────────────────┐
│                    Nginx (443/80/8080)                   │
├─────────────────────────────────────────────────────────┤
│              Vue 3 Frontend (mysite-frontend)            │
│  ┌──────────────┐  ┌──────────────┐  ┌───────────────┐ │
│  │  现有博客页面  │  │  AI 聊天浮窗  │  │  Dashboard    │ │
│  │  (Blog/Post/  │  │  (ChatWidget │  │  (知识库管理)  │ │
│  │   Category..) │  │   .vue)      │  │               │ │
│  └──────────────┘  └──────────────┘  └───────────────┘ │
├─────────────────────────────────────────────────────────┤
│              Spring Boot (mysite, port 8081)             │
│                                                         │
│  ┌────────────────────┐  ┌───────────────────────────┐ │
│  │  现有 Blog 模块     │  │  新增 RAG 模块             │ │
│  │  ┌──────────────┐  │  │  ┌─────────────────────┐  │ │
│  │  │ Controller   │  │  │  │ RAG Controller      │  │ │
│  │  │ /v1/*        │  │  │  │ /v1/rag/*           │  │ │
│  │  ├──────────────┤  │  │  ├─────────────────────┤  │ │
│  │  │ Service      │  │  │  │ RAG Service         │  │ │
│  │  │ (Article,    │  │  │  │ (Chat, Retrieval,   │  │ │
│  │  │  Category..) │  │  │  │  KnowledgeBase)     │  │ │
│  │  ├──────────────┤  │  │  ├─────────────────────┤  │ │
│  │  │ DAO/Mapper   │  │  │  │ Core Engine         │  │ │
│  │  │ (MySQL +     │  │  │  │ (Retrieval, Prompt, │  │ │
│  │  │  MyBatis-    │  │  │  │  Memory, Rewrite)   │  │ │
│  │  │  Plus)       │  │  │  ├─────────────────────┤  │ │
│  │  └──────────────┘  │  │  │ DAO/Mapper          │  │ │
│  │                    │  │  │ (MySQL blog tables   │  │ │
│  │                    │  │  │  + pgvector tables)  │  │ │
│  └────────────────────┘  │  └─────────────────────┘  │ │
│                          └───────────────────────────┘ │
│  ┌──────────────────────────────────────────────────┐  │
│  │          AI Infrastructure Layer                  │  │
│  │  ┌──────────── Model Router ────────────┐        │  │
│  │  │  Priority 1: 百炼 (qwen3-max)        │        │  │
│  │  │  Priority 2: SiliconFlow (GLM-4.7)   │        │  │
│  │  │  Priority 3: AIHubMix (gpt-5.4)      │        │  │
│  │  │  Priority 9: Ollama local (兜底)     │        │  │
│  │  └──────────────────────────────────────┘        │  │
│  │  LLM Client │ Embedding │ Rerank │ CircuitBreaker │  │
│  └──────────────────────────────────────────────────┘  │
├─────────────────────────────────────────────────────────┤
│                    Data Layer                           │
│  ┌──────────┐  ┌──────────┐  ┌────────────────────┐   │
│  │ MySQL 8.4│  │ Redis 7  │  │ PostgreSQL 17      │   │
│  │ (博客数据)│  │ (缓存/   │  │ + pgvector 0.7     │   │
│  │          │  │  会话)   │  │ (向量存储)          │   │
│  └──────────┘  └──────────┘  └────────────────────┘   │
│  ┌──────────────────────────────────────────────────┐  │
│  │        Ollama (可选，仅兜底，localhost:11434)      │  │
│  │        qwen3:8b / qwen3-embedding               │  │
│  └──────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────┘
```

### 2.2 模块划分

集成在现有单模块 Maven 项目中，通过**包级别**隔离 RAG 功能，不引入 Maven 多模块：

```
src/main/java/io/github/somehow/mysite/
├── config/              # 现有 Spring 配置 (不变)
├── controller/          # 现有 REST 控制器 (不变)
├── service/             # 现有 Blog 服务 (不变)
├── dao/                 # 现有 Blog 数据层 (不变)
├── dto/                 # 现有 Blog DTO (不变)
├── security/            # 现有 JWT 安全 (扩展：RAG 端点加入白名单)
├── commons/             # 现有通用组件 (不变)
├── elasticsearch/       # 现有 ES 集成 (不变)
│
├── ragent/              # ★ 新增：RAG 模块
│   ├── config/          # RAG 专用配置 (pgvector数据源、Ollama连接、线程池)
│   ├── controller/      # RAG REST 控制器
│   ├── service/         # RAG 业务服务
│   ├── dao/
│   │   ├── entity/      # RAG 实体 (KnowledgeBase, Document, Chunk等)
│   │   └── mapper/      # RAG MyBatis Mapper
│   ├── dto/             # RAG 请求/响应 DTO
│   ├── core/            # RAG 核心引擎
│   │   ├── retrieval/   # 检索通道 (向量检索 + 关键词)
│   │   ├── rewrite/     # 查询重写
│   │   ├── memory/      # 对话记忆
│   │   └── prompt/      # 提示模板
│   ├── llm/             # LLM 抽象层 (多供应商 + 路由)
│   │   ├── LLMService.java              # 统一 LLM 接口
│   │   ├── EmbeddingService.java        # 嵌入服务接口
│   │   ├── RerankService.java           # 重排序服务接口
│   │   ├── RoutingLLMService.java       # 模型路由器 (优先级 + 断路器)
│   │   ├── provider/                    # 各供应商实现 (均为 OpenAI 兼容)
│   │   │   ├── BaiLianLLMProvider.java       # 阿里百炼 (qwen3-max)
│   │   │   ├── SiliconFlowLLMProvider.java   # SiliconFlow (GLM-4.7)
│   │   │   ├── AIHubMixLLMProvider.java      # AIHubMix (gpt-5.4)
│   │   │   └── OllamaLLMProvider.java        # 本地 Ollama (最低优先级兜底)
│   │   └── CircuitBreaker.java          # 三态断路器 (CLOSED/OPEN/HALF_OPEN)
│   ├── ingestion/       # 文档摄取
│   │   ├── parser/      # 解析器 (Markdown, Tika)
│   │   └── chunker/     # 分块策略
│   └── vector/          # 向量存储抽象
│       ├── VectorStore.java          # 向量存储接口
│       └── PgvectorVectorStore.java  # pgvector 实现
```

### 2.3 前端组件结构

```
mysite-frontend/src/
├── components/
│   └── chat/                       # ★ 新增：AI 聊天组件
│       ├── ChatWidget.vue          # 聊天浮窗容器 (浮动按钮 + 展开面板)
│       ├── ChatMessage.vue         # 单条消息气泡
│       ├── ChatInput.vue           # 输入框 + 发送按钮
│       ├── ChatStreamWriter.vue    # 流式输出动画
│       └── ChatSources.vue         # 引用来源展示
├── composables/
│   └── useChat.ts                  # ★ 新增：聊天 SSE 逻辑
├── api/
│   └── rag.ts                      # ★ 新增：RAG API 调用
├── views/
│   └── dashboard/
│       └── KnowledgeBaseManage.vue # ★ 新增：知识库管理页面 (Dashboard)
├── stores/
│   └── chat.ts                     # ★ 新增：聊天状态 Pinia Store
└── app/router/index.ts             # 修改：添加 /dashboard/knowledge 路由
```

---

## 三、核心技术方案

### 3.1 Spring Boot 升级：3.0.7 → 3.5.7

**风险评估**：低。Spring Boot 3.x 系列之间是兼容升级。

**需关注的变化**：
- `spring.data.redis` → `spring.data.redis` (3.x 已稳定)
- `spring.elasticsearch` 客户端可能需要在 3.5.x 重新验证兼容性
- MyBatis-Plus 3.5.7 已支持 Spring Boot 3.5.x (验证: 3.5.7 发布于 2025 年)
- `springdoc-openapi` / Knife4j 需要升级到支持 3.5.x 的版本
- `jakarta.servlet` 包路径在 3.0.x 已迁移，3.5.x 无变化

**升级步骤** (在实现阶段执行)：
1. 修改 `pom.xml` 中 `spring-boot-starter-parent` 版本为 `3.5.7`
2. 验证 `mvn compile` 通过
3. 验证 `mvn test` 通过
4. 如有编译问题，逐依赖调整版本

### 3.2 双数据源设计

**策略**：MySQL 主数据源不变，新增 PostgreSQL 作为第二数据源专用于向量存储。

```
┌────────────────────────────────────┐
│         MySite Application         │
├────────────────────────────────────┤
│  Primary DataSource    │ Secondary DataSource
│  (MySQL, @Primary)     │ (PostgreSQL, @Qualifier)
├────────────────────────┼───────────
│  t_user                │ t_knowledge_base
│  t_article             │ t_knowledge_document
│  t_category            │ t_knowledge_chunk
│  t_tag                 │ t_knowledge_vector  (pgvector)
│  t_comment             │ t_conversation
│  t_collection          │ t_conversation_message
│  ...                   │
├────────────────────────┼───────────
│  MyBatis-Plus          │ MyBatis-Plus
│  (basePackages 指向     │ (basePackages 指向
│   blog entity/mapper)   │  ragent entity/mapper)
└────────────────────────┴───────────
```

**关键配置**：

```yaml
spring:
  # 主数据源：MySQL (不变)
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://...

# 第二数据源：PostgreSQL + pgvector
rag:
  datasource:
    driver-class-name: org.postgresql.Driver
    url: jdbc:postgresql://localhost:5432/ragent
    username: ragent
    password: ${RAG_PG_PASSWORD}
```

**RAG 专用 MyBatis-Plus 配置** (`RagentDataSourceConfig.java`)：

```java
@Configuration
@MapperScan(
    basePackages = "io.github.somehow.mysite.ragent.dao.mapper",
    sqlSessionFactoryRef = "ragentSqlSessionFactory"
)
public class RagentDataSourceConfig {
    @Bean
    @ConfigurationProperties("rag.datasource")
    public DataSource ragentDataSource() { ... }

    @Bean
    public SqlSessionFactory ragentSqlSessionFactory() { ... }
}
```

### 3.3 向量存储：pgvector

**为什么选 pgvector 而不是 Milvus**：

| 因素 | pgvector | Milvus |
|------|----------|--------|
| 运维复杂度 | 低（一个 PG 容器） | 高（独立 Milvus + etcd + MinIO） |
| 博客规模适配 | 完美（万级文档） | 过度（百万级文档才需要） |
| 与现有生态契合 | SQL 标准，MyBatis-Plus 直接操作 | 需要独立 SDK |
| 资源占用 | 共享 PG 实例 | 额外 2-4GB 内存 |

**pgvector 表设计**：

```sql
-- 安装 pgvector 扩展
CREATE EXTENSION IF NOT EXISTS vector;

-- 知识库
CREATE TABLE t_knowledge_base (
    id BIGINT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    collection_name VARCHAR(100) NOT NULL UNIQUE,  -- 向量隔离维度
    embedding_model VARCHAR(100) DEFAULT 'qwen3-embedding',
    embedding_dimension INT DEFAULT 1024,
    chunk_size INT DEFAULT 800,
    chunk_overlap INT DEFAULT 100,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 文档
CREATE TABLE t_knowledge_document (
    id BIGINT PRIMARY KEY,
    kb_id BIGINT NOT NULL REFERENCES t_knowledge_base(id),
    title VARCHAR(500) NOT NULL,
    source_type VARCHAR(20) NOT NULL,     -- UPLOAD, ARTICLE, URL
    source_ref VARCHAR(500),              -- 关联文章ID 或 URL
    file_type VARCHAR(20),                -- MD, PDF, HTML
    status VARCHAR(20) DEFAULT 'PENDING', -- PENDING, CHUNKING, EMBEDDING, READY, FAILED
    chunk_count INT DEFAULT 0,
    char_count INT DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 分块
CREATE TABLE t_knowledge_chunk (
    id BIGINT PRIMARY KEY,
    doc_id BIGINT NOT NULL REFERENCES t_knowledge_document(id),
    kb_id BIGINT NOT NULL REFERENCES t_knowledge_base(id),
    chunk_index INT NOT NULL,
    content TEXT NOT NULL,
    char_count INT DEFAULT 0,
    vector_id BIGINT,                      -- 关联的向量记录ID
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 向量 (pgvector)
CREATE TABLE t_knowledge_vector (
    id BIGINT PRIMARY KEY,
    chunk_id BIGINT NOT NULL REFERENCES t_knowledge_chunk(id),
    kb_id BIGINT NOT NULL REFERENCES t_knowledge_base(id),
    embedding vector(1024),                -- pgvector 类型
    model VARCHAR(100),
    -- HNSW 索引 (建表后)
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- HNSW 索引 (cosine 距离)
CREATE INDEX idx_vector_embedding ON t_knowledge_vector
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);
```

### 3.4 AI 模型层设计

#### 3.4.1 多供应商优先级策略

外部 API 优先，本地 Ollama 兜底。优先级从高到低：

| 优先级 | 供应商 | Chat 模型 | Embedding 模型 | 费用 | 说明 |
|--------|--------|-----------|---------------|------|------|
| 1 (最高) | 阿里百炼 | `qwen3-max` | `text-embedding-v4` | 按量付费 | 国内首选，中文能力强，延迟低 |
| 2 | SiliconFlow | `GLM-4.7` | `BAAI/bge-large-zh-v1.5` | 按量付费 | 备选，模型选择丰富 |
| 3 | AIHubMix | `gpt-5.4` | `text-embedding-3-small` | 按量付费 | 国际模型代理 |
| 9 (最低) | 本地 Ollama | `qwen3:8b` | `qwen3-embedding` | 免费 | 纯兜底，所有外部 API 不可用时启用 |

**优先级设计逻辑**：
- 百炼排第一：国内访问延迟最低（~200ms），qwen3-max 中文能力最强，有免费额度
- SiliconFlow 排第二：作为独立的第二供应商，避免单点故障
- AIHubMix 排第三：提供 OpenAI 等国际模型访问
- Ollama 排最后：需要 GPU/大内存服务器，质量不如云端大模型，仅作为极端情况兜底

#### 3.4.2 模型路由与断路器

从 Ragent 原设计中保留**多供应商路由 + 三态断路器**机制（这是 Ragent 最精华的设计之一，在小规模场景同样有价值）：

```
请求 → RoutingLLMService
         │
         │  按优先级排序候选模型列表
         │
         ▼
  ┌─ 遍历候选 ──────────────────────────┐
  │                                      │
  │  1. 检查断路器状态                    │
  │     CLOSED   → 正常调用              │
  │     OPEN     → 跳过（冷却中）         │
  │     HALF_OPEN → 探测一次             │
  │                                      │
  │  2. 调用模型 + 首包探测               │
  │     WebClient 异步调用 OpenAI 兼容 API│
  │     60s 内未收到首包 → 标记失败        │
  │                                      │
  │  3. 成功 → 返回结果                  │
  │     失败 → 标记失败，尝试下一个        │
  │                                      │
  └──────────────────────────────────────┘
  所有候选失败 → 返回错误给客户端
```

**断路器参数**：
- 连续 2 次失败 → OPEN（熔断）
- 冷却 30s → HALF_OPEN（试探）
- 试探成功 → CLOSED（恢复）
- 试探失败 → OPEN（重新冷却）

**首包探测**：发送请求后等待第一个 SSE 数据包，60s 内无数据则判定该供应商不可用。探测期间内容缓冲，成功后从缓冲恢复，用户无感知。

#### 3.4.3 统一抽象层设计

所有供应商 API 均兼容 OpenAI 格式，因此可以抽象为统一接口：

```java
// 统一 LLM 接口
public interface LLMService {
    Flux<String> chatStream(ChatRequest request);
    String chat(ChatRequest request);
}

// 抽象基类：所有 OpenAI 兼容供应商的公共逻辑
public abstract class AbstractOpenAILLMProvider implements LLMService {
    protected final WebClient webClient;
    protected final String apiKey;
    protected final String model;
    // POST {baseUrl}/chat/completions  (OpenAI 兼容格式)
    // 返回 Flux<String> (SSE 流式解析)
}

// 各供应商只需提供 baseUrl + apiKey + model
public class BaiLianLLMProvider extends AbstractOpenAILLMProvider {
    // baseUrl: https://dashscope.aliyuncs.com/compatible-mode/v1
}
public class SiliconFlowLLMProvider extends AbstractOpenAILLMProvider {
    // baseUrl: https://api.siliconflow.cn/v1
}
public class AIHubMixLLMProvider extends AbstractOpenAILLMProvider {
    // baseUrl: https://aihubmix.com/v1
}
public class OllamaLLMProvider extends AbstractOpenAILLMProvider {
    // baseUrl: http://localhost:11434/v1  (不需要 apiKey)
}

// 路由器：封装优先级排序 + 断路器 + 首包探测
public class RoutingLLMService implements LLMService {
    private final List<ProviderEntry> providers;  // 排序后的供应商列表
    private final Map<String, CircuitBreaker> breakers;

    @Override
    public Flux<String> chatStream(ChatRequest request) {
        return Flux.defer(() -> {
            for (ProviderEntry entry : providers) {
                CircuitBreaker cb = breakers.get(entry.name());
                if (cb.isOpen()) continue;  // 跳过熔断的

                Flux<String> result = entry.provider().chatStream(request)
                    .timeout(Duration.ofSeconds(60))  // 首包探测
                    .onErrorResume(e -> { cb.recordFailure(); return Flux.empty(); });

                // 成功 → 记录并返回
                return result.doOnNext(__ -> cb.recordSuccess());
            }
            return Flux.error(new AllProvidersFailedException());
        });
    }
}
```

嵌入和 Rerank 服务也采用同样的多供应商 + 降级策略，但优先级可以不同（例如嵌入用百炼的 `text-embedding-v4` 性价比最高，不需要频繁切换）。

#### 3.4.4 供应商选择指南

| 场景 | 推荐配置 |
|------|---------|
| 开发环境 | 仅配置 1 个供应商（百炼免费额度） |
| 生产环境 | 配置 2 个外部供应商（百炼 + SiliconFlow），关 Ollama |
| 有 GPU 服务器 | 加配 Ollama 作为第三级兜底 |
| 纯离线环境 | 仅配 Ollama，关所有外部 API |

### 3.5 文档摄取策略

**核心场景**：博客文章自动入库。

```
Blog 文章发布/更新
        │
        ▼
┌──────────────────┐
│ ArticleListener  │  Spring Event 监听
│ (@EventListener) │
└──────┬───────────┘
       │
       ▼
┌──────────────────┐
│ Markdown → Text  │  去除 frontmatter，保留正文
└──────┬───────────┘
       │
       ▼
┌──────────────────┐
│ FixedSizeChunker │  按 800 字符分块，100 字符重叠
│ (Markdown 感知)   │  尽量在段落/标题边界切分
└──────┬───────────┘
       │
       ▼
┌──────────────────┐
│ EmbeddingService │  分块文本 → 向量 (百炼 text-embedding-v4 等)
└──────┬───────────┘
       │
       ▼
┌──────────────────┐
│ PgvectorStore    │  向量写入 pgvector + 元信息写入 MySQL
└──────────────────┘
```

**摄取方式**：
1. **自动同步**：文章发布/更新时通过 Spring Event 自动触发
2. **手动上传**：Dashboard 上传 Markdown/PDF 文件 (备用)
3. **批量导入**：初始化时批量重建已有文章索引 (Admin API)

**简化说明**：博客场景不需要 Ragent 原设计中的 DAG 可编排流水线、多种解析器矩阵（以 Markdown 为主，PDF 通过 Tika 兜底）、RocketMQ 异步处理（直接用 Spring `@Async`）。

### 3.6 RAG 问答流程

完整流程（简化版，保留核心链路）：

```
用户问题 (来自博客前端聊天浮窗)
        │
        ▼
┌──────────────┐
│ 1. JWT 认证   │  复用现有 JwtAuthenticationFilter
│              │  (已登录用户自动获取身份)
└──────┬───────┘
       ▼
┌──────────────┐
│ 2. 记忆加载   │  从 MySQL 加载最近 6 轮对话
│              │  超出 10 轮时 LLM 自动摘要压缩
└──────┬───────┘
       ▼
┌──────────────┐
│ 3. 查询重写   │  LLM 判断是否需要拆分 (可选，简单问题跳过)
│  (可选)      │
└──────┬───────┘
       ▼
┌──────────────┐
│ 4. 向量检索   │  pgvector cosine 相似度检索，Top 10
│              │  同时可选叠加 MySQL LIKE 关键词检索
└──────┬───────┘
       ▼
┌──────────────┐
│ 5. 重排序     │  Rerank 模型重排 Top 10 → Top 5 (可选)
│              │  优先用百炼 gte-rerank，不可用时降级到下一供应商
└──────┬───────┘
       ▼
┌──────────────┐
│ 6. 上下文组装 │  按场景选模板：
│              │  - ARTICLE_QA：博客内容问答 (主要)
│              │  - GENERAL：通用对话
│              │  - EMPTY：无匹配结果
└──────┬───────┘
       ▼
┌──────────────┐
│ 7. 流式生成   │  RoutingLLMService → 按优先级尝试供应商
│  (SSE)       │  百炼 → SiliconFlow → AIHubMix → Ollama
│              │  断路器跳过故障供应商，首包探测 60s 超时
└──────────────┘
```

**与 Ragent 原设计的差异**：

| 原设计 | 集成版 | 理由 |
|--------|--------|------|
| 9 个专用线程池 | 1 个 `ragExecutor` + `@Async` | 博客并发低，无需复杂线程模型 |
| 分布式信号量排队 | 无 | 单实例博客，无需分布式限流 |
| 多模型供应商路由 + 断路器 | **保留** | 外部 API 优先 + Ollama 兜底的策略依赖此机制 |
| 子问题并行检索 | 顺序检索 | 单知识库场景，简化 |
| 多知识库 + 意图树 | 单一博客知识库 | 等有多知识库需求再加 |
| 多通道 RRF 融合 | 直接向量检索 + 重排序 | 单通道足够，Rerank 已是精排 |
| MCP 工具调用 | 无 (预留接口) | 博客阶段不需要 |
| 歧义检测/澄清 | 无 | 博客场景问题通常明确 |

### 3.7 对话记忆

**方案**：数据库存储 + 滑动窗口 + 可选摘要。

```sql
CREATE TABLE t_conversation (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(200),           -- 第一条消息前30字
    message_count INT DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE t_conversation_message (
    id BIGINT PRIMARY KEY,
    conversation_id BIGINT NOT NULL REFERENCES t_conversation(id),
    role VARCHAR(20) NOT NULL,     -- USER / ASSISTANT
    content TEXT NOT NULL,
    sources JSONB,                 -- 引用的文档块 [{chunkId, docTitle, score}]
    token_count INT DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

**记忆策略**：
- 最近 6 轮完整保留
- 超出部分由 LLM 生成摘要（在下次发送前触发）
- 用户可清除对话历史

### 3.8 认证与安全

**复用现有 JWT 认证体系，零改动**：

```java
// RAG 端点的权限设计
GET  /v1/rag/knowledge-bases        # 任何人可查看知识库列表
POST /v1/rag/chat/stream            # 任何人可发起聊天 (SSE)
GET  /v1/rag/conversations          # 登录用户可查看自己的对话
POST /v1/rag/conversations/{id}/clear  # 登录用户清除自己的对话
POST /v1/rag/knowledge-base         # DEVELOPER 创建知识库
POST /v1/rag/knowledge-base/{id}/docs  # DEVELOPER 上传文档
POST /v1/rag/knowledge-base/{id}/sync   # DEVELOPER 同步博客文章
```

JWT 过滤器无需调整——`/v1/rag/chat/stream` 直接加入 Spring Security 的 `permitAll` 列表，其他端点按需认证。

---

## 四、实施计划

### Phase 1：基础设施搭建 (第 1 步)

**目标**：Docker 环境就绪，依赖引入，编译通过。

1. **升级 Spring Boot 至 3.5.7**
   - 修改 `pom.xml` parent 版本
   - 验证编译和测试通过

2. **docker-compose 新增服务**
   ```yaml
   postgres:
     container_name: mysite-pgvector
     image: pgvector/pgvector:pg16
     restart: unless-stopped
     ports:
       - "5432:5432"
     environment:
       POSTGRES_USER: ragent
       POSTGRES_PASSWORD: ${RAGENT_PG_PASSWORD}
       POSTGRES_DB: ragent
     volumes:
       - ./pgvector_data:/var/lib/postgresql/data
       - ./init/ragent-schema.sql:/docker-entrypoint-initdb.d/init.sql
     deploy:
       resources:
         limits:
           memory: 512M
     networks:
       - mysite-network

   # Ollama 可选 —— 仅当你需要本地模型兜底时启用
   # 如果不启用，需要至少配一个外部 API (百炼/SiliconFlow/AIHubMix)
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
     deploy:
       resources:
         limits:
           memory: 12G    # qwen3:8b 需 ~6GB
     networks:
       - mysite-network
     profiles:            # 使用 profile 控制，默认不启动
       - ollama
   ```

   启动 Ollama：`docker compose --profile ollama up -d ollama`
   不启动 Ollama：`docker compose up -d` (postgres 正常启动)

3. **添加 Maven 依赖**
   ```xml
   <!-- WebFlux (SSE 流式响应) -->
   <dependency>
       <groupId>org.springframework.boot</groupId>
       <artifactId>spring-boot-starter-webflux</artifactId>
   </dependency>

   <!-- PostgreSQL -->
   <dependency>
       <groupId>org.postgresql</groupId>
       <artifactId>postgresql</artifactId>
   </dependency>

   <!-- pgvector 支持通过 MyBatis-Plus 原生 SQL -->
   ```

4. **配置 API Key** (至少配一个)
   ```bash
   # 在 .env 或环境变量中设置
   export BAILIAN_API_KEY="sk-xxxxxxxx"
   # export SILICONFLOW_API_KEY="sk-xxxxxxxx"   # 可选备选
   # export AIHUBMIX_API_KEY="sk-xxxxxxxx"      # 可选备选
   ```

5. **拉取 Ollama 模型** (仅当启用 Ollama 兜底时)
   ```bash
   docker exec mysite-ollama ollama pull qwen3:8b
   docker exec mysite-ollama ollama pull qwen3-embedding
   ```

### Phase 2：核心引擎实现 (第 2-3 步)

**目标**：RAG 问答链路跑通。

1. **双数据源配置** - `RagentDataSourceConfig.java`
2. **LLM 抽象层** - `AbstractOpenAILLMProvider` + 4 个供应商实现 + `RoutingLLMService` + `CircuitBreaker`
3. **向量存储** - `PgvectorVectorStore` (通过 MyBatis Mapper + 原生 SQL)
4. **检索引擎** - 向量检索 + Rerank
5. **对话记忆** - ConversationManager
6. **核心服务** - `RagChatService`，组装完整 RAG 链路
7. **SSE 控制器** - `RagController.chatStream()`

### Phase 3：文档摄取 (第 4 步)

**目标**：博客文章自动入库。

1. **分块器** - `MarkdownChunker`
2. **文档服务** - `KnowledgeDocumentService`
3. **事件监听** - `ArticleEventListener` (监听 `ArticleCreatedEvent` / `ArticleUpdatedEvent`)
4. **手动同步 API** - 批量重建已有文章索引

### Phase 4：前端集成 (第 5-6 步)

**目标**：用户可以在博客页面上使用 AI 聊天。

1. **API 层** - `api/rag.ts` (SSE EventSource 封装)
2. **Composable** - `useChat.ts` (聊天状态管理)
3. **聊天组件** - `ChatWidget.vue` 系列
4. **路由注册** - 添加 AI 聊天浮窗到默认布局、Dashboard 知识库页面
5. **Dashboard** - 知识库管理页面 (可选)

### Phase 5：部署适配 (第 7 步)

**目标**：生产环境可用。

1. **Nginx 配置** - SSE 长连接超时调整 (`proxy_read_timeout 120s`)
2. **API Key 环境变量** - 生产环境通过 systemd EnvironmentFile 或 Docker secrets 注入
3. **Ollama 启动脚本** (可选) - 仅当启用 Ollama 兜底时需要

---

## 五、关键风险与缓解

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| 外部 API 欠费/限流 | 所有供应商不可用 | 至少配 2 个外部供应商；可选配 Ollama 兜底 |
| 外部 API 延迟波动 | 用户等待时间变长 | 断路器自动跳过慢供应商；首包探测 60s 超时 |
| pgvector 检索性能 | 万级文档后变慢 | 利用 HNSW 索引；监控响应时间；预留 Milvus 升级路径 |
| SSE 连接不稳定 | 流式输出中断 | 前端加断线重连逻辑；后端加心跳 |
| Spring Boot 升级兼容性 | 编译/运行时错误 | Phase 1 即升级并验证；升级前在 git 中打 tag |
| AI 回答质量差 | 用户体验差 | 调优 chunk 大小/重叠参数；调优提示模板；优先用 qwen3-max 保证质量 |

---

## 六、与原 Ragent 设计的差异总结

本方案不是简单地将 Ragent 代码移植到 MySite，而是从 Ragent 架构设计中**提取核心思想**，以适合个人博客的规模重新实现：

| 保留的设计 | 简化的设计 | 移除的设计 |
|-----------|-----------|-----------|
| 多模型供应商路由 + 断路器 | 单通道检索 (去意图树、去 RRF 融合) | 分布式信号量排队 |
| 向量检索 + Rerank 核心链路 | 单知识库 (去多库隔离) | RocketMQ 消息队列 |
| 对话记忆滑动窗口 | 顺序检索 (去子问题并行) | MCP 工具服务器 (预留) |
| Markdown 分块策略 | 博客文章自动同步 (去 DAG 流水线) | Sa-Token (统一用 JWT) |
| SSE 流式输出 | 1 个线程池 (去 9 个专用线程池) | MinIO 对象存储 |
| pgvector + HNSW | ES 去重 (已通过 Rerank 覆盖) | MinerU SaaS (博客 Markdown 为主) |
| LLM 抽象接口 (多供应商) | | |

---

## 七、数据流全景图

```
┌─────────────────────────────────────────────────────────────────────┐
│                          摄取流 (写入)                               │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  Blog Editor                                                       │
│  (Dashboard)   ──保存文章──▶  ArticleService.saveOrUpdate()         │
│                                    │                                │
│                              Spring Event                           │
│                                    │                                │
│                                    ▼                                │
│                           ArticleEventListener                      │
│                                    │                                │
│                            MarkdownChunker                          │
│                            (800 char/块)                            │
│                                    │                                │
│                                    ▼                                │
│                     EmbeddingService (多供应商路由)                 │
│                     百炼 text-embedding-v4 → SiliconFlow → ...     │
│                                    │                                │
│                                    ▼                                │
│                     PgvectorVectorStore.insert()                    │
│                     PostgreSQL: t_knowledge_vector                  │
│                                                                     │
├─────────────────────────────────────────────────────────────────────┤
│                          问答流 (读取)                               │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  ChatWidget.vue                                                     │
│  (前端浮窗)   ──用户问题──▶  GET /v1/rag/chat/stream?q=...&cid=...  │
│                                    │                                │
│                                    ▼                                │
│                           RagController                             │
│                                    │                                │
│                    ConversationManager.loadHistory()                │
│                    (MySQL: t_conversation_message)                  │
│                                    │                                │
│                    EmbeddingService.embed(question)                 │
│                                    │                                │
│                    PgVectorStore.search(embedding, topK=10)         │
│                    PostgreSQL: t_knowledge_vector ◀── cosine        │
│                                    │                                │
│                    RerankService.rerank(question, candidates)       │
│                    (多供应商路由, topK → 5)                          │
│                                    │                                │
│                    PromptTemplate.build(context, history, question) │
│                                    │                                │
│                    RoutingLLMService.chatStream(prompt)             │
│                    百炼 qwen3-max → SiliconFlow → ... → SSE 流式    │
│                                    │                                │
│                                    ▼                                │
│                    ChatStreamWriter.vue  (逐字渲染到页面)            │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 八、配置全景图

```yaml
# application.yaml 新增 RAG 配置段
rag:
  # 数据源
  datasource:
    driver-class-name: org.postgresql.Driver
    url: jdbc:postgresql://localhost:5432/ragent
    username: ragent
    password: ${RAGENT_PG_PASSWORD}
    hikari:
      maximum-pool-size: 5
      minimum-idle: 1

  # === AI 模型供应商配置 (优先级从高到低) ===

  # P1: 阿里百炼 (首选，国内延迟最低)
  llm:
    providers:
      bailian:
        enabled: true
        priority: 1
        base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
        api-key: ${BAILIAN_API_KEY}
        chat-model: qwen3-max
        embedding-model: text-embedding-v4
        rerank-model: gte-rerank
        chat-timeout: 120s
        embedding-timeout: 30s

      # P2: SiliconFlow (备选)
      siliconflow:
        enabled: true
        priority: 2
        base-url: https://api.siliconflow.cn/v1
        api-key: ${SILICONFLOW_API_KEY}
        chat-model: glm-4.7
        embedding-model: BAAI/bge-large-zh-v1.5
        chat-timeout: 120s
        embedding-timeout: 30s

      # P3: AIHubMix (第三备选)
      aihubmix:
        enabled: true
        priority: 3
        base-url: https://aihubmix.com/v1
        api-key: ${AIHUBMIX_API_KEY}
        chat-model: gpt-5.4
        embedding-model: text-embedding-3-small
        chat-timeout: 120s
        embedding-timeout: 30s

      # P9: 本地 Ollama (最低优先级兜底，默认关闭)
      ollama:
        enabled: false
        priority: 9
        base-url: http://localhost:11434/v1
        api-key: ""                          # Ollama 无需 API Key
        chat-model: qwen3:8b
        embedding-model: qwen3-embedding
        chat-timeout: 180s                   # 本地模型推理慢，放宽超时
        embedding-timeout: 60s

    # 断路器全局参数
    circuit-breaker:
      failure-threshold: 2      # 连续失败 N 次 → OPEN
      cooldown-seconds: 30      # OPEN 后冷却 N 秒 → HALF_OPEN
      first-packet-timeout: 60  # 首包探测超时 (秒)

  # 分块参数
  chunk:
    size: 800           # 每块最大字符数
    overlap: 100        # 重叠字符数
    max-chunks-per-doc: 50  # 单文档最大分块数

  # 检索参数
  retrieval:
    top-k: 10           # 向量检索候选数
    rerank-top-k: 5     # Rerank 后保留数
    score-threshold: 0.3  # 最低相似度阈值

  # 对话记忆
  memory:
    keep-turns: 6       # 保留最近 N 轮完整对话
    summary-turns: 10   # 超过 N 轮触发摘要
    summary-enabled: true

  # 异步处理
  async:
    core-pool-size: 2
    max-pool-size: 4
    queue-capacity: 100
```

---

## 九、附录

### A. 服务器资源需求评估

#### 推荐配置 (仅外部 API，不用 Ollama)

| 组件 | 原系统 | 新增 | 总计 |
|------|--------|------|------|
| CPU | 2 核 | 0 | 2 核 |
| 内存 | 2GB | +512MB (PostgreSQL) | ~2.5GB |
| 磁盘 | 现有 | +1GB (向量数据) | +1GB |
| 月度费用 | 0 | ~10-30 元 (API 按量) | ~10-30 元 |

#### 可选配置 (加 Ollama 兜底)

| 组件 | 推荐配置 | 增量 | 总计 |
|------|---------|------|------|
| CPU | 2 核 | +1 核 (Ollama) | 3 核 |
| 内存 | 2.5GB | +8GB (Ollama 模型) | ~10GB |
| 磁盘 | +1GB | +5GB (模型文件) | +6GB |
| 月度费用 | ~10-30 元 | 0 (Ollama 免费) | ~10-30 元 |

**结论**：推荐只用外部 API。2.5GB 内存即可运行，不需要 GPU，不需要大内存服务器。Ollama 是可选附加项，有资源就开，没有也不影响使用。

### B. 后续扩展路线

1. **多知识库**：为"技术博客"和"读书笔记"建不同知识库 → 引入意图识别
2. **Ollama 兜底**：有 GPU 资源后启用本地模型作为额外兜底
3. **MCP 工具**：天气查询、友链查询等博客实用工具 → 引入 MCP 集成
4. **反馈评估**：用户点赞/踩 → 优化检索质量
5. **Rerank 微调**：积累足够反馈数据后，微调 Rerank 模型提升检索精度
