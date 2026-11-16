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
