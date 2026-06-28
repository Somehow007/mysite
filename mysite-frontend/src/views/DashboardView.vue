<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useHead } from '@unhead/vue'
import { useRouter } from 'vue-router'
import {
  FileText,
  Plus,
  Trash2,
  Edit,
  Eye,
  Search,
  SortAsc,
  SortDesc,
  Heart,
  Clock,
  X,
  CheckSquare,
  Square,
  MoreHorizontal,
  ChevronLeft,
  ChevronRight,
} from 'lucide-vue-next'
import { getArticles, deleteArticle, batchDeleteArticles } from '@/api/article'
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
const deletingIds = ref<Set<string>>(new Set())
const categories = ref<Category[]>([])
const collections = ref<Collection[]>([])

// 批量选择
const selectedIds = ref<Set<string>>(new Set())

// 筛选和排序参数
const keyword = ref('')
const searchType = ref<'title' | 'content' | 'author'>('title')
const publishedFilter = ref<number | undefined>(undefined)
const categoryFilter = ref('')
const collectionFilter = ref('')
const sortField = ref('createTime')
const sortOrder = ref('desc')

const totalArticles = computed(() => pagination.value?.total || 0)
const canModifyAll = computed(() =>
  articles.value.filter((a) => canModifyArticle(a.authorId)).map((a) => a.id)
)
const isAllSelected = computed(
  () => canModifyAll.value.length > 0 && canModifyAll.value.every((id) => selectedIds.value.has(id))
)
const hasSelection = computed(() => selectedIds.value.size > 0)

async function fetchCategories() {
  try {
    categories.value = await getCategories()
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
      authorId: !isDeveloper.value ? userStore.user?.id : undefined,
    }

    const res = await getArticles(params)

    if (isDeveloper.value) {
      articles.value = res.list
    } else {
      articles.value = res.list.filter(
        (a: ArticleListItem) => a.authorId === userStore.user?.id
      )
    }
    pagination.value = res.pagination
    // 清除当前页已不存在的选中项
    const currentPageIds = new Set(articles.value.map((a) => a.id))
    const nextSelected = new Set<string>()
    selectedIds.value.forEach((id) => {
      if (currentPageIds.has(id)) nextSelected.add(id)
    })
    selectedIds.value = nextSelected
  } catch {
    articles.value = []
  } finally {
    loading.value = false
  }
}

async function handleDelete(article: ArticleListItem) {
  if (!canModify(article)) return
  if (!confirm(`确定要删除「${article.title}」吗？`)) return

  const id = article.id
  deletingIds.value.add(id)

  // 乐观更新：立即从本地移除，避免等待后端响应导致页面卡顿
  const idx = articles.value.findIndex((a) => a.id === id)
  const removed = idx >= 0 ? articles.value.splice(idx, 1)[0] : null
  selectedIds.value.delete(id)
  if (pagination.value) {
    pagination.value = { ...pagination.value, total: Math.max(0, pagination.value.total - 1) }
  }

  try {
    await deleteArticle(id)
    toast.success('文章已删除')
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : '删除失败'
    toast.error(msg)
    // 失败时回滚
    if (removed && idx >= 0) {
      articles.value.splice(idx, 0, removed)
      if (pagination.value) {
        pagination.value = { ...pagination.value, total: pagination.value.total + 1 }
      }
    }
  } finally {
    deletingIds.value.delete(id)
  }
}

