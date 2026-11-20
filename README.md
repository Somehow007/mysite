<div align="center">

# MySite

**A personal blog platform with AI-powered RAG chat — Spring Boot 3 + Vue 3**

[![Java](https://img.shields.io/badge/Java-17-ED8B00?logo=openjdk&logoColor=white)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.7-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Vue.js](https://img.shields.io/badge/Vue.js-3-4FC08D?logo=vuedotjs&logoColor=white)](https://vuejs.org/)
[![TypeScript](https://img.shields.io/badge/TypeScript-5.9-3178C6?logo=typescript&logoColor=white)](https://www.typescriptlang.org/)
[![MySQL](https://img.shields.io/badge/MySQL-8.4-4479A1?logo=mysql&logoColor=white)](https://www.mysql.com/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-17%20%2B%20pgvector-4169E1?logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![Redis](https://img.shields.io/badge/Redis-7-DC382D?logo=redis&logoColor=white)](https://redis.io/)

<br />

A modern blog system with three subsystems: a content platform (articles, collections, comments), an AI Q&A assistant with RAG over your own articles, and an admin-only study journal API.

**[Live Demo](https://somehow007.top)**

</div>

---

## Subsystems

| Subsystem | Backend | Database | API prefix |
|-----------|---------|----------|------------|
| **Blog** | `io.github.somehow.mysite.{controller,service,dao}` | MySQL `t_*` (14 tables) | `/v1/*` |
| **RAG chat** | `io.github.somehow.mysite.ragent` | PostgreSQL + pgvector (13 tables) | `/v1/rag/*` |
| **Study journal** | `io.github.somehow.mysite.journal` | MySQL `sj_*` (6 tables) | `/api/journal/*` (ADMIN only) |

Article publish/update events flow into the RAG pipeline asynchronously via Spring Events — the blog module never calls embedding directly.

---

## Features

### Content Management

- **Rich Markdown Editor** — Milkdown Crepe WYSIWYG (Typora/Notion-style), LaTeX math (KaTeX), Callout blocks, slash menu, paste/drag image upload
- **Article Collections** — Ordered series with drag-to-sort, prev/next article navigation, per-visitor visibility rules
- **Categories & Tags** — Hierarchical category tree with SEO fields; tag filtering
- **Article Import** — Bulk import TXT/Markdown documents (UTF-8)
- **Draft System** — Draft/published states for work-in-progress content

### AI Q&A (RAG)

- **Streaming Chat** — SSE streaming with `meta → sources → content → done/error` events, conversation history, rename/delete
- **Knowledge Bases** — Per-KB document ingestion (articles auto-synced + manual uploads), Markdown-aware chunking, pgvector HNSW retrieval + rerank
- **Intent Classification** — Cheap-model intent routing (KB retrieval vs. general chat); retrieval scope is user-selected KBs
- **Multi-Provider LLM** — OpenAI-compatible routing with priority fallback + circuit breaker (DeepSeek primary, BaiLian fallback; Ollama/SiliconFlow/AIHubMix optional)
- **LLM Cost Tracking** — Token usage, provider settings, model pricing, holiday schedules, balance snapshots
- **Rate Limits** — ADMIN unlimited, CREATOR 20/h, USER 10/h

### Reading Experience

- **Full Markdown Rendering** — Marked + PrismJS highlighting + KaTeX + Callouts
- **Table of Contents** — Auto-generated sticky TOC from headings
- **Reading Time** — Smart estimation (Chinese chars, English words, code, images)
- **10 Built-in Themes** — 经典白/黑、极光紫夜、玫瑰花园、海风青韵、暖阳珊瑚、Liquid Glass 亮/暗、纸本昼/夜
- **Scroll Progress / Back to Top**

### Social & Interaction

- **Comments** — Threaded replies, guest or authenticated, Gravatar, admin review workflow, likes (user + IP dedup)
- **Favorites** — Bookmark articles with optimistic UI
- **User Following** — Follow/unfollow, follower/following lists

### Search

- **Dual Engine** — Elasticsearch (IK Chinese tokenizer) with automatic DB LIKE fallback; toggle via `elasticsearch.enabled`
- **Quick Search Dialog** — Debounced global search with keyboard shortcut

### Administration Dashboard

- Post editor, image manager (upload / URL fetch), user management, comment moderation, collection/category/tag management, site config, LLM cost dashboard

### Security

- **JWT Dual Token** — Access (24h) + Refresh (7d) with seamless refresh
- **Spring Security** — Stateless filter chain, BCrypt hashing
- **RBAC** — `ADMIN` / `CREATOR` / `USER` roles (legacy `DEVELOPER` maps to `ADMIN`)
- **Upload Security** — Type whitelist, 5MB limit, rate limiting
- **Audit Logs** — Admin operation tracking

### Performance

- **Redis Caching** — Per-entity TTL strategy (categories 2h, tags 1h, articles 30min, collections 30min, homepage 10min)
- **Image Optimization** — Nginx WebP conversion; frontend lazy loading
- **Vite Build** — Manual chunk splitting, ES2020 target, CSS code splitting
- **Logical Deletion** — All `t_*` tables use `del_flag`

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| **Backend** | Spring Boot 3.5.7, Spring Security, MyBatis-Plus, Spring WebFlux (RAG streaming) |
| **Frontend** | Vue 3 (Composition API), TypeScript, Vite 7, Tailwind CSS 4, Pinia |
| **Database** | MySQL 8.4 + PostgreSQL 17 (pgvector) |
| **Cache** | Redis 7 (Lettuce) |
| **Search** | Elasticsearch (optional, DB fallback) |
| **Editor** | Milkdown Crepe 7 (ProseMirror WYSIWYG) |
| **Markdown** | Marked + PrismJS + KaTeX |
| **API Docs** | Knife4j / Swagger UI |
| **Deploy** | Docker Compose, Nginx, Let's Encrypt |

---

## Project Structure

```
mysite/
├── src/main/java/io/github/somehow/mysite/
│   ├── config/                   # Security, Cache, CORS, Async, ES configs
│   ├── controller/               # Blog REST controllers (/v1/*)
│   ├── service/ + impl/          # Business logic
│   ├── dao/                      # MyBatis-Plus entities (14 t_*) + mappers
│   ├── dto/                      # DTOs (Long IDs → string via ToStringSerializer)
│   ├── commons/                  # UserContext, error codes, exceptions, framework
│   ├── security/                 # JWT dual-token auth
│   ├── elasticsearch/            # Optional ES search (DB fallback)
│   ├── journal/                  # Study journal API (/api/journal, ADMIN)
│   └── ragent/                   # ★ RAG subsystem (separate PG datasource)
│       ├── core/                 # Pipeline: PromptTemplate, RetrievalEngine, QueryRewriter, IntentClassifier
│       ├── llm/                  # Multi-provider routing + circuit breaker + embedding + rerank
│       ├── chunking/             # MarkdownChunker
│       ├── ingestion/            # Article events → knowledge documents
│       └── vector/               # PgvectorVectorStore (cosine, HNSW)
├── mysite-frontend/              # Vue 3 SPA
│   └── src/
│       ├── views/                # Public pages + /dashboard admin
│       ├── components/           # article/, chat/, comment/, collection/, editor/, ui/ ...
│       ├── editor/               # Milkdown editor factory/plugins
│       ├── composables/          # useChat, useMarkdown, useTheme, ...
│       ├── api/                  # axios client.ts + rag.ts (SSE fetch)
│       └── stores/               # Pinia: user, site
├── docker/                       # docker-compose: MySQL 8.4 + Redis 7 + PG17/pgvector
│   └── init/                     # schema.sql, ragent-schema.sql, journal-schema.sql, migrations/
├── deploy/                       # deploy.sh, server-deploy.sh, nginx/, scripts/
├── docs/                         # MAP.md (project map), living docs, archive/ (completed plans)
└── scripts/verify.sh             # One-command verification (quick/full)
```

---

## Getting Started

### Prerequisites

- **Java 17+**, **Node.js 18+**
- **Docker** (MySQL + Redis + PostgreSQL/pgvector via compose)

### 1. Start Infrastructure

```bash
cd docker
docker compose up -d        # MySQL + Redis + PostgreSQL(pgvector)
docker compose --profile ollama up -d   # optional: local Ollama LLM
```

### 2. Configure Backend

```bash
cp deploy/config/.env.example .env     # then edit secrets
# or edit src/main/resources/application.yaml directly for dev
```

### 3. Run Backend & Frontend

```bash
./mvnw spring-boot:run       # API on :8081, Swagger at /swagger-ui.html

cd mysite-frontend
npm install && npm run dev   # dev server :5173, proxies /v1 → 8081
```

### 4. Verify

```bash
./scripts/verify.sh          # quick: backend hermetic tests + frontend type-check
./scripts/verify.sh full     # full: needs docker infra, all tests + lint + build
```

### 5. Production Build

```bash
./mvnw clean package -DexcludedGroups=needs-infra   # same gate as deploy.sh
cd mysite-frontend && npm run build
# or one-click:
./deploy/deploy.sh
```

---

## Configuration Highlights

| Key | Description | Default |
|-----|-------------|---------|
| `server.port` | API port | `8081` |
| `spring.datasource.*` | MySQL (blog + journal) | `127.0.0.1:3306/mysite` |
| `rag.datasource.*` | PostgreSQL (RAG, separate pool) | env-configurable |
| `jwt.*` | Access 24h / Refresh 7d | — |
| `elasticsearch.enabled` | ES search on/off | `false` |
| `rag.llm.providers.*` | LLM provider priority/enabled | DeepSeek→BaiLian |
| `image.upload.*` | Upload dir / 5MB limit | `./uploads/images` |

Frontend proxies `/v1/` → `localhost:8081`; production is served by Nginx with `/v1/` reverse proxy, HTTPS, WebP conversion, SPA fallback.

---

## API Overview

REST under `/v1/` (+ `/v1/rag/*`, `/api/journal/*`). Interactive docs: **Swagger UI** at `/swagger-ui.html`.

| Module | Endpoints | Notes |
|--------|-----------|-------|
| Auth | `/v1/auth/login` `/register` `/refresh` `/logout` | JWT |
| Articles | `/v1/articles` CRUD, favorites, archive, import | |
| Comments | `/v1/comments` | threaded, likes |
| Collections | `/v1/collections` | series + ordering |
| Categories / Tags | `/v1/categories` `/v1/tags` | |
| Users | `/v1/users` follow/avatar | |
| Images | `/v1/images` | upload, URL fetch |
| Admin | `/v1/admin/*` | users, comments, site config, LLM cost |
| RAG | `/v1/rag/chat/stream` (SSE), `/v1/rag/knowledge-bases`, `/v1/rag/conversations` | login required |
| Journal | `/api/journal/*` | ADMIN only, `user_id` isolated |

Module-level detail: `docs/分类模块API文档.md`; error codes: `docs/error-code-reference.md`.

---

## Database Schema

**MySQL — blog (14 `t_*`, all `del_flag` logical delete):**
`t_user, t_article, t_category, t_tag, t_article_tag, t_collection, t_collection_article, t_comment, t_comment_like, t_image, t_site_config, t_user_follow, t_user_article_favorites, t_user_operation_log`

**MySQL — journal (6 `sj_*`, no del_flag):**
`sj_day_record, sj_learning_item, sj_custom_mood` (daily) + `sj_goal, sj_goal_task, sj_goal_checkin` (monthly goals)

**PostgreSQL + pgvector — RAG (13 tables):**
`t_knowledge_base, t_knowledge_document, t_knowledge_chunk, t_knowledge_vector(1024,HNSW), t_conversation, t_conversation_message, t_rag_intent` + LLM billing/ops: `t_llm_usage, t_llm_provider_setting, t_llm_model_price, t_llm_holiday, t_llm_seed_marker, t_llm_balance_snapshot`

Schema init: `docker/init/schema.sql`, `ragent-schema.sql`, `journal-schema.sql`, `data.sql`, `migrations/` (idempotent, re-runnable).

---

## Deployment

- `docker/docker-compose.yml` — MySQL 8.4 + Redis 7 + PG17/pgvector (+ optional Ollama profile)
- `deploy/deploy.sh` — one-click build & package (runs hermetic tests)
- `deploy/server-deploy.sh` — server-side deploy
- `deploy/scripts/` — `start.sh`, `monitor.sh`, `convert-webp.sh`, `lib.sh`
- `deploy/nginx/mysite.conf` — HTTPS (Let's Encrypt), `/v1/` + `/journal/` proxy, WebP, SPA fallback
- Env: `deploy/config/.env.example` → `.env` (secrets never committed)

See `docs/DEPLOYMENT_GUIDE.md` for the full ops guide.

---

## Documentation

- `docs/MAP.md` — **authoritative project map** (modules, entry points, data flow)
- `docs/DESIGN.md` — decision/change log
- `docs/DEPLOYMENT_GUIDE.md` — ops guide
- `docs/分类模块API文档.md` / `docs/error-code-reference.md` — API & error references
- `docs/ragent/RAG问题与方案笔记.md` — living RAG notes
- `docs/archive/` — completed plans & design docs (rag/, collections/, frontend/ v1–v4, features/, ops/)
- `AGENTS.md` / `CLAUDE.md` — engineering conventions for AI agents

---

<div align="center">

**[Back to Top](#mysite)**

</div>
