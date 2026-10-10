<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { getImages, deleteImage, uploadImage, cleanupOrphanImages, MAX_IMAGE_FILE_SIZE, type ImageItem } from '@/api/image'
import { useToast } from '@/composables/useToast'
import { useConfirm } from '@/composables/useConfirm'
import { useHead } from '@unhead/vue'
import { Image as ImageIcon, Trash2, Loader2, ExternalLink, Upload, X, ZoomIn, CheckSquare, Square } from 'lucide-vue-next'
import { PageHeader, SearchFilterBar, Pagination } from '@/components/ui'

useHead({ title: '图片管理 - MySite' })

const toast = useToast()
const { confirm } = useConfirm()

const images = ref<ImageItem[]>([])
const loading = ref(false)
const currentPage = ref(1)
const totalPages = ref(1)
const total = ref(0)
const keyword = ref('')
const sourceType = ref<number | undefined>(undefined)
/** 0 未引用(孤儿) / 1 已引用 / undefined 全部 */
const usageStatus = ref<number | undefined>(undefined)
const sortField = ref('createTime')
const sortOrder = ref('desc')
const cleaningOrphans = ref(false)

const selectedForPreview = ref<ImageItem | null>(null)
const uploadInputRef = ref<HTMLInputElement | null>(null)
const uploading = ref(false)
const uploadProgress = ref(0)

// 批量选择
const selectedIds = ref<Set<string>>(new Set())
const isDragging = ref(false)

function toggleSelect(id: string) {
  const next = new Set(selectedIds.value)
  if (next.has(id)) next.delete(id); else next.add(id)
  selectedIds.value = next
}
function toggleSelectAll() {
  if (selectedIds.value.size === images.value.length) {
    selectedIds.value = new Set()
  } else {
    selectedIds.value = new Set(images.value.map(i => i.id))
  }
}
async function handleBatchDelete() {
  if (selectedIds.value.size === 0) return
  const ok = await confirm({
    title: '批量删除',
    message: `确定删除选中的 ${selectedIds.value.size} 张图片吗？此操作不可撤销。`,
    danger: true,
    confirmText: '删除',
  })
  if (!ok) return
  let deleted = 0
  for (const id of selectedIds.value) {
    try { await deleteImage(id); deleted++ } catch { /* skip */ }
  }
  if (deleted > 0) toast.success(`已删除 ${deleted} 张图片`)
  selectedIds.value = new Set()
  await fetchImages(currentPage.value)
}

// 拖拽上传
function handleDragOver(e: DragEvent) { e.preventDefault(); isDragging.value = true }
function handleDragLeave() { isDragging.value = false }
function handleDrop(e: DragEvent) {
  e.preventDefault(); isDragging.value = false
  const file = e.dataTransfer?.files?.[0]
  if (file) uploadFile(file)
}
async function uploadFile(file: File) {
  if (!file.type.startsWith('image/')) { toast.error('请选择图片文件'); return }
  if (file.size > MAX_IMAGE_FILE_SIZE) { toast.error(`文件不能超过 ${MAX_IMAGE_FILE_SIZE / 1024 / 1024}MB`); return }
  uploading.value = true; uploadProgress.value = 0
  try {
    await uploadImage(file, (evt) => { uploadProgress.value = evt.progress })
    toast.success(`"${file.name}" 上传成功`)
    await fetchImages(currentPage.value)
  } catch (err) { toast.error('上传失败：' + (err instanceof Error ? err.message : '未知错误'))
  } finally { uploading.value = false; uploadProgress.value = 0 }
}

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
    const params: Record<string, unknown> = {
      current: page,
      size: 12,
      sortField: sortField.value,
      sortOrder: sortOrder.value,
    }
    if (keyword.value) params.keyword = keyword.value
    if (sourceType.value !== undefined) params.sourceType = sourceType.value
    if (usageStatus.value !== undefined) params.usageStatus = usageStatus.value
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

function handleUsageStatusChange() {
  selectedIds.value = new Set()
  fetchImages(1)
}

function toggleSortOrder() {
  sortOrder.value = sortOrder.value === 'desc' ? 'asc' : 'desc'
  fetchImages(1)
}

