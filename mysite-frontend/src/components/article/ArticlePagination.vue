<script setup lang="ts">
import { ChevronLeft, ChevronRight } from 'lucide-vue-next'

defineProps<{
  current: number
  total: number
}>()

defineEmits<{
  change: [page: number]
}>()
</script>

<template>
  <nav v-if="total > 1" class="flex items-center justify-center gap-1.5 mt-12">
    <button
      :disabled="current <= 1"
      @click="$emit('change', current - 1)"
      class="p-2 rounded-lg text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)] hover:bg-[var(--color-accent-light)] dark:hover:bg-[var(--color-dark-accent-light)] hover:text-[var(--color-accent)] dark:hover:text-[var(--color-dark-accent)] disabled:opacity-30 disabled:cursor-not-allowed transition-all duration-200"
      aria-label="上一页"
    >
      <ChevronLeft :size="18" />
    </button>

    <template v-for="page in total" :key="page">
      <button
        v-if="page === 1 || page === total || Math.abs(page - current) <= 1"
        @click="$emit('change', page)"
        class="min-w-[36px] h-9 px-2 rounded-lg text-sm transition-all duration-200"
        :class="[
          page === current
            ? 'bg-[var(--color-accent)] dark:bg-[var(--color-dark-accent)] text-white dark:text-[var(--color-dark-bg-primary)] font-medium shadow-sm'
            : 'text-[var(--color-text-body)] dark:text-[var(--color-dark-text-body)] hover:bg-[var(--color-accent-light)] dark:hover:bg-[var(--color-dark-accent-light)] hover:text-[var(--color-accent)] dark:hover:text-[var(--color-dark-accent)]'
        ]"
      >
        {{ page }}
      </button>
      <span
        v-else-if="Math.abs(page - current) === 2"
        class="text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)] text-sm px-1"
      >
        ...
      </span>
    </template>

    <button
      :disabled="current >= total"
      @click="$emit('change', current + 1)"
      class="p-2 rounded-lg text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)] hover:bg-[var(--color-accent-light)] dark:hover:bg-[var(--color-dark-accent-light)] hover:text-[var(--color-accent)] dark:hover:text-[var(--color-dark-accent)] disabled:opacity-30 disabled:cursor-not-allowed transition-all duration-200"
      aria-label="下一页"
    >
      <ChevronRight :size="18" />
    </button>
  </nav>
</template>
