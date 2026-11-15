# UI 重构：纸本方向（2026-11-15）

> 基于 `11-15-ui-audit` 的结论。先出了三个方向（A 纸本 / B 工程师笔记本 / C 对话即入口），**定稿为 A 纸本打底 + C 的提问交互**。
> 概念稿为静态 HTML，不动应用代码。

## 产物

| 文件 | 内容 |
|---|---|
| `final/paper.css` | 纸本 token（日间/夜间）+ 顶栏/提问框/出处卡等共用样式 → 落地时对应 `styles/themes/paper.css` |
| `final/home.html` | 首页定稿，响应式 |
| `final/post.html` | 文章详情定稿，响应式；`?sheet=ask\|toc`、`?theme=dark`、`?scroll=N` 用来演示状态 |
| `final/mobile.html` | 4 台 390×844 手机框：首页 / 详情 / 问这篇 sheet / 目录 sheet（夜间） |
| `a-/b-/c-*.html` | 初版三方向，存档 |

预览：`python3 -m http.server 5273 --bind 127.0.0.1 --directory .trellis/tasks/11-15-ui-concepts`（5190 是 study-journey 手帐概念稿在用，别撞）。
自检：Playwright 截图 1440 / 390 两档，横向溢出 0；修了 drop cap 误伤评论区、移动端 grid `1fr` 被代码块撑破（改 `minmax(0,1fr)`）。

## 设计要点

- **身份**：刊头式站名 + 副标题 + 作者卡，替掉默认的 “MySite / Admin”；数据来自站点配置。
- **字体**：标题和正文用衬线（Noto Serif SC），UI 和元信息用无衬线；数字和代码用等宽。
- **颜色**：纸色 `#f6f1e7`、墨色 `#1d1a16`、锈红强调色 `#b4441f`；夜间是暖黑底 `#16130f` 配橙色 `#e0794f`。卡片用硬阴影 `6px 6px 0`，不用圆角和玻璃效果。
- **首页层次**：置顶头条（带封面）+ 最受欢迎 4 篇 → 提问区（示例问题 + 带出处的示例回答）→ 最近更新（带缩略图，分页窗口化）+ 合集进度侧栏 + 作者卡。
- **详情页**：面包屑（替掉 `router.back()`）、合集徽标（第 N/M 篇）、封面头图、首字下沉、`§ 01` 编号的 h2、选中文字后弹出“问 AI 这段”、右栏目录 + 阅读进度 + “问这篇”小卡，文末是合集上一篇/下一篇、继续读、评论。
- **移动端**：文章页底部固定工具条（目录 / ✦问这篇 / 收藏），替掉现在的 TOC 浮钮和聊天 FAB；目录和聊天都改成底部 sheet。
- **AI 文案**：统一写“登录后使用 · 每小时 10 次”，和后端限流的实际行为一致。

## 现状核对（决定任务边界）

- 主题：`styles/themes/*.css` 共 7 套，`useTheme.ts` 的 `THEME_REGISTRY` 注册，默认 `classic-light`；token 命名（`--bg-primary`/`--accent`…）可以直接复用。
- 站点配置：`SiteConfigController` **把 MySite/Admin 硬编码在 Map 里**，前端 `stores/site.ts` 的默认值也是这两个 → 要改成走配置。
- “问这篇”：`ChatWidget` 已经在 `/post/:id` 注入 `articleId`，`api/rag.ts` 和后端 `RagChatController` 都支持 → 只需要 UI 入口和“预填问题”的能力。
- 合集信息：`Article.collectionTitle/collectionSortOrder` + `getArticleNavigation` 已有 → 徽标和文末导航不需要改后端。
- 置顶：`t_article` **没有** `is_top` 一类的字段 → 要做 migration（§2.4 先评审）；做之前头条用“最新一篇”顶上。
- 字体：`index.html` 里的 `Inter Fallback` @font-face 是死代码。

## 已拍板（2026-11-15）

1. **旧主题保留**：另外 6 套主题（含 classic）继续可选，T13 之后看使用情况再决定下不下线。
2. **聊天 FAB**：首页（有提问区）和文章页（有底部 dock / 右栏小卡）**不显示** FAB；其他前台页保留。
3. **衬线 webfont 不预先退让**：先上完整方案（正文 + 标题都用 Noto Serif SC 子集），重构完成后在 T16 性能走查时实测，不达标再退到“只给标题用”。

