<script setup lang="ts">
import { ref } from 'vue'
import { ChevronDown, ChevronUp, FileText } from 'lucide-vue-next'
import type { SourceChunk } from '@/types'

defineProps<{
  sources: SourceChunk[]
}>()

const expanded = ref(false)

// Phase 6：KB 颜色映射（与后端 application.yaml 的 knowledge-bases 配置一致）
const KB_COLORS: Record<string, string> = {
  '1': '#3b82f6',  // 技术博客
  '2': '#10b981',  // 读书笔记
  '3': '#f59e0b',  // 学习笔记
}
const DEFAULT_KB_COLOR = '#8b5cf6'

function kbColor(kbId: string | null | undefined): string {
  if (kbId == null) return DEFAULT_KB_COLOR
  return KB_COLORS[kbId] ?? DEFAULT_KB_COLOR
}
</script>

<template>
  <div v-if="sources.length > 0" class="mt-3 border-t border-border-subtle pt-2.5">
    <button
      class="flex items-center gap-1.5 text-xs text-text-muted hover:text-text-secondary transition-colors"
      @click="expanded = !expanded"
    >
      <FileText :size="12" />
      <span>参考来源 ({{ sources.length }})</span>
      <ChevronDown v-if="!expanded" :size="12" />
      <ChevronUp v-else :size="12" />
    </button>

    <div v-if="expanded" class="mt-2 space-y-2">
      <div
        v-for="(source, idx) in sources"
        :key="idx"
        class="text-xs bg-bg-elevated rounded-lg p-2.5 border border-border-subtle"
      >
        <div class="flex items-center justify-between gap-2 mb-1">
          <div class="flex items-center gap-1.5 min-w-0">
            <!-- Phase 6：KB 标签色 -->
            <span
              v-if="source.kbName"
              class="text-[10px] px-1.5 py-px rounded-full font-medium shrink-0"
              :style="{ backgroundColor: kbColor(source.kbId) + '20', color: kbColor(source.kbId) }"
            >{{ source.kbName }}</span>
            <span class="font-medium text-text-primary truncate">{{ source.title }}</span>
          </div>
          <span class="text-text-muted shrink-0">相关度 {{ (source.score * 100).toFixed(0) }}%</span>
        </div>
        <p class="text-text-muted line-clamp-3 leading-relaxed">{{ source.content }}</p>
      </div>
    </div>
  </div>
</template>
