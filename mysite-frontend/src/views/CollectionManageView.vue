<script setup lang="ts">
import { ref, onMounted, nextTick } from 'vue'
import { useHead } from '@unhead/vue'
import { useRouter } from 'vue-router'
import { Plus, Trash2, Edit3, BookOpen, Loader2 } from 'lucide-vue-next'
import { getCollections, deleteCollection } from '@/api/collection'
import { useToast } from '@/composables/useToast'
import { useConfirm } from '@/composables/useConfirm'
import { PageHeader, SearchFilterBar, Pagination as PaginationBar } from '@/components/ui'
import type { Collection, Pagination as PaginationMeta } from '@/types'

const router = useRouter()
const toast = useToast()
const { confirm } = useConfirm()

useHead(() => ({ title: '合集管理 - MySite' }))

const collections = ref<Collection[]>([])
const pagination = ref<PaginationMeta | null>(null)
const loading = ref(false)
const loadError = ref('')
const keyword = ref('')
const currentPage = ref(1)
const sortField = ref('createTime')
const sortOrder = ref('desc')

async function fetchCollections(page = 1) {
  loading.value = true
  loadError.value = ''
  try {
    const res = await getCollections({
      page,
      size: 10,
      keyword: keyword.value || undefined,
      sortField: sortField.value,
      sortOrder: sortOrder.value,
    })
    collections.value = res.list
    pagination.value = res.pagination
    currentPage.value = page
  } catch (e: unknown) {
    collections.value = []
    pagination.value = null
    loadError.value = e instanceof Error ? e.message : '加载合集列表失败'
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  fetchCollections(1)
}

function handlePageChange(page: number) {
  fetchCollections(page)
}

function toggleSortOrder() {
  sortOrder.value = sortOrder.value === 'desc' ? 'asc' : 'desc'
  fetchCollections(1)
}

function handleReset() {
  keyword.value = ''
  sortField.value = 'createTime'
  sortOrder.value = 'desc'
  fetchCollections(1)
}

const sortOptions = [
  { label: '创建时间', value: 'createTime' },
  { label: '标题', value: 'title' },
  { label: '文章数', value: 'articleCount' },
]

async function handleDelete(id: string, title: string) {
  const ok = await confirm({
    message: `确定要删除合集「${title}」吗？\n\n注意：删除合集不会删除其中的文章，文章将恢复为普通文章展示。此操作不可撤销。`,
    danger: true,
    confirmText: '删除',
  })
  if (!ok) return
  try {
    await deleteCollection(id)
    toast.success('合集已删除')
    // 删除后若当前页已无数据，回退到上一页
    const remaining = pagination.value ? pagination.value.total - 1 : 0
    const pageSize = 10
    const maxPage = Math.max(1, Math.ceil(remaining / pageSize))
    const targetPage = currentPage.value > maxPage ? maxPage : currentPage.value
    fetchCollections(targetPage)
  } catch (e: unknown) {
    toast.error(e instanceof Error ? e.message : '删除失败')
  }
}

onMounted(() => {
  fetchCollections()
})
</script>

<template>
  <div>
    <PageHeader title="合集管理" subtitle="管理文章合集与系列">
      <template #actions>
        <button @click="router.push('/dashboard/collections/new')" class="btn-primary">
          <Plus :size="14" />
          新建合集
        </button>
      </template>
    </PageHeader>

    <SearchFilterBar
      v-model="keyword"
      placeholder="搜索合集..."
      :sort-options="sortOptions"
      :sort-field="sortField"
      :sort-order="sortOrder"
      :show-filter="false"
      class="mb-4"
      @update:sort-field="sortField = $event; fetchCollections(1)"
      @update:sort-order="sortOrder = $event; fetchCollections(1)"
      @search="handleSearch"
      @reset="handleReset"
    />

    <div v-if="loading" class="py-16 text-center">
      <Loader2 :size="24" class="animate-spin mx-auto text-text-muted" />
    </div>

    <div v-else-if="loadError" class="py-16 text-center">
      <p class="text-danger mb-4">{{ loadError }}</p>
      <button @click="fetchCollections(currentPage)" class="btn-secondary">重试</button>
    </div>

    <div v-else-if="collections.length === 0" class="py-16 text-center">
      <BookOpen :size="48" class="mx-auto mb-4 text-text-muted opacity-30" />
      <p class="text-text-muted mb-4">{{ keyword ? '没有匹配的合集' : '暂无合集' }}</p>
      <button v-if="!keyword" @click="router.push('/dashboard/collections/new')" class="btn-secondary">
        <Plus :size="14" />
        创建第一个合集
      </button>
    </div>

    <div v-else class="space-y-3">
      <div
        v-for="collection in collections"
        :key="collection.id"
        class="flex items-center gap-4 p-4 rounded-lg border border-border hover:border-accent/30 hover:bg-accent-subtle/30 transition-all duration-200"
      >
        <div class="flex-1 min-w-0">
          <RouterLink
            :to="`/collection/${collection.id}`"
            class="text-base font-medium text-text-primary hover:text-accent transition-colors duration-200"
          >
            {{ collection.title }}
          </RouterLink>
          <p v-if="collection.description" class="text-sm text-text-muted line-clamp-1 mt-0.5">
            {{ collection.description }}
          </p>
          <div class="flex items-center gap-3 text-xs text-text-muted mt-1">
            <span class="inline-flex items-center gap-1">
              <BookOpen :size="12" />
              {{ collection.articleCount }} 篇
            </span>
            <span>{{ collection.authorName }}</span>
          </div>
        </div>

        <div class="flex items-center gap-2 shrink-0">
          <button
            @click="router.push(`/dashboard/collections/${collection.id}/edit`)"
            class="p-2 rounded-lg text-text-muted hover:bg-accent-subtle hover:text-accent transition-all duration-200"
            title="编辑"
          >
            <Edit3 :size="16" />
          </button>
          <button
            @click="handleDelete(collection.id, collection.title)"
            class="p-2 rounded-lg text-text-muted hover:bg-danger-subtle hover:text-danger transition-all duration-200"
            title="删除"
          >
            <Trash2 :size="16" />
          </button>
        </div>
      </div>
    </div>

    <div v-if="pagination && pagination.totalPages > 1" class="card-solid mt-6">
      <PaginationBar
        in-card
        :current="currentPage"
        :total="pagination.total"
        :page-size="10"
        @update:current="handlePageChange($event)"
      />
    </div>
  </div>
</template>
