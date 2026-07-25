<script setup lang="ts">
import { computed } from 'vue'
import { ChevronRight, ChevronDown, Plus, Edit, Trash2 } from 'lucide-vue-next'
import type { Category } from '@/types'

const props = defineProps<{
  category: Category
  depth: number
  expandedIds: Set<string>
  selectedIds: Set<string>
  maxDepth?: number
}>()

const emit = defineEmits<{
  toggleExpand: [id: string]
  toggleSelect: [id: string]
  addChild: [category: Category]
  edit: [category: Category]
  delete: [id: string]
  toggleStatus: [category: Category]
}>()

const maxDepth = props.maxDepth ?? 3
const hasChildren = computed(() => (props.category.children?.length ?? 0) > 0)
const isExpanded = computed(() => props.expandedIds.has(props.category.id))
const isSelected = computed(() => props.selectedIds.has(props.category.id))

function statusClass(status: number) {
  return status === 1
    ? 'bg-success-subtle text-success'
    : 'bg-bg-code text-text-muted'
}
</script>

<template>
  <!-- Current node -->
  <div
    class="flex items-center gap-3 px-4 py-3 hover:bg-bg-code transition-colors"
    :style="{ paddingLeft: `${1 + depth * 1.5}rem` }"
  >
    <input
      type="checkbox"
      :checked="isSelected"
      @change="emit('toggleSelect', category.id)"
      class="w-4 h-4 rounded border-border"
    />

    <!-- Expand/collapse -->
    <button
      v-if="hasChildren"
      @click="emit('toggleExpand', category.id)"
      class="p-0.5 rounded hover:bg-bg-secondary transition-colors"
    >
      <ChevronRight v-if="!isExpanded" :size="14" class="transition-transform duration-200" />
      <ChevronDown v-else :size="14" class="transition-transform duration-200" />
    </button>
    <div v-else class="w-5" />

    <span class="text-sm text-text-secondary flex-1">{{ category.name }}</span>

    <!-- Article count -->
    <span class="text-sm text-text-muted w-12 text-right">
      {{ category.articleCount || 0 }}
    </span>

    <!-- Status toggle -->
    <button
      @click="emit('toggleStatus', category)"
      class="px-2 py-0.5 text-xs rounded w-14 text-center"
      :class="statusClass(category.status)"
    >
      {{ category.status === 1 ? '启用' : '禁用' }}
    </button>

    <!-- Actions -->
    <div class="flex items-center gap-1 ml-2">
      <button
        v-if="category.level < maxDepth"
        @click="emit('addChild', category)"
        class="p-1.5 rounded text-text-muted hover:bg-bg-secondary transition-colors"
        title="添加子分类"
      >
        <Plus :size="14" />
      </button>
      <button
        @click="emit('edit', category)"
        class="p-1.5 rounded text-text-muted hover:bg-bg-secondary transition-colors"
        title="编辑"
      >
        <Edit :size="14" />
      </button>
      <button
        @click="emit('delete', category.id)"
        class="p-1.5 rounded text-text-muted hover:bg-danger-subtle hover:text-danger transition-colors"
        title="删除"
      >
        <Trash2 :size="14" />
      </button>
    </div>
  </div>

  <!-- Children (recursive) -->
  <template v-if="hasChildren && isExpanded">
    <CategoryTreeNode
      v-for="child in category.children"
      :key="child.id"
      :category="child"
      :depth="depth + 1"
      :expanded-ids="expandedIds"
      :selected-ids="selectedIds"
      :max-depth="maxDepth"
      @toggle-expand="emit('toggleExpand', $event)"
      @toggle-select="emit('toggleSelect', $event)"
      @add-child="emit('addChild', $event)"
      @edit="emit('edit', $event)"
      @delete="emit('delete', $event)"
      @toggle-status="emit('toggleStatus', $event)"
    />
  </template>
</template>
