<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useHead } from '@unhead/vue'
import { Loader2, Save, ArrowLeft, FileText, ChevronRight, AlertCircle, X, Plus, Search, TrendingUp } from 'lucide-vue-next'
import { createArticle, updateArticle, getArticleById } from '@/api/article'
import { getCategories } from '@/api/category'
import { getTags, createTag } from '@/api/tag'
import { uploadImage, MAX_IMAGE_FILE_SIZE } from '@/api/image'
import { useUserStore } from '@/stores/user'
import { useToast } from '@/composables/useToast'
import MarkdownEditor from '@/components/editor/MarkdownEditor.vue'
import type { Category, Tag } from '@/types'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const toast = useToast()

const isEdit = computed(() => !!route.params.id)
const pageTitle = computed(() => isEdit.value ? '编辑文章' : '写文章')

useHead(() => ({
  title: `${pageTitle.value} - MySite`,
}))

const title = ref('')
const content = ref('')
const summary = ref('')
const coverImage = ref('')
const categoryId = ref('')
const selectedTagIds = ref<string[]>([])

const categories = ref<Category[]>([])
const tags = ref<Tag[]>([])
const loading = ref(false)
const saving = ref(false)
const error = ref('')

const showMetaPanel = ref(false)
const showSummaryHint = ref(false)
const coverUploading = ref(false)

const tagSearchQuery = ref('')
const newTagName = ref('')
const newTagSlug = ref('')
const creatingTag = ref(false)

const filteredTags = computed(() => {
  if (!tagSearchQuery.value.trim()) return tags.value
  const q = tagSearchQuery.value.trim().toLowerCase()
  return tags.value.filter(
    t => t.name.toLowerCase().includes(q) || t.slug.toLowerCase().includes(q)
  )
})

const hotTags = computed(() =>
  [...tags.value]
    .filter(t => (t.articleCount ?? 0) > 0)
    .sort((a, b) => (b.articleCount ?? 0) - (a.articleCount ?? 0))
    .slice(0, 5)
)

