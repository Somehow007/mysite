// api/rag.ts
// SSE 不能用 axios（响应体需流式读取），这里用原生 fetch + ReadableStream。
// 本文件是项目中唯一的流式 HTTP 代码，所有容错逻辑收口于此。

import type { SourceChunk } from '@/types'

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

/** 获取或生成访客标识，用于匿名会话归属。隐私模式下退化为内存 UUID */
export function getVisitorId(): string {
  try {
    let id = localStorage.getItem(VISITOR_ID_KEY)
    if (!id) {
      id = typeof crypto.randomUUID === 'function'
        ? crypto.randomUUID()
        : `${Date.now()}-${Math.random().toString(36).slice(2)}`
      localStorage.setItem(VISITOR_ID_KEY, id)
    }
    return id
  } catch {
    // localStorage 不可用（浏览器隐私模式等），退化为内存 ID
    return typeof crypto.randomUUID === 'function'
      ? crypto.randomUUID()
      : `${Date.now()}-${Math.random().toString(36).slice(2)}`
  }
}

/** 安全回调：单个回调抛异常不中断 SSE 读取循环 */
function safe<T>(fn: (v: T) => void, v: T) {
  try { fn(v) } catch (e) { console.error('[rag] callback error:', e) }
}

/**
 * 建立聊天 SSE 流。返回 AbortController 用于取消。
 *
 * 契约：GET /v1/rag/chat/stream
 * 事件类型：meta → sources → content(×N) → done / error
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

  // 从 localStorage 读取 JWT token（与 axios client.ts 使用相同 key）
  const token = (() => {
    try {
      const raw = localStorage.getItem('mysite_access_token')
      if (!raw) return null
      // storage.ts 的 getItem 会 JSON.parse，这里原生读 localStorage 需要手动处理
      const parsed = JSON.parse(raw)
      return typeof parsed === 'string' ? parsed : null
    } catch {
      const raw = localStorage.getItem('mysite_access_token')
      return typeof raw === 'string' ? raw : null
    }
  })()

  const headers: Record<string, string> = { Accept: 'text/event-stream' }
  if (token) headers['Authorization'] = `Bearer ${token}`

  const fail = (err: ChatStreamError) => {
    if (!signal.aborted) safe(callbacks.onError, err)
  }

  fetch(`/v1/rag/chat/stream?${params}`, {
    method: 'GET',
    headers,
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
          if (!payload) continue // 跳过空 data: 行

          let data: Record<string, unknown> | undefined
          try {
            const parsed = JSON.parse(payload)
            // 兜底：后端可能错误地把 JSON 字符串再用 Jackson 序列化了一次
            // （表现为 parsed 是 string 而非 object），此时再解析一次
            data = (typeof parsed === 'string' ? JSON.parse(parsed) : parsed) as Record<string, unknown>
          } catch { continue } // 半包 JSON：跳过

          switch (data?.type) {
            case 'meta': {
              const cid = data.conversationId
              if (typeof cid === 'number') safe(callbacks.onMeta, cid)
              break
            }
            case 'sources':
              safe(callbacks.onSources, (data.sources ?? []) as SourceChunk[])
              break
            case 'content':
              if (typeof data.delta === 'string') safe(callbacks.onToken, data.delta)
              break
            case 'done':
              finished = true
              safe(callbacks.onDone, undefined as unknown as void)
              return
            case 'error':
              finished = true
              fail(new ChatStreamError('server', (data.message as string) || 'AI 服务暂时不可用'))
              return
            default:
              // 未知事件类型：记录日志但不中断流
              if (import.meta.env.DEV) {
                console.debug('[rag] unknown SSE event type:', data?.type, data)
              }
          }
        }
      }

      // 流被动结束但未收到 done/error：网络中断等
      // 按"完成但可能截断"处理，由 useChat 根据已累积内容区分
      if (!finished) safe(callbacks.onDone, undefined as unknown as void)
    })
    .catch((err: unknown) => {
      if (signal.aborted || (err instanceof DOMException && err.name === 'AbortError')) return // 用户主动取消：静默
      if (err instanceof ChatStreamError) fail(err)
      else fail(new ChatStreamError('network', '网络连接异常，请检查网络后重试'))
    })

  return controller
}
