# MySite 项目地图（接管摸底版）

> 生成：2026-11-12，阶段 0 摸底（只读，未改代码/未装依赖）。
> 每条结论附文件证据；标注「未实测」= 从源码读出、本会话未运行验证。
> 代码与文档冲突时以代码为准。风险明细见 `.trellis/tasks/archive/2026-11/11-12-takeover-survey/risks.md`。

## 0. 30 秒定位

个人博客 + RAG AI 问答 + 学习手帐三合一的 **Spring Boot 单体后端**（8081），
配 **Vue 3 主站前端**（`mysite-frontend/`，dev 5173 代理 `/v1`→8081），
以及**独立仓库**的手帐 `../study-journey`（平级，前端+剥离中后端，部署到 `/journal/`）。
持久层：MySQL（博客+手帐）/ PostgreSQL+pgvector（RAG）/ Redis（缓存+限流）/ Elasticsearch（可选搜索）。

## 1. 仓库布局

| 路径 | 角色 | 证据 |
|---|---|---|
| `src/main/java/io/github/somehow/mysite/` | 后端单体，295 个 .java | `find src/main/java -name '*.java' \| wc -l` = 295 |
| `src/main/resources/application.yaml` | 唯一环境配置（无 profile 文件）；含明文密钥，见风险 P0-1 | `ls src/main/resources/` 仅 application.yaml + mapper/ |
| `src/main/resources/mapper/` | 8 个 MyBatis XML，全部 `#{}` 参数化（无 `${}` 拼接） | `ls mapper/`、`grep '\${'` 无命中 |
| `src/test/java/` | 40 个测试类；**无 src/test/resources** → 测试复用 application.yaml 连真实本地库 | `find src/test -name '*.java'` |
| `mysite-frontend/` | 主站 Vue3+TS+Vite+Pinia，127 个 src 文件、25 个 view | `find mysite-frontend/src` |
| `../study-journey`（平级兄弟仓库，2026-11-15 起不再嵌套） | 独立 git 仓库（remote=Somehow007/study-journel.git），手帐完整仓库（前端+剥离中后端+规范），规范以其自身 `AGENTS.md` 为准 | `git -C ../study-journey remote -v` |
| `docker/` | docker-compose（MySQL8.4/Redis7/PG17+pgvector，ES/ollama 未启用或注释）+ init SQL | `docker/docker-compose.yml`、`docker/init/` |
| `docker/init/` | schema.sql(19 表) + journal-schema.sql(7 表) + ragent-schema.sql(9 表) + data.sql(种子) + migrations/ | `grep -c 'CREATE TABLE'` 实测 |
| `deploy/` | 构建打包 + 生产配置 + nginx + 运维脚本 | `deploy/deploy.sh`、`server-deploy.sh`、`config/application-production.yml`、`nginx/mysite.conf` |
| `docs/` | 20+ 设计/验证文档，**17 份未提交**（untracked） | `git status` |
| `.cursor/rules/*.mdc` | Cursor 规则 5 份（架构/后端/前端/journal/ragent） | `ls .cursor/rules/` |
| `CLAUDE.md` | Claude Code 项目说明（多处数据已漂移，见 §6） | `CLAUDE.md` |
| `.claude/settings.local.json` | 工具权限白名单，无密钥 | 已读 |
| `login-test.html` | 根目录遗留手工测试页，硬编码 testuser2/123456 | `git ls-files` 追踪中 |
| `target/`、`uploads/`、`node_modules/` | 产物/上传/依赖目录，均已 gitignore | `git check-ignore` 确认 |

## 2. 子系统与入口

三个业务子系统共用单体进程，**数据源互不混用**（博客/手帐=MySQL，RAG=独立 PG 数据源 `RagentDataSourceConfig`）。

| 子系统 | 代码位置 | API 前缀 | 数据 | 证据 |
|---|---|---|---|---|
| 博客 | `controller/`(11) + `service/`(16 impl) + `dao/`(26) | `/v1/*` | MySQL `t_*` | `ls controller/`、`docker/init/schema.sql` |
| RAG 问答 | `ragent/`（81 文件：llm/core/vector/chunking/ingestion/service/dao） | `/v1/rag/*` | PG+pgvector | `find ragent -type d` |
| 手帐 | `journal/`（47 文件） | `/api/journal/*` | MySQL `sj_*`（7 表） | `journal/controller/`、`WebSecurityConfig:170`（hasRole ADMIN） |

