<script setup lang="ts">
import { ref, watch } from 'vue'
import { Search, SortAsc, SortDesc, X } from 'lucide-vue-next'

export interface FilterOption {
  label: string
  value: string
}

const props = withDefaults(defineProps<{
  modelValue?: string
  placeholder?: string
  sortField?: string
  sortOrder?: string
  sortOptions?: FilterOption[]
  filterOptions?: FilterOption[]
  filterValue?: string
  showSort?: boolean
  showFilter?: boolean
  /** Extra slot for additional controls */
}>(), {
  placeholder: '搜索...',
  showSort: true,
  showFilter: true,
})

const emit = defineEmits<{
  'update:modelValue': [value: string]
  'update:sortField': [value: string]
  'update:sortOrder': [value: string]
  'update:filterValue': [value: string]
  search: []
  reset: []
}>()

const localKeyword = ref(props.modelValue || '')

watch(() => props.modelValue, (v) => {
  localKeyword.value = v || ''
})

watch(localKeyword, (v) => {
  emit('update:modelValue', v)
})

function handleSearch() {
  emit('search')
}

function handleClear() {
  localKeyword.value = ''
  emit('search')
}

function handleReset() {
  localKeyword.value = ''
  emit('reset')
}

function toggleSortOrder() {
  emit('update:sortOrder', props.sortOrder === 'asc' ? 'desc' : 'asc')
}
</script>

<template>
  <div class="card-solid flex flex-wrap items-center gap-2 px-4 py-3">
    <!-- Search -->
    <div class="relative flex-1 min-w-[180px]">
      <Search :size="16" class="absolute left-3 top-1/2 -translate-y-1/2 text-text-muted" />
      <input
        :value="localKeyword"
        type="text"
        :placeholder="placeholder"
        class="w-full pl-9 pr-8 py-2 text-sm rounded-lg border border-border bg-bg-secondary text-text-primary placeholder:text-text-muted focus:outline-none focus:ring-2 focus:ring-accent/50 focus:border-transparent transition-colors"
        @input="(e: Event) => localKeyword = (e.target as HTMLInputElement).value"
        @keydown.enter="handleSearch"
      />
      <button
        v-if="localKeyword"
        class="absolute right-2 top-1/2 -translate-y-1/2 p-0.5 rounded text-text-muted hover:text-text-primary transition-colors"
        @click="handleClear"
      >
        <X :size="14" />
      </button>
    </div>

    <!-- Filter -->
    <select
      v-if="showFilter && filterOptions && filterOptions.length > 0"
      :value="filterValue"
      class="px-3 py-2 text-sm rounded-lg border border-border bg-bg-secondary text-text-primary focus:outline-none focus:ring-2 focus:ring-accent/50 focus:border-accent transition-colors"
      @change="emit('update:filterValue', ($event.target as HTMLSelectElement).value)"
    >
      <option
        v-for="opt in filterOptions"
        :key="opt.value"
        :value="opt.value"
      >
        {{ opt.label }}
      </option>
    </select>

    <!-- Sort -->
    <template v-if="showSort && sortOptions && sortOptions.length > 0">
      <select
        :value="sortField"
        class="px-3 py-2 text-sm rounded-lg border border-border bg-bg-secondary text-text-primary focus:outline-none focus:ring-2 focus:ring-accent/50 focus:border-accent transition-colors"
        @change="emit('update:sortField', ($event.target as HTMLSelectElement).value)"
      >
        <option
          v-for="opt in sortOptions"
          :key="opt.value"
          :value="opt.value"
        >
          {{ opt.label }}
        </option>
      </select>
      <button
        @click="toggleSortOrder"
        class="flex items-center gap-1 px-3 py-2 text-sm rounded-lg border border-border bg-bg-secondary text-text-primary hover:bg-bg-code transition-colors"
        title="切换排序方向"
      >
        <SortAsc v-if="sortOrder === 'asc'" :size="14" />
        <SortDesc v-else :size="14" />
        {{ sortOrder === 'asc' ? '升序' : '降序' }}
      </button>
    </template>

    <!-- Search button -->
    <button @click="handleSearch" class="btn-primary text-sm">
      <Search :size="14" />
      搜索
    </button>

    <!-- Reset -->
    <button
      v-if="localKeyword || (filterValue !== undefined && filterValue !== '')"
      @click="handleReset"
      class="btn-ghost text-sm ml-auto"
    >
      <X :size="14" />
      重置
    </button>

    <!-- Extra slot -->
    <slot />
  </div>
</template>
