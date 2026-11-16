# D2 后台纸本外壳（2026-11-16）

> 总档：`.trellis/tasks/11-16-dash-redesign/prd.md`。前置：D1 已合入。
> 目标：paper 主题下，后台换成前台同一套视觉语言——**方角、细线、无柔影；浮层用墨线 + 硬阴影；页标题衬线**。
> 硬约束：classic 等 6 套旧主题、前台所有页面**零视觉变化**。做法是「作用域 CSS + 组件只加 hook class」，不改组件既有 Tailwind 类。
> 属 AGENTS.md §2.3 共享层改动，消费点见 §5。

## 1. 作用域开关（`app/layouts/DashboardLayout.vue`）

```ts
import { onMounted, onBeforeUnmount } from 'vue'
onMounted(() => document.documentElement.classList.add('dash-mode'))
onBeforeUnmount(() => document.documentElement.classList.remove('dash-mode'))
```

- 为什么挂 `<html>` 而不是 layout 根 div：`Modal` / `Drawer` / `Dropdown` / `GlobalConfirmDialog` 都 `Teleport to="body"`，不在 layout DOM 树里。
- `data-theme` 由 `useTheme.ts:135` 设在 `<html>` 上，所以统一前缀写作 `html.dash-mode[data-theme^="paper"]`（下文记作 **P**）。

## 2. 组件 hook class（只加 class，不删不改原有类）

| 文件 | 元素 | 新增 class |
|---|---|---|
| `components/ui/PageHeader.vue` | 根 div / `h1` | `ui-page-header` / `ui-page-title` |
| `components/ui/DataTable.vue` | :95 桌面表格外层 `div.overflow-x-auto` / :98 `thead tr` / :216 移动端每张数据卡片（`v-for="item in data"` 那个 div；骨架屏卡片 :206 不加） | `ui-table` / `ui-table-head` / `ui-table-card` |
| `components/ui/SearchFilterBar.vue` | :74 搜索 input、:91 / :107 两个 select、:120 排序按钮 | `dash-field` |
| `components/ui/Badge.vue` | 根 span（加在 `classes` 字符串里） | `ui-badge` |
| `components/ui/Modal.vue` | :110 `.modal-panel` 已有类名 | 不加，直接用 `.modal-panel` |
| `components/ui/Drawer.vue` | :102 `.drawer-panel` 已有 | 不加 |
| `components/ui/Dropdown.vue` | 不加。该组件面板无自带样式（`.dropdown-panel` 注释写明交给调用方），样式在插槽内容上 | — |
| `views/UserManageView.vue` :361 角色下拉的插槽内容 div（D1 已把它的阴影改成 `shadow-lg`） | 后台唯一的 Dropdown 调用方 | `ui-dropdown-panel` |
| `components/dashboard/DashboardSidebar.vue` | `aside` / 每个导航 `RouterLink` | `dash-sidebar` / `dash-nav-item`，并在激活时加 `is-active`（在现有 `:class` 数组里追加 `route.path === item.path && 'is-active'`） |
| `views/KnowledgeManageView.vue` 知识库卡片 button（:536 附近） | 可点击卡片 | `dash-hoverable` |
| `views/ImageManagerView.vue` :383 图片卡 | 可点击卡片 | `dash-hoverable` |

同时**删除** `KnowledgeManageView.vue:591` 的 `!rounded-2xl`（Tailwind important 工具类在 layer 里，会压过作用域里的 `!important`，留着就方不了）。改完 `rg -n '!rounded' src/views src/components` 确认后台范围 0 命中。

## 3. 新建 `src/styles/themes/paper-dashboard.css`

在 `styles/main.css` 中紧跟 `@import "./themes/paper.css";` 之后加 `@import "./themes/paper-dashboard.css";`。

**规则写在任何 `@layer` 之外**（unlayered 才能压过 Tailwind utilities）。不要用 CSS 嵌套语法，选择器逐条写全前缀，方便 grep。下面是规格，取值即定稿：

