<script setup lang="ts">
import { ref, watch, nextTick } from 'vue'
import { ChevronsDownUp, ChevronsUpDown } from 'lucide-vue-next'
import type { TocItem } from '@/composables/useMarkdown'
import { useToc } from '@/composables/useToc'
import TocTree from './TocTree.vue'

const props = defineProps<{
  items: TocItem[]
}>()

const tocNavRef = ref<HTMLElement | null>(null)

const {
  activeId,
  tocTree,
  minLevel,
  hasCollapsibleNodes,
  allExpanded,
  isGroupExpanded,
  staggerDelay,
  navigateTo,
  toggleNode,
  toggleAll,
  handleWheel,
  scrollTocToActive,
} = useToc(() => props.items)

watch(activeId, () => {
  nextTick(() => scrollTocToActive(tocNavRef.value))
})
</script>

<template>
  <nav
    v-if="items.length > 0"
    ref="tocNavRef"
    aria-label="文章目录"
    class="toc hidden lg:flex flex-col h-full min-h-0"
    @wheel="handleWheel"
  >
    <div class="flex items-center justify-between mb-3 px-1 shrink-0">
      <h4 class="text-xs font-semibold uppercase tracking-wider text-text-muted">
        目录
      </h4>
      <button
        v-if="hasCollapsibleNodes"
        @click="toggleAll"
        class="p-0.5 rounded text-text-muted hover:text-accent transition-colors duration-200"
        :title="allExpanded ? '收起全部' : '展开全部'"
      >
        <ChevronsDownUp v-if="allExpanded" :size="14" />
        <ChevronsUpDown v-else :size="14" />
      </button>
    </div>
    <ul
      class="text-sm border-l border-border overflow-y-auto flex-1 min-h-0 scrollbar-thin pr-1"
      role="list"
    >
      <TocTree
        :nodes="tocTree"
        :active-id="activeId"
        :min-level="minLevel"
        :is-group-expanded="isGroupExpanded"
        :stagger-delay="staggerDelay"
        @navigate="navigateTo"
        @toggle="toggleNode"
      />
    </ul>
  </nav>
</template>
