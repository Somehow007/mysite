<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useHead } from '@unhead/vue'
import { useRouter } from 'vue-router'
import { Plus, Trash2, Edit3, BookOpen, Loader2, Search } from 'lucide-vue-next'
import { getCollections, deleteCollection } from '@/api/collection'
import { useToast } from '@/composables/useToast'
import type { Collection, Pagination } from '@/types'

const router = useRouter()
const toast = useToast()

useHead(() => ({ title: '合集管理 - MySite' }))

const collections = ref<Collection[]>([])
const pagination = ref<Pagination | null>(null)
const loading = ref(false)
const keyword = ref('')
const currentPage = ref(1)

async function fetchCollections(page = 1) {
  loading.value = true
  try {
    const res = await getCollections({ page, size: 10, keyword: keyword.value || undefined })
    collections.value = res.list
    pagination.value = res.pagination
    currentPage.value = page
  } catch {
    collections.value = []
    pagination.value = null
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

async function handleDelete(id: string, title: string) {
  if (!confirm(`确定要删除合集「${title}」吗？此操作不可撤销。`)) return
  try {
    await deleteCollection(id)
    toast.success('合集已删除')
    fetchCollections(currentPage.value)
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
    <div class="flex items-center justify-between mb-6">
      <h1 class="text-2xl font-semibold text-text-primary">合集管理</h1>
      <button @click="router.push('/dashboard/collections/new')" class="btn-primary">
        <Plus :size="16" />
        新建合集
      </button>
    </div>

    <div class="mb-6 relative">
      <Search :size="16" class="absolute left-3 top-1/2 -translate-y-1/2 text-text-muted" />
      <input
        v-model="keyword"
        type="text"
        placeholder="搜索合集..."
        class="input-base pl-10"
        @keydown.enter="handleSearch"
      />
    </div>

    <div v-if="loading" class="py-16 text-center">
      <Loader2 :size="24" class="animate-spin mx-auto text-text-muted" />
    </div>

    <div v-else-if="collections.length === 0" class="py-16 text-center">
      <BookOpen :size="48" class="mx-auto mb-4 text-text-muted opacity-30" />
      <p class="text-text-muted mb-4">暂无合集</p>
      <button @click="router.push('/dashboard/collections/new')" class="btn-secondary">
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
            class="p-2 rounded-lg text-text-muted hover:bg-red-50 hover:text-red-500 transition-all duration-200"
            title="删除"
          >
            <Trash2 :size="16" />
          </button>
        </div>
      </div>
    </div>

    <div v-if="pagination && pagination.totalPages > 1" class="flex items-center justify-center gap-2 mt-8">
      <button
        :disabled="currentPage <= 1"
        @click="handlePageChange(currentPage - 1)"
        class="px-3 py-1.5 rounded-lg text-sm border border-border text-text-muted hover:bg-accent-subtle hover:text-accent transition-all duration-200 disabled:opacity-40 disabled:cursor-not-allowed"
      >
        上一页
      </button>
      <span class="text-sm text-text-muted">{{ currentPage }} / {{ pagination.totalPages }}</span>
      <button
        :disabled="currentPage >= pagination.totalPages"
        @click="handlePageChange(currentPage + 1)"
        class="px-3 py-1.5 rounded-lg text-sm border border-border text-text-muted hover:bg-accent-subtle hover:text-accent transition-all duration-200 disabled:opacity-40 disabled:cursor-not-allowed"
      >
        下一页
      </button>
    </div>
  </div>
</template>
