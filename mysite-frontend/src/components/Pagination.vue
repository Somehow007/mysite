<template>
  <nav class="pagination" aria-label="分页">
    <div class="inner">
      <div class="box pagination-box">
        <router-link
          v-if="pagination.prev"
          :to="getPageUrl(pagination.prev)"
          title="较新的文章"
          class="pagination-next"
        >
          <i class="icon icon-arrow-left">
            <IconArrowLeft />
          </i>
          <span class="pagination-label">较新的文章</span>
        </router-link>
        <span class="pagination-info">
          第 {{ pagination.page }} 页，共 {{ pagination.pages }} 页
        </span>
        <router-link
          v-if="pagination.next"
          :to="getPageUrl(pagination.next)"
          title="较旧的文章"
          class="pagination-prev"
        >
          <span class="pagination-label">较旧的文章</span>
          <i class="icon icon-arrow-right">
            <IconArrowRight />
          </i>
        </router-link>
      </div>
    </div>
  </nav>
</template>

<script setup lang="ts">
import { useRoute } from 'vue-router'
import type { Pagination } from '@/types/blog'
import IconArrowLeft from './icons/IconArrowLeft.vue'
import IconArrowRight from './icons/IconArrowRight.vue'

const props = defineProps<{
  pagination: Pagination
}>()

const route = useRoute()

const getPageUrl = (page: number): string => {
  if (page === 1) {
    return '/'
  }
  // 获取当前路径的基础部分（排除页码）
  const basePath = route.path.replace(/\/page\/\d+$/, '') || '/'
  if (basePath === '/') {
    return `/page/${page}`
  }
  return `${basePath}/page/${page}`
}
</script>

<style scoped>
/* 样式已在 blog.css 中定义 */
</style>

