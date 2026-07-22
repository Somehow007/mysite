// composables/useChat.ts
// 聊天状态机 —— 管理消息列表、流式状态、会话 ID、重试与取消。
// 采用"消息列表内原地更新"模型：流式 token 直接追加到数组里的 AI 占位消息，
// 消除"流式消息"与"历史消息"两套渲染路径。
//
// ⚠️  Vue 3 响应式关键约束：
//    reactive() 返回的是包裹原始对象的 Proxy，原始对象本身不会被修改。
//    所有回调中对消息属性的修改，必须通过 messages.value[index] 获取
//    响应式代理后再操作，否则绕过 Proxy 的 set trap，视图不会更新。

import { ref, computed } from 'vue'
import { createChatStream, ChatStreamError, type ChatStreamErrorKind } from '@/api/rag'
import type { ChatMessage, SourceChunk } from '@/types'

export type ChatStatus = 'idle' | 'streaming' | 'error'

let nextMessageId = 1

export function useChat() {
  const messages = ref<ChatMessage[]>([])
  const status = ref<ChatStatus>('idle')
  const conversationId = ref<number | null>(null)
  const lastError = ref<{ kind: ChatStreamErrorKind; message: string } | null>(null)
  const lastQuestion = ref<string | null>(null) // 供"重试"使用

  let abort: AbortController | null = null
  const isStreaming = computed(() => status.value === 'streaming')

  /**
   * 获取消息列表中最后一条 AI 消息的响应式代理。
   * 每次调用都通过 messages.value（ref）访问，确保拿到的是 Vue
   * reactive() 创建的 Proxy 对象，而不是原始 JS 对象。
   */
  function lastAssistant(): ChatMessage | null {
    const msgs = messages.value
    const last = msgs[msgs.length - 1]
    return last?.role === 'assistant' ? last : null
  }

  function sendMessage(question: string) {
    const q = question.trim()
    if (!q || isStreaming.value) return // 流式中禁止并发发送
    lastError.value = null
    lastQuestion.value = q

    messages.value.push({
      id: nextMessageId++,
      role: 'user',
      content: q,
      sources: [],
    })

    // 追加 AI 占位消息；后续回调中通过 lastAssistant() 获取响应式代理来修改
    messages.value.push({
      id: nextMessageId++,
      role: 'assistant',
      content: '',
      sources: [],
      pending: true,
    })

    status.value = 'streaming'
    abort = createChatStream(q, conversationId.value, {
      onMeta: (id) => { conversationId.value = id },

      onSources: (sources: SourceChunk[]) => {
        const msg = lastAssistant()
        if (msg) msg.sources = sources
      },

      onToken: (delta) => {
        const msg = lastAssistant()
        if (msg) msg.content += delta // ✅ 通过响应式代理修改，视图实时更新
      },

      onDone: () => {
        const msg = lastAssistant()
        if (msg) msg.pending = false
        status.value = 'idle'
        abort = null
      },

      onError: (err: ChatStreamError) => {
        const msg = lastAssistant()
        if (!msg) return
        msg.pending = false
        if (msg.content) {
          // 已有部分输出：保留内容，标记截断，不算硬失败
          msg.truncated = true
          status.value = 'idle'
        } else {
          // 完全没有输出：本条标记失败，允许重试
          msg.failed = true
          msg.content = err.message
          lastError.value = { kind: err.kind, message: err.message }
          status.value = 'error'
        }
        abort = null
      },
    })
  }

  /** 重试：移除失败的那条 AI 消息，用 lastQuestion 重发 */
  function retry() {
    const last = messages.value[messages.value.length - 1]
    if (last?.role === 'assistant' && last.failed) messages.value.pop()
    if (lastQuestion.value) sendMessage(lastQuestion.value)
  }

  /** 取消当前生成 */
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

  /** 开启新会话 */
  function newConversation() {
    cancelGeneration()
    messages.value = []
    conversationId.value = null
    lastError.value = null
    lastQuestion.value = null
  }

  return {
    messages,
    status,
    isStreaming,
    conversationId,
    lastError,
    sendMessage,
    retry,
    cancelGeneration,
    newConversation,
  }
}
