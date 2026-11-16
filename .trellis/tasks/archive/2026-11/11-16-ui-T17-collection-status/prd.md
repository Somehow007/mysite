# UI 重构 T17：合集连载状态与进度

> 任务定义见 `.trellis/tasks/11-15-ui-concepts/prd.md` T17 节（§2.4 schema 评审类，需先评审）。
> 对照稿：`final/home.html` 连载合集侧栏（`N / P 篇 · 连载中|已完结` + 3px 进度条）。
> 本文件记开工结论、评审决策与验收结果。

## 背景与现状核对（2026-11-16）

- `t_collection` 现有列：`article_count`（成员计数）、`visibility`、`sort_order` 等，无 planned/status。
- 合集管理接口安全面**不变**：`POST/PUT/DELETE /v1/collections/**` 已 `hasRole("ADMIN")`，
  GET 两个端点 `permitAll` —— 本任务不新增/放开任何端点，§2.2 不触发。
- `CollectionPageQueryRespDTO.articleCount` / `CollectionDetailRespDTO.articleCount` 是
  **访问者可见口径的动态统计**（私有文章对非作者不计），不是 `t_collection.article_count` 存储列。
  → plannedCount 的进度分子沿用这个动态口径：作者/管理员看到全量，游客看到公开部分（与现状一致）。
- `CollectionMapper.xml selectCollectionsPage` 显式列清单，需补 `c.planned_count, c.status`；
  `GROUP BY c.id` 靠主键函数依赖，新增 `c.*` 列不影响。
- `CollectionServiceImpl.updateCollection` 逐字段「null=不改」；`collectionMapper.updateById`
  走 MyBatis-Plus 默认 `NOT_NULL` 策略 —— **null 字段不会写库**，清除 planned_count 需要专用 UPDATE
  （仿 `updateArticleCount` 的 `@Update` 写法）。
- 缓存：`CACHE_HOME/CACHE_DETAIL` 等 key 不变；旧缓存条目缺新字段 → 前端按 `undefined` 兜底，TTL 自然过期，可接受。
- 前端消费点（`Collection` 类型 + articleCount 展示）：
  - `components/home/HomeSidebar.vue`（首页侧栏，T6 落地，当前只显示 `N 篇`）
  - `views/CollectionsView.vue`（/collections 行内卡，非 CollectionCard）
  - `components/collection/CollectionCard.vue`（UserProfileView 合集 tab）
  - `views/CollectionView.vue`（合集详情头 `N 篇文章`）
  - `views/CollectionManageView.vue`（后台列表 `N 篇`）
  - `views/CollectionEditView.vue`（后台编辑表单 —— 本次加字段）
  - `DashboardView.vue` 合集选择弹窗不展示计数，不动。
- `ArticleNavInfoRespDTO` / `ArticleSelectRespDTO` 的合集徽标（第 N/M 篇）不在本任务范围。

## 评审决策（待 owner 拍板）

见文末「评审记录」。默认建议：

1. `planned_count` NULL → 显示 `N 篇 · 连载中/已完结`，**不画进度条**（无分母）。
2. `articleCount > plannedCount` → 数字照实 `N / P 篇`，进度条封顶 100%。
3. `status=已完结` 与 `planned_count` **无 DB 约束**：完结可不设计划（纯手动标记）。
4. 更新语义：`plannedCount` 区分「不传=不改 / 显式 null=清除 / ≥1=设置」（Jackson setter 存在位标记）；
   `status` 沿用 null=不改。`@Min(1)` 拦截 0/负数。

## 改动清单（评审通过后执行）

### DB（§2.4）

- 新增 `docker/init/migrations/20261116-collection-status.sql`：
  `ALTER TABLE t_collection ADD COLUMN planned_count INT NULL … AFTER article_count,
   ADD COLUMN status TINYINT NOT NULL DEFAULT 0 … AFTER planned_count`（手动执行，新库走 schema.sql）。
