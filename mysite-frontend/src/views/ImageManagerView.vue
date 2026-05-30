<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { getImages, deleteImage, type ImageItem } from '@/api/image'
import { useToast } from '@/composables/useToast'
import { useHead } from '@unhead/vue'
import { Image as ImageIcon, Trash2, Search, Loader2, ChevronLeft, ChevronRight, ExternalLink, Upload } from 'lucide-vue-next'

useHead({ title: '图片管理 - MySite' })

const toast = useToast()

const images = ref<ImageItem[]>([])
const loading = ref(false)
const currentPage = ref(1)
const totalPages = ref(1)
const total = ref(0)
const keyword = ref('')
const sourceType = ref<number | undefined>(undefined)

const confirmDialog = ref<{ show: boolean; image: ImageItem | null }>({
  show: false,
  image: null,
})

function formatFileSize(bytes: number): string {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

function formatDate(dateStr: string): string {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  return d.toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}

function contentTypeLabel(ct: string): string {
  const map: Record<string, string> = {
    'image/jpeg': 'JPEG',
    'image/png': 'PNG',
    'image/gif': 'GIF',
    'image/webp': 'WebP',
    'image/svg+xml': 'SVG',
  }
  return map[ct] || ct
}

const sourceTypeOptions = computed(() => [
  { label: '全部', value: undefined },
  { label: '本地上传', value: 0 },
  { label: 'URL拉取', value: 1 },
])

async function fetchImages(page: number = 1) {
  loading.value = true
  try {
    const params: Record<string, unknown> = { current: page, size: 12 }
    if (keyword.value) params.keyword = keyword.value
    if (sourceType.value !== undefined) params.sourceType = sourceType.value
    const result = await getImages(params)
    images.value = result.list
    currentPage.value = result.pagination.page
    totalPages.value = result.pagination.totalPages
    total.value = result.pagination.total
  } catch (e) {
    toast.error('加载图片列表失败：' + (e instanceof Error ? e.message : '未知错误'))
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  fetchImages(1)
}

function handleSourceTypeChange() {
  fetchImages(1)
}

function confirmDelete(image: ImageItem) {
  confirmDialog.value = { show: true, image }
}

async function handleDelete() {
  const image = confirmDialog.value.image
  if (!image) return
  try {
    await deleteImage(image.id)
    toast.success('图片已删除')
    confirmDialog.value = { show: false, image: null }
    fetchImages(currentPage.value)
  } catch (e) {
    toast.error('删除失败：' + (e instanceof Error ? e.message : '未知错误'))
  }
}

onMounted(() => {
  fetchImages()
})
</script>

<template>
  <div>
    <div class="flex items-center justify-between mb-8">
      <h1 class="text-2xl font-semibold text-text-primary">
        图片管理
      </h1>
      <span class="text-sm text-text-muted">
        共 {{ total }} 张图片
      </span>
    </div>

    <div class="flex items-center gap-3 mb-6">
      <div class="relative flex-1 max-w-sm">
        <Search :size="14" class="absolute left-3 top-1/2 -translate-y-1/2 text-text-muted" />
        <input
          v-model="keyword"
          type="text"
          placeholder="搜索文件名..."
          class="w-full pl-9 pr-3 py-2 rounded-lg border border-border bg-bg-secondary text-text-secondary focus:outline-none focus:ring-2 focus:ring-accent text-sm"
          @keydown.enter="handleSearch"
        />
      </div>
      <select
        v-model="sourceType"
        class="px-3 py-2 rounded-lg border border-border bg-bg-secondary text-text-secondary text-sm focus:outline-none focus:ring-2 focus:ring-accent"
        @change="handleSourceTypeChange"
      >
        <option v-for="opt in sourceTypeOptions" :key="String(opt.value)" :value="opt.value">{{ opt.label }}</option>
      </select>
      <button @click="handleSearch" class="btn-primary text-sm">
        <Search :size="14" />
        搜索
      </button>
    </div>

    <div v-if="loading" class="flex justify-center py-12">
      <Loader2 :size="24" class="animate-spin text-text-muted" />
    </div>

    <div v-else-if="images.length === 0" class="py-16 text-center">
      <ImageIcon :size="48" class="mx-auto mb-4 text-text-muted" />
      <p class="text-text-muted mb-4">还没有图片，写文章时可以上传图片</p>
    </div>

    <div v-else>
      <div class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6 gap-4">
        <div
          v-for="image in images"
          :key="image.id"
          class="group relative rounded-xl glass glass-sm glass-hover overflow-hidden hover:border-accent/30 transition-all duration-200"
        >
          <div class="aspect-square bg-bg-code flex items-center justify-center overflow-hidden">
            <img
              :src="image.url"
              :alt="image.originalName"
              class="w-full h-full object-cover"
              loading="lazy"
            />
          </div>
          <div class="p-2">
            <p class="text-xs font-medium truncate text-text-secondary" :title="image.originalName">
              {{ image.originalName }}
            </p>
            <div class="flex items-center justify-between mt-1">
              <span class="text-[10px] text-text-muted">
                {{ formatFileSize(image.fileSize) }} · {{ contentTypeLabel(image.contentType) }}
              </span>
              <span
                :class="[
                  'px-1.5 py-0.5 text-[10px] rounded',
                  image.sourceType === 0
                    ? 'bg-blue-100 text-blue-700'
                    : 'bg-purple-100 text-purple-700'
                ]"
              >
                {{ image.sourceType === 0 ? '本地' : 'URL' }}
              </span>
            </div>
          </div>
          <div class="absolute top-2 right-2 flex gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
            <a
              :href="image.url"
              target="_blank"
              class="p-1.5 rounded bg-black/50 text-white hover:bg-black/70 transition-colors"
              title="在新窗口查看"
            >
              <ExternalLink :size="12" />
            </a>
            <button
              @click="confirmDelete(image)"
              class="p-1.5 rounded bg-black/50 text-white hover:bg-red-500 transition-colors"
              title="删除"
            >
              <Trash2 :size="12" />
            </button>
          </div>
        </div>
      </div>

      <div v-if="totalPages > 1" class="flex items-center justify-center gap-2 mt-6">
        <button
          @click="fetchImages(currentPage - 1)"
          :disabled="currentPage <= 1"
          class="p-2 rounded-lg text-text-muted hover:bg-bg-code disabled:opacity-50 transition-colors"
        >
          <ChevronLeft :size="16" />
        </button>
        <span class="text-sm text-text-muted">
          {{ currentPage }} / {{ totalPages }}
        </span>
        <button
          @click="fetchImages(currentPage + 1)"
          :disabled="currentPage >= totalPages"
          class="p-2 rounded-lg text-text-muted hover:bg-bg-code disabled:opacity-50 transition-colors"
        >
          <ChevronRight :size="16" />
        </button>
      </div>
    </div>

    <Teleport to="body">
      <div
        v-if="confirmDialog.show"
        class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm"
        @click.self="confirmDialog.show = false"
      >
        <div class="glass glass-lg rounded-2xl p-6 w-full max-w-md mx-4 animate-scale-in">
          <h3 class="text-lg font-semibold text-text-primary mb-2">确认删除</h3>
          <p class="text-sm text-text-muted mb-6">
            确定要删除图片「{{ confirmDialog.image?.originalName }}」吗？此操作不可撤销。
          </p>
          <div class="flex justify-end gap-3">
            <button @click="confirmDialog.show = false" class="btn-secondary">取消</button>
            <button @click="handleDelete" class="bg-red-500 text-white hover:bg-red-600 px-4 py-2 text-sm font-medium rounded-lg transition-colors">删除</button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>
