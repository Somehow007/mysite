<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useHead } from '@unhead/vue'
import { Plus, Trash2, FolderTree, Settings } from 'lucide-vue-next'
import {
  getCategoryTree,
  createCategory,
  updateCategory,
  deleteCategory,
  updateCategoryStatus,
  batchDelete,
  type CategoryCreateData,
  type CategoryUpdateData,
} from '@/api/category'
import type { Category } from '@/types'
import { useToast } from '@/composables/useToast'
import { useConfirm } from '@/composables/useConfirm'
import CategoryTreeNode from '@/components/ui/CategoryTreeNode.vue'
import { Modal, PageHeader } from '@/components/ui'

const { success, error: toastError } = useToast()
const { confirm } = useConfirm()

useHead(() => ({
  title: '分类管理 - MySite',
}))

const categories = ref<Category[]>([])
const loading = ref(false)
const showEditor = ref(false)
const editingCategory = ref<Category | null>(null)
const saving = ref(false)
const expandedIds = ref<Set<string>>(new Set())
const selectedIds = ref<Set<string>>(new Set())

const editorForm = ref<CategoryCreateData>({
  name: '',
  slug: '',
  description: '',
  sortOrder: 0,
  parentId: undefined,
  level: 1,
  status: 1,
  icon: '',
  color: '',
  seoTitle: '',
  seoDescription: '',
  seoKeywords: '',
})

const parentOptions = computed(() => {
  const options: { id: string | undefined; name: string; level: number }[] = [
    { id: undefined, name: '无（顶级分类）', level: 0 },
  ]

  function addOptions(cats: Category[], level = 1) {
    for (const cat of cats) {
      if (cat.level < 3) {
        options.push({ id: cat.id, name: cat.name, level })
        if (cat.children && cat.children.length > 0) {
          addOptions(cat.children, level + 1)
        }
      }
    }
  }

  addOptions(categories.value)
  return options
})

async function fetchCategories() {
  loading.value = true
  try {
    categories.value = await getCategoryTree()
  } catch {
    categories.value = []
  } finally {
    loading.value = false
  }
}

function openCreateModal(parentId?: string) {
  editingCategory.value = null
  editorForm.value = {
    name: '',
    slug: '',
    description: '',
    sortOrder: 0,
    parentId,
    level: parentId ? 2 : 1,
    status: 1,
    icon: '',
    color: '',
    seoTitle: '',
    seoDescription: '',
    seoKeywords: '',
  }
  showEditor.value = true
}

function openEditModal(category: Category) {
  editingCategory.value = category
  editorForm.value = {
    name: category.name,
    slug: category.slug,
    description: category.description || '',
    sortOrder: category.sortOrder || 0,
    parentId: category.parentId,
    level: category.level,
    status: category.status,
    icon: category.icon || '',
    color: category.color || '',
    seoTitle: category.seoTitle || '',
    seoDescription: category.seoDescription || '',
    seoKeywords: category.seoKeywords || '',
  }
  showEditor.value = true
}

async function handleSave() {
  if (!editorForm.value.name || !editorForm.value.slug) {
    toastError('分类名称和别名不能为空')
    return
  }

  saving.value = true
  try {
    if (editingCategory.value) {
      await updateCategory(editingCategory.value.id, editorForm.value as CategoryUpdateData)
    } else {
      await createCategory(editorForm.value)
    }
    showEditor.value = false
    success('保存成功')
    await fetchCategories()
  } catch (error) {
    toastError(error instanceof Error ? error.message : '保存失败')
  } finally {
    saving.value = false
  }
}

async function handleDelete(id: string) {
  const ok = await confirm({ message: '确定要删除这个分类吗？', danger: true, confirmText: '删除' })
  if (!ok) return

  try {
    await deleteCategory(id)
    success('删除成功')
    await fetchCategories()
  } catch (error) {
    toastError(error instanceof Error ? error.message : '删除失败')
  }
}

