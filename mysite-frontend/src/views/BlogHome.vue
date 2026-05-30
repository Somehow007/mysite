<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getArticles } from '@/api/article'
import { getCategories } from '@/api/category'
import { useSiteStore } from '@/stores/site'
import { useUserStore } from '@/stores/user'
import { useFavorite } from '@/composables/useFavorite'
import { ArrowUpDown, ArrowUp, ArrowDown, Tag, X, Loader2 } from 'lucide-vue-next'
import ArticleList from '@/components/article/ArticleList.vue'
import type { ArticleListItem, Pagination, Category } from '@/types'

type SortField = 'createTime' | 'viewCount'
type SortOrder = 'desc' | 'asc'

const route = useRoute()
const router = useRouter()
const siteStore = useSiteStore()
const userStore = useUserStore()
const { setFavoriteStatus, setFavoriteCount } = useFavorite()

const articles = ref<ArticleListItem[]>([])
const pagination = ref<Pagination | null>(null)
const loading = ref(false)
const categories = ref<Category[]>([])
const categoriesLoading = ref(false)

const sortField = ref<SortField>('createTime')
const sortOrder = ref<SortOrder>('desc')
const selectedCategorySlug = ref<string | null>(null)

const sortOptions: { field: SortField; order: SortOrder; label: string; icon: typeof ArrowUpDown }[] = [
  { field: 'createTime', order: 'desc', label: '最新', icon: ArrowDown },
  { field: 'createTime', order: 'asc', label: '最早', icon: ArrowUp },
  { field: 'viewCount', order: 'desc', label: '浏览最多', icon: ArrowDown },
  { field: 'viewCount', order: 'asc', label: '浏览最少', icon: ArrowUp },
]

const selectedCategoryName = computed(() => {
  if (!selectedCategorySlug.value) return null
  return categories.value.find(c => c.slug === selectedCategorySlug.value)?.name ?? null
})

function isSortActive(field: SortField, order: SortOrder) {
  return sortField.value === field && sortOrder.value === order
}

function setSort(field: SortField, order: SortOrder) {
  sortField.value = field
  sortOrder.value = order
  fetchArticles(1)
}

function selectCategory(slug: string | null) {
  selectedCategorySlug.value = slug
  fetchArticles(1)
}

function clearCategory() {
  selectedCategorySlug.value = null
  fetchArticles(1)
}

async function fetchArticles(page = 1) {
  loading.value = true
  try {
    const params: { page: number; size: number; categorySlug?: string; sortField?: string; sortOrder?: string } = { page, size: 10 }
    if (selectedCategorySlug.value) {
      params.categorySlug = selectedCategorySlug.value
    }
    if (sortField.value) {
      params.sortField = sortField.value
    }
    if (sortOrder.value) {
      params.sortOrder = sortOrder.value
    }
    const res = await getArticles(params)
    articles.value = res.list
    pagination.value = res.pagination
  } catch {
    articles.value = []
    pagination.value = null
  } finally {
    loading.value = false
  }
}

async function fetchCategories() {
  categoriesLoading.value = true
  try {
    categories.value = await getCategories()
  } catch {
    categories.value = []
  } finally {
    categoriesLoading.value = false
  }
}

function handlePageChange(page: number) {
  router.push(page === 1 ? '/' : `/page/${page}`)
}

function handleFavoriteToggle(articleId: string, favorited: boolean) {
  const article = articles.value.find(a => a.id === articleId)
  if (article) {
    article.isFavorited = favorited
    if (favorited) {
      article.favoriteCount += 1
    } else {
      article.favoriteCount = Math.max(0, article.favoriteCount - 1)
    }
    setFavoriteStatus(articleId, favorited)
    setFavoriteCount(articleId, article.favoriteCount)
  }
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
  fetchCategories()
})
</script>

<template>
  <div>
    <section class="mb-12 pb-10 border-b border-border">
      <h1 class="text-4xl font-bold text-text-primary mb-3 tracking-tight">
        {{ siteStore.site.title }}
      </h1>
      <p class="text-lg text-text-muted leading-relaxed max-w-xl">
        {{ siteStore.site.description }}
      </p>
    </section>

    <section class="mb-8 space-y-4">
      <div class="flex flex-wrap items-center gap-2">
        <span class="text-xs font-medium text-text-muted mr-1 flex items-center gap-1">
          <ArrowUpDown :size="12" />
          排序
        </span>
        <button
          v-for="opt in sortOptions"
          :key="`${opt.field}-${opt.order}`"
          @click="setSort(opt.field, opt.order)"
          class="inline-flex items-center gap-1 px-3 py-1.5 rounded-full text-xs font-medium transition-all duration-200"
          :class="isSortActive(opt.field, opt.order)
            ? 'bg-accent text-text-inverse shadow-sm'
            : 'bg-bg-code text-text-muted hover:text-accent hover:bg-accent-subtle'"
        >
          <component :is="opt.icon" :size="10" />
          {{ opt.label }}
        </button>
      </div>

      <div class="flex flex-wrap items-center gap-2">
        <span class="text-xs font-medium text-text-muted mr-1 flex items-center gap-1">
          <Tag :size="12" />
          分类
        </span>
        <Loader2 v-if="categoriesLoading" :size="14" class="animate-spin text-text-muted" />
        <template v-else>
          <button
            @click="selectCategory(null)"
            class="inline-flex items-center gap-1 px-3 py-1.5 rounded-full text-xs font-medium transition-all duration-200"
            :class="!selectedCategorySlug
              ? 'bg-accent text-text-inverse shadow-sm'
              : 'bg-bg-code text-text-muted hover:text-accent hover:bg-accent-subtle'"
          >
            全部
          </button>
          <button
            v-for="cat in categories"
            :key="cat.slug"
            @click="selectCategory(cat.slug)"
            class="inline-flex items-center gap-1 px-3 py-1.5 rounded-full text-xs font-medium transition-all duration-200"
            :class="selectedCategorySlug === cat.slug
              ? 'bg-accent text-text-inverse shadow-sm'
              : 'bg-bg-code text-text-muted hover:text-accent hover:bg-accent-subtle'"
          >
            {{ cat.name }}
            <span v-if="cat.articleCount" class="opacity-60">{{ cat.articleCount }}</span>
          </button>
        </template>
      </div>

      <div
        v-if="selectedCategoryName"
        class="flex items-center gap-2 text-xs text-text-muted"
      >
        <span>当前筛选：</span>
        <span class="inline-flex items-center gap-1 px-2 py-0.5 rounded-md bg-accent-subtle text-accent font-medium">
          {{ selectedCategoryName }}
        </span>
        <button
          @click="clearCategory"
          class="inline-flex items-center justify-center w-4 h-4 rounded-full hover:bg-border transition-colors"
        >
          <X :size="10" />
        </button>
      </div>
    </section>

    <ArticleList
      :articles="articles"
      :pagination="pagination"
      :loading="loading"
      :skeleton-count="5"
      :show-favorite="userStore.isLoggedIn"
      @page-change="handlePageChange"
      @favorite-toggle="handleFavoriteToggle"
    />
  </div>
</template>
