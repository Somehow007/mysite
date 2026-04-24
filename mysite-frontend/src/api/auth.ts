import { post, get } from './client'
import type { LoginRequest, RegisterRequest, AuthTokens, User, ChangePasswordRequest } from '@/types'

export function login(data: LoginRequest): Promise<AuthTokens> {
  return post<AuthTokens>('/v1/auth/login', data)
}

export function register(data: RegisterRequest): Promise<void> {
  return post<void>('/v1/auth/register', data)
}

export function logout(): Promise<void> {
  return post<void>('/v1/auth/logout')
}

export function refreshToken(token: string): Promise<AuthTokens> {
  return post<AuthTokens>('/v1/auth/refresh', { refreshToken: token })
}

export function getCurrentUser(): Promise<User> {
  return get<User>('/v1/auth/me')
}

export function changePassword(data: ChangePasswordRequest): Promise<void> {
  return post<void>('/v1/auth/change-password', data)
}