```css
/* 后台纸本外壳：仅 paper 主题 + 后台路由生效（DashboardLayout 给 <html> 挂 .dash-mode）。
   不放进 @layer，保证压过 Tailwind utilities；旧主题与前台不受影响。 */

/* ① 方角：中大圆角归零。保留 rounded / rounded-sm / rounded-full（复选框、头像、状态圆点、侧栏折叠钮） */
html.dash-mode[data-theme^="paper"] .rounded-md,
html.dash-mode[data-theme^="paper"] .rounded-lg,
html.dash-mode[data-theme^="paper"] .rounded-xl,
html.dash-mode[data-theme^="paper"] .rounded-2xl,
html.dash-mode[data-theme^="paper"] .rounded-\[10px\],
html.dash-mode[data-theme^="paper"] .rounded-r-sm,
html.dash-mode[data-theme^="paper"] .card-solid,
html.dash-mode[data-theme^="paper"] .input-base,
html.dash-mode[data-theme^="paper"] .btn-primary,
html.dash-mode[data-theme^="paper"] .btn-secondary,
html.dash-mode[data-theme^="paper"] .btn-danger,
html.dash-mode[data-theme^="paper"] .btn-ghost,
html.dash-mode[data-theme^="paper"] .ui-badge {
  border-radius: 0;
}

/* ② 容器：细线、无柔影 */
html.dash-mode[data-theme^="paper"] .card-solid {
  box-shadow: none;
  border-color: var(--border);
}
html.dash-mode[data-theme^="paper"] .shadow-sm,
html.dash-mode[data-theme^="paper"] .shadow-md,
html.dash-mode[data-theme^="paper"] .card-shadow,
html.dash-mode[data-theme^="paper"] .card-shadow-hover {
  box-shadow: none;
}

/* ③ 可点击卡片：hover 墨线 + 小硬阴影，不上浮 */
html.dash-mode[data-theme^="paper"] .dash-hoverable {
  transition: border-color .15s, box-shadow .15s;
}
html.dash-mode[data-theme^="paper"] .dash-hoverable:hover {
  border-color: var(--border-strong);
  box-shadow: 3px 3px 0 var(--border-strong);
  transform: none;
}

/* ④ 按钮：去上浮与发光；次按钮透明底 + 细线 */
html.dash-mode[data-theme^="paper"] .btn-primary:hover,
html.dash-mode[data-theme^="paper"] .btn-danger:hover {
  transform: none;
  box-shadow: none;
}
html.dash-mode[data-theme^="paper"] .btn-secondary {
  background-color: transparent;
}
html.dash-mode[data-theme^="paper"] .btn-secondary:hover {
  background-color: var(--bg-secondary);
  border-color: var(--border-strong);
  color: var(--text-primary);
}

/* ⑤ 表单控件：纸面底、细线；聚焦换墨线，不要发光环 */
html.dash-mode[data-theme^="paper"] .input-base,
html.dash-mode[data-theme^="paper"] .dash-field {
  background-color: var(--bg-primary);
  border-color: var(--border);
  border-radius: 0;
}
html.dash-mode[data-theme^="paper"] .input-base:focus,
html.dash-mode[data-theme^="paper"] .dash-field:focus {
  border-color: var(--border-strong);
  box-shadow: none;
  outline: none;
}

/* ⑥ 表格：表头透明 + 墨线下边，行线保留细线 */
html.dash-mode[data-theme^="paper"] .ui-table {
  border-color: var(--border);
  background-color: var(--bg-elevated);
}
html.dash-mode[data-theme^="paper"] .ui-table-head {
  background-color: transparent;
}
html.dash-mode[data-theme^="paper"] .ui-table-head > th {
  border-bottom: 1px solid var(--border-strong);
  color: var(--text-secondary);
  letter-spacing: .04em;
}
html.dash-mode[data-theme^="paper"] .ui-table-card {
  background-color: var(--bg-elevated);
  border-color: var(--border);
}

/* ⑦ 浮层：墨线 + 硬阴影，去玻璃模糊 */
html.dash-mode[data-theme^="paper"] .modal-panel,
html.dash-mode[data-theme^="paper"] .ui-dropdown-panel {
  border-radius: 0;
  border: 1px solid var(--border-strong);
  box-shadow: var(--shadow-hard);
  background: var(--bg-elevated);
  backdrop-filter: none;
  -webkit-backdrop-filter: none;
}
html.dash-mode[data-theme^="paper"] .ui-dropdown-panel {
  box-shadow: 4px 4px 0 var(--border-strong);
}
html.dash-mode[data-theme^="paper"] .drawer-panel {
  border-radius: 0;
  background: var(--bg-elevated);
  backdrop-filter: none;
  -webkit-backdrop-filter: none;
  box-shadow: none;
  border-right: 1px solid var(--border-strong);
}

/* ⑧ 侧栏：激活项去色块，墨字加粗，保留左侧锈红竖条 */
html.dash-mode[data-theme^="paper"] .dash-sidebar {
  background-color: var(--bg-primary);
}
html.dash-mode[data-theme^="paper"] .dash-nav-item.is-active {
  background-color: transparent;
  color: var(--text-primary);
  font-weight: 600;
}
html.dash-mode[data-theme^="paper"] .dash-nav-item:not(.is-active):hover {
  background-color: var(--bg-secondary);
}

/* ⑨ 页标题：衬线 */
html.dash-mode[data-theme^="paper"] .ui-page-title {
  font-family: var(--font-serif);
  font-weight: 900;
  letter-spacing: -0.01em;
}

/* ⑩ 焦点可见性兜底：方角/去阴影后键盘焦点仍要看得见 */
html.dash-mode[data-theme^="paper"] button:focus-visible,
html.dash-mode[data-theme^="paper"] a:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 2px;
}
```

