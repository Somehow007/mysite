<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useHead } from '@unhead/vue'
import { useRouter } from 'vue-router'
import {
  FileText, Plus, Trash2, Edit, Eye, EyeOff,
  Heart, Clock, MoreHorizontal, Lock, Globe, FolderPlus,
} from 'lucide-vue-next'
import { getArticles, deleteArticle, batchDeleteArticles, batchUpdateArticleVisibility } from '@/api/article'
import { getCategories } from '@/api/category'
import { getCollections, batchAddArticles } from '@/api/collection'
import { useUserStore } from '@/stores/user'
import { usePermission } from '@/composables/usePermission'
import { useToast } from '@/composables/useToast'
import { useConfirm } from '@/composables/useConfirm'
import { Pagination, PageHeader, SearchFilterBar, DataTable, Badge, Modal } from '@/components/ui'
import type { ArticleListItem, Pagination as PaginationType, Category, Collection } from '@/types'
import type { Column } from '@/components/ui/DataTable.vue'

useHead(() => ({
  title: '文章管理 - MySite',
}))

const router = useRouter()
const userStore = useUserStore()
const { isAdmin, canModifyArticle } = usePermission()
const toast = useToast()
const { confirm } = useConfirm()

const articles = ref<ArticleListItem[]>([])
const pagination = ref<PaginationType | null>(null)
const loading = ref(false)
const deletingIds = ref<Set<string>>(new Set())
const categories = ref<Category[]>([])
const collections = ref<Collection[]>([])

// 批量选择
const selectedIds = ref<Set<string>>(new Set())