function handleReset() {
  keyword.value = ''
  sourceType.value = undefined
  usageStatus.value = undefined
  sortField.value = 'createTime'
  sortOrder.value = 'desc'
  fetchImages(1)
}

const sortOptions = [
  { label: '创建时间', value: 'createTime' },
  { label: '文件大小', value: 'size' },
  { label: '文件名', value: 'name' },
]

const imageFilterOptions = [
  { label: '全部来源', value: undefined as number | undefined },
  { label: '本地上传', value: 0 },
  { label: 'URL拉取', value: 1 },
]

const usageFilterOptions = [
  { label: '全部引用状态', value: undefined as number | undefined },
  { label: '已引用', value: 1 },
  { label: '孤儿图', value: 0 },
]

function usageLabel(image: ImageItem): { text: string; class: string } {
  if (image.referenced === 1) {
    return { text: '已引用', class: 'bg-success-subtle text-success' }
  }
  if (image.status === 0) {
    return { text: '暂存', class: 'bg-warning-subtle text-warning' }
  }
  return { text: '孤儿', class: 'bg-danger-subtle text-danger' }
}

async function handleCleanupOrphans() {
  const ok = await confirm({
    title: '清理孤儿图',
    message: '将删除所有未被文章、合集引用的图片（含文章/合集已删、正文中已移除的图）。48 小时内新建的暂存图会保留，避免误删正在写的草稿。确定继续？',
    danger: true,
    confirmText: '清理',
  })
  if (!ok) return
  cleaningOrphans.value = true
  try {
    const n = await cleanupOrphanImages(true)
    if (n > 0) {
      toast.success(`已清理 ${n} 张孤儿图`)
    } else {
      toast.success('没有可清理的孤儿图')
    }
    selectedIds.value = new Set()
    await fetchImages(1)
  } catch (e) {
    toast.error('清理失败：' + (e instanceof Error ? e.message : '未知错误'))
  } finally {
    cleaningOrphans.value = false
  }
}

async function confirmDelete(image: ImageItem) {
  const ok = await confirm({
    title: '确认删除',
    message: `确定要删除图片「${image.originalName}」吗？此操作不可撤销。`,
    danger: true,
    confirmText: '删除',
  })
  if (!ok) return
  try {
    await deleteImage(image.id)
    toast.success('图片已删除')
    fetchImages(currentPage.value)
  } catch (e) {
    toast.error('删除失败：' + (e instanceof Error ? e.message : '未知错误'))
  }
}

function triggerUpload() {
  uploadInputRef.value?.click()
}

function handleFileUpload(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  uploadFile(file).finally(() => { input.value = '' })
}

onMounted(() => {
  fetchImages()
})
</script>

