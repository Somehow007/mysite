<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getArticles } from '@/api/article'
import { useSiteStore } from '@/stores/site'
import ArticleList from '@/components/article/ArticleList.vue'
import type { ArticleListItem, Pagination } from '@/types'

const route = useRoute()
const router = useRouter()
const siteStore = useSiteStore()

const articles = ref<ArticleListItem[]>([])
const pagination = ref<Pagination | null>(null)
const loading = ref(false)

async function fetchArticles(page = 1) {
  loading.value = true
  try {
    const res = await getArticles({ page, size: 10 })
    articles.value = res.list
    pagination.value = res.pagination
  } catch {
    articles.value = []
    pagination.value = null
  } finally {
    loading.value = false
  }
}

function handlePageChange(page: number) {
  router.push(page === 1 ? '/' : `/page/${page}`)
}

watch(
  () => route.params.page,
  (newPage) => {
    const page = newPage ? Number(newPage) : 1
    fetchArticles(page)
  },
)

onMounted(() => {
  const page = route.params.page ? Number(route.params.page) : 1
  fetchArticles(page)
})
</script>

<template>
  <div>
    <section class="mb-16 pb-12 border-b border-[var(--color-border)] dark:border-[var(--color-dark-border)]">
      <h1 class="text-4xl font-bold text-[var(--color-text-heading)] dark:text-[var(--color-dark-text-heading)] mb-3 tracking-tight">
        {{ siteStore.site.title }}
      </h1>
      <p class="text-lg text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)] leading-relaxed max-w-xl">
        {{ siteStore.site.description }}
      </p>
    </section>

    <ArticleList
      :articles="articles"
      :pagination="pagination"
      :loading="loading"
      :skeleton-count="5"
      @page-change="handlePageChange"
    />
  </div>
</template>
