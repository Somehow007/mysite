<script setup lang="ts">
import { computed } from 'vue'
import { useMediaQuery } from '@vueuse/core'
import { ChevronLeft, ChevronRight } from 'lucide-vue-next'

const props = withDefaults(defineProps<{
  current: number
  /** 条目总数（非页数） */
  total: number
  pageSize?: number
  /** 强制紧凑模式；<640px 移动端自动启用（只显示 页码/总页数 + 上下页） */
  compact?: boolean
  /** 卡片内底部条模式：上边框分隔，左侧显示「共 N 条」 */
  inCard?: boolean
}>(), {
  pageSize: 10,
  compact: false,
  inCard: false,
})

const emit = defineEmits<{
  'update:current': [page: number]
}>()

// 与 ChatWidget 的 isMobile 断点一致（Tailwind sm = 640px）
const isMobile = useMediaQuery('(max-width: 639px)')
const isCompact = computed(() => props.compact || isMobile.value)

const totalPages = computed(() => Math.max(1, Math.ceil(props.total / props.pageSize)))

// 窗口化：当前页 ±1 + 首尾页 + 省略号；总页数 ≤7 时全部展示
const pages = computed(() => {
  const n = totalPages.value
  const c = Math.min(Math.max(props.current, 1), n)

  if (n <= 7) {
    return Array.from({ length: n }, (_, i) => i + 1)
  }

  const set = new Set<number>()
  for (const p of [1, n, c - 1, c, c + 1]) {
    if (p >= 1 && p <= n) set.add(p)
  }
  const sorted = [...set].sort((a, b) => a - b)

  const result: (number | '…')[] = []
  sorted.forEach((p, i) => {
    const prev = i > 0 ? sorted[i - 1] : undefined
    if (prev !== undefined && p - prev > 1) result.push('…')
    result.push(p)
  })
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
    class="flex flex-wrap items-center justify-between gap-x-4 gap-y-2"
    :class="inCard ? 'border-t border-border px-4 py-3' : 'pt-4'"
  >
    <span v-if="!isCompact && inCard" class="text-sm text-text-secondary tabular-nums">
      共 {{ total }} 条
    </span>
    <span v-else-if="!isCompact" class="text-sm text-text-muted">
      共 {{ total }} 条，第 {{ current }} / {{ totalPages }} 页
    </span>
    <nav
      class="ml-auto flex items-center gap-1"
      :class="isCompact ? 'w-full justify-between' : ''"
      aria-label="分页"
    >
      <!-- Prev -->
      <button
        class="inline-flex h-9 items-center gap-1 rounded-lg px-2.5 text-sm font-medium text-text-secondary transition-colors hover:bg-bg-code hover:text-text-primary disabled:cursor-not-allowed disabled:opacity-40"
        :disabled="current <= 1"
        aria-label="上一页"
        @click="go(current - 1)"
      >
        <ChevronLeft :size="16" />
        <span class="hidden min-[420px]:inline">上一页</span>
      </button>

      <!-- Page numbers / compact position -->
      <template v-if="!isCompact">
        <template v-for="(page, i) in pages" :key="i">
          <span v-if="page === '…'" class="select-none px-1 text-sm text-text-muted" aria-hidden="true">…</span>
          <button
            v-else
            class="h-9 min-w-[2.25rem] rounded-lg px-2 text-sm font-medium transition-colors"
            :class="page === current
              ? 'border border-accent bg-accent-subtle font-semibold text-accent'
              : 'border border-transparent text-text-secondary hover:bg-bg-code hover:text-text-primary'"
            :aria-current="page === current ? 'page' : undefined"
            :aria-label="`第 ${page} 页`"
            @click="go(page)"
          >
            {{ page }}
          </button>
        </template>
      </template>
      <span v-else class="text-sm tabular-nums text-text-muted" aria-live="polite">
        {{ current }} / {{ totalPages }}
      </span>

      <!-- Next -->
      <button
        class="inline-flex h-9 items-center gap-1 rounded-lg px-2.5 text-sm font-medium text-text-secondary transition-colors hover:bg-bg-code hover:text-text-primary disabled:cursor-not-allowed disabled:opacity-40"
        :disabled="current >= totalPages"
        aria-label="下一页"
        @click="go(current + 1)"
      >
        <span class="hidden min-[420px]:inline">下一页</span>
        <ChevronRight :size="16" />
      </button>
    </nav>
  </div>
</template>
