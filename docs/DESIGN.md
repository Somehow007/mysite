# MySite 项目设计文档

## 更新日志

### 2026-07-28: RAG 检索质量修复（rerank 空 query + 双阈值过滤）

> 更新于 2026-07-28：修复 AI 回答引用不相关来源（甚至 34% 相关度也被引用）的问题。三个叠加根因：① rerank 精排调用传空字符串 query，cross-encoder 失去 query↔doc 相关性判据、形同虚设；② 向量粗排阈值 0.3 对中文 embedding 过宽松（无关主题实测也有 0.3~0.4 分）；③ rerank 的 relevance_score 替换向量分后无二次过滤，top_n 要几条给几条。

#### 决策内容

1. **query 透传**：`RetrievalEngine.rerank()` 增加 query 参数，单问题透传用户问题、多子问题用全部子问题拼接，让精排真正按问题相关性重排。
2. **双阈值关卡**：向量粗排阈值 `score-threshold` 0.3 → 0.5；新增 `rerank-score-threshold: 0.5`，精排后按 relevance_score 二次过滤。分数语义在链路中会变（向量分 → 精排分），每一段都必须有自己的阈值。
3. **阈值依据实测分布**：不相关档 0.1~0.37、相关档 0.65+，0.5 恰好落在两档之间的空档区。
4. **回归验证**：Phase3 集成测试新增 Part 3（真实百炼 API live 测试）——相关问题来源从 7 条收敛到 1 条（0.9643），无关问题（红烧肉）零来源，集成链路 rerank 结果与直接 API 调用基准完全一致。全量 279 测试通过。

#### 影响范围

- `ragent/core/RetrievalEngine.java`、`ragent/config/RagProperties.java`、`application.yaml`、`Phase3IntegrationTest.java`；详细根源分析与实测数据见 `docs/ragent-integration-log.md`（2026-07-28 条目）

### 2026-07-28: 管理表格长标题挤压修复（table-layout: fixed 标准实践）

> 更新于 2026-07-28：修复知识库文档表格中"文章标题过长挤掉状态/操作列"的问题，全站管理表格统一切换到 `table-layout: fixed`。

#### 决策背景

现象：知识库文档表格里文章标题一长，后面几列就被挤变形（甚至表头逐字换行）。此前 e9734d1 已给标题加过 `truncate + :title`，但没根治——因为 `DataTable` 的 `<table>` 用浏览器默认的 `table-layout: auto`：该模式下 `width` 只是"建议值"，浏览器按内容重新分配列宽，而 `white-space: nowrap` 单元格的"理想宽度"等于全文长度，长标题把整列撑宽、挤压其余列。truncate 只在单元格宽度被硬性约束时才生效，auto 布局下恰恰没有约束。

#### 决策内容

1. **切换到业界标准做法**（Ant Design / Element Plus / TanStack Table 同款）：`<table class="table-fixed">`（`table-layout: fixed`）。列宽只由表头 `width` 声明决定，内容无法把列撑宽；第一个不设 `width` 的列自动吸收剩余宽度作为**弹性列**。
2. **全站约定**（已写入 `Column` 接口注释）：每张表留一个弹性列（通常是标题/名称列），弹性列内的长文本单元格必须自行 `min-w-0 + truncate + :title`（原生 tooltip，悬浮显示全称——零依赖、稳定、无障碍友好）。
3. **4 个使用方全部排查**（知识库文档 / 仪表盘文章 / 用户管理 / 标签管理），均为「弹性首列 + 其余列显式定宽」的健康模式，全局切换安全；用户、标签两处的弹性列原本没有截断处理，一并补齐（长用户名、长标签名现在也正常收敛）。

#### 经验沉淀

表格长文本溢出的判断顺序：① 先看 `table-layout`——auto 模式下给单元格加 truncate 是无效的（列宽先被内容撑开）；② `fixed` 模式下 truncate 三件套是 `min-w-0`（flex 父容器）+ `truncate`（ellipsis）+ `:title`（悬浮全称）；③ flex 布局里 inline 元素天然是弹性项，但要显式 `min-w-0` 才允许收缩到内容宽度以下（flex item 默认 `min-width: auto`）。

#### 影响范围

