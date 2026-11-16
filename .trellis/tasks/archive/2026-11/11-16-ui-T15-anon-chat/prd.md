# UI 重构 T15：匿名聊天死代码清理

> 任务性质：§2.2 匿名可达路径变更，已于 2026-11-16 评审拍板（D1–D5 全按推荐）。
> 总计划出处：`.trellis/tasks/11-15-ui-concepts/prd.md` T15 节；审计依据：`archive/2026-11/11-15-ui-audit/prd.md`。

## 评审结论（2026-11-16 owner 拍板）

| 决策 | 结论 |
|---|---|
| D1 未登录面板 | **登录门**：面板照常打开，内容区换登录引导，不发请求；含「登录后自动重开面板」增强 |
| D2 visitorId 管道 | **保留管道改语义**：继续作会话归属 key，后端参数/列/索引不动，措辞改「浏览器级会话标识」；`user_id` 维持空置 |
| D3 会话接口安全洞 | **另开任务**：getMessages IDOR + `?userId=` 伪造，本任务不动 |
| D4 chat/stream | **收紧为 authenticated**：匿名在安全层 401 |
| D5 重命名/删除死按钮 | **删 UI + API 函数**：后端端点不存在，死代码一并清 |

## 现状核对（2026-11-16 代码实测）

### 匿名聊天确实已死，三层都堵死

| 层 | 事实 |
|---|---|
| 安全 | `WebSecurityConfig` 中 `/v1/rag/chat/stream` 是 **permitAll**（行 89 注释还写「允许匿名提问」——已过时）；`/v1/rag/conversations/**` 是 **authenticated** |
| 限流 | `ChatRateLimiter.check()` 对 `role == null` 抛「请登录后使用 AI 助手」→ `RagChatService` 转成 SSE `error` 事件（不是 HTTP 错误） |
| 前端 | 匿名唯一还能走通的只有 `GET /v1/rag/knowledge-bases`（permitAll）→ 面板里 KB 标签会渲染，但发不出消息 |

### 关键发现：visitorId 不是死代码，它是**所有会话的唯一归属 key**

- `t_conversation.user_id` 列存在但**从未被写入**：`ConversationManager.getOrCreateConversation` 只 `setVisitorId`（`ConversationDO.userId` 恒 NULL）。
- 前端 `getConversations()` 只传 `visitorId` 参数，从不传 `userId` → `listConversations` 的 `userId` 分支是死路。
- 结论：**登录用户的会话历史同样按浏览器 UUID 归属**——换浏览器/设备历史即丢；`user_id` 整条链路（列、索引、mapper、controller 参数、测试）全空转。
- `t_llm_usage.visitor_id` 每次聊天都写（前端无条件带参），后台 `AiManageView` 的「访客 xxx」标签靠它——本任务不动。

### 顺带发现的存量问题（评审时一并定性）

1. **`GET /v1/rag/conversations/{id}/messages` 无归属校验**：任何登录用户可按会话 ID 读任意会话内容（IDOR；snowflake 难枚举，但无鉴权事实成立）。
2. **`GET /v1/rag/conversations?userId=` 的 userId 来自请求参数、可伪造**：当前因 `user_id` 全 NULL 恒返回空所以无实际泄露——**一旦开始写 `user_id`，此口子立即变实**。
3. **`ChatHistory` 重命名/删除按钮调用的后端端点不存在**：`PUT /v1/rag/conversations/{id}/rename`、`DELETE /v1/rag/conversations/{id}` 在 `RagChatController` 无映射 → 必然 404 + toast 报错。死功能 UI。
4. `ChatRateLimiter` javadoc 写「未登录 → WebSecurityConfig 已拦截为 401」——与实际不符（端点 permitAll，是本类自己拒的）。

### 前端死 UI 清单

| 位置 | 现状 | 处理 |
|---|---|---|
| `ChatInput.vue:31-36` | 未登录显示「游客额度 10 次/小时」——虚假承诺（实际被拒） | 删匿名分支 |
| `ChatWidget.vue:513-523` | 匿名提示条「登录后可保存对话历史，享受更多额度」+登录钮 | 删除，改登录门（D1） |
| `ChatWidget.vue:132,239` | 面板每次打开都 `loadConversations(getVisitorId())`，匿名必 401 | 加 `isLoggedIn` 门控 |
| 空态 chips / `handleSend` | 匿名点 chip 或发送 → 后端 error「请登录后使用」 | 登录门后自然消失 |
| `api/rag.ts:31-50` | `getVisitorId()` + 注释「匿名会话归属」 | 函数保留（会话 key），改注释语义 |
| `api/rag.ts:318-337` + `ChatHistory.vue` | `renameConversation`/`deleteConversation` + 编辑/删除 UI | 后端无端点（见问题 3）→ 删 or 补，评审定 |

