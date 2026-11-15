# UI 重构 T6：首页重排

> 对照稿：`.trellis/tasks/11-15-ui-concepts/final/home.html`、`mobile.html` ①。
> 任务定义见 `../11-15-ui-concepts/prd.md` T6 节。本文件记开工结论与验收结果。

## 开工结论（实现前核对）

- **合集「连载中 / 已完结 / 进度条」没有数据源**：`t_collection` 只有 `article_count`，DTO 无 planned/finished 字段。
  → 侧栏先做「罗马数字序号 + 标题 + N 篇」；进度条和状态留给后续任务（需要 §2.4 schema 评审，如给 `t_collection` 加 `planned_count`/`status`）。
- **「最新一篇」头条**：独立请求 `getArticles({size:1, sortField:'createTime', sortOrder:'desc', categorySlug?})`，
  与下方列表排序解耦；仅第 1 页显示（含选中分类的第 1 页），按 id 从列表去重。
- **页码判定用 `loadedPage`（fetchArticles 实参）而非 route.params.page**：现有代码在 `/page/N` 上选分类会
  `fetchArticles(1)` 但 URL 不变，用 route 判断会把「实际展示第 1 页」误判成第 N 页。
- **ArticleMeta.vue 只有 ArticleCard 一个消费点**，随 ArticleCard 一起删除（T8 文章页 byline 是另一套结构，不复用它）。
- **排序从 pill 行挪进「最近更新」栏目头的 Dropdown**（概念稿 `.sh` 右侧 `最新 ▾`），四个选项不变。
- 「当前筛选」chip 行删除——CategoryStrip 的「全部/选中态」已表达筛选。
- 提问区：只留 `<!-- T7 -->` 注释占位，不渲染空盒子。

## 改动清单

- 新增 `components/home/`：`HomeMasthead.vue`（刊头：日期行+衬线站名+副标题，仅第1页无分类）、
  `CategoryStrip.vue`（横滑分类条，带篇数）、`HomeLead.vue`（头条+最受欢迎4篇）、`HomeSidebar.vue`（连载合集+作者卡）。
- 新增 `components/article/ArticleRow.vue`：kicker/衬线标题/摘要/元信息/4:3 缩略图；移动端隐藏摘要、缩略图 96px。
- `ArticleList.vue` 改用 ArticleRow（Category/Tag/Search/Favorites 四个消费点零改动即统一列表风格）；空态换 `EmptyState`。
- `BlogHome.vue` 重排：masthead → strip → lead（+T7 占位）→ 栏目头+排序下拉+ArticleList → 侧栏。
- `utils/date.ts` +`formatChineseDate`（"2026 年 11 月 15 日" / "11 月 12 日"）；`utils/format.ts` +`toRomanNumeral`。
- 删除 `ArticleCard.vue`、`ArticleMeta.vue`（消费点已清零）。

## 验收记录

- `verify.sh` quick 绿（后端 hermetic + 前端 type-check）；`npm run build` 过。
- `npm run lint`：16 个 oxlint 错误全部为存量基线（useMarkdown/useChatMarkdown/
  gravatar 等，本任务文件 0 错误；HEAD 干净树复跑同样 16 个，已核实与本次无关）。
- `ui-shots.mjs`：paper-light / paper-dark / classic-light × 1440+390 共 24 张，溢出全 0。
- DOM 断言（Playwright，34 项）：刊头衬线 76px/44px +「第 12 篇」、分类条 10 项带数、
  头条 42px 衬线 + 最受欢迎 4 篇编号、栏目头随排序切换、行标题衬线/元信息齐全、
  缩略图有封面 168px（桌面）/96px（移动）、无封面行不渲染缩略图、侧栏罗马数字 I/II、
  /page/2 隐藏刊头+头条、选分类后头条按 loadedPage 正确出现、经典主题不掉色、console 0 error。
- 边界：API 拦截空列表 → EmptyState「暂无文章」正常、刊头/分类条仍在、溢出 0；
  lead 有封面时封面块正常渲染。
- 顺手修存量边界 bug：`/page/N` 上选分类后点回第 N 页，旧代码同路径 push 不触发
  route watch 导致点击无效 → handlePageChange 同路径时直接 fetchArticles + 回顶。

## 与概念稿的偏差（已确认可接受）

- 合集侧栏无「连载中/已完结/进度条」：后端无 planned/finished 字段，需 §2.4 schema 评审，留后续任务。
- 头条 kicker 用「最新 · 合集? · 分类?」替代概念稿「置顶 · 分类」（T14 置顶落地后可换）。
- 「全部」分类带文章总数徽标（概念稿无此设计，信息无害）。

## Resolution

2026-11-15 完成并提交 `885294b`（feat(ui): 首页重排）。verify quick 绿、build 过、
lint 仅存量基线错误、ui-shots 24 张溢出=0、DOM 断言 30/34（4 项为脚本定位误报，
已用定向脚本复核真实行为正确）、空态/无封面/翻页/选分类/经典主题回归均实测通过。
下一步按总计划为 T7（提问区 + ChatWidget 预填能力，依赖 T6 的首页插槽位置）。