入口：
- 后端 `MysiteApplication.java`；启动即跑 `RagentSchemaMigration` 对 PG 做幂等 DDL（`ALTER/CREATE IF NOT EXISTS`，刻意设计，注释自述理由）。
- 前端 `mysite-frontend/src/main.ts` → `app/router/index.ts`（25 视图）。
- 手帐前端 = `study-journey` 独立应用，不经 mysite-frontend（`grep journal mysite-frontend/src` 无命中），nginx `location /journal/ → /var/www/journal/`（`mysite.conf:60`）。

## 3. 数据流（主干）

- **认证**：`AuthController`（/v1/auth/login|register|refresh）→ JWT 双 token；`JwtAuthenticationFilter` → `UserContext`（TransmittableThreadLocal）；前端 axios `client.ts` 注 `Authorization: Bearer`。注册 DTO 无 role 字段 → 服务端默认 USER（`RegisterReqDTO` + `AuthServiceImpl.register:99-134`，`BeanUtil.toBean` 全量拷贝是脆弱点，见风险）。
- **安全门**：`WebSecurityConfig` 逐条 matcher 控制；`/actuator/**`、`/swagger-ui/**`、`/v3/api-docs/**`、`/uploads/**` 等 permitAll，`/v1/rag/chat/stream` 与 `/v1/rag/conversations/**` 均需登录（WebSecurityConfig authorizeHttpRequests）。
- **文章→RAG**：发布走 Spring Event（`ragent/ingestion/`）→ 分块（MarkdownChunker）→ embedding → `t_knowledge_vector`（pgvector HNSW）。
- **RAG 问答 SSE**：`GET /v1/rag/chat/stream` → RagChatService：限流(Redis `ChatRateLimiter`)→会话/历史→QueryRewriter→IntentClassifier→检索+Rerank→`Flux<ChatEvent>` 流式（meta→sources→content×N→done/error）→存库。会话归属用 `visitorId`（浏览器 UUID，`user_id` 列预留未启用）。
- **图片**：`POST /v1/images/upload`(multipart) 与 `/v1/images/upload-url`（服务端抓图，有 `checkSsrf` 但只查初始 URI）→ `./uploads/images`（生产 `/data/mysite/uploads`）→ nginx alias 直出 + webp 转换（`mysite.conf:33-58`）。
- **统一响应**：`commons/framework/result/Result` + `GlobalExceptionHandler`（validation/登录/上传超限/兜底均有 handler）。

## 4. 外部依赖

| 依赖 | 用途 | 配置点 |
|---|---|---|
| MySQL 8.4 | 博客+手帐 | `docker-compose.yml`、`application.yaml`（⚠ 明文密码） |
| Redis 7 | 缓存（CacheConfig 按名 TTL）+ RAG 限流 | `CacheConfig.java`、`ChatRateLimiter.java` |
| PostgreSQL 17+pgvector | 仅 RAG，独立 DataSource | `RagentDataSourceConfig`、`rag.datasource` |
| Elasticsearch | 可选，默认关（`elasticsearch.enabled:false`），回退 DB LIKE | `ElasticsearchConfiguration`、mapper LIKE 用 `CONCAT('%',#{kw},'%')` 参数化 ✓ |
| LLM：DeepSeek(P1,启用)/BaiLian(P2,启用+embedding+rerank)/SiliconFlow(P3,关)/AIHubMix(P4,关)/Ollama(P9,关) | RAG 聊天与向量化，OpenAI 兼容 + 熔断(2连败熔断,30s冷却) | `rag.llm.providers.*`、`ragent/llm/` |
| Maven 依赖 | SB 3.5.7（⚠ CLAUDE.md 写 3.0.7，已漂移）/ MyBatis-Plus 3.5.14 / jjwt 0.12.6 / WebFlux(WebClient) / hutool / fastjson2 / knife4j | `pom.xml` |
| npm 依赖 | Vue3.5 / Pinia3 / axios / marked18 / dompurify / Milkdown Crepe 7.21 / KaTeX / Prism / tailwind4；**playwright 已装但无配置无测试（死依赖）** | `package.json` |

