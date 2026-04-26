import { computed } from 'vue'
import { useUserStore } from '@/stores/user'
import type { UserRole } from '@/types'

export function usePermission() {
  const userStore = useUserStore()

  const currentRole = computed<UserRole | undefined>(() => userStore.user?.role)

  const isDeveloper = computed(() => currentRole.value === 'DEVELOPER')

  const isLoggedIn = computed(() => userStore.isLoggedIn)

  function hasRole(role: UserRole): boolean {
    return currentRole.value === role
  }

  function hasAnyRole(...roles: UserRole[]): boolean {
    return !!currentRole.value && roles.includes(currentRole.value)
  }

  function canManageCategories(): boolean {
    return isDeveloper.value
  }

  function canEditArticle(authorId: string): boolean {
    if (isDeveloper.value) return true
    if (!isLoggedIn.value) return false
    return userStore.user?.id === authorId
  }

  function canDeleteArticle(authorId: string): boolean {
    if (isDeveloper.value) return true
    if (!isLoggedIn.value) return false
    return userStore.user?.id === authorId
  }

  function requireAuth(): boolean {
    return isLoggedIn.value
  }

  return {
    currentRole,
    isDeveloper,
    isLoggedIn,
    hasRole,
    hasAnyRole,
    canManageCategories,
    canEditArticle,
    canDeleteArticle,
    requireAuth,
  }
}
