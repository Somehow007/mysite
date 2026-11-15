# UI 重构 T10：选中文字问 AI（SelectionPopover）

> 总计划：`.trellis/tasks/11-15-ui-concepts/prd.md` T10 节。对照概念稿 `final/post.html`（`mark.sel > span.pop` 示意）。
> 2026-11-16 开工。

## 需求（prd 原文）

- `ArticleContent.vue` 内加 `SelectionPopover.vue`：监听 `selectionchange` / `mouseup`，选区在 `.prose` 内且长度 5–500 字时，在选区上方显示「✦ 问 AI 这段 / 引用 / 复制」。
- 问 AI → `launcher.open({ quote: 选中文本 })`，光标落在输入框；
- 引用 → 未登录跳登录；已登录滚动到评论框，并以 `> 选中文本` 预填；
- 复制 → 剪贴板 + toast。
- 只在桌面端（pointer: fine）启用，移动端用系统菜单。
- 验收：跨段选择、选到代码块、选区滚出视口时 popover 的位置 / 消失都正确；不影响 TOC 点击和图片预览。

## 现状核对（开工前）

- `ArticleContent.vue` 渲染 `.prose`（v-html），被 **BlogPost + AboutView** 两处消费 → popover 必须按路由门控（`route.name === 'post'`），否则 About 页也会出「引用」死入口（About 无评论区）。
- `useChatLauncher.open({ quote })` 已就绪（T7）：`pendingQuote` → ChatInput 引用条，发送时拼 `> 引用\n\n问题`；桌面端 `applyLaunchOptions` 已 `focus()` 输入框 → 「光标落在输入框」零改动满足。
- 匿名聊天后端 `ChatRateLimiter` role==null 直接拒绝（T15 待清理）；既有提问入口（HomeAskPanel/AskArticleCard）对匿名统一走「sessionStorage 暂存 + 跳登录 + 回跳自动恢复」。
- `CommentSection` 评论框 `newContent` 是组件内 ref，无预填通道；评论按纯文本渲染（`whitespace-pre-wrap`），`> ` 前缀按 spec 原样预填。
- `ChatWidget` 挂在 `DefaultLayout`（defineAsyncComponent），登录页也在 DefaultLayout 内 → 面板跨路由存活。
- AppHeader `sticky top-0 z-50`；chat panel z-50；post-dock z-35 → popover 取 z-40。
- 图片无 lightbox（仅编辑器预览/图床管理 hover），「不影响图片预览」= 不阻断点击/拖拽。

## 方案

### 新文件

1. **`components/article/SelectionPopover.vue`**
   - 由 `ArticleContent` 渲染为 `.prose` 同级节点，内部 `<Teleport to="body">` + `position:fixed`（避开 GSAP 进场 transform 对 fixed 的包含块影响）。
   - 常驻 DOM、`visibility:hidden` 控制显隐（保证 offsetWidth/Height 可测量用于定位 clamp）。
   - 启用条件：`route.name === 'post'` && `matchMedia('(pointer: fine)')`（监听 change）。
   - 监听（document）：`selectionchange`（rAF 节流；鼠标按住拖选中不显示）、`mouseup`（拖选结束定稿评估）、`mousedown`（置 dragging；目标在 popover 外立即隐藏）、`scroll`(capture)+`resize`（重算/隐藏）、`keydown` Esc（`removeAllRanges` + 隐藏）。
   - 显示条件：选区非折叠、anchor/focus 两端都在 container（`.prose`）内、清洗后文本长 5–500、拖拽未进行、首个有效 clientRect 非空且与视口相交。
   - 定位：首个 clientRect（跨段取首行）上方居中，横向 clamp 到视口；上方空间不足翻到选区下方；选区整体滚出视口 → 隐藏，滚回仍有效 → 重定位显示。
   - `@mousedown.prevent` 在 popover 根上：点击按钮不清掉文章选区。
   - 动作：
     - `✦ 问 AI 这段`：登录 → `launcher.open({ quote })`；匿名 → `stashPendingQuote(quote, fullPath)` + `router.push login?redirect=fullPath`。
     - `引用`（`site.commentEnabled === false` 时不渲染该钮）：登录 → `setCommentDraft({ quote, articleId })`；匿名 → `stashCommentQuote(articleId, quote)` + 跳登录。
     - `复制`：`navigator.clipboard.writeText` + `toast.success`，失败 `toast.error`。
   - 任何动作后 `hide()`；`watch(launcher.isOpen)` → 面板打开即隐藏（覆盖 dock/卡片等其他打开路径）。

