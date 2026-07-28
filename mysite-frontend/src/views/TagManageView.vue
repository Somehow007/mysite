<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useHead } from '@unhead/vue'
import { Plus, Trash2, Edit, Tags, AlertCircle, TrendingUp } from 'lucide-vue-next'
import { getTags, createTag, updateTag, deleteTag } from '@/api/tag'
import { useToast } from '@/composables/useToast'
import { useConfirm } from '@/composables/useConfirm'
import { PageHeader, SearchFilterBar, Modal, DataTable, Badge } from '@/components/ui'
import type { Tag } from '@/types'
import type { Column } from '@/components/ui/DataTable.vue'

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
const { confirm } = useConfirm()

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

const tagColumns: Column<Tag>[] = [
  { key: 'name', label: '标签名称' },
  { key: 'slug', label: '别名', width: '140px' },
  { key: 'articleCount', label: '文章数', width: '100px', align: 'center' },
  { key: 'actions', label: '操作', width: '100px', align: 'center' },
]

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
  const ok = await confirm({ message: '确定要删除这个标签吗？如果标签下有文章关联则无法删除。', danger: true, confirmText: '删除' })
  if (!ok) return

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
    <PageHeader title="标签管理" subtitle="管理文章标签">
      <template #actions>
        <button @click="openCreateModal" class="btn-primary">
          <Plus :size="14" />
          新建标签
        </button>
      </template>
    </PageHeader>

    <div v-if="hotTags.length > 0" class="card-solid mb-4 px-4 py-3">
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

    <SearchFilterBar
      v-model="searchQuery"
      placeholder="搜索标签名称或别名..."
      :show-sort="false"
      :show-filter="false"
      class="mb-4"
      @search="() => {}"
      @reset="searchQuery = ''"
    />

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

    <DataTable
      v-else
      :columns="tagColumns"
      :data="filteredTags"
      :empty-title="tags.length === 0 ? '还没有标签' : '没有找到匹配的标签'"
    >
      <template #cell-name="{ item }">
        <!-- table-fixed 下弹性列：长标签名自行截断 + 悬浮全称 -->
        <div class="flex items-center gap-2 min-w-0">
          <span class="text-xs px-2 py-0.5 rounded-full border border-border text-text-muted shrink-0">#</span>
          <span class="text-sm text-text-secondary truncate" :title="item.name">{{ item.name }}</span>
        </div>
      </template>
      <template #cell-slug="{ value }">
        <span class="text-sm text-text-muted truncate block max-w-[120px]" :title="value">{{ value }}</span>
      </template>
      <template #cell-articleCount="{ value }">
        <span class="text-sm text-text-muted">{{ value || 0 }}</span>
      </template>
      <template #cell-actions="{ item }">
        <div class="flex items-center justify-center gap-1">
          <button @click="openEditModal(item)" class="p-1.5 rounded text-text-muted hover:bg-bg-code hover:text-accent transition-colors" title="编辑">
            <Edit :size="14" />
          </button>
          <button @click="handleDelete(item.id)" class="p-1.5 rounded text-text-muted hover:bg-danger-subtle hover:text-danger transition-colors" title="删除">
            <Trash2 :size="14" />
          </button>
        </div>
      </template>
    </DataTable>

    <Modal
      :open="showEditor"
      :title="editingTag ? '编辑标签' : '新建标签'"
      max-width="max-w-md"
      @update:open="showEditor = $event"
    >
      <div class="space-y-4">
        <div>
          <label class="block text-sm font-medium text-text-secondary mb-1">
            标签名称 <span class="text-danger">*</span>
          </label>
          <input
            v-model="editorForm.name"
            type="text"
            class="input-base"
            placeholder="请输入标签名称"
            @input="!editingTag && generateSlug()"
          />
        </div>

        <div>
          <label class="block text-sm font-medium text-text-secondary mb-1">
            标签别名 <span class="text-danger">*</span>
          </label>
          <input
            v-model="editorForm.slug"
            type="text"
            class="input-base"
            placeholder="URL友好的别名，如：java"
          />
          <p class="mt-1 text-xs text-text-muted">用于URL中显示，建议使用英文、数字和连字符</p>
        </div>

        <div v-if="editingTag && (editingTag.articleCount ?? 0) > 0" class="flex items-start gap-2 p-3 rounded-lg bg-warning-subtle text-warning text-sm">
          <AlertCircle :size="16" class="flex-shrink-0 mt-0.5" />
          <span>该标签下有 {{ editingTag.articleCount }} 篇文章关联，修改别名可能影响已有链接。</span>
        </div>
      </div>

      <template #footer>
        <button @click="showEditor = false" class="btn-secondary">取消</button>
        <button @click="handleSave" :disabled="saving" class="btn-primary disabled:opacity-50">
          {{ saving ? '保存中...' : '保存' }}
        </button>
      </template>
    </Modal>
  </div>
</template>
