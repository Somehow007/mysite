import { get, post, put, del } from './config'
import type { ApiResponse, PaginatedResponse, ArticlePageQueryRespDTO, ArticleSelectRespDTO } from '@/types/blog'

export interface ArticleCreateParams {
  title: string
  content: string
  summary?: string
  authorId?: string
  published?: number
}

export interface ArticleUpdateParams {
  id: number
  title?: string
  content?: string
  summary?: string
  published?: number
}

export interface ArticleSearchParams {
  keyword?: string
  page?: number
  size?: number
  searchType?: 'title' | 'content' | 'author'
}

export interface FavoriteArticleParams {
  page?: number
  size?: number
}

export interface FavoriteParams {
  articleId: string
  userId: string
  isFavorite: boolean
}

export const createArticle = (params: ArticleCreateParams): Promise<ApiResponse<void>> => {
  return post<void>('/api/article/create', params)
}

export const updateArticle = (params: ArticleUpdateParams): Promise<ApiResponse<void>> => {
  return put<void>('/api/article/update', params)
}

export const deleteArticle = (id: string | number): Promise<ApiResponse<void>> => {
  return del<void>(`/api/article/delete/${id}`)
}

export const searchArticles = (params: ArticleSearchParams = {}): Promise<ApiResponse<PaginatedResponse<ArticlePageQueryRespDTO>>> => {
  return get<PaginatedResponse<ArticlePageQueryRespDTO>>('/api/article/search', {
    params: {
      keyword: params.keyword || '',
      page: params.page || 1,
      size: params.size || 10,
      searchType: params.searchType || 'title',
    }
  })
}

export const getFavoriteArticles = (params: FavoriteArticleParams = {}): Promise<ApiResponse<PaginatedResponse<ArticlePageQueryRespDTO>>> => {
  return get<PaginatedResponse<ArticlePageQueryRespDTO>>('/api/article/favorite/page', {
    params: {
      page: params.page || 1,
      size: params.size || 10,
    }
  })
}

export const getArticleById = (id: string | number): Promise<ApiResponse<ArticleSelectRespDTO>> => {
  return get<ArticleSelectRespDTO>(`/api/article/select/${id}`)
}

export const favoriteArticle = (params: FavoriteParams): Promise<ApiResponse<void>> => {
  return post<void>('/api/article/favorite', params)
}
