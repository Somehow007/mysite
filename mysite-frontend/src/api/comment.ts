import { get, post, del, getPaginated } from './client'
import type { Comment, CommentAdmin, CommentLikeResult, PaginatedResponse } from '@/types'

export interface CreateCommentRequest {
  articleId: string
  parentId?: string | null
  content: string
}

export function getArticleComments(articleId: string): Promise<Comment[]> {
  return get<Comment[]>(`/v1/comments/article/${articleId}`)
}

export function createComment(data: CreateCommentRequest): Promise<void> {
  return post<void>('/v1/comments', data)
}

export function deleteComment(id: string): Promise<void> {
  return del<void>(`/v1/comments/${id}`)
}

export function toggleCommentLike(id: string): Promise<CommentLikeResult> {
  return post<CommentLikeResult>(`/v1/comments/${id}/like`)
}

export function getAdminComments(params?: {
  page?: number
  size?: number
  articleId?: number
  status?: number
  keyword?: string
}): Promise<PaginatedResponse<CommentAdmin>> {
  return getPaginated<CommentAdmin>('/v1/admin/comments/list', {
    current: params?.page || 1,
    size: params?.size || 20,
    articleId: params?.articleId,
    status: params?.status,
    keyword: params?.keyword,
  })
}

export function updateCommentStatus(id: string, status: number): Promise<void> {
  return post<void>(`/v1/admin/comments/${id}/status?status=${status}`)
}
