# UI 调研与锐评（2026-11-15）

> 任务性质：只读调研 + 运行时实测，不改代码。目标：为后续 UI 迭代提供问题清单。

## 怎么验的

- 本地拉起全栈：docker MySQL 8.4（手动灌 `init/schema.sql`+`journal-schema.sql`+`data.sql`，compose 不挂 init）+ brew redis 6379 + brew postgresql@17（本机已有 `ragent` 库含 pgvector 0.8.6，`ragent/ragent123` 直连）+ `./mvnw spring-boot:run` + `npm run dev`。
- docker Hub 直连不通，镜像走 `docker.m.daocloud.io`；pgvector 最终没用上（本地 PG 顶替）。
- Playwright（项目已装未用）+ 本机 Chrome for Testing 无头截图 25 张 + DOM/计算样式提取。
- 注意：本模型不支持看图，结论来自代码通读 + 运行时 DOM/样式/截图像素量化，非目检。

## 运行时实测结论（证据）

- 首页：10 篇/页、5 张有封面卡、15 个分类 pill、合集区 2 张卡；正文对比度 body 9.78 / h1 16.83 / muted-meta 4.59（12px，压线过 AA）。
- 文章页 TOC 正常（126 标题 → 177 个目录按钮）、滚动进度条正常、`.prose` max-w 720px。
- **文章详情页不渲染 coverImage**（头部无封面图，仅列表卡和 og:image 用）。
- **内容图片懒加载**（`loading="lazy"`），长文内图不滚动不加载，属预期。
- **匿名 AI 聊天已死但 UI 未清理**：后端 `ChatRateLimiter` 对 role==null 直接抛「请登录后使用 AI 助手」，前端仍显示游客额度 10 次/小时、匿名提示条、visitorId 管道全是死代码；CLAUDE.md 仍写匿名聊天。
- 路由无 404 兜底；`BlogPost`/`CollectionsView` 的「返回」用 `router.back()`，直达入口会退出站点。
- 全站**零 webfont 加载**：`--font-serif` 依赖本机 Noto Serif SC/Source Serif 4（均未装）→ macOS 中文落 Songti SC + 拉丁 Georgia 混排，Win→SimSun，Linux 有 tofu 风险。index.html 里 `Inter Fallback` @font-face 是死代码（无引用）。
- 三套分页并存：`article/ArticlePagination`（全量渲染页码，无窗口化，页数多会爆按钮）、`components/ui/Pagination`（后台）、`CollectionsView` 手写上一页/下一页。
- `formatViewCount` 在 CollectionCard/CollectionsView 重复实现。
- About 页近乎空白 + `prose-custom` class 不存在（死 class）；站点配置默认 title=MySite/author=Admin，各视图 `useHead` 标题硬编码 "- MySite" 不走 siteStore。

## 遗留环境状态

- `mysite-mysql` 容器仍在跑（数据已灌）；`mysite-redis` 容器因 6379 被 brew redis 占用未起（无碍）；pgvector 镜像拉取中止。
- 后端 8081 / 前端 5173 仍在跑（后台 nohup），截图存 `/tmp/mysite-shots/`（25 张，重启后 tmp 可能清）。
- admin/000000 可登录后台；LLM key 未配 → AI 对话只能到限流层。

## 后续方向（候选迭代项，未排期）

1. 首页视觉：hero/banner、置顶文章、封面占比设计。
2. 文章页：coverImage 头图、返回按钮兜底、404 路由。
3. 字体：决定 serif 正文是否上 webfont（Noto Serif SC 子集）或改无衬线统一。
4. 清理匿名聊天死代码或恢复游客额度（产品决策）。
5. 统一分页组件与 formatViewCount 工具函数；标题模板走 siteStore。
