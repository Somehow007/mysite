<script setup lang="ts">
import { computed } from 'vue'
import { Calendar, Eye, Clock, Tag, Heart, Lock } from 'lucide-vue-next'
import { formatDate, calculateReadingTime } from '@/utils/date'
import type { ArticleListItem } from '@/types'

const props = defineProps<{
  article: ArticleListItem
  showCategory?: boolean
  showFavorite?: boolean
}>()

const readingTime = computed(() =>
  props.article.readingTime || calculateReadingTime(props.article.summary || '')
)
</script>

<template>
  <div class="flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-text-muted">
    <span class="inline-flex items-center gap-1">
      <Calendar :size="12" />
      <time :datetime="article.updateTime">{{ formatDate(article.updateTime) }}</time>
    </span>
    <span v-if="article.viewCount > 0" class="inline-flex items-center gap-1">
      <Eye :size="12" />
      <span>{{ article.viewCount }}</span>
    </span>
    <span v-if="showFavorite && article.favoriteCount > 0" class="inline-flex items-center gap-1" :class="article.isFavorited ? 'text-red-500' : ''">
      <Heart :size="12" :fill="article.isFavorited ? 'currentColor' : 'none'" :stroke-width="article.isFavorited ? 0 : 2" />
      <span>{{ article.favoriteCount }}</span>
    </span>
    <span class="inline-flex items-center gap-1">
      <Clock :size="12" />
      <span>{{ readingTime }} 分钟</span>
    </span>
    <RouterLink
      v-if="showCategory && article.categorySlug"
      :to="`/category/${article.categorySlug}`"
      class="inline-flex items-center gap-1 hover:text-text-primary transition-colors"
    >
      <Tag :size="12" />
      <span>{{ article.categoryName }}</span>
    </RouterLink>
    <span v-if="article.visibility === 1" class="inline-flex items-center gap-1 text-amber-600 dark:text-amber-400">
      <Lock :size="12" />
      <span class="hidden sm:inline">仅自己可见</span>
    </span>
  </div>
</template>
