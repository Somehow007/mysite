import { put } from './client'
import apiClient from './client'
import type { User } from '@/types'
import type { ApiResponse } from '@/types'

export function updateUser(data: Partial<User>): Promise<User> {
  return put<User>('/v1/users/me', data)
}

export function uploadAvatar(file: File): Promise<string> {
  const formData = new FormData()
  formData.append('file', file)
  return apiClient.post<ApiResponse<{ url: string }>>('/v1/users/avatar', formData, {
    timeout: 30000,
  }).then(res => {
    userStore().updateUser({ avatar: res.data.data?.url })
    return res.data.data?.url || ''
  })
}

import { useUserStore as userStore } from '@/stores/user'
