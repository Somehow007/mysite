# 学习手帐（花期 Blossom）集成日志与说明

> 本文档记录学习手帐项目（`study-journey/`）集成进 MySite 博客的决策、实施过程、服务器现状与运维方法。
> 创建：2026-07-26 ｜ 状态：**第一期已上线** ✅ ／ **第二期已上线并验收（2026-07-27）** ✅

---

## 1. 背景

学习手帐「花期」是一个独立的 React 18 + Vite 前端项目（仓库 `git@github.com:Somehow007/study-journel.git`），最初是纯前端 SPA，数据存浏览器 IndexedDB。目标是把它挂到已部署的博客站点（`https://somehow007.top`）上，入口**仅对管理员可见**。

手帐项目自身的规范文档（位于 `study-journey/` 目录内）：

- `🌸 学习手帐 · 花期 Blossom — 开发设计文档 v5.0.md` — 视觉与交互规范
- `📔 学习手帐 — 网站集成与 MySQL 存储方案 v2.0.md` — 后端化集成方案（第二期依据）

## 2. 决策记录（2026-07-26）

### URL 形态：采用子路径 `somehow007.top/journal/`

| 方案 | 形态 | 结论 | 关键理由 |
|------|------|------|----------|
| 子路径 | `somehow007.top/journal/` | ✅ **采用** | 零 DNS/证书/CORS 成本；同源，为第二期复用博客登录态铺路；与手帐集成方案 v2.0 §7.3 的推荐一致 |
| 子域名 | `journal.somehow007.top` | ❌ 放弃 | 需新增 DNS A 记录、`certbot --expand` 扩容证书、后端 CORS 放行；localStorage 按源隔离导致博客 JWT 登录态无法共享，与 v2.0「同源请求携带登录态」假设冲突 |
| 独立端口 | `IP:8082` | ❌ 放弃 | URL 不美观、无 HTTPS、需开安全组端口 |

### 入口可见性：仅 ADMIN

- 博客顶栏通过 `userStore.isAdmin` 条件渲染「手帐」链接（桌面端 + 移动端菜单）。
- 当前阶段手帐数据存在各自浏览器 IndexedDB 中，URL 泄露不会暴露任何数据；第二期后端化后由服务端 `user_id` 隔离做真正鉴权。

### 实施节奏：分两期

- **第一期（已完成 ✅）**：手帐按现状（纯前端 IndexedDB 版）以子路径静态部署，博客加管理员入口。
- **第二期（待定）**：按手帐仓库内《网站集成与 MySQL 存储方案 v2.0》执行后端化——MySQL 新增 `sj_` 前缀 3 张表、后端新增 journal 模块、前端数据层切换为 `/api/journal` 同源接口，实现跨设备同步。

## 3. 架构总览

```
浏览器
  │
  ├─ https://somehow007.top/            → 博客（Vue 3 SPA，/var/www/mysite）
  │     └─ 顶栏「手帐」入口（仅 ADMIN 可见，原生 <a href="/journal/">）
  │
  └─ https://somehow007.top/journal/    → 手帐（React 18 SPA，/var/www/journal）
        ├─ vite base='/journal/'，BrowserRouter basename='/journal'
        ├─ 数据（第二期）：同源 /api/journal → Spring Boot(8081) journal 模块 → MySQL sj_ 表
        │    └─ 登录态复用博客 JWT（localStorage['mysite_access_token']），服务端按 user_id 隔离
        └─ 浏览器 IndexedDB 降级为迁移兜底（设置页「迁移本地旧数据」读取后不再使用）

Nginx (443/HTTPS，证书由 Certbot 管理，自动覆盖子路径)
  ├─ location /journal/ { alias /var/www/journal/; try_files … }   ← 手帐
  ├─ location /        { try_files … /index.html; }                ← 博客 SPA
  └─ location ~ ^/v[0-9]/ 与 /api/  →  proxy_pass localhost:8081   ← 后端
```

## 4. 第一期实施记录（2026-07-26）

### 4.1 本地代码改动

**手帐仓库（`study-journey/`，3 个文件）：**

