<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import { Search, X, CornerDownLeft } from 'lucide-vue-next'
import { useSearch } from '@/composables/useSearch'
import { useRouter } from 'vue-router'

const { query, results, loading, isOpen, open, close, search } = useSearch()
const router = useRouter()
const inputRef = ref<HTMLInputElement | null>(null)

function openAndFocus() {
  open()
  nextTick(() => {
    if (inputRef.value) inputRef.value.focus()
  })
}

function handleKeydown(e: KeyboardEvent) {
  if ((e.metaKey || e.ctrlKey) && e.key === 'k') {
    e.preventDefault()
    if (isOpen.value) {
      close()
    } else {
      openAndFocus()
    }
  }
  if (e.key === 'Escape' && isOpen.value) {
    close()
  }
}

function handleInput() {
  search()
}

function goToResult(id: number) {
  close()
  router.push(`/post/${id}`)
}

onMounted(() => {
  window.addEventListener('keydown', handleKeydown)
})

onUnmounted(() => {
  window.removeEventListener('keydown', handleKeydown)
})
</script>

<template>
  <button
    @click="openAndFocus"
    class="p-2 rounded-lg text-[var(--color-text-body)] dark:text-[var(--color-dark-text-body)] hover:bg-[var(--color-bg-code)] dark:hover:bg-[var(--color-dark-bg-code)] transition-colors"
    aria-label="搜索"
  >
    <Search :size="18" />
  </button>

  <teleport to="body">
    <transition name="fade">
      <div
        v-if="isOpen"
        class="fixed inset-0 z-50 flex items-start justify-center pt-[20vh]"
      >
        <div
          class="absolute inset-0 bg-black/50"
          @click="close"
        />
        <div
          class="relative w-full max-w-lg mx-4 bg-[var(--color-bg-card)] dark:bg-[var(--color-dark-bg-card)] rounded-xl shadow-2xl border border-[var(--color-border)] dark:border-[var(--color-dark-border)] overflow-hidden"
        >
          <div class="flex items-center gap-3 px-4 border-b border-[var(--color-border)] dark:border-[var(--color-dark-border)]">
            <Search :size="18" class="text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)] shrink-0" />
            <input
              ref="inputRef"
              v-model="query"
              type="text"
              placeholder="搜索文章..."
              class="flex-1 py-4 bg-transparent text-[var(--color-text-heading)] dark:text-[var(--color-dark-text-heading)] placeholder:text-[var(--color-text-muted)] dark:placeholder:text-[var(--color-dark-text-muted)] outline-none text-sm"
              @input="handleInput"
            />
            <button
              @click="close"
              class="p-1 text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)] hover:text-[var(--color-text-body)] dark:hover:text-[var(--color-dark-text-body)]"
            >
              <X :size="16" />
            </button>
          </div>

          <div class="max-h-80 overflow-y-auto">
            <div v-if="loading" class="px-4 py-8 text-center text-sm text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)]">
              搜索中...
            </div>

            <div v-else-if="query && results.length === 0" class="px-4 py-8 text-center text-sm text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)]">
              未找到相关文章
            </div>

            <div v-else-if="results.length > 0">
              <button
                v-for="article in results"
                :key="article.id"
                @click="goToResult(article.id)"
                class="w-full text-left px-4 py-3 hover:bg-[var(--color-bg-code)] dark:hover:bg-[var(--color-dark-bg-code)] transition-colors flex items-center justify-between gap-3"
              >
                <div class="min-w-0">
                  <p class="text-sm font-medium text-[var(--color-text-heading)] dark:text-[var(--color-dark-text-heading)] truncate">
                    {{ article.title }}
                  </p>
                  <p v-if="article.summary" class="text-xs text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)] truncate mt-0.5">
                    {{ article.summary }}
                  </p>
                </div>
                <CornerDownLeft :size="14" class="text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)] shrink-0" />
              </button>
            </div>

            <div v-else class="px-4 py-6 text-center text-sm text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)]">
              输入关键词搜索文章
            </div>
          </div>

          <div class="px-4 py-2 border-t border-[var(--color-border)] dark:border-[var(--color-dark-border)] flex items-center gap-4 text-xs text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)]">
            <span class="flex items-center gap-1">
              <kbd class="px-1.5 py-0.5 rounded bg-[var(--color-bg-code)] dark:bg-[var(--color-dark-bg-code)] text-[10px]">ESC</kbd>
              关闭
            </span>
            <span class="flex items-center gap-1">
              <kbd class="px-1.5 py-0.5 rounded bg-[var(--color-bg-code)] dark:bg-[var(--color-dark-bg-code)] text-[10px]">↵</kbd>
              打开
            </span>
          </div>
        </div>
      </div>
    </transition>
  </teleport>
</template>
