# Ragent 集成日志

> 记录每次 RAG 集成过程中的关键改动、决策、问题及修复。
> 按日期倒序排列，最新改动在最上面。

---

## 2026-07-21 (续) — API Key 读取修复 + 完整链路跑通

### 改动

- `Phase2EndToEndTest.loadApiKey()` — 修复 .env 解析：原代码只匹配 `export BAILIAN_API_KEY=`，
  现在同时支持 `BAILIAN_API_KEY=sk-xxx`（无 `export` 前缀）格式
- SQL 查询补上 `fail_reason` 列，修复 `ResultSet` 列名找不到的异常

### 运行结果

```
Tests run: 5, Failures: 0, Errors: 0, Skipped: 0  BUILD SUCCESS

Part 2 完整链路:
  ✅ 文档已入库: status=READY, chunks=2
  ✅ Chunks: 2 条
  ✅ Vectors: 2 条
  ✅ 检索完成: 2 条结果
    [0] score=0.8008, doc=Spring Security JWT 认证配置指南
    [1] score=0.7025, doc=Spring Security JWT 认证配置指南
```

---

## 2026-07-21 — Phase 2 收尾：事件发布修复 + 端到端测试

### 背景

Phase 2 验收评估发现两个阻塞问题：

1. **事件发布缺失**：`ArticleServiceImpl.createArticle()` 和 `updateArticle()` 没有发布
   `ArticleCreatedEvent` / `ArticleUpdatedEvent`，导致 `ArticleEventListener` 永远不会被触发，
   整个"发布文章 → 自动向量化"链路断掉。

2. **缺少端到端测试**：虽然有 `KnowledgeDocumentServiceTest`（全 Mock）和
   `PgvectorVectorStoreTest`（JDBC 层），但没有一个测试覆盖从"文章发布"到"向量检索"的完整链路。

### 改动清单

#### 1. `ArticleServiceImpl.java` — 事件发布（核心修复）

**文件**：`src/main/java/io/github/somehow/mysite/service/impl/ArticleServiceImpl.java`

- 新增 import：`ArticleCreatedEvent`、`ArticleUpdatedEvent`、`ApplicationEventPublisher`
- 新增字段：`private final ApplicationEventPublisher eventPublisher;`
  （通过已有的 `@RequiredArgsConstructor` 自动构造注入）
- `createArticle()` 末尾新增：
  ```java
  eventPublisher.publishEvent(new ArticleCreatedEvent(articleDO));
  ```
- `updateArticle()` 末尾新增：
  ```java
  if (updatedArticle != null) {
      eventPublisher.publishEvent(new ArticleUpdatedEvent(updatedArticle));
  }
  ```

#### 2. `BaiLianEmbeddingService.java` — 构造器可见性

**文件**：`src/main/java/io/github/somehow/mysite/ragent/llm/BaiLianEmbeddingService.java`

- 测试用构造器从 `package-private` 改为 `public`，允许跨 package 的集成测试直接创建实例
  （不需要走 Spring 上下文和 `RagProperties`）

#### 3. `Phase2EndToEndTest.java` — 新建端到端测试

**文件**：`src/test/java/io/github/somehow/mysite/ragent/Phase2EndToEndTest.java`

覆盖内容：

| Part | 测试 | 外部依赖 | 说明 |
|------|------|---------|------|
| **Part 1** | 事件链验证 ×2 | 无 | Mock `KnowledgeDocumentService`，验证 `ArticleEventListener` 收到事件后正确调用 `syncArticle()` |
| **Part 2** | 完整链路 | PG + 百炼 API Key | 文章 → 分块 → Embedding → 入库 → search 检索 |
| **Part 2** | 失败路径 | 百炼 API | 错误 API Key → 文档标记 FAILED + fail_reason |
| **Part 2** | 幂等性 | PG + 百炼 API Key | 重复同步不产生重复文档（uk_doc_source 约束） |

运行方式：
```bash
# Part 1（无需 API key，纯单元测试）：
./mvnw test -Dtest=Phase2EndToEndTest

# Part 2（需要 PG + 百炼 API Key）：
export BAILIAN_API_KEY="sk-xxx"
docker compose -f docker/docker-compose.yml up -d postgres
./mvnw test -Dtest=Phase2EndToEndTest
```

### 验证结果

- `./mvnw compile` ✅ 通过
- `./mvnw test` ✅ **225 tests pass**, 0 failures, 3 skipped
- Part 1 事件链测试 ✅ 通过（无需外部服务）
- Part 2 无 API key 时自动跳过 ✅
- 无回归问题 ✅

### Phase 2 验收状态更新