async function handleToggleStatus(category: Category) {
  try {
    await updateCategoryStatus(category.id, category.status === 1 ? 0 : 1)
    success('操作成功')
    await fetchCategories()
  } catch (error) {
    toastError(error instanceof Error ? error.message : '操作失败')
  }
}

async function handleBatchDelete() {
  if (selectedIds.value.size === 0) {
    toastError('请选择要删除的分类')
    return
  }

  const ok = await confirm({
    message: `确定要删除选中的 ${selectedIds.value.size} 个分类吗？`,
    danger: true,
    confirmText: '删除',
  })
  if (!ok) return

  try {
    await batchDelete(Array.from(selectedIds.value))
    selectedIds.value.clear()
    success('批量删除成功')
    await fetchCategories()
  } catch (error) {
    toastError(error instanceof Error ? error.message : '删除失败')
  }
}

function toggleExpand(id: string) {
  if (expandedIds.value.has(id)) {
    expandedIds.value.delete(id)
  } else {
    expandedIds.value.add(id)
  }
}

function toggleSelect(id: string) {
  if (selectedIds.value.has(id)) {
    selectedIds.value.delete(id)
  } else {
    selectedIds.value.add(id)
  }
}

function toggleSelectAll() {
  const allIds = getAllCategoryIds(categories.value)
  if (selectedIds.value.size === allIds.length) {
    selectedIds.value.clear()
  } else {
    selectedIds.value = new Set(allIds)
  }
}

function getAllCategoryIds(cats: Category[]): string[] {
  const ids: string[] = []
  for (const cat of cats) {
    ids.push(cat.id)
    if (cat.children && cat.children.length > 0) {
      ids.push(...getAllCategoryIds(cat.children))
    }
  }
  return ids
}

onMounted(() => {
  fetchCategories()
})
</script>

