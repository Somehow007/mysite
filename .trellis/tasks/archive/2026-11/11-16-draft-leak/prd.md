# 修复：草稿/非公开内容泄漏（公开读路径 + RAG 入库）

> 2026-11-16 立项。T14/T18 阶段评审确认的存量安全问题，评审结论「建议立项、先过 §2.2」。
> 本会话 owner 授权「能修的都修」，按下述方案直接实施。

## 1. 问题（摸底全量，2026-11-16 代码实测）

`published`（0=草稿 1=发布，NOT NULL）与 `visibility`（0=公开 1=仅自己可见）是两道门，
但公开读路径只查了 visibility、漏了 published；RAG 入库两道门都没查。

| 泄漏点 | 现状 | 后果 |
|---|---|---|
| `GET /v1/articles` 列表 | `DatabaseArticleSearchServiceImpl` 仅在请求显式传 `published` 时过滤；BlogHome/Category/Tag/Search/BlogPost 相关推荐/NotFoundPanel 均不传 | 匿名可见草稿标题/摘要/封面 |
| `GET /v1/articles/{id}` 详情 | `selectOneArticle` 只查 visibility，不查 published | 匿名凭 ID 直读草稿全文 |
| 合集详情 items | T18 重写的可见性过滤只看 visibility+del_flag | 合集内草稿对匿名可见（计数同口径泄漏） |
| `/v1/articles/favorites` | XML 只查 `a.del_flag=0`，不查 published/visibility | 收藏列表残留草稿/转私有文章元数据 |
| RAG `syncArticle` | 不查 published/visibility，草稿/私有文章照常向量化 | **草稿/私有全文可经 AI 问答 sources 泄漏**；取消发布/转私有后旧向量仍在 |
| KB 管理 `articles/available` + `addArticles` | 不过滤 published/visibility/del 外条件 | 管理员可把草稿/私有文章加进共享 KB |

已确认**安全**的路径（无需改）：`getArchive`（已 `published=1`）、`getArticleNavigation`
（prev/next/合集成员全查 `published=1`）、UserProfileView（前端已传 `published:1`）、
ES impl dormant 但 post-filter 结构可同法补。

Dashboard/CollectionEditView 是**合法草稿消费方**（文章管理、合集选文），不能一刀切。

## 2. 方案（评审口径）

统一语义：**published=1 是公开读的默认门；草稿仅「作者本人 / ADMIN」可见；
管理视图（Dashboard、合集选文）经显式参数放开。**

### 后端

1. `ArticlePageQueryReqDTO` +`includeDraft`（Boolean）：管理视图显式放开标记。
2. `DatabaseArticleSearchServiceImpl.searchArticles`：
   - `managementView = isAdmin() || role==CREATOR`；
   - `allowDrafts = managementView && (published != null || includeDraft == true)`；
   - allowDrafts → 按 published 参数过滤（null+includeDraft=全量）；否则恒 `published=1`。
   - 非管理角色的 `published` 参数**忽略**（USER 传 published=0 也只得已发布）。
3. `ElasticsearchArticleSearchServiceImpl.buildArticlePageResult`：同规则 post-filter（dormant，防启用回归）。
4. `selectOneArticle`：仿 visibility 检查加 published 检查——草稿仅作者本人/isAdmin 可见，否则 `ARTICLE_NOT_FOUND`；`ArticleSelectRespDTO` +`published` 字段（BeanUtil 自动映射）。
5. `CollectionServiceImpl.getCollectionDetail`：存在性查询补 `published` 列；可见 = `admin || 本人文章 || (published==1 && visibility 公开)`——与详情端点同语义。
6. `ArticleMapper.xml pageFavoriteArticleResults`：+`AND a.published = 1`（个人收藏列表，草稿移出；visibility 边角留观察项）。
7. RAG `KnowledgeDocumentService.syncArticle(ArticleDO, kbId)`：入口加 `published==1 && visibility!=1` 门——不满足直接 return（不建档）；1-arg `syncArticle` 入口同判定走 `removeArticle` 清理（取消发布/转私有时清已入库向量）。
8. `KnowledgeDocumentController`：`availableArticles` 过滤 `published=1 && visibility=0`；`addArticles` 预过滤同条件（避免 added 计数说谎）。

### 前端

- `api/article.ts getArticles` +`includeDraft` 透传；`DashboardView`/`CollectionEditView` 传 `includeDraft: true`。
- 公开视图零改动（后端默认收紧）。

## 3. 验收

- hermetic 单测：searchArticles 三态（默认=1 / 管理+includeDraft=全量 / USER 强制=1）、selectOneArticle 草稿四态、合集详情草稿过滤、syncArticle 门、RagChat 无关。
- `./scripts/verify.sh` quick 绿；lint 15 基线 0 新增；build 过。
- 真实 API 抽查：匿名 GET /v1/articles 无草稿、GET /v1/articles/{draftId} → 404、合集详情计数口径、RAG sync 跳过草稿。

## Resolution

2026-11-16 完成实施与验证。