async function handleBatchDelete() {
  if (selectedIds.value.size === 0) return
  const count = selectedIds.value.size
  if (!confirm(`确定要删除选中的 ${count} 篇文章吗？此操作不可恢复。`)) return

  const idsToDelete = Array.from(selectedIds.value)
  deletingIds.value = new Set(idsToDelete)

  // 乐观更新
  const backup = articles.value.filter((a) => idsToDelete.includes(a.id))
  articles.value = articles.value.filter((a) => !idsToDelete.includes(a.id))
  if (pagination.value) {
    pagination.value = {
      ...pagination.value,
      total: Math.max(0, pagination.value.total - backup.length),
    }
  }
  selectedIds.value = new Set()

  try {
    await batchDeleteArticles(idsToDelete)
    toast.success(`已删除 ${backup.length} 篇文章`)
    // 后台静默刷新以确保分页数据准确
    if (articles.value.length === 0 && pagination.value && pagination.value.page > 1) {
      await fetchArticles(pagination.value.page - 1)
    }
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : '批量删除失败'
    toast.error(msg)
    // 回滚
    articles.value = [...backup, ...articles.value].sort(
      (a, b) =>
        new Date(b.createTime).getTime() - new Date(a.createTime).getTime()
    )
    if (pagination.value) {
      pagination.value = {
        ...pagination.value,
        total: pagination.value.total + backup.length,
      }
    }
  } finally {
    deletingIds.value = new Set()
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

function toggleSelect(id: string) {
  const next = new Set(selectedIds.value)
  if (next.has(id)) next.delete(id)
  else next.add(id)
  selectedIds.value = next
}

function toggleSelectAll() {
  if (isAllSelected.value) {
    selectedIds.value = new Set()
  } else {
    selectedIds.value = new Set(canModifyAll.value)
  }
}

function clearSelection() {
  selectedIds.value = new Set()
}

function formatDate(dateStr: string | undefined): string {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  const now = new Date()
  const diffMs = now.getTime() - date.getTime()
  const diffMin = Math.floor(diffMs / 60000)
  const diffHour = Math.floor(diffMs / 3600000)
  const diffDay = Math.floor(diffMs / 86400000)

  if (diffMin < 1) return '刚刚'
  if (diffMin < 60) return `${diffMin} 分钟前`
  if (diffHour < 24) return `${diffHour} 小时前`
  if (diffDay < 7) return `${diffDay} 天前`
  return date.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' })
}

onMounted(() => {
  fetchCategories()
  fetchCollections()
  fetchArticles()
})
</script>

<template>
  <div class="article-manage">
    <!-- 页头 -->
    <div class="page-header">
      <div>
        <h1 class="page-title">文章管理</h1>
        <p class="page-subtitle">共 {{ totalArticles }} 篇文章</p>
      </div>
      <button @click="router.push('/dashboard/posts/new')" class="btn-primary">
        <Plus :size="15" />
        写文章
      </button>
    </div>

    <!-- 筛选栏 -->
    <div class="filter-bar">
      <div class="filter-row">
        <div class="search-box">
          <Search :size="14" class="search-icon" />
          <input
            v-model="keyword"
            type="text"
            :placeholder="
              searchType === 'title'
                ? '搜索标题…'
                : searchType === 'content'
                ? '搜索内容…'
                : '搜索作者…'
            "
            class="search-input"
            @keyup.enter="handleSearch"
          />
          <button
            v-if="keyword"
            @click="keyword = ''; handleSearch()"
            class="search-clear"
            title="清空"
          >
            <X :size="12" />
          </button>
        </div>

        <div class="filter-group">
          <select v-model="searchType" class="filter-select" @change="handleSearch">
            <option value="title">标题</option>
            <option value="content">内容</option>
            <option value="author">作者</option>
          </select>

          <select v-model="publishedFilter" class="filter-select" @change="handleSearch">
            <option :value="undefined">全部状态</option>
            <option :value="1">已发布</option>
            <option :value="0">草稿</option>
          </select>

          <select v-model="categoryFilter" class="filter-select" @change="handleSearch">
            <option value="">全部分类</option>
            <option v-for="cat in categories" :key="cat.id" :value="cat.slug">
              {{ cat.name }}
            </option>
          </select>

          <select v-model="collectionFilter" class="filter-select" @change="handleSearch">
            <option value="">全部合集</option>
            <option v-for="col in collections" :key="col.id" :value="col.id">
              {{ col.title }}
            </option>
          </select>
        </div>
      </div>

      <div class="filter-row filter-row-secondary">
        <div class="sort-group">
          <select v-model="sortField" class="filter-select" @change="handleSearch">
            <option value="createTime">创建时间</option>
            <option value="viewCount">浏览量</option>
          </select>
          <button @click="toggleSortOrder" class="sort-dir-btn" title="切换排序方向">
            <SortAsc v-if="sortOrder === 'asc'" :size="13" />
            <SortDesc v-else :size="13" />
            {{ sortOrder === 'asc' ? '升序' : '降序' }}
          </button>
        </div>

        <div class="action-group">
          <button @click="handleSearch" class="btn-primary btn-sm">搜索</button>
          <button @click="handleReset" class="btn-secondary btn-sm">重置</button>
        </div>
      </div>
    </div>

    <!-- 批量操作栏 -->
    <Transition name="batch-bar">
      <div v-if="hasSelection" class="batch-bar">
        <div class="batch-info">
          <CheckSquare :size="14" />
          <span>已选择 <strong>{{ selectedIds.size }}</strong> 篇文章</span>
        </div>
        <div class="batch-actions">
          <button @click="clearSelection" class="batch-btn">
            <X :size="13" />
            取消选择
          </button>
          <button @click="handleBatchDelete" class="batch-btn batch-btn-danger">
            <Trash2 :size="13" />
            删除选中
          </button>
        </div>
      </div>
    </Transition>

    <!-- 内容区 -->
    <div v-if="loading" class="empty-state">
      <div class="loading-dots">
        <span></span><span></span><span></span>
      </div>
      <p class="empty-text">加载中…</p>
    </div>

    <div v-else-if="articles.length === 0" class="empty-state">
      <div class="empty-icon">
        <FileText :size="40" />
      </div>
      <p class="empty-title">还没有文章</p>
      <p class="empty-text">点击上方「写文章」开始你的第一篇</p>
      <button @click="router.push('/dashboard/posts/new')" class="btn-primary">
        <Plus :size="14" />
        写文章
      </button>
    </div>

    <div v-else class="table-wrap">
      <table class="article-table">
        <thead>
          <tr>
            <th class="col-check">
              <button
                class="check-btn"
                @click="toggleSelectAll"
                :title="isAllSelected ? '取消全选' : '全选当前页'"
              >
                <CheckSquare v-if="isAllSelected" :size="15" class="check-icon checked" />
                <Square v-else :size="15" class="check-icon" />
              </button>
            </th>
            <th class="col-title">标题</th>
            <th class="col-meta">分类</th>
            <th class="col-meta">状态</th>
            <th class="col-stats">数据</th>
            <th class="col-date">更新时间</th>
            <th class="col-actions">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="article in articles"
            :key="article.id"
            class="article-row"
            :class="{
              selected: selectedIds.has(article.id),
              deleting: deletingIds.has(article.id),
            }"
          >
            <td class="col-check">
              <button
                v-if="canModify(article)"
                class="check-btn"
                @click="toggleSelect(article.id)"
              >
                <CheckSquare
                  v-if="selectedIds.has(article.id)"
                  :size="15"
                  class="check-icon checked"
                />
                <Square v-else :size="15" class="check-icon" />
              </button>
            </td>
            <td class="col-title">
              <div class="title-cell">
                <div
                  v-if="article.coverImage"
                  class="cover-thumb"
                >
                  <img :src="article.coverImage" :alt="article.title" />
                </div>
                <div class="title-info">
                  <button
                    class="article-title"
                    @click="router.push(`/post/${article.id}`)"
                    :title="article.title"
                  >
                    {{ article.title }}
                  </button>
                  <p v-if="article.summary" class="article-summary">
                    {{ article.summary }}
                  </p>
                  <div class="title-meta-mobile">
                    <span v-if="article.categoryName" class="tag tag-category">
                      {{ article.categoryName }}
                    </span>
                    <span :class="article.published === 1 ? 'tag tag-published' : 'tag tag-draft'">
                      {{ article.published === 1 ? '已发布' : '草稿' }}
                    </span>
                  </div>
                </div>
              </div>
            </td>
            <td class="col-meta">
              <span v-if="article.categoryName" class="tag tag-category">
                {{ article.categoryName }}
              </span>
              <span v-else class="text-dim">—</span>
            </td>
            <td class="col-meta">
              <span :class="article.published === 1 ? 'tag tag-published' : 'tag tag-draft'">
                {{ article.published === 1 ? '已发布' : '草稿' }}
              </span>
            </td>
            <td class="col-stats">
              <div class="stats-row">
                <span class="stat" title="浏览量">
                  <Eye :size="11" />
                  {{ article.viewCount }}
                </span>
                <span class="stat" title="收藏">
                  <Heart :size="11" />
                  {{ article.favoriteCount }}
                </span>
                <span v-if="article.readingTime" class="stat" title="阅读时长">
                  <Clock :size="11" />
                  {{ article.readingTime }}m
                </span>
              </div>
            </td>
            <td class="col-date">
              <span class="date-text">{{ formatDate(article.updateTime) }}</span>
            </td>
            <td class="col-actions">
              <div class="action-btns">
                <button
                  @click="router.push(`/post/${article.id}`)"
                  class="icon-btn"
                  title="查看"
                >
                  <Eye :size="14" />
                </button>
                <button
                  v-if="canModify(article)"
                  @click="router.push(`/dashboard/posts/${article.id}/edit`)"
                  class="icon-btn"
                  title="编辑"
                >
                  <Edit :size="14" />
                </button>
                <button
                  v-if="canModify(article)"
                  @click="handleDelete(article)"
                  :disabled="deletingIds.has(article.id)"
                  class="icon-btn icon-btn-danger"
                  title="删除"
                >
                  <Trash2 :size="14" />
                </button>
                <span
                  v-if="!canModify(article)"
                  class="no-perm"
                >
                  <MoreHorizontal :size="14" />
                </span>
              </div>
            </td>
          </tr>
        </tbody>
      </table>

      <!-- 分页 -->
      <div
        v-if="pagination && pagination.totalPages > 1"
        class="pagination"
      >
        <span class="pagination-info">
          第 {{ pagination.page }} / {{ pagination.totalPages }} 页
        </span>
        <div class="pagination-btns">
          <button
            :disabled="pagination.page === 1"
            @click="fetchArticles(pagination.page - 1)"
            class="page-btn"
          >
            <ChevronLeft :size="14" />
          </button>
          <template v-for="page in pagination.totalPages" :key="page">
            <button
              v-if="
                page === 1 ||
                page === pagination.totalPages ||
                Math.abs(page - pagination.page) <= 1
              "
              @click="fetchArticles(page)"
              class="page-btn"
              :class="{ active: page === pagination.page }"
            >
              {{ page }}
            </button>
            <span
              v-else-if="
                page === pagination.page - 2 || page === pagination.page + 2
              "
              class="page-ellipsis"
            >
              …
            </span>
          </template>
          <button
            :disabled="pagination.page === pagination.totalPages"
            @click="fetchArticles(pagination.page + 1)"
            class="page-btn"
          >
            <ChevronRight :size="14" />
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.article-manage {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

/* 页头 */
.page-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 1rem;
}
.page-title {
  font-size: 1.5rem;
  font-weight: 600;
  color: var(--text-primary);
  line-height: 1.2;
  letter-spacing: -0.01em;
}
.page-subtitle {
  margin-top: 0.25rem;
  font-size: 0.8125rem;
  color: var(--text-muted);
}

/* 筛选栏 */
.filter-bar {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  padding: 1rem;
  border-radius: 0.75rem;
  border: 1px solid var(--border);
  background: var(--surface-primary);
}
.filter-row {
  display: flex;
  align-items: center;
  gap: 0.625rem;
  flex-wrap: wrap;
}
.filter-row-secondary {
  padding-top: 0.75rem;
  border-top: 1px dashed var(--border);
  justify-content: space-between;
}
.search-box {
  position: relative;
  flex: 1;
  min-width: 200px;
  max-width: 320px;
}
.search-icon {
  position: absolute;
  left: 0.75rem;
  top: 50%;
  transform: translateY(-50%);
  color: var(--text-muted);
  pointer-events: none;
}
.search-input {
  width: 100%;
  padding: 0.5rem 2rem 0.5rem 2.25rem;
  font-size: 0.8125rem;
  border-radius: 0.5rem;
  border: 1px solid var(--border);
  background: var(--bg-secondary);
  color: var(--text-primary);
  transition: border-color 0.15s, box-shadow 0.15s;
}
.search-input::placeholder {
  color: var(--text-muted);
}
.search-input:focus {
  outline: none;
  border-color: var(--accent);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--accent) 12%, transparent);
}
.search-clear {
  position: absolute;
  right: 0.5rem;
  top: 50%;
  transform: translateY(-50%);
  padding: 0.25rem;
  border-radius: 0.25rem;
  color: var(--text-muted);
  background: transparent;
  border: none;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
}
.search-clear:hover {
  color: var(--text-primary);
  background: var(--surface-secondary);
}
.filter-group {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  flex-wrap: wrap;
}
.filter-select {
  padding: 0.5rem 0.75rem;
  font-size: 0.8125rem;
  border-radius: 0.5rem;
  border: 1px solid var(--border);
  background: var(--bg-secondary);
  color: var(--text-primary);
  cursor: pointer;
  transition: border-color 0.15s;
}
.filter-select:focus {
  outline: none;
  border-color: var(--accent);
}
.sort-group {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}
.sort-dir-btn {
  display: inline-flex;
  align-items: center;
  gap: 0.375rem;
  padding: 0.5rem 0.75rem;
  font-size: 0.8125rem;
  border-radius: 0.5rem;
  border: 1px solid var(--border);
  background: var(--bg-secondary);
  color: var(--text-primary);
  cursor: pointer;
  transition: background 0.15s;
}
.sort-dir-btn:hover {
  background: var(--surface-secondary);
}
.action-group {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}
.btn-sm {
  padding: 0.4375rem 0.875rem;
  font-size: 0.8125rem;
}