- `mysite-frontend/src/components/ui/DataTable.vue`（table-fixed + 接口语义注释）、`views/UserManageView.vue`、`views/TagManageView.vue`（弹性列截断补齐）

### 2026-07-28: SPA 部署后仍渲染旧版——index.html 缓存策略修复

> 更新于 2026-07-28：修复"线上重新部署后，页面（如知识库管理文档表格）仍显示旧版本"的问题。根因是 index.html 无缓存头被浏览器启发式缓存，叠加 hash 资源 immutable 1y，旧版整套从磁盘缓存渲染、不碰网络。

#### 决策背景

现象：服务器侧一切正常（仓库 HEAD 最新、`/var/www/mysite` 是当天新构建、产物里 grep 得到修复标记），但浏览器看到的仍是旧 UI。排查路径（可复用）：

1. **先排除服务端**：服务器 `git log -1` 确认代码 → `ls -la /var/www/mysite` 确认文件时间戳 → 在部署产物 chunk 里 grep 修复提交的标记字符串（本次是 `truncate`）→ 三者都对，说明部署链路没问题。
2. **再看缓存头**：`curl -sI https://域名/` 发现 index.html **没有任何 `Cache-Control`**，只有 ETag/Last-Modified；而 assets 是 `expires 1y; Cache-Control: public, immutable`。
3. **根因**（RFC 7234 启发式缓存）：响应无显式新鲜度信息时，浏览器允许按"文件年龄的 ~10%"启发式缓存，期间**连协商请求都不发**。于是旧 index.html（缓存）→ 引用旧 hash chunk（immutable 1y，也在缓存）→ 整套旧应用从磁盘缓存本地组装，网络层零流量。部署多少次都无效，必须强制刷新。

#### 决策内容

1. **缓存策略改为 SPA 行业标准**：HTML 永不缓存（每次协商）、hash 资源永久缓存。在 443 与 8080 两个 server 块的 `location /` 内加：
   ```nginx
   location = /index.html {
       add_header Cache-Control "no-cache" always;
   }
   ```
   `no-cache` ≠ 不缓存：浏览器会存副本但每次带 ETag 协商，未变返回 304（开销极小），变了拿新版——**部署即时生效，访客无需手动刷新**。hash 资源的 `immutable 1y` 保持不变（文件名含 hash，内容变则名变，长缓存永远安全）。
2. **精确匹配而非正则**：刻意用 `location = /index.html` 而不是 `location ~* \.html$` —— nginx 正则 location 会全局参与匹配，`\.html$` 会抢走 `/journal/index.html` 的内部重定向，把子应用 fallback 劫持到博客根目录。精确匹配只命中入口 HTML。（踩点：nginx 嵌套 location 在内部重定向后会重新参与全局匹配，嵌套层级不限制正则候选。）
3. **服务器已验证**：`/` 与 SPA 深链（`/dashboard`）返回 `no-cache`；assets 仍 `max-age=31536000, immutable`；journal 深链兜底 200 且无 no-cache 头。旧配置备份于 `mysite.conf.bak.20260728`。

#### 经验沉淀（可复用的排查清单）

"部署了但线上还是旧的"按此顺序排查，多数停在第 2 步：

1. 服务端是否真新：`git log -1` + web 根目录文件 mtime + 产物内 grep 修复标记
2. 入口 HTML 缓存头：`curl -sI https://域名/`，无 `Cache-Control` 即中招；有则看是不是被设成了长缓存
3. CDN/反代层：有 CDN 时还需刷 CDN 缓存（本站无 CDN，Nginx 即边缘）
4. 浏览器层：前 3 步都正常才让用户 Cmd/Ctrl+Shift+R 或换无痕窗口

通用原则：**凡是"文件名不含内容 hash"的入口文件（index.html、robots.txt、SW），一律 `no-cache`；含 hash 的资源才配 `immutable` 长缓存。** 二者是同一策略的两半，缺一半就出本次这种问题。

#### 影响范围

- `deploy/nginx/mysite.conf`（443 与 8080 两个 server 块）、服务器 `/etc/nginx/sites-available/mysite.conf`（已 reload 生效）

### 2026-07-28: WebP 副本生成平台兼容性修复（Apple Silicon 原生库告警）

