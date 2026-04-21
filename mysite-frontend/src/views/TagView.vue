<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useHead } from '@unhead/vue'
import { getTagBySlug, getTagArticles } from '@/api/tag'
import ArticleList from '@/components/article/ArticleList.vue'
import type { Tag, ArticleListItem, Pagination } from '@/types'

const route = useRoute()

const tag = ref<Tag | null>(null)
const articles = ref<ArticleListItem[]>([])
const pagination = ref<Pagination | null>(null)
const loading = ref(false)
const error = ref(false)

useHead(() => ({
  title: tag.value ? `#${tag.value.name} - MySite` : '标签 - MySite',
}))

async function fetchData(slug: string, page = 1) {
  loading.value = true
  error.value = false
  try {
    const [tagData, artData] = await Promise.all([
      getTagBySlug(slug),
      getTagArticles(slug, { page, size: 10 }),
    ])
    tag.value = tagData
    articles.value = artData.list
    pagination.value = artData.pagination
  } catch {
    error.value = true
    tag.value = null
    articles.value = []
    pagination.value = null
  } finally {
    loading.value = false
  }
}

function handlePageChange(page: number) {
  if (tag.value) {
    fetchData(tag.value.slug, page)
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
    <div v-if="loading && !tag" class="animate-pulse space-y-4">
      <div class="skeleton h-8 w-32 rounded" />
    </div>

    <div v-else-if="error" class="py-16 text-center">
      <p class="text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)]">标签不存在或加载失败</p>
    </div>

    <template v-else-if="tag">
      <header class="mb-12 pb-8 border-b border-[var(--color-border)] dark:border-[var(--color-dark-border)]">
        <h1 class="text-3xl font-bold text-[var(--color-text-heading)] dark:text-[var(--color-dark-text-heading)]">
          #{{ tag.name }}
        </h1>
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
