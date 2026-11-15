<script setup lang="ts">
import { Loader2 } from 'lucide-vue-next'
import type { Category } from '@/types'

defineProps<{
  categories: Category[]
  selectedSlug: string | null
  /** 文章总数；仅未筛选分类时传入（筛选后 pagination.total 是分类内数量） */
  totalCount?: number | null
  loading?: boolean
}>()

const emit = defineEmits<{
  select: [slug: string | null]
}>()
</script>

<template>
  <nav
    aria-label="分类筛选"
    class="-mx-4 flex overflow-x-auto border-b border-border px-4 py-2 [scrollbar-width:none] sm:-mx-6 sm:px-6 lg:mx-0 lg:px-0 [&::-webkit-scrollbar]:hidden"
  >
    <div class="mx-auto flex items-baseline gap-5 lg:gap-7">
      <button
        type="button"
        class="shrink-0 whitespace-nowrap py-1 text-sm transition-colors duration-200"
        :class="!selectedSlug
          ? 'font-semibold text-text-primary'
          : 'text-text-secondary hover:text-text-primary'"
        :aria-pressed="!selectedSlug"
        @click="emit('select', null)"
      >
        全部<template v-if="totalCount && totalCount > 0"><span class="ml-1 text-[11px] font-normal text-text-muted">{{ totalCount }}</span></template>
      </button>

      <Loader2 v-if="loading" :size="14" class="animate-spin self-center text-text-muted" />

      <template v-else>
        <button
          v-for="cat in categories"
          :key="cat.slug"
          type="button"
          class="shrink-0 whitespace-nowrap py-1 text-sm transition-colors duration-200"
          :class="selectedSlug === cat.slug
            ? 'font-semibold text-text-primary'
            : 'text-text-secondary hover:text-text-primary'"
          :aria-pressed="selectedSlug === cat.slug"
          @click="emit('select', cat.slug)"
        >
          {{ cat.name }}<span v-if="cat.articleCount" class="ml-1 text-[11px] font-normal text-text-muted">{{ cat.articleCount }}</span>
        </button>
      </template>
    </div>
  </nav>
</template>