## 方案选项（待拍板）

### D1 未登录用户打开聊天面板看到什么

- **A（推荐）登录门**：面板照常打开（标题/关闭可用），消息列表+输入框整块替换为「登录后使用 AI 助手」+ 说明 + 登录按钮（`login?redirect=当前页`）。可选增强：点击登录先写 sessionStorage 标记，回跳后自动开面板（对齐 pendingAsk 体验，~15 行）。不发任何请求。
- **B 保留输入框，发送时跳登录**：走 `stashPendingAsk` 既有模式；但现仅 HomeAskPanel/BlogPost 消费暂存，其他页面发起会丢 → 需把 ChatWidget 变成全局 pendingAsk 消费者，改动更大。
- **C 匿名不渲染 FAB/入口**：最省事但 AI 功能对匿名完全隐形，dock「问这篇」等入口还要各自处理。

### D2 visitorId 管道（会话归属）

- **A（推荐）保留管道、改语义**：visitorId 继续作会话归属 key 传给后端（它现在就是），后端参数/列/索引不动；把「匿名访客标识」的注释/文档措辞改为「浏览器级会话标识」。`user_id` 维持空置。未来要切账号归属时再开任务。
- **B 顺手双写 user_id**：`getOrCreateConversation` 加写 `user_id`（仅落库不改查询）→ 但 `?userId=` 伪造面立即激活，必须同改 `listConversations` 鉴权（从 UserContext 取），且现有会话 `user_id` 全 NULL，查询逻辑要做 visitorId 兜底设计——实际把安全修复也拉进本任务。
- **C 直接切账号归属**：登录用户 `user_id` 为主 key、visitorId 兜底 → 存量会话 `user_id` 全 NULL，历史断代（除非回填）；建议独立任务做。

### D3 会话接口安全小洞（getMessages IDOR + userId 伪造）

- **推荐另开任务**：正确解依赖归属模型（D2），且属 §2.2 认证面。本任务只记录不动。
- 若用户要求本任务顺手修：最小修法 = `listConversations` 的 `userId` 改从 `UserContext` 取（去请求参数）；`getMessages` 加归属校验需前端补传 visitorId/userId——工作量中等，评审时可升级。

### D4 `/v1/rag/chat/stream` 要不要从 permitAll 收紧为 authenticated

- **收紧**：匿名在安全层直接 401，不再走到限流器；前端有登录门后不发匿名请求。属 §2.2 变更，未来恢复游客额度时需 revert。
- **保持 permitAll**：改动最小，限流器继续兜底拒绝；「保留恢复开关」的成本最低。

### D5 ChatHistory 重命名/删除死按钮

- **删 UI + 删 API 函数**：与 T15 死代码清理一致；功能要用时和 D3 安全修复一起补后端。
- **补后端端点**：`PUT .../rename` + `DELETE .../{id}`（带归属校验）——属新功能，建议并入后续「会话管理」任务而非本任务。

## 文档同步清单（实施时）

- `CLAUDE.md`「Anonymous chat」bullet → 按最终方案改写
- `.cursor/rules/frontend-conventions.mdc`「匿名聊天用 visitorId UUID」→ 改写
- `.cursor/rules/backend-conventions.mdc`「匿名可读 … RAG stream」→ 视 D4
- `.cursor/rules/ragent.mdc` SSE 签名 → 参数保留则不动
- `docs/MAP.md`「匿名用 visitorId」→ 改写
- 代码注释：`WebSecurityConfig:89`、`ChatRateLimiter` javadoc、`RagChatController`/`ConversationManager`/`ConversationDO` 的「匿名访客标识」措辞 → 按 D2/D4 改述
- `docker/init/ragent-schema.sql` 注释「可空：匿名聊天不强制登录（见 3.5 节决策 3）」→ 顺手改（仅影响新装库）
- `docs/ragent-integration-plan.md`、`ragent-frontend-design.md` → 历史设计文档，不改

## 消费点（已 grep 全量）

