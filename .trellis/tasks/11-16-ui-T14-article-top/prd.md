# UI 重构 T14：文章置顶

> 总计划：`.trellis/tasks/11-15-ui-concepts/prd.md` T14 节（需先评审 §2.3/§2.4）。
> 状态：**已完成**（2026-11-16 评审三项全按推荐拍板 → 实施 → 验证）。见文末 Resolution。

## 目标

- `t_article` 增加 `is_top` / `top_time`；作者本人或 ADMIN 可置顶/取消。
- 首页头条（HomeLead）优先取置顶（多篇按 `top_time` 最新），无置顶回落最新一篇；kicker 显示「置顶」。
- Dashboard 文章表加置顶开关。

## 现状核对（决定改动边界）

- **列表查询无缓存**：`GET /v1/articles` → `ArticleServiceImpl.pageQueryArticle` → `ArticleSearchService.searchArticles`。DB 实现（`DatabaseArticleSearchServiceImpl`，ES `enabled:false` 为 dormant）每次直查 MySQL，**无 @Cacheable**。→ 置顶不需要列表缓存失效。
- **缓存只有两处与文章相关**：`article_detail`（@Cacheable by id，30min，`ArticleCacheService.getArticleDetail` → `ArticleSelectRespDTO`）和 `article_nav`（合集上下篇导航，`CollectionServiceImpl`）。置顶影响前者（DTO 加 isTop），不影响后者。
- **DTO 映射是两处手工 + 一处 BeanUtil**：`ArticlePageQueryRespDTO` 在 DB/ES 两个 search impl 里手工 set 字段（**两个消费点都要补 isTop**）；`ArticleSelectRespDTO` 走 `BeanUtil.toBean(articleDO)` 自动映射。
- **`pageArticleResults`（ArticleMapper.xml）是死代码**：全仓无调用方，本任务不动。
- **权限**：`PUT /v1/articles/**` 已 `authenticated()`（WebSecurityConfig），服务层 `checkArticleOwnership` = ADMIN 或作者本人——与 prd「仅 ADMIN / 作者本人」一致，**安全配置零改动**。
- **ES 索引 `ArticleDocument` 无 isTop 字段**，且 ES 路径的 DTO 是查回 MySQL `ArticleDO` 重建的 → isTop 字段能透出；但 isTop 过滤/排序在 ES 查询路径不生效（dormant，记为已知限制）。
- **RAG 耦合点**：`ArticleUpdatedEvent` 会触发重向量化（LLM 计费）——置顶**刻意不发**此事件。

## 方案

### DB（schema 变更，先评审）

`docker/init/migrations/20261116-article-top.sql`（沿用既有迁移格式，重复执行报 Duplicate column 可忽略）：

```sql
ALTER TABLE `t_article`
    ADD COLUMN `is_top` TINYINT NOT NULL DEFAULT 0
        COMMENT '置顶 0:否 1:是' AFTER `visibility`,
    ADD COLUMN `top_time` DATETIME NULL
        COMMENT '置顶时间，多篇置顶按此倒序' AFTER `is_top`;
```

`docker/init/schema.sql` 的 `t_article` 同步加两列。**不加索引**：个人博客量级，`ORDER BY is_top DESC` filesort 可忽略；文章量上来再补 `idx_is_top`。

### 后端

| 文件 | 改动 |
|---|---|
| `dao/entity/ArticleDO.java` | +`Integer isTop`、`Date topTime`（对齐 published/visibility 的 Integer 惯例） |
| `dto/req/article/ArticlePageQueryReqDTO.java` | +`Integer isTop` 过滤参数；`sortField` Schema 注明支持 `topTime` |
| `dto/req/article/ArticleTopReqDTO.java` | 新建 `{isTop: Integer(0\|1)}` |
| `dto/resp/ArticlePageQueryRespDTO.java` | +`Integer isTop` |
| `dto/resp/ArticleSelectRespDTO.java` | +`Integer isTop`（BeanUtil 自动映射） |
| `service/ArticleService.java` + `impl` | +`updateArticleTop(Long id, Integer isTop)` |
| `service/impl/DatabaseArticleSearchServiceImpl.java` | isTop eq 过滤；`sortField='topTime'` → `is_top DESC, top_time DESC, create_time DESC, id DESC`；DTO 映射补 `setIsTop` |
| `service/impl/ElasticsearchArticleSearchServiceImpl.java` | DTO 映射补 `setIsTop`（过滤/排序不接，dormant 已知限制） |
| `controller/ArticleController.java` | +`PUT /v1/articles/{id}/top` |