/* 批量操作栏 */
.batch-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  padding: 0.625rem 1rem;
  border-radius: 0.625rem;
  border: 1px solid color-mix(in srgb, var(--accent) 30%, transparent);
  background: color-mix(in srgb, var(--accent) 6%, var(--surface-primary));
}
.batch-info {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.8125rem;
  color: var(--text-secondary);
}
.batch-info strong {
  color: var(--accent);
  font-weight: 600;
}
.batch-actions {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}
.batch-btn {
  display: inline-flex;
  align-items: center;
  gap: 0.375rem;
  padding: 0.375rem 0.75rem;
  font-size: 0.8125rem;
  border-radius: 0.375rem;
  border: 1px solid var(--border);
  background: var(--surface-primary);
  color: var(--text-secondary);
  cursor: pointer;
  transition: all 0.15s;
}
.batch-btn:hover {
  background: var(--surface-secondary);
  color: var(--text-primary);
}
.batch-btn-danger {
  border-color: color-mix(in srgb, #ef4444 40%, transparent);
  color: #dc2626;
}
.batch-btn-danger:hover {
  background: #fef2f2;
  color: #b91c1c;
}
:root.dark .batch-btn-danger:hover,
.batch-btn-danger:hover {
  background: color-mix(in srgb, #ef4444 10%, transparent);
}
.batch-bar-enter-active,
.batch-bar-leave-active {
  transition: all 0.2s ease;
}
.batch-bar-enter-from,
.batch-bar-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}

/* 空状态 */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 4rem 1rem;
  text-align: center;
}
.empty-icon {
  width: 4.5rem;
  height: 4.5rem;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--surface-secondary);
  color: var(--text-muted);
  margin-bottom: 1rem;
}
.empty-title {
  font-size: 0.9375rem;
  font-weight: 500;
  color: var(--text-primary);
  margin-bottom: 0.25rem;
}
.empty-text {
  font-size: 0.8125rem;
  color: var(--text-muted);
  margin-bottom: 1.25rem;
}
.loading-dots {
  display: flex;
  gap: 0.375rem;
  margin-bottom: 0.75rem;
}
.loading-dots span {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--text-muted);
  animation: dotPulse 1.2s infinite ease-in-out;
}
.loading-dots span:nth-child(2) { animation-delay: 0.15s; }
.loading-dots span:nth-child(3) { animation-delay: 0.3s; }
@keyframes dotPulse {
  0%, 80%, 100% { opacity: 0.3; transform: scale(0.8); }
  40% { opacity: 1; transform: scale(1); }
}

