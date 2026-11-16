# D5 细节打磨（2026-11-16）

> 总档：`.trellis/tasks/11-16-dash-redesign/prd.md`。前置：D1–D4 已合入（用到 D1 的 `UserAvatar`、D2 的作用域 CSS 文件）。
> 范围：破图兜底、移动端操作收敛、空状态。每项独立，逐项提交也可以（同一个 commit 也行，但 message 要列清）。

## 1. 破图兜底

### 1.1 文章表封面（`views/DashboardView.vue` 约 :489 桌面、:534 移动）

现状：`v-if="item.coverImage"` 只判断有没有 URL，外链失效时渲染一个空米色块，移动端还把标题挤歪（基线截图 `paper-light-mobile-posts.png` 首卡）。

```ts
const brokenCovers = reactive(new Set<string>())   // 或 ref(new Set()) + 替换赋值，按文件现有风格
```

- 两处容器都改 `v-if="item.coverImage && !brokenCovers.has(item.id)"`，`<img>` 加 `@error="brokenCovers.add(item.id)"`，再加 `loading="lazy"`。
- 失败后整个缩略图位**不占位**（标题左对齐），不显示占位图标。

### 1.2 评论头像（`views/CommentManageView.vue` 约 :169）

现状：`<img v-if="comment.avatar">`，坏链接显示浏览器破图 + alt 文字（基线截图评论第 2、3 条）。

- 换成 `<UserAvatar :src="comment.avatar" :name="comment.nickname" :size="24" />`，**去掉 v-if**（无头像也显示首字，行首对齐一致）。

## 2. 移动端文章卡操作收敛（`views/DashboardView.vue` mobile-card 约 :548-553）

现状：「查看 / 置顶 / 编辑 / 删除」四个纯文字 ghost 按钮平铺，删除与编辑相邻，按钮高约 24px（偏小，易误触）。

改为：

- 保留两个常用操作为文字按钮：`查看`、`编辑`（`编辑` 仍受 `canModify(item)` 控制）。按钮 `btn-ghost text-xs px-2.5 py-1.5`（触控高度 ≥ 32px）。
- 新增「更多」按钮（`MoreHorizontal :size="16"`，`aria-label="更多操作"`，`btn-ghost px-2 py-1.5`），仅 `canModify(item)` 时渲染，放在行尾（`ml-auto`）。用现有 `components/ui/Dropdown.vue`（`trigger="click" placement="bottom-end" :spacing="4"`），面板内容：
  - `置顶` / `取消置顶`（调用原 `handleToggleTop(item)`）
  - 分隔线 `<div class="my-1 border-t border-border" />`
  - `删除`（`text-danger`，调用原 `handleDelete(item)`，保留 `deletingIds` 的 disabled 逻辑）
  - 每项点击后调用插槽提供的 `close()`。
- 面板容器样式照抄 `UserManageView` 角色下拉（D1/D2 后为 `ui-dropdown-panel w-36 py-1 rounded-xl border border-border bg-bg-elevated shadow-lg`），菜单项 `w-full text-left px-3 py-2 text-sm hover:bg-bg-secondary`。
- 桌面端图标操作列**不动**。
- 删除仍走原有 `confirm()` 二次确认，不能因为收进菜单就跳过。

## 3. 空状态

### 3.1 `EmptyState.vue` 纸本化（作用域，不影响旧主题）

- 图标容器（`w-16 h-16 rounded-2xl bg-bg-code`）加 hook class `ui-empty-icon`。
- `styles/themes/paper-dashboard.css` 追加：

```css
/* ⑪ 空状态：虚线框图标（呼应前台 chip 的虚线语言） */
html.dash-mode[data-theme^="paper"] .ui-empty-icon {
  background-color: transparent;
  border: 1px dashed var(--border);
}
```

### 3.2 手写空状态换 `EmptyState`