<template>
  <div>
    <PageHeader title="分类管理" subtitle="管理文章分类层级结构">
      <template #actions>
        <button
          v-if="selectedIds.size > 0"
          @click="handleBatchDelete"
          class="btn-secondary !text-danger !border-danger hover:bg-danger-subtle"
        >
          <Trash2 :size="14" />
          删除选中 ({{ selectedIds.size }})
        </button>
        <button
          @click="openCreateModal()"
          class="btn-primary"
        >
          <Plus :size="14" />
          新建分类
        </button>
      </template>
    </PageHeader>

    <div v-if="loading" class="py-16 text-center text-text-muted">
      加载中...
    </div>

    <div v-else-if="categories.length === 0" class="py-16 text-center">
      <FolderTree :size="48" class="mx-auto mb-4 text-text-muted" />
      <p class="text-text-muted">还没有分类，开始创建第一个吧</p>
      <button
        @click="openCreateModal()"
        class="btn-primary"
      >
        新建分类
      </button>
    </div>

    <div v-else class="rounded-lg border border-border bg-bg-secondary overflow-hidden">
      <div class="flex items-center gap-3 px-4 py-3 border-b border-border bg-bg-code">
        <input
          type="checkbox"
          :checked="selectedIds.size === getAllCategoryIds(categories).length"
          @change="toggleSelectAll"
          class="w-4 h-4 rounded border-border"
        />
        <span class="text-sm font-medium text-text-secondary">分类名称</span>
        <span class="ml-auto text-sm text-text-muted">文章数</span>
        <span class="ml-4 text-sm text-text-muted">状态</span>
        <span class="ml-4 text-sm text-text-muted">操作</span>
      </div>

      <div class="divide-y divide-border">
        <CategoryTreeNode
          v-for="category in categories"
          :key="category.id"
          :category="category"
          :depth="0"
          :expanded-ids="expandedIds"
          :selected-ids="selectedIds"
          @toggle-expand="toggleExpand"
          @toggle-select="toggleSelect"
          @add-child="(cat: Category) => openCreateModal(cat.id)"
          @edit="openEditModal"
          @delete="handleDelete"
          @toggle-status="handleToggleStatus"
        />
      </div>
    </div>

    <Modal
      :open="showEditor"
      :title="editingCategory ? '编辑分类' : '新建分类'"
      max-width="max-w-2xl"
      @update:open="showEditor = $event"
    >
      <div class="space-y-4">
        <div>
          <label class="block text-sm font-medium text-text-secondary mb-1">
            分类名称 <span class="text-danger">*</span>
          </label>
          <input
            v-model="editorForm.name"
            type="text"
            class="input-base"
            placeholder="请输入分类名称"
          />
        </div>

        <div>
          <label class="block text-sm font-medium text-text-secondary mb-1">
            分类别名 <span class="text-danger">*</span>
          </label>
          <input
            v-model="editorForm.slug"
            type="text"
            class="input-base"
            placeholder="URL友好的别名，如：tech"
          />
        </div>

        <div>
          <label class="block text-sm font-medium text-text-secondary mb-1">
            父分类
          </label>
          <select
            v-model="editorForm.parentId"
            class="input-base"
          >
            <option
              v-for="option in parentOptions"
              :key="String(option.id)"
              :value="option.id"
              :disabled="!!editingCategory && option.id === editingCategory.id"
            >
              {{ '&nbsp;&nbsp;'.repeat(option.level) }}{{ option.name }}
            </option>
          </select>
        </div>

        <div>
          <label class="block text-sm font-medium text-text-secondary mb-1">
            分类描述
          </label>
          <textarea
            v-model="editorForm.description"
            rows="3"
            class="input-base"
            placeholder="分类描述（可选）"
          />
        </div>

        <div class="grid grid-cols-2 gap-4">
          <div>
            <label class="block text-sm font-medium text-text-secondary mb-1">
              排序
            </label>
            <input
              v-model.number="editorForm.sortOrder"
              type="number"
              class="input-base"
            />
          </div>

          <div>
            <label class="block text-sm font-medium text-text-secondary mb-1">
              状态
            </label>
            <select
              v-model.number="editorForm.status"
              class="input-base"
            >
              <option :value="1">启用</option>
              <option :value="0">禁用</option>
            </select>
          </div>
        </div>

        <div class="grid grid-cols-2 gap-4">
          <div>
            <label class="block text-sm font-medium text-text-secondary mb-1">
              图标
            </label>
            <input
              v-model="editorForm.icon"
              type="text"
              class="input-base"
              placeholder="图标名称或URL"
            />
          </div>

          <div>
            <label class="block text-sm font-medium text-text-secondary mb-1">
              颜色
            </label>
            <input
              v-model="editorForm.color"
              type="text"
              class="input-base"
              placeholder="#1890ff"
            />
          </div>
        </div>

        <div class="pt-4 border-t border-border">
          <h3 class="text-sm font-medium text-text-secondary mb-3 flex items-center gap-2">
            <Settings :size="14" />
            SEO设置
          </h3>

          <div class="space-y-4">
            <div>
              <label class="block text-sm font-medium text-text-secondary mb-1">
                SEO标题
              </label>
              <input
                v-model="editorForm.seoTitle"
                type="text"
                class="input-base"
                placeholder="SEO标题（可选）"
              />
            </div>

            <div>
              <label class="block text-sm font-medium text-text-secondary mb-1">
                SEO描述
              </label>
              <textarea
                v-model="editorForm.seoDescription"
                rows="2"
                class="input-base"
                placeholder="SEO描述（可选）"
              />
            </div>

            <div>
              <label class="block text-sm font-medium text-text-secondary mb-1">
                SEO关键词
              </label>
              <input
                v-model="editorForm.seoKeywords"
                type="text"
                class="input-base"
                placeholder="关键词1,关键词2,关键词3"
              />
            </div>
          </div>
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
