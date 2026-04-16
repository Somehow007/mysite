<template>
  <div class="extra-pagination" :class="{ paged: pagination && pagination.pages > 1 }">
    <Pagination
      v-if="pagination && pagination.pages > 1"
      :pagination="pagination"
      @page-change="handlePageChange"
    />
  </div>

  <PostCard v-for="post in posts" :key="post.id" :post="post" />

  <Pagination
    v-if="pagination && pagination.pages > 1"
    :pagination="pagination"
    @page-change="handlePageChange"
  />
</template>

<script setup lang="ts">
import type { Post, Pagination as PaginationType } from '@/types/blog'
import PostCard from './PostCard.vue'
import Pagination from './Pagination.vue'

const props = defineProps<{
  posts: Post[]
  pagination?: PaginationType
}>()

const emit = defineEmits<{
  (e: 'page-change', page: number): void
}>()

const handlePageChange = (page: number) => {
  emit('page-change', page)
}
</script>

<style scoped>
/* 样式已在 blog.css 中定义 */
</style>

