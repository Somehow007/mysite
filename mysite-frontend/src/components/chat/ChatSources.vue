<script setup lang="ts">
import { ref } from 'vue'
import { ChevronDown, ChevronUp, FileText, ExternalLink } from 'lucide-vue-next'
import type { SourceChunk } from '@/types'

defineProps<{
  sources: SourceChunk[]
}>()

const expanded = ref(false)
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
          <span class="font-medium text-text-primary truncate">{{ source.title }}</span>
          <span class="text-text-muted shrink-0">相关度 {{ (source.score * 100).toFixed(0) }}%</span>
        </div>
        <p class="text-text-muted line-clamp-3 leading-relaxed">{{ source.content }}</p>
      </div>
    </div>
  </div>
</template>