2. **`composables/useCommentDraft.ts`**
   - 模块级 `draft = ref<{ quote, articleId } | null>`：登录态原地引用的实时通道（仿 useChatLauncher 单例）。
   - sessionStorage `mysite_pending_comment` `{quote, articleId}`：匿名→登录→回跳的一次性暂存（仿 usePendingAsk 语义：读取即清、articleId 不匹配丢弃）。

### 改动

3. **`composables/usePendingAsk.ts`**：同文件追加 `stashPendingQuote` / `takePendingQuote`（sessionStorage `mysite_pending_quote`，path 作用域一次性），复用 PendingAsk 的 path 匹配语义。

4. **`components/article/ArticleContent.vue`**：模板 `.prose` 后挂 `<SelectionPopover :container="contentRef" />`；content watch 内 `render()` 后 `window.getSelection()?.removeAllRanges()`（换文清选区，防 popover 残留）。

5. **`components/comment/CommentSection.vue`**
   - 根 div 加 `ref` + `scroll-mt-24`（AppHeader sticky 80px 避让）。
   - `watch(articleId, {immediate:true})` 合并现有 fetchComments + `takeCommentQuote(articleId)`：命中且 `hasAuthSession()` → 500ms 后 `applyQuote(q, 'auto')`（等 router scrollBehavior top:0 落地再跳）。
   - `watch(draft)`：articleId 匹配 → 清 draft + `applyQuote(quote, 'smooth')`。
   - `applyQuote`：`> ` 逐行前缀 + 空行；已有内容则引用块置顶拼接；`scrollIntoView` 到评论区根。

6. **`views/BlogPost.vue`**：onMounted 在 pendingAsk 之后 `takePendingQuote(route.fullPath)`，命中且 `hasAuthSession()` → `launcher.open({ quote })`（else-if，防双暂存互相覆盖）。

7. **`styles/post.css`**：`.sel-pop`（fixed、bg-elevated、border-strong、radius-md、shadow）+ `.sel-pop-btn`（sans 13px、hover accent-subtle）+ `[data-theme^="paper"]` 方角/硬阴影覆盖。全 token，无写死色。

## 匿名链路决策（超出 prd 字面、服务于意图）

- 「问 AI 这段」匿名 → 暂存 quote 跳登录，回跳自动打开面板带引用条（对齐 HomeAskPanel/AskArticleCard 既有模式；不暂存则登录后引用丢失）。
- 「引用」匿名 → 暂存 comment quote 跳登录，回跳评论框预填（同理；匿名是博客主力读者，不暂存等于功能对多数人不可用）。

## 验收计划

- `./scripts/verify.sh` quick + `npm run lint` + `npm run build`
- `scripts/t10-verify.mjs`（Playwright，dev server 5173 + 后端 8081 已在跑）：
  - 桌面 paper-light/paper-dark/classic-light：选区 → popover 出现、位置在选区上方、三按钮；
  - 跨段选择、代码块内选择、选区滚出视口隐藏/回位、Esc、点击外部消失、<5/>500 字不显示、选标题等 .prose 外不显示；
  - 问 AI（登录）→ 面板开 + 引用条 + 输入框聚焦；匿名 → 跳登录 + sessionStorage 暂存；
  - 引用（登录）→ 滚动到评论框 + `> ` 预填；匿名 → 跳登录；
  - 复制 → clipboard 内容核对；移动端 hasTouch → 无 popover；
  - TOC 点击、文章内锚点不受影响；横向溢出=0。

## Resolution（2026-11-16）

### 落地文件

