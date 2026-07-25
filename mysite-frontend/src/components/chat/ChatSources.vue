<script setup lang="ts">
import { ref } from 'vue'
import { RouterLink } from 'vue-router'
import { ChevronDown, ChevronUp, FileText, ExternalLink } from 'lucide-vue-next'
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

function relevanceDotBg(score: number): string {
  if (score >= 0.8) return 'bg-success'
  if (score >= 0.6) return 'bg-warning'
  return 'bg-text-muted'
}

// KB color mapping (aligned with backend knowledge-bases config)
const KB_COLORS: Record<string, string> = {
  '1': '#3b82f6',
  '2': '#10b981',
  '3': '#f59e0b',
}
const DEFAULT_KB_COLOR = '#8b5cf6'

function kbColor(kbId: string | null | undefined): string {
  if (kbId == null) return DEFAULT_KB_COLOR
  return KB_COLORS[kbId] ?? DEFAULT_KB_COLOR
}

/** Determine if a source links to an article */
function sourceLink(source: SourceChunk): { type: 'article' | 'none'; href?: string } {
  if (source.articleId) {
    return { type: 'article', href: `/article/${source.articleId}` }
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
      <div
        v-for="(source, idx) in sources"
        :key="idx"
        class="text-xs bg-bg-primary rounded-[10px] border border-border overflow-hidden transition-all duration-150"
        :class="{ 'cursor-pointer hover:border-accent hover:shadow-[0_4px_12px_-4px_rgba(79,70,229,0.25)] hover:-translate-y-px': sourceLink(source).type === 'article' }"
      >
        <RouterLink
          v-if="sourceLink(source).type === 'article'"
          :to="sourceLink(source).href!"
          class="flex items-center gap-2.5 px-2.5 py-2"
        >
          <!-- 编号徽章 22px -->
          <span
            class="w-[22px] h-[22px] rounded-full bg-accent-subtle text-accent text-[10px] font-bold flex items-center justify-center shrink-0"
          >{{ startIndex + idx + 1 }}</span>
          <!-- KB 名称 -->
          <span
            v-if="source.kbName"
            class="text-[10px] px-1.5 py-px rounded-full font-medium shrink-0"
            :style="{ backgroundColor: kbColor(source.kbId) + '20', color: kbColor(source.kbId) }"
          >{{ source.kbName }}</span>
          <!-- 标题 -->
          <span class="font-medium text-text-primary truncate group-hover:text-accent transition-colors flex-1 min-w-0">
            {{ source.title }}
          </span>
          <!-- 相关度 -->
          <span class="text-text-muted shrink-0 flex items-center gap-1" :class="relevanceColor(source.score)">
            <span class="w-1.5 h-1.5 rounded-full shrink-0" :class="relevanceDotBg(source.score)" />
            {{ (source.score * 100).toFixed(0) }}%
          </span>
          <ExternalLink :size="10" class="text-text-muted shrink-0 opacity-0 group-hover:opacity-100 transition-opacity" />
        </RouterLink>

        <!-- Non-linkable source -->
        <div v-else class="flex items-center gap-2.5 px-2.5 py-2">
          <span
            class="w-[22px] h-[22px] rounded-full bg-bg-code text-text-muted text-[10px] font-bold flex items-center justify-center shrink-0"
          >{{ startIndex + idx + 1 }}</span>
          <span
            v-if="source.kbName"
            class="text-[10px] px-1.5 py-px rounded-full font-medium shrink-0"
            :style="{ backgroundColor: kbColor(source.kbId) + '20', color: kbColor(source.kbId) }"
          >{{ source.kbName }}</span>
          <span class="font-medium text-text-primary truncate flex-1 min-w-0">{{ source.title }}</span>
          <span class="text-text-muted shrink-0 flex items-center gap-1" :class="relevanceColor(source.score)">
            <span class="w-1.5 h-1.5 rounded-full shrink-0" :class="relevanceDotBg(source.score)" />
            {{ (source.score * 100).toFixed(0) }}%
          </span>
        </div>
      </div>
    </div>
  </div>
</template>