| 位置 | 现状 | 改为 |
|---|---|---|
| `CategoryManageView.vue` 约 :27-35 | 手写 FolderTree 48px + 灰字 + 按钮 | `<EmptyState :icon="FolderTree" title="还没有分类" description="分类用于组织文章，支持多级" action-label="新建分类" @action="<原按钮的处理函数>" />` |
| `CollectionManageView.vue` 约 :35-42 | 手写 BookOpen + 「暂无合集 / 没有匹配的合集」+ 次按钮 | `keyword` 有值：`<EmptyState :icon="BookOpen" title="没有匹配的合集" description="换个关键词试试" />`（无按钮）；否则 `title="还没有合集" description="把相关文章串成系列，读者可以按顺序阅读" action-label="新建合集" @action="router.push('/dashboard/collections/new')"` |
| `KnowledgeManageView.vue` 约 :520-530（零知识库） | 手写 Database 图标块 + 文案 + 按钮 | 换 `EmptyState`，文案保留原文，`action-label="新建知识库" @action="openCreate"` |

### 3.3 知识库「未选中」半屏空白（`KnowledgeManageView.vue`）

现状：有知识库但未选中时，下方大半屏只有一行「选择一个知识库以查看和管理文档」。

- `fetchKbs()` 成功后：若 `selectedKbId.value === null && kbs.value.length > 0`，自动 `selectKb(kbs.value[0].id)`。**只在首次加载时做**（加一个 `autoSelected` 标记），用户主动点击取消选中后不再自动选回（`selectKb` 现在是 toggle 语义，要保留）。
- 原占位块保留（用户取消选中时仍会出现），文案改「点击上方知识库查看文档」。
- 注意：自动选中会触发 `fetchDocs()` + `fetchAvailable()` 两个请求，都是 DB 查询，不涉及 LLM/embedding 计费，可接受。

## 不做

- 不改 `fetchKbs` 里的 `catch { /* ignore */ }`（违反 AGENTS §3「catch 必须落日志」，但属另一关注点，记进实现记录的「观察项」，另开任务）。
- 不改 DataTable 的空状态默认文案。
- 不改桌面端操作列。

## 验收（总档口径之外）

- 封面：devtools 把某篇文章 `coverImage` 的请求 block 掉（或改成坏 URL）→ 桌面和移动端都不出现空块，标题左对齐。
- 评论：坏头像链接显示首字圆标。
- 移动 390：卡片只有「查看 / 编辑 / ⋯」；⋯ 展开为墨线硬阴影菜单（paper），「删除」红字且有分隔线，点删除仍弹确认框；置顶切换后菜单关闭、徽标更新；非本人文章（CREATOR 账号看他人文章时）不显示 ⋯。
- 空状态：清空搜索条件后搜一个不存在的合集名 → 「没有匹配的合集」无按钮；paper 下图标为虚线框。
- 知识库页：进入即选中第一个知识库并展示文档；点击已选中的卡片取消选中 → 显示新占位文案，且不会被自动选回。

## 实现记录（2026-11-16）

**改动文件**（7 个应用文件 + 1 个走查脚本）：

- `views/DashboardView.vue`：
  - script：`reactive` 入 vue 导入；新增 `brokenCovers = reactive(new Set<string>())`；`Dropdown` 入 `@/components/ui` 导入。
  - 桌面 `#cell-title` 与移动 `#mobile-card` 两处封面：`v-if="item.coverImage && !brokenCovers.has(item.id)"`；`<img>` 加 `loading="lazy"` + `@error="brokenCovers.add(item.id)"`。失败后整个缩略图位不占位。
  - 移动 `#mobile-card` 操作行重构：`查看`/`编辑` 保留文字钮（`btn-ghost text-xs px-2.5 py-1.5`，编辑仍 `canModify(item)`）；新增 `<Dropdown trigger="click" placement="bottom-end" :spacing="4" class="ml-auto">`（仅 canModify 渲染），trigger 为 `MoreHorizontal :size="16"` + `aria-label="更多操作"` + `btn-ghost px-2 py-1.5`；面板 `ui-dropdown-panel w-36 py-1 rounded-xl border border-border bg-bg-elevated shadow-lg`，含 `置顶/取消置顶`（原 `handleToggleTop`）、`my-1 border-t border-border` 分隔线、`text-danger` `删除`（原 `handleDelete`，`deletingIds` disabled 保留），各项点击后调插槽 `close()`。桌面端图标操作列未动。
