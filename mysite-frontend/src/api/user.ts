import { get, getPaginated, post, put } from './client'
import type { User, PaginatedResponse } from '@/types'

export function getCurrentUser(): Promise<User> {
  return get<User>('/v1/auth/me')
}

export function getUserById(id: string): Promise<User> {
  return get<User>(`/v1/users/${id}`)
}

export function updateUser(data: Partial<User>): Promise<User> {
  return put<User>('/v1/users/me', data)
}

export function followUser(userId: string): Promise<void> {
  return post<void>('/v1/users/follow', { userId })
}

export function getFollowers(userId: string, params?: {
  page?: number
  size?: number
}): Promise<PaginatedResponse<User>> {
  return getPaginated<User>(`/v1/users/${userId}/followers`, params as Record<string, unknown>)
}

export function getFollowings(userId: string, params?: {
  page?: number
  size?: number
}): Promise<PaginatedResponse<User>> {
  return getPaginated<User>(`/v1/users/${userId}/followings`, params as Record<string, unknown>)
}
