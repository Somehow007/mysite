<script setup lang="ts">
import { useChatMarkdown } from '@/composables/useChatMarkdown'
import { ref, toRef } from 'vue'

const props = defineProps<{
  content: string
  streaming: boolean
}>()

const contentRef = toRef(props, 'content')
const streamingRef = toRef(props, 'streaming')
const { renderedHtml } = useChatMarkdown(contentRef, streamingRef)
</script>

<template>
  <div class="chat-markdown-body">
    <!-- 渲染完成的 HTML -->
    <div v-if="renderedHtml" class="markdown-body" v-html="renderedHtml" />
    <!-- 流式加载中且暂无内容时：三点跳动 -->
    <div v-else-if="streaming" class="flex items-center gap-1.5 py-2">
      <span class="w-2 h-2 rounded-full bg-accent animate-pulse" />
      <span class="w-2 h-2 rounded-full bg-accent animate-pulse" style="animation-delay: 0.15s" />
      <span class="w-2 h-2 rounded-full bg-accent animate-pulse" style="animation-delay: 0.3s" />
    </div>
    <!-- 流式光标 -->
    <span
      v-if="streaming && content"
      class="cursor-blink inline-block w-[2px] h-[1em] bg-accent align-text-bottom ml-px"
    />
  </div>
</template>

<style scoped>
@keyframes cursor-blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}

.cursor-blink {
  animation: cursor-blink 1s step-end infinite;
}

/* 聊天场景 Markdown 精简化：
   - 不渲染 TOC（本组件不使用文章页的 TOC 逻辑）
   - 与文章正文共享 .markdown-body 排版与 --code-token-* 代码高亮变量
   - 代码块自适应字号、更好的行间距 */
.chat-markdown-body :deep(.markdown-body) {
  font-size: 0.875rem;
  line-height: 1.65;
  word-break: break-word;
}

.chat-markdown-body :deep(.markdown-body pre) {
  font-size: 0.8rem;
  border-radius: 0.5rem;
  margin: 0.5rem 0;
}

.chat-markdown-body :deep(.markdown-body p) {
  margin: 0.35rem 0;
}

.chat-markdown-body :deep(.markdown-body p:first-child) {
  margin-top: 0;
}

.chat-markdown-body :deep(.markdown-body p:last-child) {
  margin-bottom: 0;
}

.chat-markdown-body :deep(.markdown-body ul),
.chat-markdown-body :deep(.markdown-body ol) {
  padding-left: 1.5rem;
  margin: 0.35rem 0;
}

.chat-markdown-body :deep(.markdown-body h1),
.chat-markdown-body :deep(.markdown-body h2),
.chat-markdown-body :deep(.markdown-body h3) {
  font-size: 1rem;
  margin: 0.75rem 0 0.35rem;
}
</style>