`updateArticleTop` 行为：
- `checkArticleOwnership(id)`（复用：ADMIN 直通，否则校验作者）。
- 置顶：`is_top=1, top_time=NOW()`；取消：`is_top=0, top_time=NULL`（幂等，重复置顶刷新 top_time）。
- `articleCacheService.evictArticleDetail(id)`；**不驱逐** `article_nav`（不相关）、**不发** RAG 事件、**不碰** ES 索引。
- `ArticleCreateReqDTO`/`ArticleUpdateReqDTO` **不加** isTop——置顶是独立动作，防编辑器保存误改。

### 前端

| 文件 | 改动 |
|---|---|
| `types/index.ts` | `ArticleListItem.isTop?: number`、`Article.isTop?: number` |
| `api/article.ts` | +`setArticleTop(id, isTop)` → `PUT /v1/articles/{id}/top` |
| `views/DashboardView.vue` | 操作列加 Pin 开关（`canModify(item)` 门控，lucide `Pin` 图标），状态列 isTop 时加置顶标记；乐观更新 + toast |
| `views/BlogHome.vue` | `fetchLead` 改 `sortField:'topTime', sortOrder:'desc'`（单请求：置顶按 top_time 倒序在最前，无置顶自然回落 create_time 最新） |
| `components/home/HomeLead.vue` | kicker：`lead.isTop ? '置顶' : '最新'` |

## 评审要点（待 owner 拍板）

1. **缓存失效范围**：仅 `article_detail` 单条 evict。列表无缓存、`article_nav` 不相关 → 影响极小。
2. **分页 total**：不受影响。isTop 是可选过滤/排序参数，不改默认列表 WHERE；HomeLead 用 `sortField=topTime` 一次查询同时拿到「置顶优先 / 无置顶回落最新」，不需要 isTop=1 + 二次回落查询。
3. **分类筛选下的头条**：lead 查询沿用现有 `categorySlug` 透传——置顶只在当前筛选范围内优先。即：全局置顶的 A 分类文章不会顶掉 B 分类筛选下的头条。替代方案是置顶全局优先（忽略分类），会与「头条=当前视图代表」语义冲突，**建议维持现状**。
4. **草稿可否置顶**：允许置顶草稿（发布后自然生效；置顶本身不改 published）。⚠️ 摸底时发现**存量问题**：公开列表接口不过滤 `published`（BlogHome/Category/Tag/Search 均不传），草稿可能出现在首页——与 T14 无关，建议另立项，本任务不顺手修。
5. **ES 路径**：`isTop` 过滤与 `topTime` 排序只在 DB 实现生效；ES impl 仅透传 DTO 字段。ES 当前 `enabled:false`，若未来启用需补 `ArticleDocument.isTop` + repository 查询。
6. **top_time 精度**：`DATETIME` 秒级；同秒双置顶由 `create_time DESC, id DESC` 兜底（手工操作同秒概率可忽略）。
7. **多篇置顶**：允许，不限数量；HomeLead 取 top_time 最新那篇，其余置顶文章仍按各自排序出现在列表原位（不做「置顶文章浮顶」的列表改造——那是另一种交互，本期不做）。

## 验收（评审通过后执行）

1. `./scripts/verify.sh`（quick）通过；`npm run lint && npm run build` 通过。
2. hermetic 单测：`updateArticleTop` 的权限/置顶取消/幂等用 Mockito 测试钉住（参照 `ArticleServiceImplWriteTest`）。
3. Playwright 或手工：置顶后首页头条变为该文 + kicker「置顶」；取消后回落最新；Dashboard 开关状态一致；多篇置顶取最新 top_time。
4. 迁移 SQL 在本地 docker MySQL 执行验证（`docker compose` 起的库跑一遍 ALTER）。
5. `.trellis/CHANGES.md` 追加一行。

