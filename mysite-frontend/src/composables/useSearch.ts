import { ref } from 'vue'
import { searchArticles } from '@/api/article'
import type { ArticleListItem } from '@/types'

export function useSearch() {
  const query = ref('')
  const results = ref<ArticleListItem[]>([])
  const loading = ref(false)
  const isOpen = ref(false)

  function open() {
    isOpen.value = true
  }

  function close() {
    isOpen.value = false
    query.value = ''
    results.value = []
  }

  async function search(keyword?: string) {
    const q = keyword ?? query.value
    if (!q.trim()) {
      results.value = []
      return
    }

    loading.value = true
    try {
      const res = await searchArticles({ keyword: q, page: 1, size: 10 })
      results.value = res.list
    } catch {
      results.value = []
    } finally {
      loading.value = false
    }
  }

  return {
    query,
    results,
    loading,
    isOpen,
    open,
    close,
    search,
  }
}
