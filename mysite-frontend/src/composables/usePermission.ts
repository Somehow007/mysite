import { computed } from 'vue'
import { useUserStore } from '@/stores/user'
import type { UserRole } from '@/types'

export function usePermission() {
  const userStore = useUserStore()

  const currentRole = computed<UserRole | undefined>(() => userStore.role)

  const isAdmin = computed(() => currentRole.value === 'ADMIN')
  const isCreator = computed(() => currentRole.value === 'CREATOR')
  const isDeveloper = computed(() => isAdmin.value) // deprecated compat
  const isLoggedIn = computed(() => userStore.isLoggedIn)

  /** 是否有权创建/编辑文章 */
  function canCreateArticle(): boolean {
    return isAdmin.value || isCreator.value
  }

  /** 是否有权管理所有资源 */
  function canManageAll(): boolean {
    return isAdmin.value
  }

  function canManageCategories(): boolean {
    return isAdmin.value
  }

  function canModifyArticle(authorId: string): boolean {
    if (isAdmin.value) return true
    if (!isLoggedIn.value) return false
    return userStore.user?.id === authorId
  }

  return {
    currentRole,
    isAdmin,
    isCreator,
    isDeveloper, // deprecated compat
    isLoggedIn,
    canCreateArticle,
    canManageAll,
    canManageCategories,
    canModifyArticle,
  }
}
