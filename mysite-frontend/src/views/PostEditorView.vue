<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useHead } from '@unhead/vue'
import { Loader2, Save, ArrowLeft, FileText, ChevronRight, AlertCircle } from 'lucide-vue-next'
import { createArticle, updateArticle, getArticleById } from '@/api/article'
import { getCategories } from '@/api/category'
import { getTags } from '@/api/tag'
import { useUserStore } from '@/stores/user'
import MarkdownEditor from '@/components/editor/MarkdownEditor.vue'
import type { Category, Tag } from '@/types'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

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
        coverImage: coverImage.value.trim() || undefined,
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
        coverImage: coverImage.value.trim() || undefined,
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

function toggleMetaPanel() {
  showMetaPanel.value = !showMetaPanel.value
}

async function handleImageUpload(_file: File) {
  void _file
}
</script>

<template>
  <div class="max-w-[1400px] mx-auto">
    <div class="flex items-center justify-between mb-6">
      <div class="flex items-center gap-4">
        <button
          @click="router.back()"
          class="p-2 rounded-lg text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)] hover:bg-[var(--color-accent-light)] dark:hover:bg-[var(--color-dark-accent-light)] hover:text-[var(--color-accent)] dark:hover:text-[var(--color-dark-accent)] transition-all duration-200 group"
        >
          <ArrowLeft :size="18" class="group-hover:-translate-x-0.5 transition-transform duration-200" />
        </button>
        <h1 class="text-2xl font-semibold text-[var(--color-text-heading)] dark:text-[var(--color-dark-text-heading)]">
          {{ pageTitle }}
        </h1>
      </div>

      <div class="flex items-center gap-3">
        <button
          @click="toggleMetaPanel"
          class="btn-secondary"
          :class="{ 'bg-[var(--color-accent-light)] dark:bg-[var(--color-dark-accent-light)] text-[var(--color-accent)] dark:text-[var(--color-dark-accent)] border-[var(--color-accent)]/30 dark:border-[var(--color-dark-accent)]/30': showMetaPanel }"
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

    <div v-if="error" class="mb-6 p-3 rounded-lg bg-red-50 dark:bg-red-900/20 text-red-600 dark:text-red-400 text-sm flex items-center gap-2">
      <AlertCircle :size="16" />
      {{ error }}
    </div>

    <div v-if="loading" class="py-16 text-center text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)]">
      加载中...
    </div>

    <div v-else class="flex gap-6">
      <div class="flex-1 min-w-0 space-y-4">
        <input
          v-model="title"
          type="text"
          placeholder="文章标题..."
          class="w-full px-0 py-3 text-2xl font-semibold bg-transparent border-none outline-none text-[var(--color-text-heading)] dark:text-[var(--color-dark-text-heading)] placeholder:text-[var(--color-text-muted)] dark:placeholder:text-[var(--color-dark-text-muted)]"
        />

        <div class="h-[calc(100vh-280px)] min-h-[500px] rounded-xl border border-[var(--color-border)] dark:border-[var(--color-dark-border)] overflow-hidden bg-[var(--color-bg-card)] dark:bg-[var(--color-dark-bg-card)] card-shadow">
          <MarkdownEditor
            v-model="content"
            placeholder="用 Markdown 写文章..."
            @image-upload="handleImageUpload"
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
            <label class="block text-sm font-medium text-[var(--color-text-heading)] dark:text-[var(--color-dark-text-heading)] mb-1.5">
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
            <label class="block text-sm font-medium text-[var(--color-text-heading)] dark:text-[var(--color-dark-text-heading)] mb-1.5">
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
            <label class="block text-sm font-medium text-[var(--color-text-heading)] dark:text-[var(--color-dark-text-heading)] mb-1.5">
              标签
            </label>
            <div class="flex flex-wrap gap-2">
              <button
                v-for="tag in tags"
                :key="tag.id"
                @click="toggleTag(tag.id)"
                class="text-xs px-2.5 py-1 rounded-full border transition-all duration-200"
                :class="selectedTagIds.includes(tag.id)
                  ? 'border-[var(--color-accent)] dark:border-[var(--color-dark-accent)] bg-[var(--color-accent)] dark:bg-[var(--color-dark-accent)] text-white dark:text-[var(--color-dark-bg-primary)]'
                  : 'border-[var(--color-border)] dark:border-[var(--color-dark-border)] text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)] hover:border-[var(--color-accent)] dark:hover:border-[var(--color-dark-accent)] hover:text-[var(--color-accent)] dark:hover:text-[var(--color-dark-accent)]'"
              >
                #{{ tag.name }}
              </button>
              <span v-if="tags.length === 0" class="text-xs text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)]">
                暂无标签
              </span>
            </div>
          </div>

          <div>
            <label class="block text-sm font-medium text-[var(--color-text-heading)] dark:text-[var(--color-dark-text-heading)] mb-1.5">
              封面图片
            </label>
            <input
              v-model="coverImage"
              type="text"
              placeholder="图片URL（可选）"
              class="input-base"
            />
          </div>

          <div class="p-4 rounded-lg bg-[var(--color-bg-code)] dark:bg-[var(--color-dark-bg-code)]">
            <p class="text-xs text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)] leading-relaxed mb-2">
              编辑器支持丰富的Markdown快捷键，点击工具栏的 <strong>?</strong> 按钮查看所有快捷键。
            </p>
            <p class="text-xs text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)] leading-relaxed">
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