<template>
  <div>
    <PageHeader title="图片管理" :subtitle="`共 ${total} 张图片`">
      <template #actions>
        <input
          ref="uploadInputRef"
          type="file"
          accept="image/*"
          class="hidden"
          @change="handleFileUpload"
        />
        <button
          type="button"
          class="btn-secondary text-sm"
          :disabled="cleaningOrphans"
          title="删除未被任何文章引用的图片"
          @click="handleCleanupOrphans"
        >
          <Loader2 v-if="cleaningOrphans" :size="14" class="animate-spin" />
          <Trash2 v-else :size="14" />
          {{ cleaningOrphans ? '清理中…' : '清理孤儿图' }}
        </button>
        <button
          @click="triggerUpload"
          class="btn-secondary text-sm"
          :disabled="uploading"
        >
          <Loader2 v-if="uploading" :size="14" class="animate-spin" />
          <Upload v-else :size="14" />
          {{ uploading ? '上传中 ' + uploadProgress + '%' : '上传图片' }}
        </button>
      </template>
    </PageHeader>

    <SearchFilterBar
      v-model="keyword"
      placeholder="搜索文件名..."
      :sort-options="sortOptions"
      :sort-field="sortField"
      :sort-order="sortOrder"
      :show-filter="false"
      class="mb-4"
      @update:sort-field="sortField = $event; fetchImages(1)"
      @update:sort-order="sortOrder = $event; fetchImages(1)"
      @search="handleSearch"
      @reset="handleReset"
    >
      <select
        v-model="sourceType"
        class="px-3 py-2 text-sm rounded-lg border border-border bg-bg-secondary text-text-primary focus:outline-none focus:ring-2 focus:ring-accent/50 focus:border-accent transition-colors"
        @change="handleSourceTypeChange"
      >
        <option v-for="opt in imageFilterOptions" :key="String(opt.value)" :value="opt.value">{{ opt.label }}</option>
      </select>
      <select
        v-model="usageStatus"
        class="px-3 py-2 text-sm rounded-lg border border-border bg-bg-secondary text-text-primary focus:outline-none focus:ring-2 focus:ring-accent/50 focus:border-accent transition-colors"
        @change="handleUsageStatusChange"
      >
        <option v-for="opt in usageFilterOptions" :key="String(opt.value)" :value="opt.value">{{ opt.label }}</option>
      </select>
    </SearchFilterBar>

    <div v-if="loading" class="flex justify-center py-12">
      <Loader2 :size="24" class="animate-spin text-text-muted" />
    </div>

    <div v-else-if="images.length === 0" class="py-16 text-center">
      <ImageIcon :size="48" class="mx-auto mb-4 text-text-muted" />
      <p class="text-text-muted mb-4">还没有图片，写文章时可以上传图片</p>
    </div>

    <div v-else @dragover="handleDragOver" @dragleave="handleDragLeave" @drop="handleDrop">
      <!-- 拖拽上传遮罩 -->
      <div
        v-if="isDragging"
        class="fixed inset-0 z-50 flex items-center justify-center bg-accent/10 backdrop-blur-sm border-2 border-dashed border-accent rounded-xl m-4 pointer-events-none"
      >
        <div class="text-center">
          <Upload :size="40" class="mx-auto mb-3 text-accent" />
          <p class="text-lg font-semibold text-accent">释放以上传图片</p>
        </div>
      </div>

      <!-- Upload progress bar -->
      <div v-if="uploading" class="mb-4 px-4 py-2.5 rounded-lg bg-accent-subtle border border-accent/20 flex items-center gap-3">
        <Loader2 :size="16" class="animate-spin text-accent shrink-0" />
        <div class="flex-1 h-2 bg-bg-code rounded-full overflow-hidden">
          <div class="h-full bg-accent rounded-full transition-all duration-300" :style="{ width: `${uploadProgress}%` }" />
        </div>
        <span class="text-xs text-accent font-medium tabular-nums shrink-0">{{ uploadProgress }}%</span>
      </div>

      <!-- Select all / 批量操作：同一行右侧切换，行高固定，选中时页面不跳动 -->
      <div class="flex items-center gap-2 mb-3 min-h-[28px]">
        <button @click="toggleSelectAll" class="inline-flex items-center gap-1.5 text-xs text-text-muted hover:text-accent transition-colors">
          <CheckSquare v-if="selectedIds.size === images.length && images.length > 0" :size="14" class="text-accent" />
          <Square v-else :size="14" />
          {{ selectedIds.size === images.length ? '取消全选' : '全选' }}
        </button>
        <span class="text-xs text-text-muted">已选 {{ selectedIds.size }} / {{ images.length }}</span>

        <Transition
          enter-active-class="transition-opacity duration-150 ease-out"
          enter-from-class="opacity-0"
          leave-active-class="transition-opacity duration-150 ease-in"
          leave-to-class="opacity-0"
        >
          <div v-if="selectedIds.size > 0" class="ml-auto flex items-center gap-2">
            <button
              @click="handleBatchDelete"
              class="inline-flex items-center gap-1.5 px-2.5 py-1 text-xs rounded-md bg-danger-subtle text-danger hover:bg-danger/15 transition-colors"
            >
              <Trash2 :size="12" />
              删除所选
            </button>
            <button
              @click="selectedIds = new Set()"
              class="px-2 py-1 text-xs text-text-muted hover:text-text-primary transition-colors"
            >
              取消
            </button>
          </div>
        </Transition>
      </div>

      <div class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6 gap-4">
        <div
          v-for="image in images"
          :key="image.id"
          class="group relative rounded-xl card-solid overflow-hidden transition-all duration-200 hover:shadow-md"
          :class="selectedIds.has(image.id) ? 'ring-2 ring-accent border-accent' : ''"
        >
          <!-- Select checkbox -->
          <button
            @click.stop="toggleSelect(image.id)"
            class="absolute top-2 left-2 z-10 p-1 rounded-md bg-black/40 hover:bg-black/60 transition-colors"
          >
            <CheckSquare v-if="selectedIds.has(image.id)" :size="14" class="text-accent" />
            <Square v-else :size="14" class="text-white/70" />
          </button>

          <div
            class="aspect-square bg-bg-code flex items-center justify-center overflow-hidden cursor-pointer relative"
            @click="selectedForPreview = image"
          >
            <img
              :src="image.url"
              :alt="image.originalName"
              class="w-full h-full object-cover"
              loading="lazy"
            />
            <!-- Hover overlay for preview -->
            <div class="absolute inset-0 bg-black/0 group-hover:bg-black/30 flex items-center justify-center transition-colors">
              <ZoomIn :size="24" class="text-white opacity-0 group-hover:opacity-100 transition-opacity" />
            </div>
          </div>
          <div class="p-2">
            <p class="text-xs font-medium truncate text-text-secondary" :title="image.originalName">
              {{ image.originalName }}
            </p>
            <div class="flex items-center justify-between mt-1 gap-1">
              <span class="text-[10px] text-text-muted truncate">
                {{ formatFileSize(image.fileSize) }} · {{ contentTypeLabel(image.contentType) }}
              </span>
              <span
                class="px-1.5 py-0.5 text-[10px] rounded flex-shrink-0"
                :class="usageLabel(image).class"
                :title="image.referenced === 1 ? '已被文章或合集引用' : (image.status === 0 ? '编辑器暂存，保存后转正' : '未被文章或合集引用')"
              >
                {{ usageLabel(image).text }}
              </span>
            </div>
          </div>
          <!-- Always-visible action buttons (touch-friendly) -->
          <div class="absolute top-2 right-2 flex gap-1">
            <a
              :href="image.url"
              target="_blank"
              class="p-1.5 rounded bg-black/50 text-white hover:bg-black/70 transition-colors hidden sm:flex"
              title="在新窗口查看"
            >
              <ExternalLink :size="12" />
            </a>
            <button
              @click.stop="confirmDelete(image)"
              class="p-1.5 rounded bg-black/50 text-white hover:bg-danger transition-colors"
              title="删除"
            >
              <Trash2 :size="12" />
            </button>
          </div>
        </div>
      </div>

      <Pagination
        v-if="totalPages > 1"
        :current="currentPage"
        :total="total"
        :page-size="12"
        class="mt-6"
        @update:current="fetchImages($event)"
      />
    </div>

    <!-- Lightbox Preview -->
    <Teleport to="body">
      <div
        v-if="selectedForPreview"
        class="fixed inset-0 z-50 flex items-center justify-center bg-black/80 backdrop-blur-sm p-4 cursor-pointer"
        @click="selectedForPreview = null"
      >
        <button
          class="absolute top-4 right-4 p-2 rounded-lg bg-white/10 text-white hover:bg-white/20 transition-colors"
          @click="selectedForPreview = null"
        >
          <X :size="20" />
        </button>
        <div class="max-w-[90vw] max-h-[90vh] flex flex-col gap-3" @click.stop>
          <img
            :src="selectedForPreview.url"
            :alt="selectedForPreview.originalName"
            class="max-w-full max-h-[75vh] object-contain rounded-lg shadow-2xl"
          />
          <div class="text-center text-white/80 text-sm">
            <p class="font-medium">{{ selectedForPreview.originalName }}</p>
            <p class="text-xs text-white/50 mt-0.5">
              {{ formatFileSize(selectedForPreview.fileSize) }} · {{ contentTypeLabel(selectedForPreview.contentType) }}
            </p>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>
