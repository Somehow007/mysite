// composables/useChat.ts
// 聊天状态机 —— 管理消息列表、流式状态、会话 ID、重试与取消。
// 采用"消息列表内原地更新"模型：流式 token 直接追加到数组里的 AI 占位消息，
// 消除"流式消息"与"历史消息"两套渲染路径。

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

    // 原地追加 AI 占位消息，流式 token 直接写进这条消息 content
    const assistantMsg: ChatMessage = {
      id: nextMessageId++,
      role: 'assistant',
      content: '',
      sources: [],
      pending: true,
    }
    messages.value.push(assistantMsg)

    status.value = 'streaming'
    abort = createChatStream(q, conversationId.value, {
      onMeta: (id) => { conversationId.value = id },
      onSources: (sources: SourceChunk[]) => { assistantMsg.sources = sources },
      onToken: (delta) => { assistantMsg.content += delta },
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
