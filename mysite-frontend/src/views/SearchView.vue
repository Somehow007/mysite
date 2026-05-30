<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useHead } from '@unhead/vue'
import { Search as SearchIcon } from 'lucide-vue-next'
import { searchArticles } from '@/api/article'
import ArticleList from '@/components/article/ArticleList.vue'
import type { ArticleListItem, Pagination } from '@/types'

const route = useRoute()
const router = useRouter()

const articles = ref<ArticleListItem[]>([])
const pagination = ref<Pagination | null>(null)
const loading = ref(false)
const keyword = ref('')

useHead(() => ({
  title: keyword.value ? `搜索: ${keyword.value} - MySite` : '搜索 - MySite',
}))

async function doSearch(q: string, page = 1) {
  if (!q) return
  loading.value = true
  keyword.value = q
  try {
    const res = await searchArticles({ keyword: q, page, size: 10 })
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
  doSearch(keyword.value, page)
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

watch(
  () => route.query.q,
  (q) => {
    if (q && typeof q === 'string') {
      doSearch(q)
    }
  },
  { immediate: true },
)
</script>

<template>
  <div>
    <section class="mb-10 pb-8 border-b border-border">
      <div class="flex items-center gap-3 mb-2">
        <SearchIcon :size="28" class="text-accent" />
        <h1 class="text-3xl sm:text-4xl font-bold text-text-primary tracking-tight">
          搜索结果
        </h1>
      </div>
      <p v-if="keyword" class="text-text-muted">
        关键词：「{{ keyword }}」
      </p>
    </section>

    <div v-if="!loading && articles.length === 0 && keyword" class="py-16 text-center">
      <SearchIcon :size="48" class="mx-auto mb-4 text-text-muted opacity-30" />
      <p class="text-text-muted">
        没有找到与「{{ keyword }}」相关的文章
      </p>
    </div>

    <ArticleList
      v-else
      :articles="articles"
      :pagination="pagination"
      :loading="loading"
      :skeleton-count="5"
      @page-change="handlePageChange"
    />
  </div>
</template>