/* 表格 */
.table-wrap {
  border-radius: 0.75rem;
  border: 1px solid var(--border);
  background: var(--surface-primary);
  overflow: hidden;
}
.article-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.8125rem;
}
.article-table thead {
  background: var(--bg-code, var(--surface-secondary));
  border-bottom: 1px solid var(--border);
}
.article-table th {
  padding: 0.625rem 0.875rem;
  font-weight: 500;
  font-size: 0.75rem;
  color: var(--text-muted);
  text-align: left;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  white-space: nowrap;
}
.article-table td {
  padding: 0.75rem 0.875rem;
  vertical-align: middle;
}
.col-check {
  width: 2.5rem;
  text-align: center;
}
.col-title {
  min-width: 200px;
}
.col-meta {
  width: 6.5rem;
}
.col-stats {
  width: 9rem;
}
.col-date {
  width: 6.5rem;
}
.col-actions {
  width: 7.5rem;
}
.check-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0.25rem;
  border: none;
  background: transparent;
  cursor: pointer;
  border-radius: 0.25rem;
  color: var(--text-muted);
  transition: color 0.15s;
}
.check-btn:hover {
  color: var(--accent);
}
.check-icon {
  transition: color 0.15s;
}
.check-icon.checked {
  color: var(--accent);
}

