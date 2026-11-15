# UI 重构 T8：文章页重做

> 对照稿：`.trellis/tasks/11-15-ui-concepts/final/post.html`（桌面）、`final/mobile.html` ②。
> 任务定义见 `.trellis/tasks/11-15-ui-concepts/prd.md` T8 节。本文件记开工结论与验收结果。

## 开工结论（实现前核对）

- **`ArticleNavInfo` 缺合集进度数据**：概念稿要「第 N / M 篇」徽标 + ArticleNav 进度点，
  但 `ArticleNavInfoRespDTO` 只有 prev/next/inCollection/collectionId/collectionTitle。
  `article.collectionSortOrder` 是 `t_collection_article.sort_order` 原始值——
  `removeArticleFromCollection` 物理删除不重排，**sort_order 会有空洞**，不能直接当「第 N 篇」。
  → 决定：DTO 新增 `collectionArticles: List<NavArticle>`（id+title，按 sort_order 有序、
  只含当前访问者可见的已发布文章），前端自己 indexOf 出位置与总数，一处数据两处消费
  （PostHeader 徽标 + ArticleNav 进度点）。可见性口径与 `getCollectionDetail` 一致
  （published=1 + visibility 规则，管理员全见）；CACHE_NAV 按 (articleId,userId,isAdmin) 分键，天然正确。
- **`article:published_time` 缺字段**：`ArticleSelectRespDTO` 只有 `updateTime`。
  → DTO 新增 `createTime`（BeanUtil 从 `ArticleDO.createTime` 自动映射），前端 `Article` 类型补可选字段；
  byline 日期改用 createTime（发布时间），meta 加 `article:published_time`/`article:modified_time`。
- **匿名提问链路**：AskArticleCard 未登录提交 → 复用 T7 的 sessionStorage 暂存 +
  `/login?redirect=<当前页>`；登录回跳后由 BlogPost `onMounted` 消费并 `launcher.open({send:true})`。
  → 抽 `composables/usePendingAsk.ts`（stash/take/hasAuthSession），HomeAskPanel 重构复用，
  BlogPost 同样消费；文章页不需要 KB 门槛（articleId 上下文由 ChatWidget 路由注入，与 KB 无关）。
- **移动端提问入口缺口**：概念稿移动端靠 dock「✦ 问这篇」（T9 范围）。T8 先落地桌面右栏
  AskArticleCard；移动端在 T9 dock 落地前无文章页提问入口——已知中间态，不视为缺陷。
- **`.prose` 只被 ArticleContent 使用**（AboutView 是死 class `prose-custom`，T12 处理），
  paper 作用域正文排版可安全写进 `typography.css`。
- **布局**：正文列 `minmax(0,700px)` + 右栏 240px + gap 72px（概念稿 .layout），
  容器仍走 DefaultLayout `max-w-[1180px]`；封面 21:9（≥lg）/ 16:10 出血（<lg，负外边距=容器 padding）。

## 改动清单

### 后端
- `dto/resp/collection/ArticleNavInfoRespDTO.java`：+ `List<NavArticle> collectionArticles`。
- `service/impl/CollectionServiceImpl.java#getArticleNavigation`：合集分支内批量查文章
  （id/title/published/visibility/authorId），按可见性过滤后填充；prev/next 现有逻辑不动。
- `dto/resp/ArticleSelectRespDTO.java`：+ `Date createTime`。
- `service/impl/CollectionServiceImplTest.java`：补 collectionArticles 断言（含私有文章过滤）。

### 前端
- `types/index.ts`：`ArticleNavInfo.collectionArticles?`、`Article.createTime?`。
- `composables/usePendingAsk.ts`（新）：`stashPendingAsk` / `takePendingAsk` / `hasAuthSession`；
  `HomeAskPanel.vue` 改用共享实现（行为不变）。
- 新组件 `components/article/`：`PostHeader.vue`（面包屑/kicker/合集徽标/h1/dek/byline/封面）、
  `ShareButton.vue`（navigator.share → 复制链接 + toast）、`AskArticleCard.vue`（右栏提问卡）、
  `PostRelated.vue`（继续读 3 篇）。
- 换肤：`ArticleNav.vue`（合集头 + 进度点 + 上下篇，新增 `articleId` prop）、
  `ArticleToc.vue`（激活态对齐概念稿）、`CommentSection.vue`/`CommentItem.vue`（纸面列表风）、
  `styles/typography.css`（`[data-theme^="paper"] .prose` 作用域排版）。
- `views/BlogPost.vue` 重写：新布局 + 文末标签/收藏/分享 + 已读进度 + 登录回跳恢复 +
  `useHead` 补 published/modified time。
- `scripts/t8-verify.mjs`（Playwright 验证脚本，沿用 t7-verify 模式）。

## 验收记录

**验证结果（2026-11-15）**

