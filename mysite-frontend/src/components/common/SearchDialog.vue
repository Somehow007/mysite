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

function goToResult(id: string) {
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
    class="p-2 rounded-lg text-text-muted hover:text-accent hover:bg-accent-subtle transition-all duration-200"
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
          class="absolute inset-0 bg-black/40 backdrop-blur-sm"
          @click="close"
        />
        <div
          class="relative w-full max-w-lg mx-4 glass glass-lg rounded-2xl overflow-hidden animate-scale-in"
        >
          <div class="flex items-center gap-3 px-4 border-b border-border">
            <Search :size="18" class="text-text-muted shrink-0" />
            <input
              ref="inputRef"
              v-model="query"
              type="text"
              placeholder="搜索文章..."
              class="flex-1 py-4 bg-transparent text-text-primary placeholder:text-text-muted outline-none text-sm"
              @input="handleInput"
            />
            <button
              @click="close"
              class="p-1 text-text-muted hover:text-text-secondary transition-colors"
            >
              <X :size="16" />
            </button>
          </div>

          <div class="max-h-80 overflow-y-auto">
            <div v-if="loading" class="px-4 py-8 text-center text-sm text-text-muted">
              搜索中...
            </div>

            <div v-else-if="query && results.length === 0" class="px-4 py-8 text-center text-sm text-text-muted">
              未找到相关文章
            </div>

            <div v-else-if="results.length > 0">
              <button
                v-for="article in results"
                :key="article.id"
                @click="goToResult(article.id)"
                class="w-full text-left px-4 py-3 hover:bg-accent-subtle transition-colors duration-150 flex items-center justify-between gap-3"
              >
                <div class="min-w-0">
                  <p class="text-sm font-medium text-text-primary truncate">
                    {{ article.title }}
                  </p>
                  <p v-if="article.summary" class="text-xs text-text-muted truncate mt-0.5">
                    {{ article.summary }}
                  </p>
                </div>
                <CornerDownLeft :size="14" class="text-text-muted shrink-0" />
              </button>
            </div>

            <div v-else class="px-4 py-6 text-center text-sm text-text-muted">
              输入关键词搜索文章
            </div>
          </div>

          <div class="px-4 py-2.5 border-t border-border flex items-center gap-4 text-xs text-text-muted">
            <span class="flex items-center gap-1">
              <kbd class="px-1.5 py-0.5 rounded bg-bg-code text-[10px] font-medium">ESC</kbd>
              关闭
            </span>
            <span class="flex items-center gap-1">
              <kbd class="px-1.5 py-0.5 rounded bg-bg-code text-[10px] font-medium">↵</kbd>
              打开
            </span>
          </div>
        </div>
      </div>
    </transition>
  </teleport>
</template>
