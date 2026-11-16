# D4 统一页面骨架（2026-11-16）

> 总档：`.trellis/tasks/11-16-dash-redesign/prd.md`。前置：D2、D3 已合入。
> 目标：所有后台页**标题位置一致、容器规则一致、列表/表格长相一致**。切换侧栏菜单时页面不跳位。
> 这是布局对齐，改动对所有主题生效（classic 下也会看到对齐结果，属预期，验收时逐条核对是否只发生在下列位置）。

## 0. 页面骨架约定（写进实现，后续新页照此）

```
<div class="flex flex-col gap-5 min-w-0">        ← 页面根：不带 padding、不带 max-width（padding 由 DashboardLayout main 给）
  <PageHeader title subtitle>#actions</PageHeader> ← 每个后台页必须有，PageHeader 自带 mb-6，根节点 gap 不再叠加时可去掉 mb（见 §1）
  [SearchFilterBar]                               ← 有筛选才放
  [内容：DataTable / 列表卡 / 网格]
</div>
```

- 列表/表格页：全宽（跟随 main）。
- 表单页（设置、合集编辑）：根节点加 `max-w-4xl`，**左对齐**（不要 `mx-auto`，否则标题和其他页错位）。
- 例外：`PostEditorView`（全屏编辑器，自有布局）不纳入。

## 1. `PageHeader.vue`：去掉自带外边距，交给页面 gap

- 根 div 的 `mb-6` 删掉（D2 已加的 `ui-page-header` 保留）。
- 新增可选 prop `back?: string`（路由路径）：有值时标题左边渲染返回按钮（样式抄 `CollectionEditView.vue` 现有返回钮：`p-2 rounded-lg text-text-muted hover:bg-accent-subtle hover:text-accent`，图标 `ArrowLeft :size="18"`），点击 `router.push(back)`。不用 `router.back()`（直达访问会离站，前台 T8 已踩过）。
- 消费点：9 个后台页（见 D2 §5）。删 `mb-6` 后，每个页面根节点必须提供间距：逐页检查根节点，**统一改成 `flex flex-col gap-5 min-w-0`**，并删掉 PageHeader 后面兄弟元素上多余的 `mb-4` / `mt-6` 这类补丁外边距（例如 `CollectionManageView` 模板 :19 的 `class="mb-4"`、:234 的 `mt-6`）。逐页截图确认间距一致即可，不要求像素级。

## 2. 逐页改动

| 页面 | 现状 | 改 |
|---|---|---|
| `KnowledgeManageView.vue` :500 | 根 `w-full max-w-6xl mx-auto p-6 space-y-6 min-w-0`，比其他页多一层内边距 + 居中，标题右下偏移 | 根改为 `flex flex-col gap-5 min-w-0`；内部 `space-y-*` 不受影响 |
| `SettingsView.vue` :163-164 | 根 `max-w-4xl`，裸 `h1.text-2xl.font-semibold.mb-8`，无副标题 | 根 `flex flex-col gap-5 min-w-0 max-w-4xl`；`h1` 换 `<PageHeader title="个人设置" subtitle="资料、账号安全与偏好" />`；两个 `section.card-solid` 去掉 `rounded-2xl`（统一用 card-solid 自带 14px，paper 下 D2 归零），内边距 `p-7 md:p-8` → `p-6`（与其他卡片同档） |
| `CollectionEditView.vue` :2-11 | 根 `max-w-[900px] mx-auto`（居中，与其他页错位）；手写返回钮 + h1 | 根 `flex flex-col gap-5 min-w-0 max-w-4xl`（左对齐）；头部换 `<PageHeader :title="pageTitle" back="/dashboard/collections" />`；表单块 :28 `rounded-xl border border-border bg-bg-primary` → `card-solid`（与设置页同款容器） |
| `CategoryManageView.vue` 树表 :38-52 | 手写表格：容器 `rounded-lg border bg-bg-secondary`、表头 `text-sm`，与 DataTable 不一致 | 见 §3 |
| `CollectionManageView.vue` 列表 :44-110 | 每个合集一张独立圆角卡 + 手写 `rounded-full` 状态胶囊（:60、:68） | 见 §4 |
| `CommentManageView.vue` | 已是「单容器 + divide-y」列表，样式合格 | 只做 §1 的根节点间距；头像在 D5 处理 |

