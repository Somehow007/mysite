<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useHead } from '@unhead/vue'
import { useRouter } from 'vue-router'
import { FileText, Plus, Trash2, Edit, Eye, Search, SortAsc, SortDesc, Heart, Clock } from 'lucide-vue-next'
import { getArticles, deleteArticle } from '@/api/article'
import { getCategories } from '@/api/category'
import { getCollections } from '@/api/collection'
import { useUserStore } from '@/stores/user'
import { usePermission } from '@/composables/usePermission'
import { useToast } from '@/composables/useToast'
import type { ArticleListItem, Pagination, Category, Collection } from '@/types'

useHead(() => ({
  title: '文章管理 - MySite',
}))

const router = useRouter()
const userStore = useUserStore()
const { isDeveloper, canModifyArticle } = usePermission()
const toast = useToast()

const articles = ref<ArticleListItem[]>([])
const pagination = ref<Pagination | null>(null)
const loading = ref(false)
const deleting = ref<string | null>(null)
const categories = ref<Category[]>([])
const collections = ref<Collection[]>([])

// 筛选和排序参数
const keyword = ref('')
const searchType = ref<'title' | 'content' | 'author'>('title')
const publishedFilter = ref<number | undefined>(undefined)
const categoryFilter = ref('')
const collectionFilter = ref('')
const sortField = ref('createTime')
const sortOrder = ref('desc')

// 计算文章总数
const totalArticles = computed(() => pagination.value?.total || 0)

async function fetchCategories() {
  try {
    const res = await getCategories()
    categories.value = res
  } catch (error) {
    console.error('获取分类列表失败:', error)
  }
}

async function fetchCollections() {
  try {
    const res = await getCollections({ size: 100 })
    collections.value = res.list
  } catch (error) {
    console.error('获取合集列表失败:', error)
  }
}

async function fetchArticles(page = 1) {
  loading.value = true
  try {
    const params = {
      page,
      size: 20,
      keyword: keyword.value || undefined,
      searchType: searchType.value,
      categorySlug: categoryFilter.value || undefined,
      collectionId: collectionFilter.value || undefined,
      published: publishedFilter.value,
      sortField: sortField.value,
      sortOrder: sortOrder.value,
      // 如果不是开发者，传递当前用户ID作为作者筛选
      authorId: !isDeveloper.value ? userStore.user?.id : undefined,
    }

    const res = await getArticles(params)

    // 如果不是开发者，只显示自己的文章（后端已经通过authorId筛选）
    if (isDeveloper.value) {
      articles.value = res.list
    } else {
      articles.value = res.list.filter(
        (a: ArticleListItem) => a.authorId === userStore.user?.id
      )
    }
    pagination.value = res.pagination
  } catch {
    articles.value = []
  } finally {
    loading.value = false
  }
}

async function handleDelete(id: string) {
  if (!confirm('确定要删除这篇文章吗？')) return
  deleting.value = id
  try {
    await deleteArticle(id)
    toast.success('文章已删除')
    await fetchArticles(pagination.value?.page || 1)
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : '删除失败'
    toast.error(msg)
  } finally {
    deleting.value = null
  }
}

function handleSearch() {
  fetchArticles(1)
}

function handleReset() {
  keyword.value = ''
  searchType.value = 'title'
  publishedFilter.value = undefined
  categoryFilter.value = ''
  collectionFilter.value = ''
  sortField.value = 'createTime'
  sortOrder.value = 'desc'
  fetchArticles(1)
}

function toggleSortOrder() {
  sortOrder.value = sortOrder.value === 'desc' ? 'asc' : 'desc'
  fetchArticles(1)
}

function canModify(article: ArticleListItem): boolean {
  return canModifyArticle(article.authorId)
}

onMounted(() => {
  fetchCategories()
  fetchCollections()
  fetchArticles()
})
</script>

