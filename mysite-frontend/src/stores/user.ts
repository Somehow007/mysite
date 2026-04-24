import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { User } from '@/types'
import { getItem, setItem, removeItem } from '@/utils/storage'
import * as authApi from '@/api/auth'
import * as userApi from '@/api/user'

export const useUserStore = defineStore('user', () => {
  const user = ref<User | null>(null)
  const loading = ref(false)

  const isLoggedIn = computed(() => !!user.value)
  const displayName = computed(() => user.value?.realName || user.value?.username || '')

  async function fetchCurrentUser() {
    loading.value = true
    try {
      user.value = await authApi.getCurrentUser()
    } catch {
      user.value = null
      removeItem('access_token')
      removeItem('refresh_token')
    } finally {
      loading.value = false
    }
  }

  function setUser(userData: User | null) {
    user.value = userData
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
    fetchCurrentUser,
    setUser,
    setTokens,
    updateUser,
    logout,
    init,
  }
})