| 文件 | 改动 |
|------|------|
| `vite.config.ts` | 加 `base: '/journal/'`；移除 VitePWA 插件（集成方案 §7.4：子路径下 SW 作用域易与主站冲突）；dev server 自动打开 `/journal/` |
| `src/main.tsx` | `<BrowserRouter basename="/journal">` |
| `index.html` | 清理 PWA 相关 meta（favicon 等绝对路径由 Vite 按 base 自动重写） |

**博客仓库（mysite，3 个文件）：**

| 文件 | 改动 |
|------|------|
| `mysite-frontend/src/components/common/AppHeader.vue` | 桌面导航与移动端菜单各加「手帐」入口，`v-if="userStore.isAdmin"`；用原生 `<a>` 而非 RouterLink（避免被 vue-router 拦截） |
| `deploy/nginx/mysite.conf` | 443 与 8080 两个 server 块各加 `location /journal/`（见 §5.2） |
| `docs/DESIGN.md` | 更新日志追加集成决策条目 |

### 4.2 验证结果

| 检查项 | 命令 | 结果 |
|--------|------|------|
| 手帐构建 | `npm run build`（tsc && vite build） | ✅ 产物路径全部带 `/journal/` 前缀，无 PWA 残留文件 |
| 博客类型检查 | `npx vue-tsc --noEmit --pretty` | ✅ |
| 博客构建 | `npx vite build` | ✅ |
| Nginx 语法 | `sudo nginx -t` | ✅ |
| `/journal/` 首页 | `curl -sk https://somehow007.top/journal/` | ✅ 200，资源引用均为 `/journal/...` |
| SPA 路由 fallback | `curl -sk /journal/stats` | ✅ 200（返回 index.html） |
| 博客主站 / 文章页 / 后端 API | curl | ✅ 全部 200，无回归 |
| 线上 bundle 含入口 | grep DefaultLayout chunk | ✅ 含 `/journal/`，与本地构建产物一致 |

### 4.3 服务器操作流水

1. 备份 Nginx 配置 → `/etc/nginx/sites-available/mysite.conf.bak-20260726`
2. 备份旧博客前端 → `/var/www/mysite.bak-20260726.tar.gz`
3. 上传并替换 Nginx 配置（先 diff 确认服务器版本与仓库版本**仅差新增的 journal 块**，无漂移，才整体覆盖）
4. 手帐 `dist/` → `/var/www/journal/`（`rsync -a --delete`，owner 设为 `www-data`）
5. 博客前端 `dist/` → `/var/www/mysite/`（仅同步静态产物，未动后端服务）
6. `sudo nginx -t && sudo systemctl reload nginx`，线上 curl 全量验证

## 5. 服务器现状说明

### 5.1 服务器上有什么、没有什么

| 路径 | 内容 | 说明 |
|------|------|------|
| `/var/www/journal/` | 手帐**构建产物**（index.html、assets/、book.svg） | 纯静态文件，无源码、无 node_modules |
| `/var/www/mysite/` | 博客前端构建产物 | 同上 |
| `~/project/mysite` | mysite 仓库检出（标准部署脚本 `server-deploy.sh` 的 git pull + 服务器构建流程用） | 手帐源码**不在**这里 |

**结论：服务器上没有任何手帐源码。** 当前模式是「本地构建 → 上传产物 → 服务器只托管静态文件」。

### 5.2 Nginx 改了哪里、改了什么

- **文件**：`/etc/nginx/sites-available/mysite.conf`（通过 `/etc/nginx/sites-enabled/mysite.conf` 软链接启用）。仓库内副本 `deploy/nginx/mysite.conf` 已同步为相同内容。
- **备份**：`/etc/nginx/sites-available/mysite.conf.bak-20260726`。
- **改动**：在 443 HTTPS server 块（`/uploads/` 块之后、`location /` 之前）和 8080 兼容 server 块中，各加入同一段：

```nginx
# 学习手帐（花期 Blossom）子应用：独立构建产物部署于 /var/www/journal/，SPA fallback
location /journal/ {
    alias /var/www/journal/;
    try_files $uri $uri/ /journal/index.html;
}
```