## 3. 分类树表对齐 DataTable

分类是树（`CategoryTreeNode` 递归），不能直接换 DataTable，只对齐外观：

- 容器：`rounded-lg border border-border bg-bg-secondary overflow-hidden` → `ui-table rounded-lg border border-border overflow-hidden`（去掉 `bg-bg-secondary`，与 DataTable 一致；`ui-table` 让 D2 的纸本规则生效）。
- 表头行 :39：`bg-bg-code` 保留；文字统一 DataTable 表头规格 `text-xs font-medium text-text-muted`（现在是 `text-sm` 混 `text-text-secondary`）；内边距 `px-3 py-3`。加 hook class `ui-table-headrow`。
- `paper-dashboard.css` 追加（放在 ⑥ 表格段落末尾）：

```css
html.dash-mode[data-theme^="paper"] .ui-table-headrow {
  background-color: transparent;
  border-bottom: 1px solid var(--border-strong);
}
html.dash-mode[data-theme^="paper"] .ui-table-headrow > * {
  color: var(--text-secondary);
  letter-spacing: .04em;
}
```

- `CategoryTreeNode.vue` 行内：数字列加 `tabular-nums`；「启用」徽标若是手写 span，换成 `<Badge variant="success">启用</Badge>` / `<Badge variant="muted">停用</Badge>`（先读代码确认现有字段名和取值）。

## 4. 合集列表改「单容器 + 行」

与评论管理同构，减少「一堆漂浮圆角卡」：

- 外层 `space-y-3` → `card-solid divide-y divide-border overflow-hidden`。
- 每行：去掉 `rounded-lg border border-border`，保留 `flex items-center gap-4 p-4`，hover 改 `hover:bg-bg-secondary`（去掉 `hover:border-accent/30 hover:bg-accent-subtle/30`）。
- :60 / :68 的手写胶囊换成 `<Badge size="sm" variant="warning">…</Badge>` / `<Badge size="sm" variant="success">…</Badge>`，文案和图标保持不变（图标放进 Badge 默认插槽）。
- 分页容器 :234 `card-solid mt-6` → `card-solid`（间距交给根 gap）。
- 不改数据、排序、操作按钮逻辑。

## 5. 衬线标题兜底

`PostEditorView.vue:581` 的 `h1` 不纳入骨架改造，但加 hook class `ui-page-title`，让纸本下标题字体与其他页一致。

## 不做

- 不拆 `KnowledgeManageView` / `AiManageView` 大文件。
- 不改 `DashboardLayout` 的 main padding（`p-4 sm:p-6 md:p-8`）。
- 不给列表页加 max-width。

## 验收（总档口径之外）

- 写个一次性 Playwright 断言（放 `scripts/output/` 下不入库，或直接在 node -e 里跑）：桌面 1440 下逐个访问 11 个后台路由，读 `.ui-page-title` 的 `getBoundingClientRect().left/top`，**所有页面 left 相同、top 相同**（容差 1px；PostEditor 除外）。输出贴进实现记录。
- 截图：知识库页标题不再右下偏；设置页有副标题、卡片与其他页同宽规则；合集编辑页左对齐、返回钮在标题左侧；分类表头与文章表头同款；合集列表为单容器分隔线。
- 合集编辑页：直接在地址栏打开 `/dashboard/collections/<id>/edit`，点返回 → 回到合集管理（不离站）。
- classic-light 下以上改动同样生效且不难看；其余页面与基线一致。

## 实现记录（2026-11-16）

**改动文件**（15 个，全部前端）：

