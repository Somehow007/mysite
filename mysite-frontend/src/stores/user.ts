import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { getCurrentUser, logoutUser } from '@/api/user'
import type { User } from '@/types/blog'

export const useUserStore = defineStore('user', () => {
  const user = ref<User | null>(null)
  const loading = ref(false)

  const isLoggedIn = computed(() => user.value !== null)

  const displayName = computed(() => {
    if (!user.value) return ''
    return user.value.username || user.value.realName || '用户'
  })

  const fetchCurrentUser = async () => {
    try {
      loading.value = true
      const response = await getCurrentUser()
      if (response && response.data) {
        user.value = {
          id: response.data.id,
          username: response.data.username,
          realName: response.data.realName,
          sex: response.data.sex,
          email: response.data.email,
          phoneNumber: response.data.phoneNumber,
          followingCount: response.data.followingCount || 0,
          followerCount: response.data.followerCount || 0,
        }
      } else {
        user.value = null
      }
    } catch {
      user.value = null
    } finally {
      loading.value = false
    }
  }

  const setUser = (userData: User | null) => {
    user.value = userData
  }

  const logout = async () => {
    try {
      await logoutUser()
    } catch {
      // ignore
    } finally {
      user.value = null
    }
  }

  return {
    user,
    loading,
    isLoggedIn,
    displayName,
    fetchCurrentUser,
    setUser,
    logout,
  }
})
