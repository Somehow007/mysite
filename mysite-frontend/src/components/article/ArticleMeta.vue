<script setup lang="ts">
import { Calendar, Eye, Clock, Tag } from 'lucide-vue-next'
import { formatDate, calculateReadingTime } from '@/utils/date'
import type { ArticleListItem } from '@/types'

const props = defineProps<{
  article: ArticleListItem
  showCategory?: boolean
}>()

const readingTime = calculateReadingTime(props.article.summary || '')
</script>

<template>
  <div class="flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)]">
    <span class="inline-flex items-center gap-1">
      <Calendar :size="12" />
      <time :datetime="article.updateTime">{{ formatDate(article.updateTime) }}</time>
    </span>
    <span v-if="article.viewCount > 0" class="inline-flex items-center gap-1">
      <Eye :size="12" />
      <span>{{ article.viewCount }}</span>
    </span>
    <span class="inline-flex items-center gap-1">
      <Clock :size="12" />
      <span>{{ readingTime }} 分钟</span>
    </span>
    <RouterLink
      v-if="showCategory && article.categorySlug"
      :to="`/category/${article.categorySlug}`"
      class="inline-flex items-center gap-1 hover:text-[var(--color-text-heading)] dark:hover:text-[var(--color-dark-text-heading)] transition-colors"
    >
      <Tag :size="12" />
      <span>{{ article.categoryName }}</span>
    </RouterLink>
  </div>
</template>