> 更新于 2026-07-28：消除本地（Apple Silicon）每次上传图片都报 `生成WebP副本失败 ... UnsatisfiedLinkError` 的 WARN 刷屏。

#### 决策背景

`org.sejda.imageio:webp-imageio:0.1.6` 捆绑的原生库仅含 x86_64（macOS/Linux/Windows）。Apple Silicon（arm64）JVM 上 dlopen 提取出的 x86_64 dylib 失败抛 `UnsatisfiedLinkError`，且该错误发生在实际写入触发原生库加载时——原代码每次上传都走到写入、每次都被 `catch(Throwable)` 兜住并 WARN，日志刷屏。上传本身不受影响（原图正常落盘入库），失败的只是 WebP 压缩副本优化（生产 Linux x86_64 正常，Nginx 依赖 `.webp` 副本做内容协商）。

#### 决策内容

1. **一次性探测 + 缓存**：`ImageServiceImpl.isWebpWriterAvailable()` 用 1×1 像素真实试写触发原生库加载，结果缓存到静态字段（double-checked locking）。探测必须用真实写入——仅查 writer 注册发现不了 dlopen 失败。
2. **不支持平台静默跳过**：arm64 macOS 等环境后续上传不再生成 WebP、不再告警；首次探测时输出一次 WARN（含原因）。生产 x86_64 探测通过 → 输出 INFO，行为与之前完全一致。
3. 未更换依赖（存在 arm64 支持的替代库，但 WebP 副本是锦上添花，探测跳过方案零风险且跨平台通用）。

#### 影响范围

- `src/main/java/io/github/somehow/mysite/service/impl/ImageServiceImpl.java`

### 2026-07-28: 本地 PG 表权限修复（t_rag_intent permission denied）

> 更新于 2026-07-28：修复本地开发环境 AI 聊天报 `permission denied for table t_rag_intent` 的问题。

#### 决策背景

本地 5432 端口跑的是 macOS 本地 PostgreSQL（Homebrew，超级用户 `somehow`），不是 Docker 容器。ragent 库的 7 张表全部由 `somehow` 创建并持有（owner），应用以非超级用户 `ragent` 连接。旧 6 张表早期手动 GRANT 过 `ragent` 全量 DML 权限，但 **GRANT 是快照、对授权之后新建的表不追溯**——Phase 6 的 `t_rag_intent` 在授权之后创建，`ragent` 对它无任何权限，意图识别查询直接报错。

#### 决策内容

1. **补授权**：`GRANT SELECT, INSERT, UPDATE, DELETE, TRUNCATE, REFERENCES ON TABLE t_rag_intent TO ragent`，与其余 6 张表权限对齐。
2. **防复发**：`ALTER DEFAULT PRIVILEGES FOR ROLE somehow IN SCHEMA public GRANT ... ON TABLES/SEQUENCES TO ragent` —— 今后由 `somehow` 在 public schema 新建的表/序列自动授权给 `ragent`，彻底终结"新表又没权限"的循环。
3. **种子补齐**：本地 `t_rag_intent` 缺第 5 条「全局检索」种子（schema 文件有 5 条），已按 `docker/init/ragent-schema.sql` 补齐。
4. **生产无此问题**：生产 PG 跑在 Docker 内，`POSTGRES_USER: ragent` 即超级用户且是所有表的 owner，不存在跨用户授权问题。

#### 影响范围

- 本地 PG `ragent` 库权限（无代码改动）；同类问题的排查模式可复用：`permission denied for table X` → 查 `pg_tables.tableowner` 与 `\dp X`，表 owner 与应用连接用户不一致即为此类问题。

### 2026-07-28: 文章编辑器图片上传功能恢复 + 上传链路加固

> 更新于 2026-07-28：修复"写文章时无法上传/粘贴图片"的问题，并对整条上传链路（前端编辑器 → API → 后端服务 → Nginx）做健壮性排查与加固。

#### 决策背景

2026 年早些时候 CodeMirror 编辑器出现问题，commit `7bf272b` 临时将写文章页面切换到精简版 `SimpleMarkdownEditor`（纯 textarea）。切换时图片上传能力没有迁移过来：工具栏无上传按钮、无粘贴/拖拽处理，导致写文章时完全无法上传图片。同时排查发现后端上传服务存在两处健壮性隐患（Redis 故障导致上传整体不可用、粘贴图片空文件名触发 DB NOT NULL 约束 500）。

