# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

MySite is a full-featured personal blog platform with AI-powered RAG (Retrieval-Augmented Generation) chat, plus an admin-only study journal API. The backend runs on port 8081, the frontend dev server on port 5173 with proxy to backend.

Cursor 会话记忆：`.cursor/rules/`（架构 alwaysApply；后端/前端/RAG/手帐按文件 glob 加载）。新会话不要为摸结构再扫全库。

| Layer | Stack |
|-------|-------|
| Backend | Spring Boot 3.0.7 + MyBatis-Plus + Spring WebFlux (Reactor) |
| Frontend | Vue 3 + TypeScript + Vite + Pinia |
| Primary DB | MySQL 8.4 (13 tables, all logical-deletion) |
| Cache | Redis 7 (strategy-pattern TTL per entity) |
| RAG DB | PostgreSQL 17 + pgvector (6 tables: KB, docs, chunks, vectors, conversations) |

## Quick Start Commands

### Infrastructure

```bash
# Start all services (MySQL + Redis + PostgreSQL)
cd docker && docker compose up -d

# Start with optional local LLM (requires 8GB+ RAM)
cd docker && docker compose --profile ollama up -d
```

### Backend (Spring Boot)

```bash
./mvnw spring-boot:run          # Dev server (port 8081)
./mvnw compile                   # Compile check
./mvnw test                      # Run tests
./mvnw clean package -Pproduction # Production build
```

### Frontend (Vue 3 + Vite)

```bash
cd mysite-frontend
npm install                      # Install dependencies
npm run dev                      # Dev server (port 5173, proxies /v1 → 8081)
npx vue-tsc --noEmit --pretty   # Type check
npm run build                    # Production build
```

### Deploy

```bash
./deploy/deploy.sh               # One-click build + package
./deploy/server-deploy.sh        # Server-side deploy
```

## Architecture

### Backend Package Structure

```
src/main/java/io/github/somehow/mysite/
├── MysiteApplication.java
├── config/          # Spring configs: Security, Cache, CORS, Async, WebMvc
├── controller/      # REST API endpoints (/v1/*)
├── service/         # Business logic
│   └── impl/        # Service implementations
├── dao/             # Data access (MyBatis-Plus)
│   ├── entity/      # 13 MySQL entities with logical deletion
│   └── mapper/      # MyBatis mapper interfaces
├── dto/             # Request/response DTOs (Long IDs → @JsonSerialize ToString)
├── commons/         # Shared: UserContext, error codes, exceptions, enums
├── security/        # JWT dual-token auth, Spring Security filter chain
├── elasticsearch/   # ES integration (optional, with DB LIKE fallback)
├── utils/           # ReadingTimeCalculator, etc.
├── journal/         # 学习手帐（花期 Blossom）：/api/journal，仅 ADMIN，MySQL sj_*
│
└── ragent/          # ★ RAG AI subsystem (separate PG datasource)
    ├── config/      # RagProperties, RagentDataSourceConfig, RagAsyncConfig, SchemaMigration
    ├── controller/  # RagChatController (SSE), KnowledgeBase/Document controllers
    ├── service/     # RagChatService, KnowledgeBaseService, KnowledgeDocumentService, ChatRateLimiter
    ├── dto/         # KnowledgeBaseDTO, SourceChunkDTO, ChatMessageDTO, ChatStreamRequest
    ├── dao/         # RAG entities + mappers (entity/, mapper/, handler/)
    ├── core/        # RAG pipeline: PromptTemplate, ConversationManager, RetrievalEngine, QueryRewriter, IntentClassifier
    ├── llm/         # LLM integration (multi-provider routing + circuit breaker)
    │   ├── model/       # ChatEvent, ChatMessage, ChatRequest, ChatResponse
    │   ├── provider/    # AbstractOpenAiProvider, BaiLian, Deepseek, Ollama, SiliconFlow
    │   ├── embedding/   # EmbeddingService (interface), BaiLianEmbeddingService
    │   └── rerank/      # RerankService (interface), BaiLianRerankProvider
    ├── chunking/    # DocumentChunker (interface), MarkdownChunker
    ├── ingestion/   # Spring events: ArticleCreatedEvent, ArticleUpdatedEvent, ArticleEventListener
    └── vector/      # VectorStore (interface), PgvectorVectorStore (JDBC, cosine distance)
```

### Frontend Component Structure

