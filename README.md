<div align="center">

# MySite

**A full-featured personal blog platform built with Spring Boot 3 + Vue 3**

[![Java](https://img.shields.io/badge/Java-17-ED8B00?logo=openjdk&logoColor=white)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.0.7-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Vue.js](https://img.shields.io/badge/Vue.js-3-4FC08D?logo=vuedotjs&logoColor=white)](https://vuejs.org/)
[![TypeScript](https://img.shields.io/badge/TypeScript-5.9-3178C6?logo=typescript&logoColor=white)](https://www.typescriptlang.org/)
[![MySQL](https://img.shields.io/badge/MySQL-8.4-4479A1?logo=mysql&logoColor=white)](https://www.mysql.com/)
[![Redis](https://img.shields.io/badge/Redis-7-DC382D?logo=redis&logoColor=white)](https://redis.io/)
[![Elasticsearch](https://img.shields.io/badge/Elasticsearch-optional-005571?logo=elasticsearch&logoColor=white)](https://www.elastic.co/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](#license)

<br />

A modern, production-ready blog system featuring article management, collections, full-text search, comments, and a rich Markdown editor with real-time preview.

**[Live Demo](https://somehow007.top)**

</div>

---

## Features

### Content Management

- **Rich Markdown Editor** -- CodeMirror 6-based editor with Obsidian-style live preview, LaTeX math formula rendering (KaTeX), Callout blocks (`[!NOTE]`, `[!TIP]`, `[!WARNING]`, etc.), Enter-key continuation for lists and blockquotes, and auto-conversion shortcuts
- **Article Collections** -- Organize articles into ordered series with drag-to-sort, per-article navigation (previous/next), and dedicated collection pages
- **Categories & Tags** -- Hierarchical category tree with SEO metadata fields; tag-based article filtering
- **Draft System** -- Articles support draft/published states for work-in-progress content

### Reading Experience

- **Full Markdown Rendering** -- Marked + PrismJS syntax highlighting (18 languages) + KaTeX math + Obsidian-style Callout blocks
- **Table of Contents** -- Auto-generated sticky TOC sidebar from article headings
- **Reading Time Estimation** -- Smart calculation accounting for Chinese characters (300/min), English words (225/min), code blocks (200 chars/min), and images
- **Scroll Progress Bar** -- Visual reading progress indicator
- **8 Built-in Themes** -- Classic Light/Dark, Aurora Purple Night, Rose Garden, Sea Breeze Teal, Warm Coral, Liquid Glass Light/Dark -- with smooth transition animations
- **Back to Top** -- Floating button for long articles

### Social & Interaction

- **Comments** -- Threaded/nested replies, guest or authenticated commenting, Gravatar support, admin review workflow
- **Comment Likes** -- Supports both logged-in user and anonymous IP-based deduplication
- **Favorites** -- Bookmark articles with optimistic UI updates and batch status checking
- **User Following** -- Follow/unfollow users, follower/following lists

### Search

- **Dual Search Engine** -- Elasticsearch (IK Chinese tokenizer) for full-text search, with automatic database LIKE fallback when ES is unavailable. Toggle via configuration flag.
- **Quick Search Dialog** -- Debounced (300ms) global search with keyboard shortcut support

### Administration Dashboard

- **Post Editor** -- Full-featured Markdown editor with image upload, collection assignment, category/tag selection
- **Image Manager** -- Upload from file or fetch by URL; JPEG/PNG/GIF/WebP/SVG support; auto WebP conversion at the Nginx layer
- **User Management** -- Role assignment (Developer/User), account enable/disable, operation audit logs
- **Comment Moderation** -- Review, approve, or reject comments
- **Collection Management** -- Create/edit collections, add/remove/sort articles, batch operations
- **Category & Tag Management** -- CRUD with batch operations, drag-to-sort, SEO fields

### Security

- **JWT Dual Token** -- Access token (24h) + Refresh token (7d) with seamless token refresh
- **Spring Security** -- Stateless authentication filter chain, BCrypt password hashing
- **Role-Based Access Control** -- Developer (admin) and User roles with route-level and API-level guards
- **Operation Audit Logs** -- Track admin actions on user accounts
- **Upload Security** -- File type whitelist, size limits (5MB), rate limiting (10/min)

### Performance

- **Redis Caching** -- Multi-strategy TTL cache for categories (2h), tags (1h), article details (30min), collections (30min), homepage (10min)
- **Image Optimization** -- Nginx-side WebP conversion based on `Accept` header; frontend lazy loading via `MutationObserver`
- **Vite Build Optimization** -- Manual chunk splitting (Vue vendor, Markdown libs, UI vendor, CodeMirror); ES2020 target; CSS code splitting
- **Database** -- Connection pool tuning, batch write optimization, logical deletion across all tables

### SEO

- **Dynamic Meta Tags** -- `@unhead/vue` for per-page `<title>`, Open Graph, and description meta
- **Category SEO Fields** -- Dedicated `seo_title`, `seo_description`, `seo_keywords`
- **Semantic HTML** -- Proper heading hierarchy, `<time>` elements, `<article>` tags

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| **Backend** | Spring Boot 3.0.7, Spring Security, MyBatis-Plus 3.5.7 |
| **Frontend** | Vue 3 (Composition API), TypeScript, Vite 7, Tailwind CSS 4 |
| **Database** | MySQL 8.4 |
| **Cache** | Redis 7 (Lettuce) |
| **Search** | Elasticsearch (optional, with DB fallback) |
| **Editor** | CodeMirror 6 with custom Lezer extensions |
| **Markdown** | Marked + PrismJS + KaTeX |
| **API Docs** | Knife4j (OpenAPI 3 / Swagger) |
| **Deploy** | Docker Compose, Nginx, Let's Encrypt |

---

## Project Structure

```
mysite/
├── src/                              # Spring Boot backend
│   └── main/java/io/github/somehow/mysite/
│       ├── config/                   # Security, Cache, CORS, Async configs
│       ├── controller/               # REST controllers (Article, Auth, Comment, Collection, etc.)
│       ├── service/                  # Business logic layer
│       │   └── impl/                 # Service implementations
│       ├── dao/                      # Data access layer
│       │   ├── entity/               # JPA/MyBatis-Plus entities
│       │   └── mapper/               # MyBatis mappers
│       ├── dto/                      # Request/Response DTOs
│       ├── commons/                  # Shared framework (UserContext, error codes, exceptions)
│       └── utils/                    # Utilities (ReadingTimeCalculator)
├── mysite-frontend/                  # Vue 3 frontend
│   └── src/
│       ├── views/                    # Page components
│       ├── components/               # Reusable UI components
│       │   ├── article/              # ArticleCard, ArticleContent, ArticleToc, FavoriteButton...
│       │   ├── comment/              # CommentSection, CommentItem
│       │   ├── collection/           # CollectionCard, ArticleNav
│       │   ├── auth/                 # LoginForm, RegisterForm
│       │   └── common/               # AppHeader, AppFooter, ThemeToggle, SearchDialog...
│       ├── composables/              # Vue composables (useMarkdown, useTheme, useSearch...)
│       ├── api/                      # Axios API layer
│       ├── stores/                   # Pinia stores
│       ├── types/                    # TypeScript type definitions
│       └── utils/                    # Utilities (gravatar, validators, date formatting)
├── docker/                           # Docker Compose (MySQL + Redis)
├── deploy/                           # Deployment scripts & Nginx config
│   ├── scripts/                      # start.sh, monitor.sh, convert-webp.sh
│   ├── nginx/                        # Nginx site config
│   └── config/                       # Production application.yml, .env.example
└── pom.xml                           # Maven dependencies
```

---

## Getting Started

### Prerequisites

- **Java 17+**
- **Node.js 18+**
- **MySQL 8.x**
- **Redis 7.x**
- (Optional) **Elasticsearch 8.x**

### 1. Start Infrastructure

```bash
cd docker
docker compose up -d
```

This starts MySQL and Redis containers.

### 2. Configure the Backend

Copy the environment template and edit as needed:

```bash
cp deploy/config/.env.example .env
```

Then update `src/main/resources/application.yaml` with your database credentials, Redis connection, and JWT secret.

### 3. Run the Backend

```bash
./mvnw spring-boot:run
```

The API server starts on `http://localhost:8081`. API docs are available at `http://localhost:8081/swagger-ui.html`.

### 4. Run the Frontend

```bash
cd mysite-frontend
npm install
npm run dev
```

The dev server starts on `http://localhost:5173` with API proxy to the backend.

### 5. Build for Production

```bash
# Backend
./mvnw clean package -Pproduction

# Frontend
cd mysite-frontend
npm run build
```

Or use the one-click deploy script:

```bash
./deploy/deploy.sh
```

---

## Configuration

### Backend (`application.yaml`)

| Key | Description | Default |
|-----|-------------|---------|
| `server.port` | API server port | `8081` |
| `spring.datasource.url` | MySQL JDBC URL | `jdbc:mysql://127.0.0.1:3306/mysite` |
| `spring.data.redis.host` | Redis host | `localhost` |
| `jwt.secret` | JWT signing secret (256-bit min) | *(set in config)* |
| `jwt.access-token-expiration` | Access token TTL | `86400000` (24h) |
| `jwt.refresh-token-expiration` | Refresh token TTL | `604800000` (7d) |
| `elasticsearch.enabled` | Enable ES search | `false` |
| `image.upload.base-path` | Image upload directory | `./uploads/images` |
| `image.upload.max-file-size` | Max upload size | `5MB` |

### Frontend (Vite)

The frontend dev server proxies `/v1/` requests to `http://localhost:8081`. Production builds are served as static files via Nginx with API reverse proxy.

---

## API Overview

The backend exposes a RESTful API under `/v1/`. Full interactive documentation is available via **Swagger UI** at `/swagger-ui.html` when the server is running.

| Module | Endpoints | Description |
|--------|-----------|-------------|
| **Auth** | `POST /v1/auth/login`, `/register`, `/refresh`, `/logout` | JWT authentication |
| **Articles** | `GET/POST/PUT/DELETE /v1/articles` | CRUD, search, favorites, archive |
| **Comments** | `GET/POST/DELETE /v1/comments` | Threaded comments, likes |
| **Collections** | `GET/POST/PUT/DELETE /v1/collections` | Series management, article ordering |
| **Categories** | `GET/POST/PUT/DELETE /v1/categories` | Hierarchical categories with SEO |
| **Tags** | `GET/POST/PUT/DELETE /v1/tags` | Tag management |
| **Users** | `GET/PUT /v1/users`, `POST /v1/users/avatar` | Profiles, following, avatars |
| **Images** | `GET/POST/DELETE /v1/images` | Upload, URL fetch, delete |
| **Admin** | `/v1/admin/users`, `/v1/admin/comments` | User & comment moderation |

---

## Database Schema

13 tables with logical deletion (`del_flag`) across all entities:

```
t_user                    # Users (roles, avatar, follow counts)
t_article                 # Articles (views, favorites, reading time, draft/published)
t_category                # Hierarchical categories (SEO fields, icon, color)
t_tag                     # Tags
t_article_tag             # Article-Tag (many-to-many)
t_collection              # Article collections
t_collection_article      # Collection-Article (with sort order)
t_comment                 # Comments (nested replies, guest/auth, review status)
t_comment_like            # Comment likes (user ID + IP dedup)
t_image                   # Images (local upload / URL fetch)
t_user_follow             # User follow relationships
t_user_article_favorites  # User article favorites
t_user_operation_log      # Admin operation audit logs
```

---

## Deployment

The project includes production-ready deployment tooling:

- **`docker/docker-compose.yml`** -- MySQL 8.4 + Redis 7 with resource limits
- **`deploy/deploy.sh`** -- One-click build (Maven + npm) and packaging
- **`deploy/server-deploy.sh`** -- Server-side deployment script
- **`deploy/scripts/start.sh`** -- Application start/stop with PID management
- **`deploy/scripts/monitor.sh`** -- Health monitoring
- **`deploy/nginx/mysite.conf`** -- Nginx config with HTTPS (Let's Encrypt), API reverse proxy, static asset caching, WebP conversion, and SPA fallback

Production environment variables are configured via `.env` file (see `deploy/config/.env.example`).

---

## License

This project is licensed under the [MIT License](LICENSE).

---

<div align="center">

**[Back to Top](#mysite)**

</div>
