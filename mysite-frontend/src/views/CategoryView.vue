<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useHead } from '@unhead/vue'
import { getCategoryBySlug, getCategoryArticles } from '@/api/category'
import ArticleList from '@/components/article/ArticleList.vue'
import type { Category, ArticleListItem, Pagination } from '@/types'

const route = useRoute()

const category = ref<Category | null>(null)
const articles = ref<ArticleListItem[]>([])
const pagination = ref<Pagination | null>(null)
const loading = ref(false)
const error = ref(false)

useHead(() => ({
  title: category.value ? `${category.value.name} - MySite` : '分类 - MySite',
}))

async function fetchData(slug: string, page = 1) {
  loading.value = true
  error.value = false
  try {
    const [catData, artData] = await Promise.all([
      getCategoryBySlug(slug),
      getCategoryArticles(slug, { page, size: 10 }),
    ])
    category.value = catData
    articles.value = artData.list
    pagination.value = artData.pagination
  } catch {
    error.value = true
    category.value = null
    articles.value = []
    pagination.value = null
  } finally {
    loading.value = false
  }
}

function handlePageChange(page: number) {
  if (category.value) {
    fetchData(category.value.slug, page)
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }
}

watch(
  () => route.params.slug,
  (newSlug) => {
    if (newSlug) fetchData(newSlug as string)
  },
)

onMounted(() => {
  if (route.params.slug) {
    fetchData(route.params.slug as string)
  }
})
</script>

<template>
  <div>
    <div v-if="loading && !category" class="animate-pulse space-y-4">
      <div class="skeleton h-8 w-48 rounded" />
      <div class="skeleton h-4 w-64 rounded" />
    </div>

    <div v-else-if="error" class="py-16 text-center">
      <p class="text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)]">分类不存在或加载失败</p>
    </div>

    <template v-else-if="category">
      <header class="mb-12 pb-8 border-b border-[var(--color-border)] dark:border-[var(--color-dark-border)]">
        <h1 class="text-3xl font-bold text-[var(--color-text-heading)] dark:text-[var(--color-dark-text-heading)] mb-2">
          {{ category.name }}
        </h1>
        <p v-if="category.description" class="text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)]">
          {{ category.description }}
        </p>
      </header>

      <ArticleList
        :articles="articles"
        :pagination="pagination"
        :loading="loading"
        :skeleton-count="5"
        @page-change="handlePageChange"
      />
    </template>
  </div>
</template>
