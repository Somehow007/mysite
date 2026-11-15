<script setup lang="ts">
import { Bot, RefreshCw } from 'lucide-vue-next'
import type { ChatMessage } from '@/types'
import ChatStreamWriter from './ChatStreamWriter.vue'
import ChatSources from './ChatSources.vue'

defineProps<{
  message: ChatMessage
}>()

const emit = defineEmits<{
  retry: []
}>()
</script>

<template>
  <div
    class="flex mb-4 gap-2.5"
    :class="message.role === 'user' ? 'justify-end' : 'justify-start'"
  >
    <!-- AI 头像 -->
    <div
      v-if="message.role === 'assistant'"
      class="chat-avatar w-[30px] h-[30px] rounded-[9px] bg-accent-subtle text-accent flex items-center justify-center shrink-0 mt-0.5"
    >
      <Bot :size="17" />
    </div>

    <!-- AI 回答：无卡片底，衬线正文裸排（chat.css）；失败态保留 danger 提示框 -->
    <div
      v-if="message.role === 'assistant'"
      class="max-w-[82%] chat-bubble-ai"
      :class="message.failed
        ? 'chat-bubble-ai--failed bg-danger-subtle border border-danger/35 rounded-[4px_14px_14px_14px] px-3.5 py-2.5'
        : ''"
    >
      <!-- 失败态 -->
      <template v-if="message.failed">
        <div class="flex items-start gap-2 text-danger text-sm">
          <RefreshCw :size="14" class="mt-0.5 shrink-0" />
          <div>
            <p class="mb-1.5">{{ message.content }}</p>
            <button
              class="px-3 py-1 rounded-lg border border-danger/40 text-danger text-xs hover:bg-danger/10 transition-colors font-medium"
              @click="emit('retry')"
            >
              重新生成
            </button>
          </div>
        </div>
      </template>

      <!-- 流式/正常渲染 -->
      <template v-else>
        <ChatStreamWriter
          :content="message.content"
          :streaming="!!message.pending"
        />
        <!-- 截断标记 -->
        <span
          v-if="message.truncated && !message.pending"
          class="text-xs text-text-muted mt-1 inline-block"
        >已停止生成</span>
      </template>

      <!-- 来源（非流式才展示） -->
      <ChatSources
        v-if="!message.pending && !message.failed && message.sources.length > 0"
        :sources="message.sources"
      />
    </div>

    <!-- 用户消息：bg-secondary 墨块（chat-bubble-user，chat.css 定色） -->
    <div
      v-if="message.role === 'user'"
      class="max-w-[82%] chat-bubble-user rounded-[14px_14px_4px_14px] px-3.5 py-2.5 text-sm leading-relaxed"
    >
      {{ message.content }}
    </div>
  </div>
</template>