- `docker/init/schema.sql` `t_collection` 同步两列（article_count 之后）。

### 后端

- `CollectionDO` +`plannedCount`、`status`（Integer，javadoc 注释口径）。
- `CollectionCreateReqDTO` +`plannedCount`（@Min(1)）、`status`（@Min(0)@Max(1)）；
  `CollectionUpdateReqDTO` 同两字段，plannedCount 带 provided 标记（手写 setter + @JsonIgnore flag）。
- `CollectionPageQueryRespDTO` / `CollectionDetailRespDTO` +两字段；XML select 补两列。
- `CollectionMapper` +`updatePlannedCount(id, plannedCount)`（@Update，null → 置 NULL）。
- `CollectionServiceImpl`：
  - create：BeanUtil 自动带字段；`status` null→0、`plannedCount` null→null 兜底。
  - update：`status` 非空则写 existing；`plannedCount` provided 时走 `updatePlannedCount`
    （null=清除，≥1=设置），不走 updateById。
- 测试：`CollectionServiceImplTest` 补 create/update 新字段用例（hermetic）。

### 前端

- `types/index.ts`：`Collection` +`plannedCount?: number | null`、`status?: number`；
  Create/UpdateRequest 同步（update 的 plannedCount 允许显式 null=清除）。
- `utils/format.ts` +`collectionProgress()`：返回 `{count, planned, finished, percent}`，
  percent = planned>0 ? min(100, round(count/planned*100)) : null。
- `HomeSidebar.vue`：`N 篇` → `N / P 篇 · 状态` + 3px 进度条（概念稿 `.bar`：bg-border 轨 + accent 填充）。
- `CollectionsView.vue` / `CollectionCard.vue` / `CollectionView.vue` / `CollectionManageView.vue`：
  计数文案与状态徽标同步（合集详情头加进度条）。
- `CollectionEditView.vue`：表单加「计划篇数」（number, min 1, 空=null）+「连载状态」
  （连载中/已完结 分段按钮，仿可见性）；编辑时回填，保存时 update 恒带两字段。

## 验收

- `./scripts/verify.sh`（quick）+ `npm run lint && npm run build`。
- Playwright：首页侧栏三种形态（有计划连载中/有计划完结/无计划）、/collections、合集详情、
  后台表单与列表；paper-light/dark + classic-light 不掉色；横向溢出 0。
- 迁移 SQL 在本地 docker MySQL 手动执行验证（幂等性：重复执行报 duplicate，可忽略——沿用现有 migration 惯例）。

## 评审记录（2026-11-16 owner 拍板）

1. `planned_count` NULL → **只显篇数+状态**：`N 篇 · 连载中/已完结`，不画进度条。
2. `articleCount > plannedCount` → **照实显示** `N / P 篇`，进度条封顶 100%。
3. `status=已完结` 与 `planned_count` → **无 DB 约束、纯手动**（owner 授权由实现方定）：
   已完结可不设计划，亦可是提前完结（count < planned 也允许标完结）；不派生、不联动校验。
4. 更新语义（实现细节，已定）：`plannedCount` 区分「不传=不改 / 显式 null=清除 / ≥1=设置」，
   `status` 沿用 null=不改；`@Min(1)` 拦 0/负数。

## 实现记录（2026-11-16）

- DB：`docker/init/migrations/20261116-collection-status.sql` + `schema.sql` 同步两列。
  **注意**：本机 `127.0.0.1:3306` 有两个 mysqld——brew 本地实例优先于 docker 映射，
  应用实际连的是本地库；迁移已对**两个库都执行**（docker 容器那份顺手保持一致）。
- 后端按清单落地；`updatePlannedCount` 仿 `updateArticleCount` 的 `@Update` 写法，
  显式 null → `planned_count = NULL`（updateById NOT_NULL 策略写不了 null）。