- `getVisitorId`：`api/rag.ts` 内部 + `ChatWidget.vue:132,239`（2 处 `loadConversations` 调用）
- `getConversations`：`useChat.loadConversations` ← 仅 `ChatWidget` 调用
- `getConversationMessages`：`useChat.switchConversation` ← `ChatHistory @select` ← `ChatWidget`
- `renameConversation`/`deleteConversation`：仅 `ChatHistory.vue`
- `visitorId` 后端链：`RagChatController.chatStream` → `RagChatService.chat`（3 个重载）→ `doChat` → `UsageContext.visitorId` + `ConversationManager.getOrCreateConversation` → `t_conversation.visitor_id`；`listConversations` visitorId 分支
- 测试：`RagChatControllerTest`（visitorId 断言 4 处）、`ConversationManagerTest`（IDOR 2 例）、`ChatRateLimiterTest`（无匿名用例）

## 验收（实施后）

1. `./scripts/verify.sh`（quick）通过；`npm run lint && npm run build` 通过
2. Playwright 1440×900 + 390×844：匿名开 FAB/sheet → 登录门；登录 → 面板正常（历史/KB/额度文案按角色）；无横向溢出
3. paper-light / paper-dark / classic-light 三主题各截一张，确认无掉色
4. `.trellis/tasks/<本目录>/prd.md` 补 Resolution；`.trellis/CHANGES.md` 追加一行

## 实施记录（2026-11-16）

按评审结论落地，改动文件：

**后端**
- `WebSecurityConfig`：`/v1/rag/chat/stream` 由 permitAll 收紧为 authenticated，与 `/v1/rag/conversations/**` 合并为一条规则；过时注释「允许匿名提问」已改。
- 语义注释统一改述（参数/列/索引零改动）：`RagChatController`（SSE 头注释 + @param + maskVisitorId）、`RagChatService` @param、`ConversationManager` @param + IDOR 注释补「user_id 预留未启用」、`ConversationDO` 类/字段注释、`ChatRateLimiter` 类注释（「WebSecurityConfig 已拦截 401」改为「安全层拦截 + 本类双保险」——D4 后此描述才成立）、`docker/init/ragent-schema.sql` t_conversation 两列注释。

**前端**
- `ChatWidget.vue`：删匿名提示条；新增登录门（`!isLoggedIn` 时消息区+输入框整块替换为「登录后使用 AI 助手」+登录按钮，header 历史/新会话按钮同步隐藏，KB 栏加登录门控防登出残留）；`loadConversations`/`loadKbs` 仅登录时调用（匿名不再打必 401 的 conversations）；`goLogin` 写 `mysite_reopen_chat` sessionStorage 标记 + `isLoggedIn` watcher 消费 → 登录回跳自动重开面板；删 `handleDeleteConversation`/`handleRenameConversation`（随死按钮一并清）。
- `ChatInput.vue`：quotaText 删「游客额度 10 次/小时」分支。
- `ChatHistory.vue`：删重命名/删除全部 UI+状态+emits（后端端点不存在，点了必 404）。
- `api/rag.ts`：删 `renameConversation`/`deleteConversation`（无后端映射）；`getVisitorId`/`getConversations` 注释改「会话归属标识」。
- `chat.css`：注释「匿名提示」→「限流 / 错误条」。

**文档**：`CLAUDE.md` Anonymous chat → Chat access 改写；`docs/MAP.md` 安全门清单移除 chat/stream permitAll + 「匿名用 visitorId」改述；`.cursor/rules/frontend-conventions.mdc`、`backend-conventions.mdc` 同步。

**验证**
- `./scripts/verify.sh`（quick）✅（含后端 hermetic 全量测试 + 前端 type-check；工作树有并行 T14/T17 改动，全部一并通过）
- `npm run lint`：15 errors = 存量基线（useMarkdown/useCollection/useScrollReveal/useToc/useChatMarkdown/gravatar），本任务 0 新增
- `npm run build` ✅
- `scripts/t15-verify.mjs`（新增，沉淀）：**38/38 PASS**——匿名桌面/移动登录门、面板期间零 `/v1/rag/*` 请求、登录跳转+reopen 标记、登录态（route mock /v1/auth/me）面板/历史抽屉/KB/额度文案正常、历史区无重命名删除按钮、paper-light 主题、双视口溢出=0、pageerror=0。截图 `scripts/output/t15/`（5 张，gitignored）。
- 未覆盖：真实后端 401 行为（docker 未起）——安全改动与相邻 `conversations/**` 同为 `authenticated()` matcher，模式一致；建议下次 `verify.sh full` 时顺手 curl 验证。