- `views/CommentManageView.vue`：裸 `<img v-if="comment.avatar" class="w-6 h-6 rounded-full">` → `<UserAvatar :src="comment.avatar" :name="comment.nickname" :size="24" />`（去 v-if，无头像也显示首字）；新增 `import UserAvatar from '@/components/common/UserAvatar.vue'`。
- `components/ui/EmptyState.vue`：图标容器加 `ui-empty-icon` hook class（共享层，消费点复核见下）。
- `styles/themes/paper-dashboard.css`：追加 ⑪ `.ui-empty-icon` 虚线框规则（`html.dash-mode[data-theme^="paper"]` 作用域，透明底 + `1px dashed var(--border)`）。
- `views/CategoryManageView.vue`：手写空态 → `<EmptyState :icon="FolderTree" title="还没有分类" description="分类用于组织文章，支持多级" action-label="新建分类" @action="openCreateModal()" />`；`EmptyState` 入 ui 导入。
- `views/CollectionManageView.vue`：手写空态 → 两个 `<EmptyState v-else-if>`：`keyword` 有值 → `title="没有匹配的合集" description="换个关键词试试"`（无按钮）；否则 `title="还没有合集" description="把相关文章串成系列，读者可以按顺序阅读" action-label="新建合集" @action="router.push('/dashboard/collections/new')"`；`EmptyState` 入 ui 导入。
- `views/KnowledgeManageView.vue`：
  - `fetchKbs()` 成功后：`!kbAutoSelected && selectedKbId === null && firstKb` → `kbAutoSelected = true; selectKb(firstKb.id)`（新增 `let kbAutoSelected = false` 标志，仅首载自动选中，用户取消后不再选回；`selectKb` toggle 语义不变）。
  - 零库手写空态 → `<EmptyState :icon="Database" title="还没有知识库" description="创建知识库后，可将博客文章同步为向量索引，为 AI 助手提供知识来源" action-label="新建知识库" @action="openCreate" />`（文案保留原文）。
  - 未选中占位文案「选择一个知识库以查看和管理文档」→「点击上方知识库查看文档」。

**偏离方案**（均已评估，记录备查）：

1. `brokenCovers` 用 `reactive(new Set())` 而非 `ref(new Set())`——Set 需原地 `.add()` 触发渲染，Vue3 响应式集合正统做法；模板内 `brokenCovers.has(item.id)` / `@error="brokenCovers.add(item.id)"` 直写。
2. KB 自动选中因 `noUncheckedIndexedAccess` 先取 `const firstKb = kbs.value[0]` 判空再 `selectKb(firstKb.id)`（首轮 type-check 报 TS2532，修后复跑绿）。
3. 合集空态拆成两个 `<EmptyState v-else-if>` 分支（keyword / 默认），比 prd 表格单行描述更直白；两分支互斥、与 loading/loadError 链衔接正确。
4. 知识库空态 icon 用 `Database` 保留原视觉意象（prd 未指定 icon，EmptyState 默认 Inbox 会偏离原设计）。
5. 移动端「更多」Dropdown 直接挂 `class="ml-auto"`——属性落根节点 `.dropdown-root`（inline-flex），在 `flex items-center gap-2` 行内实现行尾对齐。
6. `fetchKbs` 的 `catch { /* ignore */ }` 按 prd「不做」保留，记观察项（违反 AGENTS §3 catch 必须落日志，另开任务）。

**消费点复核**：动手前 rg 复核——`EmptyState` 既有消费方 = `DataTable.vue`、`ArticleList.vue`（前台！）、`CommentManageView`、`AiManageView`，本次新增 3 处后台消费；`ui-empty-icon` 仅加 class 不改结构，虚线框规则限定 `dash-mode + paper`，前台 `ArticleList` 与旧主题不受影响（classic-light computed 断言已验）。`UserAvatar` 消费方 = D1 的 Sidebar/Settings/UserManage/UserProfile + 本次 CommentManageView。

**验收输出**：