- 新增 `components/article/SelectionPopover.vue`：Teleport 到 body + fixed 定位，常驻 DOM `visibility` 控显隐；启用条件 `route.name==='post' && pointer:fine`（AboutView 复用 ArticleContent 但无此路由，天然门控）；rAF 节流评估，拖选中隐藏、mouseup 定稿；Esc `removeAllRanges`+隐藏；scroll(capture)/resize 重算，选区滚出视口隐藏、回位重现；popover 内 `@mousedown.prevent` 保选区，mouseup 落在 popover 内不重算（防 click 前闪现）；`watch(launcher.isOpen)` 面板打开即收。
- 新增 `composables/useCommentDraft.ts`：模块级 `draft` ref（登录态实时通道）+ sessionStorage `mysite_pending_comment`（匿名一次性暂存，读取即清、articleId 不匹配丢弃）。
- `composables/usePendingAsk.ts`：追加 `stashPendingQuote`/`takePendingQuote`（sessionStorage `mysite_pending_quote`，path 作用域一次性）。
- `components/article/ArticleContent.vue`：挂 `<SelectionPopover :container="contentRef">`；换文 render 后 `removeAllRanges()` 清旧选区。
- `components/comment/CommentSection.vue`：根 div 加 ref+`scroll-mt-24`；`watch(articleId,{immediate})` 合并 fetchComments+`takeCommentQuote`（命中且 hasAuthSession → 500ms 后 applyQuote 'auto'，等 router scrollBehavior 落地）；`watch(draft)` articleId 匹配即消费；`applyQuote` 逐行 `> ` 前缀、已有草稿置顶拼接、scrollIntoView。
- `views/BlogPost.vue`：`takePendingQuote` 命中且登录态 → `launcher.open({ quote })`。**注意**：该 hunk 在 T12 提交（72f2646）按文件隔离时已先行入库，本次 T10 commit 不含此文件；其依赖的 usePendingAsk.ts 导出随本次一并补上（T12 提交后 HEAD 单独 type-check 会缺导出，本次修复）。
- `styles/post.css`：`.sel-pop` 全 token（bg-elevated/border-strong/radius-md/shadow-color-md）+ `.sel-pop-btn`；`[data-theme^="paper"]` 方角+`--shadow-hard`；z-40（header z-50 / dock z-35 之间）。
- `scripts/t10-verify.mjs`：Playwright 验收脚本（自注册 t10check 账号，覆盖 A–I 九组）。

### 行为决策（相对概念稿的取舍）

- 概念稿是 `mark.sel` 行内高亮+气泡；实现为选区上方浮动 toolbar（Teleport+fixed），更接近 GitHub/Notion 惯例，且不改 prose 排版/不引入高亮 DOM。
- 「问 AI 这段」匿名走 stashPendingQuote→登录→回跳 `launcher.open({quote})`（对齐 T7/T8 既有模式）；「引用」匿名走 stashCommentQuote→回跳评论框预填。复制对匿名直接可用（无后端依赖）。
- 站点 `commentEnabled===false` 时「引用」钮不渲染（无落点）。
- 选区 5–500 字边界按清洗后（trim+换行归一）长度判定；跨段/代码块取首个有效 clientRect 定位。

### 验证结论

- `scripts/t10-verify.mjs`：**55 pass / 0 fail**（复跑两次，含 mouseup 守卫优化后）。覆盖：出现/定位/三按钮、paper 方角硬阴影、classic-light 圆角 8px、跨段+代码块选择、<5/>500 不显示、.prose 外不显示、Esc（藏+折叠选区）、滚出隐藏/回位重现、外部点击、拖动抑制、复制+toast、登录/匿名问AI链路（面板+引用条+登录回跳恢复）、登录/匿名引用链路（评论框预填+滚动+回跳恢复）、移动端 pointer:fine=false 抑制、paper-dark 兼容、About 页门控、横向溢出=0、无 pageerror。
- `./scripts/verify.sh` quick：绿（后端 hermetic + 前端 type-check）。
- `npm run build`：过。`npm run lint`：16 errors 全为存量基线（useMarkdown/useChatMarkdown/t12-walkthrough 等，本任务文件 0 新增）。
- 截图留证：`mysite-frontend/scripts/output/t10/sel-pop-{paper-light,classic-light}.png`。

### 遗留/备注

- t10-debug.mjs、t10-shot.mjs 为调试脚本，已删（截图脚本一并清理）。
- lint 基线 16 errors 为历史存量（CHANGES 多处记录「lint 16 基线」），非本任务引入。
- HEAD 在 T12 提交后短暂缺 usePendingAsk 导出（type-check 会挂），本次 commit 修复；后续如有并行任务注意 usePendingAsk.ts 归属 T10。
