# T18 合集详情 articleCount 口径统一

> 2026-11-16 立项。T17 收尾走查时确认的存量 bug，体量小，本会话内直接修。

## 1. 问题

`getCollectionDetail` 的 `articleCount` 与其实际列出的 `articles` 不同口径：

- **非管理员**：可见性过滤后 `detail.articleCount = articleIds.size()` ✓
- **管理员**：跳过整个过滤块，`articleCount` 落回 `BeanUtil.toBean` 拷贝的**存储列** `t_collection.article_count` —— 该列是 addArticle/removeArticle 时维护的计数器，会漂移（成员行指向已删文章、历史操作漏计数），与列表接口 `COUNT(a.id)` 口径也不一致。
- **附带 bug**：旧代码里非管理员过滤后 `articleIds` 被重建为 `visibilityCheck` 的返回顺序（DB `IN` 查询序），**丢失 `sortOrder` 排序**，分页切片取错成员；页内 items 虽按 sortOrderMap 重排，但页边界已经错了。

实锤数据：合集 1 存储列=5、成员行实际 join 得=3（2 条成员行指向 `del_flag=1` 文章）→ admin 详情显示「5 / 9 篇」，列表/匿名都是「3 / 9 篇」。

## 2. 修法（`CollectionServiceImpl.getCollectionDetail`）

统一为：**`articleCount` = 当前访问者实际可列出的成员文章数**（存在性 + 可见性过滤后）：

1. 所有访问者先做存在性过滤：`SELECT id,visibility,author_id FROM t_article WHERE id IN (...) AND del_flag=0`，剔除指向已删文章的成员行。
2. 非管理员再叠可见性过滤（`visibility=0` 或作者本人）；管理员跳过该条件。
3. 用「允许 id 集合」**回滤原 member 顺序列表**（而非重建），保住 `sortOrder`。
4. `articleCount` 与分页 `totalArticles` 同用过滤后 size。

语义后果（均为修正方向）：
- admin 详情计数从「存储列（漂移）」→「实际成员数」，与列表 `COUNT(a.id)`、与 items 同口径。
- 非管理员分页恢复 `sortOrder` 顺序。
- 草稿仍计入（`del_flag=0`），与 items 展示一致；**草稿对匿名泄漏是另一独立问题**（T14 遗留记录，不在本任务修）。

## 3. 验证

- `CollectionServiceVisibilityTest`：
  - `adminShouldSeeAllPrivateArticles` 更新（selectList 两次调用 + count=2 断言）。
  - 新增 `adminDetailShouldDropDanglingMemberRows`：存储列=5、成员行 2 条但 1 篇已删 → count=1、items=1。
- `mvnw test -Dtest='Collection*Test'`：67/67 绿。
- 真实 API：admin `/v1/collections/1` → `articleCount=3, items=3`；匿名同值。

## 4. 边界与决策

- 存储列 `t_collection.article_count` 保留不动（其他消费点如列表 COUNT 不走它；写路径维护照旧）。
- 不修「草稿对匿名泄漏」：可见性过滤只看 visibility 不看 published，属独立安全问题，已另立遗留项。

## Resolution

2026-11-16 完成。详情计数统一为「实际可列出成员数」，顺带修复非管理员分页丢 sortOrder 的隐藏 bug。67/67 测试 + 真实 API 验证。未提交（工作树有并行 T15 改动，随阶段评审统一 commit）。
