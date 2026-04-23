export interface User {
  id: string
  username: string
  realName: string
  email?: string
  sex?: number
  followingCount?: number
  followerCount?: number
  favorites?: any[]
  histories?: any[]
}

export interface Category {
  id: string
  name: string
  slug: string
  description: string
  sortOrder: number
  articleCount?: number
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

export interface AuthTokens {
  accessToken: string
  refreshToken: string
  expiresIn: number
  userId: string
  username: string
}

export interface SiteConfig {
  title: string
  description: string
  author: string
  url: string
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
  id: number
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