**实施（方案 §2 全部落地 + 走查中新发现并修 4 处）**

- `ArticleSearchService.resolvePublishedFilter`（静态共用，DB/ES 两实现同规则，防逻辑复制漂移）：
  非 ADMIN/CREATOR 恒 `published=1`；管理视图按 `published` 参数；`includeDraft=true` 且未传 published → 不过滤。
- `selectOneArticle`：详情缓存后补 published 门，草稿仅作者/ADMIN，否则 ARTICLE_NOT_FOUND；
  `ArticleSelectRespDTO` +`published`（BeanUtil 自动映射）。
- `CollectionServiceImpl.getCollectionDetail`：成员过滤升级为「admin||文章作者」或
  「published=1 && visibility 公开」，与详情端点同口径；计数/分页同一份过滤列表。
- `favoriteArticle`：补草稿门（收藏动作与详情同语义），XML 收藏列表 `published=1` +
  `visibility 公开 OR author=self`（顺带封掉「收藏后转私有仍可见」）。
- `KnowledgeDocumentService.isPubliclySyncable`（published=1 && visibility 公开）：
  两个 syncArticle 入口同门；1-arg 不满足走 `removeArticle` 清已入库向量；
  `syncAllKnowledgeBase`/`reprocess` 对转非公开文档直接清档（防 PENDING 卡死+旧向量残留）。
- `availableArticles`/`addArticles` 预过滤同口径。
- **走查新增**：分类/标签文章计数（`CategoryServiceImpl`×3、`TagServiceImpl`×1）此前把草稿/私有算进
  公开计数——改按「公开可列出」口径（published=1 && visibility 0/NULL）；删除保护计数不改（必须算草稿）。
- **visibility NULL 语义**：旧数据 NULL=公开，`eq(0)` 会误排——所有新增过滤用 `0 OR NULL`。

**验证**

- 新增/更新单测：`ArticleServiceImplDraftTest`（5）、`resolvePublishedFilter` 矩阵（5）、
  合集草稿成员（3）、KB 公开性门（3）、`RagChatControllerTest` 归属四态；Phase2/3、
  KnowledgeDocumentServiceTest、FavoriteTest 的 ArticleDO fixture 补 `published(1)`（镜像 NOT NULL）。
- `./mvnw test -DexcludedGroups=needs-infra` **505/505 绿**；前端 type-check 绿、build 绿；
  lint 无新增（触碰文件内仅存量基线项）。

**遗留/观察**

- ~~真实环境抽查~~ 已做（见下走查记录）。
- `t_collection.article_count` 存储列仍可能漂移（T18 观察项，当前无错误消费点）。
- KB 孤儿文档：源文章已删的 `t_knowledge_document` 残留（syncAll 对 article==null 只跳过不清除）——
  本机发现 3 条（T14 验证文章），观察项，下次清理任务顺手做。
- ~~环境：`.env` `BAILIAN_EMBEDDING_MODEL=qwen3.7-text-embedding` 调 embeddings 返 400~~
  已修：`.env` 改 `text-embedding-v4` 重启后重试 reprocess，文档恢复 READY（13 chunks/13 vectors），
  KB.embedding_model 同步更新为 v4。期间另遇 DNS 瞬断（热点网络），重启后自愈。

## 走查记录（2026-11-16 真实 API，brew MySQL+PG，后端新代码）

| 场景 | 结果 |
|---|---|
| 匿名 `/v1/articles` size=50 | total=9，草稿 2108477975514066944 缺席 ✓ |
| 匿名 `?published=0` | total=9（参数被忽略）✓ |
| 匿名 `?includeDraft=true` | 同上被忽略 ✓ |
| 匿名 GET 草稿详情 | `A030100 文章不存在` ✓；已发布 200 ✓ |
| ADMIN `?published=0` | total=3 草稿 ✓；`?includeDraft=true` → 12 全量 ✓ |
| USER `?published=0` / `?includeDraft=true` | 均被忽略 total=9 ✓ |
| USER 非作者草稿详情 | `A030100` ✓；ADMIN 200 ✓ |
| 合集详情 | articleCount=3 = 可列出成员数 ✓ |
| 分类/标签计数 | 仅公开文章口径 ✓ |
| USER 收藏草稿 | `A030100` ✓；收藏已发布成功（已复原）✓ |
| `conversations/{id}/messages` | 正确 visitorId→2 条；错误/缺失/不存在→空 ✓ |
| `conversations?userId=1` 伪造 | 参数已删→空（不再可查他人）✓ |
| 匿名 conversations | `A070100` 未登录 ✓ |
| 匿名 KB list/detail | 200/200（死 pattern `{id}`→`*` 修复生效）✓ |
| USER 访问 KB 管理 | 403 ✓ |
| **存量泄漏实锤** | 草稿 2108477975514066944 早已入库 KB `READY`——新 `syncAll` 清档 `removed=1` 已删 ✓ |

验证结论：全部预期语义通过；唯一非预期=合法文章重同步因 .env 坏模型名 FAILED（环境）。
