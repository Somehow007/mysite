<script setup lang="ts" generic="T extends Record<string, any>">
import { computed } from 'vue'
import { ChevronUp, ChevronDown, CheckSquare, Square } from 'lucide-vue-next'
import Pagination from './Pagination.vue'
import EmptyState from './EmptyState.vue'
import ErrorState from './ErrorState.vue'
import type { Component } from 'vue'

export interface Column<T = any> {
  key: string
  label: string
  sortable?: boolean
  width?: string
  align?: 'left' | 'center' | 'right'
  headerAlign?: 'left' | 'center' | 'right'
  /** Hide on mobile */
  hideMobile?: boolean
}

const props = withDefaults(defineProps<{
  columns: Column<T>[]
  data: T[]
  loading?: boolean
  error?: string | null
  /** Server-side pagination */
  total?: number
  currentPage?: number
  pageSize?: number
  /** Selection */
  selectable?: boolean
  selectedIds?: Set<string>
  idKey?: string
  /** Sort */
  sortField?: string
  sortOrder?: string
  /** Empty state text */
  emptyIcon?: Component
  emptyTitle?: string
  emptyDescription?: string
  /** Row expansion: clicking a row emits toggle-expand; render detail via #expand slot */
  expandable?: boolean
  expandedId?: string | null
}>(), {
  idKey: 'id',
  pageSize: 10,
  expandedId: null,
})

const emit = defineEmits<{
  'update:currentPage': [page: number]
  'update:sortField': [field: string]
  'update:sortOrder': [order: string]
  'toggle-sort': [field: string]
  'toggle-select-all': []
  'toggle-select': [id: string]
  'toggle-expand': [id: string]
}>()

const allSelected = computed(() => {
  if (!props.selectable || !props.selectedIds) return false
  return props.data.length > 0 && props.data.every(item => props.selectedIds!.has(String(item[props.idKey])))
})

const someSelected = computed(() => {
  if (!props.selectable || !props.selectedIds) return false
  return props.data.some(item => props.selectedIds!.has(String(item[props.idKey])))
})

function handleSort(field: string) {
  emit('toggle-sort', field)
}

function getSortIcon(field: string) {
  if (props.sortField !== field) return null
  return props.sortOrder === 'asc' ? ChevronUp : ChevronDown
}
</script>

