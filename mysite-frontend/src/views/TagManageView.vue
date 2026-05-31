<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useHead } from '@unhead/vue'
import { Plus, Trash2, Edit, Tags, Search, X, AlertCircle, TrendingUp } from 'lucide-vue-next'
import { getTags, createTag, updateTag, deleteTag } from '@/api/tag'
import { useToast } from '@/composables/useToast'
import type { Tag } from '@/types'

useHead(() => ({
  title: '标签管理 - MySite',
}))

const tags = ref<Tag[]>([])
const loading = ref(false)
const showEditor = ref(false)
const editingTag = ref<Tag | null>(null)
const saving = ref(false)
const searchQuery = ref('')
const toast = useToast()

const editorForm = ref({
  name: '',
  slug: '',
})

const filteredTags = computed(() => {
  if (!searchQuery.value.trim()) return tags.value
  const q = searchQuery.value.trim().toLowerCase()
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

async function fetchTags() {
  loading.value = true
  try {
    tags.value = await getTags()
  } catch {
    tags.value = []
  } finally {
    loading.value = false
  }
}

function openCreateModal() {
  editingTag.value = null
  editorForm.value = { name: '', slug: '' }
  showEditor.value = true
}

function openEditModal(tag: Tag) {
  editingTag.value = tag
  editorForm.value = { name: tag.name, slug: tag.slug }
  showEditor.value = true
}

function generateSlug() {
  if (!editorForm.value.name) return
  editorForm.value.slug = editorForm.value.name
    .toLowerCase()
    .replace(/\s+/g, '-')
    .replace(/[^\w\u4e00-\u9fa5-]/g, '')
}

async function handleSave() {
  if (!editorForm.value.name.trim()) {
    toast.error('标签名称不能为空')
    return
  }
  if (!editorForm.value.slug.trim()) {
    toast.error('标签别名不能为空')
    return
  }

  saving.value = true
  try {
    if (editingTag.value) {
      await updateTag(editingTag.value.id, {
        name: editorForm.value.name.trim(),
        slug: editorForm.value.slug.trim(),
      })
      toast.success('标签已更新')
    } else {
      await createTag({
        name: editorForm.value.name.trim(),
        slug: editorForm.value.slug.trim(),
      })
      toast.success('标签已创建')
    }
    showEditor.value = false
    await fetchTags()
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : '保存失败'
    toast.error(msg)
  } finally {
    saving.value = false
  }
}

async function handleDelete(id: string) {
  if (!confirm('确定要删除这个标签吗？如果标签下有文章关联则无法删除。')) return

  try {
    await deleteTag(id)
    toast.success('标签已删除')
    await fetchTags()
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : '删除失败'
    toast.error(msg)
  }
}

onMounted(() => {
  fetchTags()
})
</script>

<template>
  <div>
    <div class="flex items-center justify-between mb-8">
      <h1 class="text-2xl font-semibold text-text-primary">
        标签管理
      </h1>
      <button
        @click="openCreateModal"
        class="btn-primary"
      >
        <Plus :size="14" />
        新建标签
      </button>
    </div>

    <div v-if="hotTags.length > 0" class="mb-6 p-4 rounded-xl glass glass-sm">
      <div class="flex items-center gap-2 mb-3">
        <TrendingUp :size="16" class="text-accent" />
        <span class="text-sm font-medium text-text-primary">热门标签</span>
      </div>
      <div class="flex flex-wrap gap-2">
        <span
          v-for="tag in hotTags"
          :key="tag.id"
          class="text-xs px-2.5 py-1 rounded-full border border-accent/30 bg-accent-subtle text-accent transition-all duration-200"
        >
          #{{ tag.name }}
          <span class="ml-1 opacity-70">({{ tag.articleCount }})</span>
        </span>
      </div>
    </div>

    <div class="mb-4 relative">
      <Search :size="16" class="absolute left-3 top-1/2 -translate-y-1/2 text-text-muted" />
      <input
        v-model="searchQuery"
        type="text"
        placeholder="搜索标签..."
        class="input-base pl-9 pr-8"
      />
      <button
        v-if="searchQuery"
        @click="searchQuery = ''"
        class="absolute right-3 top-1/2 -translate-y-1/2 text-text-muted hover:text-text-secondary transition-colors"
      >
        <X :size="14" />
      </button>
    </div>

    <div v-if="loading" class="py-16 text-center text-text-muted">
      加载中...
    </div>

    <div v-else-if="tags.length === 0" class="py-16 text-center">
      <Tags :size="48" class="mx-auto mb-4 text-text-muted" />
      <p class="text-text-muted">还没有标签，开始创建第一个吧</p>
      <button
        @click="openCreateModal"
        class="btn-primary mt-4"
      >
        新建标签
      </button>
    </div>

    <div v-else-if="filteredTags.length === 0" class="py-16 text-center">
      <Search :size="48" class="mx-auto mb-4 text-text-muted" />
      <p class="text-text-muted">没有找到匹配的标签</p>
    </div>

    <div v-else class="rounded-lg border border-border bg-bg-secondary overflow-hidden">
      <div class="flex items-center gap-3 px-4 py-3 border-b border-border bg-bg-code">
        <span class="text-sm font-medium text-text-secondary flex-1">标签名称</span>
        <span class="text-sm text-text-muted w-24 text-center">别名</span>
        <span class="text-sm text-text-muted w-20 text-center">文章数</span>
        <span class="text-sm text-text-muted w-20 text-center">操作</span>
      </div>

      <div class="divide-y divide-border">
        <div
          v-for="tag in filteredTags"
          :key="tag.id"
          class="flex items-center gap-3 px-4 py-3 hover:bg-bg-code transition-colors"
        >
          <div class="flex-1 flex items-center gap-2">
            <span class="text-xs px-2 py-0.5 rounded-full border border-border text-text-muted">#</span>
            <span class="text-sm text-text-secondary">{{ tag.name }}</span>
          </div>
          <span class="text-sm text-text-muted w-24 text-center truncate" :title="tag.slug">
            {{ tag.slug }}
          </span>
          <span class="text-sm text-text-muted w-20 text-center">
            {{ tag.articleCount || 0 }}
          </span>
          <div class="w-20 flex items-center justify-center gap-1">
            <button
              @click="openEditModal(tag)"
              class="p-1.5 rounded text-text-muted hover:bg-bg-code hover:text-accent transition-colors"
              title="编辑"
            >
              <Edit :size="14" />
            </button>
            <button
              @click="handleDelete(tag.id)"
              class="p-1.5 rounded text-text-muted hover:bg-red-50 hover:text-red-500 transition-colors"
              title="删除"
            >
              <Trash2 :size="14" />
            </button>
          </div>
        </div>
      </div>
    </div>

    <div
      v-if="showEditor"
      class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm"
      @click.self="showEditor = false"
    >
      <div class="w-full max-w-md glass glass-lg rounded-2xl animate-scale-in">
        <div class="flex items-center justify-between px-6 py-4 border-b border-border">
          <h2 class="text-lg font-semibold text-text-primary">
            {{ editingTag ? '编辑标签' : '新建标签' }}
          </h2>
          <button
            @click="showEditor = false"
            class="p-2 rounded-lg text-text-muted hover:bg-accent-subtle transition-all duration-200"
          >
            ✕
          </button>
        </div>

        <div class="p-6 space-y-4">
          <div>
            <label class="block text-sm font-medium text-text-secondary mb-1">
              标签名称 <span class="text-red-500">*</span>
            </label>
            <input
              v-model="editorForm.name"
              type="text"
              class="w-full px-3 py-2 rounded-lg border border-border bg-bg-secondary text-text-secondary focus:outline-none focus:ring-2 focus:ring-accent"
              placeholder="请输入标签名称"
              @input="!editingTag && generateSlug()"
            />
          </div>

          <div>
            <label class="block text-sm font-medium text-text-secondary mb-1">
              标签别名 <span class="text-red-500">*</span>
            </label>
            <input
              v-model="editorForm.slug"
              type="text"
              class="w-full px-3 py-2 rounded-lg border border-border bg-bg-secondary text-text-secondary focus:outline-none focus:ring-2 focus:ring-accent"
              placeholder="URL友好的别名，如：java"
            />
            <p class="mt-1 text-xs text-text-muted">用于URL中显示，建议使用英文、数字和连字符</p>
          </div>

          <div v-if="editingTag && (editingTag.articleCount ?? 0) > 0" class="flex items-start gap-2 p-3 rounded-lg bg-amber-50 text-amber-700 text-sm">
            <AlertCircle :size="16" class="flex-shrink-0 mt-0.5" />
            <span>该标签下有 {{ editingTag.articleCount }} 篇文章关联，修改别名可能影响已有链接。</span>
          </div>
        </div>

        <div class="flex items-center justify-end gap-2 px-6 py-4 border-t border-border">
          <button
            @click="showEditor = false"
            class="btn-secondary"
          >
            取消
          </button>
          <button
            @click="handleSave"
            :disabled="saving"
            class="btn-primary disabled:opacity-50"
          >
            {{ saving ? '保存中...' : '保存' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
