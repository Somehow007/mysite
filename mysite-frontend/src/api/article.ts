import { get, getPaginated, post, put, del } from './client'
import type { Article, ArticleListItem, PaginatedResponse, SearchParams } from '@/types'

export interface CreateArticleRequest {
  title: string
  content: string
  authorId: string
  summary?: string
  coverImage?: string
  categoryId?: string
  tagIds?: string[]
  published?: number
}

export interface UpdateArticleRequest {
  id: string
  title?: string
  content?: string
  summary?: string
  coverImage?: string
  categoryId?: string
  tagIds?: string[]
  published?: number
}

export function getArticles(params?: {
  page?: number
  size?: number
  keyword?: string
  searchType?: string
  categorySlug?: string
  tagSlug?: string
}): Promise<PaginatedResponse<ArticleListItem>> {
  return getPaginated<ArticleListItem>('/v1/articles', {
    current: params?.page || 1,
    size: params?.size || 10,
    keyword: params?.keyword,
    searchType: params?.searchType,
    categorySlug: params?.categorySlug,
    tagSlug: params?.tagSlug,
  })
}

export function getArticleById(id: string): Promise<Article> {
  return get<Article>(`/v1/articles/${id}`)
}

export function createArticle(data: CreateArticleRequest): Promise<void> {
  return post<void>('/v1/articles', data)
}

export function updateArticle(data: UpdateArticleRequest): Promise<void> {
  return put<void>(`/v1/articles/${data.id}`, data)
}

export function deleteArticle(id: string): Promise<void> {
  return del<void>(`/v1/articles/${id}`)
}

export function favoriteArticle(id: string): Promise<void> {
  return post<void>(`/v1/articles/${id}/favorite`)
}

export function getFavoriteArticles(params?: {
  page?: number
  size?: number
}): Promise<PaginatedResponse<ArticleListItem>> {
  return getPaginated<ArticleListItem>('/v1/articles/favorites', {
    current: params?.page || 1,
    size: params?.size || 10,
  })
}

export function getArchiveList(): Promise<unknown> {
  return get<unknown>('/v1/articles/archive')
}

export function searchArticles(params: SearchParams): Promise<PaginatedResponse<ArticleListItem>> {
  return getPaginated<ArticleListItem>('/v1/articles', {
    keyword: params.keyword,
    searchType: params.searchType,
    current: params.page || 1,
    size: params.size || 10,
  })
}
