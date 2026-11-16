# D3 语义色达标与去彩（2026-11-16）

> 总档：`.trellis/tasks/11-16-dash-redesign/prd.md`。前置：D2 已合入。
> 原则：纸本里**颜色只表达语义**。锈红 accent 是唯一的品牌强调色；success / warning / danger 只用于真实状态；身份类信息（角色、计数）用墨色层级，不上彩。

## 1. paper 主题语义色重调（`styles/themes/paper.css` 两个主题块顶部 8 行）

现值是从 classic 抄的 Tailwind 默认色，paper-light 下不达标（实测 `#FBF8F2` 底：success 2.39:1、warning 2.03:1、info 3.47:1）。

| token | paper-light 现 → 新 | paper-dark 现 → 新 |
|---|---|---|
| `--success` | `#10B981` → `#2A6B43` | `#34D399` → `#6FBF8A` |
| `--success-subtle` | → `rgb(42 107 67 / 0.12)` | → `rgb(111 191 138 / 0.16)` |
| `--warning` | `#F59E0B` → `#85570F` | `#FBBF24` → `#E0A84A` |
| `--warning-subtle` | → `rgb(133 87 15 / 0.12)` | → `rgb(224 168 74 / 0.16)` |
| `--danger` | `#EF4444` → `#A8261B` | `#F87171` → `#F07B6E` |
| `--danger-subtle` | → `rgb(168 38 27 / 0.12)` | → `rgb(240 123 110 / 0.16)` |
| `--info` | `#3B82F6` → `#36597A` | `#60A5FA` → `#8FB0CF` |
| `--info-subtle` | → `rgb(54 89 122 / 0.12)` | → `rgb(143 176 207 / 0.16)` |

已验算对比度（文字色 vs 底色 / vs 12% 同色浅底）：

- paper-light 在 `--bg-elevated #FBF8F2` 上：success 6.04 / 5.09、warning 5.88 / 4.99、danger 6.70 / 5.51、info 6.91 / 5.79。
- paper-dark 在 `--bg-elevated #221E18` 上：success 7.49 / 6.05、warning 7.79 / 6.17、danger 6.13 / 5.09、info 7.32 / 5.86。
- danger `#A8261B` 偏正红，与锈红 accent `#B4441F`（偏橙）可区分；danger 只出现在删除/失败/禁用这类语境，不会和 accent 同框混淆。

⚠️ 这是**主题级** token，前台 paper 页面同步受益（评论状态、聊天错误、toast 等）。改完要截前台首页 / 文章页 / 聊天面板确认无异常。`--danger` 的按钮 `.btn-danger` 用 `color:#FFFFFF`，`#A8261B` 底白字 7.0:1，没问题。

## 2. `components/ui/StatsCard.vue`：新增中性色，并设为默认

- `SemanticColor` 加 `'neutral'`，`iconClasses.neutral = 'bg-bg-secondary text-text-secondary'`。
- `resolvedIconColor`：`iconColor` 优先；其次 `accent` prop（非 default）；**兜底改为 `'neutral'`**（原兜底 accent）。
  - 兼容性：当前所有调用方都显式传了 `icon-color`（见 §4），兜底变化不影响现有页面。
- 不改数值着色逻辑。

## 3. 调用方调整

**`views/UserManageView.vue` :278-281**（4 张统计卡现在是 accent/warning/info/danger 四色，像色板）：

| 卡 | 现 | 改 |
|---|---|---|
| 用户总数 | `icon-color="accent"` | 删掉 `icon-color`（走 neutral） |
| 管理员 | `warning` | 删掉 |
| 创作者 | `info` | 删掉 |
| 禁用 | `danger` | `:icon-color="disabledCount > 0 ? 'danger' : undefined"`，并加 `:color="disabledCount > 0 ? 'danger' : undefined"`（有禁用才报警；`disabledCount` 抽成 computed，复用原表达式 `users.filter(u => u.status === 0).length`） |

**`views/AiManageView.vue` :677-702**：

| 卡 | 现 | 改 |
|---|---|---|
| 费用 | `accent` | 保留 accent（成本是这页的主指标，CLAUDE.md 成本是硬约束，值得唯一强调） |
| Token | `info` | 删掉 |
| 调用次数 | `success` | 删掉（调用多≠成功） |
| 成功率 | `:icon-color="successTone"` | 保留（真实状态） |

**角色徽标 `views/UserManageView.vue` :354**：

现 `ADMIN→warning（黄）/ CREATOR→accent / USER→info（蓝）`。角色是身份不是状态，不该用警示黄和信息蓝。

- `Badge.vue` 新增 variant `'outline'`：`'bg-transparent text-text-secondary border border-border'`（dot 色用 `bg-text-muted`）。
- 映射改为：`ADMIN → 'accent'`、`CREATOR → 'outline'`、`USER → 'muted'`。保留 `dot`。

## 4. 其他徽标语义

下列 Badge 调用已逐个核对，结论如下：

- `AiManageView.vue:39-45` `CALL_TYPE_BADGE`（调用类型是**分类**不是状态）：现 `CHAT accent / REWRITE info / CLASSIFY warning / EMBED success / RERANK muted`——「向量化」显示为成功绿、「意图分类」显示为警告黄，会被读成状态。改为 `CHAT → 'accent'`（主力调用，保留强调），其余四类统一 `'outline'`。**必须改。**
  - 同文件 :47 的 `CALL_TYPE_COLOR` 是消费看板图表的分类配色，图表需要可区分的色相，**不改**。
- `KnowledgeManageView` `sourceType ARTICLE→accent / 其他→muted`：保留。
- `Settings` 邮箱/手机「已验证/已绑定」success、未验证 warning：保留。
- `CommentManageView.vue:39-43` `statusMap`：待审核=warning、已通过=success、已拒绝=danger，语义正确，保留。