## 开发总约定（所有任务通用）

- **对照稿**：每个任务开工前打开对应的 `final/*.html` 并排看；实现以概念稿为准，概念稿没覆盖的状态（loading / empty / error）沿用现有骨架屏和 EmptyState，换成纸本 token。
- **只用 token，不写死颜色**：组件里只用 `bg-bg-primary` / `text-text-muted` / `border-border` / `text-accent` 这类 Tailwind token 类（`main.css` 的 `@theme` 已映射）。新增 token 只加在 `tokens.css` 默认值 + 每套主题文件里，且**所有 7+2 套主题都要给值**，否则旧主题会掉色。
  - 本次新增 token：`--border-strong`（墨线 / 硬阴影色）、`--shadow-hard`、`--font-serif` 改指 webfont。
- **视觉语言**：卡片和容器用 `border` + 方角（`rounded-none`），强调块用 `--shadow-hard`；不在纸本主题里用 glass 效果。为了不破坏旧主题，方角 / 硬阴影写成主题作用域的工具类（例如 `.paper-card`），在 `[data-theme^="paper"]` 下才生效，其他主题回落到现有的圆角卡片。
- **字体分工**：衬线 = 标题、正文、引用、提问输入框；无衬线 = 导航、按钮、元信息、标签；等宽 = 代码、快捷键提示。
- **断点**：沿用 Tailwind；概念稿的 900px 断点对应 `lg`（1024）以下切单栏、`sm`（640）以下用移动端交互（dock / sheet）。ChatWidget 现在 `isMobile = max-width: 639px`，保持一致。
- **前台布局宽度**：`DefaultLayout` 的 `max-w-[1080px]` 改成 `max-w-[1180px]`（概念稿宽度），dashboard 布局不动。
- **可访问性**：可点击元素保持 `button` / `RouterLink` 语义；sheet 和 popover 支持 Esc 关闭、焦点回到触发按钮；纸本日间 `--text-muted #7d7466` 在 `#f6f1e7` 上约 4.5:1，12px 元信息不要再调浅。
- **不做**：不改 dashboard 页面的布局（只要它在纸本主题下能看）；不升级依赖；不顺手格式化无关文件。
- **每个任务的验收**（缺一不算完成）：
  1. `./scripts/verify.sh`（quick）通过；
  2. `cd mysite-frontend && npm run lint && npm run build` 通过；
  3. Playwright 截图 1440×900 与 390×844 两档，和 `final/*.html` 对照；页面横向溢出为 0（`document.documentElement.scrollWidth === innerWidth`）。截图脚本可参考 `/tmp/concept-shots/shot.mjs`，建议沉淀成 `mysite-frontend/scripts/ui-shots.mjs`（T1 顺手做）；
  4. 至少切一次 paper-light / paper-dark / classic-light 三套主题，确认没有掉色；
  5. 任务目录 `.trellis/tasks/<MM-DD>-ui-Tn-<名>/prd.md` 留痕，`.trellis/CHANGES.md` 追加一行。

## 实现任务

依赖顺序：T1/T2/T3/T5 可并行 → T4 → T6 → T7 → T8 → T9/T10/T11 → T12 → T13；T14、T15 独立，评审通过后随时插入；T16 收尾。

### T1 纸本主题 token

- **目标**：新增 `paper-light` / `paper-dark` 两套主题，可在主题切换器里选到；默认主题暂不改。
- **改动**：
  - 新增 `src/styles/themes/paper.css`：两个 `[data-theme="paper-light|paper-dark"]` 块，变量全集照抄 `classic.css` 的结构（含 code token、semantic 色、glass 变量）。取值以 `final/paper.css` 为准；code token 用暖色系（参考 post.html 代码块：关键字 `#e0794f`、字符串 `#b5c98a`、注释 `#7d7466`、函数 `#e9c46a`）。
  - `main.css` 里 `@import` 新文件；`tokens.css` 默认值和其他主题补 `--border-strong`、`--shadow-hard`（旧主题可以给 `--border` 和普通阴影，视觉不变）。
  - `useTheme.ts`：`ThemeId` 加两个 id；`THEME_REGISTRY` 加两项（group `paper`，互为 light/dark variant，preview 用纸色 / 锈红）；`isThemeDark` 认 `paper-dark`。
  - 纸本作用域工具类（`.paper-card`、`.paper-hard-shadow` 等）放在 `paper.css` 尾部。
