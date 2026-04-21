<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useHead } from '@unhead/vue'
import { Loader2, Eye, Save, ArrowLeft, ImagePlus } from 'lucide-vue-next'
import { createArticle, updateArticle, getArticleById } from '@/api/article'
import { getCategories } from '@/api/category'
import { getTags } from '@/api/tag'
import { useUserStore } from '@/stores/user'
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
const published = ref(1)

const categories = ref<Category[]>([])
const tags = ref<Tag[]>([])
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const showPreview = ref(false)

const previewHtml = ref('')

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

function togglePreview() {
  showPreview.value = !showPreview.value
  if (showPreview.value && content.value) {
    renderMarkdown(content.value).then(html => {
      previewHtml.value = html
    })
  }
}

async function renderMarkdown(md: string): Promise<string> {
  try {
    const { marked } = await import('marked')
    return await marked(md)
  } catch {
    return md.replace(/\n/g, '<br>')
  }
}
</script>

<template>
  <div class="max-w-[1080px] mx-auto">
    <div class="flex items-center justify-between mb-8">
      <div class="flex items-center gap-4">
        <button
          @click="router.back()"
          class="p-2 rounded-lg text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)] hover:bg-[var(--color-bg-code)] dark:hover:bg-[var(--color-dark-bg-code)] transition-colors"
        >
          <ArrowLeft :size="18" />
        </button>
        <h1 class="text-2xl font-semibold text-[var(--color-text-heading)] dark:text-[var(--color-dark-text-heading)]">
          {{ pageTitle }}
        </h1>
      </div>

      <div class="flex items-center gap-3">
        <button
          @click="togglePreview"
          class="flex items-center gap-2 px-4 py-2 text-sm rounded-lg border border-[var(--color-border)] dark:border-[var(--color-dark-border)] text-[var(--color-text-body)] dark:text-[var(--color-dark-text-body)] hover:bg-[var(--color-bg-code)] dark:hover:bg-[var(--color-dark-bg-code)] transition-colors"
          :class="{ 'bg-[var(--color-bg-code)] dark:bg-[var(--color-dark-bg-code)]': showPreview }"
        >
          <Eye :size="14" />
          预览
        </button>
        <button
          @click="handleSave(false)"
          :disabled="saving"
          class="flex items-center gap-2 px-4 py-2 text-sm rounded-lg border border-[var(--color-border)] dark:border-[var(--color-dark-border)] text-[var(--color-text-body)] dark:text-[var(--color-dark-text-body)] hover:bg-[var(--color-bg-code)] dark:hover:bg-[var(--color-dark-bg-code)] transition-colors disabled:opacity-50"
        >
          <Loader2 v-if="saving" :size="14" class="animate-spin" />
          <Save v-else :size="14" />
          存草稿
        </button>
        <button
          @click="handleSave(true)"
          :disabled="saving"
          class="flex items-center gap-2 px-4 py-2 text-sm rounded-lg bg-[var(--color-accent)] dark:bg-[var(--color-dark-accent)] text-[var(--color-bg-card)] dark:text-[var(--color-dark-bg-card)] hover:opacity-90 transition-opacity disabled:opacity-50"
        >
          <Loader2 v-if="saving" :size="14" class="animate-spin" />
          发布
        </button>
      </div>
    </div>

    <div v-if="error" class="mb-6 p-3 rounded-lg bg-red-50 dark:bg-red-900/20 text-red-600 dark:text-red-400 text-sm">
      {{ error }}
    </div>

    <div v-if="loading" class="py-16 text-center text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)]">
      加载中...
    </div>

    <div v-else class="grid grid-cols-1 lg:grid-cols-[1fr_280px] gap-6">
      <div class="space-y-4">
        <input
          v-model="title"
          type="text"
          placeholder="文章标题..."
          class="w-full px-0 py-3 text-2xl font-semibold bg-transparent border-none outline-none text-[var(--color-text-heading)] dark:text-[var(--color-dark-text-heading)] placeholder:text-[var(--color-text-muted)] dark:placeholder:text-[var(--color-dark-text-muted)]"
        />

        <div v-if="showPreview" class="prose prose-sm max-w-none min-h-[400px] p-6 bg-[var(--color-bg-card)] dark:bg-[var(--color-dark-bg-card)] rounded-lg border border-[var(--color-border)] dark:border-[var(--color-dark-border)]" v-html="previewHtml" />

        <textarea
          v-else
          v-model="content"
          placeholder="用 Markdown 写文章..."
          class="w-full min-h-[400px] px-0 py-3 bg-transparent border-none outline-none resize-y text-[var(--color-text-body)] dark:text-[var(--color-dark-text-body)] placeholder:text-[var(--color-text-muted)] dark:placeholder:text-[var(--color-dark-text-muted)] font-mono text-sm leading-relaxed"
        />

        <div>
          <label class="block text-sm font-medium text-[var(--color-text-heading)] dark:text-[var(--color-dark-text-heading)] mb-1.5">
            摘要
          </label>
          <textarea
            v-model="summary"
            placeholder="文章摘要（可选）..."
            rows="2"
            class="w-full px-3 py-2 rounded-lg border border-[var(--color-border)] dark:border-[var(--color-dark-border)] bg-[var(--color-bg-card)] dark:bg-[var(--color-dark-bg-card)] text-[var(--color-text-body)] dark:text-[var(--color-dark-text-body)] placeholder:text-[var(--color-text-muted)] dark:placeholder:text-[var(--color-dark-text-muted)] outline-none focus:ring-2 focus:ring-[var(--color-accent)] dark:focus:ring-[var(--color-dark-accent)] focus:border-transparent text-sm resize-y"
          />
        </div>
      </div>

      <div class="space-y-6">
        <div>
          <label class="block text-sm font-medium text-[var(--color-text-heading)] dark:text-[var(--color-dark-text-heading)] mb-1.5">
            分类
          </label>
          <select
            v-model="categoryId"
            class="w-full px-3 py-2 rounded-lg border border-[var(--color-border)] dark:border-[var(--color-dark-border)] bg-[var(--color-bg-card)] dark:bg-[var(--color-dark-bg-card)] text-[var(--color-text-body)] dark:text-[var(--color-dark-text-body)] outline-none focus:ring-2 focus:ring-[var(--color-accent)] dark:focus:ring-[var(--color-dark-accent)] focus:border-transparent text-sm"
          >
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
              class="text-xs px-2.5 py-1 rounded-full border transition-colors"
              :class="selectedTagIds.includes(tag.id)
                ? 'border-[var(--color-accent)] dark:border-[var(--color-dark-accent)] bg-[var(--color-accent)] dark:bg-[var(--color-dark-accent)] text-[var(--color-bg-card)] dark:text-[var(--color-dark-bg-card)]'
                : 'border-[var(--color-border)] dark:border-[var(--color-dark-border)] text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)] hover:border-[var(--color-text-body)] dark:hover:border-[var(--color-dark-text-body)]'"
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
            class="w-full px-3 py-2 rounded-lg border border-[var(--color-border)] dark:border-[var(--color-dark-border)] bg-[var(--color-bg-card)] dark:bg-[var(--color-dark-bg-card)] text-[var(--color-text-body)] dark:text-[var(--color-dark-text-body)] placeholder:text-[var(--color-text-muted)] dark:placeholder:text-[var(--color-dark-text-muted)] outline-none focus:ring-2 focus:ring-[var(--color-accent)] dark:focus:ring-[var(--color-dark-accent)] focus:border-transparent text-sm"
          />
        </div>

        <div class="p-4 rounded-lg bg-[var(--color-bg-code)] dark:bg-[var(--color-dark-bg-code)]">
          <p class="text-xs text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)] leading-relaxed">
            支持 Markdown 语法。标题使用 #，粗体使用 **文字**，链接使用 [文字](URL)，代码使用 \`code\`。
          </p>
        </div>
      </div>
    </div>
  </div>
</template>
