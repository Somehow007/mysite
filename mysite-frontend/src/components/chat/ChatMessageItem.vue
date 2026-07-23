<script setup lang="ts">
import { RefreshCw } from 'lucide-vue-next'
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
    class="flex mb-4"
    :class="message.role === 'user' ? 'justify-end' : 'justify-start'"
  >
    <!-- 用户气泡 -->
    <div
      v-if="message.role === 'user'"
      class="max-w-[85%] bg-accent text-white rounded-2xl rounded-br-sm px-4 py-2.5 text-sm leading-relaxed"
    >
      {{ message.content }}
    </div>

    <!-- AI 气泡 -->
    <div
      v-else
      class="max-w-[85%] bg-bg-secondary text-text-primary rounded-2xl rounded-bl-sm px-4 py-2.5"
      :class="message.failed ? 'border border-red-500/30' : ''"
    >
      <!-- 失败态 -->
      <template v-if="message.failed">
        <div class="flex items-start gap-2 text-red-500 text-sm">
          <RefreshCw :size="14" class="mt-0.5 shrink-0" />
          <div>
            <p class="mb-1.5">{{ message.content }}</p>
            <button
              class="text-accent hover:underline text-xs"
              @click="emit('retry')"
            >
              点击重试
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
  </div>
</template>