- **消费点**：`ThemeToggle.vue`（菜单渲染 registry）、`SettingsView` 有主题设置的话也要看一眼。
- **验收**：切到 paper-light / paper-dark，前台和 dashboard 页面都能正常阅读，代码高亮颜色协调；其他主题截图前后无差异。

### T2 衬线 webfont

- **目标**：跨平台的衬线渲染一致，不再依赖本机装没装 Noto Serif SC。
- **改动**：
  - 字体文件放 `mysite-frontend/public/fonts/`：Noto Serif SC 400 / 700 / 900，按 unicode-range 分片子集（拉丁 + 常用 3500 汉字 + 标点为第一片，其余按需），woff2。子集化工具（如 `fonttools pyftsubset`）只在本地跑，不加进项目依赖；在任务 prd 里记录生成命令和字符集来源，方便重新生成。
  - 新增 `src/styles/fonts.css`：`@font-face`，`font-display: swap`；在 `main.css` 引入。
  - `tokens.css` 的 `--font-serif` 第一项改成自托管字体名，后面保留系统回退。
  - `index.html`：删掉没人引用的 `Inter Fallback` @font-face；给首屏字重（400、900 的第一片）加 `<link rel="preload" as="font" crossorigin>`。
  - Nginx（`deploy/nginx/mysite.conf`）确认 `/fonts/` 有长缓存和 woff2 MIME —— 只核对，需要改的话按 §2.5 单独提。
- **验收**：macOS / Windows（或无 Noto 的 Linux 容器）截图里标题字形一致；记录首屏字体总传输体积，写进任务 prd（T16 要用）。
- **注意**：这次先不设体积门槛（已拍板）。

### T3 站点配置真实化

- **目标**：站名、副标题、作者、简介、社交链接都来自配置，前端不再出现 “MySite / Admin”。
- **后端**：
  - 新增 `config/SiteProperties`（`@ConfigurationProperties("site")`）：`title`、`subtitle`、`description`、`author`、`authorBio`、`authorTagline`（如“Java 后端 · 在杭州”）、`avatar`、`url`、`github`、`rss`、`commentEnabled`、`navigation`。
  - `application.yaml` 加 `site:` 段，值用 `${SITE_TITLE:MySite}` 这种带默认值的环境变量，真实值走 `.env`；`.env.example` 补占位。
  - `SiteConfigController` 改为返回 `SiteProperties` 序列化结果，接口路径和 permitAll 不变。
- **前端**：
  - `types` 里的 `SiteConfig` 扩字段（全部可选，兼容旧响应）；`stores/site.ts` 默认值改成中性占位，不写死 MySite。
  - 标题模板集中化：在 `App.vue`（或 DefaultLayout）用 `useHead({ titleTemplate: t => t ? \`${t} - ${site.title}\` : site.title })`；所有视图里的 `- MySite` 后缀删掉，只传页面标题（上面 grep 出来的 25 处，含 dashboard 和 `DashboardSidebar` 的 “MySite Admin”）。
- **纪律**：§2.5 配置变更 —— 开工前先在任务 prd 里列方案和 yaml 键名，确认后再写。
- **验收**：改 `.env` 里的 `SITE_TITLE` 重启后，浏览器标签页、顶栏、页脚、About 全部跟着变；`grep -rn "MySite" mysite-frontend/src` 只剩注释或零结果。

### T4 顶栏 / 页脚