- `alias` 把 URL 前缀 `/journal/` 映射到目录 `/var/www/journal/`；
- `try_files` 最后一项 fallback 到 `/journal/index.html`，保证 React Router 的前端路由（如 `/journal/stats`）刷新不 404；
- 443 与 8080 两块都改是为了让 `IP:8080` 兼容入口也能访问手帐；
- 生效方式：`sudo nginx -t && sudo systemctl reload nginx`（已执行，纯静态文件更新以后无需再 reload）。

## 6. 运维手册

### 6.1 更新手帐（改完手帐代码后）

```bash
# 本地
cd study-journey && npm run build
scp -r dist ubuntu@124.222.65.169:/tmp/journal-dist

# 服务器
ssh ubuntu@124.222.65.169 \
  'sudo rsync -a --delete /tmp/journal-dist/ /var/www/journal/ \
   && sudo chown -R www-data:www-data /var/www/journal'
# 刷新页面即生效，无需 reload nginx
```

### 6.2 更新博客前端

走标准流程（`./deploy/deploy.sh` 打包 → 服务器 `server-deploy.sh`）；若只改前端不想重启后端，可参照 §4.3 步骤 5 手工同步 `dist/`（先 tar 备份）。

### 6.3 改 Nginx 配置

```bash
sudo cp /etc/nginx/sites-available/mysite.conf \
        /etc/nginx/sites-available/mysite.conf.bak-$(date +%Y%m%d)
sudo vim /etc/nginx/sites-available/mysite.conf
sudo nginx -t && sudo systemctl reload nginx   # -t 不过不要 reload
# 记得把改动同步回仓库 deploy/nginx/mysite.conf
```

### 6.4 回滚（完全撤销手帐集成）

```bash
sudo cp /etc/nginx/sites-available/mysite.conf.bak-20260726 \
        /etc/nginx/sites-available/mysite.conf
sudo nginx -t && sudo systemctl reload nginx
sudo rm -rf /var/www/journal
# 博客前端如需回滚：解压 /var/www/mysite.bak-20260726.tar.gz 覆盖 /var/www/mysite
```

## 7. 仓库结构注意事项

- `study-journey/` 目录物理上位于 mysite 工作区内，但它是**独立 git 仓库**（remote：`git@github.com:Somehow007/study-journel.git`，注意仓库名原拼写为 journel）。
- mysite 仓库的 `.gitignore` **未**忽略该目录，但因嵌套 `.git` 的存在，mysite 不会跟踪其内容。⚠️ 在 mysite 根目录执行 `git add .` 时留意不要把 `study-journey/` 作为嵌入式仓库（gitlink）误加入。
- 两个仓库的提交相互独立，按功能模块分别提交。

## 8. 第二期实施记录（2026-07-27，待部署）

依据手帐仓库内《网站集成与 MySQL 存储方案 v2.0》，完成 P0/P1（后端）+ P2（前端数据层）+ 迁移入口。**范围边界**：v2.0 §6 体验清单中的 v4.0 视觉重写、字体本地化、移动端重构不在本期，UI 保持现状只换数据底座。

### 8.1 数据库

mysite 库新增 3 张表（DDL 按 v2.0 §3 原样）：

| 表 | 要点 |
|----|------|
| `sj_day_record` | `(user_id,date)` 唯一索引；`mood` 存预设枚举或自定义心情 nanoid；`diary` TEXT；`created_at/updated_at` BIGINT Unix 毫秒 |
| `sj_learning_item` | 外键 `record_id → sj_day_record.id` **ON DELETE CASCADE**；`client_id` 存前端 nanoid |
| `sj_custom_mood` | nanoid 直接作 VARCHAR(21) 主键；深色三档色存 `dark_colors` JSON 列 |

与博客建表惯例的两处差异（均为 v2.0 定稿）：**硬删除**（无 `del_flag`，手帐删除是真实删除语义）；时间用 BIGINT 毫秒、`date` 用 CHAR(10) 字符串（全程禁止时区转换，否则日记「串天」）。