#### 决策内容

1. **精简版编辑器补齐图片上传能力**（与 MarkdownEditor 行为对齐）：
   - 工具栏「图片」按钮：文件选择器上传，支持多选（JPEG/PNG/GIF/WebP/SVG，≤5MB）；
   - 编辑框内**粘贴图片**（截图/复制的图片）自动上传；
   - **拖拽图片**到编辑区上传（带拖入高亮遮罩）；
   - 上传成功后在光标处插入 `![alt](url)` Markdown 语法，支持多文件并发上传 + 工具栏进度显示 + toast 错误提示。
2. **后端 `ImageServiceImpl` 健壮性加固**：
   - Redis 限流降级：`checkUploadRateLimit` 捕获 Redis 异常并放行（限流是保护措施，缓存故障不应导致上传整体不可用）；
   - 文件名兜底：粘贴/截图图片可能无文件名，`original_name` 为空时生成 `pasted_image_<timestamp><ext>`，超长文件名截断到列宽内；
   - `uploaderId` 安全解析：非数字 userId 归为 0（匿名），不中断上传。
3. **链路排查结论（无需改动）**：Nginx（仓库 + 生产）已配置 `client_max_body_size 6m` 与 `/uploads/` 静态别名；Spring multipart 限制 6MB/12MB；`GlobalExceptionHandler` 已覆盖 `MaxUploadSizeExceededException` / `MultipartException`；生产 `application-production.yml` 的 `base-path` 与 Nginx 别名一致（`/data/mysite/uploads/images`）。
4. **测试同步修复**（此前测试编译已整体失败，部署脚本以 `-Dmaven.test.skip=true` 绕过）：RAG 相关测试补齐 `chat(..., kbIds)` / `search(..., List<Long>)` 签名变更；`KnowledgeDocumentService` 系测试补 `kbMapper.selectById` mock 并将"自动创建默认 KB"用例改为验证当前"无启用 KB → 跳过同步"行为；限流测试对齐 USER 10 次/小时阈值；文章删除测试改经 `UserRole.fromAuthority("ROLE_DEVELOPER")` 验证废弃角色映射。`./mvnw test` 恢复全绿（276 通过 / 3 按设计跳过）。

#### 影响范围

- `mysite-frontend/src/components/editor/SimpleMarkdownEditor.vue`（核心修复）、`mysite-frontend/src/views/PostEditorView.vue`（提示文案）
- `src/main/java/io/github/somehow/mysite/service/impl/ImageServiceImpl.java`
- `src/test/java/.../ragent/{Phase2EndToEndTest, Phase3IntegrationTest, service/RagChatServiceTest, service/KnowledgeDocumentServiceTest, service/ChatRateLimiterTest, vector/PgvectorVectorStoreTest}.java`、`src/test/java/.../service/impl/ArticleServiceImplDeleteTest.java`

### 2026-07-28: AI 聊天移动端体验优化（底部 sheet + 视口基建）

> 更新于 2026-07-28：移动端聊天面板由全屏抽屉改为底部 sheet，并新建全站 visualViewport 视口基建，解决"打开即全屏、键盘弹起遮住对话"的体验问题。

#### 决策背景

移动端真机使用反馈：① 聊天面板打开即吞掉全屏，压迫感强；② 软键盘弹起后输入框与对话被遮挡（iOS Safari 键盘弹起时布局视口不收缩，而面板高度基于布局视口）。调研确认根因：原设计有意采用全屏抽屉（见 ragent-frontend-design.md）；`open()` 无条件聚焦 textarea 导致打开即弹键盘；全项目无 visualViewport 代码。

#### 决策内容

1. **移动端形态改为底部 sheet**（用户确认）：顶部留 24px 缝隙、圆角 + 拖拽把手、下滑超阈值关闭；桌面端 520×680 右下角面板不变。属局部体验优化，不改变"移动端重构不在本期"的范围边界（见 2026-07-27 手帐条目）。
2. **视口基建**：新增 `useVisualViewport` composable（visualViewport API → `--vvh` CSS 变量 + `vvHeight` ref），面板高度 = `min(100dvh - 24px, var(--vvh) - 8px)`，键盘弹起时面板实时压缩到键盘上方；viewport meta 追加 `interactive-widget=resizes-content`（Android Chrome 108+ 生效，iOS 由 visualViewport 兜底）。该基建为后续所有 fixed 弹层的键盘适配提供统一方案。
3. **输入体验**：移动端打开不自动聚焦；textarea 移动端 16px（防 iOS 聚焦缩放）+ `enterkeyhint="send"` + safe-area 底边距；消息列表 `overscroll-contain` + 遮罩 touchmove 拦截防滚动穿透。

