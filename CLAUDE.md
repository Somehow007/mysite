# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

MySite is a full-featured personal blog platform with AI-powered RAG (Retrieval-Augmented Generation) chat. The backend runs on port 8081, the frontend dev server on port 5173 with proxy to backend.

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
│
└── ragent/          # ★ RAG AI subsystem (separate PG datasource)
    ├── config/      # RagProperties, RagentDataSourceConfig, RagAsyncConfig, SchemaMigration
    ├── controller/  # RagChatController (SSE), KnowledgeBase/Document controllers
    ├── service/     # RagChatService, KnowledgeBaseService, KnowledgeDocumentService, ChatRateLimiter
    ├── dto/         # KnowledgeBaseDTO, SourceChunkDTO, ChatMessageDTO, ChatStreamRequest
    ├── dao/         # RAG entities + mappers (entity/, mapper/, handler/)
    ├── core/        # RAG pipeline: PromptTemplate, ConversationManager, RetrievalEngine, QueryRewriter
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
├── views/              # 23 page components (routes)
├── components/
│   ├── article/        # ArticleCard, ArticleContent, ArticleToc, ArticleMeta, TocTree, etc.
│   ├── auth/           # LoginForm, RegisterForm
│   ├── chat/           # ★ AI Chat: ChatWidget, ChatMessageItem, ChatStreamWriter, ChatInput, ChatSources, ChatHistory
│   ├── collection/     # CollectionCard, ArticleNav
│   ├── comment/        # CommentSection, CommentItem
│   ├── common/         # AppHeader, AppFooter, ThemeToggle, SearchDialog, BackToTop, ToastContainer, etc.
│   ├── dashboard/      # DashboardSidebar
│   └── editor/         # MarkdownEditor, SimpleMarkdownEditor
├── composables/        # 14 composables: useChat, useMarkdown, useTheme, useSearch, useToast, usePermission, etc.
├── api/                # 11 API modules: client.ts (axios), rag.ts (fetch SSE), article, auth, etc.
├── stores/             # Pinia stores (user)
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

**MySQL (13 tables, all with `del_flag` logical deletion):**

```
t_user, t_article, t_category, t_tag, t_article_tag,
t_collection, t_collection_article, t_comment, t_comment_like,
t_image, t_user_follow, t_user_article_favorites, t_user_operation_log
```

**PostgreSQL + pgvector (6 tables for RAG):**

```
t_knowledge_base         # KB definitions (collection name, embedding model, chunk config)
t_knowledge_document     # Documents in KB (sourceType: ARTICLE|UPLOAD, status: PENDING→CHUNKING→READY|FAILED)
t_knowledge_chunk        # Text chunks from documents
t_knowledge_vector       # pgvector vector(1024) embeddings, HNSW index, cosine distance (<=>)
t_conversation           # Chat conversations (visitorId/userId + title)
t_conversation_message   # Chat history messages (role, content, sources JSONB)

Schema init: docker/init/ragent-schema.sql (auto-loaded by postgres container)
```

### RAG Pipeline (SSE event sequence)

```
Client GET /v1/rag/chat/stream?q=...&visitorId=...&conversationId=...
  │
  ▼
RagChatService.chat()
  ├─ Step 0: Rate limit check (sync, boundedElastic)
  ├─ Step 1: Get or create conversation (visitorId → DB)
  ├─ Step 2: Load chat history (sliding window, default 6 turns)
  ├─ Step 3: Vector retrieval (pgvector cosine distance) → Rerank
  ├─ Step 4: Build prompt (RAG with sources or general chat)
  ├─ Step 5: LLM streaming via WebClient (multi-provider routing + circuit breaker)
  │   └─ meta → sources → content×N → done/error
  └─ Step 6: Save exchange to DB (boundedElastic, after stream completes)

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
- **DB Init**: `docker/init/schema.sql`, `docker/init/ragent-schema.sql`, `docker/init/data.sql`
- **Nginx**: `deploy/nginx/mysite.conf` (production HTTPS, WebP, static cache, SPA fallback)

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
