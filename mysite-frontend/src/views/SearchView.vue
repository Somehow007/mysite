<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useHead } from '@unhead/vue'
import { Search } from 'lucide-vue-next'
import { searchArticles } from '@/api/article'
import ArticleCard from '@/components/article/ArticleCard.vue'
import ArticlePagination from '@/components/article/ArticlePagination.vue'
import SkeletonCard from '@/components/common/SkeletonCard.vue'
import type { ArticleListItem, Pagination } from '@/types'

const route = useRoute()
const router = useRouter()

const keyword = ref('')
const articles = ref<ArticleListItem[]>([])
const pagination = ref<Pagination | null>(null)
const loading = ref(false)
const searched = ref(false)

useHead(() => ({
  title: keyword.value ? `搜索: ${keyword.value} - MySite` : '搜索 - MySite',
}))

async function doSearch(kw: string, page = 1) {
  if (!kw.trim()) return

  loading.value = true
  searched.value = true
  try {
    const res = await searchArticles({ keyword: kw, page, size: 10 })
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

function handleSearch() {
  if (keyword.value.trim()) {
    router.replace({ path: '/search', query: { q: keyword.value } })
  }
}

watch(
  () => route.query.q,
  (newQ) => {
    if (newQ && typeof newQ === 'string') {
      keyword.value = newQ
      doSearch(newQ)
    }
  },
)

onMounted(() => {
  const q = route.query.q as string
  if (q) {
    keyword.value = q
    doSearch(q)
  }
})
</script>

<template>
  <div>
    <header class="mb-12 pb-8 border-b border-[var(--color-border)] dark:border-[var(--color-dark-border)]">
      <h1 class="text-3xl font-bold text-[var(--color-text-heading)] dark:text-[var(--color-dark-text-heading)] mb-6">
        搜索
      </h1>

      <form @submit.prevent="handleSearch" class="flex gap-3 max-w-lg">
        <div class="flex-1 relative">
          <Search :size="16" class="absolute left-3 top-1/2 -translate-y-1/2 text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)]" />
          <input
            v-model="keyword"
            type="text"
            placeholder="输入关键词搜索文章..."
            class="w-full pl-9 pr-4 py-2.5 text-sm bg-[var(--color-bg-card)] dark:bg-[var(--color-dark-bg-card)] border border-[var(--color-border)] dark:border-[var(--color-dark-border)] rounded-lg text-[var(--color-text-body)] dark:text-[var(--color-dark-text-body)] placeholder:text-[var(--color-text-muted)] dark:placeholder:text-[var(--color-dark-text-muted)] outline-none focus:border-[var(--color-accent)] dark:focus:border-[var(--color-dark-accent)] transition-colors"
          />
        </div>
        <button
          type="submit"
          class="px-5 py-2.5 text-sm font-medium bg-[var(--color-accent)] dark:bg-[var(--color-dark-accent)] text-[var(--color-bg-card)] dark:text-[var(--color-dark-bg-card)] rounded-lg hover:opacity-80 transition-opacity"
        >
          搜索
        </button>
      </form>
    </header>

    <div v-if="loading" class="space-y-10">
      <SkeletonCard v-for="i in 5" :key="i" :lines="2" />
    </div>

    <div v-else-if="searched && articles.length === 0" class="py-16 text-center">
      <p class="text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)]">
        未找到与 "{{ keyword }}" 相关的文章
      </p>
    </div>

    <div v-else-if="searched && articles.length > 0">
      <p class="text-sm text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)] mb-8">
        找到 {{ pagination?.total || articles.length }} 篇相关文章
      </p>

      <div class="space-y-10">
        <ArticleCard v-for="article in articles" :key="article.id" :article="article" />
      </div>

      <ArticlePagination
        v-if="pagination"
        :current="pagination.page"
        :total="pagination.totalPages"
        @change="handlePageChange"
      />
    </div>

    <div v-else class="py-16 text-center">
      <p class="text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)]">
        输入关键词开始搜索
      </p>
    </div>
  </div>
</template>
