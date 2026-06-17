<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useHead } from '@unhead/vue'
import { ArrowLeft, Save, Loader2, Plus, X, GripVertical, AlertCircle, BookOpen, FilePlus, ChevronUp, ChevronDown } from 'lucide-vue-next'
import { createCollection, updateCollection, getCollectionById, addArticleToCollection, removeArticleFromCollection, updateArticleSort, batchAddArticles } from '@/api/collection'
import { getArticles } from '@/api/article'
import { uploadImage, MAX_IMAGE_FILE_SIZE } from '@/api/image'
import { useToast } from '@/composables/useToast'
import type { CollectionArticleItem, ArticleListItem } from '@/types'

const route = useRoute()
const router = useRouter()
const toast = useToast()

const isEdit = computed(() => !!route.params.id && route.params.id !== 'new')
const pageTitle = computed(() => isEdit.value ? '编辑合集' : '新建合集')

useHead(() => ({ title: `${pageTitle.value} - MySite` }))

const title = ref('')
const description = ref('')
const coverImage = ref('')
const sortOrder = ref(0)
const saving = ref(false)
const loading = ref(false)
const error = ref('')
const coverUploading = ref(false)

// 合集中的文章列表（可拖拽排序）
const articles = ref<CollectionArticleItem[]>([])
// 记录原始文章ID集合，用于保存时判断新增/移除
const originalArticleIds = ref<Set<string>>(new Set())
// 记录原始排序顺序，用于判断排序是否变更
const originalSortOrder = ref<string[]>([])

// 添加文章相关
const showAddArticle = ref(false)
const searchKeyword = ref('')
const searchResults = ref<ArticleListItem[]>([])
const searching = ref(false)

const existingArticleIds = computed(() => new Set(articles.value.map(a => a.id)))

