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
