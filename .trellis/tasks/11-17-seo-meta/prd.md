# 11-17 SEO / description / OG 标签优化

## 背景

前台已用 `@unhead/vue`（`createHead()` client 版，main.ts 已修对），但 SEO 面很薄：

- 仅 BlogPost/CollectionView 有零散 og 标签；多数公开页只有 title；Category/Tag/Collections/UserProfile 连 description 都没有。
- 无 canonical、无 og:url、无 og:site_name/locale、无 twitter:card、无 JSON-LD。
- `index.html` 静态 description/OG 是写死的 "MySite - 个人博客"，与 `SITE_*` 动态配置脱节。
- 私有/工具页（favorites/search/login/register/dashboard/404）未标 noindex。
- 无 robots.txt；无 sitemap（本次未做，见后续）。

## 方案

不引入第二套 head 管理，全部走 unhead「App 级默认 + 视图级覆盖」（视图条目优先级更高；`index.html` 同 key 静态标签会被收养并运行时更新，已实测）。

新增两个文件：

- `src/utils/seo.ts`：`absoluteUrl`（相对路径按站点域名补全，SITE_URL 空则省略）、`isoDateTime`（归一 ISO 8601，兼容 "yyyy-MM-dd HH:mm:ss"）、`truncateText`（description 160 字截断）、`siteOrigin`/`siteUrl`（SITE_URL 优先，空回落 `window.location.origin`——生产 nginx 同域时天然正确）。
- `src/composables/useSeo.ts`：`useSeo(options)` 统一产出 title/description/keywords/robots/og:type/og:image/article:*(published/modified/author/section/tag)/JSON-LD；description 缺省回落 `site.description`。canonical 与 og:url 由 App.vue 按 `route.path` 全局维护（query 自动剥离）。`useSiteOrigin()` 供 JSON-LD 拼 URL。

### 各页面

| 页面 | 改动 |
|---|---|
| App.vue | 全局 canonical/og:url（随路由）、og:site_name、og:locale=zh_CN、twitter:card=summary_large_image |
| index.html | 静态 OG 兜底（title/desc/type/site_name/locale/twitter:card），供无 JS 爬虫 |
| BlogHome | useSeo：标题缺省=纯站名；og:image=avatar；JSON-LD WebSite+SearchAction（`/search?q={search_term_string}`） |
| BlogPost | useHead→useSeo：article og:type、封面图绝对化、article:* 时间 ISO 化补 section/tag/author；错误/404 时 noindex；JSON-LD BlogPosting |
| CategoryView | 新增 `getCategoryBySlug` 拉详情：title=seoTitle‖name、desc=seoDescription‖description、keywords=seoKeywords；空分类 noindex |
| TagView | 新增 `getTagBySlug`：标题用真实 tag.name（原来直接显示 slug）、desc 含文章数；h1 同步改 |
| ArchiveView | desc 带文章总数 |
| AboutView | desc=site.description+authorBio 拼接，og:image=avatar |
| CollectionsView | desc 带合集总数 |
| CollectionView | desc/og:image=coverImage；私有合集(visibility=1)或加载失败 noindex |
| UserProfileView | desc=bio+文章数，og:image=avatar；加载失败 noindex |
| SearchView | noindex（关键词组合无限，防薄内容） |
| FavoritesView | noindex（需登录的私人列表） |
| LoginView/RegisterView | 补 title + noindex |
| NotFoundView | useHead→useSeo，保持 noindex |
| DashboardLayout | useHead robots=noindex，一处覆盖全部 /dashboard 子页（子页 useHead 不冲突，meta 合并） |
| public/robots.txt | 新增：Disallow /dashboard /search /favorites /login /register |

### 有意不做

- **sitemap.xml**：需后端动态生成（文章 URL 是主要收录面），属新增公开端点，按 AGENTS.md §2.2 需先讨论；robots.txt 未写 Sitemap 指向避免 404 引用。
- **SSR/预渲染**：SPA 现状不变，Google 可渲染 JS；若以后要百度/微信等弱渲染爬虫再评估。
- dashboard 子页的 title 仍走原 useHead（只影响 titleTemplate，无 SEO 影响）。

## 验证

- `npm run type-check` ✅、`npm run build` ✅；eslint 对全部改动文件 ✅（`npm run lint` 全量仍卡 17 个既有基线错误，均在未触碰文件：test-callout.js、scripts/、useMarkdown 等）。
- Playwright 冒烟（5173 dev，无后端）：6 条路由验证 title/desc/robots/og/canonical/twitter:card 渲染正确，unhead 收养 index.html 静态标签并按页更新，无重复 meta。
- Playwright + route mock 全链路（/v1/site/config 返回 SITE_URL=https://blog.example.com）：
  - `/post/123`：title「测试文章标题 - 测试博客」、og:type=article、og:image/canonical/og:url 全部绝对化到 blog.example.com、article:* ISO 8601、JSON-LD BlogPosting 完整；
  - `/category/tech`：标题/描述取自 `/v1/categories/{slug}`；
  - `/`：纯站名标题、站点 desc、og:image=avatar 绝对化、JSON-LD WebSite+SearchAction。
- 后端未动，无需后端验证。

## 后续方向

- sitemap.xml（后端 `/v1/sitemap.xml` 或构建期生成，含已发布文章 URL + lastmod）。
- 文章页 `article:author` 目前是文本名，OG 规范更推荐 profile URL，可后续给作者页 `/user/{username}` 做关联。
- 若 SITE_URL 长期不配，canonical 依赖请求 origin——生产 nginx 同域下已正确；多域名/镜像场景需显式配 SITE_URL。

## Resolution

已完成并验证（见上）。走查补了三处边界：JSON-LD `<` 转义防 `</script>` 逃逸、协议相对图片地址补 `https:`、og:title/og:site_name 空值守卫保住 index.html 静态兜底。随后随 SEO 上线流程提交并部署。