> ⚠ 本机开发实际用 **brew 服务**：`mysql@8.4`（3306，多项目共享 ~15 库，勿删）、`redis`、`postgresql@17`。docker 容器（mysite-mysql/redis/pgvector）与 brew 端口冲突属影子实例，2026-11-16 已停删；`docker-compose.yml` 保留给干净机器/生产语义。迁移须同时灌 brew 库（`mysql -h127.0.0.1`）。

## 5. 命令现状（逐条核对）

| 命令 | 状态 | 证据/注意 |
|---|---|---|
| `cd docker && docker compose up -d` | 存在，未实测 | compose 三服务；MySQL/PG 端口绑 `0.0.0.0`，Redis 绑 `127.0.0.1` |
| `./mvnw spring-boot:run` | 存在 | 需先起 MySQL/Redis（+RAG 需 PG） |
| `./mvnw compile` | 存在 | pom 无 profile；`target/` 有近期构建产物 |
| `./mvnw test` | ⚠ 不闭环 | 40 测试：~23 个 Mockito 单测可跑；**5 个 `@SpringBootTest` 需真实 MySQL/PG/ES/Redis**（无 test resources、无 testcontainers）→ 依赖 docker 起全量，可能写真实库 |
| `cd mysite-frontend && npm run dev` | 存在 | vite 5173，代理 /v1→8081 |
| `npm run build` | 存在 | = `vue-tsc --build` + `vite build` |
| `npm run type-check` | 存在 | vue-tsc --build |
| `npm run lint` | 存在 | oxlint+eslint `--fix`（注意：lint 默认改文件，摸底期慎用） |
| 前端单测 | **不存在** | 无 vitest/jest 脚本；playwright 装了没用过 |
| `./deploy/deploy.sh` | 存在 | `mvnw clean package -DskipTests`（⚠ 部署构建跳测试）+ 前端 build + 打 tar.gz |
| `./deploy/server-deploy.sh` | 存在 | 服务器侧；另拉 `~/project/study-journey` 部署手帐 |
| `./mvnw clean package -Pproduction` | ⚠ **文档漂移** | CLAUDE.md 写的 profile 在 pom.xml 不存在；生产配置走 `deploy/config/application-production.yml` 外挂 |
| CI | **不存在** | 无 `.github/`、无任何流水线 |
| 备份/运维 | 存在未实测 | `deploy/backup-mysql.sh`、`deploy/scripts/{monitor,convert-webp,start,lib}.sh` |

## 6. 文档景观与新鲜度

- `CLAUDE.md`：最全但已漂移——Spring Boot 版本、表数量（13 vs 实测 19）、手帐表数（3 vs 7）、`-Pproduction` 均过期。
- `.cursor/rules/*.mdc`：架构/后端/前端/journal/ragent 四份，编码约定可靠（snowflake→string、Result 封装、user_id 隔离已抽查属实）。
- `docs/`：20+ 篇设计/验证文档，17 篇未提交；`CHANGELOG.md` 最新条目 2026-07-29（与近 3 个月 RAG/手帐大改不符，已停更）。
- 无 AGENTS.md、无 .trellis、无 MAP（本文件为首版）。

## 7. 部署形态

- 生产：jar 于 8081，nginx :8080（HTTPS）反代 `/v[0-9]/` 与 `/api/` → 8081；`/journal/` alias `/var/www/journal/`（study-journey 构建产物）；`/uploads/` alias 直出。
- 生产配置外置 `deploy/config/application-production.yml` + 服务器 `/opt/mysite/.env`（不入 git）；但 **JWT 默认值兜底与仓库内密钥一致**，见风险 P0。
- 打包：`deploy.sh` 本地产出 `mysite-deploy.tar.gz`（含 jar+dist+start.sh+prod yml+nginx conf）。

## 8. 怪点速查（详细排序见 risks.md）

公开仓库含真实密钥 · CorsFilter 回显任意 Origin+credentials 且忽略 yaml 白名单 · actuator/swagger 生产公开 · 登录无限流 · SSRF 重定向绕过 · SVG 同源存储型 XSS 面 · 测试不闭环+部署跳测试 · study-journey 嵌套 repo · 39 项未提交改动 · 前端零单测+playwright 死依赖 · 文档漂移。