async function fetchCollectionDetail(id: string) {
  loading.value = true
  try {
    // 分页加载所有文章，避免超过100篇时数据丢失
    const allArticles: CollectionArticleItem[] = []
    let currentPage = 1
    const pageSize = 100
    let hasMore = true
    while (hasMore) {
      const detail = await getCollectionById(id, currentPage, pageSize)
      if (currentPage === 1) {
        title.value = detail.title
        description.value = detail.description || ''
        coverImage.value = detail.coverImage || ''
        sortOrder.value = detail.sortOrder
      }
      const pageArticles = detail.articles || []
      allArticles.push(...pageArticles)
      hasMore = pageArticles.length === pageSize && allArticles.length < (detail.articleCount ?? 0)
      currentPage++
    }
    articles.value = allArticles
    originalArticleIds.value = new Set(articles.value.map(a => a.id))
    originalSortOrder.value = articles.value.map(a => a.id)
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
}

async function searchArticles() {
  if (!searchKeyword.value.trim()) return
  searching.value = true
  try {
    const res = await getArticles({ keyword: searchKeyword.value, size: 20 })
    searchResults.value = res.list
  } catch {
    searchResults.value = []
  } finally {
    searching.value = false
  }
}

function addArticle(article: ArticleListItem) {
  const newArticle: CollectionArticleItem = {
    id: article.id,
    title: article.title,
    summary: article.summary,
    coverImage: article.coverImage,
    authorName: article.authorName,
    authorId: article.authorId,
    viewCount: article.viewCount,
    favoriteCount: article.favoriteCount,
    readingTime: article.readingTime ?? null,
    sortOrder: articles.value.length,
    createTime: article.createTime,
  }
  articles.value.push(newArticle)
}

function removeArticle(index: number) {
  articles.value.splice(index, 1)
  // 重新编号 sortOrder
  articles.value.forEach((a, i) => { a.sortOrder = i })
}

// 拖拽排序相关
const draggedIndex = ref<number | null>(null)

function onDragStart(index: number) {
  draggedIndex.value = index
}

function onDragOver(e: DragEvent, index: number) {
  e.preventDefault()
  if (draggedIndex.value === null || draggedIndex.value === index) return
  const arr = articles.value
  const draggedItem = arr[draggedIndex.value]
  if (!draggedItem) return
  arr.splice(draggedIndex.value, 1)
  arr.splice(index, 0, draggedItem)
  draggedIndex.value = index
  arr.forEach((a, i) => { a.sortOrder = i })
}

function onDragEnd() {
  draggedIndex.value = null
}

// 简单的上下移动排序
function moveUp(index: number) {
  if (index <= 0) return
  const arr = articles.value
  const temp = arr[index]
  const prev = arr[index - 1]
  if (!temp || !prev) return
  arr[index] = prev
  arr[index - 1] = temp
  arr.forEach((a, i) => { a.sortOrder = i })
}

function moveDown(index: number) {
  if (index >= articles.value.length - 1) return
  const arr = articles.value
  const temp = arr[index]
  const next = arr[index + 1]
  if (!temp || !next) return
  arr[index] = next
  arr[index + 1] = temp
  arr.forEach((a, i) => { a.sortOrder = i })
}

// 跳转到文章编辑器新建文章，并通过 query 参数关联当前合集
function goToNewArticle() {
  if (!isEdit.value) {
    toast.info('请先保存合集后再新建文章')
    return
  }
  router.push({ name: 'post-new', query: { collection: route.params.id as string } })
}

async function handleSave() {
  if (!title.value.trim()) {
    error.value = '请输入合集标题'
    return
  }

  saving.value = true
  error.value = ''

  try {
    let collectionId: string

    if (isEdit.value && route.params.id) {
      collectionId = route.params.id as string
      await updateCollection(collectionId, {
        title: title.value.trim(),
        description: description.value.trim() || undefined,
        coverImage: coverImage.value.trim() || undefined,
        sortOrder: sortOrder.value,
      })
    } else {
      collectionId = await createCollection({
        title: title.value.trim(),
        description: description.value.trim() || undefined,
        coverImage: coverImage.value.trim() || undefined,
        sortOrder: sortOrder.value,
      })
    }

    // 计算新增和移除的文章
    const currentArticleIds = new Set(articles.value.map(a => a.id))
    const addedArticleIds: string[] = []
    const removedArticleIds: string[] = []

    for (const id of currentArticleIds) {
      if (!originalArticleIds.value.has(id)) {
        addedArticleIds.push(id)
      }
    }
    for (const id of originalArticleIds.value) {
      if (!currentArticleIds.has(id)) {
        removedArticleIds.push(id)
      }
    }

    let failedCount = 0

    // 移除文章（先移除，避免排序冲突）
    for (const articleId of removedArticleIds) {
      try {
        await removeArticleFromCollection(collectionId, articleId)
      } catch (e) {
        failedCount++
        console.warn('移除文章失败:', articleId, e)
      }
    }

    // 新增文章 - 使用批量接口提升性能
    if (addedArticleIds.length > 0) {
      try {
        await batchAddArticles(collectionId, addedArticleIds)
      } catch (e) {
        // 批量失败时回退到逐个添加，统计成功数
        console.warn('批量添加失败，尝试逐个添加:', e)
        for (const articleId of addedArticleIds) {
          try {
            await addArticleToCollection(collectionId, articleId)
          } catch (e2) {
            failedCount++
            console.warn('添加文章失败:', articleId, e2)
          }
        }
      }
    }

    // 更新文章排序 - 仅在排序变更时调用
    const currentSortOrder = articles.value.map(a => a.id)
    const sortChanged = currentSortOrder.length === originalSortOrder.value.length
      && currentSortOrder.some((id, i) => id !== originalSortOrder.value[i])
    if (sortChanged && currentSortOrder.length > 0) {
      try {
        await updateArticleSort(collectionId, currentSortOrder)
      } catch (e) {
        failedCount++
        console.warn('更新排序失败:', e)
      }
    }

    if (failedCount > 0) {
      toast.info(`合集已保存，但 ${failedCount} 项操作失败，请检查文章列表`)
    } else {
      toast.success(isEdit.value ? '合集已更新' : '合集已创建')
    }
    router.push('/dashboard/collections')
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : '保存失败'
  } finally {
    saving.value = false
  }
}

async function handleCoverUpload(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  if (!file.type.startsWith('image/')) {
    toast.error('请选择图片文件')
    return
  }

  if (file.size > MAX_IMAGE_FILE_SIZE) {
    const maxSizeMB = MAX_IMAGE_FILE_SIZE / (1024 * 1024)
    toast.error(`图片文件过大（限制: ${maxSizeMB}MB）`)
    return
  }

  coverUploading.value = true
  try {
    const result = await uploadImage(file)
    coverImage.value = result.url
  } catch (err) {
    toast.error(err instanceof Error ? err.message : '封面上传失败')
  } finally {
    coverUploading.value = false
    input.value = ''
  }
}

function removeCover() {
  coverImage.value = ''
}

onMounted(() => {
  if (isEdit.value && route.params.id) {
    fetchCollectionDetail(route.params.id as string)
  }
})
</script>

<template>
  <div class="max-w-[900px] mx-auto">
    <div class="flex items-center gap-4 mb-6">
      <button
        @click="router.back()"
        class="p-2 rounded-lg text-text-muted hover:bg-accent-subtle hover:text-accent transition-all duration-200 group"
      >
        <ArrowLeft :size="18" class="group-hover:-translate-x-0.5 transition-transform duration-200" />
      </button>
      <h1 class="text-2xl font-semibold text-text-primary">{{ pageTitle }}</h1>
    </div>

    <div v-if="error" class="mb-6 p-3 rounded-lg bg-red-50 text-red-600 text-sm flex items-center gap-2">
      <AlertCircle :size="16" />
      {{ error }}
    </div>

    <div v-if="loading" class="py-16 text-center text-text-muted">加载中...</div>

    <div v-else-if="error && isEdit" class="py-16 text-center">
      <AlertCircle :size="32" class="mx-auto text-red-400 mb-3" />
      <p class="text-text-muted mb-4">{{ error }}</p>
      <button @click="fetchCollectionDetail(route.params.id as string)" class="btn-secondary text-sm">重试</button>
    </div>

    <div v-else class="space-y-6">
      <!-- 基本信息 -->
      <div class="space-y-4 p-6 rounded-xl border border-border bg-bg-primary">
        <div>
          <label class="block text-sm font-medium text-text-primary mb-1.5">合集标题 *</label>
          <input v-model="title" type="text" placeholder="输入合集标题..." class="input-base" />
        </div>

        <div>
          <label class="block text-sm font-medium text-text-primary mb-1.5">描述</label>
          <textarea v-model="description" placeholder="合集描述（可选）..." rows="3" class="input-base resize-y" />
        </div>

        <div>
          <label class="block text-sm font-medium text-text-primary mb-1.5">封面图片</label>
          <div v-if="coverImage" class="relative group mb-2">
            <img :src="coverImage" alt="封面预览" class="w-full aspect-[2/1] object-cover rounded-lg border border-border" />
            <button @click="removeCover" class="absolute top-2 right-2 p-1 rounded-full bg-black/60 text-white opacity-0 group-hover:opacity-100 transition-opacity">
              <X :size="14" />
            </button>
          </div>
          <div class="flex gap-2">
            <label class="btn-secondary cursor-pointer text-xs flex items-center gap-1.5">
              <Loader2 v-if="coverUploading" :size="12" class="animate-spin" />
              <span v-else>📁</span>
              {{ coverUploading ? '上传中...' : '本地上传' }}
              <input type="file" accept="image/*" class="hidden" @change="handleCoverUpload" :disabled="coverUploading" />
            </label>
          </div>
          <input v-model="coverImage" type="text" placeholder="或输入图片URL" class="input-base mt-2" />
        </div>

        <div>
          <label class="block text-sm font-medium text-text-primary mb-1.5">排序序号</label>
          <input v-model.number="sortOrder" type="number" min="0" class="input-base w-32" />
        </div>
      </div>

      <!-- 文章列表管理 -->
      <div class="p-6 rounded-xl border border-border bg-bg-primary">
        <div class="flex items-center justify-between mb-4">
          <h2 class="text-lg font-semibold text-text-primary flex items-center gap-2">
            <BookOpen :size="18" class="text-accent" />
            文章列表
            <span class="text-sm font-normal text-text-muted">({{ articles.length }} 篇)</span>
          </h2>
          <div class="flex items-center gap-2">
            <button v-if="isEdit" @click="goToNewArticle" class="btn-secondary text-sm">
              <FilePlus :size="14" />
              新建文章
            </button>
            <button @click="showAddArticle = !showAddArticle" class="btn-secondary text-sm">
              <Plus :size="14" />
              添加文章
            </button>
          </div>
        </div>

        <!-- 添加文章面板 -->
        <div v-if="showAddArticle" class="mb-4 p-4 rounded-lg bg-bg-code border border-border">
          <div class="flex gap-2 mb-3">
            <input
              v-model="searchKeyword"
              type="text"
              placeholder="搜索文章..."
              class="input-base flex-1 text-sm"
              @keydown.enter="searchArticles"
            />
            <button @click="searchArticles" :disabled="searching" class="btn-secondary text-sm">
              <Loader2 v-if="searching" :size="14" class="animate-spin" />
              搜索
            </button>
          </div>
          <div v-if="searchResults.length > 0" class="space-y-2 max-h-60 overflow-y-auto">
            <div
              v-for="article in searchResults"
              :key="article.id"
              class="flex items-center justify-between p-2 rounded-lg hover:bg-accent-subtle/50 transition-colors"
            >
              <div class="min-w-0 flex-1">
                <span class="text-sm text-text-primary">{{ article.title }}</span>
                <span class="text-xs text-text-muted ml-2">{{ article.authorName }}</span>
              </div>
              <button
                v-if="!existingArticleIds.has(article.id)"
                @click="addArticle(article)"
                class="text-xs px-2 py-1 rounded border border-accent text-accent hover:bg-accent hover:text-text-inverse transition-all"
              >
                添加
              </button>
              <span v-else class="text-xs text-text-muted">已添加</span>
            </div>
          </div>
        </div>

        <!-- 文章排序列表（支持拖拽） -->
        <div v-if="articles.length > 0" class="space-y-2">
          <div
            v-for="(article, index) in articles"
            :key="article.id"
            draggable="true"
            @dragstart="onDragStart(index)"
            @dragover="onDragOver($event, index)"
            @dragend="onDragEnd"
            class="flex items-center gap-3 p-3 rounded-lg border border-border hover:border-accent/20 transition-all duration-200 group cursor-move"
            :class="{ 'border-accent bg-accent-subtle/30': draggedIndex === index }"
          >
            <div class="flex flex-col gap-0.5 shrink-0">
              <button
                @click="moveUp(index)"
                :disabled="index === 0"
                class="p-0.5 text-text-muted hover:text-accent disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
                title="上移"
              >
                <ChevronUp :size="14" />
              </button>
              <button
                @click="moveDown(index)"
                :disabled="index === articles.length - 1"
                class="p-0.5 text-text-muted hover:text-accent disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
                title="下移"
              >
                <ChevronDown :size="14" />
              </button>
            </div>

            <div class="shrink-0 text-text-muted/40 cursor-grab" title="拖拽排序">
              <GripVertical :size="14" />
            </div>

            <span class="text-xs text-text-muted w-6 text-center shrink-0">{{ index + 1 }}</span>

            <div class="flex-1 min-w-0">
              <span class="text-sm font-medium text-text-primary line-clamp-1">{{ article.title }}</span>
              <div class="text-xs text-text-muted mt-0.5">
                {{ article.authorName }}
              </div>
            </div>

            <button
              @click="removeArticle(index)"
              class="p-1.5 rounded text-text-muted hover:text-red-500 hover:bg-red-50 transition-all opacity-0 group-hover:opacity-100"
              title="移除"
            >
              <X :size="14" />
            </button>
          </div>
        </div>

        <div v-else class="py-8 text-center text-text-muted text-sm">
          暂无文章，点击上方「添加文章」按钮添加
        </div>
      </div>

      <!-- 保存按钮 -->
      <div class="flex items-center justify-end gap-3">
        <button @click="router.back()" class="btn-secondary">取消</button>
        <button @click="handleSave" :disabled="saving" class="btn-primary disabled:opacity-50">
          <Loader2 v-if="saving" :size="14" class="animate-spin" />
          <Save v-else :size="14" />
          {{ isEdit ? '保存修改' : '创建合集' }}
        </button>
      </div>
    </div>
  </div>
</template>
