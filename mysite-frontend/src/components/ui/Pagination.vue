<script setup lang="ts">
import { computed } from 'vue'
import { ChevronLeft, ChevronRight } from 'lucide-vue-next'

const props = withDefaults(defineProps<{
  current: number
  total: number
  pageSize?: number
  maxVisible?: number
  /** 卡片内底部条模式：上边框分隔，左侧显示「共 N 条」 */
  inCard?: boolean
}>(), {
  pageSize: 10,
  maxVisible: 7,
  inCard: false,
})

const emit = defineEmits<{
  'update:current': [page: number]
}>()

const totalPages = computed(() => Math.max(1, Math.ceil(props.total / props.pageSize)))

const pages = computed(() => {
  const total = totalPages.value
  const current = props.current
  const max = props.maxVisible

  if (total <= max) {
    return Array.from({ length: total }, (_, i) => i + 1)
  }

  const half = Math.floor(max / 2)
  let start = current - half
  let end = current + half

  if (start < 1) {
    start = 1
    end = max
  }
  if (end > total) {
    end = total
    start = total - max + 1
  }

  const result: (number | '...')[] = []

  if (start > 1) {
    result.push(1)
    if (start > 2) result.push('...')
  }

  for (let i = start; i <= end; i++) {
    result.push(i)
  }

  if (end < total) {
    if (end < total - 1) result.push('...')
    result.push(total)
  }

  return result
})

function go(page: number) {
  if (page < 1 || page > totalPages.value || page === props.current) return
  emit('update:current', page)
}

const showPagination = computed(() => props.inCard || totalPages.value > 1)
</script>

<template>
  <div
    v-if="showPagination"
    class="flex items-center justify-between"
    :class="inCard ? 'border-t border-border px-4 py-3' : 'pt-4'"
  >
    <span v-if="inCard" class="text-sm text-text-secondary tabular-nums">
      共 {{ total }} 条
    </span>
    <span v-else class="text-sm text-text-muted">
      共 {{ total }} 条，第 {{ current }} / {{ totalPages }} 页
    </span>
    <nav class="flex items-center gap-1" aria-label="分页">
      <!-- Prev -->
      <button
        class="p-2 rounded-lg text-text-muted hover:text-text-primary hover:bg-bg-code transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
        :disabled="current <= 1"
        @click="go(current - 1)"
      >
        <ChevronLeft :size="16" />
      </button>

      <!-- Page numbers -->
      <template v-for="page in pages" :key="page">
        <span v-if="page === '...'" class="px-1 text-text-muted text-sm select-none">...</span>
        <button
          v-else
          class="min-w-[2.25rem] h-9 px-2 rounded-lg text-sm font-medium transition-colors"
          :class="page === current
            ? 'bg-accent-subtle text-accent border border-accent font-semibold'
            : 'border border-transparent text-text-secondary hover:text-text-primary hover:bg-bg-code'"
          @click="go(page)"
        >
          {{ page }}
        </button>
      </template>

      <!-- Next -->
      <button
        class="p-2 rounded-lg text-text-muted hover:text-text-primary hover:bg-bg-code transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
        :disabled="current >= totalPages"
        @click="go(current + 1)"
      >
        <ChevronRight :size="16" />
      </button>
    </nav>
  </div>
</template>
