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

  // 本地 profile 缓存（mysite_user_profile）：/me 返回前先用它水合 user.value，
  // 让侧栏/页面在首帧就有角色与身份，消除「过段时间才出现菜单」的观感。
  // 仅作 UI 先行渲染，信任级别与路由守卫读 user_role 一致，服务端鉴权不变。
  const PROFILE_KEY = 'user_profile'
  let loadPromise: Promise<void> | null = null
  let fetched = false

  function persistProfile(u: User | null) {
    if (u) {
      setItem(PROFILE_KEY, u)
      if (u.role) setItem('user_role', u.role)
    } else {
      removeItem(PROFILE_KEY)
      removeItem('user_role')
    }
  }

  function clearSession() {
    user.value = null
    fetched = false
    removeItem('access_token')
    removeItem('refresh_token')
    persistProfile(null)
  }

  async function fetchCurrentUser() {
    loading.value = true
    try {
      user.value = await authApi.getCurrentUser()
      fetched = true
      persistProfile(user.value)
    } catch {
      clearSession()
    } finally {
      loading.value = false
    }
  }

  function setUser(userData: User | null) {
    user.value = userData
    persistProfile(userData)
  }

  function setTokens(accessToken: string, refreshToken: string) {
    setItem('access_token', accessToken)
    setItem('refresh_token', refreshToken)
  }

  async function updateUser(data: Partial<User>) {
    const updated = await userApi.updateUser(data)
    user.value = updated
    persistProfile(updated)
    return updated
  }

  async function logout() {
    try {
      await authApi.logout()
    } finally {
      clearSession()
      const { useFavorite } = await import('@/composables/useFavorite')
      useFavorite().clearCache()
    }
  }

  // token 存在时：同步水合缓存 profile（首帧即有角色），再去重拉取 /me 校验刷新。
  // 并发调用共享 loadPromise；fetched 保证本会话至多真正请求一次 /me。
  async function ensureUser() {
    const token = getItem<string>('access_token')
    if (!token) return
    if (!user.value) {
      const cached = getItem<User>(PROFILE_KEY)
      if (cached?.id) {
        user.value = cached
        if (cached.role) setItem('user_role', cached.role)
      }
    }
    if (!fetched) {
      loadPromise ??= fetchCurrentUser().finally(() => { loadPromise = null })
      await loadPromise
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
    ensureUser,
  }
})