- `components/ui/PageHeader.vue`：根 div 删 `mb-6`（`ui-page-header` hook 保留）；新增可选 prop `back?: string`，有值时标题左侧渲染返回钮（`ArrowLeft :size="18"`，样式抄 CollectionEditView 原返回钮），点击 `router.push(back)`。
- `styles/themes/paper-dashboard.css`：⑥ 表格段末尾追加 `.ui-table-headrow` 两条规则（透明底 + `--border-strong` 下边、`> *` 文字 secondary + letter-spacing），与 prd 定稿一致。
- `components/ui/CategoryTreeNode.vue`：数字列加 `tabular-nums`；手写状态钮改为 `<button>` 包 `<Badge variant="success|muted" size="sm">`（原状是 button 不是 span，保留 emit toggleStatus 交互）；删 `statusClass`。
- 9 个 PageHeader 页根节点统一 `flex flex-col gap-5 min-w-0`，删兄弟元素补丁外边距：
  - `DashboardView`：根补 `min-w-0`；删 SearchFilterBar `mb-4`、批量条 `mb-4`。
  - `UserManageView`：根改 flex；删统计卡/筛选栏/批量条 3 处 `mb-4`。
  - `TagManageView`：根改 flex；删热门标签卡/筛选栏 `mb-4`。
  - `CommentManageView`：根改 flex；删筛选栏 `mb-4`。
  - `CategoryManageView`：根改 flex；树容器 → `ui-table rounded-lg border border-border overflow-hidden`（去 `bg-bg-secondary`）；表头行加 `ui-table-headrow`、`px-4→px-3`、4 个 span 统一 `text-xs font-medium text-text-muted`。
  - `CollectionManageView`：根改 flex；删筛选栏 `mb-4`；列表 `space-y-3` → `card-solid divide-y divide-border overflow-hidden` 单容器；行去圆角边框、hover 改 `hover:bg-bg-secondary`；手写胶囊 → `<Badge variant="warning|success" size="sm">`（私有徽标 Lock 图标入插槽、`title` 提示保留）；分页 `card-solid mt-6` → `card-solid`。
  - `ImageManagerView`：根改 flex；删筛选栏 `mb-4`。
  - `AiManageView`：根改 flex；删页签栏 `mb-5`、筛选栏 `mb-4`、dashboard 页预设行/统计卡/图表卡/双列栅格 `mb-4`、供应商页说明 `mb-4` 共 6 处。
  - `KnowledgeManageView`：根 `w-full max-w-6xl mx-auto p-6 space-y-6 min-w-0` → `flex flex-col gap-5 min-w-0`（标题不再右下偏）。
- `SettingsView.vue`：引 PageHeader；根 → `flex flex-col gap-5 min-w-0 max-w-4xl`；裸 h1 → `<PageHeader title="个人设置" subtitle="资料、账号安全与偏好" />`；内层 `space-y-8` → `flex flex-col gap-5`；两 section `card-solid rounded-2xl p-7 md:p-8` → `card-solid p-6`。
- `CollectionEditView.vue`：引 PageHeader、删 ArrowLeft 导入；根 `max-w-[900px] mx-auto` → `flex flex-col gap-5 min-w-0 max-w-4xl`；手写返回钮+h1 → `<PageHeader :title="pageTitle" back="/dashboard/collections" />`；错误条删 `mb-6`；两个表单块 `p-6 rounded-xl border border-border bg-bg-primary` → `card-solid p-6`。
- `PostEditorView.vue`：h1 加 `ui-page-title` hook（不纳入骨架改造）。

**偏离方案**（均已评估，记录备查）：

1. `SettingsView` 内层 `space-y-8` 改为 `flex flex-col gap-5` 而非拆除包裹 div——视觉节奏同为 20px，避免重排 270 行缩进。
2. `CollectionEditView` 的 `card-solid` 应用于**两个**表单块（基本信息+文章列表），prd 写单数「表单块」——同容器样式，拆一半会更不齐。
3. PageHeader 返回钮加 `-ml-2`（让图标左缘与标题文字对齐）+ `type="button"` + `aria-label="返回"`；按钮与标题 `gap-3`。
4. `CategoryTreeNode` 状态是可点击 button（切换启用），保留 button 外壳包 Badge，不是直接换 span；Badge 自动宽度与原 `w-14` 等宽（启用/禁用同字数）。
5. 合集私有徽标加 `class="gap-1"`（Badge 仅 dot 时才有 gap class）+ Lock `:size="10"`（配 text-xs）。
6. `ImageManagerView` v-else 内容块内部的 `mb-4/mb-3/mt-6` 保留——它们是单个根子节点内部的版式节奏，非 PageHeader 补偿外边距。
7. 分类表头按 prd 用 `px-3`（对齐 DataTable 单元格），复选框比树行（paddingLeft=1rem）左偏 4px——prd 明确取值，从之。

**消费点复核**：动手前 rg 复核——PageHeader 调用方=9 后台页（无前台）；本次新增 SettingsView/CollectionEditView 两处消费（均后台路由）；`!rounded`、手写胶囊 span 无其他遗漏；`statusClass`/`ArrowLeft` 删除后 0 残留引用。

