import { get, post } from './config'
import type { ApiResponse, PaginatedResponse, UserSelectRespDTO, UserPageQueryFollowRespDTO, UserSearchRespDTO } from '@/types/blog'

export interface UserRegisterParams {
  username: string
  password: string
  realName: string
  email?: string
  phoneNumber?: string
  sex?: number
}

export interface UserLoginParams {
  username: string
  password: string
}

export interface UserFollowParams {
  userId: string
  isFollow: boolean
}

export interface UserFollowListParams {
  userId: string | number
  page?: number
  size?: number
}

export interface UserSearchParams {
  keyword?: string
  page?: number
  size?: number
}

export const registerUser = (params: UserRegisterParams): Promise<ApiResponse<void>> => {
  return post<void>('/registry', params)
}

export const loginUser = (params: UserLoginParams): Promise<ApiResponse<void>> => {
  const formData = new URLSearchParams()
  formData.append('username', params.username)
  formData.append('password', params.password)
  
  return post<void>('/login', formData, {
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded',
    },
  })
}

export const logoutUser = (): Promise<ApiResponse<void>> => {
  return post<void>('/logout')
}

export const getCurrentUser = (): Promise<ApiResponse<UserSelectRespDTO>> => {
  return get<UserSelectRespDTO>('/api/user/current')
}

export const getUserById = (id: string | number): Promise<ApiResponse<UserSelectRespDTO>> => {
  return get<UserSelectRespDTO>(`/api/user/query/${id}`)
}

export const followUser = (params: UserFollowParams): Promise<ApiResponse<void>> => {
  return post<void>('/api/user/follow', params)
}

export const getFollowers = (params: UserFollowListParams): Promise<ApiResponse<PaginatedResponse<UserPageQueryFollowRespDTO>>> => {
  return get<PaginatedResponse<UserPageQueryFollowRespDTO>>(`/api/user/followees/${params.userId}`, {
    params: {
      page: params.page || 1,
      size: params.size || 10,
    }
  })
}

export const getFollowings = (params: UserFollowListParams): Promise<ApiResponse<PaginatedResponse<UserPageQueryFollowRespDTO>>> => {
  return get<PaginatedResponse<UserPageQueryFollowRespDTO>>(`/api/user/followers/${params.userId}`, {
    params: {
      page: params.page || 1,
      size: params.size || 10,
    }
  })
}

export const searchUsers = (params: UserSearchParams = {}): Promise<ApiResponse<PaginatedResponse<UserSearchRespDTO>>> => {
  return get<PaginatedResponse<UserSearchRespDTO>>('/api/user/search', {
    params: {
      keyword: params.keyword || '',
      page: params.page || 1,
      size: params.size || 10,
    }
  })
}