- **对照**：`final/home.html` 顶栏、页脚；移动端见 `mobile.html` ①。
- **AppHeader**：
  - 左：衬线 logo，`site.title`；如果标题里有分隔点 `·`，就用 accent 色渲染（可以做成 `site.titleAccent` 配置或简单字符串替换）。
  - 中：导航取 `site.navigation`，当前项下划 2px accent；文章页不高亮任何导航项。
  - 右：搜索提示框（点击 / ⌘K 打开现有 `SearchDialog`，文案“搜索或提问”）、明暗切换（在当前主题的 light/dark variant 间切；没有 variant 的主题保持现有行为）、登录按钮 / 已登录的用户菜单（保留现有逻辑）。
  - 移动端：只保留 logo、明暗切换、菜单按钮；菜单展开为全宽下拉，包含导航和搜索入口。
  - 背景：`bg-primary` 88% + `backdrop-blur-sm`，下边框 `border`。
- **AppFooter**：左版权 + 一句话；右链接：归档 / GitHub / RSS / 关于；`site.github`、`site.rss` 为空就不渲染对应链接（**目前后端没有 RSS 端点**，见“后续”）。
- **验收**：桌面 / 移动截图对照；⌘K 仍可打开搜索；登录态菜单不回归。

### T5 统一分页 + 工具函数

- **目标**：前台只剩一套分页组件，页数多时窗口化显示。
- **改动**：
  - 以 `components/ui/Pagination.vue` 为唯一实现，补：窗口化（当前页 ±1、首尾页、省略号）、`compact` 模式（移动端只显示 当前页 / 总页数 + 上下页）、上一页 / 下一页文字按钮。
  - 替换 `article/ArticlePagination.vue`（被 `ArticleList.vue` 使用）、`CollectionsView` 和 `CollectionView` 手写的上一页 / 下一页；替换完删掉 `ArticlePagination.vue`。
  - 新增 `utils/format.ts` 的 `formatViewCount`（1.2k / 3.4w 规则以现有实现为准），替换 `CollectionCard.vue` 和 `CollectionsView.vue` 里的两份重复实现；文章卡片里的阅读量也统一用它。
- **消费点**（共享层，改前再 grep 一遍）：`DataTable.vue`、`DashboardView`、`ImageManagerView`、`KnowledgeManageView`、`CollectionManageView`、`UserManageView`、`UserProfileView`、`CommentManageView`。后台的调用方式不能变（props 向后兼容）。
- **验收**：首页、分类、标签、合集、后台各分页都能翻；构造 50 页的数据（或 mock total）时按钮不超过 7 个。

### T6 首页重排

- **对照**：`final/home.html`、`mobile.html` ①。
- **结构**（自上而下，均为 `BlogHome.vue` 下的新组件，放 `components/home/`）：
  1. `HomeMasthead`：日期行（今天日期 + “第 N 篇”，N = 文章总数，取分页 total）、大号衬线站名、副标题 `site.subtitle`。只在第 1 页、且没选分类时显示；翻页或筛选时收起，只保留分类条。
  2. `CategoryStrip`：替换现有分类 pill，横向滚动，带篇数；保留选中分类 → `fetchArticles(1)` 的现有逻辑和 URL 行为。
  3. `HomeLead`：左边头条（kicker = 置顶 / 分类，标题，摘要，封面，byline），右边“最受欢迎”4 篇（`getArticles({ sortField:'viewCount', sortOrder:'desc', size:4 })`，带序号）。头条在 T14 之前取“最新一篇”，并从下方列表里去重。无封面时头条不渲染封面块，标题字号保持。
  4. 提问区占位：留一个插槽，T7 填。
  5. 主栏 `ArticleRow` 列表：kicker、衬线标题、摘要、元信息（日期 · 阅读时长 · `formatViewCount`）、右侧 4:3 缩略图（有封面才显示）；移动端隐藏摘要、缩略图缩到 96px。排序下拉保留现有四项。
  6. 侧栏：合集（罗马数字序号、篇数、连载中 / 已完结、进度条）+ 作者卡（`site.avatar/author/authorTagline/authorBio` + 链接）。
- **替换 / 清理**：`ArticleCard.vue` 如果首页之外没人用就删掉（先 grep：`CategoryView`、`TagView`、`SearchView`、`FavoritesView` 大概率在用 → 那就让它们也改用 `ArticleRow`，在本任务一起做，保证列表风格统一）。
- **验收**：首页三种状态（第 1 页、翻页、选分类）截图；无封面文章、无合集、零文章（EmptyState）都不崩。

### T7 提问区 + ChatWidget 预填能力

