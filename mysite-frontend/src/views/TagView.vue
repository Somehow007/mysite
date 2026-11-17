<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { getArticles } from '@/api/article'
import { getTagBySlug } from '@/api/tag'
import { useSeo } from '@/composables/useSeo'
import ArticleList from '@/components/article/ArticleList.vue'
import type { ArticleListItem, Pagination, Tag } from '@/types'

const route = useRoute()

const articles = ref<ArticleListItem[]>([])
const pagination = ref<Pagination | null>(null)
const loading = ref(false)
const tag = ref<Tag | null>(null)
const tagName = ref('')

useSeo({
  title: () => tag.value?.name || tagName.value || '标签',
  description: () => {
    const name = tag.value?.name || tagName.value
    if (!name) return undefined
    const count = tag.value?.articleCount ?? pagination.value?.total
    return `「${name}」标签下的${count ? `${count} 篇` : ''}文章`
  },
  keywords: () => tag.value?.name,
  noindex: () => !tag.value && articles.value.length === 0 && !loading.value,
})

async function fetchTag(slug: string) {
  try {
    tag.value = await getTagBySlug(slug)
    if (tag.value?.name) tagName.value = tag.value.name
  } catch {
    tag.value = null
  }
}

async function fetchArticles(slug: string, page = 1) {
  loading.value = true
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
    if (newSlug) {
      tag.value = null
      tagName.value = ''
      fetchTag(newSlug as string)
      fetchArticles(newSlug as string)
    }
  },
)

onMounted(() => {
  if (route.params.slug) {
    fetchTag(route.params.slug as string)
    fetchArticles(route.params.slug as string)
  }
})
</script>


<template>
  <div>
    <section class="mb-10 pb-8 border-b border-border">
      <h1 class="text-3xl sm:text-4xl font-bold text-text-primary tracking-tight">
        #{{ tag?.name || tagName || '标签' }}
      </h1>
      <p class="mt-2 text-text-muted">
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