#### 影响范围

- `mysite-frontend/index.html`、`src/composables/useVisualViewport.ts`（新增）、`src/components/chat/ChatWidget.vue`、`src/components/chat/ChatInput.vue`、`docs/ragent-frontend-design.md`

### 2026-07-27: 生产环境 RAG 数据源配置修复（PG 用户名不一致）

> 更新于 2026-07-27：定位并修复线上 RAG 模块全线报错（知识库列表/新建、聊天记录"消失"）。

#### 决策背景

线上报"新建知识库报错、原有知识库与聊天记录消失"。排查结论：**数据未丢失**（PG 内仍有 1 个知识库 / 46 文档 / 334 chunks / 11 会话 / 24 消息），根因是连接认证失败——`application.yaml` 中 RAG 数据源 `username` 在 commit `960cfd9`（2026-07-26）被误改为本地开发用户 `somehow`（此前 `530b969` 已修过一次，属二次复发），重新部署后后端以 `somehow` 连接 PG，报 `FATAL: password authentication failed for user "somehow"`，所有 RAG 接口 500，前端表现为数据消失。

#### 决策内容

1. **配置环境变量化**：`application.yaml` 的 RAG 数据源用户名改为 `${RAGENT_PG_USER:ragent}`，与 docker-compose 的 `POSTGRES_USER: ragent` 默认一致；本地开发若用别的 PG 用户，通过环境变量 `RAGENT_PG_USER` 覆盖，**禁止再直接改文件提交**。
2. **生产配置显式覆盖**：`deploy/config/application-production.yml` 新增 `rag.datasource` 段（host/port/db/user/password 全部环境变量化，默认 ragent/ragent123），使生产配置不再依赖 jar 内默认值，杜绝本地开发配置泄漏到生产。
3. **服务器热修**：同步追加 `/opt/mysite/application-production.yml` 的 `rag.datasource` 段并 `start.sh restart`，无需重新打包即恢复。
4. **PG 密码对齐**：修完用户名后暴露出第二层问题——PG 数据卷初始化时用的是 `docker/.env` 里的强密码 `Shragent123!`（`POSTGRES_PASSWORD` 仅在卷首次初始化时生效，与 compose 默认值 `ragent123` 无关）。已通过 `ALTER USER` 将 DB 密码统一为 `Shragent123!`，并同步到生产 yml；`/opt/mysite/.env` 已有该值，新 jar 的 `${RAGENT_PG_PASSWORD:...}` 占位符会自动取到。

#### 影响范围

- `src/main/resources/application.yaml`、`deploy/config/application-production.yml`、服务器 `/opt/mysite/application-production.yml`

### 2026-07-27: 学习手帐第二期后端化实施

> 更新于 2026-07-27：第二期（后端化 + 跨设备同步）开发与本地验证完成，待部署上线。

#### 决策背景

第一期已把纯前端手帐挂到 `/journal/` 子路径；第二期按《网站集成与 MySQL 存储方案 v2.0》执行后端化，解决第一期遗留的两个核心限制：数据困在各自浏览器（不跨设备）、URL 无服务端鉴权。

#### 决策内容

