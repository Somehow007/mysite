<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useHead } from '@unhead/vue'
import { Search as SearchIcon, Library } from 'lucide-vue-next'
import { searchArticles } from '@/api/article'
import { getCollections } from '@/api/collection'
import ArticleList from '@/components/article/ArticleList.vue'
import CollectionCard from '@/components/collection/CollectionCard.vue'
import type { ArticleListItem, Collection, Pagination } from '@/types'

const route = useRoute()
const router = useRouter()

const articles = ref<ArticleListItem[]>([])
const pagination = ref<Pagination | null>(null)
const loading = ref(false)
const loadError = ref('')
const keyword = ref('')
const collections = ref<Collection[]>([])

useHead(() => ({
  title: keyword.value ? `搜索: ${keyword.value} - MySite` : '搜索 - MySite',
}))

async function doSearch(q: string, page = 1) {
  if (!q) return
  loading.value = true
  loadError.value = ''
  keyword.value = q
  try {
    const [articleRes, collectionRes] = await Promise.all([
      searchArticles({ keyword: q, page, size: 10 }),
      getCollections({ page: 1, size: 5, keyword: q }).catch(() => ({ list: [] as Collection[], pagination: { page: 1, size: 5, total: 0, totalPages: 0 } })),
    ])
    articles.value = articleRes.list
    pagination.value = articleRes.pagination
    collections.value = collectionRes.list
  } catch (e: unknown) {
    articles.value = []
    pagination.value = null
    collections.value = []
    loadError.value = e instanceof Error ? e.message : '搜索请求失败'
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

    <!-- 合集搜索结果 -->
    <section v-if="collections.length > 0" class="mb-10">
      <h2 class="flex items-center gap-2 text-lg font-semibold text-text-primary mb-4">
        <Library :size="18" class="text-accent" />
        相关合集
        <span class="text-sm font-normal text-text-muted">({{ collections.length }})</span>
      </h2>
      <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
        <CollectionCard
          v-for="collection in collections"
          :key="collection.id"
          :collection="collection"
        />
      </div>
    </section>

    <!-- 文章搜索结果 -->
    <section>
      <h2 v-if="articles.length > 0 || loading" class="text-lg font-semibold text-text-primary mb-4">
        相关文章
      </h2>

      <div v-if="loadError" class="py-16 text-center">
        <p class="text-red-500 mb-4">{{ loadError }}</p>
        <button @click="doSearch(keyword)" class="btn-secondary">重试</button>
      </div>

      <div v-else-if="!loading && articles.length === 0 && keyword && collections.length === 0" class="py-16 text-center">
        <SearchIcon :size="48" class="mx-auto mb-4 text-text-muted opacity-30" />
        <p class="text-text-muted">
          没有找到与「{{ keyword }}」相关的内容
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
    </section>
  </div>
</template>
