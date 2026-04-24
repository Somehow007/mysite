import { get, getPaginated, post, put, del, patch } from './client'
import type { Category, PaginatedResponse, ArticleListItem } from '@/types'

export interface CategoryQueryParams {
  name?: string
  parentId?: string
  level?: number
  status?: number
  tree?: boolean
  current?: number
  size?: number
}

export interface CategoryCreateData {
  name: string
  slug: string
  description?: string
  sortOrder?: number
  parentId?: string
  level?: number
  status?: number
  icon?: string
  color?: string
  seoTitle?: string
  seoDescription?: string
  seoKeywords?: string
}

export interface CategoryUpdateData {
  name?: string
  slug?: string
  description?: string
  sortOrder?: number
  parentId?: string
  level?: number
  status?: number
  icon?: string
  color?: string
  seoTitle?: string
  seoDescription?: string
  seoKeywords?: string
}

export function getCategories(): Promise<Category[]> {
  return get<Category[]>('/v1/categories')
}

export function getCategoryTree(): Promise<Category[]> {
  return get<Category[]>('/v1/categories/tree')
}

export function queryCategories(params: CategoryQueryParams): Promise<Category[]> {
  return get<Category[]>('/v1/categories/query', params)
}

export function getCategoryBySlug(slug: string): Promise<Category> {
  return get<Category>(`/v1/categories/${slug}`)
}

export function getCategoryById(id: string): Promise<Category> {
  return get<Category>(`/v1/categories/id/${id}`)
}

export function getChildrenByParentId(parentId: string): Promise<Category[]> {
  return get<Category[]>(`/v1/categories/${parentId}/children`)
}

export function getCategoryArticles(slug: string, params?: {
  page?: number
  size?: number
}): Promise<PaginatedResponse<ArticleListItem>> {
  return getPaginated<ArticleListItem>('/v1/articles', {
    categorySlug: slug,
    current: params?.page || 1,
    size: params?.size || 10,
  })
}

export function createCategory(data: CategoryCreateData): Promise<void> {
  return post<void>('/v1/categories', data)
}

export function updateCategory(id: string, data: CategoryUpdateData): Promise<void> {
  return put<void>(`/v1/categories/${id}`, data)
}

export function deleteCategory(id: string): Promise<void> {
  return del<void>(`/v1/categories/${id}`)
}

export function updateCategoryStatus(id: string, status: number): Promise<void> {
  return patch<void>(`/v1/categories/${id}/status?status=${status}`)
}

export function batchUpdateStatus(ids: string[], status: number): Promise<void> {
  return patch<void>('/v1/categories/batch/status', { ids, status })
}

export function batchDelete(ids: string[]): Promise<void> {
  return del<void>('/v1/categories/batch', { ids })
}

export function updateSortOrder(id: string, sortOrder: number): Promise<void> {
  return patch<void>('/v1/categories/sort', { id, sortOrder })
}