- **对照**：`final/home.html` “不想翻目录？直接问。” 区块。
- **ChatWidget 能力扩展**（先做，这是 T9/T10 的基础）：
  - 新增 `composables/useChatLauncher.ts`（模块级单例状态）：`open(opts?: { question?: string; quote?: string; send?: boolean })`、`isOpen`。`ChatWidget` 内部的 `isOpen` 改成从这里读，原有 FAB 行为不变。
  - `quote` 渲染为输入框上方的引用条（可删除），发送时拼成“> 引用\n\n问题”。
  - FAB 隐藏规则：`route.name` 是首页或文章页时不渲染 FAB（已拍板），面板仍可由 launcher 打开。
- **`HomeAskPanel`**：
  - 输入框（衬线 17px）+ 范围标签“全部文章 ▾” + 提问按钮；示例问题 chips（先写死 4 条，放进 `site` 配置作为可选项 `askExamples`）；下方静态示例回答 + 2 张出处卡（也来自配置，没配就不显示示例块）。
  - 范围：文章会同步进**所有启用的知识库**（`KnowledgeDocumentService` 遍历 enabled KB），所以“全部文章”= 选中全部可用 KB。用现有的 KB 列表接口；没有可用 KB 时整个面板降级为一行提示“AI 助手暂未开放”。
  - 提交：已登录 → `launcher.open({ question, send: true })` 并带上 kbIds；未登录 → 跳 `/login?redirect=/`，并把问题暂存到 sessionStorage，登录回来后自动打开面板并填好。
  - 文案“登录后使用 · 每小时 10 次”：额度按角色从现有限流配置取（ADMIN 不显示额度）。
- **消费点**：`ChatWidget.vue`、`useChat.ts`（不要改 `api/rag.ts` 的签名，§2.3 共享层）。
- **验收**：未登录点击提问 → 登录 → 回首页自动打开面板并发出；chips 点击会填进输入框；聊天在其他页面的打开 / 关闭行为不回归。

### T8 文章页重做

- **对照**：`final/post.html`（桌面）、`mobile.html` ②。
- **头部**（`BlogPost.vue`，抽 `components/article/PostHeader.vue`）：
  - 面包屑 `首页 / 分类`，**删掉 `router.back()` 按钮**；有合集时在 kicker 旁显示合集徽标（`collectionTitle` + 第 `collectionSortOrder` / 总数篇，总数取 `navInfo`），点击进合集页。
  - 衬线 h1（桌面 48px，移动 30px），摘要作为 dek，byline 行：头像（`site.avatar` 或 gravatar）、作者、日期、阅读时长、阅读量，右侧收藏 / 分享（分享 = `navigator.share`，不支持就复制链接 + toast）。
  - 封面 `coverImage`：21:9（移动端 16:10 出血），用 `OptimizedImage`，**首屏不要 lazy**；没封面就不渲染。
- **正文**（`typography.css` 的 `.prose`，§2.3 共享层：目前只有 `ArticleContent.vue` 用 `.prose`，About 用的是死 class，影响面可控）：
  - 衬线 18px / 行高 1.95，正文宽度 ≤ 700px；首段首字下沉（只作用于 `.prose > p:first-of-type`，概念稿踩过误伤评论区的坑）；h2 上边框 + CSS counter 生成的 `§ 01` 小字编号；链接 accent 下划线；blockquote 左 3px accent 竖线；callout、代码块、图片说明换纸本风格（代码块深色底保持不变，和 `code.css` 的 token 对齐）。
  - 纸本风格的 `.prose` 规则写在 `[data-theme^="paper"] .prose` 下，旧主题的正文排版不变。
- **右栏**（lg 以上）：`ArticleToc` 换肤（左边 2px 竖线，当前项 accent）+ 底部“已读 N% · 剩约 M 分钟”（复用 `useScrollProgress`）+ `AskArticleCard`（输入框 + 2 个 chips，提交 → `launcher.open({ question, send:true })`，`articleId` 由 ChatWidget 现有逻辑注入）。
- **文末**：标签 + 收藏 / 分享；合集导航（`ArticleNav.vue` 换肤：进度点 + 上一篇 / 下一篇）；“继续读”3 篇（`getArticles({ categorySlug, size:4 })` 去掉当前篇，后续可以换成向量相似度）；评论区换肤（`CommentSection` / `CommentItem` 只改样式）。
- **顺手修**：`useHead` 的 `og:image` 保留；加 `article:published_time`。
- **验收**：有封面 / 无封面、有合集 / 无合集、仅自己可见文章（锁提示条）四种情况截图；长文（126 个标题那篇）目录高亮正常。

