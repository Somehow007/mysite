import { get, getPaginated, post, put, del } from './client'
import type { Article, ArticleListItem, PaginatedResponse, SearchParams } from '@/types'

export interface CreateArticleRequest {
  title: string
  content: string
  authorId: string
  summary?: string
  coverImage?: string | null
  categoryId?: string
  tagIds?: string[]
  published?: number
  visibility?: number
  collectionId?: string
}

export interface UpdateArticleRequest {
  id: string
  title?: string
  content?: string
  summary?: string
  coverImage?: string | null
  categoryId?: string
  tagIds?: string[]
  published?: number
  visibility?: number
}

export function getArticles(params?: {
  page?: number
  size?: number
  keyword?: string
  searchType?: string
  categorySlug?: string
  tagSlug?: string
  collectionId?: string
  published?: number | null
  sortField?: string
  sortOrder?: string
}): Promise<PaginatedResponse<ArticleListItem>> {
  return getPaginated<ArticleListItem>('/v1/articles', {
    current: params?.page || 1,
    size: params?.size || 10,
    keyword: params?.keyword,
    searchType: params?.searchType,
    categorySlug: params?.categorySlug,
    tagSlug: params?.tagSlug,
    collectionId: params?.collectionId,
    published: params?.published,
    sortField: params?.sortField,
    sortOrder: params?.sortOrder,
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

export function batchDeleteArticles(ids: string[]): Promise<void> {
  return del<void>('/v1/articles/batch', { ids })
}

/** 批量修改可见性：0=公开，1=仅自己可见。选中文章混合公开/隐藏状态时后端会拒绝 */
export function batchUpdateArticleVisibility(ids: string[], visibility: number): Promise<void> {
  return put<void>('/v1/articles/visibility/batch', { ids, visibility })
}

export interface FavoriteResult {
  favorited: boolean
  favoriteCount: number
}

export function favoriteArticle(id: string): Promise<FavoriteResult> {
  return post<FavoriteResult>(`/v1/articles/${id}/favorite`)
}

export function getFavoriteArticles(params?: {
  page?: number
  size?: number
  keyword?: string
  /** 管理员查看他人收藏时传入目标 userId */
  userId?: string
}): Promise<PaginatedResponse<ArticleListItem>> {
  return getPaginated<ArticleListItem>('/v1/articles/favorites', {
    current: params?.page || 1,
    size: params?.size || 10,
    keyword: params?.keyword,
    userId: params?.userId,
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

/** 文档导入图片处理明细 */
export interface ArticleImportImage {
  originalRef: string
  status: 'UPLOADED' | 'KEPT' | 'FAILED'
  url?: string | null
  reason?: string | null
}

/** 文档导入结果：content 可直接写入编辑器 */
export interface ArticleImportResult {
  title?: string | null
  content: string
  fileName: string
  images: ArticleImportImage[]
}

/**
 * 导入 TXT / Markdown 文档；可附带图片附件（Obsidian ![[file]] / 相对路径按文件名匹配）。
 * 后端会自动转存可上传的图片；失败的图片在 content 里以「图片待补传」callout 标出。
 */
export function importArticle(file: File, attachments?: File[]): Promise<ArticleImportResult> {
  const formData = new FormData()
  formData.append('file', file)
  if (attachments && attachments.length > 0) {
    for (const att of attachments) {
      formData.append('attachments', att)
    }
  }
  // 图片阶段有独立预算，导入接口给足超时
  return post<ArticleImportResult>('/v1/articles/import', formData, { timeout: 90000 })
}
