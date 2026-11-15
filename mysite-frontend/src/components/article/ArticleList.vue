<script setup lang="ts">
import ArticleRow from './ArticleRow.vue'
import { Pagination as PaginationBar, EmptyState } from '@/components/ui'
import type { ArticleListItem, Pagination } from '@/types'

defineProps<{
  articles: ArticleListItem[]
  pagination: Pagination | null
  loading?: boolean
  skeletonCount?: number
  showFavorite?: boolean
  emptyTitle?: string
  emptyDescription?: string
}>()

defineEmits<{
  'page-change': [page: number]
  'favorite-toggle': [articleId: string, favorited: boolean]
}>()
</script>

<template>
  <div>
    <div v-if="loading" class="animate-pulse">
      <div
        v-for="i in (skeletonCount || 5)"
        :key="i"
        class="flex gap-4 border-b border-border py-5 lg:gap-6 lg:py-6"
      >
        <div class="min-w-0 flex-1 space-y-2.5">
          <div class="skeleton h-3 w-24 rounded" />
          <div class="skeleton h-6 w-3/4 rounded" />
          <div class="skeleton mt-3 h-4 w-full rounded" />
          <div class="skeleton h-4 w-2/3 rounded" />
          <div class="skeleton mt-4 h-3 w-40 rounded" />
        </div>
        <div class="skeleton aspect-[4/3] w-24 rounded lg:w-[168px]" />
      </div>
    </div>

    <EmptyState
      v-else-if="articles.length === 0"
      :title="emptyTitle || '暂无文章'"
      :description="emptyDescription"
    />

    <div v-else>
      <ArticleRow
        v-for="(article, index) in articles"
        :key="article.id"
        :article="article"
        :show-favorite="showFavorite"
        :priority="index < 2"
        data-reveal
        @favorite-toggle="(...args: [string, boolean]) => $emit('favorite-toggle', ...args)"
      />
    </div>

    <PaginationBar
      v-if="pagination && !loading"
      :current="pagination.page"
      :total="pagination.total"
      :page-size="pagination.size"
      class="mt-2"
      @update:current="$emit('page-change', $event)"
    />
  </div>
</template>
