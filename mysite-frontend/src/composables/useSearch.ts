import { ref } from 'vue'
import { useDebounceFn } from '@vueuse/core'
import { searchArticles } from '@/api/article'
import type { ArticleListItem } from '@/types'

// 共享单例：搜索弹层全局只有一个实例（SearchDialog），开合状态供顶栏提示框、
// 移动菜单等多个入口共用；⌘K 监听也只在 SearchDialog 注册一份。
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

async function _search(keyword?: string) {
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

const search = useDebounceFn(_search, 300)

export function useSearch() {
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