### T9 移动端文章 dock + sheet

- **对照**：`mobile.html` ②③④。
- **改动**：
  - 新增 `components/article/PostDock.vue`：`sm` 以下在文章页底部固定（左右 12px、距底 12px + `env(safe-area-inset-bottom)`），三格：目录（显示已读 %）/ ✦ 问这篇（accent 底）/ 收藏（显示数量，复用 FavoriteButton 逻辑）。
  - `MobileTocDrawer.vue` 改成底部 sheet 形态，由 dock 控制打开，删掉它自己的 FAB（`useFabStack('mobile-toc')` 注销）。
  - `ChatWidget` 移动端已经是 sheet：换肤成纸本（顶部抓手、标题“✦ 问这篇文章”+ 当前文章标题、出处卡、底部 chips + 输入框）；文章页隐藏 FAB（T7 已做）。
  - `BackToTop` 在文章页移动端让位给 dock（往上避让或隐藏）。
  - 虚拟键盘：沿用 ChatWidget 现有的键盘适配，不要回归。
- **验收**：390×844 下 dock 不挡正文最后一段（正文底部留出 dock 高度的 padding）；sheet Esc / 点遮罩 / 下滑可关闭；iOS Safari（或 Playwright webkit）跑一遍。

### T10 选中文字问 AI

- **改动**：`ArticleContent.vue` 内加 `SelectionPopover.vue`：监听 `selectionchange` / `mouseup`，选区在 `.prose` 内且长度 5–500 字时，在选区上方显示「✦ 问 AI 这段 / 引用 / 复制」。
  - 问 AI → `launcher.open({ quote: 选中文本 })`，光标落在输入框；
  - 引用 → 未登录跳登录；已登录滚动到评论框，并以 `> 选中文本` 预填；
  - 复制 → 剪贴板 + toast。
- **范围**：只在桌面端（pointer: fine）启用，移动端用系统菜单（概念稿已去掉移动端 popover）。
- **验收**：跨段选择、选到代码块、选区滚出视口时 popover 的位置 / 消失都正确；不影响 TOC 点击和图片预览。

### T11 聊天面板换肤

- **改动**：`ChatMessageItem`（用户气泡用 `bg-secondary` 方块，AI 回答用衬线正文，`[n]` 角标用 accent）、`ChatSources`（出处卡：`[n] 标题` + `§章节 / 分类`，hover 描边 accent，点击跳文章锚点）、`ChatInput`（衬线输入框 + 墨色发送按钮）、`ChatWidget` 头部。
- **范围**：只改样式和标记结构，**不改** `useChat` 的状态机、流式渲染节流和 `useChatMarkdown`（§2.3）。
- **验收**：流式输出、截断 / 失败状态、历史会话列表在纸本主题下都能看；旧主题无回归。

### T12 404 + About

- **404**：router 在 DefaultLayout children 末尾加 `{ path: ':pathMatch(.*)*', name: 'not-found', component: NotFoundView }`。`NotFoundView`：大号衬线“404”、一句文案、回首页按钮、搜索入口和“最受欢迎”3 篇。文章 id 不存在时 `BlogPost` 的错误态也引导到同样的样式（不改 URL）。
- **About**：读 `site.authorBio` 等配置字段（长文可以支持 markdown，用 `useMarkdown` 渲染进 `.prose`）；删掉死 class `prose-custom`；加作者卡、链接、站点技术栈一句话。
- **验收**：随便访问一个不存在的路径显示 404 且停留在站内；About 内容随配置变化。

### T13 切换默认主题

- **改动**：`useTheme.ts` 默认值 `classic-light` → `paper-light`；“跟随系统”时暗色映射到 `paper-dark`。**storage key `mysite_theme_v2` 不改**，已经手动选过主题的用户保持原样。
- **独立 commit**，回滚只需要 revert 这一个提交。
- **验收**：清掉 localStorage 后首次访问是纸本；系统切暗色后自动切到 paper-dark。

