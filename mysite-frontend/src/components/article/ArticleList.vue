<script setup lang="ts">
import ArticleCard from './ArticleCard.vue'
import ArticlePagination from './ArticlePagination.vue'
import SkeletonCard from '@/components/common/SkeletonCard.vue'
import type { ArticleListItem, Pagination } from '@/types'

defineProps<{
  articles: ArticleListItem[]
  pagination: Pagination | null
  loading?: boolean
  skeletonCount?: number
}>()

defineEmits<{
  'page-change': [page: number]
}>()
</script>

<template>
  <div>
    <div v-if="loading" class="space-y-10">
      <SkeletonCard v-for="i in (skeletonCount || 5)" :key="i" :lines="2" />
    </div>

    <div v-else-if="articles.length === 0" class="py-16 text-center">
      <p class="text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)]">暂无文章</p>
    </div>

    <div v-else class="space-y-10">
      <ArticleCard v-for="article in articles" :key="article.id" :article="article" />
    </div>

    <ArticlePagination
      v-if="pagination && !loading"
      :current="pagination.page"
      :total="pagination.totalPages"
      @change="$emit('page-change', $event)"
    />
  </div>
</template>
