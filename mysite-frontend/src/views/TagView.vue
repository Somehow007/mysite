<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useHead } from '@unhead/vue'
import { getArticles } from '@/api/article'
import ArticleList from '@/components/article/ArticleList.vue'
import type { ArticleListItem, Pagination } from '@/types'

const route = useRoute()

const articles = ref<ArticleListItem[]>([])
const pagination = ref<Pagination | null>(null)
const loading = ref(false)
const tagSlug = ref('')

useHead(() => ({
  title: tagSlug.value ? `${tagSlug.value} - MySite` : '标签 - MySite',
}))

async function fetchArticles(slug: string, page = 1) {
  loading.value = true
  tagSlug.value = slug
  try {
    const res = await getArticles({ page, size: 10, tagSlug: slug })
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
  fetchArticles(route.params.slug as string, page)
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

watch(
  () => route.params.slug,
  (newSlug) => {
    if (newSlug) fetchArticles(newSlug as string)
  },
)

onMounted(() => {
  if (route.params.slug) {
    fetchArticles(route.params.slug as string)
  }
})
</script>

<template>
  <div>
    <section class="mb-10 pb-8 border-b border-[var(--color-border)] dark:border-[var(--color-dark-border)]">
      <h1 class="text-3xl sm:text-4xl font-bold text-[var(--color-text-heading)] dark:text-[var(--color-dark-text-heading)] tracking-tight">
        #{{ tagSlug || '标签' }}
      </h1>
      <p class="mt-2 text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)]">
        该标签下的所有文章
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
