<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useHead } from '@unhead/vue'
import { Plus, Trash2, Edit, FolderTree, ChevronRight, ChevronDown, Settings } from 'lucide-vue-next'
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
    alert('分类名称和别名不能为空')
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
    await fetchCategories()
  } catch (error) {
    alert(error instanceof Error ? error.message : '保存失败')
  } finally {
    saving.value = false
  }
}

async function handleDelete(id: string) {
  if (!confirm('确定要删除这个分类吗？')) return
  
  try {
    await deleteCategory(id)
    await fetchCategories()
  } catch (error) {
    alert(error instanceof Error ? error.message : '删除失败')
  }
}

async function handleToggleStatus(category: Category) {
  try {
    await updateCategoryStatus(category.id, category.status === 1 ? 0 : 1)
    await fetchCategories()
  } catch (error) {
    alert(error instanceof Error ? error.message : '操作失败')
  }
}

async function handleBatchDelete() {
  if (selectedIds.value.size === 0) {
    alert('请选择要删除的分类')
    return
  }
  
  if (!confirm(`确定要删除选中的 ${selectedIds.value.size} 个分类吗？`)) return
  
  try {
    await batchDelete(Array.from(selectedIds.value))
    selectedIds.value.clear()
    await fetchCategories()
  } catch (error) {
    alert(error instanceof Error ? error.message : '删除失败')
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
    <div class="flex items-center justify-between mb-8">
      <h1 class="text-2xl font-semibold text-[var(--color-text-heading)] dark:text-[var(--color-dark-text-heading)]">
        分类管理
      </h1>
      <div class="flex items-center gap-2">
        <button
          v-if="selectedIds.size > 0"
          @click="handleBatchDelete"
          class="inline-flex items-center gap-2 px-4 py-2 text-sm rounded-lg border border-red-500 text-red-500 hover:bg-red-50 dark:hover:bg-red-900/20 transition-all duration-200"
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
      </div>
    </div>

    <div v-if="loading" class="py-16 text-center text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)]">
      加载中...
    </div>

    <div v-else-if="categories.length === 0" class="py-16 text-center">
      <FolderTree :size="48" class="mx-auto mb-4 text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)]" />
      <p class="text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)]">还没有分类，开始创建第一个吧</p>
      <button
        @click="openCreateModal()"
        class="btn-primary"
      >
        新建分类
      </button>
    </div>

    <div v-else class="rounded-lg border border-[var(--color-border)] dark:border-[var(--color-dark-border)] bg-[var(--color-bg-card)] dark:bg-[var(--color-dark-bg-card)] overflow-hidden">
      <div class="flex items-center gap-3 px-4 py-3 border-b border-[var(--color-border)] dark:border-[var(--color-dark-border)] bg-[var(--color-bg-code)] dark:bg-[var(--color-dark-bg-code)]">
        <input
          type="checkbox"
          :checked="selectedIds.size === getAllCategoryIds(categories).length"
          @change="toggleSelectAll"
          class="w-4 h-4 rounded border-[var(--color-border)] dark:border-[var(--color-dark-border)]"
        />
        <span class="text-sm font-medium text-[var(--color-text)] dark:text-[var(--color-dark-text)]">分类名称</span>
        <span class="ml-auto text-sm text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)]">文章数</span>
        <span class="ml-4 text-sm text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)]">状态</span>
        <span class="ml-4 text-sm text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)]">操作</span>
      </div>

      <div class="divide-y divide-[var(--color-border)] dark:divide-[var(--color-dark-border)]">
        <template v-for="category in categories" :key="category.id">
          <div class="flex items-center gap-3 px-4 py-3 hover:bg-[var(--color-bg-code)] dark:hover:bg-[var(--color-dark-bg-code)] transition-colors">
            <input
              type="checkbox"
              :checked="selectedIds.has(category.id)"
              @change="toggleSelect(category.id)"
              class="w-4 h-4 rounded border-[var(--color-border)] dark:border-[var(--color-dark-border)]"
            />
            <button
              v-if="category.children && category.children.length > 0"
              @click="toggleExpand(category.id)"
              class="p-0.5 rounded hover:bg-[var(--color-bg-code)] dark:hover:bg-[var(--color-dark-bg-code)] transition-colors"
            >
              <ChevronRight v-if="!expandedIds.has(category.id)" :size="14" />
              <ChevronDown v-else :size="14" />
            </button>
            <div v-else class="w-5" />
            <span class="text-sm text-[var(--color-text)] dark:text-[var(--color-dark-text)]">{{ category.name }}</span>
            <span class="ml-auto text-sm text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)]">
              {{ category.articleCount || 0 }}
            </span>
            <span class="ml-4">
              <button
                @click="handleToggleStatus(category)"
                :class="[
                  'px-2 py-0.5 text-xs rounded',
                  category.status === 1
                    ? 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400'
                    : 'bg-gray-100 text-gray-700 dark:bg-gray-800 dark:text-gray-400'
                ]"
              >
                {{ category.status === 1 ? '启用' : '禁用' }}
              </button>
            </span>
            <div class="ml-4 flex items-center gap-1">
              <button
                v-if="category.level < 3"
                @click="openCreateModal(category.id)"
                class="p-1.5 rounded text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)] hover:bg-[var(--color-bg-code)] dark:hover:bg-[var(--color-dark-bg-code)] transition-colors"
                title="添加子分类"
              >
                <Plus :size="14" />
              </button>
              <button
                @click="openEditModal(category)"
                class="p-1.5 rounded text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)] hover:bg-[var(--color-bg-code)] dark:hover:bg-[var(--color-dark-bg-code)] transition-colors"
                title="编辑"
              >
                <Edit :size="14" />
              </button>
              <button
                @click="handleDelete(category.id)"
                class="p-1.5 rounded text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)] hover:bg-red-50 dark:hover:bg-red-900/20 hover:text-red-500 transition-colors"
                title="删除"
              >
                <Trash2 :size="14" />
              </button>
            </div>
          </div>

          <template v-if="expandedIds.has(category.id) && category.children">
            <template v-for="child in category.children" :key="child.id">
              <div class="flex items-center gap-3 px-4 py-3 pl-12 hover:bg-[var(--color-bg-code)] dark:hover:bg-[var(--color-dark-bg-code)] transition-colors">
                <input
                  type="checkbox"
                  :checked="selectedIds.has(child.id)"
                  @change="toggleSelect(child.id)"
                  class="w-4 h-4 rounded border-[var(--color-border)] dark:border-[var(--color-dark-border)]"
                />
                <button
                  v-if="child.children && child.children.length > 0"
                  @click="toggleExpand(child.id)"
                  class="p-0.5 rounded hover:bg-[var(--color-bg-code)] dark:hover:bg-[var(--color-dark-bg-code)] transition-colors"
                >
                  <ChevronRight v-if="!expandedIds.has(child.id)" :size="14" />
                  <ChevronDown v-else :size="14" />
                </button>
                <div v-else class="w-5" />
                <span class="text-sm text-[var(--color-text)] dark:text-[var(--color-dark-text)]">{{ child.name }}</span>
                <span class="ml-auto text-sm text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)]">
                  {{ child.articleCount || 0 }}
                </span>
                <span class="ml-4">
                  <button
                    @click="handleToggleStatus(child)"
                    :class="[
                      'px-2 py-0.5 text-xs rounded',
                      child.status === 1
                        ? 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400'
                        : 'bg-gray-100 text-gray-700 dark:bg-gray-800 dark:text-gray-400'
                    ]"
                  >
                    {{ child.status === 1 ? '启用' : '禁用' }}
                  </button>
                </span>
                <div class="ml-4 flex items-center gap-1">
                  <button
                    v-if="child.level < 3"
                    @click="openCreateModal(child.id)"
                    class="p-1.5 rounded text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)] hover:bg-[var(--color-bg-code)] dark:hover:bg-[var(--color-dark-bg-code)] transition-colors"
                    title="添加子分类"
                  >
                    <Plus :size="14" />
                  </button>
                  <button
                    @click="openEditModal(child)"
                    class="p-1.5 rounded text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)] hover:bg-[var(--color-bg-code)] dark:hover:bg-[var(--color-dark-bg-code)] transition-colors"
                    title="编辑"
                  >
                    <Edit :size="14" />
                  </button>
                  <button
                    @click="handleDelete(child.id)"
                    class="p-1.5 rounded text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)] hover:bg-red-50 dark:hover:bg-red-900/20 hover:text-red-500 transition-colors"
                    title="删除"
                  >
                    <Trash2 :size="14" />
                  </button>
                </div>
              </div>

              <template v-if="expandedIds.has(child.id) && child.children">
                <div
                  v-for="grandchild in child.children"
                  :key="grandchild.id"
                  class="flex items-center gap-3 px-4 py-3 pl-20 hover:bg-[var(--color-bg-code)] dark:hover:bg-[var(--color-dark-bg-code)] transition-colors"
                >
                  <input
                    type="checkbox"
                    :checked="selectedIds.has(grandchild.id)"
                    @change="toggleSelect(grandchild.id)"
                    class="w-4 h-4 rounded border-[var(--color-border)] dark:border-[var(--color-dark-border)]"
                  />
                  <div class="w-5" />
                  <span class="text-sm text-[var(--color-text)] dark:text-[var(--color-dark-text)]">{{ grandchild.name }}</span>
                  <span class="ml-auto text-sm text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)]">
                    {{ grandchild.articleCount || 0 }}
                  </span>
                  <span class="ml-4">
                    <button
                      @click="handleToggleStatus(grandchild)"
                      :class="[
                        'px-2 py-0.5 text-xs rounded',
                        grandchild.status === 1
                          ? 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400'
                          : 'bg-gray-100 text-gray-700 dark:bg-gray-800 dark:text-gray-400'
                      ]"
                    >
                      {{ grandchild.status === 1 ? '启用' : '禁用' }}
                    </button>
                  </span>
                  <div class="ml-4 flex items-center gap-1">
                    <button
                      @click="openEditModal(grandchild)"
                      class="p-1.5 rounded text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)] hover:bg-[var(--color-bg-code)] dark:hover:bg-[var(--color-dark-bg-code)] transition-colors"
                      title="编辑"
                    >
                      <Edit :size="14" />
                    </button>
                    <button
                      @click="handleDelete(grandchild.id)"
                      class="p-1.5 rounded text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)] hover:bg-red-50 dark:hover:bg-red-900/20 hover:text-red-500 transition-colors"
                      title="删除"
                    >
                      <Trash2 :size="14" />
                    </button>
                  </div>
                </div>
              </template>
            </template>
          </template>
        </template>
      </div>
    </div>

    <div
      v-if="showEditor"
      class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm"
      @click.self="showEditor = false"
    >
      <div class="w-full max-w-2xl max-h-[90vh] overflow-y-auto rounded-2xl bg-[var(--color-bg-card)] dark:bg-[var(--color-dark-bg-card)] shadow-2xl border border-[var(--color-border)] dark:border-[var(--color-dark-border)] animate-scale-in">
        <div class="sticky top-0 flex items-center justify-between px-6 py-4 border-b border-[var(--color-border)] dark:border-[var(--color-dark-border)] bg-[var(--color-bg-card)] dark:bg-[var(--color-dark-bg-card)] z-10">
          <h2 class="text-lg font-semibold text-[var(--color-text-heading)] dark:text-[var(--color-dark-text-heading)]">
            {{ editingCategory ? '编辑分类' : '新建分类' }}
          </h2>
          <button
            @click="showEditor = false"
            class="p-2 rounded-lg text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)] hover:bg-[var(--color-accent-light)] dark:hover:bg-[var(--color-dark-accent-light)] transition-all duration-200"
          >
            ✕
          </button>
        </div>

        <div class="p-6 space-y-4">
          <div>
            <label class="block text-sm font-medium text-[var(--color-text)] dark:text-[var(--color-dark-text)] mb-1">
              分类名称 <span class="text-red-500">*</span>
            </label>
            <input
              v-model="editorForm.name"
              type="text"
              class="w-full px-3 py-2 rounded-lg border border-[var(--color-border)] dark:border-[var(--color-dark-border)] bg-[var(--color-bg-card)] dark:bg-[var(--color-dark-bg-card)] text-[var(--color-text)] dark:text-[var(--color-dark-text)] focus:outline-none focus:ring-2 focus:ring-[var(--color-accent)]"
              placeholder="请输入分类名称"
            />
          </div>

          <div>
            <label class="block text-sm font-medium text-[var(--color-text)] dark:text-[var(--color-dark-text)] mb-1">
              分类别名 <span class="text-red-500">*</span>
            </label>
            <input
              v-model="editorForm.slug"
              type="text"
              class="w-full px-3 py-2 rounded-lg border border-[var(--color-border)] dark:border-[var(--color-dark-border)] bg-[var(--color-bg-card)] dark:bg-[var(--color-dark-bg-card)] text-[var(--color-text)] dark:text-[var(--color-dark-text)] focus:outline-none focus:ring-2 focus:ring-[var(--color-accent)]"
              placeholder="URL友好的别名，如：tech"
            />
          </div>

          <div>
            <label class="block text-sm font-medium text-[var(--color-text)] dark:text-[var(--color-dark-text)] mb-1">
              父分类
            </label>
            <select
              v-model="editorForm.parentId"
              class="w-full px-3 py-2 rounded-lg border border-[var(--color-border)] dark:border-[var(--color-dark-border)] bg-[var(--color-bg-card)] dark:bg-[var(--color-dark-bg-card)] text-[var(--color-text)] dark:text-[var(--color-dark-text)] focus:outline-none focus:ring-2 focus:ring-[var(--color-accent)]"
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
            <label class="block text-sm font-medium text-[var(--color-text)] dark:text-[var(--color-dark-text)] mb-1">
              分类描述
            </label>
            <textarea
              v-model="editorForm.description"
              rows="3"
              class="w-full px-3 py-2 rounded-lg border border-[var(--color-border)] dark:border-[var(--color-dark-border)] bg-[var(--color-bg-card)] dark:bg-[var(--color-dark-bg-card)] text-[var(--color-text)] dark:text-[var(--color-dark-text)] focus:outline-none focus:ring-2 focus:ring-[var(--color-accent)]"
              placeholder="分类描述（可选）"
            />
          </div>

          <div class="grid grid-cols-2 gap-4">
            <div>
              <label class="block text-sm font-medium text-[var(--color-text)] dark:text-[var(--color-dark-text)] mb-1">
                排序
              </label>
              <input
                v-model.number="editorForm.sortOrder"
                type="number"
                class="w-full px-3 py-2 rounded-lg border border-[var(--color-border)] dark:border-[var(--color-dark-border)] bg-[var(--color-bg-card)] dark:bg-[var(--color-dark-bg-card)] text-[var(--color-text)] dark:text-[var(--color-dark-text)] focus:outline-none focus:ring-2 focus:ring-[var(--color-accent)]"
              />
            </div>

            <div>
              <label class="block text-sm font-medium text-[var(--color-text)] dark:text-[var(--color-dark-text)] mb-1">
                状态
              </label>
              <select
                v-model.number="editorForm.status"
                class="w-full px-3 py-2 rounded-lg border border-[var(--color-border)] dark:border-[var(--color-dark-border)] bg-[var(--color-bg-card)] dark:bg-[var(--color-dark-bg-card)] text-[var(--color-text)] dark:text-[var(--color-dark-text)] focus:outline-none focus:ring-2 focus:ring-[var(--color-accent)]"
              >
                <option :value="1">启用</option>
                <option :value="0">禁用</option>
              </select>
            </div>
          </div>

          <div class="grid grid-cols-2 gap-4">
            <div>
              <label class="block text-sm font-medium text-[var(--color-text)] dark:text-[var(--color-dark-text)] mb-1">
                图标
              </label>
              <input
                v-model="editorForm.icon"
                type="text"
                class="w-full px-3 py-2 rounded-lg border border-[var(--color-border)] dark:border-[var(--color-dark-border)] bg-[var(--color-bg-card)] dark:bg-[var(--color-dark-bg-card)] text-[var(--color-text)] dark:text-[var(--color-dark-text)] focus:outline-none focus:ring-2 focus:ring-[var(--color-accent)]"
                placeholder="图标名称或URL"
              />
            </div>

            <div>
              <label class="block text-sm font-medium text-[var(--color-text)] dark:text-[var(--color-dark-text)] mb-1">
                颜色
              </label>
              <input
                v-model="editorForm.color"
                type="text"
                class="w-full px-3 py-2 rounded-lg border border-[var(--color-border)] dark:border-[var(--color-dark-border)] bg-[var(--color-bg-card)] dark:bg-[var(--color-dark-bg-card)] text-[var(--color-text)] dark:text-[var(--color-dark-text)] focus:outline-none focus:ring-2 focus:ring-[var(--color-accent)]"
                placeholder="#1890ff"
              />
            </div>
          </div>

          <div class="pt-4 border-t border-[var(--color-border)] dark:border-[var(--color-dark-border)]">
            <h3 class="text-sm font-medium text-[var(--color-text)] dark:text-[var(--color-dark-text)] mb-3 flex items-center gap-2">
              <Settings :size="14" />
              SEO设置
            </h3>

            <div class="space-y-4">
              <div>
                <label class="block text-sm font-medium text-[var(--color-text)] dark:text-[var(--color-dark-text)] mb-1">
                  SEO标题
                </label>
                <input
                  v-model="editorForm.seoTitle"
                  type="text"
                  class="w-full px-3 py-2 rounded-lg border border-[var(--color-border)] dark:border-[var(--color-dark-border)] bg-[var(--color-bg-card)] dark:bg-[var(--color-dark-bg-card)] text-[var(--color-text)] dark:text-[var(--color-dark-text)] focus:outline-none focus:ring-2 focus:ring-[var(--color-accent)]"
                  placeholder="SEO标题（可选）"
                />
              </div>

              <div>
                <label class="block text-sm font-medium text-[var(--color-text)] dark:text-[var(--color-dark-text)] mb-1">
                  SEO描述
                </label>
                <textarea
                  v-model="editorForm.seoDescription"
                  rows="2"
                  class="w-full px-3 py-2 rounded-lg border border-[var(--color-border)] dark:border-[var(--color-dark-border)] bg-[var(--color-bg-card)] dark:bg-[var(--color-dark-bg-card)] text-[var(--color-text)] dark:text-[var(--color-dark-text)] focus:outline-none focus:ring-2 focus:ring-[var(--color-accent)]"
                  placeholder="SEO描述（可选）"
                />
              </div>

              <div>
                <label class="block text-sm font-medium text-[var(--color-text)] dark:text-[var(--color-dark-text)] mb-1">
                  SEO关键词
                </label>
                <input
                  v-model="editorForm.seoKeywords"
                  type="text"
                  class="w-full px-3 py-2 rounded-lg border border-[var(--color-border)] dark:border-[var(--color-dark-border)] bg-[var(--color-bg-card)] dark:bg-[var(--color-dark-bg-card)] text-[var(--color-text)] dark:text-[var(--color-dark-text)] focus:outline-none focus:ring-2 focus:ring-[var(--color-accent)]"
                  placeholder="关键词1,关键词2,关键词3"
                />
              </div>
            </div>
          </div>
        </div>

        <div class="sticky bottom-0 flex items-center justify-end gap-2 px-6 py-4 border-t border-[var(--color-border)] dark:border-[var(--color-dark-border)] bg-[var(--color-bg-card)] dark:bg-[var(--color-dark-bg-card)]">
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