实现时允许微调的只有：① 选择器里如有遗漏的圆角类（例如 `rounded-[14px]`）可以补进①；⑥ 如果 `ui-table-head` 加在 `tr` 上导致 `> th` 选不中，改成实际结构。**其余取值不要自行发挥**，有异议写进实现记录由验收决定。

## 4. 明确不做

- 不改 `styles/main.css` 里 `.btn-*` / `.input-base` / `.card-solid` 的原定义（前台在用）。
- 不在组件里删 `rounded-*` / `shadow-*` 类（旧主题依赖）。
- 不给普通卡片加硬阴影（后台信息密度高，满屏硬阴影会很吵；硬阴影只给浮层和 hover 反馈）。
- 侧栏宽度、折叠逻辑、导航分组不动。

## 5. 消费点（§2.3，动手前 `rg` 复核）

- `PageHeader`：Comment/User/CollectionManage/Dashboard/Category/Tag/Ai/Knowledge/ImageManager 9 个后台页，**无前台调用**。
- `DataTable`：User/Dashboard/Tag/Ai/Knowledge 5 个后台页，无前台调用。
- `SearchFilterBar`：Comment/User/CollectionManage/Dashboard/Tag/Ai/ImageManager，无前台调用。
- `Badge`：AiProviderCard、Comment/Settings/User/Dashboard/Ai/Knowledge，无前台调用。
- `Modal`：ConfirmDialog（→ `GlobalConfirmDialog`，**前台也会弹**）、Dashboard/Category/Tag。
- `Dropdown`：组件本身不改；前台 AppHeader、ThemeToggle、BlogHome 的面板样式在各自插槽里，不受影响；后台只有 UserManage 加 hook。
- `Drawer`：DashboardLayout。
- → 前台调用方只受 hook class 影响（无样式，因为前台没有 `dash-mode`），验收时截前台首页/文章页确认零变化。

## 验收（总档口径之外）

- classic-light 下 66 张截图与 `dash-shots-baseline` 对比，无差异（可用 ImageMagick `compare -metric AE` 或肉眼逐页）。
- 前台：`node scripts/ui-shots.mjs http://127.0.0.1:5173` 跑一遍，与改动前一致（T1 产物在 `scripts/output/ui-shots/`）。
- paper-light / paper-dark：卡片方角无柔影；表头墨线；Modal（删文章确认）、角色 Dropdown、移动端抽屉均为墨线 + 硬阴影；知识库卡/图片卡 hover 硬阴影不上浮；筛选栏聚焦为墨线无光环；页标题衬线；Tab 键焦点可见。
- 从后台点「回到主页」：devtools 确认 `<html>` 已无 `dash-mode`，前台卡片圆角/阴影恢复原样。