<template>
  <div>
    <div class="flex items-center justify-between mb-6">
      <h1 class="text-2xl font-semibold text-text-primary">
        文章管理
        <span class="text-sm font-normal text-text-muted ml-2">共 {{ totalArticles }} 篇</span>
      </h1>
      <button
        @click="router.push('/dashboard/posts/new')"
        class="btn-primary"
      >
        <Plus :size="14" />
        写文章
      </button>
    </div>

    <!-- 筛选栏 -->
    <div class="mb-6 p-4 rounded-xl glass glass-sm space-y-3">
      <div class="flex items-center gap-3 flex-wrap">
        <div class="relative flex-1 min-w-[200px]">
          <Search :size="14" class="absolute left-3 top-1/2 -translate-y-1/2 text-text-muted" />
          <input
            v-model="keyword"
            type="text"
            :placeholder="searchType === 'title' ? '搜索文章标题...' : searchType === 'content' ? '搜索文章内容...' : '搜索作者...'"
            class="w-full pl-9 pr-3 py-2 text-sm rounded-lg border border-border bg-surface-primary text-text-primary placeholder:text-text-muted focus:outline-none focus:ring-2 focus:ring-accent/50 focus:border-accent transition-colors"
            @keyup.enter="handleSearch"
          />
        </div>
        <select
          v-model="searchType"
          class="px-3 py-2 text-sm rounded-lg border border-border bg-surface-primary text-text-primary focus:outline-none focus:ring-2 focus:ring-accent/50 focus:border-accent transition-colors"
          @change="handleSearch"
        >
          <option value="title">按标题</option>
          <option value="content">按内容</option>
          <option value="author">按作者</option>
        </select>
        <select
          v-model="publishedFilter"
          class="px-3 py-2 text-sm rounded-lg border border-border bg-surface-primary text-text-primary focus:outline-none focus:ring-2 focus:ring-accent/50 focus:border-accent transition-colors"
          @change="handleSearch"
        >
          <option :value="undefined">全部状态</option>
          <option :value="1">已发布</option>
          <option :value="0">草稿</option>
        </select>
        <select
          v-model="categoryFilter"
          class="px-3 py-2 text-sm rounded-lg border border-border bg-surface-primary text-text-primary focus:outline-none focus:ring-2 focus:ring-accent/50 focus:border-accent transition-colors"
          @change="handleSearch"
        >
          <option value="">全部分类</option>
          <option v-for="cat in categories" :key="cat.id" :value="cat.slug">
            {{ cat.name }}
          </option>
        </select>
        <select
          v-model="collectionFilter"
          class="px-3 py-2 text-sm rounded-lg border border-border bg-surface-primary text-text-primary focus:outline-none focus:ring-2 focus:ring-accent/50 focus:border-accent transition-colors"
          @change="handleSearch"
        >
          <option value="">全部合集</option>
          <option v-for="col in collections" :key="col.id" :value="col.id">
            {{ col.title }}
          </option>
        </select>
      </div>

      <div class="flex items-center gap-3">
        <select
          v-model="sortField"
          class="px-3 py-2 text-sm rounded-lg border border-border bg-surface-primary text-text-primary focus:outline-none focus:ring-2 focus:ring-accent/50 focus:border-accent transition-colors"
          @change="handleSearch"
        >
          <option value="createTime">按创建时间</option>
          <option value="viewCount">按浏览量</option>
        </select>
        <button
          @click="toggleSortOrder"
          class="flex items-center gap-1 px-3 py-2 text-sm rounded-lg border border-border bg-surface-primary text-text-primary hover:bg-surface-secondary transition-colors"
          title="切换排序方向"
        >
          <SortAsc v-if="sortOrder === 'asc'" :size="14" />
          <SortDesc v-else :size="14" />
          {{ sortOrder === 'asc' ? '升序' : '降序' }}
        </button>
        <button
          @click="handleSearch"
          class="btn-primary text-sm"
        >
          搜索
        </button>
        <button
          @click="handleReset"
          class="btn-secondary text-sm"
        >
          重置
        </button>
      </div>
    </div>

    <div v-if="loading" class="py-16 text-center text-text-muted">
      加载中...
    </div>

    <div v-else-if="articles.length === 0" class="py-16 text-center">
      <FileText :size="48" class="mx-auto mb-4 text-text-muted" />
      <p class="text-text-muted mb-4">还没有文章，开始写第一篇吧</p>
      <button
        @click="router.push('/dashboard/posts/new')"
        class="btn-primary"
      >
        <Plus :size="14" />
        写文章
      </button>
    </div>

    <div v-else class="space-y-3">
      <div
        v-for="article in articles"
        :key="article.id"
        class="p-4 rounded-xl glass glass-sm glass-hover hover:border-accent/30 transition-all duration-200"
      >
        <div class="flex items-start gap-4">
          <!-- 封面图 -->
          <div v-if="article.coverImage" class="shrink-0 w-24 h-16 rounded-lg overflow-hidden bg-surface-secondary">
            <img
              :src="article.coverImage"
              :alt="article.title"
              class="w-full h-full object-cover"
            />
          </div>

          <!-- 文章信息 -->
          <div class="flex-1 min-w-0">
            <h3 class="text-sm font-medium text-text-primary truncate">
              {{ article.title }}
            </h3>
            <p v-if="article.summary" class="text-xs text-text-muted mt-1 line-clamp-2">
              {{ article.summary }}
            </p>
            <div class="flex items-center gap-3 mt-2 text-xs text-text-muted flex-wrap">
              <span v-if="article.published === 0" class="px-1.5 py-0.5 rounded bg-amber-50 text-amber-600">
                草稿
              </span>
              <span v-else class="px-1.5 py-0.5 rounded bg-green-50 text-green-600">
                已发布
              </span>
              <span v-if="article.categoryName" class="px-1.5 py-0.5 rounded bg-accent-subtle text-accent">
                {{ article.categoryName }}
              </span>
              <span v-if="article.authorName" class="inline-flex items-center gap-1">
                {{ article.authorName }}
              </span>
              <span class="inline-flex items-center gap-1">
                <Eye :size="10" />
                {{ article.viewCount }}
              </span>
              <span class="inline-flex items-center gap-1">
                <Heart :size="10" />
                {{ article.favoriteCount }}
              </span>
              <span v-if="article.readingTime" class="inline-flex items-center gap-1">
                <Clock :size="10" />
                {{ article.readingTime }}分钟
              </span>
              <span>{{ article.updateTime ? new Date(article.updateTime).toLocaleDateString('zh-CN') : '' }}</span>
            </div>
          </div>

          <!-- 操作按钮 -->
          <div class="flex items-center gap-1 ml-4 shrink-0">
            <button
              @click="router.push(`/post/${article.id}`)"
              class="p-2 rounded-lg text-text-muted hover:bg-accent-subtle hover:text-accent transition-all duration-200"
              title="查看"
            >
              <Eye :size="14" />
            </button>
            <button
              v-if="canModify(article)"
              @click="router.push(`/dashboard/posts/${article.id}/edit`)"
              class="p-2 rounded-lg text-text-muted hover:bg-accent-subtle hover:text-accent transition-all duration-200"
              title="编辑"
            >
              <Edit :size="14" />
            </button>
            <button
              v-if="canModify(article)"
              @click="handleDelete(article.id)"
              :disabled="deleting === article.id"
              class="p-2 rounded-lg text-text-muted hover:bg-red-50 hover:text-red-500 transition-all duration-200 disabled:opacity-50"
              title="删除"
            >
              <Trash2 :size="14" />
            </button>
          </div>
        </div>
      </div>

      <!-- 分页 -->
      <div v-if="pagination && pagination.totalPages > 1" class="flex items-center justify-center gap-2 mt-6">
        <button
          :disabled="pagination.page === 1"
          @click="fetchArticles(pagination.page - 1)"
          class="px-3 py-1.5 text-sm rounded-lg border border-border text-text-muted hover:bg-surface-secondary disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
        >
          上一页
        </button>
        <button
          v-for="page in pagination.totalPages"
          :key="page"
          @click="fetchArticles(page)"
          class="w-8 h-8 text-sm rounded-lg transition-colors"
          :class="page === pagination.page ? 'bg-accent text-white' : 'text-text-muted hover:bg-surface-secondary'"
        >
          {{ page }}
        </button>
        <button
          :disabled="pagination.page === pagination.totalPages"
          @click="fetchArticles(pagination.page + 1)"
          class="px-3 py-1.5 text-sm rounded-lg border border-border text-text-muted hover:bg-surface-secondary disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
        >
          下一页
        </button>
      </div>
    </div>
  </div>
</template>