### T14 文章置顶（需先评审，§2.4）

- **DB**：`docker/init/migrations/<date>-article-top.sql`：`ALTER TABLE t_article ADD COLUMN is_top TINYINT NOT NULL DEFAULT 0 COMMENT '置顶'` + `top_time DATETIME NULL`（用于多篇置顶时排序），同步更新 `schema.sql`。
- **后端**：`ArticleDO` / 列表和详情 DTO 加 `isTop`；列表查询支持 `isTop` 过滤；后台“设为置顶 / 取消”接口（仅 ADMIN / 作者本人）；Redis 里文章列表的缓存要失效。
- **前端**：Dashboard 文章表加置顶开关；`HomeLead` 优先取置顶（多篇取 `top_time` 最新那篇），没有置顶就回落到最新一篇；kicker 同步把 T6 的「最新」占位换成「置顶」。
- **评审要点**：缓存失效范围、置顶是否影响分页 total。

### T15 匿名聊天死代码清理（需先评审，§2.2）

- **背景**：后端 `ChatRateLimiter` 对 role == null 直接拒绝，前端还在展示游客额度、匿名提示和 visitorId 流程（见 `11-15-ui-audit`）。
- **改动**：前端删掉游客额度 / 匿名提示条，未登录统一显示“登录后使用”；`visitorId` 参数后端仍接受的话，前端可以保留传参，但不要再暴露在 UI 上（是否删管道在评审时定）；同步更新 CLAUDE.md 的 “Anonymous chat” 一节和 `.cursor/rules/ragent.mdc`。
- **评审要点**：是否保留未来恢复游客额度的开关；会话历史的 visitorId 归属。

### T17 合集连载状态与进度（需先评审，§2.4）

- **背景**：T6 侧栏只做了「罗马数字 + N 篇」，概念稿还有「连载中 / 已完结 / 完成度」；`t_collection` 现只有 `article_count`，DTO 无 planned/finished（T6 偏差①收口）。
- **DB**：`docker/init/migrations/<date>-collection-status.sql`：`ALTER TABLE t_collection ADD COLUMN planned_count INT NULL COMMENT '计划总篇数' ADD COLUMN status TINYINT NOT NULL DEFAULT 0 COMMENT '0连载中 1已完结'`，同步 `schema.sql`。
- **后端**：`CollectionDO` + 列表/详情 DTO 加 `plannedCount`、`status`；管理端合集编辑接口接受两字段（仅 ADMIN / 作者本人）。
- **前端**：管理端合集表单加「计划篇数 / 状态」；`HomeSidebar` 合集条目 `N 篇` → `N / planned 篇` + 进度条，status=完结时显示「已完结」；`/collections` 合集卡同步。
- **评审要点**：`planned_count` 为 NULL 的展示形态（只显篇数还是估算进度）；`articleCount > planned` 的处理；status=已完结 与 planned_count 非空的约束关系。
- 只依赖 T6 已落地的 HomeSidebar，与其他任务无交叠，可在 T14/T15 评审时一并过 schema。

### T16 性能 / 体验走查（收尾）

- **度量**：Lighthouse（移动端 Slow 4G 模拟）跑首页和长文页，记录 LCP / CLS / TBT / 总传输量 / 字体传输量；和重构前（先在 T1 开工前跑一次基线，存进任务 prd）对比。
- **字体判定**（已拍板的退路）：如果字体导致 LCP 比基线恶化超过 500ms，或首屏字体传输超过 400KB → 退到“只给标题用 webfont 子集，正文回落系统衬线 / 无衬线”，并在 prd 里记录数据。
- **其他检查**：封面图尺寸和 WebP（Nginx 已有转换）、`OptimizedImage` 的 srcset、分包（Milkdown 不能进前台 chunk）、夜间模式闪烁（首屏前要先注入 data-theme）。
- **产出**：走查报告写进任务 prd，问题拆成后续任务。

## 后续（不在本轮）

- RSS / Atom 端点（目前后端没有，页脚链接靠配置开关）。
- “继续读”改成向量相似度推荐（复用 pgvector）。
- 旧主题去留（T13 之后观察）。