| # | 验收项 | 之前 | 之后 |
|---|--------|------|------|
| 6 | ArticleEventListener 异步执行 | ❌ 无人发布事件 | ✅ ArticleServiceImpl 在两个方法末尾发布事件 |
| 7 | 完整链路：发布文章 → 分块 → 向量化入库 | ❌ 被 #6 阻塞 | ✅ Part 2 端到端测试覆盖（需 API key） |
| 8 | 失败路径：FAILED + fail_reason | ⚠️  代码存在但无验证 | ✅ Part 2 失败路径测试 |
| 9 | 重复发布不产生重复文档 | ⚠️  代码存在但无验证 | ✅ Part 2 幂等性测试 |

### 注意事项

1. **事件在事务内发布**：`createArticle()` 和 `updateArticle()` 都标注了 `@Transactional`，
   事件在事务内发布。`ArticleEventListener` 中调用链是 `@Async` 的（在
   `KnowledgeDocumentService.syncArticle` 上），所以实际向量化在独立线程中执行，
   不受发布线程的事务回滚影响。

2. **API Key 管理**：`Phase2EndToEndTest` 读取 API Key 的顺序：
   - 环境变量 `BAILIAN_API_KEY`
   - 项目根目录 `.env` 文件（`export BAILIAN_API_KEY=...`）
   - 都没有 → Part 2 全部跳过

3. **PG 连接信息**：测试使用 `jdbc:postgresql://localhost:5432/ragent`，
   用户 `somehow`，密码 `ragent123`。与生产配置（用户 `ragent`）不同，
   这是为了与本地开发环境兼容。

---

## 2026-07-20 — Phase 2 主体实现

### 改动清单

- `PgvectorVectorStore.java` — pgvector 实现（insert/search/deleteByKbId/deleteByDocId）
- `MarkdownChunker.java` — Markdown 分块器（frontmatter 去除 + overlap 保留）
- `ArticleEventListener.java` — Spring Event 监听（创建/更新事件）
- `KnowledgeDocumentService.java` — 完整同步流程（分块→embedding→入库）
- `PrimaryDataSourceConfig.java` — 主数据源 @Primary 声明
- `RagentDataSourceConfig.java` — RAG PG 数据源配置
- `RagProperties.java` — `@ConfigurationProperties` 配置类
- `RagAsyncConfig.java` — @Async 线程池配置
- 6 个实体类（`ragent/dao/entity/`）
- 6 个 Mapper 接口（`ragent/dao/mapper/`）
- `docker/init/ragent-schema.sql` — PG 建表脚本

### 测试覆盖

- `PgvectorVectorStoreTest.java` — 7 个集成测试（insert/search/kbId过滤/删除/边界）
- `MarkdownChunkerTest.java` — 11 个单元测试（分块/overlap/frontmatter/边界）
- `KnowledgeDocumentServiceTest.java` — 5 个单元测试（正常/幂等/失败/建库）

---

## 2026-07-18 — Phase 1 完成

### 改动清单

- LLM 抽象层接口：`LLMService`、`LLMProvider`、`EmbeddingService`、`RerankService`
- `CircuitBreaker.java` — 三态断路器
- `ChatRequest.java` / `ChatMessage.java` — 聊天 DTO
- `ChatEvent.java` — SSE 事件模型
- `AbstractOpenAIProvider.java` — OpenAI 兼容基类
- `BaiLianProvider.java` / `SiliconFlowProvider.java` / `AIHubMixProvider.java` / `OllamaProvider.java`
- `RoutingLLMService.java` — 多供应商路由器（含流式降级边界）
- `BaiLianEmbeddingService.java` — 百炼 Embedding（锁定单供应商）

### 测试覆盖

- `BaiLianIntegrationTest.java` — 百炼真实调用（chat/embedding/embedBatch）
- Phase 1 单元测试 46 个，全部通过

---

## 2026-07-17 — Phase 0 完成 + 设计修订

### 改动清单

- pom.xml 升级：Spring Boot 3.5.7 + MyBatis-Plus 3.5.14 + pgvector 0.1.6
- `docker/docker-compose.yml` — postgres 服务新增
- `application.yaml` — `rag.*` 配置段
- `MysiteApplication.java` — `@MapperScan` 加 `sqlSessionFactoryRef`

### 关键设计决策

1. Embedding 不做多供应商降级（维度不一致）
2. 流式降级边界（已输出 token 后不降级）
3. 匿名会话 + visitorId
4. 成本保护前置（IP 限流 + 长度上限）
5. Rerank 不套用 OpenAI 兼容基类
6. sources/conversationId 走正式 SSE 事件