## 不做

- 不改 classic 等旧主题的语义色。
- 不改 `--accent`。
- 不新增 token 名（只改值），所以不需要给其他 8 套主题补值。

## 验收（总档口径之外）

- 对比度复核：把上表 16 个值和对应底色跑一遍 WCAG 公式（总档目录下可写个一次性 node 脚本，不入库），文字色在 `--bg-elevated` / `--bg-primary` 上 ≥ 4.5:1。
- 截图：用户管理统计卡 3 灰 1（禁用=0 时也灰）；AI 管理只有「费用」和「成功率」有颜色；角色列 ADMIN 锈红、CREATOR 线框、USER 灰；调用类型不再红绿。
- 前台 paper-light / paper-dark：首页、文章页、聊天面板（触发一次错误提示）无掉色。
- classic-light 截图：除统计卡图标变灰、角色徽标、调用类型徽标外，无变化（这几处是语义修正，所有主题生效，属预期）。

## 实现记录（2026-11-16）

### 改动文件

- `src/styles/themes/paper.css`：paper-light / paper-dark 两块顶部 8 个语义 token 按 §1 表重调（subtle alpha light 0.12 / dark 0.16）。
- `src/components/ui/StatsCard.vue`：`SemanticColor` 增 `'neutral'`（`bg-bg-secondary text-text-secondary`）；兜底 accent→neutral。
- `src/components/ui/Badge.vue`：variant 增 `'outline'`（`bg-transparent text-text-secondary border border-border`，dot `bg-text-muted`）。
- `src/views/UserManageView.vue`：4 张统计卡去彩，`disabledCount` 抽 computed，禁用卡 `>0` 才 danger（icon+color）；角色徽标 ADMIN→accent / CREATOR→outline / USER→muted。
- `src/views/AiManageView.vue`：Token / 调用次数统计卡去 `icon-color`；`CALL_TYPE_BADGE` 改 CHAT→accent、其余四类→outline（`CALL_TYPE_COLOR` 图表配色不动）。

### 偏离方案

- 无实质偏离。两点说明：
  1. §1 验算表中 paper-dark subtle 对比度（6.05/6.17/5.09/5.86）按 WCAG 公式复算对不上（subtle 色 alpha 合成到 bg-elevated 得 5.54/5.70/4.73/5.45，合成到 bg-primary 得 6.31/6.51/5.37/6.21）；两种口径全部 ≥4.5，最严口径 dark danger-on-elevated-subtle = 4.73 仍达标。
  2. `StatsCard` deprecated `accent` prop 类型未加 `'neutral'`（prd 只要求 `SemanticColor`；运行时 accent→iconColor 链路已兼容）。

### 验收输出摘要

- `./scripts/verify.sh` quick：VERIFY OK（后端 hermetic + 前端 type-check）。
- `npm run type-check` / `npm run build`：绿。
- `npm run lint`：oxlint 18 errors 全部为存量（stash 后 HEAD 复跑同为 18；总档记 15，D1/D2 走查脚本带入增量），本任务 0 新增；`--fix` 无误改（git status 仅 5 个目标文件）。
- 对比度脚本（/tmp 一次性，不入库）：paper-light/dark × 4 语义色 × bg-elevated/bg-primary × 纯底/subtle 合成底 = 32 项全部 ≥4.5:1。
- `dash-shots.mjs` 三主题 × 双视口 × 11 路由：66 张 `OK: no overflow, no page error`。
- DOM 断言（`scripts/output/d3-check.mjs`，一次性 gitignored）：token computed 值、用户管理 4 卡全 neutral（库内禁用=0，全灰符合预期）、角色徽标 ADMIN accent / USER muted、AI 统计卡仅「费用」accent +「成功率」danger（真实低成功率语义）、调用类型 CHAT accent + EMBED/CLASSIFY outline —— 17/20 PASS；3 项 FAIL 均为种子数据缺失（t_user 无 CREATOR、LLM 日志无 REWRITE/RERANK 行），同一三元/记录字面量代码路径的兄弟分支已验证。
- 前台回归（`scripts/output/d3-front-check.mjs`）：paper-light/dark 首页 + 文章页 token 正确、无 pageerror。
- classic-light token computed 复核：`--success #10B981` 等 4 值不变（classic 不引用 paper.css）。

## 走查记录（2026-11-16，`scripts/d3-walkthrough.mjs`）

- 方法：三主题 × 桌面 1440 + paper-light 移动 390，DOM 级断言（computed color / class / border-radius），覆盖 users / posts / ai 三页。
- 造数：临时将 `t14check` 置 CREATOR+禁用以覆盖条件分支（禁用卡 danger、CREATOR outline、禁用状态徽标），走查后已还原 USER+正常（DB 复核确认）。
- 结果：**66 pass / 0 fail / 6 skip**。skip = REWRITE/RERANK 调用类型徽标（×3 主题，种子数据无此类日志；与已验证的 CLASSIFY/EMBED 同一记录字面量路径）。
- 覆盖到的关键点：禁用=1 时「禁用」卡 icon+数值双 danger；禁用=0 时全 neutral（前次 d3-check 已验）；outline 徽标 computed borderColor=主题 --border、1px；paper+dash-mode 徽标方角 0px、classic pill；文章管理手写「已发布/草稿」徽标 computed 色=success/warning（paper 新值生效）；AI 页仅费用 accent + 成功率语义色。
- 踩坑：Tailwind v4 `rounded-full` 编译为 `calc(infinity*1px)`，computed border-radius 是 `3.36e+07px` 而非 `9999px`——断言改按 `>=9999` 判 pill。
- lint：走查脚本 0 新增（oxlint 维持 18 存量）。