**验收输出**：

- `./scripts/verify.sh` → `VERIFY OK (quick)`（后端 hermetic + 前端 type-check）。
- `npm run type-check` / `npm run build` → 绿；`npm run lint` → oxlint 18 errors **全部存量**（scripts/d2-walkthrough、d3 走查脚本、composables 等），改动文件 0 命中，基线自 15 漂移至 18（D2/D3 走查脚本带入），**0 新增**；`git diff` 确认 lint --fix 无误改无关文件。
- `DASH_USER/DASH_PASS node scripts/dash-shots.mjs scripts/output/dash-shots-D4` → 66 张 `OK: no overflow, no page error`，overflowX 全 0。
- 位置断言 `scripts/output/d4-check.mjs`（一次性，不入库）：classic-light 1440 下 10 个 PageHeader 路由 `.ui-page-title` **left=256.00 / top=32.00 全部一致（Δ=0px）**；合集编辑页返回钮点击 → `/dashboard/collections`（不离站）→ **10/10 PASS**。
- paper-light DOM 断言 `scripts/output/d4-dom-check.mjs`：**17/17 PASS**——5 页标题对齐（含知识库页）、settings 副标题+2×`card-solid p-6` 无 rounded-2xl、collections 单容器 `card-solid.divide-y`+`ui-badge`、categories `ui-table`/`ui-table-headrow` computed（透明底、`border-strong` 下边、secondary 12px 文字、徽标方角）、PostEditor h1 衬线 900。
- classic-light 回归（PIL 像素 diff vs `dash-shots-baseline`）：21/22 张 DIFF 属预期——间距归一化（16/24px 混用 → 统一 20px）波及所有后台页；desktop 变化量 0.17%–6.69%（posts 6.69% 含相对时间/计数数据漂移，同 D2 观察口径；posts_new 0.17% 近零印证 PostEditor 仅加 hook class）；mobile 0.95%–9.53%（卡片流式重排+间距级联）；`posts_new` mobile 字节级 IDENTICAL。无横向溢出、无 pageerror。
- 截图目检项（知识库标题不再右下偏、设置页副标题、合集编辑左对齐+返回钮、分类表头同款、合集列表单容器分隔线）已由上面 DOM/computed 断言等价覆盖；人工目检建议终验时补看 `dash-shots-D4/` 对应页。

## 走查记录（2026-11-16）

脚本 `mysite-frontend/scripts/d4-walkthrough.mjs`（入库，同 d2/d3 惯例）：**108 pass / 0 fail / 0 skip**——三主题桌面全量矩阵 + 结构断言 + 交互 + paper-light 移动抽查。应用代码零改动。

- §1 三主题 × 10 个 PageHeader 路由：`.ui-page-title` left/top 逐页全一致（Δ≤1px）；页面根可见子元素相邻间距全部 =20px（30 页 × 若干间隙，含 AI 页签切换后、知识库选中详情面板）。
- §2 paper-light 结构：knowledge 根类、settings 副标题+2×`card-solid p-6` 无 rounded-2xl+内层 20px、collections 单容器 divide-y+行内 Badge、categories `ui-table`/`ui-table-headrow`/`tabular-nums`/状态 Badge、comments 单容器结构保持、合集 new/edit `PageHeader back`+`max-w-4xl`+返回回 `/dashboard/collections`（不离站）、dash-mode 挂载/摘除生命周期。
- §3 逐主题 computed：headrow 底（paper 透明 / classic bg-code）、下边色（paper `--border-strong` / classic `--border`）、文字色（paper secondary / classic muted）+12px/500；徽标圆角 paper 0 / classic 非 0；card-solid 圆角 paper 0 / classic 14px；页标题衬线+900（paper）vs 无衬线（classic）；PostEditor h1 同规则。
- §4 paper-light 390px：collections/categories/knowledge/settings/posts-new 无横向溢出 + `.ui-page-title` 存在。
- 首轮 5 个 FAIL 全部为脚本自身缺陷、非应用问题：①分类页无 `.card-solid`（圆角改到合集页量）；②`/serif/i` 会命中 `sans-serif` 子串（改末位 generic token 判定）。修正后复跑全绿。
