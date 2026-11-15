<script setup lang="ts">
import { ref } from 'vue'
import { RouterLink } from 'vue-router'
import { ChevronDown, ChevronUp, FileText } from 'lucide-vue-next'
import type { SourceChunk } from '@/types'

const props = withDefaults(defineProps<{
  sources: SourceChunk[]
  /** Index of the citation number in message content, displayed as badge */
  startIndex?: number
}>(), {
  startIndex: 0,
})

const expanded = ref(true) // 默认展开

// Relevance color
function relevanceColor(score: number): string {
  if (score >= 0.8) return 'text-success'
  if (score >= 0.6) return 'text-warning'
  return 'text-text-muted'
}

/** Determine if a source links to an article（T11 起后端下发 articleId；旧消息/UPLOAD 降级为静态卡） */
function sourceLink(source: SourceChunk): { type: 'article' | 'none'; href?: string } {
  if (source.articleId) {
    return { type: 'article', href: `/post/${source.articleId}` }
  }
  return { type: 'none' }
}
</script>

<template>
  <div v-if="sources.length > 0" class="mt-3 pt-2.5 border-t border-dashed border-border">
    <button
      class="flex items-center gap-1.5 text-[11.5px] font-semibold text-text-muted hover:text-text-secondary transition-colors"
      @click="expanded = !expanded"
    >
      <FileText :size="12" />
      <span>参考来源 ({{ sources.length }})</span>
      <ChevronDown v-if="expanded" :size="11" />
      <ChevronUp v-else :size="11" />
    </button>

    <div v-if="expanded" class="mt-2 space-y-2">
      <component
        :is="sourceLink(source).type === 'article' ? RouterLink : 'div'"
        v-for="(source, idx) in sources"
        :key="idx"
        :to="sourceLink(source).type === 'article' ? sourceLink(source).href : undefined"
        class="chat-source-card"
        :class="{ 'chat-source-card--link': sourceLink(source).type === 'article' }"
      >
        <span class="chat-source-title">
          <span class="chat-source-num">[{{ startIndex + idx + 1 }}]</span>{{ source.title }}
        </span>
        <span class="chat-source-meta">
          <template v-if="source.kbName">{{ source.kbName }} · </template><span :class="relevanceColor(source.score)">{{ (source.score * 100).toFixed(0) }}%</span>
        </span>
      </component>
    </div>
  </div>
</template>