1. **范围边界**：本期 = v2.0 的 P0/P1（后端）+ P2 数据层切换 + P3/P4（迁移与部署）。v2.0 §6 体验清单中的 v4.0 视觉重写、字体本地化、移动端重构**不在本期**（属独立的前端重开专项，另立阶段）。
2. **数据库**：mysite 库新增 `sj_day_record` / `sj_learning_item` / `sj_custom_mood`（v2.0 §3 DDL 原样落地）。与博客建表惯例的差异均为 v2.0 定稿内容：**硬删除 + 外键级联**（无 `del_flag`，手帐删除是真实删除语义）、BIGINT Unix 毫秒时间戳（与前端 `number` 对齐）、`date` 为 CHAR(10) 字符串（全程禁止时区转换）。
3. **后端**：新增顶层包 `io.github.somehow.mysite.journal`（controller/service/dao/dto，MyBatis-Plus 主数据源；`@MapperScan` 追加 `journal.dao.mapper` 绑定主库 `sqlSessionFactory`）。API 前缀 `/api/journal`；响应统一 `Result<T>` 包装（data 段与前端 TS 类型逐字段对齐——v2.0「裸 JSON」示例相应调整为全站一致风格）；`user_id` 一律从博客 JWT（`UserContext`）解析，禁止前端传参；`/api/journal/**` 仅 ADMIN 可访问。未采用 v2.0 的 `default-user-id` 兜底配置——博客认证链已完整，直接接真实用户。upsert 为事务内 patch 语义（learnings 传则全量替换）；`/import` 兼容 v1/v2 格式、按 `(user_id,date)` 幂等。
4. **前端**：新增 `http.ts`（Bearer token 取自同源 `localStorage['mysite_access_token']`、Result 解包、401 跳 `/login`、写操作遇 5xx/网络错误自动重试一次）+ `api.ts`（与 `db.ts` 同名同签名）+ `journalEvents` 事件总线 + `useApiQuery`（替代 dexie `useLiveQuery` 的响应式查询）。9 个页面/组件切换数据源，页面代码对存储切换无感；`useDataIO` 改走服务端导入导出，并新增「迁移本地旧数据」一键入口（读旧 IndexedDB → POST /import，幂等可重复）；App 启动门控由「IndexedDB 就绪」改为「登录态检查」；dev 端口改 5174（避让博客 5173）+ `/api → localhost:8081` 代理。`db.ts` 保留不删，仅供迁移读取。
5. **Nginx**：零改动——`location /api/` 反代第一期就已存在，这正是当初选子路径方案的核心收益。

#### 影响范围

