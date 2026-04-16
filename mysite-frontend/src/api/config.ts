import axios, { type AxiosError, type AxiosInstance, type AxiosRequestConfig, type AxiosResponse } from 'axios'
import { ApiError, ErrorCode, type ApiResponse } from './errors'
import { useUserStore } from '@/stores/user'
import router from '@/router'

const apiClient: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080',
  timeout: 10000,
  withCredentials: true,
  headers: {
    'Content-Type': 'application/json',
  },
})

apiClient.interceptors.request.use(
  (config) => {
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

apiClient.interceptors.response.use(
  (response: AxiosResponse) => {
    const data = response.data
    
    if (data.code === ErrorCode.SUCCESS) {
      return Promise.resolve(data as ApiResponse)
    }
    
    const error = ApiError.fromResponse(data)
    
    if (error.isUnauthorized()) {
      const userStore = useUserStore()
      userStore.setUser(null)
      router.push('/login')
    }
    
    return Promise.reject(error)
  },
  (error: AxiosError) => {
    let apiError: ApiError
    
    if (error.code === 'ECONNABORTED' || error.message.includes('timeout')) {
      apiError = ApiError.timeout()
    } else if (!error.response) {
      apiError = ApiError.networkError()
    } else {
      const status = error.response.status
      
      switch (status) {
        case 401:
          apiError = ApiError.unauthorized()
          const userStore = useUserStore()
          userStore.setUser(null)
          router.push('/login')
          break
        case 403:
          apiError = new ApiError(ErrorCode.FORBIDDEN, '没有权限访问')
          break
        case 404:
          apiError = new ApiError(ErrorCode.NOT_FOUND, '请求的资源不存在')
          break
        case 500:
        case 502:
        case 503:
          apiError = ApiError.serverError()
          break
        default:
          apiError = ApiError.unknownError(`请求失败: ${error.message}`)
      }
    }
    
    return Promise.reject(apiError)
  }
)

export async function request<T = unknown>(config: AxiosRequestConfig): Promise<ApiResponse<T>> {
  const response = await apiClient.request(config)
  return response as ApiResponse<T>
}

export async function get<T = unknown>(url: string, config?: AxiosRequestConfig): Promise<ApiResponse<T>> {
  return request<T>({ ...config, method: 'GET', url })
}

export async function post<T = unknown>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<ApiResponse<T>> {
  return request<T>({ ...config, method: 'POST', url, data })
}

export async function put<T = unknown>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<ApiResponse<T>> {
  return request<T>({ ...config, method: 'PUT', url, data })
}

export async function del<T = unknown>(url: string, config?: AxiosRequestConfig): Promise<ApiResponse<T>> {
  return request<T>({ ...config, method: 'DELETE', url })
}

export default apiClient
