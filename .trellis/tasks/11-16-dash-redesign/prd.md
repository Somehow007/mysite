# 后台控制台纸本化（D1–D5 总档，2026-11-16）

> 起因：前台已按 `11-15-ui-concepts` 完成纸本改版（T1–T18），后台 `/dashboard/**` 当时明确「不改布局，只要纸本下能看」。
> 评估结论：骨架（PageHeader / SearchFilterBar / DataTable / Pagination / ConfirmDialog）扎实，**不重写**；
> 问题是视觉语言仍是通用 SaaS（紫渐变、大圆角柔影、Tailwind 默认彩色、页面骨架不齐），与前台割裂。
> 分工：便宜模型按 D1→D5 逐个实现；每个子任务完成后由 Opus 统一验收。

## 证据（2026-11-16 基线）

- 基线截图：`mysite-frontend/scripts/output/dash-shots-baseline/`（paper-light / paper-dark / classic-light × 1440 / 390 × 11 路由 = 66 张，溢出全 0，gitignored，本机才有）。
- 纸本作用域类（`paper-card` / `shadow-hard` / `border-strong` / `paper-flat`）前台 8 个文件在用，后台 **0** 处。
- 硬编码异色：`DashboardSidebar.vue:106`（accent→#8B5CF6）、`:206`（#F59E0B→#EF4444）、`UserManageView.vue:334`（accent→#7C3AED）、`SettingsView.vue:181` / `UserProfileView.vue:234`（indigo-500→purple-600）、`KnowledgeManageView.vue:537`（rgba(79,70,229,.14) 靛蓝光晕）。
- **可访问性实锤**：paper-light 的语义色是从 classic 抄的 Tailwind 默认值，在 `#FBF8F2` 上：success `#10B981` **2.39:1**、warning `#F59E0B` **2.03:1**、info `#3B82F6` 3.47:1，全部低于 WCAG AA 4.5:1 → 「已发布 / 草稿 / 普通用户」徽标不达标（D3 修）。
- 骨架不齐：`KnowledgeManageView.vue:500` 自带 `max-w-6xl mx-auto p-6`（比其他页多一层内边距，切页跳位）；`SettingsView.vue:163-164` 不用 PageHeader；`CategoryManageView` 手写表格且表头样式与 DataTable 不同。

## 子任务与顺序

| 任务 | 目录 | 内容 | 改动面 | 依赖 |
|---|---|---|---|---|
| D1 | `11-16-dash-D1-identity` | 去紫/靛渐变与硬编码色；新增 `UserAvatar`；侧栏头部改站点身份 | 小 | — |
| D2 | `11-16-dash-D2-paper-shell` | 后台作用域纸本外壳（方角/细线/硬阴影浮层/衬线标题），旧主题与前台零影响 | 中（共享层，§2.3） | D1 |
| D3 | `11-16-dash-D3-palette` | paper 语义色达标重调；StatsCard 中性默认；角色徽标去彩 | 小（含 paper token） | D2 |
| D4 | `11-16-dash-D4-skeleton` | 统一页面骨架：知识库/设置/分类页对齐 PageHeader + 容器 + 表头 | 中 | D2 |
| D5 | `11-16-dash-D5-details` | 封面失败兜底、移动端操作收敛、空状态引导 | 小 | D2–D4 |

> 顺序说明：先 D1 去掉最刺眼的紫色（独立、零风险）；D2 建外壳后 D3 的徽标方角才有意义；D3 先于 D4 是因为 D4 改的页面会用到 D3 的徽标语义。每个任务**单独 commit**。

## 共同约定（所有 D 任务）

1. **只用 token**：`bg-bg-*` / `text-text-*` / `border-border*` / `text-accent` / `bg-*-subtle`；禁止新写死颜色（图片遮罩上的 `bg-black/50 text-white` 属于图片层，不算）。accent 底上的文字用 `text-text-inverse`，不要 `text-white`（paper-dark accent 是亮橙，白字对比不足）。
2. **作用域**：纸本视觉只在「paper 主题 + 后台路由」生效。机制由 D2 建立：`DashboardLayout` 挂载时给 `<html>` 加 `dash-mode` class，卸载时移除；样式选择器统一前缀 `html.dash-mode[data-theme^="paper"]`。这样：旧 6 套主题不变、前台不变、Teleport 到 body 的 Modal/Dropdown/Drawer/全局确认框也能命中。
3. **不改**：路由、接口、数据流、权限判断、组件 props 的既有语义（只允许**新增**可选 props / hook class）；不升依赖；不格式化无关文件；`AiManageView`(978 行)/`KnowledgeManageView`(1080 行) 不做拆分重构。
4. **共享层**（`components/ui/*`、`styles/main.css`）属 AGENTS.md §2.3：每个子任务 prd 已列消费点，动手前再 `rg` 复核一遍，有新增调用方要补进 prd。
5. 行号以 2026-11-16 HEAD 为准，**动手前先 rg 定位**，以内容匹配为准。

## 验收口径（每个子任务都要过，缺一不算完成）

1. `./scripts/verify.sh`（quick）绿。
2. `cd mysite-frontend && npm run type-check && npm run build` 绿；`npm run lint` 维持基线（当前 15 个既有问题，**0 新增**；lint 带 --fix，跑完 `git diff` 检查没有误改无关文件）。
3. 截图：`DASH_USER=<admin> DASH_PASS=<pwd> node scripts/dash-shots.mjs scripts/output/dash-shots-Dn`（默认三主题 × 双视口 × 11 路由）。输出 `OK: no overflow, no page error`；与 `dash-shots-baseline/` 对比，**classic-light 截图除本任务明示的改动外应无变化**。账号只走环境变量，不得写进任何文件/prd。
4. 子任务 prd 末尾写 `## 实现记录`（改了哪些文件、偏离方案之处及原因、验收输出摘要）。
5. `.trellis/CHANGES.md` 追加一行。commit 作者为用户本人、message 不含 Co-authored-by；一个子任务一个 commit。

## Opus 终验清单（全部 D 完成后）

- 三主题 × 双视口逐页对比基线；paper 下与前台首页/文章页并排看是否同一产品。
- 交互走查：侧栏折叠/移动抽屉、Modal/ConfirmDialog/角色 Dropdown（Teleport 层）、表格选择 + 批量条、筛选栏聚焦态、知识库选中态、图片卡 hover。
- 键盘：Tab 焦点可见（paper 下 focus-visible 不能被方角/去阴影规则吃掉）。
- 对比度脚本复核 D3 取值；前台 paper 页面用到语义色的地方（评论状态、聊天错误提示等）无掉色。
- 从后台点「回到主页」后 `<html>` 不再带 `dash-mode`。