- 前端：`format.ts` +`collectionProgress`/`formatCollectionCount`/`formatCollectionProgress`；
  HomeSidebar（文案+3px 进度条）、CollectionsView/CollectionCard（badge+状态字）、
  CollectionView（头部文案+进度条）、CollectionManageView（文案+已完结 chip）、
  CollectionEditView（连载计划输入+状态分段钮）。
- 测试：`CollectionServiceImplTest` +7 用例（create 两字段/默认值、update 三种 plannedCount 语义、
  status、Jackson presence flag 反序列化三态）。

## 踩坑

- **`<input type="number" v-model>` 运行时值是 number 不是 string**：空值为 `''`/`null`，
  `.trim()` 直接 TypeError 导致保存静默失败（Playwright F 段抓到）。已改 `string | number` 联合类型 + 归一化。
- 路由守卫 `requiresAdmin` 读 localStorage `user_role`（UX 层），注入 token 测后台页须连带注入。

## 验收记录

- `./scripts/verify.sh`（quick）绿；`npm run lint` 15 基线 0 新增；`npm run build` 过。
- `CollectionServiceImplTest` 28/28 绿（含新 7 例；单独跑会撞存量 MP lambda cache 顺序依赖，
  与 VisibilityTest 同跑或全量下正常——存量问题非本次引入）。
- 真实 E2E（本地 mysqld + 新后端 8081 + dev 5173）：
  - API：PUT plannedCount=9/status=0、status=1、plannedCount=null 清除、字段缺省不改、POST 创建，全部符合契约；GET 列表/详情返回新字段。
  - `scripts/t17-verify.mjs` Playwright **41/41**：三主题首页侧栏文案+进度条宽度（33%/100%）、
    /collections、合集详情（匿名口径 3/9=33%）、移动端 390、后台管理列表（含无计划「0 篇 · 连载中」）、
    编辑表单回填+保存→API 复核→还原、溢出=0、console 0 error。
- 数据现状（开发库，供 owner 查看）：Spring Boot 系列 planned=9/连载中；前端技术精选 planned=1/已完结。

## 观察项（非本次改动，记录备查）

- `articleCount` 口径分裂是**存量行为**：列表页 SQL 动态统计可见文章数；合集详情 admin 走
  `BeanUtil` 存储列 `article_count`（含草稿/私有成员），匿名才修正为可见数 → 同一合集
  admin 详情显示 5/9、匿名/列表显示 3/9。进度条随各自口径，与既有 articleCount 展示一致。
  → **已由 T18 修复**（`11-16-ui-T18-collection-count-consistency`）：详情计数统一为
  「存在性过滤后可列出成员数」，admin/列表/匿名同口径。
- 工作树有大量并行在途改动（T14 已提交 d11f02f；WebSecurityConfig/ragent/chat 等其他任务未提交），
  本任务**未提交**，commit 由阶段评审统一处理。

## Resolution

2026-11-16 验收完成，功能与 E2E 全绿（见 §验证）。

二次走查（`scripts/t17-walkthrough.mjs`，mock 数据 DOM 级复核）**51/51**：
五形态文案（连载中 3/9、已完结 6/6、无计划 4 篇、超计划照实 12/9、0 篇 0/5）、
进度条宽度（33%/100%/0%、超计划封顶非 133%、无计划不渲染）、详情 max-w-2xl 栏宽、
编辑表单三态（回填 9 → 切已完结 → 清空计划 → PUT body 显式 `plannedCount:null`）、
移动端 390 / paper-dark 溢出=0、状态钮 button 可聚焦、无计划行无空轨道。
修正断言 2 处（列表卡为「计数+独立状态 chip」非连字符全文、计划输入框用 placeholder 定位）。

走查中确认并另行修复 `articleCount` 口径分裂 → 立 T18（本会话内完成）。
收尾：本机 docker 影子实例（mysite-mysql/redis/pgvector）已停删，开发统一用 brew 服务（MAP.md §4 已记）。
