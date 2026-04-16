<template>
  <div v-if="isOpen" class="search-modal" @click.self="close">
    <div class="search-modal-content">
      <div class="search-modal-header">
        <h2>搜索</h2>
        <button class="search-modal-close" @click="close" aria-label="关闭">
          <IconArrowRight />
        </button>
      </div>
      <div class="search-modal-input-wrapper">
        <IconSearch class="search-icon" />
        <input
          v-model="query"
          type="text"
          class="search-modal-input"
          placeholder="输入关键词搜索..."
          @input="debouncedSearch"
          @keydown.enter="handleSearch"
          ref="inputRef"
        />
      </div>
      <div v-if="loading" class="search-modal-results">
        <p>搜索中...</p>
      </div>
      <div v-else-if="results.length > 0" class="search-modal-results">
        <div
          v-for="post in results"
          :key="post.id"
          class="search-result-item"
          @click="goToPost(post.id)"
        >
          <h3>{{ post.title }}</h3>
          <p v-if="post.excerpt" v-html="highlightText(post.excerpt)"></p>
        </div>
      </div>
      <div v-else-if="query && !loading" class="search-modal-results">
        <p>未找到相关文章</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { searchArticles } from '@/api/article'
import type { Post, ArticlePageQueryRespDTO } from '@/types/blog'
import IconSearch from './icons/IconSearch.vue'
import IconArrowRight from './icons/IconArrowRight.vue'

const props = defineProps<{
  isOpen: boolean
}>()

const emit = defineEmits<{
  (e: 'close'): void
}>()

const router = useRouter()
const query = ref('')
const results = ref<Post[]>([])
const loading = ref(false)
const inputRef = ref<HTMLInputElement | null>(null)

let debounceTimer: ReturnType<typeof setTimeout> | null = null

const highlightText = (text: string): string => {
  if (!query.value) return text
  const regex = new RegExp(`(${query.value})`, 'gi')
  return text.replace(regex, '<mark>$1</mark>')
}

const mapArticleToPost = (article: ArticlePageQueryRespDTO): Post => {
  return {
    id: String(article.id),
    title: article.title,
    summary: article.summary,
    content: '',
    featured: false,
    createTime: article.createTime || '',
    updateTime: article.updateTime || '',
    reading_time: '1 min read',
    viewCount: article.viewCount,
    favoriteCount: article.favoriteCount,
    authorName: article.authorName,
    authorId: '',
    tags: [],
    url: `/post/${article.id}`,
    authors: [],
    published_at: article.createTime || '',
    excerpt: article.summary,
  }
}

const handleSearch = async () => {
  if (!query.value.trim()) {
    results.value = []
    return
  }

  try {
    loading.value = true
    const response = await searchArticles({ keyword: query.value, page: 1, size: 10 })
    if (response && response.data) {
      results.value = (response.data.records || []).map(mapArticleToPost)
    } else {
      results.value = []
    }
  } catch (error) {
    console.error('搜索失败:', error)
    results.value = []
  } finally {
    loading.value = false
  }
}

const debouncedSearch = () => {
  if (debounceTimer) {
    clearTimeout(debounceTimer)
  }
  debounceTimer = setTimeout(() => {
    handleSearch()
  }, 300)
}

const goToPost = (id: string) => {
  router.push(`/post/${id}`)
  close()
}

const close = () => {
  emit('close')
  query.value = ''
  results.value = []
}

watch(
  () => props.isOpen,
  (isOpen) => {
    if (isOpen) {
      nextTick(() => {
        inputRef.value?.focus()
      })
    }
  }
)
</script>

<style scoped>
.search-modal {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: var(--color-background-overlay);
  z-index: 1000;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding-top: 10vh;
}

.search-modal-content {
  background: var(--color-background-main);
  border-radius: 8px;
  width: 90%;
  max-width: 600px;
  max-height: 80vh;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.3);
}

.search-modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 2rem;
  border-bottom: 1px solid var(--color-background-contrast);
}

.search-modal-header h2 {
  margin: 0;
  font-size: 2rem;
  color: var(--color-content-lead);
}

.search-modal-close {
  background: none;
  border: none;
  cursor: pointer;
  padding: 0.5rem;
  color: var(--color-content-main);
}

.search-modal-input-wrapper {
  position: relative;
  padding: 2rem;
}

.search-icon {
  position: absolute;
  left: 3rem;
  top: 50%;
  transform: translateY(-50%);
  color: var(--color-content-secondary);
}

.search-modal-input {
  width: 100%;
  padding: 1.5rem 1.5rem 1.5rem 4rem;
  font-size: 1.6rem;
  border: 1px solid var(--color-background-contrast);
  border-radius: 4rem;
  background: var(--color-background-secondary);
  color: var(--color-content-main);
}

.search-modal-input:focus {
  outline: none;
  border-color: var(--ghost-accent-color);
}

.search-modal-results {
  flex: 1;
  overflow-y: auto;
  padding: 2rem;
}

.search-result-item {
  padding: 1.5rem;
  margin-bottom: 1rem;
  border: 1px solid var(--color-background-contrast);
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.search-result-item:hover {
  background: var(--color-background-secondary);
  border-color: var(--ghost-accent-color);
}

.search-result-item h3 {
  margin: 0 0 0.5rem;
  font-size: 1.8rem;
  color: var(--color-content-lead);
}

.search-result-item p {
  margin: 0;
  font-size: 1.4rem;
  color: var(--color-content-secondary);
}

.search-result-item :deep(mark) {
  background: var(--ghost-accent-color);
  color: var(--color-background-main);
  padding: 0 0.2rem;
}
</style>
