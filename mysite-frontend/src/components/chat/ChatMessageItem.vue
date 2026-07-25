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
    <!-- AI 机器人头像 -->
    <div
      v-if="message.role === 'assistant'"
      class="w-[30px] h-[30px] rounded-[9px] bg-accent-subtle text-accent flex items-center justify-center shrink-0 mt-0.5"
    >
      <Bot :size="17" />
    </div>

    <!-- AI 气泡 -->
    <div
      v-if="message.role === 'assistant'"
      class="max-w-[82%]"
      :class="message.failed
        ? 'bg-danger-subtle border border-danger/35 rounded-[4px_14px_14px_14px] px-3.5 py-2.5'
        : 'bg-bg-elevated border border-border rounded-[4px_14px_14px_14px] px-3.5 py-2.5 shadow-[0_1px_2px_rgba(17,24,39,0.05)]'"
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

    <!-- 用户气泡 -->
    <div
      v-if="message.role === 'user'"
      class="max-w-[82%] bg-accent text-white rounded-[14px_14px_4px_14px] px-3.5 py-2.5 text-sm leading-relaxed shadow-[0_4px_12px_-4px_rgba(79,70,229,0.45)]"
    >
      {{ message.content }}
    </div>
  </div>
</template>