/* 行 */
.article-row {
  border-bottom: 1px solid var(--border);
  transition: background 0.12s;
}
.article-row:last-child {
  border-bottom: none;
}
.article-row:hover {
  background: color-mix(in srgb, var(--accent) 3%, transparent);
}
.article-row.selected {
  background: color-mix(in srgb, var(--accent) 6%, transparent);
}
.article-row.deleting {
  opacity: 0.5;
}

/* 标题单元格 */
.title-cell {
  display: flex;
  align-items: flex-start;
  gap: 0.75rem;
}
.cover-thumb {
  flex-shrink: 0;
  width: 3.5rem;
  height: 2.375rem;
  border-radius: 0.375rem;
  overflow: hidden;
  background: var(--surface-secondary);
}
.cover-thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.title-info {
  flex: 1;
  min-width: 0;
}
.article-title {
  display: block;
  font-weight: 500;
  color: var(--text-primary);
  text-align: left;
  background: none;
  border: none;
  padding: 0;
  cursor: pointer;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 100%;
  line-height: 1.4;
  transition: color 0.15s;
}
.article-title:hover {
  color: var(--accent);
}
.article-summary {
  margin-top: 0.125rem;
  font-size: 0.75rem;
  color: var(--text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  line-height: 1.4;
}
.title-meta-mobile {
  display: none;
  margin-top: 0.375rem;
  gap: 0.375rem;
}

/* 标签 */
.tag {
  display: inline-flex;
  align-items: center;
  padding: 0.1875rem 0.5rem;
  font-size: 0.6875rem;
  font-weight: 500;
  border-radius: 0.25rem;
  white-space: nowrap;
}
.tag-category {
  background: color-mix(in srgb, var(--accent) 10%, transparent);
  color: var(--accent);
}
.tag-published {
  background: color-mix(in srgb, #10b981 10%, transparent);
  color: #059669;
}
.tag-draft {
  background: color-mix(in srgb, #f59e0b 10%, transparent);
  color: #d97706;
}
.text-dim {
  color: var(--text-muted);
}

/* 数据列 */
.stats-row {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}
.stat {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  font-size: 0.75rem;
  color: var(--text-muted);
  white-space: nowrap;
}
.date-text {
  font-size: 0.75rem;
  color: var(--text-muted);
  white-space: nowrap;
}

/* 操作按钮 */
.action-btns {
  display: flex;
  align-items: center;
  gap: 0.125rem;
}
.icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 1.75rem;
  height: 1.75rem;
  padding: 0;
  border: none;
  background: transparent;
  border-radius: 0.375rem;
  color: var(--text-muted);
  cursor: pointer;
  transition: all 0.15s;
}
.icon-btn:hover {
  background: color-mix(in srgb, var(--accent) 10%, transparent);
  color: var(--accent);
}
.icon-btn-danger:hover {
  background: color-mix(in srgb, #ef4444 10%, transparent);
  color: #dc2626;
}
.icon-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
.no-perm {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 1.75rem;
  height: 1.75rem;
  color: var(--text-muted);
  opacity: 0.5;
}

/* 分页 */
.pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.75rem 1rem;
  border-top: 1px solid var(--border);
  background: var(--bg-code, var(--surface-secondary));
}
.pagination-info {
  font-size: 0.75rem;
  color: var(--text-muted);
}
.pagination-btns {
  display: flex;
  align-items: center;
  gap: 0.25rem;
}
.page-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 1.75rem;
  height: 1.75rem;
  padding: 0 0.375rem;
  font-size: 0.8125rem;
  border: 1px solid transparent;
  border-radius: 0.375rem;
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all 0.15s;
}
.page-btn:hover:not(:disabled):not(.active) {
  background: var(--surface-secondary);
  color: var(--text-primary);
}
.page-btn.active {
  background: var(--accent);
  color: #fff;
  font-weight: 500;
}
.page-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
.page-ellipsis {
  padding: 0 0.25rem;
  font-size: 0.8125rem;
  color: var(--text-muted);
}

/* 响应式 */
@media (max-width: 768px) {
  .filter-bar {
    padding: 0.75rem;
  }
  .search-box {
    max-width: none;
  }
  .col-meta,
  .col-stats,
  .col-date {
    display: none;
  }
  .title-meta-mobile {
    display: flex;
  }
  .article-table th,
  .article-table td {
    padding: 0.625rem 0.625rem;
  }
  .batch-bar {
    flex-direction: column;
    align-items: flex-start;
  }
  .pagination {
    flex-direction: column;
    gap: 0.5rem;
    align-items: center;
  }
}
</style>