onMounted(async () => {
  loading.value = true
  try {
    const [catsRes, tagsRes] = await Promise.all([
      getCategories(),
      getTags(),
    ])
    categories.value = catsRes
    tags.value = tagsRes

    if (isEdit.value && route.params.id) {
      const article = await getArticleById(route.params.id as string)
      title.value = article.title
      content.value = article.content || ''
      summary.value = article.summary || ''
      coverImage.value = article.coverImage || ''
      categoryId.value = article.categoryId || ''
      if (article.tags) {
        selectedTagIds.value = article.tags.map(t => t.id)
      }
      if (summary.value || categoryId.value || coverImage.value || selectedTagIds.value.length > 0) {
        showMetaPanel.value = true
      }
    }
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
})

async function handleSave(isPublish: boolean) {
  if (!title.value.trim()) {
    error.value = '请输入文章标题'
    return
  }
  if (!content.value.trim()) {
    error.value = '请输入文章内容'
    return
  }

  if (isPublish && !summary.value.trim()) {
    showMetaPanel.value = true
    showSummaryHint.value = true
    error.value = '发布文章前，建议填写文章摘要'
    setTimeout(() => {
      showSummaryHint.value = false
    }, 3000)
    return
  }

  saving.value = true
  error.value = ''

  try {
    const userId = userStore.user?.id
    if (!userId) {
      error.value = '请先登录'
      return
    }

    if (isEdit.value && route.params.id) {
      await updateArticle({
        id: route.params.id as string,
        title: title.value.trim(),
        content: content.value,
        summary: summary.value.trim() || undefined,
        coverImage: coverImage.value.trim() || null,
        categoryId: categoryId.value || undefined,
        tagIds: selectedTagIds.value.length > 0 ? selectedTagIds.value : undefined,
        published: isPublish ? 1 : 0,
      })
    } else {
      await createArticle({
        title: title.value.trim(),
        content: content.value,
        authorId: userId,
        summary: summary.value.trim() || undefined,
        coverImage: coverImage.value.trim() || null,
        categoryId: categoryId.value || undefined,
        tagIds: selectedTagIds.value.length > 0 ? selectedTagIds.value : undefined,
        published: isPublish ? 1 : 0,
      })
    }

    router.push('/dashboard')
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : '保存失败'
  } finally {
    saving.value = false
  }
}

function toggleTag(tagId: string) {
  const idx = selectedTagIds.value.indexOf(tagId)
  if (idx >= 0) {
    selectedTagIds.value.splice(idx, 1)
  } else {
    selectedTagIds.value.push(tagId)
  }
}

async function handleCreateTag() {
  if (!newTagName.value.trim()) {
    toast.error('请输入标签名称')
    return
  }
  const slug = newTagSlug.value.trim() || newTagName.value.trim()
    .toLowerCase()
    .replace(/\s+/g, '-')
    .replace(/[^\w\u4e00-\u9fa5-]/g, '')

  if (!slug) {
    toast.error('请输入标签别名')
    return
  }

  creatingTag.value = true
  try {
    const created = await createTag({
      name: newTagName.value.trim(),
      slug,
    })
    tags.value.push(created)
    selectedTagIds.value.push(created.id)
    newTagName.value = ''
    newTagSlug.value = ''
    toast.success('标签已创建')
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : '创建标签失败'
    toast.error(msg)
  } finally {
    creatingTag.value = false
  }
}

function toggleMetaPanel() {
  showMetaPanel.value = !showMetaPanel.value
}

async function handleCoverUpload(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  if (!file.type.startsWith('image/')) return
  
  if (file.size > MAX_IMAGE_FILE_SIZE) {
    const maxSizeMB = MAX_IMAGE_FILE_SIZE / (1024 * 1024)
    const fileSizeMB = (file.size / (1024 * 1024)).toFixed(2)
    toast.error(`图片文件过大（当前: ${fileSizeMB}MB，限制: ${maxSizeMB}MB）`)
    return
  }
  
  coverUploading.value = true
  try {
    const result = await uploadImage(file, (progressEvent) => {
      if (progressEvent.total) {
        const progress = Math.round((progressEvent.loaded * 100) / progressEvent.total)
        console.log(`上传进度: ${progress}%`)
      }
    })
    coverImage.value = result.url
  } catch (err) {
    const errorMessage = err instanceof Error ? err.message : '封面上传失败'
    if (errorMessage.includes('413') || errorMessage.includes('过大')) {
      const maxSizeMB = MAX_IMAGE_FILE_SIZE / (1024 * 1024)
      const fileSizeMB = (file.size / (1024 * 1024)).toFixed(2)
      error.value = `图片文件过大（当前: ${fileSizeMB}MB，限制: ${maxSizeMB}MB）`
    } else {
      error.value = errorMessage
    }
  } finally {
    coverUploading.value = false
    input.value = ''
  }
}

function removeCover() {
  coverImage.value = ''
}
</script>

<template>
  <div class="max-w-[1400px] mx-auto">
    <div class="flex items-center justify-between mb-6">
      <div class="flex items-center gap-4">
        <button
          @click="router.back()"
          class="p-2 rounded-lg text-text-muted hover:bg-accent-subtle hover:text-accent transition-all duration-200 group"
        >
          <ArrowLeft :size="18" class="group-hover:-translate-x-0.5 transition-transform duration-200" />
        </button>
        <h1 class="text-2xl font-semibold text-text-primary">
          {{ pageTitle }}
        </h1>
      </div>

      <div class="flex items-center gap-3">
        <button
          @click="toggleMetaPanel"
          class="btn-secondary"
          :class="{ 'bg-accent-subtle text-accent border-accent/30': showMetaPanel }"
          :aria-expanded="showMetaPanel"
          :aria-label="showMetaPanel ? '收起文章信息面板' : '展开文章信息面板'"
        >
          <FileText :size="14" />
          <span class="hidden sm:inline">{{ showMetaPanel ? '收起' : '文章信息' }}</span>
          <ChevronRight
            :size="14"
            class="transition-transform duration-200"
            :class="{ 'rotate-90': showMetaPanel }"
          />
        </button>
        <button
          @click="handleSave(false)"
          :disabled="saving"
          class="btn-secondary disabled:opacity-50"
        >
          <Loader2 v-if="saving" :size="14" class="animate-spin" />
          <Save v-else :size="14" />
          存草稿
        </button>
        <button
          @click="handleSave(true)"
          :disabled="saving"
          class="btn-primary disabled:opacity-50"
        >
          <Loader2 v-if="saving" :size="14" class="animate-spin" />
          发布
        </button>
      </div>
    </div>

    <div v-if="error" class="mb-6 p-3 rounded-lg bg-red-50 text-red-600 text-sm flex items-center gap-2">
      <AlertCircle :size="16" />
      {{ error }}
    </div>

    <div v-if="loading" class="py-16 text-center text-text-muted">
      加载中...
    </div>

    <div v-else class="flex gap-6">
      <div class="flex-1 min-w-0 space-y-4">
        <input
          v-model="title"
          type="text"
          placeholder="文章标题..."
          class="w-full px-0 py-3 text-2xl font-semibold bg-transparent border-none outline-none text-text-primary placeholder:text-text-muted"
        />

        <div class="h-[calc(100vh-280px)] min-h-[500px] rounded-xl border border-border overflow-hidden bg-bg-secondary card-shadow">
          <MarkdownEditor
            v-model="content"
            placeholder="用 Markdown 写文章..."
            @save="handleSave(false)"
          />
        </div>
      </div>

      <transition
        name="slide"
        @enter="showMetaPanel = true"
        @leave="showMetaPanel = false"
      >
        <div
          v-show="showMetaPanel"
          class="w-80 flex-shrink-0 space-y-6 overflow-y-auto max-h-[calc(100vh-200px)]"
          role="region"
          aria-label="文章元数据"
        >
          <div>
            <label class="block text-sm font-medium text-text-primary mb-1.5">
              摘要
              <span v-if="showSummaryHint" class="text-red-500 ml-1">*</span>
            </label>
            <textarea
              v-model="summary"
              placeholder="文章摘要（建议填写）..."
              rows="3"
              class="input-base resize-y"
              :class="{ 'ring-2 ring-red-500': showSummaryHint }"
            />
          </div>

          <div>
            <label class="block text-sm font-medium text-text-primary mb-1.5">
              分类
            </label>
            <select v-model="categoryId" class="input-base">
              <option value="">未分类</option>
              <option v-for="cat in categories" :key="cat.id" :value="cat.id">
                {{ cat.name }}
              </option>
            </select>
          </div>

          <div>
            <label class="block text-sm font-medium text-text-primary mb-1.5">
              标签
            </label>

            <div v-if="selectedTagIds.length > 0" class="flex flex-wrap gap-1.5 mb-3">
              <span
                v-for="tagId in selectedTagIds"
                :key="tagId"
                class="inline-flex items-center gap-1 text-xs px-2 py-1 rounded-full border border-accent bg-accent text-text-inverse"
              >
                #{{ tags.find(t => t.id === tagId)?.name || tagId }}
                <button @click="toggleTag(tagId)" class="hover:opacity-70 transition-opacity">
                  <X :size="10" />
                </button>
              </span>
            </div>

            <div v-if="hotTags.length > 0" class="mb-3">
              <div class="flex items-center gap-1.5 mb-1.5">
                <TrendingUp :size="12" class="text-accent" />
                <span class="text-xs text-text-muted">热门标签</span>
              </div>
              <div class="flex flex-wrap gap-1.5">
                <button
                  v-for="tag in hotTags"
                  :key="'hot-' + tag.id"
                  @click="toggleTag(tag.id)"
                  class="text-xs px-2 py-1 rounded-full border transition-all duration-200"
                  :class="selectedTagIds.includes(tag.id)
                    ? 'border-accent bg-accent text-text-inverse'
                    : 'border-accent/30 bg-accent-subtle text-accent hover:border-accent hover:bg-accent hover:text-text-inverse'"
                >
                  #{{ tag.name }}
                </button>
              </div>
            </div>

            <div class="relative mb-2">
              <Search :size="14" class="absolute left-2.5 top-1/2 -translate-y-1/2 text-text-muted" />
              <input
                v-model="tagSearchQuery"
                type="text"
                placeholder="搜索标签..."
                class="input-base pl-8 text-xs"
              />
            </div>

            <div class="flex flex-wrap gap-1.5 mb-3 max-h-32 overflow-y-auto">
              <button
                v-for="tag in filteredTags"
                :key="tag.id"
                @click="toggleTag(tag.id)"
                class="text-xs px-2.5 py-1 rounded-full border transition-all duration-200"
                :class="selectedTagIds.includes(tag.id)
                  ? 'border-accent bg-accent text-text-inverse'
                  : 'border-border text-text-muted hover:border-accent hover:text-accent'"
              >
                #{{ tag.name }}
              </button>
              <span v-if="filteredTags.length === 0" class="text-xs text-text-muted">
                {{ tagSearchQuery ? '没有匹配的标签' : '暂无标签' }}
              </span>
            </div>

            <div class="border-t border-border pt-3">
              <p class="text-xs text-text-muted mb-2">创建新标签</p>
              <div class="flex gap-2">
                <input
                  v-model="newTagName"
                  type="text"
                  placeholder="标签名称"
                  class="input-base text-xs flex-1"
                  @keydown.enter="handleCreateTag"
                />
                <input
                  v-model="newTagSlug"
                  type="text"
                  placeholder="别名(可选)"
                  class="input-base text-xs w-24"
                  @keydown.enter="handleCreateTag"
                />
                <button
                  @click="handleCreateTag"
                  :disabled="creatingTag || !newTagName.trim()"
                  class="p-2 rounded-lg border border-border text-text-muted hover:bg-accent-subtle hover:text-accent hover:border-accent transition-all duration-200 disabled:opacity-50 disabled:cursor-not-allowed"
                  title="创建标签"
                >
                  <Loader2 v-if="creatingTag" :size="14" class="animate-spin" />
                  <Plus v-else :size="14" />
                </button>
              </div>
            </div>
          </div>

          <div>
            <label class="block text-sm font-medium text-text-primary mb-1.5">
              封面图片
            </label>
            <div v-if="coverImage" class="relative group mb-2">
              <img :src="coverImage" alt="封面预览" class="w-full aspect-[2/1] object-cover rounded-lg border border-border" />
              <button @click="removeCover" class="absolute top-2 right-2 p-1 rounded-full bg-black/60 text-white opacity-0 group-hover:opacity-100 transition-opacity" title="移除封面">
                <X :size="14" />
              </button>
            </div>
            <div class="flex gap-2 mb-2">
              <label class="btn-secondary cursor-pointer text-xs flex items-center gap-1.5">
                <Loader2 v-if="coverUploading" :size="12" class="animate-spin" />
                <span v-else>📁</span>
                {{ coverUploading ? '上传中...' : '本地上传' }}
                <input type="file" accept="image/*" class="hidden" @change="handleCoverUpload" :disabled="coverUploading" />
              </label>
            </div>
            <input
              v-model="coverImage"
              type="text"
              placeholder="或输入图片URL"
              class="input-base"
            />
          </div>

          <div class="p-4 rounded-lg bg-bg-code">
            <p class="text-xs text-text-muted leading-relaxed mb-2">
              编辑器支持丰富的Markdown快捷键，点击工具栏的 <strong>?</strong> 按钮查看所有快捷键。
            </p>
            <p class="text-xs text-text-muted leading-relaxed">
              支持实时预览、语法高亮和自动补全功能。
            </p>
          </div>
        </div>
      </transition>
    </div>
  </div>
</template>

<style scoped>
.slide-enter-active,
.slide-leave-active {
  transition: all 0.3s var(--ease-out);
}

.slide-enter-from,
.slide-leave-to {
  opacity: 0;
  transform: translateX(20px);
}
</style>
