# T5 统一分页 + 工具函数

> 上游：`.trellis/tasks/11-15-ui-concepts/prd.md` §T5。前台只剩一套分页组件，页数多时窗口化；阅读量格式化收敛到 `utils/format.ts`。

## 改动清单

| 文件 | 改动 |
|---|---|
| `src/components/ui/Pagination.vue` | 重写为唯一分页实现：窗口化（当前页 ±1 + 首尾页 + 省略号，总页数 ≤7 全展示）、`compact` 模式、新增上一页/下一页文字按钮、aria 标签；移除无人使用的 `maxVisible` prop |
| `src/utils/format.ts` | 新增 `formatViewCount`（规则与原两份重复实现一致：≥10000→`x.x万`，≥1000→`x.xk`，否则原样） |
| `src/components/article/ArticleList.vue` | `ArticlePagination` → `Pagination`（`Pagination as PaginationBar`，避免与 `@/types` 的 `Pagination` 类型重名；沿用 `CollectionManageView` 既有别名约定） |
| `src/components/article/ArticlePagination.vue` | 删除 |
| `src/views/CollectionsView.vue` | 手写「上一页/N/下一页」→ `PaginationBar`；删本地 `formatViewCount` |
| `src/views/CollectionView.vue` | 同上；文章行阅读量走 `formatViewCount` |
| `src/components/collection/CollectionCard.vue` | 删本地 `formatViewCount`，改引 utils |
| `src/components/article/ArticleMeta.vue` | 文章卡片阅读量 `{{ article.viewCount }}` → `formatViewCount(...)`（影响所有 ArticleCard：首页/分类/标签/搜索/收藏/合集） |
| `src/views/UserProfileView.vue` | 文章列表、收藏列表两处阅读量 → `formatViewCount` |

## 设计要点

- **props 向后兼容**：`current` / `total`（条目数）/ `pageSize` / `inCard` / `update:current` 全部不变；8 个后台消费点（DataTable、DashboardView 经 DataTable、ImageManager、KnowledgeManage、CollectionManage、UserManage、UserProfile×3、CommentManage）零改动。
- **窗口化**：`{1, N, c-1, c, c+1} ∩ [1,N]` 排序后按 gap>1 插 `…`。最多 5 个数字按钮 + 2 个文字按钮 = **7 个按钮上限**（验收口径）。页 1 渲染 `1 2 … N`，页中渲染 `1 … c-1 c c+1 … N`。
- **compact**：`compact` prop 强制开启；`<640px`（与 ChatWidget `isMobile` 同断点，`useMediaQuery('(max-width: 639px)')`）自动开启。紧凑态只显示 `c / N` + 上/下页，隐藏「共 N 条」信息条。
- **文字按钮**：`‹ 上一页` / `下一页 ›`（lucide chevron + 文字，<420px 只留图标）；禁用态 `disabled:opacity-40`；补 `aria-label`/`aria-current="page"`。
- **修复**：原实现 `v-for :key="page"` 在双省略号时 key 重复 → 改用索引 key。
- **走查补充**：nav 加 `ml-auto`——非紧凑态、宽度刚超 640px（信息条 + 全量页码放不下换行）时保持右对齐；实测 660px 渲染 `1 2 … 50`、溢出 0。

## 验收记录

- `./scripts/verify.sh`（quick）：**VERIFY OK**（后端 hermetic + 前端 type-check）。
- `npm run build`：通过。`npm run lint`：**16 个 oxlint 错误均为 HEAD 存量**（stash 验证过基线同样 16 个，集中在 gravatar.ts、api/collection.ts 等未触碰文件），本次改动 0 新增。
- Playwright（mock `/v1/articles`→50 页、`/v1/collections`→25 页、合集详情 articleCount=250）+ 真实数据复核：
  - 桌面 1440：页 1 = `上一页 1 2 … 50 下一页`（5 按钮）；翻到页 25 = `1 … 24 25 26 … 50`（7 按钮 ≤7）✅
  - 移动 390：compact = `‹ 1 / 50 ›` + 上/下页（2 按钮）✅
  - `/collections`、`/collection/1` 替换后渲染正常 ✅
  - 真实数据首页：`共 12 条，第 1 / 2 页` + `1 2` ✅
  - 横向溢出：全部截图 `scrollWidth === innerWidth`（overflow=0）✅
  - 主题：paper-light / paper-dark / classic-light 三套均截图无异常（组件只用 token 类）✅
  - `formatViewCount` 实测：1200→`1.2k`、3400→`3.4k`、12000→`1.2万`、56000→`5.6万` ✅
- 截图存档：`/tmp/t5-shots/`（10 张 mock + 2 张真实数据，含 desktop/mobile × 3 主题）。

## 遗留 / 注意

- **未提交**：T1/T3 与本任务并行改同一工作树，`CollectionsView.vue`/`CollectionView.vue`/`UserProfileView.vue` 同时含 T3 的 `- MySite` 标题清理，无法干净拆成独立 commit；按并行任务惯例留在工作树由 owner 合并提交。
- `BlogPost.vue` 详情页 `{{ article.viewCount }} 阅读`、`DashboardView` 后台表格原始数值未动 —— 前者归 T8 详情页重做，后者后台保持精确值。
- `ArticleList` 现在展示「共 N 条，第 c / N 页」信息条（原 ArticlePagination 无此信息），属统一组件的既有行为。
- lint 基线 16 个存量错误建议单独任务清理（oxlint no-new-array / unused imports 等）。

## Resolution

T5 完成：Pagination.vue 重写为唯一分页实现（±1 窗口化 + 省略号 + compact 移动模式 + 文字按钮，按钮上限 7），ArticlePagination 删除，formatViewCount 收敛 utils/format.ts；8 个后台消费点零改动。verify quick 绿、build 过、Playwright mock 50 页验证。阶段验收通过并随 359f61a 提交。遗留：lint 基线 16 个存量错误建议单独清理任务。