## 实现记录（2026-11-16）

**改动文件**：
- `app/layouts/DashboardLayout.vue`：onMounted/onBeforeUnmount 给 `<html>` 挂/摘 `dash-mode`（Teleport 浮层才能命中）。
- `styles/themes/paper-dashboard.css`（新建，138 行）：§3 规格 CSS 全量落地，规则全部在 `@layer` 外、选择器统一 `html.dash-mode[data-theme^="paper"]` 前缀；`styles/main.css` 紧随 `paper.css` 引入。
- 组件 hook class（只加不改）：`PageHeader`（`ui-page-header`/`ui-page-title`）、`DataTable`（`ui-table`/`ui-table-head`/`ui-table-card`，骨架屏卡未加）、`SearchFilterBar`（4 控件 `dash-field`）、`Badge`（`classes` 串加 `ui-badge`）、`UserManageView` 角色下拉插槽（`ui-dropdown-panel`）、`DashboardSidebar`（`dash-sidebar`/`dash-nav-item`+`is-active`）、`KnowledgeManageView` KB 卡与 `ImageManagerView` 图片卡（`dash-hoverable`）。
- `KnowledgeManageView.vue`：删除 `!rounded-2xl`，`rg '!rounded' src/` 复核 0 命中。
- Modal/Drawer/Dropdown 组件本体未动（`.modal-panel`/`.drawer-panel` 已有类名，Dropdown 面板样式走调用方插槽）。

**偏离方案**：仅 §3.① 允许的微调一处——补 `.rounded-t-md`（`AiManageView.vue:760/767` 成本图柱条顶角，后台范围内唯一遗漏的中圆角类）；其余取值与 prd 定稿一致，未自发挥。

**消费点复核**：动手前 rg 复核——`!rounded` 仅 KnowledgeManageView 1 处；后台 Dropdown 调用方仅 UserManageView（前台 AppHeader/ThemeToggle/BlogHome 插槽自带样式不受影响）；Badge/PageHeader/DataTable/SearchFilterBar 全部调用方均为后台视图，无前台消费。

**验收输出**：
- `./scripts/verify.sh` → `VERIFY OK (quick)`（后端 hermetic + 前端 type-check）。
- `npm run lint` → oxlint 16 errors 均为存量（test-callout.js/useToc.ts 等），改动文件 0 命中，基线持平。
- `npm run build` → 绿。
- `DASH_USER/DASH_PASS` 环境变量注入 `node scripts/dash-shots.mjs scripts/output/dash-shots-D2` → 66 张 `OK: no overflow, no page error`，overflowX 全 0。
- classic-light 对比：相对 **D1 产物**（`dash-shots-D1/`，相对 pre-D1 基线含 D1 既定改动）17/22 张字节级 IDENTICAL；5 张差异经像素级定位均为数据/时间漂移（文章列表相对时间列、图片页噪点），与样式无关——paper-dashboard.css 全部规则以 `[data-theme^="paper"]` 门控，classic-light 下 computed 验证 `border-radius:14px`、柔影、无衬线标题全部保持原样。
- 前台回归：`ui-shots.mjs` 跑通 `OK: no horizontal overflow`；前台页 `dash-mode=false`（home/404/未登录跳转均验），且前台不消费任何被改组件——ui-shots 与 T1 旧基线的差异为期间其他任务（T14–T18 等）的既定改动 + 首篇文章 id 变化，非本任务引入。
- paper 交互断言（Playwright computed-style，paper-light 实测）：表头 th 墨线下边 `1px rgb(29,26,22)`+透明底、卡片 `radius:0/shadow:none`、页标题 Noto Serif SC/900、徽标方角、侧栏激活 `bg:transparent/color:ink/weight:600`、筛选控件聚焦 `border-strong` 无光环、Modal `6px` 硬阴影无模糊、角色下拉 `4px 4px 0` 硬阴影、移动抽屉方角无模糊墨线右边、KB/图片卡 hover `3px 3px 0` 硬阴影 `transform:none`、Tab 焦点 `2px solid var(--accent)`——全 PASS。paper-dark computed 抽查同绿。
