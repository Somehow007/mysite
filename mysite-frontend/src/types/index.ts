export type UserRole = 'DEVELOPER' | 'USER'

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
  tags: Tag[] | null
  updateTime: string
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
  createTime: string
  updateTime: string
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
