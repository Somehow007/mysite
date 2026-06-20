<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useHead } from '@unhead/vue'
import { useRouter } from 'vue-router'
import { BookOpen, Library, Eye, Loader2, Search, ArrowLeft } from 'lucide-vue-next'
import { getCollections } from '@/api/collection'
import { formatDate } from '@/utils/date'
import OptimizedImage from '@/components/common/OptimizedImage.vue'
import SkeletonCard from '@/components/common/SkeletonCard.vue'
import type { Collection, Pagination } from '@/types'

const router = useRouter()

useHead(() => ({ title: '合集 - MySite' }))

const collections = ref<Collection[]>([])
const pagination = ref<Pagination | null>(null)
const loading = ref(false)
const loadError = ref('')
const keyword = ref('')
const currentPage = ref(1)

async function fetchCollections(page = 1) {
  loading.value = true
  loadError.value = ''
  try {
    const res = await getCollections({ page, size: 10, keyword: keyword.value || undefined })
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
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

function formatViewCount(count?: number): string {
  if (!count || count <= 0) return '0'
  if (count >= 10000) return (count / 10000).toFixed(1) + '万'
  if (count >= 1000) return (count / 1000).toFixed(1) + 'k'
  return String(count)
}

onMounted(() => {
  fetchCollections()
})
</script>

<template>
  <div>
    <header class="mb-8">
      <button
        @click="router.back()"
        class="inline-flex items-center gap-1.5 text-sm text-text-muted hover:text-accent transition-colors duration-200 mb-4 group"
      >
        <ArrowLeft :size="14" class="group-hover:-translate-x-0.5 transition-transform duration-200" />
        返回
      </button>

      <div class="flex items-center gap-2 mb-3">
        <Library :size="24" class="text-accent" />
        <h1 class="text-3xl sm:text-4xl font-bold text-text-primary tracking-tight">合集</h1>
      </div>
      <p class="text-text-muted">浏览全部文章合集，每个合集围绕一个主题展开</p>
    </header>

    <div class="mb-6 relative max-w-md">
      <Search :size="16" class="absolute left-3 top-1/2 -translate-y-1/2 text-text-muted" />
      <input
        v-model="keyword"
        type="text"
        placeholder="搜索合集..."
        class="input-base pl-10"
        @keydown.enter="handleSearch"
      />
    </div>

    <div v-if="loading" class="space-y-4">
      <SkeletonCard v-for="i in 4" :key="i" :lines="3" />
    </div>

    <div v-else-if="loadError" class="py-16 text-center">
      <p class="text-red-500 mb-4">{{ loadError }}</p>
      <button @click="fetchCollections(currentPage)" class="btn-secondary">重试</button>
    </div>

    <div v-else-if="collections.length === 0" class="py-16 text-center">
      <Library :size="48" class="mx-auto mb-4 text-text-muted opacity-30" />
      <p class="text-text-muted">{{ keyword ? '没有匹配的合集' : '暂无合集' }}</p>
    </div>

    <div v-else class="space-y-4">
      <RouterLink
        v-for="collection in collections"
        :key="collection.id"
        :to="`/collection/${collection.id}`"
        class="group flex gap-5 p-4 sm:p-5 rounded-xl border border-border bg-bg-secondary hover:border-accent/40 hover:shadow-lg card-shadow transition-all duration-300"
      >
        <!-- 封面缩略图 -->
        <div class="w-32 h-24 sm:w-40 sm:h-28 rounded-lg overflow-hidden shrink-0 bg-bg-code">
          <OptimizedImage
            v-if="collection.coverImage"
            :src="collection.coverImage"
            :alt="collection.title"
            class="w-full h-full object-cover transition-transform duration-500 group-hover:scale-[1.05]"
          />
          <div v-else class="w-full h-full flex items-center justify-center bg-gradient-to-br from-accent/10 to-accent/5">
            <BookOpen :size="32" class="text-accent/30" />
          </div>
        </div>

        <!-- 合集信息 -->
        <div class="flex-1 min-w-0 flex flex-col justify-between py-0.5">
          <div>
            <h2 class="text-lg font-semibold text-text-primary group-hover:text-accent transition-colors duration-200 line-clamp-1 flex items-center gap-1.5">
              <Library :size="16" class="text-accent shrink-0" />
              {{ collection.title }}
            </h2>
            <p v-if="collection.description" class="text-sm text-text-secondary line-clamp-2 mt-1 leading-relaxed">
              {{ collection.description }}
            </p>
          </div>

          <div class="flex items-center gap-4 text-xs text-text-muted mt-2">
            <span class="inline-flex items-center gap-1 px-1.5 py-0.5 rounded bg-accent-subtle text-accent font-medium">
              <BookOpen :size="10" />
              {{ collection.articleCount ?? 0 }} 篇
            </span>
            <span class="inline-flex items-center gap-1">
              <Eye :size="12" />
              {{ formatViewCount(collection.totalViewCount) }} 浏览
            </span>
            <span class="hidden sm:inline">{{ collection.authorName || '未知作者' }}</span>
            <span class="hidden sm:inline">{{ formatDate(collection.createTime) }}</span>
          </div>
        </div>
      </RouterLink>
    </div>

    <div v-if="pagination && pagination.totalPages > 1" class="flex items-center justify-center gap-2 mt-8">
      <button
        :disabled="currentPage <= 1"
        @click="handlePageChange(currentPage - 1)"
        class="px-3 py-1.5 rounded-lg text-sm border border-border text-text-muted hover:bg-accent-subtle hover:text-accent hover:border-accent/30 transition-all duration-200 disabled:opacity-40 disabled:cursor-not-allowed"
      >
        上一页
      </button>
      <span class="text-sm text-text-muted">{{ currentPage }} / {{ pagination.totalPages }}</span>
      <button
        :disabled="currentPage >= pagination.totalPages"
        @click="handlePageChange(currentPage + 1)"
        class="px-3 py-1.5 rounded-lg text-sm border border-border text-text-muted hover:bg-accent-subtle hover:text-accent hover:border-accent/30 transition-all duration-200 disabled:opacity-40 disabled:cursor-not-allowed"
      >
        下一页
      </button>
    </div>
  </div>
</template>