- 后端：`journal/` 包 20 个新文件；`MysiteApplication`（@MapperScan）、`WebSecurityConfig`（/api/journal/** ADMIN 规则）、`ErrorCode`（A11 段）；`docker/init/schema.sql` 追加 + `docker/init/journal-schema.sql` 新增
- 手帐：`src/lib/{http,api,journalEvents,useApiQuery}.ts` 新增；App.tsx、moodUtils、useDataIO、6 个页面、MoodEditModal、vite.config.ts 修改

### 2026-07-26: 学习手帐（花期 Blossom）集成方案确定

> 更新于 2026-07-26：与用户讨论后确定集成路线，第一期已实施。

#### 决策背景

学习手帐项目（`study-journey/`，React 18 + Vite 独立前端，当前数据存浏览器 IndexedDB）需要集成进已部署的博客站点（`somehow007.top`）。讨论了三种 URL 方案：

| 方案 | 形态 | 结论 |
|------|------|------|
| 子路径 | `somehow007.top/journal/` | ✅ **采用** |
| 子域名 | `journal.somehow007.top` | ❌ 放弃 |
| 独立端口 | `IP:8082` | ❌ 放弃 |

#### 决策内容

1. **URL 形态：子路径 `somehow007.top/journal/`**
   - 理由：与手帐《网站集成与 MySQL 存储方案 v2.0》§7.3 的推荐一致；无需变更 DNS、无需扩容 SSL 证书（现有 Let's Encrypt 证书自动覆盖）、无 CORS 与跨域 cookie 问题；**同源**为第二期后端化（`sj_` 表 + 复用博客 JWT 登录态）铺平道路。
   - 放弃子域名的理由：需要新增 DNS A 记录、`certbot --expand` 扩容证书、后端 CORS 放行，且 localStorage 按源隔离导致博客登录态无法直接共享，与 v2.0 方案"同源请求携带登录态"的核心假设冲突。

2. **入口可见性：仅 ADMIN 可见**
   - 博客顶栏（AppHeader）通过 `userStore.isAdmin` 条件渲染「手帐」链接，桌面端与移动端菜单同步。
   - 当前阶段数据存各自浏览器 IndexedDB，URL 暴露不泄露数据；第二期后端化后由服务端 `user_id` 隔离做真正鉴权。

3. **分两期实施**
   - **第一期（2026-07-26 实施 ✅）**：手帐按现状（纯前端 IndexedDB 版）部署到 `/journal/`；手帐侧 `vite.config.ts` 设 `base: '/journal/'`、`BrowserRouter basename="/journal"`、按集成文档 §7.4 移除 PWA 注册；博客侧 Nginx（443 与 8080 两个 server 块）新增 `location /journal/ { alias /var/www/journal/; try_files ... }`；产物部署至服务器 `/var/www/journal/`。
   - **第二期（待定）**：按《网站集成与 MySQL 存储方案 v2.0》执行后端化——MySQL 新增 `sj_` 前缀 3 张表、后端新增 journal 模块、前端数据层由 IndexedDB 切换为 `/api/journal` 同源接口，实现跨设备同步与真实用户隔离。

#### 影响范围

- `study-journey/vite.config.ts`、`src/main.tsx`、`index.html`：子路径构建改造
- `mysite-frontend/src/components/common/AppHeader.vue`：管理员入口
- `deploy/nginx/mysite.conf` + 服务器 `/etc/nginx/sites-available/mysite.conf`：`/journal/` location

### 2026-06-22: 管理页面排序和筛选功能优化

#### 问题描述
- 文章管理页面内容显示不全，缺少关键信息
- 所有管理页面都是默认排序，无法多维度定位数据
- 缺少筛选功能，管理效率低下

#### 解决方案
对所有管理页面进行全面的排序和筛选功能增强：

##### 1. DashboardView (文章管理)
- **筛选功能**:
  - 关键词搜索: 支持按标题、内容、作者三种方式搜索
  - 状态筛选: 全部状态、已发布、草稿
  - 分类筛选: 从分类列表中选择
  - 重置功能: 一键清空所有筛选条件

- **排序功能**:
  - 排序字段: 创建时间、浏览量
  - 排序方向: 升序/降序切换

- **分页功能**:
  - 完整的分页组件: 页码按钮、上一页/下一页
  - 显示文章总数

- **信息展示优化**:
  - 封面图: 显示文章封面缩略图
  - 摘要: 显示文章摘要（限制2行）
  - 作者信息: 显示作者名
  - 统计数据: 显示浏览量、收藏数、阅读时长
  - 状态标签: 已发布(绿色)、草稿(黄色)

##### 2. CommentManageView (评论管理)
- **新增排序功能**:
  - 排序字段: 创建时间、点赞数、回复数
  - 排序方向: 升序/降序切换

##### 3. UserManageView (用户管理)
- **新增排序功能**:
  - 排序字段: 注册时间、用户名、最后登录时间
  - 排序方向: 升序/降序切换

##### 4. ImageManagerView (图片管理)
- **新增排序功能**:
  - 排序字段: 创建时间、文件大小、文件名
  - 排序方向: 升序/降序切换

##### 5. CollectionManageView (合集管理)
- **新增排序功能**:
  - 排序字段: 创建时间、标题、文章数
  - 排序方向: 升序/降序切换

#### 技术实现
- 前端: Vue 3 + TypeScript + Tailwind CSS
- 后端: Spring Boot + MyBatis-Plus，完全支持所有筛选和排序参数
- API: 前端 API 层已完整支持 keyword, searchType, categorySlug, published, sortField, sortOrder 等参数

#### 验证结果
- ✅ 前端类型检查: vue-tsc --noEmit --pretty 通过
- ✅ 前端构建: vite build 成功
- ✅ 后端编译: mvnw compile 成功
- ✅ 所有管理页面的排序和筛选功能正常工作

#### 影响范围
- DashboardView.vue: 289 行修改
- CommentManageView.vue: 33 行修改
- UserManageView.vue: 38 行修改
- ImageManagerView.vue: 38 行修改
- CollectionManageView.vue: 58 行修改
- 总计: 385 行新增，71 行删除

#### 优势
1. **多维度定位**: 支持通过多个条件快速定位到目标数据
2. **灵活排序**: 支持按不同字段排序，升序/降序可切换
3. **管理效率**: 大幅提升管理页面的操作效率
4. **用户体验**: 界面直观，操作简单
5. **信息完整**: 文章卡片信息丰富，便于快速识别