## 后续（另开任务）

- **会话接口安全加固**（D3 遗留）：`GET /conversations/{id}/messages` 无归属校验（IDOR）；`GET /conversations?userId=` 参数可伪造（当前 user_id 全 NULL 无实际泄露，写 user_id 前必须先修）。
- **会话归属切账号**（D2-C 遗留）：登录用户历史目前按浏览器 visitorId 归属，换设备即丢；切 user_id 需处理存量 user_id 全 NULL 的断代/回填。
- **会话重命名/删除**：后端补 `PUT .../rename` + `DELETE .../{id}`（带归属校验）时再恢复 UI。

## 走查复核（2026-11-16，`scripts/t15-walkthrough.mjs`）

DOM 级二次复核，40/40 PASS，截图 `scripts/output/t15-walk/`。覆盖首轮 verify 之外：

- **A. 登录回跳重开 e2e**：mock POST `/v1/auth/login` + `/v1/auth/me`，真实填表单提交 → redirect /about → watcher 消费 `mysite_reopen_chat` → 面板自开、门消失、输入框回归；flag 单次消费后换页不再自开。
- **B. 历史有数据态**：mock 裸数组 conversations（2 条）+ messages → 抽屉渲染/选中回填/无重命名删除入口。
- **C. 角色额度**：USER=10/h、CREATOR=20/h、ADMIN=无限，mock 三角色 /v1/auth/me 逐验。
- **D. 管道保留证据**：mock SSE 发消息 → 请求 URL 仍带 `visitorId=`（D2 落地佐证）、非文章页不带 articleId、content 流式渲染。
- **E. 关闭路径**：Esc/遮罩/重复开合。
- **F. dock 入口**：mock 文章 → 移动 dock「问这篇」→ 登录门 +「✦ 问这篇文章」标题。
- **G/H**：paper-dark 门、首页无 FAB 回归。

### 走查发现并修复（2 处）

1. **移动端遮罩点击是死交互**（存量，T9 起）：sheet 顶留 ~24px 缝本意为可点遮罩关闭，但 sticky header（`z-50`，52px）整体压在遮罩（`z-40`）上——`elementFromPoint` 实测全屏无遮罩顶层点，`@click=close` 永远不可达。修：遮罩 `z-40 → z-50`（与 header/sheet 同级，DOM 序在 header 后 → 盖住 header），24px 缝真正生效且 header 随模态变暗更符合语义。
2. **post 上下文匿名副标题丢阅读上下文**：匿名走登录门时副标题一律「登录后可向文章提问」，丢了「正在阅读：{title}」。修：匿名 + isPostContext 仍显示 `正在阅读：{title}`，其余匿名场景副标题=「登录后使用 AI 助手」。

同步修：`t15-verify.mjs` 断言选择器因副标题/门标题文案重复触发 Playwright strict-mode，改 `p.font-serif` 精确命中门标题，回归 38/38。

## Resolution

- **结论**：匿名聊天已清理收口。`/v1/rag/chat/stream` 收紧为 `authenticated()`；前端匿名打开面板=登录门（零 RAG 请求、不发历史/KB 请求），登录回跳自动重开面板；游客额度文案、匿名提示条、历史重命名/删除死 UI、`api/rag.ts` 无映射 API 全部移除。`visitorId` 保留为浏览器级会话标识（SSE 请求与会话归属不变，D2）。
- **验证**：verify quick 绿、build 过、lint 15 基线 0 新增；`t15-verify.mjs` 38/38；`t15-walkthrough.mjs` 40/40（含登录回跳重开 e2e、SSE visitorId 透传、三角色额度、dock 门）。
- **走查修复**：遮罩 z-40→z-50（存量死交互：header 压顶致点遮罩关闭不可达）；post 匿名副标题保留「正在阅读：{title}」。
- **未覆盖**：真实后端 401 未实测（docker 未起），下次 `verify.sh full` 补 curl。
- **遗留另开任务**：会话接口安全加固（`/conversations/{id}/messages` IDOR、`?userId=` 可伪造）、登录用户会话按 user_id 归属切换、重命名/删除后端端点（补齐后恢复 UI）。
