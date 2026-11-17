<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { getArticles } from '@/api/article'
import { getCategoryBySlug } from '@/api/category'
import { useSeo } from '@/composables/useSeo'
import ArticleList from '@/components/article/ArticleList.vue'
import type { ArticleListItem, Category, Pagination } from '@/types'

const route = useRoute()

const articles = ref<ArticleListItem[]>([])
const pagination = ref<Pagination | null>(null)
const loading = ref(false)
const category = ref<Category | null>(null)
const categoryName = ref('')

useSeo({
  title: () => category.value?.seoTitle || category.value?.name || categoryName.value || '分类',
  description: () => category.value?.seoDescription || category.value?.description || undefined,
  keywords: () => category.value?.seoKeywords,
  noindex: () => !category.value && articles.value.length === 0 && !loading.value,
})

async function fetchCategory(slug: string) {
  try {
    category.value = await getCategoryBySlug(slug)
  } catch {
    category.value = null
  }
}

async function fetchArticles(slug: string, page = 1) {
  loading.value = true
  try {
    const res = await getArticles({ page, size: 10, categorySlug: slug })
    articles.value = res.list
    pagination.value = res.pagination
    if (res.list.length > 0) {
      categoryName.value = res.list[0]?.categoryName || slug
    }
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
    if (newSlug) {
      category.value = null
      categoryName.value = ''
      fetchCategory(newSlug as string)
      fetchArticles(newSlug as string)
    }
  },
)

onMounted(() => {
  if (route.params.slug) {
    fetchCategory(route.params.slug as string)
    fetchArticles(route.params.slug as string)
  }
})
</script>


<template>
  <div>
    <section class="mb-10 pb-8 border-b border-border">
      <h1 class="text-3xl sm:text-4xl font-bold text-text-primary tracking-tight">
        {{ category?.name || categoryName || '分类' }}
      </h1>
      <p class="mt-2 text-text-muted">
        该分类下的所有文章
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
