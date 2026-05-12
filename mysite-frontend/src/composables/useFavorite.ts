import { ref } from 'vue'
import { favoriteArticle } from '@/api/article'
import { useUserStore } from '@/stores/user'

const favoriteCache = ref<Record<string, boolean>>({})
const favoriteCountCache = ref<Record<string, number>>({})
const pendingRequests = ref<Set<string>>(new Set())

export function useFavorite() {
  const userStore = useUserStore()

  function isFavorited(articleId: string): boolean {
    return favoriteCache.value[articleId] ?? false
  }

  function setFavoriteStatus(articleId: string, status: boolean) {
    favoriteCache.value[articleId] = status
  }

  function setFavoriteCount(articleId: string, count: number) {
    favoriteCountCache.value[articleId] = count
  }

  function getFavoriteCount(articleId: string): number | undefined {
    return favoriteCountCache.value[articleId]
  }

  function batchSetFavoriteStatus(statusMap: Record<string, boolean>) {
    favoriteCache.value = { ...favoriteCache.value, ...statusMap }
  }

  async function toggleFavorite(articleId: string): Promise<{ success: boolean; favorited: boolean; favoriteCount?: number; message?: string }> {
    if (!userStore.isLoggedIn) {
      return { success: false, favorited: false, message: '请先登录' }
    }

    if (pendingRequests.value.has(articleId)) {
      return { success: false, favorited: isFavorited(articleId), message: '操作进行中' }
    }

    const currentStatus = isFavorited(articleId)
    const optimisticStatus = !currentStatus

    favoriteCache.value[articleId] = optimisticStatus
    pendingRequests.value.add(articleId)

    try {
      const result = await favoriteArticle(articleId)
      favoriteCache.value[articleId] = result.favorited
      favoriteCountCache.value[articleId] = result.favoriteCount
      return { success: true, favorited: result.favorited, favoriteCount: result.favoriteCount }
    } catch (error) {
      favoriteCache.value[articleId] = currentStatus
      const message = error instanceof Error ? error.message : '操作失败'
      return { success: false, favorited: currentStatus, message }
    } finally {
      pendingRequests.value.delete(articleId)
    }
  }

  function isPending(articleId: string): boolean {
    return pendingRequests.value.has(articleId)
  }

  function clearCache() {
    favoriteCache.value = {}
    favoriteCountCache.value = {}
  }

  return {
    favoriteCache,
    favoriteCountCache,
    isFavorited,
    setFavoriteStatus,
    setFavoriteCount,
    getFavoriteCount,
    batchSetFavoriteStatus,
    toggleFavorite,
    isPending,
    clearCache,
  }
}