<template>
  <div class="max-w-full">
    <!-- Desktop Table -->
    <div class="hidden md:block overflow-x-auto rounded-lg border border-border">
      <table class="w-full text-sm">
        <thead>
          <tr class="bg-bg-code">
            <!-- Select column -->
            <th v-if="selectable" class="w-10 px-3 py-3">
              <button
                class="p-0.5 rounded hover:bg-bg-secondary transition-colors"
                @click="emit('toggle-select-all')"
              >
                <CheckSquare v-if="allSelected" :size="16" class="text-accent" />
                <Square v-else-if="someSelected" :size="16" class="text-text-muted" />
                <Square v-else :size="16" class="text-text-muted" />
              </button>
            </th>
            <th
              v-for="col in columns"
              :key="col.key"
              :style="{ width: col.width, textAlign: col.headerAlign || col.align || 'left' }"
              class="px-4 py-3 font-medium text-text-muted text-xs uppercase tracking-wider"
            >
              <button
                v-if="col.sortable"
                class="inline-flex items-center gap-1 hover:text-text-primary transition-colors"
                @click="handleSort(col.key)"
              >
                {{ col.label }}
                <component
                  :is="getSortIcon(col.key)"
                  v-if="getSortIcon(col.key)"
                  :size="12"
                  class="text-accent"
                />
              </button>
              <span v-else>{{ col.label }}</span>
            </th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-if="loading"
            v-for="i in 5"
            :key="'skeleton-' + i"
            class="border-t border-border"
          >
            <td v-if="selectable" class="px-3 py-3">
              <div class="w-4 h-4 rounded bg-bg-code animate-pulse" />
            </td>
            <td v-for="col in columns" :key="col.key" class="px-4 py-3">
              <div class="h-4 bg-bg-code rounded animate-pulse" :style="{ width: col.width || '60%' }" />
            </td>
          </tr>
          <template
            v-else
            v-for="item in data"
            :key="String(item[idKey])"
          >
            <tr
              class="border-t border-border transition-colors"
              :class="[
                expandable ? 'cursor-pointer' : '',
                expandable && expandedId === String(item[idKey])
                  ? 'bg-accent-subtle hover:bg-accent-subtle'
                  : 'hover:bg-bg-code/50',
              ]"
              @click="expandable && emit('toggle-expand', String(item[idKey]))"
            >
              <td v-if="selectable" class="px-3 py-3">
                <button
                  class="p-0.5 rounded hover:bg-bg-secondary transition-colors"
                  @click.stop="emit('toggle-select', String(item[idKey]))"
                >
                  <CheckSquare
                    v-if="selectedIds?.has(String(item[idKey]))"
                    :size="16"
                    class="text-accent"
                  />
                  <Square v-else :size="16" class="text-text-muted" />
                </button>
              </td>
              <td
                v-for="col in columns"
                :key="col.key"
                :style="{ textAlign: col.align || 'left' }"
                class="px-4 py-3 text-text-secondary"
              >
                <slot :name="'cell-' + col.key" :item="item" :value="item[col.key]" :row="item">
                  {{ item[col.key] }}
                </slot>
              </td>
            </tr>
            <!-- Expanded detail row -->
            <tr
              v-if="expandable && expandedId === String(item[idKey])"
              class="border-t border-border"
            >
              <td :colspan="columns.length + (selectable ? 1 : 0)" class="!p-0">
                <slot name="expand" :item="item" :row="item" />
              </td>
            </tr>
          </template>
        </tbody>
      </table>
    </div>

    <!-- Mobile Card List -->
    <div class="md:hidden space-y-3">
      <div v-if="loading">
        <div
          v-for="i in 3"
          :key="'m-skeleton-' + i"
          class="p-4 rounded-lg border border-border bg-bg-secondary animate-pulse"
        >
          <div class="h-4 bg-bg-code rounded w-3/4 mb-2" />
          <div class="h-3 bg-bg-code rounded w-1/2" />
        </div>
      </div>
      <div
        v-else
        v-for="item in data"
        :key="String(item[idKey])"
        class="p-4 rounded-lg border border-border bg-bg-secondary hover:border-accent/30 transition-colors"
        :class="expandable ? 'cursor-pointer' : ''"
        @click="expandable && emit('toggle-expand', String(item[idKey]))"
      >
        <slot name="mobile-card" :item="item" :row="item">
          <div v-for="col in columns.filter(c => !c.hideMobile)" :key="col.key" class="flex items-center gap-2 py-0.5">
            <span class="text-xs text-text-muted shrink-0">{{ col.label }}:</span>
            <span class="text-sm text-text-secondary truncate">{{ item[col.key] }}</span>
          </div>
        </slot>
        <div
          v-if="expandable && expandedId === String(item[idKey])"
          class="mt-3 -mx-4 -mb-4"
          @click.stop
        >
          <slot name="expand" :item="item" :row="item" />
        </div>
      </div>
    </div>

    <!-- Error state -->
    <ErrorState
      v-if="!loading && error"
      :message="error"
      @retry="emit('update:currentPage', currentPage || 1)"
    />

    <!-- Empty state -->
    <EmptyState
      v-if="!loading && !error && data.length === 0"
      :icon="emptyIcon"
      :title="emptyTitle"
      :description="emptyDescription"
    />

    <!-- Pagination -->
    <Pagination
      v-if="total && total > pageSize"
      :current="currentPage || 1"
      :total="total"
      :page-size="pageSize"
      @update:current="emit('update:currentPage', $event)"
    />
  </div>
</template>
