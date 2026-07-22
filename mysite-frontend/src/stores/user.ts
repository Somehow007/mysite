import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { User, UserRole } from '@/types'
import { getItem, setItem, removeItem } from '@/utils/storage'
import * as authApi from '@/api/auth'
import * as userApi from '@/api/user'

export const useUserStore = defineStore('user', () => {
  const user = ref<User | null>(null)
  const loading = ref(false)

  const isLoggedIn = computed(() => !!user.value)
  const displayName = computed(() => user.value?.realName || user.value?.username || '')

  /** 兼容旧角色映射 */
  function normalizeRole(r?: string): UserRole | undefined {
    if (!r) return undefined
    if (r === 'DEVELOPER') return 'ADMIN'
    if (r === 'ADMIN' || r === 'CREATOR' || r === 'USER') return r
    return 'USER'
  }

  const role = computed<UserRole | undefined>(() => normalizeRole(user.value?.role))
  const isAdmin = computed(() => role.value === 'ADMIN')
  const isCreator = computed(() => role.value === 'CREATOR')
  /** @deprecated 使用 {@link isAdmin} 替代 */
  const isDeveloper = computed(() => isAdmin.value)

  async function fetchCurrentUser() {
    loading.value = true
    try {
      user.value = await authApi.getCurrentUser()
      if (user.value?.role) {
        setItem('user_role', user.value.role)
      }
    } catch {
      user.value = null
      removeItem('access_token')
      removeItem('refresh_token')
      removeItem('user_role')
    } finally {
      loading.value = false
    }
  }

  function setUser(userData: User | null) {
    user.value = userData
    if (userData?.role) {
      setItem('user_role', userData.role)
    }
  }

  function setTokens(accessToken: string, refreshToken: string) {
    setItem('access_token', accessToken)
    setItem('refresh_token', refreshToken)
  }

  async function updateUser(data: Partial<User>) {
    const updated = await userApi.updateUser(data)
    user.value = updated
    return updated
  }

  async function logout() {
    try {
      await authApi.logout()
    } finally {
      user.value = null
      removeItem('access_token')
      removeItem('refresh_token')
      removeItem('user_role')
      const { useFavorite } = await import('@/composables/useFavorite')
      useFavorite().clearCache()
    }
  }

  function init() {
    const token = getItem<string>('access_token')
    if (token) {
      fetchCurrentUser()
    }
  }

  return {
    user,
    loading,
    isLoggedIn,
    displayName,
    role,
    isAdmin,
    isCreator,
    isDeveloper, // deprecated compat
    fetchCurrentUser,
    setUser,
    setTokens,
    updateUser,
    logout,
    init,
  }
})