```
mysite-frontend/src/
├── app/                # App.vue, layouts/ (DefaultLayout, DashboardLayout), router/
├── views/              # Page components (前台博客 + /dashboard 管理页)
├── components/
│   ├── article/        # ArticleCard, ArticleContent, ArticleToc, ArticleMeta, TocTree, etc.
│   ├── auth/           # LoginForm, RegisterForm
│   ├── chat/           # ★ AI Chat: ChatWidget, ChatMessageItem, ChatStreamWriter, ChatInput, ChatSources, ChatHistory
│   ├── collection/     # CollectionCard, ArticleNav
│   ├── comment/        # CommentSection, CommentItem
│   ├── common/         # AppHeader, AppFooter, ThemeToggle, SearchDialog, BackToTop, ToastContainer, etc.
│   ├── dashboard/      # DashboardSidebar
│   ├── editor/         # MarkdownWysiwygEditor (Milkdown Crepe WYSIWYG)
│   └── ui/             # 后台通用：DataTable, Pagination, EmptyState, Modal, Drawer, etc.
├── editor/             # Milkdown 创建/插件/图片上传
├── composables/        # useChat, useMarkdown, useTheme, useSearch, useToast, usePermission, etc.
├── api/                # client.ts (axios), rag.ts (fetch SSE), article, auth, etc.
├── stores/             # Pinia: user.ts, site.ts
├── types/              # TypeScript interfaces (all Snowflake IDs: string)
└── utils/              # gravatar, validators, date formatting, storage
```

**Key patterns:**
- **SSE streaming**: `api/rag.ts` uses native `fetch + ReadableStream` (not axios) for `/v1/rag/chat/stream`. All other APIs use axios `client.ts`.
- **Chat state machine**: `useChat.ts` — status: `idle | streaming | error`, messages with `pending | failed | truncated` flags, abort via `AbortController`.
- **Markdown rendering**: `useMarkdown` (articles, async render + TOC + KaTeX + Callouts) vs `useChatMarkdown` (chat, sync render + rAF throttle + DOMPurify).
- **Snowflake ID safety**: All `Long` IDs in DTOs use `@JsonSerialize(using = ToStringSerializer.class)`. Frontend types use `string` for IDs. Snowflake values exceed JS `Number.MAX_SAFE_INTEGER` (2^53).
- **Anonymous chat**: `visitorId` (localStorage UUID) for unauthenticated users; rate-limited per IP + role.
- **Role-based rate limits**: ADMIN unlimited, CREATOR 20/h, USER 10/h. Question max 500 chars.

### Database Schema

**MySQL — 博客 13 张表（均有 `del_flag` 逻辑删除）：**

```
t_user, t_article, t_category, t_tag, t_article_tag,
t_collection, t_collection_article, t_comment, t_comment_like,
t_image, t_user_follow, t_user_article_favorites, t_user_operation_log
```

**MySQL — 学习手帐 3 张表（无 del_flag，`/api/journal`，仅 ADMIN）：**

```
sj_day_record, sj_learning_item, sj_custom_mood
```

**PostgreSQL + pgvector（RAG）：**

```
t_knowledge_base         # KB definitions
t_knowledge_document     # sourceType: ARTICLE|UPLOAD, status: PENDING→CHUNKING→READY|FAILED
t_knowledge_chunk        # Text chunks
t_knowledge_vector       # vector(1024), HNSW, cosine (<=>)
t_conversation           # visitorId/userId + title
t_conversation_message   # role, content, sources JSONB
t_rag_intent             # 扁平意图：KB_RETRIEVAL / CHAT，绑定 kb_id

Schema init: docker/init/schema.sql + ragent-schema.sql（手帐增量见 journal-schema.sql）
```

### RAG Pipeline (SSE event sequence)

```
Client GET /v1/rag/chat/stream?q=...&visitorId=...&conversationId=...
  │
  ▼
RagChatService.chat()
  ├─ Step 0: Rate limit + question length
  ├─ Step 1: Get or create conversation + load history
  ├─ Step 2: QueryRewriter（指代消解 / 拆分 / 口语正规化）
  ├─ Step 3: IntentClassifier（KB_RETRIEVAL / CHAT / MCP；失败降级，不阻塞）
  ├─ [短路] 非 KB_RETRIEVAL 跳过检索
  ├─ Step 4: 按前端传入的 kbIds 检索（未选 KB = 纯 LLM）→ Rerank
  ├─ Step 5: Build prompt → LLM streaming（routing + circuit breaker）
  │   └─ meta → sources → content×N → done/error
  └─ Step 6: Save exchange to DB (boundedElastic, after stream completes)

意图只判断模式，不替用户选择知识库。分类 LLM（cheap model）与聊天主力模型分开。

SSE events:
  data: {"type":"meta","conversationId":"123"}
  data: {"type":"sources","sources":[...]}
  data: {"type":"content","delta":"JWT"}
  ...
  data: {"type":"done"}
  data: {"type":"error","message":"..."}      ← errors become events, not bare disconnects
```

