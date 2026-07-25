import { ref, watch, onBeforeUnmount, type Ref } from 'vue'

export interface PagedListOptions<T> {
  fetcher: (params: { page: number; size: number; keyword?: string; sort?: string; order?: string }) => Promise<{ items: T[]; total: number }>
  defaultSize?: number
  defaultSort?: string
  defaultOrder?: string
  /** Debounce delay for search in ms */
  searchDebounce?: number
}

export function usePagedList<T>(options: PagedListOptions<T>) {
  const { fetcher, defaultSize = 10, defaultSort = '', defaultOrder = 'desc', searchDebounce = 300 } = options

  const items = ref<T[]>([]) as Ref<T[]>
  const total = ref(0)
  const loading = ref(false)
  const error = ref<string | null>(null)

  const page = ref(1)
  const size = ref(defaultSize)
  const keyword = ref('')
  const sort = ref(defaultSort)
  const order = ref(defaultOrder)

  let abortController: AbortController | null = null
  let debounceTimer: ReturnType<typeof setTimeout> | null = null

  async function fetch() {
    // Cancel any in-flight request
    if (abortController) {
      abortController.abort()
    }
    abortController = new AbortController()

    loading.value = true
    error.value = null

    try {
      const result = await fetcher({
        page: page.value,
        size: size.value,
        keyword: keyword.value || undefined,
        sort: sort.value || undefined,
        order: order.value || undefined,
      })

      // Ignore stale responses
      if (abortController.signal.aborted) return

      items.value = result.items
      total.value = result.total

      // If current page is beyond total, roll back
      const maxPage = Math.max(1, Math.ceil(result.total / size.value))
      if (page.value > maxPage) {
        page.value = maxPage
      }
    } catch (e: any) {
      if (e?.name === 'AbortError' || abortController?.signal.aborted) return
      error.value = e?.message || '加载失败'
    } finally {
      if (!abortController?.signal.aborted) {
        loading.value = false
      }
    }
  }

  function setPage(p: number) {
    if (p === page.value) return
    page.value = p
  }

  function setSize(s: number) {
    size.value = s
    page.value = 1
  }

  function setKeyword(k: string) {
    keyword.value = k
    page.value = 1
  }

  function setSort(s: string, o?: string) {
    sort.value = s
    if (o) order.value = o
    page.value = 1
  }

  function refresh() {
    return fetch()
  }

  // Debounced keyword search
  watch(keyword, () => {
    if (debounceTimer) clearTimeout(debounceTimer)
    debounceTimer = setTimeout(() => {
      fetch()
    }, searchDebounce)
  })

  // Immediate refetch on page/size/sort/order change
  watch([page, size, sort, order], () => {
    fetch()
  }, { immediate: true })

  onBeforeUnmount(() => {
    if (abortController) abortController.abort()
    if (debounceTimer) clearTimeout(debounceTimer)
  })

  return {
    items,
    total,
    loading,
    error,
    page,
    size,
    keyword,
    sort,
    order,
    setPage,
    setSize,
    setKeyword,
    setSort,
    refresh,
  }
}
