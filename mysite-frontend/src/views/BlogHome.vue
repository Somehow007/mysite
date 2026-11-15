<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ChevronDown } from 'lucide-vue-next'
import { getArticles } from '@/api/article'
import { getCategories } from '@/api/category'
import { getCollections } from '@/api/collection'
import { useUserStore } from '@/stores/user'
import { useFavorite } from '@/composables/useFavorite'
import { useScrollReveal } from '@/composables/useScrollReveal'
import { Dropdown } from '@/components/ui'
import ArticleList from '@/components/article/ArticleList.vue'
import HomeMasthead from '@/components/home/HomeMasthead.vue'
import CategoryStrip from '@/components/home/CategoryStrip.vue'
import HomeLead from '@/components/home/HomeLead.vue'
import HomeAskPanel from '@/components/home/HomeAskPanel.vue'
import HomeSidebar from '@/components/home/HomeSidebar.vue'
import type { ArticleListItem, Pagination, Category, Collection } from '@/types'

type SortField = 'createTime' | 'viewCount'
type SortOrder = 'desc' | 'asc'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const { setFavoriteStatus, setFavoriteCount } = useFavorite()

const articles = ref<ArticleListItem[]>([])
const pagination = ref<Pagination | null>(null)
const loading = ref(false)
// 实际已加载的页码：/page/N 上选分类会 fetchArticles(1) 但 URL 不变，以它为准
const loadedPage = ref(route.params.page ? Number(route.params.page) : 1)
const categories = ref<Category[]>([])
const categoriesLoading = ref(false)
const collections = ref<Collection[]>([])
const leadArticle = ref<ArticleListItem | null>(null)
const popularArticles = ref<ArticleListItem[]>([])

const sortField = ref<SortField>('createTime')
const sortOrder = ref<SortOrder>('desc')
const selectedCategorySlug = ref<string | null>(null)

const sortOptions: { field: SortField; order: SortOrder; label: string }[] = [
  { field: 'createTime', order: 'desc', label: '最新' },
  { field: 'createTime', order: 'asc', label: '最早' },
  { field: 'viewCount', order: 'desc', label: '浏览最多' },
  { field: 'viewCount', order: 'asc', label: '浏览最少' },
]

const currentSortLabel = computed(
  () => sortOptions.find(o => o.field === sortField.value && o.order === sortOrder.value)?.label ?? '最新'
)

const sectionTitle = computed(() => {
  if (sortField.value === 'createTime') {
    return sortOrder.value === 'desc' ? '最近更新' : '最早发布'
  }
  return sortOrder.value === 'desc' ? '最多阅读' : '最少阅读'
})

const isFirstPage = computed(() => loadedPage.value === 1)
const showMasthead = computed(() => isFirstPage.value && !selectedCategorySlug.value)

// 头条与列表去重：头条固定取「最新一篇」（T14 置顶前），与下方排序无关
const displayArticles = computed(() => {
  if (!isFirstPage.value || !leadArticle.value) return articles.value
  const leadId = leadArticle.value.id
  return articles.value.filter(a => a.id !== leadId)
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

async function fetchArticles(page = 1) {
  loading.value = true
  loadedPage.value = page
  if (page === 1) {
    fetchLead()
  } else {
    leadArticle.value = null
  }
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

async function fetchLead() {
  try {
    const res = await getArticles({
      page: 1,
      size: 1,
      sortField: 'createTime',
      sortOrder: 'desc',
      categorySlug: selectedCategorySlug.value || undefined,
    })
    leadArticle.value = res.list[0] ?? null
  } catch {
    leadArticle.value = null
  }
}

async function fetchPopular() {
  try {
    const res = await getArticles({ page: 1, size: 4, sortField: 'viewCount', sortOrder: 'desc' })
    popularArticles.value = res.list
  } catch {
    popularArticles.value = []
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

async function fetchCollections() {
  try {
    const res = await getCollections({ page: 1, size: 5, sortBy: 'viewCount' })
    collections.value = res.list
  } catch {
    collections.value = []
  }
}

function handlePageChange(page: number) {
  const target = page === 1 ? '/' : `/page/${page}`
  // 同路径 push 不触发 route watch（如 /page/2 上选分类后点回第 2 页），直接拉取
  if (route.path === target) {
    fetchArticles(page)
    window.scrollTo({ top: 0, behavior: 'smooth' })
    return
  }
  router.push(target)
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

// Enable scroll-reveal animations for cards
useScrollReveal()

onMounted(() => {
  const page = route.params.page ? Number(route.params.page) : 1
  fetchArticles(page)
  fetchCategories()
  fetchCollections()
  fetchPopular()
})
</script>

<template>
  <div>
    <HomeMasthead v-if="showMasthead" :total="pagination?.total ?? 0" />

    <CategoryStrip
      :categories="categories"
      :selected-slug="selectedCategorySlug"
      :total-count="selectedCategorySlug ? null : (pagination?.total ?? null)"
      :loading="categoriesLoading"
      @select="selectCategory"
    />

    <HomeLead
      v-if="isFirstPage && leadArticle"
      :lead="leadArticle"
      :popular="popularArticles"
    />

    <HomeAskPanel v-if="isFirstPage" />

    <div class="mt-12 lg:mt-14 lg:grid lg:grid-cols-[minmax(0,1fr)_300px] lg:gap-14">
      <section class="min-w-0">
        <div class="flex items-baseline justify-between gap-4 border-b-2 border-b-border-strong pb-2">
          <h2 class="text-[12px] font-semibold uppercase tracking-[0.16em] text-text-secondary">
            {{ sectionTitle }}
          </h2>
          <Dropdown trigger="click" placement="bottom-end" :spacing="6">
            <template #trigger="{ open }">
              <button
                type="button"
                class="inline-flex items-center gap-1 text-[13px] font-medium text-text-muted transition-colors hover:text-text-primary"
                :aria-expanded="open"
              >
                {{ currentSortLabel }}
                <ChevronDown :size="13" class="transition-transform duration-200" :class="open && 'rotate-180'" />
              </button>
            </template>
            <template #default="{ close }">
              <div class="paper-card w-36 rounded-xl border border-border bg-bg-elevated py-1.5">
                <button
                  v-for="opt in sortOptions"
                  :key="`${opt.field}-${opt.order}`"
                  type="button"
                  class="flex w-full items-center px-3.5 py-2 text-left text-sm transition-colors duration-150"
                  :class="isSortActive(opt.field, opt.order)
                    ? 'font-semibold text-accent'
                    : 'text-text-secondary hover:bg-bg-code hover:text-text-primary'"
                  @click="setSort(opt.field, opt.order); close()"
                >
                  {{ opt.label }}
                </button>
              </div>
            </template>
          </Dropdown>
        </div>

        <ArticleList
          :articles="displayArticles"
          :pagination="pagination"
          :loading="loading"
          :skeleton-count="5"
          :show-favorite="userStore.isLoggedIn"
          @page-change="handlePageChange"
          @favorite-toggle="handleFavoriteToggle"
        />
      </section>

      <HomeSidebar :collections="collections" />
    </div>
  </div>
</template>