**Key RAG design decisions:**
- `Flux<ChatEvent>` is the core streaming type. Frontend cancellation propagates: `SseEmitter.onCompletion → subscription.dispose() → WebClient cancel → LLM API connection closed`.
- Circuit breaker: 2 consecutive failures → open; cooldown resets on first success.
- LLM routing: tries providers by priority; skips if circuit-breaker open. Can fallback pre-first-token only (prevents mixed responses).
- Rerank: BaiLian DashScope native API; falls back to vector-score truncation if unavailable.
- Chunking: Markdown-aware (preserves code blocks, headings, lists). Config: 800 char chunks, 100 overlap, max 50 per doc.

### Role System

| Role | Authority | Permissions |
|------|-----------|-------------|
| `ADMIN` | `ROLE_ADMIN` | Full access, unlimited AI queries |
| `CREATOR` | `ROLE_CREATOR` | Publish/edit own articles, AI: 20/h |
| `USER` | `ROLE_USER` | Browse, comment, AI: 10/h |

Deprecated `DEVELOPER` role auto-maps to `ADMIN` via `fromAuthority()`. Unknown roles default to `USER`.

### LLM Provider Configuration

Configured in `application.yaml` under `rag.llm.providers`:

| Priority | Provider | Status | Notes |
|----------|----------|--------|-------|
| 1 | DeepSeek | `enabled: true` | Primary (v4-flash, 120s timeout) |
| 2 | BaiLian (Alibaba) | `enabled: true` | Fallback + embedding (text-embedding-v4) + rerank (qwen3-rerank) |
| 3 | SiliconFlow | disabled | |
| 4 | AIHubMix | disabled | |
| 5 | Ollama | disabled | Local, requires `--profile ollama` |

All providers use OpenAI-compatible `/chat/completions` API via `AbstractOpenAiProvider`.

## Development Workflow

### Git Commit Rules (Mandatory)

- **所有 git commit 的作者必须是用户本人**（somehow-g15），不得将 Claude/Claude Code 添加为 co-author。
- **禁止在 commit message 中出现 `Co-authored-by:` 等 co-author 标记。**
- 每次提交前检查 `git config user.name` 和 `git config user.email` 确保是正确的用户身份。

### Before Committing (Mandatory)

```bash
# Frontend
npx vue-tsc --noEmit --pretty    # Must pass
npx vite build                   # Must pass

# Backend
./mvnw compile                   # Must pass
./mvnw test                      # Must pass
```

**Rule: Never commit unverified code. Fix all errors before pushing. Commits are grouped by functional module — don't squash unrelated changes.**

### Configuration

- **Backend**: `src/main/resources/application.yaml` (MySQL, Redis, JWT, ES, RAG datasource, LLM providers)
- **Frontend**: `mysite-frontend/vite.config.ts` (proxy, build chunks, dedupe)
- **Docker**: `docker/docker-compose.yml` (MySQL 8.4, Redis 7, Postgres 17+pgvector)
- **DB Init**: `docker/init/schema.sql`, `docker/init/ragent-schema.sql`, `docker/init/journal-schema.sql`, `docker/init/data.sql`
- **Nginx**: `deploy/nginx/mysite.conf` (production HTTPS, WebP, static cache, SPA fallback)
- **Cursor rules**: `.cursor/rules/*.mdc`

### API Documentation

Swagger UI: `http://localhost:8081/swagger-ui.html` (backend must be running).

### Project Documentation

Design docs and implementation plans are in `docs/`:
- `docs/ragent-integration-design.md` — RAG architecture design
- `docs/ragent-integration-plan.md` — Implementation plan with phases
- `docs/ragent-frontend-design.md` — Frontend design specs (SSE client, ChatWidget, KB management)
- `docs/ragent-project-summary.md` — Learning notes and project summary
- `docs/DESIGN.md` — Original site design doc

### Production Deployment

- Backend: port 8081 (API server)
- Frontend: Nginx on port 8080 with `/v1/` reverse proxy
- MySQL 8.4 + Redis 7 + PostgreSQL 17 (Docker Compose)
- Nginx: HTTPS (Let's Encrypt), WebP conversion, static caching, SPA fallback
