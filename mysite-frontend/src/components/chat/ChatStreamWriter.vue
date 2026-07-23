<script setup lang="ts">
import { useChatMarkdown } from '@/composables/useChatMarkdown'
import { toRef } from 'vue'

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

    <!-- 流式中、暂无渲染内容时的加载动画（包含空字符串与仅空白字符场景） -->
    <div v-else-if="streaming" class="flex items-center gap-1.5 py-2">
      <span class="w-2 h-2 rounded-full bg-accent animate-bounce" />
      <span class="w-2 h-2 rounded-full bg-accent animate-bounce" style="animation-delay: 0.15s" />
      <span class="w-2 h-2 rounded-full bg-accent animate-bounce" style="animation-delay: 0.3s" />
    </div>

    <!-- 非流式、无内容：占位（防御性处理，正常不应进入） -->
    <div v-else-if="!content" class="text-xs text-text-muted italic">
      空响应
    </div>

    <!-- 流式光标：只在流式中 && 有内容时显示 -->
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

/* ── 聊天场景 Markdown 排版 ────────────────────────────────────
   与文章正文共享 .markdown-body 排版与 --code-token-* 代码高亮变量。
   Prism / KaTeX / Callout 的全局 CSS 由文章样式表提供，这里只做聊天
   场景的微调（字号、间距、表格自适应等）。 */

.chat-markdown-body :deep(.markdown-body) {
  font-size: 0.875rem;
  line-height: 1.65;
  word-break: break-word;
}

/* 段落 */
.chat-markdown-body :deep(.markdown-body p) {
  margin: 0.35rem 0;
}
.chat-markdown-body :deep(.markdown-body p:first-child) {
  margin-top: 0;
}
.chat-markdown-body :deep(.markdown-body p:last-child) {
  margin-bottom: 0;
}

/* 标题 */
.chat-markdown-body :deep(.markdown-body h1),
.chat-markdown-body :deep(.markdown-body h2),
.chat-markdown-body :deep(.markdown-body h3),
.chat-markdown-body :deep(.markdown-body h4) {
  font-size: 1rem;
  margin: 0.75rem 0 0.35rem;
}

/* 列表 */
.chat-markdown-body :deep(.markdown-body ul),
.chat-markdown-body :deep(.markdown-body ol) {
  padding-left: 1.5rem;
  margin: 0.35rem 0;
}

/* ── 代码块（Prism 高亮依托全局 CSS token） ─────────────────── */
.chat-markdown-body :deep(.markdown-body pre) {
  font-size: 0.8rem;
  border-radius: 0.5rem;
  margin: 0.5rem 0;
  padding: 0.75rem 1rem;
  overflow-x: auto;
  background: var(--bg-code);
}

.chat-markdown-body :deep(.markdown-body pre code) {
  font-size: inherit;
  background: none;
  padding: 0;
}

/* ── 行内代码 ──────────────────────────────────────────────── */
.chat-markdown-body :deep(.markdown-body code) {
  font-size: 0.825em;
  background: var(--bg-code);
  padding: 0.15em 0.35em;
  border-radius: 0.25rem;
}

/* ── 表格（GFM） ───────────────────────────────────────────── */
.chat-markdown-body :deep(.markdown-body table) {
  width: 100%;
  max-width: 100%;
  border-collapse: collapse;
  margin: 0.5rem 0;
  font-size: 0.8rem;
  display: block;
  overflow-x: auto;
}

.chat-markdown-body :deep(.markdown-body th),
.chat-markdown-body :deep(.markdown-body td) {
  border: 1px solid var(--border);
  padding: 0.4rem 0.6rem;
  text-align: left;
  white-space: nowrap;
}

.chat-markdown-body :deep(.markdown-body th) {
  background: var(--bg-elevated);
  font-weight: 600;
}

.chat-markdown-body :deep(.markdown-body tr:nth-child(even)) td {
  background: var(--bg-secondary);
}

/* ── 引用块 ────────────────────────────────────────────────── */
.chat-markdown-body :deep(.markdown-body blockquote) {
  border-left: 3px solid var(--accent);
  padding: 0.25rem 0 0.25rem 0.75rem;
  margin: 0.4rem 0;
  color: var(--text-secondary);
}

/* ── 分割线 ────────────────────────────────────────────────── */
.chat-markdown-body :deep(.markdown-body hr) {
  border: 0;
  border-top: 1px solid var(--border);
  margin: 0.75rem 0;
}

/* ── 流式中的数学占位符 ───────────────────────────────────── */
.chat-markdown-body :deep(.chat-math) {
  font-family: 'KaTeX_Main', 'Times New Roman', serif;
  color: var(--text-secondary);
}

.chat-markdown-body :deep(.chat-math-display) {
  display: block;
  text-align: center;
  margin: 0.5rem 0;
  font-size: 0.9rem;
}

.chat-markdown-body :deep(.chat-math-inline) {
  display: inline;
}

/* ── 图片 ──────────────────────────────────────────────────── */
.chat-markdown-body :deep(.markdown-body .img-wrapper) {
  display: block;
  margin: 0.5rem 0;
}

.chat-markdown-body :deep(.markdown-body .img-wrapper img) {
  max-width: 100%;
  height: auto;
  border-radius: 0.5rem;
}

/* ── KaTeX 渲染后的数学（流结束后的最终渲染） ──────────────── */
.chat-markdown-body :deep(.markdown-body .katex) {
  font-size: 1em;
}

.chat-markdown-body :deep(.markdown-body .katex-display) {
  margin: 0.5rem 0;
  overflow-x: auto;
  overflow-y: hidden;
}
</style>
