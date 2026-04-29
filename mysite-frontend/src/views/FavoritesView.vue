<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useHead } from '@unhead/vue'
import { Heart, Search, X } from 'lucide-vue-next'
import { getFavoriteArticles } from '@/api/article'
import { useFavorite } from '@/composables/useFavorite'
import ArticleList from '@/components/article/ArticleList.vue'
import type { ArticleListItem, Pagination } from '@/types'

const router = useRouter()
const { setFavoriteStatus } = useFavorite()

const articles = ref<ArticleListItem[]>([])
const pagination = ref<Pagination | null>(null)
const loading = ref(false)
const searchKeyword = ref('')
const searchInput = ref('')

useHead(() => ({
  title: '我的收藏 - MySite',
  meta: [
    { name: 'description', content: '我收藏的文章列表' },
  ],
}))

async function fetchFavorites(page = 1) {
  loading.value = true
  try {
    const res = await getFavoriteArticles({
      page,
      size: 10,
      keyword: searchKeyword.value || undefined,
    })
    articles.value = res.list
    pagination.value = res.pagination
    for (const article of res.list) {
      if (article.isFavorited !== undefined) {
        setFavoriteStatus(article.id, article.isFavorited)
      }
    }
  } catch {
    articles.value = []
    pagination.value = null
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  searchKeyword.value = searchInput.value.trim()
  fetchFavorites(1)
}

function clearSearch() {
  searchInput.value = ''
  searchKeyword.value = ''
  fetchFavorites(1)
}

function handlePageChange(page: number) {
  fetchFavorites(page)
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

function handleFavoriteToggle(articleId: string, favorited: boolean) {
  if (!favorited) {
    articles.value = articles.value.filter(a => a.id !== articleId)
    if (pagination.value) {
      pagination.value.total -= 1
      pagination.value.totalPages = Math.max(1, Math.ceil(pagination.value.total / pagination.value.size))
    }
  }
}

onMounted(() => {
  fetchFavorites(1)
})
</script>

<template>
  <div>
    <section class="mb-10 pb-8 border-b border-[var(--color-border)] dark:border-[var(--color-dark-border)]">
      <div class="flex items-center gap-3 mb-4">
        <Heart :size="28" fill="currentColor" stroke-width="0" class="text-red-500 dark:text-red-400" />
        <h1 class="text-3xl sm:text-4xl font-bold text-[var(--color-text-heading)] dark:text-[var(--color-dark-text-heading)] tracking-tight">
          我的收藏
        </h1>
      </div>
      <p class="text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)]">
        你收藏的所有文章都在这里
      </p>
    </section>

    <section class="mb-8">
      <form @submit.prevent="handleSearch" class="flex gap-2 max-w-md">
        <div class="relative flex-1">
          <Search :size="16" class="absolute left-3 top-1/2 -translate-y-1/2 text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)]" />
          <input
            v-model="searchInput"
            type="text"
            placeholder="搜索收藏的文章..."
            class="input-base pl-9 pr-9"
          />
          <button
            v-if="searchInput"
            type="button"
            @click="clearSearch"
            class="absolute right-3 top-1/2 -translate-y-1/2 text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)] hover:text-[var(--color-text-heading)] dark:hover:text-[var(--color-dark-text-heading)] transition-colors"
          >
            <X :size="14" />
          </button>
        </div>
        <button
          type="submit"
          class="btn-primary"
        >
          搜索
        </button>
      </form>

      <div
        v-if="searchKeyword"
        class="flex items-center gap-2 mt-3 text-xs text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)]"
      >
        <span>搜索：</span>
        <span class="inline-flex items-center gap-1 px-2 py-0.5 rounded-md bg-[var(--color-accent-light)] dark:bg-[var(--color-dark-accent-light)] text-[var(--color-accent)] dark:text-[var(--color-dark-accent)] font-medium">
          {{ searchKeyword }}
        </span>
        <button
          @click="clearSearch"
          class="inline-flex items-center justify-center w-4 h-4 rounded-full hover:bg-[var(--color-border)] dark:hover:bg-[var(--color-dark-border)] transition-colors"
        >
          <X :size="10" />
        </button>
      </div>
    </section>

    <div v-if="!loading && articles.length === 0 && !searchKeyword" class="py-20 text-center">
      <Heart :size="48" class="mx-auto mb-4 text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)] opacity-30" />
      <p class="text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)] mb-2">还没有收藏任何文章</p>
      <p class="text-sm text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)] opacity-60">
        浏览文章时点击 ❤️ 即可收藏
      </p>
      <button
        @click="router.push('/')"
        class="mt-4 text-sm text-[var(--color-accent)] dark:text-[var(--color-dark-accent)] hover:opacity-80 transition-opacity font-medium"
      >
        去看看文章
      </button>
    </div>

    <div v-else-if="!loading && articles.length === 0 && searchKeyword" class="py-16 text-center">
      <Search :size="40" class="mx-auto mb-4 text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)] opacity-30" />
      <p class="text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)]">没有找到匹配「{{ searchKeyword }}」的收藏文章</p>
    </div>

    <ArticleList
      v-else
      :articles="articles"
      :pagination="pagination"
      :loading="loading"
      :skeleton-count="5"
      show-favorite
      @page-change="handlePageChange"
      @favorite-toggle="handleFavoriteToggle"
    />
  </div>
</template>
