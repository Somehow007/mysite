# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

MySite is a full-featured personal blog platform built with Spring Boot 3.0.7 + Vue 3 + TypeScript + MySQL 8.4 + Redis 7. The backend runs on port 8081, the frontend dev server on port 5173 with proxy to backend.

## Quick Start Commands

### Backend (Spring Boot)

```bash
# Start infrastructure
cd docker && docker compose up -d

# Run development server
./mvnw spring-boot:run

# Build for production
./mvnw clean package -Pproduction

# Run tests
./mvnw test

# Compile check (no tests)
./mvnw compile
```

### Frontend (Vue 3 + Vite)

```bash
cd mysite-frontend

# Install dependencies
npm install

# Development server (port 5173)
npm run dev

# Type check only
npx vue-tsc --noEmit --pretty

# Build for production
npm run build

# Lint (if configured)
npm run lint
```

### Deploy

```bash
# One-click deploy (builds + packages)
./deploy/deploy.sh

# Server-side deploy
./deploy/server-deploy.sh
```

## Architecture

### Backend (Spring Boot 3.0.7)

```
src/main/java/io/github/somehow/mysite/
├── config/          # Spring configs: Security, Cache, CORS, Async
├── controller/      # REST API endpoints (/v1/*)
├── service/         # Business logic
│   └── impl/        # Service implementations
├── dao/             # Data access layer
│   ├── entity/      # MyBatis-Plus entities with logical deletion
│   └── mapper/      # MyBatis mapper interfaces
├── dto/             # Request/response DTOs
├── commons/         # Shared: UserContext, error codes, exceptions
├── security/        # JWT auth, Spring Security filter chain
├── elasticsearch/   # ES integration (optional, with DB LIKE fallback)
└── utils/           # ReadingTimeCalculator, etc.
```

**Key patterns:**
- All database tables use logical deletion (`del_flag`)
- Redis caching with strategy pattern: categories (2h), tags (1h), articles (30min), collections (30min), homepage (10min)
- JWT dual-token auth: access (24h) + refresh (7d)
- Role-based access: Developer (admin) vs User

### Frontend (Vue 3 + TypeScript)

```
mysite-frontend/src/
├── views/           # Page components (routes)
├── components/      # Reusable UI
│   ├── article/     # ArticleCard, ArticleContent, ArticleToc
│   ├── comment/     # CommentSection, CommentItem
│   ├── collection/  # CollectionCard, ArticleNav
│   ├── auth/        # LoginForm, RegisterForm
│   └── common/      # AppHeader, AppFooter, ThemeToggle, SearchDialog
├── composables/     # Vue composables (useMarkdown, useTheme, useSearch)
├── api/             # Axios API layer
├── stores/          # Pinia stores
├── editor/          # CodeMirror 6 markdown editor
├── types/           # TypeScript type definitions
└── utils/           # gravatar, validators, date formatting
```

**Key patterns:**
- Vite build splits: vue-vendor, markdown, ui-vendor, codemirror
- 8 built-in themes with smooth transitions (classic-light/dark, aurora, rose-garden, sea-breeze, warm-coral, liquid-glass-light/dark)
- Markdown rendering: Marked + PrismJS (18 languages) + KaTeX + Obsidian-style Callouts
- API proxy: `/v1/`, `/uploads/`, `/api/` → `http://localhost:8081`

### Database Schema

13 tables with logical deletion across all entities:

```
t_user                    # Users (roles, avatar, follow counts)
t_article                 # Articles (views, favorites, reading time)
t_category                # Hierarchical categories (SEO metadata)
t_tag                     # Tags (many-to-many with articles)
t_collection              # Article collections with ordering
t_comment                 # Nested comments (guest/auth, review workflow)
t_comment_like            # Comment likes (user + IP dedup)
t_image                   # Images (local upload / URL fetch)
t_user_follow             # Follow relationships
t_user_article_favorites  # Article favorites
t_user_operation_log      # Admin audit logs
t_article_tag             # Article-Tag junction
t_collection_article      # Collection-Article junction (sort order)
```

## Development Workflow

### Before Committing (Mandatory)

**Frontend:**
```bash
npx vue-tsc --noEmit --pretty    # Must pass
npx vite build                   # Must pass
```

**Backend:**
```bash
./mvnw compile                   # Must pass
./mvnw test                      # Must pass
```

**Rule: Never commit unverified code. Fix all errors before pushing.**

### Configuration

- **Backend**: `src/main/resources/application.yaml` (database, Redis, JWT, ES, image upload)
- **Frontend**: Vite config at `mysite-frontend/vite.config.ts`
- **Nginx**: `deploy/nginx/mysite.conf` (production with HTTPS, WebP conversion)
- **Deploy**: `.env` file (see `deploy/config/.env.example`)

### API Documentation

Swagger UI available at: `http://localhost:8081/swagger-ui.html` when backend is running.

### Production Deployment

- Backend: `http://localhost:8081` (API server)
- Frontend: Served via Nginx on port 8080 with API reverse proxy
- MySQL 8.4 + Redis 7 via Docker Compose
- Nginx handles HTTPS (Let's Encrypt), WebP conversion, static caching, SPA fallback
