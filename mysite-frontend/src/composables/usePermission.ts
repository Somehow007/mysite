import { computed } from 'vue'
import { useUserStore } from '@/stores/user'
import type { UserRole } from '@/types'

export function usePermission() {
  const userStore = useUserStore()

  const currentRole = computed<UserRole | undefined>(() => userStore.user?.role)

  const isDeveloper = computed(() => currentRole.value === 'DEVELOPER')

  const isLoggedIn = computed(() => userStore.isLoggedIn)

  function canManageCategories(): boolean {
    return isDeveloper.value
  }

  function canModifyArticle(authorId: string): boolean {
    if (isDeveloper.value) return true
    if (!isLoggedIn.value) return false
    return userStore.user?.id === authorId
  }

  return {
    currentRole,
    isDeveloper,
    isLoggedIn,
    canManageCategories,
    canModifyArticle,
  }
}
