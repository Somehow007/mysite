import { get, getPaginated, post, put, del } from './client'
import type { Tag, PaginatedResponse, ArticleListItem } from '@/types'

export function getTags(): Promise<Tag[]> {
  return get<Tag[]>('/v1/tags')
}

export function getTagBySlug(slug: string): Promise<Tag> {
  return get<Tag>(`/v1/tags/${slug}`)
}

export function getTagArticles(slug: string, params?: {
  page?: number
  size?: number
}): Promise<PaginatedResponse<ArticleListItem>> {
  return getPaginated<ArticleListItem>('/v1/articles', {
    tagSlug: slug,
    current: params?.page || 1,
    size: params?.size || 10,
  })
}

export function createTag(data: Partial<Tag>): Promise<Tag> {
  return post<Tag>('/v1/tags', data)
}

export function updateTag(id: string, data: Partial<Tag>): Promise<void> {
  return put<void>(`/v1/tags/${id}`, data)
}

export function deleteTag(id: string): Promise<void> {
  return del<void>(`/v1/tags/${id}`)
}
