import { put } from './client'
import apiClient from './client'
import type { User } from '@/types'
import type { ApiResponse } from '@/types'

export function updateUser(data: Partial<User>): Promise<User> {
  return put<User>('/v1/users/me', data)
}

export async function uploadAvatar(file: File): Promise<string> {
  const formData = new FormData()
  formData.append('file', file)
  const res = await apiClient.post<ApiResponse<{ url: string }>>('/v1/users/avatar', formData, {
    timeout: 30000,
  })
  const url = res.data.data?.url || ''
  if (url) {
    const { useUserStore } = await import('@/stores/user')
    const store = useUserStore()
    if (store.user) {
      store.user.avatar = url
    }
  }
  return url
}