## Resolution（2026-11-16）

**评审拍板**：① 置顶跟随分类筛选（lead 查询透传 categorySlug，跨分类置顶不顶掉分类头条）；② 草稿泄漏存量 bug 另立项不顺手修；③ 头条取法 = `sortField=topTime` 单查询（`is_top DESC, top_time DESC, create_time DESC, id DESC`），无置顶自然回落最新。

**落地清单**：

- DB：`docker/init/migrations/20261116-article-top.sql`（`is_top TINYINT NOT NULL DEFAULT 0` + `top_time DATETIME NULL`，AFTER visibility）+ `schema.sql` t_article 同步；无索引（个人博客量级 filesort 可忽略）。
- 后端：`ArticleDO.isTop/topTime`；`ArticlePageQueryReqDTO.isTop` 过滤 + `sortField=topTime`；`ArticleTopReqDTO{isTop}`；`ArticlePageQueryRespDTO/ArticleSelectRespDTO.isTop`（后者 BeanUtil 自动映射）；`PUT /v1/articles/{id}/top` → `ArticleServiceImpl.updateArticleTop`（checkArticleOwnership = ADMIN/作者本人；置顶写 top_time=now、取消置顶 NULL；仅 evict `article_detail`，不发 RAG 事件不碰 ES）；DB/ES 两个 search impl 的 DTO 手工映射都补了 `setIsTop`（ES 路径 isTop 过滤/排序不接，dormant 已知限制）。
- 前端：`Article/ArticleListItem.isTop?: number`；`setArticleTop()`；DashboardView 操作列 Pin 开关（canModify 门控，桌面图标+移动文字钮，状态列/移动卡片置顶标记，乐观更新+toast）；BlogHome.fetchLead 改 `sortField:'topTime'`；HomeLead kicker `isTop===1?'置顶':'最新'`。
- 安全：零配置改动——`PUT /v1/articles/**` 本就要求 ADMIN/CREATOR，服务层再校验作者归属。

**验证结论**：

- `./scripts/verify.sh` quick 绿（hermetic 测试 + 前端 type-check）；`npm run lint` 15 基线 0 新增（全部存量文件）；`npm run build` 过。
- 新增 `ArticleServiceImplTopTest` 9 例全过（参数校验/权限四态/置顶取消 SQL SET 断言/缓存驱逐/不发 RAG 事件边界）。
- 本地 brew MySQL 8.4 实跑迁移成功；后端 8081 + 前端 5173 E2E（t14check 临时 CREATOR）：匿名 PUT→401、isTop=2→A030101、置顶→is_top=1+top_time 落库、双置顶 topTime 排序新者在前、isTop=1 过滤 total=1、详情 DTO isTop+缓存驱逐正确、他人文章→A030109；UI Playwright：首页头条 kicker「置顶 · Spring Boot 系列」+ 头条换成被置顶旧文、Dashboard 桌面/移动置顶按钮点击链路通、双视口溢出=0、pageerror=0。
- 测试残留已清：两篇测试文章删除、SQL 置顶还原、t14check 回 USER。

**并行提示**：实施期间工作树有 T15（已落地待提交）+ T17（合集状态）在途改动；本任务 commit 按文件+hunk 隔离（schema.sql / types/index.ts / CHANGES.md 与 T17/T15 共享文件只提交本任务 hunk）。

**遗留/后续**：

- 草稿泄漏存量 bug（公开列表不过滤 published）→ 建议另立项，影响 BlogHome/Category/Tag/Search 全部公开列表。
- ES 启用时需补 `ArticleDocument.isTop` + repository 过滤/排序（当前 dormant）。
- T17（合集连载状态）schema 评审可与本任务一并过——其 migration `20261116-collection-status.sql` 已在树内。
