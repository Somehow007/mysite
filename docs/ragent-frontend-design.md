# MySite RAG 前端设计配套方案

> 配套文档：[ragent-integration-plan.md](./ragent-integration-plan.md)（后端集成方案，本文对应其 Phase 4 / Phase 5 的前端部分）+
> [ragent-integration-design.md](./ragent-integration-design.md)。
>
> 编写日期：2026-07-21
> 定位：后端方案的"前端落地篇"——给出与当前 Vue 3 站点**无缝融合**的 UI 设计、
> 健壮的 SSE 通信层与状态管理设计，并对后端方案 Phase 4 中的示例代码做了
> 若干健壮性修正（见 §7 "与后端方案 Phase 4 的差异说明"）。

---

## 目录

1. [设计目标与原则](#一设计目标与原则)
2. [后端接口契约（前端视角）](#二后端接口契约前端视角)
3. [UI/UX 设计](#三uiux-设计)
4. [技术架构与文件组织](#四技术架构与文件组织)
5. [核心模块设计](#五核心模块设计)
6. [健壮性设计（重点）](#六健壮性设计重点)
7. [与后端方案 Phase 4 的差异说明](#七与后端方案-phase-4-的差异说明)
8. [知识库管理页（Phase 5 前端）](#八知识库管理页phase-5-前端)
9. [实施步骤与验收清单](#九实施步骤与验收清单)

---

## 一、设计目标与原则

### 1.1 目标

| 目标 | 具体要求 |
|------|---------|
| **UI 美观** | 浮窗全面复用站点现有设计体系（glass 毛玻璃、主题 token、过渡动画），在全部 8 套主题（含暗色变体）下观感一致、无违和感 |
| **完美集成** | 零侵入现有页面：仅在 `DefaultLayout` 挂一个组件、router/sidebar 各加一条记录；不改任何现有组件逻辑 |
| **代码健壮** | 聊天状态用显式状态机管理；组件遵循项目既有"容器持状态、子组件纯展示 + emit"模式（参照 `components/comment/`） |
| **API 调用健壮** | SSE 解析容错（半包/CRLF/心跳）、错误分类处理（网络/限流/服务端）、可取消、可重试、有超时 |

### 1.2 原则

1. **复用优先**：能用现有的就不用新的 —— Markdown 渲染复用 `useMarkdown` 的依赖（marked/katex/prismjs，均已按需分包）、样式复用 `.glass` `.btn-primary` `.scrollbar-thin` 等已有工具类、图标继续用 lucide-vue-next、反馈继续用 `useToast`。
2. **按需加载**：聊天功能对首屏是非关键路径，整个 `chat/` 模块异步加载，不拖慢博客首屏。
3. **不引入新范式**：项目目前没有任何 SSE/fetch 流式代码（全 axios），本方案引入的 `fetch + ReadableStream` 封装在单一文件 `api/rag.ts` 内收口，不扩散。
4. **安全默认**：LLM 输出一律 `v-html` 前过 DOMPurify；匿名身份用 localStorage UUID（对应后端 visitorId 决策）。

---

## 二、后端接口契约（前端视角）

> 摘自后端方案 §3.5 / Phase 3 / Phase 5，前端以此为准，**不允许前端假设契约之外的字段**。

### 2.1 端点一览

| 端点 | 方法 | 鉴权 | 用途 |
|------|------|------|------|
| `/v1/rag/chat/stream?q=&conversationId=&visitorId=` | GET (SSE) | permitAll | 流式问答 |
| `/v1/rag/knowledge-bases` | GET | permitAll | 知识库列表 |
| `/v1/rag/knowledge-bases` / `/{id}` | POST/PUT/DELETE | DEVELOPER | 知识库 CRUD |
| `/v1/rag/knowledge-bases/{id}/docs` | GET/POST | DEVELOPER | 文档列表 / 上传 |
| `/v1/rag/knowledge-bases/{id}/sync` | POST | DEVELOPER | 批量同步文章 |
| `/v1/rag/knowledge-bases/{id}/docs/{docId}` | DELETE | DEVELOPER | 删除文档 |

### 2.2 SSE 事件协议（核心契约）

响应 `Content-Type: text/event-stream`，每条事件一行 `data: {json}\n\n`，共 5 种：

```text
data: {"type":"meta","conversationId":123}                    ← 首事件：会话 ID 回传
data: {"type":"sources","sources":[{"title":"...","content":"...","score":0.87}]}
data: {"type":"content","delta":"JWT"}                        ← 逐 token，N 条
data: {"type":"done"}                                         ← 正常结束
data: {"type":"error","message":"提问太频繁，请稍后再试"}        ← 业务错误（限流/熔断降级失败等）
```

前端须知：

- `meta` **一定最先到达**，拿到后必须更新本地 `conversationId`，后续轮次带回（多轮记忆的关键）。
- `sources` 在 `content` 之前到达；`content` 的 `delta` 需按到达顺序拼接。
- `error` 事件到达 = 本次问答失败，流随后关闭；**不要再等 `done`**。
- 流静默断开（无 `done`/`error`）按异常处理，见 §6.3。
- HTTP 层错误（非 2xx，如 429 限流、502）在第一个 SSE 事件之前，需读响应体取错误信息。

### 2.3 匿名身份与会话

- `visitorId`：前端生成 UUID 存 localStorage（key 沿用后端方案约定的 `rag-visitor-id`），每次请求必带；后端据此做会话归属校验（防 IDOR）。
- 限流：每 IP 每小时 20 次、问题 ≤ 500 字（后端 `rate-limit` 配置）。前端在输入框做同样的长度限制，超限流时展示后端返回的友好文案。

---

## 三、UI/UX 设计

### 3.1 整体形态：右下角浮窗（ChatWidget）

```
默认态                          展开态
┌─────────────────────┐        ┌─────────────────────┐
│                     │        │              ┌─────┴──────┐
│    博客页面内容       │        │   博客内容    │  AI 助手 ✕ │
│                     │        │              ├────────────┤
│                  ╭──╮│        │              │ 消息列表     │
│                  │✦││ ← FAB   │              │  ▓▓▓▓      │
│                  ╰──╯│        │              │      ░░░░  │
└─────────────────────┘        │              │ [来源] 📄   │
                               │              ├────────────┤
                               │              │ 输入框  ➤  │
                               │              └────────────┘
                               └─────────────────────┘
```

**尺寸与定位**（对齐站点 token，不硬编码颜色）：

- FAB：`fixed bottom-6 right-6 z-40`，56px 圆形，`bg-accent text-white shadow-lg hover:bg-accent-hover`，图标用 lucide `Sparkles`（或 `MessageCircle`），带 `aria-label="打开 AI 助手"`。
  - 注意避让：`BackToTop` 也在右下 —— 将 ChatWidget FAB 置于 BackToTop **上方**（`bottom-6`，BackToTop 如也在右下则将其上移或 ChatWidget 用 `bottom-24`），实施时以两处实际 DOM 为准微调，二者不得重叠。
- 面板：桌面端 `w-[380px] h-[560px] max-h-[70vh]`，`fixed bottom-24 right-6 z-50`；移动端（`<640px`）**全屏抽屉**：`inset-0 w-full h-full rounded-none`。
- 面板材质：`glass glass-lg rounded-2xl border border-glass-border`（复用 `SearchDialog.vue` 已验证的毛玻璃语言），进入动画复用 `animate-scale-in`，收起用 `fade`。

**为什么是浮窗而不是独立页面**：与后端方案 Phase 4 一致——读者在阅读文章时随时提问，上下文不中断；这也是 `ChatWidget` 挂在 `DefaultLayout`（而非某个 view）的原因。

### 3.2 面板内部结构

```
┌──────────────────────────────┐
│ ✦ AI 助手          ⟳  ✕     │ ← header：标题 / 新会话 / 关闭
├──────────────────────────────┤
│ ┌─空状态─────────────────┐   │
│ │ 👋 你好，我是博客 AI 助手 │   │
│ │ [推荐问题1] [推荐问题2]  │   │ ← 仅 messages 为空时显示
│ └───────────────────────┘   │
│  ┌─────────────┐            │
│  │ 用户气泡 →右 │            │ ← bg-accent text-white，右对齐
│  └─────────────┘            │
│  ┌─────────────┐            │
│  │ AI 气泡 →左  │            │ ← bg-bg-secondary，左对齐
│  │ Markdown…   │            │
│  │ ┌来源 1/2/3┐│            │ ← ChatSources 折叠卡片
│  └─────────────┘            │
│  AI 气泡（流式中）▊           │ ← 打字机光标
├──────────────────────────────┤
│ ┌──────────────────┐ ┌────┐ │
│ │ 输入框（500 字限） │ │ ➤  │ │ ← 流式中 ➤ 变为 ■ 停止
│ └──────────────────┘ └────┘ │
│ 由博客内容驱动 · 回答仅供参考   │ ← 底部免责小字
└──────────────────────────────┘
```

组件拆分（沿用 `components/comment/` 的"容器 + 展示子组件"模式）：

| 组件 | 职责 | 类比现有 |
|------|------|---------|
| `ChatWidget.vue` | 容器：开合状态、消息列表、自动滚动、快捷键 | `CommentSection.vue` |
| `ChatMessageItem.vue` | 单条消息气泡（用户/AI 两种形态）+ sources | `CommentItem.vue` |
| `ChatStreamWriter.vue` | 流式中的 AI 消息：增量 Markdown 渲染 + 光标 | —（新） |
| `ChatSources.vue` | 引用来源折叠列表（标题 + 相关度 + 摘要，点击跳文章） | —（新） |
| `ChatInput.vue` | 输入框：字数限制、Enter 发送 / Shift+Enter 换行、发送/停止双态按钮 | 评论输入区 |

### 3.3 视觉细节规范

- **配色**：全部走主题 token 类 —— `bg-bg-primary/secondary`、`text-text-primary/secondary/muted`、`text-accent`、`border-border`、`bg-accent-subtle`。禁止出现 `#hex` 或 `bg-blue-*` 这类硬编码，否则 8 套主题下必翻车。
- **气泡**：用户气泡 `bg-accent text-white rounded-2xl rounded-br-sm`；AI 气泡 `bg-bg-secondary text-text-primary rounded-2xl rounded-bl-sm`；最大宽度 `85%`。
- **打字机光标**：`▊` 字符 + `cursor-blink` keyframes（见后端方案 4.2，直接可用），流结束即移除。
- **AI 消息 Markdown**：复用文章正文的 `.markdown-body` 排版与 `--code-token-*` 代码高亮变量，保证聊天里的代码块与文章页观感统一。聊天场景**不渲染 TOC、不渲染 callout**（见 §5.4）。
- **空状态**：推荐问题 3 条，chip 样式 `bg-accent-subtle text-accent rounded-full px-3 py-1.5 text-sm hover:opacity-80`。
- **滚动**：消息容器 `.scrollbar-thin`；新消息到达自动滚到底，但**用户上翻阅读时不强制滚动**（记录 `isUserScrolledUp`，见 §5.3）。
- **暗色/多主题**：不写任何 `dark:` 特例 —— 站点的多主题机制是 `html[data-theme]` 覆盖 CSS 变量，只要全部使用 token 类即自动适配；验收时逐主题过一遍（§9）。
- **无障碍**：Esc 关闭面板；FAB 与发送按钮带 `aria-label`；消息列表 `role="log" aria-live="polite"`（屏幕阅读器可感知流式更新）。

### 3.4 加载与反馈状态

| 场景 | UI 表现 |
|------|---------|
| 发送后等待首个 token | AI 气泡显示三点跳动加载动画（复用现有 keyframes 风格） |
| 流式中 | 打字机 + 闪烁光标；发送按钮变为"停止"（lucide `Square`） |
| 取消 | 已生成内容固化，尾部追加灰色小字"已停止生成" |
| 失败 | 该条 AI 气泡变为错误态（`text-red-500` 语义色 + 重试按钮），同时 `useToast().error()` 提示 |
| 限流（429 / error 事件） | Toast 提示后端文案（如"提问太频繁"），输入框不禁用 |

---

## 四、技术架构与文件组织

### 4.1 新增/修改清单

```
mysite-frontend/src/
├── api/
│   └── rag.ts                        # ★ 新增：SSE 流式客户端 + 知识库 REST 封装
├── composables/
│   ├── useChat.ts                    # ★ 新增：聊天状态机（发送/流式/取消/重试/历史）
│   └── useChatMarkdown.ts            # ★ 新增：聊天专用轻量 Markdown 渲染（§5.4）
├── components/chat/
│   ├── ChatWidget.vue                # ★ 容器（异步加载）
│   ├── ChatMessageItem.vue
│   ├── ChatStreamWriter.vue
│   ├── ChatSources.vue
│   └── ChatInput.vue
├── components/dashboard/
│   └── knowledge/                    # ★ Phase 5：知识库管理子组件（§8）
├── views/
│   └── KnowledgeManageView.vue       # ★ Phase 5：/dashboard/knowledge 页面
├── types/index.ts                    # ★ 追加：ChatMessage / SourceChunk / KnowledgeBase* 类型
├── app/layouts/DefaultLayout.vue     # ★ 修改：挂 <ChatWidget />（异步）
├── app/router/index.ts               # ★ 修改：+ /dashboard/knowledge 子路由
└── components/dashboard/DashboardSidebar.vue  # ★ 修改：nav 数组 + 知识库项
```

**依赖新增**：`dompurify`（必须，理由见后端方案 4.1.1）。其余全部复用现有依赖。

```bash
cd mysite-frontend && npm install dompurify
```

### 4.2 分层与数据流

```
ChatWidget / ChatInput / ChatMessageItem …（纯 UI，props down / emits up）
        │
        ▼
useChat()  ← 状态机：messages / status / conversationId / error
        │
        ▼
api/rag.ts ← createChatStream()：fetch + ReadableStream SSE 解析
        │            getKnowledgeBases() 等：复用 client.ts 的 get/post/del（走 axios 拦截器）
        ▼
后端 /v1/rag/**（Vite proxy 已覆盖 /v1，无需改配置）
```

要点：

- **SSE 不走 axios**（axios 不支持流式响应体），单独在 `rag.ts` 内用原生 fetch；**知识库 CRUD 走 `api/client.ts`**，自动获得 token 注入与统一错误处理 —— 一个文件两种通道，按协议选。
- `useChat` 采用**工厂式**（每次调用独立状态，同 `useMarkdown`/`useSearch`），状态由 `ChatWidget` 持有并下传，不做全局单例——dashboard 页面不需要共享聊天状态。
- 组件异步加载：`DefaultLayout` 中 `defineAsyncComponent(() => import('@/components/chat/ChatWidget.vue'))`，并在 `vite.config.ts` 的 manualChunks 视构建产物决定是否给 `chat` 单独分包（marked/dompurify 已是独立 chunk，天然复用）。

---

## 五、核心模块设计

### 5.1 `api/rag.ts` —— SSE 客户端（健壮版）

相对后端方案 4.2 的草稿，补强点：CRLF 兼容、半包缓冲、非 2xx 读错误体、空 `data:` 心跳行跳过、AbortError 静默、回调异常隔离。

```typescript
// api/rag.ts
// SSE 不能用 axios（响应体需流式读取），这里用原生 fetch + ReadableStream。
// 本文件是项目中唯一的流式 HTTP 代码，所有容错逻辑收口于此。

import { get, post, put, del } from './client'
import type { SourceChunk, KnowledgeBase, KnowledgeDocument, PaginatedResponse } from '@/types'

export interface ChatStreamCallbacks {
  onMeta: (conversationId: number) => void
  onSources: (sources: SourceChunk[]) => void
  onToken: (delta: string) => void
  onDone: () => void
  onError: (error: ChatStreamError) => void
}

/** 错误分类：UI 据此决定提示文案与是否展示重试 */
export type ChatStreamErrorKind = 'network' | 'http' | 'server' | 'aborted'

export class ChatStreamError extends Error {
  constructor(
    public kind: ChatStreamErrorKind,
    message: string,
    public status?: number, // kind === 'http' 时的状态码（如 429）
  ) {
    super(message)
    this.name = 'ChatStreamError'
  }
}

const VISITOR_ID_KEY = 'rag-visitor-id'

export function getVisitorId(): string {
  let id = localStorage.getItem(VISITOR_ID_KEY)
  if (!id) {
    id = typeof crypto.randomUUID === 'function'
      ? crypto.randomUUID()
      : `${Date.now()}-${Math.random().toString(36).slice(2)}`
    localStorage.setItem(VISITOR_ID_KEY, id)
  }
  return id
}

/** 安全回调：单个回调抛异常不中断 SSE 读取循环 */
function safe<T>(fn: (v: T) => void, v: T) {
  try { fn(v) } catch (e) { console.error('[rag] callback error:', e) }
}

/**
 * 建立聊天 SSE 流。返回 AbortController 用于取消。
 * 契约：/v1/rag/chat/stream，事件类型 meta/sources/content/error/done（见本文 §2.2）。
 */
export function createChatStream(
  question: string,
  conversationId: number | null,
  callbacks: ChatStreamCallbacks,
): AbortController {
  const params = new URLSearchParams({ q: question, visitorId: getVisitorId() })
  if (conversationId != null) params.set('conversationId', String(conversationId))

  const controller = new AbortController()
  const { signal } = controller

  const fail = (err: ChatStreamError) => {
    if (!signal.aborted) safe(callbacks.onError, err)
  }

  fetch(`/v1/rag/chat/stream?${params}`, {
    method: 'GET',
    headers: { Accept: 'text/event-stream' },
    signal,
  })
    .then(async (response) => {
      if (!response.ok) {
        // 限流/网关错误等：尽量读响应体里的 message，兜底用状态文案
        let message = `请求失败（HTTP ${response.status}）`
        try {
          const body = await response.json()
          if (body?.message) message = body.message
        } catch { /* 响应体非 JSON，忽略 */ }
        if (response.status === 429) message = body?.message ?? message // 保留后端限流文案
        throw new ChatStreamError('http', message, response.status)
      }
      if (!response.body) throw new ChatStreamError('network', '浏览器不支持流式响应')

      const reader = response.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''
      let finished = false // 收到 done / error

      while (true) {
        const { done, value } = await reader.read()
        if (done) break
        buffer += decoder.decode(value, { stream: true })

        // SSE 以空行为事件边界；兼容 \n 与 \r\n。只处理完整行，残行留 buffer。
        const lines = buffer.split('\n')
        buffer = lines.pop() ?? ''

        for (const rawLine of lines) {
          const line = rawLine.endsWith('\r') ? rawLine.slice(0, -1) : rawLine
          if (!line.startsWith('data:')) continue // 跳过注释行/心跳行/event: 行
          const payload = line.slice(5).trim()
          if (!payload) continue

          let data: any
          try { data = JSON.parse(payload) } catch { continue } // 半包 JSON：跳过

          switch (data?.type) {
            case 'meta':    safe(callbacks.onMeta, Number(data.conversationId)); break
            case 'sources': safe(callbacks.onSources, (data.sources ?? []) as SourceChunk[]); break
            case 'content': if (typeof data.delta === 'string') safe(callbacks.onToken, data.delta); break
            case 'done':
              finished = true
              safe(callbacks.onDone, undefined)
              return
            case 'error':
              finished = true
              fail(new ChatStreamError('server', data.message || 'AI 服务暂时不可用'))
              return
          }
        }
      }

      // 流被动结束但未收到 done/error：网络中断等。已有部分输出时按"完成但截断"处理，
      // 完全无输出时报错——由 useChat 根据已累积内容区分（见 §5.2）。
      if (!finished) safe(callbacks.onDone, undefined)
    })
    .catch((err) => {
      if (signal.aborted || err?.name === 'AbortError') return // 用户主动取消：静默
      if (err instanceof ChatStreamError) fail(err)
      else fail(new ChatStreamError('network', '网络连接异常，请检查网络后重试'))
    })

  return controller
}

/* ===== 以下走 axios client.ts（自动带 token + 统一错误处理），Phase 5 管理页使用 ===== */

export function getKnowledgeBases(): Promise<KnowledgeBase[]> {
  return get<KnowledgeBase[]>('/v1/rag/knowledge-bases')
}
export function createKnowledgeBase(data: Partial<KnowledgeBase>): Promise<void> {
  return post<void>('/v1/rag/knowledge-bases', data)
}
export function updateKnowledgeBase(id: string, data: Partial<KnowledgeBase>): Promise<void> {
  return put<void>(`/v1/rag/knowledge-bases/${id}`, data)
}
export function deleteKnowledgeBase(id: string): Promise<void> {
  return del<void>(`/v1/rag/knowledge-bases/${id}`)
}
export function getKnowledgeDocuments(kbId: string): Promise<KnowledgeDocument[]> {
  return get<KnowledgeDocument[]>(`/v1/rag/knowledge-bases/${kbId}/docs`)
}
export function syncKnowledgeBase(kbId: string): Promise<void> {
  return post<void>(`/v1/rag/knowledge-bases/${kbId}/sync`)
}
export function deleteKnowledgeDocument(kbId: string, docId: string): Promise<void> {
  return del<void>(`/v1/rag/knowledge-bases/${kbId}/docs/${docId}`)
}
```

### 5.2 `useChat.ts` —— 聊天状态机

后端方案草稿把流式中的消息放在独立的 `currentAssistantMessage` ref，模板里还要 `.value` 手动取值（在 `<script setup>` 中是错误用法）。这里改为**消息列表内原地更新**模型 + 显式状态机：

```typescript
// composables/useChat.ts
import { ref, computed } from 'vue'
import { createChatStream, ChatStreamError, type ChatStreamErrorKind } from '@/api/rag'
import type { ChatMessage, SourceChunk } from '@/types'

export type ChatStatus = 'idle' | 'streaming' | 'error'

export function useChat() {
  const messages = ref<ChatMessage[]>([])
  const status = ref<ChatStatus>('idle')
  const conversationId = ref<number | null>(null)
  const lastError = ref<{ kind: ChatStreamErrorKind; message: string } | null>(null)
  const lastQuestion = ref<string | null>(null) // 供"重试"使用

  let abort: AbortController | null = null
  const isStreaming = computed(() => status.value === 'streaming')

  function sendMessage(question: string) {
    const q = question.trim()
    if (!q || isStreaming.value) return // 流式中禁止并发发送
    lastError.value = null
    lastQuestion.value = q

    messages.value.push({ role: 'user', content: q, sources: [] })
    // 原地追加 AI 占位消息，流式 token 直接写进这条消息 → 模板无需区分两种消息
    const assistantMsg: ChatMessage = { role: 'assistant', content: '', sources: [], pending: true }
    messages.value.push(assistantMsg)

    status.value = 'streaming'
    abort = createChatStream(q, conversationId.value, {
      onMeta: (id) => { conversationId.value = id },
      onSources: (sources: SourceChunk[]) => { assistantMsg.sources = sources },
      onToken: (delta) => { assistantMsg.content += delta }, // 响应式原地追加
      onDone: () => {
        assistantMsg.pending = false
        status.value = 'idle'
        abort = null
      },
      onError: (err: ChatStreamError) => {
        assistantMsg.pending = false
        if (assistantMsg.content) {
          // 已有部分输出：保留内容，标记截断，不算硬失败
          assistantMsg.truncated = true
          status.value = 'idle'
        } else {
          // 完全没有输出：本条标记失败，允许重试
          assistantMsg.failed = true
          assistantMsg.content = err.message
          lastError.value = { kind: err.kind, message: err.message }
          status.value = 'error'
        }
        abort = null
      },
    })
  }

  function retry() {
    // 移除失败的那条 AI 消息，用 lastQuestion 重发
    const last = messages.value[messages.value.length - 1]
    if (last?.role === 'assistant' && last.failed) messages.value.pop()
    if (lastQuestion.value) sendMessage(lastQuestion.value)
  }

  function cancelGeneration() {
    abort?.abort()
    abort = null
    const last = messages.value[messages.value.length - 1]
    if (last?.role === 'assistant' && last.pending) {
      last.pending = false
      last.truncated = true // UI 显示"已停止生成"
    }
    status.value = 'idle'
  }

  function newConversation() {
    cancelGeneration()
    messages.value = []
    conversationId.value = null
    lastError.value = null
    lastQuestion.value = null
  }

  return { messages, status, isStreaming, conversationId, lastError,
           sendMessage, retry, cancelGeneration, newConversation }
}
```

设计要点：

- **消息对象就地可变**：`assistantMsg` 是 `messages` 数组里的引用，token 追加直接改它，Vue 响应式自动驱动视图 —— 消除草稿中"流式消息"与"历史消息"两套渲染路径。
- `pending / failed / truncated` 三个标志位覆盖全部终态，UI 映射简单（§3.4）。
- **流式中禁止并发发送**（`isStreaming` 守卫），避免两个流抢一个 `conversationId`。
- 性能：token 到达频率高（几十/秒），每条都触发一次渲染。`ChatStreamWriter` 的 Markdown 渲染做 **rAF 节流**（§5.4），DOM 文本追加本身开销可忽略。

### 5.3 `ChatWidget.vue` 容器职责

- 开合状态 `isOpen` + Esc 关闭 + 打开时聚焦输入框。
- 持有 `useChat()` 返回值，向下分发 props / 接收 emits（`@send`、`@cancel`、`@retry`）。
- **智能滚动**：`watch(messages, …)` 中先读容器 `scrollHeight - scrollTop - clientHeight < 80` 判断用户是否贴底，贴底才 `scrollTop = scrollHeight`；用户上翻时不打扰。
- 消息列表 `v-for` 用稳定 key：消息对象加内部自增 `id` 字段（`key="msg.id"`），不用数组下标（重试 pop 会导致下标复用）。

### 5.4 `useChatMarkdown.ts` —— 聊天专用渲染

**为什么不直接复用 `useMarkdown`**：现有 `useMarkdown` 面向文章页 —— async render、TOC 提取、callout、KaTeX 占位符保护，对"每秒重渲染几十次、每次几十到几百字"的聊天流式场景过重。方案：

- **流式中**：`marked.parse(content, { breaks: true })` 同步渲染 + DOMPurify + rAF 节流（`requestAnimationFrame` 合并一帧内多次 token 更新，只渲染一次）。
- **渲染完成后**：对 AI 消息中的代码块用 Prism 高亮（语言包按需 `import()`，命中未加载语言则先按纯文本渲染、加载完成后重渲染一次），数学公式走 `katex.renderToString` —— 均只在 `pending=false` 时执行一次，不与流式渲染竞争。
- 安全：marked 输出 **始终** 过 `DOMPurify.sanitize`，无例外路径。

```typescript
// composables/useChatMarkdown.ts（核心逻辑示意）
import { computed, type Ref } from 'vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'

export function useChatMarkdown(source: Ref<string>, streaming: Ref<boolean>) {
  let rafId = 0
  const renderedHtml = ref('')

  watch(source, () => {
    if (rafId) return // 本帧已排队，合并
    rafId = requestAnimationFrame(() => {
      rafId = 0
      const raw = marked.parse(source.value, { breaks: true }) as string
      renderedHtml.value = DOMPurify.sanitize(raw)
    })
  }, { flush: 'sync' })

  onScopeDispose(() => { if (rafId) cancelAnimationFrame(rafId) })
  return { renderedHtml }
}
```

### 5.5 类型定义（追加到 `types/index.ts`）

```typescript
/* ===== RAG / AI 助手 ===== */
export interface SourceChunk {
  title: string
  content: string
  score: number
  articleId?: string | null // 若后端 sources 带文章 ID，点击可跳文章页
}

export interface ChatMessage {
  id?: number                // 前端自增，仅作 v-for key
  role: 'user' | 'assistant'
  content: string
  sources: SourceChunk[]
  pending?: boolean          // 流式中
  failed?: boolean           // 无输出即失败，可重试
  truncated?: boolean        // 被取消/中断，保留部分内容
}

export interface KnowledgeBase {
  id: string
  name: string
  description: string | null
  collectionName: string
  embeddingModel: string
  chunkSize: number
  docCount?: number
  createTime: string
}

export interface KnowledgeDocument {
  id: string
  kbId: string
  title: string
  sourceType: 'ARTICLE' | 'UPLOAD'
  status: 'PENDING' | 'CHUNKING' | 'READY' | 'FAILED'
  failReason: string | null
  chunkCount: number
  createTime: string
}
```

---

## 六、健壮性设计（重点）

### 6.1 错误分类与处理矩阵

| 错误 | 检测点 | 分类 | 前端行为 |
|------|--------|------|---------|
| 用户取消 | AbortController | `aborted` | 静默；保留部分内容标记"已停止" |
| 断网/连接中断 | fetch reject / 流提前结束 | `network` | 无输出 → 错误气泡 + 重试；有输出 → 保留 + 截断标记 |
| 限流 429 | HTTP 状态 | `http` | Toast 展示后端文案；不重试（重试也无用） |
| 其他 4xx/5xx | HTTP 状态 | `http` | 错误气泡 + 重试按钮 |
| 后端 `error` 事件（熔断全开、LLM 全挂） | SSE 事件 | `server` | 错误气泡 + 重试按钮 |
| 回调/JSON 解析异常 | 解析层 | — | 跳过该行，不中断流（记 console） |
| Markdown 渲染异常 | 渲染层 | — | try/catch 兜底渲染为纯文本 `<pre>` |

### 6.2 并发与生命周期守卫

- 流式中禁用发送按钮（输入仍可编辑）；`sendMessage` 内置 `isStreaming` 守卫双保险。
- 组件卸载（`onScopeDispose`）时 `abort?.abort()`，防止页面切换后回调写入已销毁组件。
- 开启新会话先 `cancelGeneration()`。
- 同一面板内消息数组操作全部走 `messages.value` 的响应式 API，不持有数组快照。

### 6.3 边界与防御

- **空 delta / 空 sources**：解析层判空跳过。
- **`conversationId` 到达前连发**：流式中禁发已规避；`meta` 未到达就断流则 `conversationId` 保持 null，下一条消息后端会视为新会话——可接受，无需补偿。
- **localStorage 不可用**（隐私模式）：`getVisitorId` 加 try/catch，退化为内存 UUID（仅本次会话有效，不影响功能）。
- **超长问题**：输入框 `maxlength=500` + 字数计数，与后端 `max-question-length` 对齐，前端先行拦截。
- **XSS**：唯一 `v-html` 出口是 `useChatMarkdown`，DOMPurify 强制消毒；sources 的 `title/content` 一律文本插值（`{{ }}`），绝不 `v-html`。
- **构建期保障**：`vue-tsc --noEmit` + `oxlint` 必须通过（纳入验收）。

### 6.4 用户体验兜底

- 面板打开但从未提问 → 空状态 + 推荐问题。
- 会话消息**不持久化到 localStorage**：刷新即新会话。理由：后端已按 `conversationId` 存 PG，匿名场景下前端本地缓存消息会与后端记忆不一致（后端有历史、前端没有），反而造成"AI 记得、界面空白"的割裂。刷新后开新会话是语义最清晰的行为。
- 错误气泡的重试只重放 `lastQuestion`，不污染会话历史。

---

## 七、与后端方案 Phase 4 的差异说明

实施时以后端方案的 API 契约为准、以**本文的代码结构**为准。主要修正：

| # | 后端方案 Phase 4 草稿 | 本文方案 | 原因 |
|---|----------------------|---------|------|
| 1 | `ChatWidget` 模板中 `chat.messages.value` | setup 返回的 ref 在模板自动解包，直接 `chat.messages` | 草稿写法在 `<script setup>` 下是运行错误 |
| 2 | 流式消息独立于 `messages`（`currentAssistantMessage`） | 消息列表内原地更新的占位消息（`pending` 标志） | 两套渲染路径 → 闪烁与滚动 bug；单一路径更简单 |
| 3 | SSE 解析仅按 `\n` 切分 | 兼容 `\r\n`、跳过非 `data:` 行、空 payload | Nginx/代理可能改写行尾；心跳注释行会触发 JSON 解析噪音 |
| 4 | 非 2xx 只报 `HTTP 500` | 读响应体取后端 `message`，429 单独处理 | 限流场景必须给用户后端配置的友好文案 |
| 5 | 错误一律 push 一条"抱歉出错了"消息 | 错误分类（network/http/server/aborted）+ 重试机制 | "出错了"消息无法恢复，且用户取消时不该出现 |
| 6 | 取消直接拼接 `' [已取消]'` 进内容 | `truncated` 标志位，UI 层渲染灰色小字 | 内容与展示状态分离；i18n/样式调整不用改数据 |
| 7 | 每 token 全量 `marked.parse` | rAF 节流 + 完成后才做 Prism/KaTeX | 流式期间高频全量渲染是主要性能风险 |
| 8 | 消息 `v-for :key="idx"` | 消息自带自增 `id` | 重试时 pop 消息导致下标 key 复用、DOM 错乱 |
| 9 | `marked` 直接渲染进 `v-html` | DOMPurify 强制消毒（草稿已提，本文固化为唯一出口） | 防 prompt injection 回显脚本 |

---

## 八、知识库管理页（Phase 5 前端）

### 8.1 路由与导航

```typescript
// app/router/index.ts —— dashboard children 中追加
{
  path: 'knowledge',
  name: 'knowledge-manage',
  component: () => import('@/views/KnowledgeManageView.vue'),
  meta: { requiresDeveloper: true },   // 与其他管理页一致
}
```

```vue
<!-- DashboardSidebar.vue —— allNavItems 数组追加一项，无需改其他逻辑 -->
{ label: '知识库', path: '/dashboard/knowledge', icon: Database, requireDeveloper: true }
```

### 8.2 页面结构（KnowledgeManageView.vue）

沿用 `CategoryManageView.vue` / `TagManageView.vue` 的页面骨架（页头 + 新建按钮 + 卡片/表格列表 + 操作列）：

```
┌ 知识库管理 ───────────── [同步文章] [新建知识库] ┐
│ ┌────────────┐ ┌────────────┐                  │
│ │ 博客文章库   │ │ 笔记库      │  ← 知识库卡片      │
│ │ 32 篇文档   │ │ 5 篇文档    │     名称/描述/      │
│ │ [管理][删除]│ │ [管理][删除]│     文档数/操作     │
│ └────────────┘ └────────────┘                  │
└────────────────────────────────────────────────┘
        │ 点击"管理" → 展开/跳转文档列表
┌ 文档列表（所选知识库）───────────────────────────┐
│ 标题          来源    状态     分块数   操作      │
│ Spring实战    ARTICLE ●READY   12    [删除]     │
│ 上传笔记.pdf  UPLOAD  ●FAILED  0     [查看原因]  │
└────────────────────────────────────────────────┘
```

- **状态徽章**：`READY` 绿 / `PENDING|CHUNKING` 黄（可带转动 icon）/ `FAILED` 红，FAILED 悬浮或点击展示 `failReason`（对应后端表的 `fail_reason` 字段，摄取失败排查就靠它）。
- **同步文章**按钮 → `syncKnowledgeBase()`，Toast 反馈；同步期间按钮 loading 防重复点击。
- **删除**二次确认（沿用项目现有 `confirm()` 惯例）。
- 文档上传（`POST .../docs`）走 axios `post`（client.ts 已处理 FormData 的 Content-Type）。
- 全部请求经 `api/client.ts` → 自动带 JWT、401 跳登录、`requiresDeveloper` 路由守卫兜底。

---

## 九、实施步骤与验收清单

### 9.1 实施顺序（建议 4 个 commit）

1. **地基**：`npm i dompurify`；`types/index.ts` 追加类型；`api/rag.ts`（含 SSE 客户端）；`useChat.ts`。
2. **聊天 UI**：`useChatMarkdown.ts` + `components/chat/` 五组件；`DefaultLayout` 挂载（异步）。此时可联调完整问答链路。
3. **打磨**：智能滚动、Esc/快捷键、主题走查、错误态与重试、移动端全屏适配。
4. **管理页**（Phase 5）：`KnowledgeManageView.vue` + router + sidebar。

### 9.2 验收清单

功能：

- [ ] 右下角 FAB 与 BackToTop 不重叠，点击展开/收起动画流畅
- [ ] 提问后出现打字机效果，`meta` 事件后 `conversationId` 被后续请求带回
- [ ] 多轮对话可回答指代性问题（记忆生效）；localStorage 有 `rag-visitor-id`
- [ ] sources 正确展示（标题 + 相关度），可折叠
- [ ] 流式中可随时停止，保留部分内容并标记"已停止生成"
- [ ] 断网/后端 500/限流 429 分别有正确 UI 反馈，失败消息可重试
- [ ] 用户上翻消息列表时不被强制滚动到底部

安全与健壮：

- [ ] 诱导 LLM 输出 `<script>alert(1)</script>` / `<img onerror=...>`，确认被 DOMPurify 清除
- [ ] 流式中无法重复发送；切换页面后无控制台报错（abort 生效）
- [ ] 超长问题（>500 字）前端拦截

UI 集成：

- [ ] 8 套主题（classic/aurora/rose-garden/ocean-breeze/warm-sunset/liquid-glass × 明暗）逐一切换走查，无硬编码颜色穿帮
- [ ] 聊天内代码块高亮与文章页一致；移动端（<640px）面板全屏可用
- [ ] 无任何对现有组件的侵入式修改（`git diff` 只含 §4.1 列出的文件）

工程：

- [ ] `npx vue-tsc --noEmit` 通过
- [ ] `npm run lint` 通过
- [ ] `npm run build` 通过，聊天代码在独立 chunk、首屏 bundle 无明显增大
- [ ] 知识库管理页 CRUD + 同步 + 失败原因展示可用（Phase 5）
