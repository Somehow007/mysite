import { get, getPaginated, post, put, del } from './client'
import type { Collection, CollectionDetail, ArticleNavInfo, CreateCollectionRequest, UpdateCollectionRequest, PaginatedResponse } from '@/types'

export function getCollections(params?: {
  page?: number
  size?: number
  keyword?: string
  authorId?: string
  sortBy?: 'viewCount'
  sortField?: string
  sortOrder?: string
}): Promise<PaginatedResponse<Collection>> {
  return getPaginated<Collection>('/v1/collections', {
    current: params?.page || 1,
    size: params?.size || 10,
    keyword: params?.keyword,
    authorId: params?.authorId,
    sortBy: params?.sortBy,
    sortField: params?.sortField,
    sortOrder: params?.sortOrder,
  })
}

export function getCollectionById(id: string, current = 1, size = 10): Promise<CollectionDetail> {
  return get<CollectionDetail>(`/v1/collections/${id}`, { current, size })
}

export function createCollection(data: CreateCollectionRequest): Promise<string> {
  return post<string>('/v1/collections', data)
}

export function updateCollection(id: string, data: UpdateCollectionRequest): Promise<void> {
  return put<void>(`/v1/collections/${id}`, data)
}

export function deleteCollection(id: string): Promise<void> {
  return del<void>(`/v1/collections/${id}`)
}

export function addArticleToCollection(collectionId: string, articleId: string): Promise<void> {
  return post<void>(`/v1/collections/${collectionId}/articles/${articleId}`)
}

export function removeArticleFromCollection(collectionId: string, articleId: string): Promise<void> {
  return del<void>(`/v1/collections/${collectionId}/articles/${articleId}`)
}

export function batchAddArticles(collectionId: string, articleIds: string[]): Promise<void> {
  return post<void>(`/v1/collections/${collectionId}/articles/batch`, { articleIds })
}

export function updateArticleSort(collectionId: string, articleIds: string[]): Promise<void> {
  return put<void>(`/v1/collections/${collectionId}/articles/sort`, { articleIds })
}

export function getArticleNavigation(articleId: string): Promise<ArticleNavInfo> {
  return get<ArticleNavInfo>(`/v1/articles/${articleId}/navigation`)
}