- `./scripts/verify.sh` → `VERIFY OK (quick)`（后端 hermetic + 前端 type-check；首轮 type-check 报 `kbs.value[0]` TS2532 已修）。
- `npm run type-check` / `npm run build` → 绿；`npm run lint` → oxlint+eslint **20 errors 全部存量**（test-callout.js 控制字符、composables、d2/d4/ragsec 走查脚本等），本次改动文件 0 命中、**0 新增**（基线 18→20 系 d4-walkthrough.mjs 带入 2 个，非本次）；`git status` 确认 lint --fix 无误改无关文件。
- `DASH_USER/DASH_PASS node scripts/dash-shots.mjs scripts/output/dash-shots-D5` → 66 张 `OK: no overflow, no page error`，overflowX 全 0。
- 走查脚本 `scripts/d5-walkthrough.mjs`（入库，同 d2/d3/d4 惯例）：**26 pass / 0 fail / 0 skip**——详见走查记录。
- classic-light 回归（PIL 像素 diff vs `dash-shots-D4`，隔离 D1–D4 已合入改动）：22 路由中 16 张 0.00% 逐像素一致；变化仅落在本任务明示范围——`comments`（0.07–0.28%，UserAvatar）、`knowledge` 桌面/移动（0.9–2.0%，自动选中出详情面板）、`posts` 移动（1.25–1.31%，操作收敛）、`posts` 桌面（0.86–0.87%，含相对时间数据漂移+个别真破图封面被移除）；`paper-light-mobile-tags`/`paper-dark-mobile-users` 等未触及页 ≤1.3% 为时间戳/计数数据漂移（同 D2–D4 观察口径）。无横向溢出、无 pageerror。

## 走查记录（2026-11-16）

脚本 `mysite-frontend/scripts/d5-walkthrough.mjs`（入库）：**26 pass / 0 fail / 0 skip**。

- §1 破图兜底：`page.route` 拦截 `/v1/articles` 给首条注入 `127.0.0.1:9` 必失败封面 → 桌面+移动端坏 img 全部移除、`loading="lazy"` 在位、空封面块=0、行/卡数不丢、移动无横向溢出。
- §2 评论头像：`role="img"`（UserAvatar）存在、旧 `img.w-6.h-6.rounded-full` 清零；对头像内 img 派发 `error` → 回退首字保留。
- §3 移动端操作（390px）：卡片仅 查看/编辑/⋯；⋯ 展开面板含置顶项+分隔线+`text-danger` 删除；点删除弹原确认框（点取消未真删）；点置顶菜单关闭+文案翻转（置顶↔取消置顶，走查后已还原）；非本人文章 ⋯ 隐藏分支无第二账号种子，未覆盖（同 D3 skip 口径说明）。
- §4 空状态：拦截 `/v1/categories/tree`→[] 验证 paper 下 `.ui-empty-icon` computed = `dashed/1px/transparent`、文案+新建分类按钮；classic-light 同拦截验证无虚线框（作用域隔离）；合集搜 nonsense keyword →「没有匹配的合集」+ 无操作按钮；拦截 `/v1/rag/knowledge-bases`→[] → 零库空态文案+新建知识库按钮。
- §5 知识库自动选中：进页未点击即见「文档总数」面板+卡片选中态；点已选中卡片 → 占位「点击上方知识库查看文档」+面板收起，等待后未被自动选回；再点同卡片恢复选中。

**终验补充**（`scripts/output/d5-review.mjs`，一次性不入库）：**15 pass / 0 fail / 0 skip**——

- ⋯菜单 computed（390px）：paper-light `border-radius:0` + 硬阴影（`4px 4px 0` 系 `--border-strong`）vs classic-light 圆角+柔影，作用域正确；菜单不越出视口；ESC 与外部点击均关闭。
- 桌面操作列回归：首行仍为查看/置顶/编辑/删除图标按钮（title 断言）。
- `dash-mode` 生命周期：dashboard 挂载存在、回前台摘除。
- KB 交互：自动选中后点第二个 KB 卡片正常切换；空状态 `.ui-empty-icon` 64px + 内部 svg 可见。
- 「非本人文章不显示 ⋯」：`DashboardView.fetchArticles` 对非 admin 恒带 `authorId=user.id` 且前端再过滤，列表内 `canModify` 恒 true——隐藏分支为防御性 v-if，实际不可达（与桌面端 `!canModify` 禁用图标同理），代码路径已核对 `usePermission.canModifyArticle`。
