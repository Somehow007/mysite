# D1 后台身份与去异色（2026-11-16）

> 总档：`.trellis/tasks/11-16-dash-redesign/prd.md`（共同约定 + 验收口径必读）。
> 目标：去掉与锈红纸本冲突的紫/靛/橙红渐变和硬编码色；头像兜底统一；侧栏头部从「控制台 / MySite Admin」换成站点身份。
> 本任务**不依赖** D2 作用域机制，改动对所有主题生效（用的全是 token，旧主题会随各自 accent 变色，属预期）。

## 1. 新增 `components/common/UserAvatar.vue`

统一 4 处「有图显示图、无图显示首字」的头像块。

```ts
props: {
  src?: string | null      // 头像 URL
  name: string             // 用于首字与 alt
  size?: number            // px，默认 32
}
```

- 结构：外层 `span`，`inline-flex items-center justify-center rounded-full overflow-hidden shrink-0 select-none`，`:style="{ width: size+'px', height: size+'px', fontSize: Math.round(size*0.4)+'px' }"`。
- 有 `src` → `<img :src :alt="name" class="w-full h-full object-cover" @error="failed = true">`；`failed` 或无 `src` → 首字。
- 首字兜底样式：`bg-accent-subtle text-accent font-semibold`（**不要渐变**；纸本下是浅锈底 + 锈红字，与前台作者卡同调）。
- 首字：`name.trim().charAt(0).toUpperCase() || '?'`。
- 不带外圈 ring；调用方需要时自己包。

## 2. 替换点（4 处）

| 位置 | 现状 | 改为 |
|---|---|---|
| `components/dashboard/DashboardSidebar.vue` 约 :204-214 用户区头像 | `linear-gradient(135deg,#F59E0B,#EF4444)` + 白字 | `<UserAvatar :src="userStore.user?.avatar" :name="userStore.displayName \|\| 'U'" :size="32" />` |
| `views/UserManageView.vue` 约 :334 用户列表 | `linear-gradient(135deg,var(--accent),#7C3AED)`，从不显示真实头像 | `<UserAvatar :src="item.avatar" :name="item.realName \|\| item.username" :size="28" />`（先确认 `item` 类型上有 `avatar` 字段；没有就只传 `name`，不要改类型/后端） |
| `views/SettingsView.vue` 约 :181 资料卡大头像 | `from-indigo-500 to-purple-600` | `<UserAvatar ... :size="64" />`，外层保留原来的双圈 `shadow-[0_0_0_3px_var(--bg-elevated),0_0_0_4px_var(--accent-subtle)]` 包一层 `span.rounded-full` |
| `views/UserProfileView.vue` 约 :234（前台个人主页，同一问题顺手收） | 同上 | `:size="96"`，同样保留外圈 |

## 3. 侧栏头部（`DashboardSidebar.vue` 约 :98-115）

现状：30px 圆角方块 `linear-gradient(accent,#8B5CF6)` + `LayoutDashboard` 图标 + 「控制台 / {title} Admin」。

改为（与前台 `AppHeader.vue:26-27,96-99` 的 logo 写法一致）：

- 展开态：
  - 第一行：站名，`font-serif text-[17px] font-black tracking-tight text-text-primary`，按 `siteStore.site.title.split('·')` 渲染，分隔点 `·` 用 `text-accent`（直接抄 AppHeader 的 `logoSegments` 逻辑）。`title` 为空时显示「控制台」。
  - 第二行：`text-[11px] text-text-muted tracking-[0.06em]`，文案固定「控制台」（title 为空时这一行不渲染，避免重复）。
- 折叠态（`w-16`）：只显示站名首字，`w-8 h-8 border border-border-strong font-serif font-black text-text-primary flex items-center justify-center`（墨线方框首字，像印章）；`title` 为空时用 `LayoutDashboard` 图标兜底。**不要再有渐变底。**
- 删除 `siteAdminLabel` computed（不再使用）；`LayoutDashboard` import 若仍用于兜底则保留。

## 4. 其他硬编码色

| 位置 | 现状 | 改为 |
|---|---|---|
| `KnowledgeManageView.vue` 约 :537 选中知识库 | `shadow-[0_0_0_3px_rgba(79,70,229,0.14)]`（靛蓝） | `shadow-[0_0_0_3px_var(--accent-subtle)]` |
| `KnowledgeManageView.vue` 约 :544 选中角标 | `bg-accent text-white` | `bg-accent text-text-inverse` |
| `KnowledgeManageView.vue` 约 :855 勾选图标 | `text-white`（在 accent 底上） | `text-text-inverse`（先确认其底色是 accent；若是图片遮罩则不改） |
| `UserManageView.vue` 约 :361 角色下拉面板 | `shadow-[0_12px_40px_-12px_rgba(0,0,0,0.18)]` | `shadow-lg`（D2 会在纸本下覆盖为硬阴影） |

## 不做

