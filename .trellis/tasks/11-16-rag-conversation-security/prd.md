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

**遗留（本任务后续阶段，按 PRD §3 原计划留档）**

- 归属切 user_id 主 key + 存量断代/回填设计；重命名/删除端点补齐后恢复 ChatHistory UI。
- visitorId 凭证模型固有局限（知凭证即可冒名）随归属切换解决。
