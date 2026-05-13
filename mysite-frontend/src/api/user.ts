import { put } from './client'
import type { User } from '@/types'

export function updateUser(data: Partial<User>): Promise<User> {
  return put<User>('/v1/users/me', data)
}