脚本：`docker/init/schema.sql` 已追加（新环境一次建好）；**存量库用独立迁移脚本** `docker/init/journal-schema.sql`（全部 `CREATE TABLE IF NOT EXISTS`，幂等）。

### 8.2 后端 journal 模块

新增顶层包 `io.github.somehow.mysite.journal`（仿 ragent 的模块边界，但走**主 MySQL 数据源**）：

```
journal/
├── controller/  JournalController        /api/journal/records・/search・/import・/export
│                JournalMoodController    /api/journal/moods/custom
├── service/     JournalService / CustomMoodService（+ impl）
├── dao/entity/  SjDayRecordDO / SjLearningItemDO / SjCustomMoodDO（不继承 BaseDO）
├── dao/mapper/  3 个 BaseMapper（@MapperScan 追加 journal.dao.mapper，绑定主库 sqlSessionFactory）
└── dto/         DayRecordDTO / LearningItemDTO / CustomMoodDTO / DarkColorsDTO / 请求与导入导出 DTO
```

API 清单（响应统一博客 `Result<T>` 包装，**data 段与前端 TS 类型逐字段对齐**，camelCase）：

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/journal/records/{date}` | 单日详情（含 learnings） |
| GET | `/api/journal/records?month=YYYY-MM` / `?year=YYYY` / 无参 | 按月 / 按年 / 全部（date 升序） |
| PUT | `/api/journal/records/{date}` | upsert，patch 语义：mood/diary 传了才更新（mood 空串=清除）；learnings 传则全量替换。事务内完成 |
| DELETE | `/api/journal/records/{date}` | 删整日（条目级联），幂等 |
| GET | `/api/journal/search?keyword=` | 日记 LIKE 搜索，updatedAt 倒序，上限 200 |
| POST | `/api/journal/import` | 兼容 v1 纯数组 / v2 含 customMoods；按 `(user_id,date)` 与心情 nanoid **幂等 upsert** |
| GET | `/api/journal/export` | 导出 v2 格式 JSON（与旧前端导出文件格式一致，可直接回导） |
| GET/POST/PUT/DELETE | `/api/journal/moods/custom[/{id}]` | 自定义心情 CRUD |

**鉴权与用户隔离**：`WebSecurityConfig` 中 `/api/journal/**` → `hasRole("ADMIN")`；所有 service 方法强制带 `user_id` 条件，`user_id` 由服务端从博客 JWT（`UserContext.getUserId()`）解析，**前端不传**。未采用 v2.0 的 `default-user-id` 兜底——博客认证链已完整，直接接真实用户。错误码新增 A11 段（`ErrorCode`）。

**Nginx 零改动**：`location /api/ { proxy_pass http://localhost:8081/api/; }` 第一期就在配置里（当时尚未使用），本期直接复用——这是子路径方案的预期收益。

### 8.3 手帐前端数据层切换

| 文件 | 改动 |
|------|------|
| `src/lib/http.ts`（新） | fetch 封装：Bearer token 取自同源 `localStorage['mysite_access_token']`（与博客 `storage.ts` 格式一致，JSON 字符串）；`Result` 解包；401 → 跳网站 `/login`；写操作遇 5xx/网络错误自动重试一次 |
| `src/lib/api.ts`（新） | 与 `db.ts` **同名同签名**的数据操作（另加 `getAllRecords`）；写操作成功后发变更事件 |
| `src/lib/journalEvents.ts` + `useApiQuery.ts`（新） | 轻量事件总线 + 查询 Hook，替代 dexie `useLiveQuery` 的响应式：任何写操作后挂载中的查询自动重拉，页面体验与 IndexedDB 版一致 |
| 6 个页面 + `moodUtils` + `MoodEditModal` + `Settings` | import 来源 `lib/db` → `lib/api`；`useLiveQuery` → `useApiQuery` |
| `src/lib/useDataIO.ts` | 导出/导入改走服务端；**新增「迁移本地旧数据」入口**：读本浏览器 IndexedDB（`db.ts` 保留的读取能力）→ POST `/import`，幂等可重复 |
| `src/App.tsx` | 启动门控由「IndexedDB 就绪」改为「登录态检查」（无 token 跳 `/login`）；移除 DB 错误页 |
| `vite.config.ts` | dev 端口 5173 → **5174**（避让博客）；新增 `/api → localhost:8081` 开发代理 |

`src/lib/db.ts`（Dexie）**保留不删**，仅供迁移入口读取历史数据。

### 8.4 本地验证结果（2026-07-27）

| 检查项 | 结果 |
|--------|------|
| 后端编译 `./mvnw compile` | ✅ |
| 后端测试 `./mvnw test` | ⚠️ 与改动前基线**完全一致**的失败集（6 个 RAG 测试类 + 1 个 DEVELOPER 角色旧测试，均为改动前已存在的问题，非本期引入）；且发现干净构建下 RAG 测试**源码**编译不过（此前靠增量缓存掩盖）——见 §10 |
| 手帐构建 `npm run build`（tsc + vite build） | ✅ |
| 建表脚本 | ✅ 本地 MySQL 执行通过，3 表结构与 DDL 一致 |
| 本地全链路冒烟（8082 临时实例，admin 真实 JWT） | ✅ upsert + patch 语义、learnings 全量替换、按月查询、搜索、自定义心情 CRUD、导出、导入幂等（两次导入记录数不增）、无 token→401、USER 角色→403、删除后条目级联清零 |
| 冒烟发现并修复的 bug | `importData` 原实现先导记录后导心情，记录引用的自定义心情尚不存在时校验失败导致整批回滚 → **已修复为先导心情后导记录** |

### 8.5 部署执行记录（2026-07-27 已完成）

1. ✅ **建表**：`journal-schema.sql` 经 `docker exec mysite-mysql` 在生产库执行，3 表就位
2. ✅ **后端**：服务器 `~/project/mysite` git pull → `./mvnw clean package -Dmaven.test.skip=true` → jar 拷至 `/opt/mysite/mysite.jar` → `start.sh` 重启（14s 启动完成）。因 RAG 测试源码编译问题（§10），`deploy/server-deploy.sh` 已同步修正为 `-Dmaven.test.skip=true`
3. ✅ **手帐产物**：本地 `npm run build` → scp → rsync 到 `/var/www/journal/`（同第一期 §6.1）
4. ✅ **线上匿名验证**：`/journal/` 与 SPA fallback 200、8080 兼容入口 200、`/api/journal/export` 未登录 401（A070100）、线上 bundle 哈希与本地构建一致、博客首页与 `/v1/site/info` 无回归
5. ✅ **浏览器验收（2026-07-27）**：管理员线上实际使用了心情记录、学习条目、日记（当日数据已落库，见 §8.7），全链路正常。验收过程中暴露并修复了事务管理器事故（§8.6）
6. ⏳ **历史数据迁移（按需）**：若某浏览器第一期 IndexedDB 里还有想保留的旧数据，在该浏览器的手帐设置页点「迁移本地旧数据」（幂等，可重复）；无需迁移则可忽略

> 备注：`/actuator/health` 返回 Result 包装的错误是既有怪癖（ES 等健康指示器异常被全局异常处理器接住），与本期无关；部署脚本的健康检查本就对此有告警兜底。

### 8.6 上线当日事故与热修复（2026-07-27 12:13）

**现象**：手帐页面点击心情、添加学习记录无反应。

**根因**（既有架构缺陷，由手帐首次高频触发）：`ragentTransactionManager`（绑定 PG）曾是容器中唯一的 `PlatformTransactionManager`，Boot 的事务管理器自动配置因此退避，**所有未限定名称的 `@Transactional`（博客主业务 + journal）都被绑到 PG 数据源**；事故当时生产服务器未部署 PG（同日稍后已随 RAG 数据源配置修复一并解决，见 DESIGN.md「2026-07-27: 生产环境 RAG 数据源配置修复」条目），带事务的写操作全部抛 `CannotCreateTransactionException`。前端写操作是 fire-and-forget，错误被静默吞掉，表现为「按钮无反应」。本地冒烟通过是因为本地 PG 在运行。

**修复**（commit `2dc1611`，已重新部署）：`PrimaryDataSourceConfig` 显式声明 `@Primary` 的主库 `DataSourceTransactionManager`；`KnowledgeBaseService` 的 `@Transactional` 显式限定 `ragentTransactionManager`。副作用收益：博客所有带事务的写路径（发文、评论管理等）在未部署 PG 的环境也一并修复。

**教训**：前端 fire-and-forget 写操作必须补「保存失败」提示（v2.0 §6 第 3 项「保存状态可信化」，列为后续打磨项）。

### 8.7 数据存储现状（2026-07-27 起）

**主存储**：服务器 `124.222.65.169` 的 docker 容器 `mysite-mysql`（MySQL 8.4）→ `mysite` 库 → `sj_day_record` / `sj_learning_item` / `sj_custom_mood`。访问链路：浏览器 → 同源 `/api/journal/**`（Nginx 反代）→ Spring Boot journal 模块 → MySQL，服务端从博客 JWT 解析 `user_id` 隔离，仅 ADMIN。

**首批真实数据**（事务事故修复后产生，验证了全链路）：2026-07-27 当日 1 条日记录（心情 + 51 字日记）+ 1 条学习条目（60 分钟），自定义心情 0 个。

**遗留本地数据**：各浏览器 IndexedDB（`StudyJournalDB`）里可能还留着第一期数据，已降级为迁移兜底，仅设置页「迁移本地旧数据」读取。

**备份**（⚠️ 待落实）：`sj_` 表已随网站 MySQL 成为数据的唯一权威副本，但服务器目前**没有系统化的 MySQL 定期备份**。日记从「不出浏览器」变成「只在服务器」后这是新的单点风险（v2.0 §10 已提示）。建议：每日 `docker exec mysite-mysql mysqldump` 全库快照 + 异地留存，待确认后实施。

## 9. 当前阶段的已知限制

| 限制 | 说明 | 计划 |
|------|------|------|
| ~~数据不跨设备~~ | ~~存各自浏览器 IndexedDB~~ | ✅ 第二期已解决（MySQL + /api/journal） |
| ~~URL 无服务端鉴权~~ | ~~仅隐藏入口~~ | ✅ 第二期已解决（/api/journal/** 仅 ADMIN + user_id 隔离） |
| 断网不可用 | 数据上服务端后，离线无法读写（v2.0 §10 已接受的取舍） | 如在意，后续用保留的 Dexie 做「本地优先 + 后台同步」，另立专项 |
| MySQL 备份待落实 | 后端化后服务器 DB 是数据唯一权威副本，目前无系统化定期备份 | 每日 mysqldump 全库快照 + 异地留存（§8.7，待确认后实施） |
| 并发写无版本号 | 同一管理员多设备同时编辑同一天，后写覆盖先写（个人自用场景概率极低） | 有需要时给 upsert 加 updatedAt 乐观锁 |
| 字体走 Google Fonts CDN | 国内偶发加载慢 | 集成方案 v2.0 §6 体验清单第 7 项：字体本地化 |
| 手帐产物未做不可变缓存头 | 仅 ETag 协商缓存，对管理员自用场景无感 | 有需要时在 `location /journal/` 内补静态资源缓存规则 |

## 10. 仓库既有问题备忘（非本期引入）

- **RAG 测试源码与主代码签名漂移**：`RagChatServiceTest` / `Phase3IntegrationTest` / `PgvectorVectorStoreTest` 等 6 个测试类按旧签名编写（如 `RagChatService.chat` 少了 `List<Long>` 参数），干净构建下**测试源码编译不过**；此前 `./mvnw test` 能跑是靠 `target/` 增量缓存（旧 class 运行时报 NoSuchMethod）。另有 `ArticleServiceImplDeleteTest` 一个 DEVELOPER 角色旧测试失败。均为本期改动之前就存在的问题（已用 git stash 基线对比确认）。
- **影响**：后端打包必须跳过测试编译（`-Dmaven.test.skip=true`，注意不是 `-DskipTests`——后者只跳过执行、仍会编译）。部署脚本与 CI 需注意。
- **建议**：单独立项修复 RAG 测试签名，恢复 `./mvnw test` 的干净构建可用性。
