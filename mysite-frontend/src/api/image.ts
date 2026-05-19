import apiClient from './client'
import type { ApiResponse } from '@/types'

export interface ImageUploadResult {
  id: string
  originalName: string
  url: string
  fileSize: number
  contentType: string
  width: number | null
  height: number | null
}

export function uploadImage(file: File): Promise<ImageUploadResult> {
  const formData = new FormData()
  formData.append('file', file)
  const response = apiClient.post<ApiResponse<ImageUploadResult>>('/v1/images/upload', formData, {
    timeout: 30000,
  })
  return response.then(res => res.data.data as ImageUploadResult)
}

export function uploadImageByUrl(url: string): Promise<ImageUploadResult> {
  const response = apiClient.post<ApiResponse<ImageUploadResult>>('/v1/images/upload-url', { url }, {
    timeout: 30000,
  })
  return response.then(res => res.data.data as ImageUploadResult)
}

export function deleteImage(id: string): Promise<void> {
  const response = apiClient.delete<ApiResponse<void>>(`/v1/images/${id}`)
  return response.then(() => {})
}
