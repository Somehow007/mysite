# MySite 项目设计文档

## 更新日志

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
