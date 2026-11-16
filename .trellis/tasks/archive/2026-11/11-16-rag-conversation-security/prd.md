# RAG 会话接口安全加固（第一步：堵两个洞）

> 2026-11-16 立项。T15 遗留「另开任务」项的落地——先修可立即修的两个安全洞，
> 归属模型切换（user_id）与重命名/删除端点留在本任务后续阶段。
> §2.2 认证面变更，owner 已授权本会话实施。

## 1. 问题（T15 摸底确认）

| 洞 | 现状 | 风险 |
|---|---|---|
| `GET /v1/rag/conversations/{id}/messages` | **无任何归属校验**，登录用户按会话 ID 读任意会话全文 | IDOR（snowflake 难枚举但无鉴权事实成立） |
| `GET /v1/rag/conversations?userId=` | userId 来自请求参数可伪造 | 当前 `t_conversation.user_id` 全 NULL 恒返回空，无实际泄露；**启用 user_id 前必须先修** |

归属模型现状：所有会话以 `visitor_id`（浏览器 UUID）归属，`user_id` 列预留未启用。
visitorId 本身是持有者凭证——知道他人 visitorId 即可冒名（固有局限，归属切换任务解决）。

## 2. 本次修复（立即可修部分）

1. `listConversations`：**删除 `userId` 请求参数**（伪造面 + 死分支——user_id 全 NULL 恒空）。
   签名收敛为 `listConversations(visitorId)`；`selectByUserId` mapper 保留待归属切换启用。
2. `getMessages`：加归属校验——`@RequestParam visitorId`，会话不存在或 `visitorId` 不匹配
   → 返回空列表（不泄露存在性，与 listConversations 空语义一致）。
3. 前端：`api/rag.ts getConversationMessages(convId, visitorId)` 透传；
   `useChat.switchConversation` 传 `getVisitorId()`。

## 3. 留在本任务后续（不在本次提交）

- **归属切账号**：登录用户历史按 user_id 主 key、visitorId 兜底；存量会话 user_id 全 NULL
  需断代/回填设计；切换前必须先完成本次 userId 参数清除（已做）。
- **重命名/删除端点**：`PUT /conversations/{id}/rename` + `DELETE /conversations/{id}`
  （带归属校验），补齐后恢复 ChatHistory UI（T15 已删死按钮）。
- visitorId 凭证模型的根本局限随归属切换一并解决。

## 4. 验收

- `RagChatControllerTest` 更新：userId 参数用例移除/改写、getMessages 归属四态
  （匹配/不匹配/不存在/缺 visitorId）。
- verify quick 绿；前端 type-check 绿。
- 手工：A 浏览器登录态切换自己历史正常；伪造 visitorId 拉 messages → 空。

## Resolution

2026-11-16 完成实施与验证。

**实施（方案 §2 全部落地 + 走查新修 2 处）**

- `listConversations`：`userId` 请求参数删除，签名收敛为 `(visitorId)`；
  `selectByUserId` mapper 保留待归属切换启用。
- `getMessages`：+`visitorId` 归属校验——会话不存在 / 不匹配 / 缺 visitorId → 空列表。
- `ConversationManager.getOrCreateConversation` 同源加固：归属匹配要求 visitorId 非空
  （visitor_id 列可空，双 null 不得视为归属）。
- 前端：`getConversationMessages(convId, visitorId)`、`useChat.switchConversation` 传 `getVisitorId()`。
- **走查新修**：`WebSecurityConfig` 中 `GET /v1/rag/knowledge-bases/{id}` 是死 pattern
  （Ant 匹配不认 `{id}` 占位），KB 详情实际落到 `/**` ADMIN——改 `*` 恢复注释声明的公开意图
  （KB DTO 仅元数据，与已公开的 list 同数据等级；fail-open 方向但恢复的是既有文档化意图）。

**验证**

- `RagChatControllerTest`：归属四态（匹配→消息 / 错访客→空且不查库 / 缺 visitorId→空 /
  会话不存在→空）+ listConversations 无参→空；13/13 绿。
- 全量 hermetic 505/505 绿；前端 type-check/build 绿。
- 真实 API 走查（2026-11-16）：正确 visitorId→消息返回；伪造/缺参/不存在→空；
  `?userId=1` 伪造失效（参数已删）；匿名→A070100；KB 详情匿名 200、USER 管理 403。

**遗留（本任务后续阶段，按 PRD §3 原计划留档）**

- 归属切 user_id 主 key + 存量断代/回填设计；重命名/删除端点补齐后恢复 ChatHistory UI。
- visitorId 凭证模型固有局限（知凭证即可冒名）随归属切换解决。

---

# 阶段二：归属切 user_id + 重命名/删除端点（2026-11-16 实施）

## 5. 设计（已与 owner 对齐两项契约决策）

**归属谓词**（`ConversationManager.isOwner`，读/写单一事实源）：

