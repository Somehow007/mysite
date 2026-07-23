export type UserRole = 'ADMIN' | 'CREATOR' | 'USER'

export interface User {
  id: string
  username: string
  realName: string
  email?: string
  sex?: number
  role?: UserRole
  followingCount?: number
  followerCount?: number
  avatar?: string
  favorites?: Record<string, unknown>[]
  histories?: Record<string, unknown>[]
}

export interface Category {
  id: string
  name: string
  slug: string
  description: string
  sortOrder: number
  parentId?: string
  level: number
  path?: string
  status: number
  icon?: string
  color?: string
  seoTitle?: string
  seoDescription?: string
  seoKeywords?: string
  articleCount?: number
  children?: Category[]
}

export interface Tag {
  id: string
  name: string
  slug: string
  articleCount?: number
}

export interface Article {
  id: string
  title: string
  summary: string
  content: string
  coverImage: string | null
  categoryId: string | null
  categoryName: string | null
  categorySlug: string | null
  authorId: string
  authorName: string
  viewCount: number
  favoriteCount: number
  readingTime?: number
  isFavorited?: boolean
  visibility?: number
  tags: Tag[] | null
  updateTime: string
  collectionId?: string | null
  collectionTitle?: string | null
  collectionSortOrder?: number | null
}

export interface ArticleListItem {
  id: string
  title: string
  summary: string
  coverImage: string | null
  authorId: string
  authorName: string
  categoryName: string | null
  categorySlug: string | null
  viewCount: number
  favoriteCount: number
  readingTime?: number
  published?: number
  isFavorited?: boolean
  visibility?: number
  createTime: string
  updateTime: string
  collectionId?: string | null
  collectionTitle?: string | null
  collectionSortOrder?: number | null
}

export interface Pagination {
  page: number
  size: number
  total: number
  totalPages: number
}

export interface PaginatedResponse<T> {
  list: T[]
  pagination: Pagination
}

export interface ApiResponse<T> {
  code: string
  message: string | null
  data: T | null
  requestId?: string | null
  success: boolean
}

export interface LoginRequest {
  username: string
  password: string
}

export interface RegisterRequest {
  username: string
  password: string
  email: string
  realName: string
  phoneNumber: string
}

export interface ChangePasswordRequest {
  oldPassword: string
  newPassword: string
}

export interface AuthTokens {
  accessToken: string
  refreshToken: string
  expiresIn: number
  userId: string
  username: string
  role?: UserRole
}

export interface SiteConfig {
  title: string
  description: string
  author: string
  url: string
  commentEnabled?: boolean
  navigation: NavigationItem[]
}

export interface NavigationItem {
  label: string
  path: string
}

export interface ArchiveItem {
  year: string
  months: ArchiveMonth[]
}

export interface ArchiveMonth {
  month: string
  articles: ArchiveArticle[]
}

export interface ArchiveArticle {
  id: string
  title: string
  summary: string
  coverImage: string | null
  authorName: string
  createTime: string
}

export interface SearchParams {
  keyword: string
  page?: number
  size?: number
  searchType?: 'title' | 'content' | 'all'
}

export interface Comment {
  id: string
  articleId: string
  parentId: string | null
  rootId: string | null
  userId: string | null
  nickname: string
  email: string | null
  avatar: string | null
  content: string
  likeCount: number
  replyCount: number
  status: number
  isLiked: boolean
  createTime: string
  replies: Comment[]
}

export interface CommentAdmin {
  id: string
  articleId: string
  articleTitle: string | null
  parentId: string | null
  userId: string | null
  nickname: string
  email: string | null
  avatar: string | null
  content: string
  ipAddress: string | null
  userAgent: string | null
  likeCount: number
  replyCount: number
  status: number
  createTime: string
}

export interface CommentLikeResult {
  liked: boolean
  likeCount: number
}

// ========== 合集相关类型 ==========

export interface Collection {
  id: string
  title: string
  description: string | null
  coverImage: string | null
  authorId: string
  authorName: string
  articleCount: number
  sortOrder: number
  totalViewCount?: number
  createTime: string
  updateTime: string
}

export interface CollectionDetail extends Collection {
  articles: CollectionArticleItem[]
}

export interface CollectionArticleItem {
  id: string
  title: string
  summary: string | null
  coverImage: string | null
  authorName: string
  authorId: string
  viewCount: number
  favoriteCount: number
  readingTime: number | null
  sortOrder: number
  createTime: string
}

export interface ArticleNavInfo {
  prev: { id: string; title: string } | null
  next: { id: string; title: string } | null
  inCollection: boolean
  collectionId: string | null
  collectionTitle: string | null
}

export interface CollectionListItem extends Collection {}

export interface CreateCollectionRequest {
  title: string
  description?: string
  coverImage?: string
  sortOrder?: number
}

export interface UpdateCollectionRequest {
  title?: string
  description?: string
  coverImage?: string
  sortOrder?: number
}

// ========== RAG / AI 助手 ==========

export interface SourceChunk {
  title: string
  content: string
  score: number
  articleId?: string | null
}

export interface ChatMessage {
  id: number               // 前端自增，v-for key
  role: 'user' | 'assistant'
  content: string
  sources: SourceChunk[]
  pending?: boolean        // 流式生成中
  failed?: boolean         // 无输出即失败，可重试
  truncated?: boolean      // 被取消或中断，保留部分内容
}

// ========== RAG 知识库管理 ==========

export interface KnowledgeBase {
  id: string
  name: string
  description: string | null
  collectionName: string
  embeddingModel: string
  embeddingDimension: number
  chunkSize: number
  chunkOverlap: number
  docCount: number
  createTime: string
  updateTime: string
}

export interface KnowledgeDocument {
  id: string
  kbId: string
  title: string
  sourceType: 'ARTICLE' | 'UPLOAD'
  sourceRef: string | null
  fileType: string | null
  status: 'PENDING' | 'CHUNKING' | 'READY' | 'FAILED'
  failReason: string | null
  chunkCount: number
  charCount: number
  createTime: string
}
