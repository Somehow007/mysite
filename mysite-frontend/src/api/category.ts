import { get, getPaginated, post, put, del } from './client'
import type { Category, PaginatedResponse, ArticleListItem } from '@/types'

export function getCategories(): Promise<Category[]> {
  return get<Category[]>('/v1/categories')
}

export function getCategoryBySlug(slug: string): Promise<Category> {
  return get<Category>(`/v1/categories/${slug}`)
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

export function createCategory(data: Partial<Category>): Promise<Category> {
  return post<Category>('/v1/categories', data)
}

export function updateCategory(id: string, data: Partial<Category>): Promise<Category> {
  return put<Category>(`/v1/categories/${id}`, data)
}

export function deleteCategory(id: string): Promise<void> {
  return del<void>(`/v1/categories/${id}`)
}