- `user_id` 非空 → 只认账号；`visitorId` 立即失效（共享浏览器/跨设备都安全）
- `user_id` 为空 → 存量会话，`visitor_id` 非空匹配兜底

**写路径**：`getOrCreateConversation(convId, visitorId, userId, question)`——命中归属且
`user_id` 为空时 lazy claim（`claimUserId` 带 `user_id IS NULL` 条件，并发先到先得）；
新会话 `user_id`+`visitor_id` 双写。归属不匹配 → 静默开新会话（沿用防 IDOR 口径）。

**读路径**：`listConversations` → `selectVisibleTo(userId, visitorId)`
（`user_id = ? OR (user_id IS NULL AND visitor_id = ?)`）；`userId` 只取自
`UserContext`，不接受请求参数。`getMessages` 非属主 → 空列表（不泄露存在性）。

**存量回填**：`RagentSchemaMigration` 幂等 UPDATE——`t_llm_usage` 中该会话
`COUNT(DISTINCT user_id)=1` 才回填（本地 10/10 会话全部无歧义命中）；
多用户歧义的留给 lazy claim。

**重命名/删除**：`PUT /conversations/{id}/rename`（body `{title}`）、
`DELETE /conversations/{id}`，均带 `visitorId` query 兜底参数；归属失败与不存在
统一 `A120100 会话不存在`（不区分，不泄露存在性）。删除同事务先清
`t_conversation_message`（FK 无级联）再删会话，`@Transactional("ragentTransactionManager")`。
conversations 系列端点统一 `Result` 包装（消除历史裸 JSON 债），前端走 `client.ts`。

**已知边界**：未认领会话在共享 visitorId 下对多账号可见（认领前固有歧义，
续聊即收敛）；空标题 `A120101`、超 200 字 `A120102`。

## Resolution（阶段二）

2026-11-16 完成实施、单测与真实 API 走查。

**实施**

- `ConversationManager`：`isOwner` 谓词、`getOrCreateConversation` +userId/lazy claim、
  `requireOwnedConversation`/`renameConversation`/`deleteConversation`（写路径）。
- `ConversationMapper`：`selectVisibleTo`/`claimUserId`/`renameTitle`；
  `ConversationMessageMapper`：`deleteByConversationId`。
- `RagChatController`：三端点 `Result` 化 + rename/delete 新端点；
  `RagChatService.doChat` 透传 userId；`ErrorCode` 新增 A12 RAG 段。
- `RagentSchemaMigration`：usage 回填（幂等，仅 `user_id IS NULL` 且单 user_id 会话）。
- 前端：`rag.ts` 四会话 API 全走 `client.ts`（自动 token + Result 解包），
  恢复 `renameConversation`/`deleteConversation`；`ChatHistory.vue` 从 cade240^
  恢复重命名/删除 UI；`ChatWidget` 接 `@deleted`/`@renamed`。

**验证**

- `ConversationManagerTest` 27 例（isOwner 四态/getOrCreate 归属+claim/写路径校验）、
  `RagChatControllerTest` 18 例、`RagChatServiceTest`+`Phase3IntegrationTest` 桩签名同步；
  verify quick 全绿（hermetic 全量）、前端 type-check/build 绿、lint 15 基线 0 新增。
- 真实 API 走查（8081 重启后）：回填 10/10 命中；t7check 列表 8 条/admin 2 条
  （visitorId 无关）；owner 乱填 visitorId 读消息正常；admin 持 t7check 真 visitorId
  读已认领会话 → 空（凭证失效实锤）；rename 非属主 A120100/属主成功/空标题 A120101；
  delete 非属主 A120100/属主成功且消息同事务清零；匿名 PUT/DELETE → 401；
  未认领会话 visitorId 兜底可见（共享浏览器歧义如期）。

**遗留**

- visitorId 凭证模型对「未认领存量 + 共享浏览器」的固有歧义：认领前多账号可见，
  续聊收敛——个人博客规模可接受，如需彻底可后续加「认领确认」交互。
- 会话标题目前=首问截断+手动 rename；无自动摘要。

**UI 走查补记（2026-11-16）**：新增 `mysite-frontend/scripts/ragsec-walkthrough.mjs`
（Playwright + route mock，wire=Result 包装），33/33 过——历史 Result 解包+visitorId
透传、hover 双钮、rename 全流程（预填/PUT 带 visitorId/列表重拉/toast）、Esc 取消、
空标题守卫、delete 确认弹窗+URL 断言+列表移除、删当前会话回空态、A120100 error
toast、非属主 messages 空不报错、匿名登录门不拉 conversations、移动端抽屉。
**走查发现并修**：行内编辑 Esc 冒泡触发面板级 `handleEsc`（分级退出先于恢复的重命名
UI 落地，未含编辑层），Esc 取消编辑同时误关整个历史抽屉 → `handleKeydown` 补
`stopPropagation`，Esc 只剥编辑层。另确认抽屉为面板级覆盖层时 header 历史钮被盖住，
关闭走抽屉自身 X/遮罩（设计行为非缺陷）。
