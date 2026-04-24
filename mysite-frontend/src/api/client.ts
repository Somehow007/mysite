import axios from 'axios'
import type { AxiosInstance, InternalAxiosRequestConfig, AxiosResponse } from 'axios'
import { getItem, removeItem } from '@/utils/storage'
import type { ApiResponse } from '@/types'

const apiClient: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '',
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json',
  },
})

apiClient.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = getItem<string>('access_token')
    const noAuthPaths = ['/v1/auth/login', '/v1/auth/register', '/v1/auth/refresh']
    const isNoAuthPath = noAuthPaths.some(path => config.url?.includes(path))
    if (token && config.headers && !isNoAuthPath) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  },
)

apiClient.interceptors.response.use(
  (response: AxiosResponse<ApiResponse<unknown>>) => {
    const data = response.data
    if (data && data.code !== '0') {
      const err = new Error(data.message || '请求失败')
      return Promise.reject(err)
    }
    return response
  },
  (error) => {
    const noAuthPaths = ['/v1/auth/login', '/v1/auth/register', '/v1/auth/refresh']
    const isNoAuthPath = noAuthPaths.some(path => error.config?.url?.includes(path))
    if (error.response?.status === 401 && !isNoAuthPath) {
      removeItem('access_token')
      removeItem('refresh_token')
      window.location.href = '/login'
    }
    const message = error.response?.data?.message || error.message || '网络错误'
    return Promise.reject(new Error(message))
  },
)

function transformIPage<T>(raw: unknown): { list: T[]; pagination: { page: number; size: number; total: number; totalPages: number } } {
  if (raw && typeof raw === 'object' && 'records' in raw) {
    const page = raw as Record<string, unknown>
    return {
      list: (page.records || []) as T[],
      pagination: {
        page: (page.current as number) || 1,
        size: (page.size as number) || 10,
        total: (page.total as number) || 0,
        totalPages: (page.pages as number) || 0,
      },
    }
  }
  return { list: [] as T[], pagination: { page: 1, size: 10, total: 0, totalPages: 0 } }
}

export async function get<T>(url: string, params?: Record<string, unknown>): Promise<T> {
  const response = await apiClient.get<ApiResponse<T>>(url, { params })
  return response.data.data as T
}

export async function getPaginated<T>(url: string, params?: Record<string, unknown>): Promise<{ list: T[]; pagination: { page: number; size: number; total: number; totalPages: number } }> {
  const response = await apiClient.get<ApiResponse<unknown>>(url, { params })
  return transformIPage<T>(response.data.data)
}

export async function post<T>(url: string, data?: unknown): Promise<T> {
  const response = await apiClient.post<ApiResponse<T>>(url, data)
  return response.data.data as T
}

export async function put<T>(url: string, data?: unknown): Promise<T> {
  const response = await apiClient.put<ApiResponse<T>>(url, data)
  return response.data.data as T
}

export async function del<T>(url: string, data?: unknown): Promise<T> {
  const response = await apiClient.delete<ApiResponse<T>>(url, { data })
  return response.data.data as T
}

export async function patch<T>(url: string, data?: unknown): Promise<T> {
  const response = await apiClient.patch<ApiResponse<T>>(url, data)
  return response.data.data as T
}

export default apiClient