- 不改 `CategoryManageView.vue:408` 的 `placeholder="#1890ff"`（是颜色输入框的示例值，不是样式）。
- 不改图片管理/封面上的 `bg-black/50 text-white` 遮罩按钮。
- 不动侧栏导航项样式（D2 负责）。

## 验收（在总档口径之外）

- `rg -n "linear-gradient|from-indigo|to-purple|#8B5CF6|#7C3AED|#F59E0B, #EF4444|79,70,229" mysite-frontend/src/views mysite-frontend/src/components/dashboard` → 0 命中。
- 截图：侧栏头部在 paper-light 显示衬线站名、paper-dark 正常、classic-light 正常；折叠态印章首字；用户管理列表头像不再是紫色。
- 手动：设置页上传头像后侧栏/设置页即时显示图片；把某用户 avatar 改成坏 URL（或 devtools 改 src）→ 回落首字不出现破图。

## 实现记录（2026-11-16）

**改动文件**

- 新增 `src/components/common/UserAvatar.vue`（props: src/name/size=32；`@error` 置 failed 回落首字；`bg-accent-subtle text-accent font-semibold`；src 变化重置 failed）
- `src/components/dashboard/DashboardSidebar.vue`：头部改站点身份（展开=衬线站名 `logoSegments` 分段 + 「控制台」副标；折叠=墨线方框首字印章，title 空兜底 LayoutDashboard）；删 `siteAdminLabel`；footer 头像换 UserAvatar(size=32)
- `src/views/UserManageView.vue`：列表头像换 UserAvatar(size=28，**只传 name**——`AdminUser`/`AdminUserRespDTO` 均无 avatar 字段，按 prd 预留分支);角色下拉 `shadow-[0_12px_40px...]` → `shadow-lg`
- `src/views/SettingsView.vue`：资料卡头像换 UserAvatar(size=64)，外层保留双圈 shadow span
- `src/views/UserProfileView.vue`：主页头像换 UserAvatar(size=96)，外圈 shadow 不变（顺手收，前台同一问题）
- `src/views/KnowledgeManageView.vue`：选中卡 ring `rgba(79,70,229,0.14)` → `var(--accent-subtle)`；角标/勾选图标 `text-white` → `text-text-inverse`（两处，均在 accent 底上非图片遮罩）

**偏离方案（均已验证）**

1. **KB 选中卡 ring 修复**：`shadow-sm` 原在静态 class，与 `:class` 里的任意值 `shadow-[0_0_0_3px_...]` 同优先级且落败（实测 computed `--tw-shadow`=shadow-sm 值）——选中光晕自始是死代码，改前的靛蓝也从未渲染。把 `shadow-sm` 移进未选中分支后 ring 真实生效（未选中态视觉不变）。
2. **`scripts/dash-shots.mjs` 补 `mysite_user_role` 预置**：路由守卫 `requiresAdmin` 读 localStorage `user_role`，早于 `fetchCurrentUser` 回填；不预置时首个落地就是 admin 路由会被重定向到 `/dashboard`（本次 knowledge 直达实测复现）。截图脚本要求 ADMIN 账号，直接透传登录响应 `role`。
3. `UserAvatar` 规格外小补充：根节点加 `role="img"` + `:aria-label="name"`（兜底/图片两种形态统一可访问名）。

**未动（观察项）**

- `CommentManageView.vue` 评论头像是游客 gravatar（无首字兜底、无渐变），不在 prd「4 处」范围，未动。
- `AppHeader.vue` 两处头像块已是 token 写法（`bg-accent text-text-inverse`），不在 4 处范围，未动。

**验收输出**

- `rg` 目标模式 → 0 命中；`npm run type-check` / `npm run build` 绿；`./scripts/verify.sh`（quick）绿。
- `npm run lint`：oxlint 16 errors 全部位于未触碰文件（useToc/useCollection/useMarkdown/useScrollReveal/test-callout/useChatMarkdown/gravatar/ragsec-walkthrough），本次 0 新增；总数 16 vs 文档基线 15 的漂移来自既有脚本，非本次引入。
- `dash-shots.mjs` → `OK: no overflow, no page error`（66 张，`scripts/output/dash-shots-D1/`）。
- DOM 走查（临时脚本，已删）三主题 42/42：侧栏展开=衬线站名「MySite·控制台」副标、折叠=w-16 印章「M」、footer 头像 accent-subtle 底+accent 字（paper-light rgb(240,217,204)/rgb(180,68,31)、paper-dark rgb(58,36,25)/rgb(224,121,79)、classic rgb(238,242,255)/rgb(79,70,229)）、用户页 9 头像 0 渐变、设置页 64px、KB ring=各主题 accent-subtle、勾选图标=text-inverse（paper-dark 正确为深色 rgb(22,19,15)）。
- 坏 URL 回落实测：footer 头像 img src 改 404 → `@error` → 显示首字「管」，无破图。

## Resolution

已落地并通过 Opus 终验（见 `../11-16-dash-redesign/prd.md` 终验记录）。
