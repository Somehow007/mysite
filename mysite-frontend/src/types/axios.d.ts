import type { ApiResponseType } from './api/config'

declare module 'axios' {
  export interface AxiosInstance {
    request<T = ApiResponseType>(config: AxiosRequestConfig): Promise<T>;
    get<T = ApiResponseType>(url: string, config?: AxiosRequestConfig): Promise<T>;
    delete<T = ApiResponseType>(url: string, config?: AxiosRequestConfig): Promise<T>;
    head<T = ApiResponseType>(url: string, config?: AxiosRequestConfig): Promise<T>;
    options<T = ApiResponseType>(url: string, config?: AxiosRequestConfig): Promise<T>;
    post<T = ApiResponseType>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T>;
    put<T = ApiResponseType>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T>;
    patch<T = ApiResponseType>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T>;
  }
}