- `./scripts/verify.sh` quick：绿（后端 hermetic 测试全过 + 前端 vue-tsc）。
- `./mvnw test -DexcludedGroups=needs-infra`：32 项 0 失败（`CollectionServiceImplTest`
  新增 collectionArticles 断言通过；imageService null 告警为存量测试桩固有）。
- `npm run build`：通过（chunk 体积告警为存量）。
- `npm run lint`：16 个存量错误（test-callout.js/useMarkdown.ts/useChatMarkdown.ts 等，
  与 T6/T7 记录一致），本任务文件 0 新增。
- `node scripts/t8-verify.mjs`（Playwright，后端 8081 + 前端 5173 实跑）：**59/59 PASS**
  - A 桌面合集文章（paper-light 1440）：面包屑/kicker/合集徽标「第 N / M 篇」/dek/byline/
    阅读时长/双栏 700+240/TOC+已读进度/问这篇文章卡+3 chips/§ 序号/首字下沉/文末双细线/
    收藏+分享/标签/snav 合集头+进度点+上下篇/继续读/评论/无 FAB/溢出 0。
  - B 非合集文章：无 series-tag、封面渲染、snav 无合集头、溢出 0。
  - C 主题对照：paper-dark 方角+§+溢出 0；classic-light 圆角+无 §+溢出 0。
  - D 移动端 390：右栏隐藏、目录 FAB、标题≈30px、snav 纵向、继续读单列、溢出 0。
  - E 提问链路：匿名→sessionStorage 暂存（问题+path）→登录回跳→面板自动打开→已发送；
    chip 填充；登录态直接开面板。
- 截图：`mysite-frontend/scripts/output/t8/`（a/b/c/d/e 系列，含 paper-dark/classic-light 对照）。

**走查中修复的问题**

1. 文末操作区 DOM 结构与验证选择器不符 → 调整 `BlogPost.vue` 文末 actions 标记对齐。
2. 封面图断言过严：picsum 外链在沙箱内始终 loading → 断言改为 img src 正确性
   （加载/兜底由 OptimizedImage 既有逻辑保证），非缺陷。
3. 移动端横向溢出 9px：定位为正文 `<table>` 超出 390 视口 → `typography.css`
   prose table 改 `display:block; overflow-x:auto` 容器化；`ArticleContent.vue`
   代码块 overflow 兜底；`PostHeader.vue` byline 加 `min-w-0`。修复后溢出=0。

**已知中间态（不视为缺陷）**

- 移动端文章页暂无「问这篇」入口——概念稿由 T9 dock 承接，T8 仅桌面右栏 AskArticleCard。
- 收藏 snav 进度点数=可见已发布篇数；私有/未发布文章不计入（与 prev/next 口径一致）。

**走查记录（2026-11-15 第二轮，DOM 级复核 + 交互实测）**

新增 `scripts/t8-walkthrough.mjs`：**38/38 PASS**

- A 滚动联动：TOC active 切换、已读 % / 剩余分钟实时更新、顶部 2px 进度条、TOC 点击滚动到位。
- B 分享/收藏/snav：分享复制链接+toast；匿名收藏→/login?redirect=本篇；snav 下一篇
  翻页后滚动回顶（router scrollBehavior top:0）+ 徽标「第 N/M 篇」随文章刷新。
- C 登录态收藏：刊头/文末两个 FavoriteButton 计数同步（props 回写 + result.favoriteCount
  双通道收敛一致）；移动目录 FAB→抽屉打开→点项滚动→抽屉自关。
- D 剩余 5 主题（aurora/rose-garden/ocean-breeze/warm-sunset/liquid-glass）横向溢出=0、
  console 0 error。
- E 降级：短文章无 TOC 时提问卡仍渲染；无封面文章不渲染 .post-cover。

代码级复核结论：

- `--border-strong` 由 tokens.css 全局兜底（aurora 未定义也安全回落 --border）。
- `.prose` serif 为存量基线，paper 覆盖层只做尺寸/装饰；非 paper 主题不受影响。
- `.prose` 仅 ArticleContent 消费（仅 BlogPost 使用），无跨页面外溢。
- 封面出血 -16/-24px 精确抵消 main px-4/sm:px-6；post→post 复用组件时
  router scrollBehavior 已回顶，fetchArticle 重新加载。
- usePendingAsk 跨页一次性语义：path 不匹配即丢弃，防止文章暂存被首页误消费
  （文章上下文只在 /post/:id 注入，设计正确）。
- FavoriteButton 双实例计数：toggle 方先拿 result.favoriteCount（权威值），
  另一实例经 props 回写收敛，单用户场景一致。

走查发现并修复：

1. `.post-kicker` 原 `v-if=article.categoryName`，未分类文章缺 kicker chip 而面包屑
   显示「未分类」→ 统一回退为 `categoryName || '未分类'`。

**后续**

- T9（移动 dock/抽屉）落地后文章页移动提问入口补齐；T11 聊天面板换肤。