// 批量加入合集弹窗
const collectionModalOpen = ref(false)
const batchCollectionId = ref('')
const batchAdding = ref(false)

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
      authorId: !isAdmin.value ? userStore.user?.id : undefined,
    }

    const res = await getArticles(params)

    if (isAdmin.value) {
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
  const ok = await confirm({ message: `确定要删除「${article.title}」吗？`, danger: true, confirmText: '删除' })
  if (!ok) return

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
  const ok = await confirm({ message: `确定要删除选中的 ${count} 篇文章吗？此操作不可恢复。`, danger: true, confirmText: '删除' })
  if (!ok) return

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

async function handleBatchVisibility(target: 0 | 1) {
  if (selectedIds.value.size === 0) return

  const selected = articles.value.filter((a) => selectedIds.value.has(a.id))

  // 前端预检：选中文章混合公开/隐藏状态时直接拒绝（后端同样会拒绝，双保险）
  const visSet = new Set(selected.map((a) => a.visibility ?? 0))
  if (visSet.size > 1) {
    toast.error('选中的文章包含公开和隐藏两种状态，无法批量操作')
    return
  }
  if (visSet.size === 1 && visSet.has(target)) {
    toast.info(target === 1 ? '选中的文章已全部处于隐藏状态' : '选中的文章已全部处于公开状态')
    return
  }

  const actionText = target === 1 ? '隐藏（仅自己可见）' : '公开'
  const ok = await confirm({
    message: `确定要将选中的 ${selected.length} 篇文章设为${actionText}吗？`,
    confirmText: '确定',
  })
  if (!ok) return

  // 乐观更新：记录旧值，立即切换本地状态
  const backup = new Map(selected.map((a) => [a.id, a.visibility]))
  articles.value = articles.value.map((a) =>
    selectedIds.value.has(a.id) ? { ...a, visibility: target } : a
  )

  try {
    await batchUpdateArticleVisibility(Array.from(selectedIds.value), target)
    toast.success(target === 1 ? `已隐藏 ${selected.length} 篇文章` : `已公开 ${selected.length} 篇文章`)
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : '批量修改可见性失败'
    toast.error(msg)
    // 失败时回滚
    articles.value = articles.value.map((a) =>
      backup.has(a.id) ? { ...a, visibility: backup.get(a.id) } : a
    )
  }
}

// 可选合集：管理员可选任意合集，其他用户只能选自己的（后端 checkCollectionOwnership 同样校验）
const availableCollections = computed(() =>
  isAdmin.value
    ? collections.value
    : collections.value.filter((c) => c.authorId === userStore.user?.id)
)

function openCollectionModal() {
  if (selectedIds.value.size === 0) return
  batchCollectionId.value = ''
  collectionModalOpen.value = true
}

async function confirmBatchAddToCollection() {
  if (!batchCollectionId.value) {
    toast.error('请先选择一个合集')
    return
  }
  const ids = Array.from(selectedIds.value)
  const collectionTitle =
    collections.value.find((c) => c.id === batchCollectionId.value)?.title ?? ''

  batchAdding.value = true
  try {
    await batchAddArticles(batchCollectionId.value, ids)
    toast.success(`已将 ${ids.length} 篇文章加入合集「${collectionTitle}」`)
    collectionModalOpen.value = false
    selectedIds.value = new Set()
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : '加入合集失败'
    toast.error(msg)
  } finally {
    batchAdding.value = false
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

const articleColumns: Column<ArticleListItem>[] = [
  { key: 'title', label: '标题' },
  { key: 'category', label: '分类', hideMobile: true, width: '110px' },
  { key: 'status', label: '状态', hideMobile: true, width: '90px' },
  { key: 'stats', label: '数据', hideMobile: true, width: '150px' },
  { key: 'updateTime', label: '更新时间', hideMobile: true, width: '110px' },
  { key: 'actions', label: '操作', width: '120px' },
]

const sortOptions = [
  { label: '创建时间', value: 'createTime' },
  { label: '浏览量', value: 'viewCount' },
]

const publishedFilterOptions = [
  { label: '全部状态', value: '' },
  { label: '已发布', value: '1' },
  { label: '草稿', value: '0' },
]

const searchTypeOptions = [
  { label: '标题', value: 'title' },
  { label: '内容', value: 'content' },
  { label: '作者', value: 'author' },
]

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
  <div class="flex flex-col gap-5">
    <!-- 页头 -->
    <PageHeader title="文章管理" :subtitle="`共 ${totalArticles} 篇文章`">
      <template #actions>
        <button @click="router.push('/dashboard/posts/new')" class="btn-primary">
          <Plus :size="15" />
          写文章
        </button>
      </template>
    </PageHeader>

    <!-- 筛选栏 -->
    <SearchFilterBar
      v-model="keyword"
      placeholder="搜索标题…"
      :sort-options="sortOptions"
      :sort-field="sortField"
      :sort-order="sortOrder"
      :filter-options="publishedFilterOptions"
      :filter-value="publishedFilter === undefined ? '' : String(publishedFilter)"
      class="mb-4"
      @update:sort-field="sortField = $event; handleSearch()"
      @update:sort-order="sortOrder = $event; handleSearch()"
      @update:filter-value="publishedFilter = $event === '' ? undefined : Number($event); handleSearch()"
      @search="handleSearch"
      @reset="handleReset"
    >
      <select v-model="searchType" class="px-3 py-2 text-sm rounded-lg border border-border bg-bg-secondary text-text-primary cursor-pointer focus:outline-none focus:border-accent transition-colors" @change="handleSearch">
        <option v-for="opt in searchTypeOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
      </select>
      <select v-if="categories.length > 0" v-model="categoryFilter" class="px-3 py-2 text-sm rounded-lg border border-border bg-bg-secondary text-text-primary cursor-pointer focus:outline-none focus:border-accent transition-colors" @change="handleSearch">
        <option value="">全部分类</option>
        <option v-for="cat in categories" :key="cat.id" :value="cat.slug">{{ cat.name }}</option>
      </select>
      <select v-if="collections.length > 0" v-model="collectionFilter" class="px-3 py-2 text-sm rounded-lg border border-border bg-bg-secondary text-text-primary cursor-pointer focus:outline-none focus:border-accent transition-colors" @change="handleSearch">
        <option value="">全部合集</option>
        <option v-for="col in collections" :key="col.id" :value="col.id">{{ col.title }}</option>
      </select>
    </SearchFilterBar>

    <!-- 批量操作栏（悬浮胶囊） -->
    <Transition name="batch-bar">
      <div v-if="hasSelection" class="flex justify-center mb-4">
        <div class="inline-flex items-center gap-3 px-4 py-2 rounded-full border border-accent/30 bg-bg-elevated shadow-md">
          <span class="text-[13px] font-semibold text-accent">已选 {{ selectedIds.size }} 项</span>
          <button @click="handleBatchVisibility(0)" class="inline-flex items-center gap-1.5 px-3 py-1.5 text-[12.5px] rounded-full bg-bg-secondary text-text-primary hover:bg-accent/10 hover:text-accent transition-colors">
            <Globe :size="12" />批量公开
          </button>
          <button @click="handleBatchVisibility(1)" class="inline-flex items-center gap-1.5 px-3 py-1.5 text-[12.5px] rounded-full bg-bg-secondary text-text-primary hover:bg-accent/10 hover:text-accent transition-colors">
            <EyeOff :size="12" />批量隐藏
          </button>
          <button @click="openCollectionModal" class="inline-flex items-center gap-1.5 px-3 py-1.5 text-[12.5px] rounded-full bg-bg-secondary text-text-primary hover:bg-accent/10 hover:text-accent transition-colors">
            <FolderPlus :size="12" />加入合集
          </button>
          <button @click="handleBatchDelete" class="inline-flex items-center gap-1.5 px-3 py-1.5 text-[12.5px] rounded-full bg-danger-subtle text-danger hover:bg-danger/20 transition-colors">
            <Trash2 :size="12" />批量删除
          </button>
          <button @click="clearSelection" class="px-2 py-1 text-[12.5px] text-text-muted hover:text-text-primary transition-colors">取消</button>
        </div>
      </div>
    </Transition>

    <!-- 加载状态 -->
    <div v-if="loading" class="flex flex-col items-center justify-center py-16 text-center">
      <div class="flex gap-1.5 mb-3">
        <span class="w-1.5 h-1.5 rounded-full bg-text-muted animate-[dotPulse_1.2s_infinite_ease-in-out]" />
        <span class="w-1.5 h-1.5 rounded-full bg-text-muted animate-[dotPulse_1.2s_0.15s_infinite_ease-in-out]" />
        <span class="w-1.5 h-1.5 rounded-full bg-text-muted animate-[dotPulse_1.2s_0.3s_infinite_ease-in-out]" />
      </div>
      <p class="text-[13px] text-text-muted">加载中…</p>
    </div>

    <!-- 空状态 -->
    <div v-else-if="articles.length === 0" class="flex flex-col items-center justify-center py-16 text-center">
      <div class="w-[72px] h-[72px] flex items-center justify-center rounded-full bg-bg-secondary text-text-muted mb-4">
        <FileText :size="40" />
      </div>
      <p class="text-[15px] font-medium text-text-primary mb-1">还没有文章</p>
      <p class="text-[13px] text-text-muted mb-5">点击上方「写文章」开始你的第一篇</p>
      <button @click="router.push('/dashboard/posts/new')" class="btn-primary">
        <Plus :size="14" />
        写文章
      </button>
    </div>

    <DataTable
      v-else
      :columns="articleColumns"
      :data="articles"
      :loading="loading"
      selectable
      :selected-ids="selectedIds"
      id-key="id"
      :total="pagination?.total"
      :current-page="pagination?.page"
      :page-size="pagination?.size || 10"
      @toggle-select="toggleSelect($event)"
      @toggle-select-all="toggleSelectAll()"
      @update:current-page="fetchArticles($event)"
    >
      <template #cell-title="{ item }">
        <div class="flex items-start gap-3">
          <div v-if="item.coverImage" class="shrink-0 w-14 h-[38px] rounded-md overflow-hidden bg-bg-secondary">
            <img :src="item.coverImage" :alt="item.title" class="w-full h-full object-cover" />
          </div>
          <div class="flex-1 min-w-0">
            <button class="block font-medium text-text-primary text-left hover:text-accent transition-colors truncate max-w-full leading-relaxed" @click="router.push(`/post/${item.id}`)" :title="item.title">
              {{ item.title }}
            </button>
            <p v-if="item.summary" class="mt-0.5 text-xs text-text-muted truncate leading-relaxed">{{ item.summary }}</p>
          </div>
        </div>
      </template>
      <template #cell-category="{ item }">
        <span v-if="item.categoryName" class="inline-flex items-center px-1.5 py-px text-[11px] font-medium rounded bg-accent-subtle text-accent whitespace-nowrap">{{ item.categoryName }}</span>
        <span v-else class="text-text-muted">—</span>
      </template>
      <template #cell-status="{ item }">
        <div class="flex items-center gap-1.5">
          <span :class="item.published === 1 ? 'inline-flex items-center px-1.5 py-px text-[11px] font-medium rounded bg-success-subtle text-success whitespace-nowrap' : 'inline-flex items-center px-1.5 py-px text-[11px] font-medium rounded bg-warning-subtle text-warning whitespace-nowrap'">
            {{ item.published === 1 ? '已发布' : '草稿' }}
          </span>
          <Lock v-if="item.visibility === 1" :size="12" class="text-warning" title="仅自己可见" />
        </div>
      </template>
      <template #cell-stats="{ item }">
        <div class="flex items-center gap-3">
          <span class="inline-flex items-center gap-1 text-xs text-text-muted whitespace-nowrap" title="浏览量"><Eye :size="11" />{{ item.viewCount }}</span>
          <span class="inline-flex items-center gap-1 text-xs text-text-muted whitespace-nowrap" title="收藏"><Heart :size="11" />{{ item.favoriteCount }}</span>
          <span v-if="item.readingTime" class="inline-flex items-center gap-1 text-xs text-text-muted whitespace-nowrap" title="阅读时长"><Clock :size="11" />{{ item.readingTime }}m</span>
        </div>
      </template>
      <template #cell-updateTime="{ item }">
        <span class="text-xs text-text-muted whitespace-nowrap">{{ formatDate(item.updateTime) }}</span>
      </template>
      <template #cell-actions="{ item }">
        <div class="flex items-center gap-0.5">
          <button @click="router.push(`/post/${item.id}`)" class="inline-flex items-center justify-center w-7 h-7 rounded-md text-text-muted hover:bg-accent/10 hover:text-accent transition-colors" title="查看"><Eye :size="14" /></button>
          <button v-if="canModify(item)" @click="router.push(`/dashboard/posts/${item.id}/edit`)" class="inline-flex items-center justify-center w-7 h-7 rounded-md text-text-muted hover:bg-accent/10 hover:text-accent transition-colors" title="编辑"><Edit :size="14" /></button>
          <button v-if="canModify(item)" @click="handleDelete(item)" :disabled="deletingIds.has(item.id)" class="inline-flex items-center justify-center w-7 h-7 rounded-md text-text-muted hover:bg-danger-subtle hover:text-danger transition-colors disabled:opacity-40 disabled:cursor-not-allowed" title="删除"><Trash2 :size="14" /></button>
          <span v-if="!canModify(item)" class="inline-flex items-center justify-center w-7 h-7 text-text-muted opacity-50"><MoreHorizontal :size="14" /></span>
        </div>
      </template>
      <template #mobile-card="{ item }">
        <div class="flex items-start gap-3">
          <div v-if="item.coverImage" class="shrink-0 w-14 h-[38px] rounded-md overflow-hidden bg-bg-secondary mt-0.5">
            <img :src="item.coverImage" :alt="item.title" class="w-full h-full object-cover" />
          </div>
          <div class="flex-1 min-w-0">
            <button class="block font-medium text-text-primary text-left text-sm hover:text-accent transition-colors truncate" @click="router.push(`/post/${item.id}`)">
              {{ item.title }}
            </button>
            <div class="flex items-center gap-1.5 mt-1 flex-wrap">
              <span v-if="item.categoryName" class="inline-flex items-center px-1.5 py-px text-[11px] font-medium rounded bg-accent-subtle text-accent">{{ item.categoryName }}</span>
              <span :class="item.published === 1 ? 'inline-flex items-center px-1.5 py-px text-[11px] font-medium rounded bg-success-subtle text-success' : 'inline-flex items-center px-1.5 py-px text-[11px] font-medium rounded bg-warning-subtle text-warning'">
                {{ item.published === 1 ? '已发布' : '草稿' }}
              </span>
              <Lock v-if="item.visibility === 1" :size="11" class="text-warning" />
              <span class="text-[11px] text-text-muted">{{ formatDate(item.updateTime) }}</span>
            </div>
            <div class="flex items-center gap-2 mt-1.5">
              <button @click="router.push(`/post/${item.id}`)" class="btn-ghost text-xs py-1 px-2">查看</button>
              <button v-if="canModify(item)" @click="router.push(`/dashboard/posts/${item.id}/edit`)" class="btn-ghost text-xs py-1 px-2">编辑</button>
              <button v-if="canModify(item)" @click="handleDelete(item)" :disabled="deletingIds.has(item.id)" class="btn-ghost text-xs py-1 px-2 !text-danger">删除</button>
            </div>
          </div>
        </div>
      </template>
    </DataTable>

    <!-- 批量加入合集弹窗 -->
    <Modal v-model:open="collectionModalOpen" title="加入合集" :description="`将选中的 ${selectedIds.size} 篇文章加入指定合集（已在合集中的文章会自动跳过）`" max-width="max-w-md">
      <div v-if="availableCollections.length > 0" class="flex flex-col gap-1.5 max-h-[45vh] overflow-y-auto">
        <label
          v-for="col in availableCollections"
          :key="col.id"
          class="flex items-center gap-3 px-3 py-2.5 rounded-lg border cursor-pointer transition-colors"
          :class="batchCollectionId === col.id ? 'border-accent bg-accent/5' : 'border-border hover:border-accent/40'"
        >
          <input type="radio" name="batch-collection" :value="col.id" v-model="batchCollectionId" class="accent-accent" />
          <div class="flex-1 min-w-0">
            <p class="text-sm font-medium text-text-primary truncate">{{ col.title }}</p>
            <p class="text-xs text-text-muted mt-0.5">{{ col.articleCount }} 篇文章</p>
          </div>
        </label>
      </div>
      <div v-else class="py-8 text-center text-sm text-text-muted">
        还没有可用的合集{{ isAdmin ? '' : '（只能选择自己创建的合集）' }}
      </div>
      <template #footer>
        <button class="btn-ghost" @click="collectionModalOpen = false">取消</button>
        <button
          class="btn-primary"
          :disabled="!batchCollectionId || batchAdding"
          @click="confirmBatchAddToCollection"
        >
          {{ batchAdding ? '加入中…' : '确认加入' }}
        </button>
      </template>
    </Modal>
  </div>
</template>

<style scoped>
@keyframes dotPulse {
  0%, 80%, 100% { opacity: 0.3; transform: scale(0.8); }
  40% { opacity: 1; transform: scale(1); }
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
</style>
