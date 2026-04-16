export interface Author {
  id: string
  name: string
  slug: string
  profile_image?: string
  bio?: string
  website?: string
  twitter?: string
  facebook?: string
  linkedin?: string
  instagram?: string
  youtube?: string
  mastodon?: string
  bluesky?: string
  threads?: string
  tiktok?: string
}

export interface Tag {
  id: string
  name: string
  slug: string
  description?: string
  feature_image?: string
}

export interface Post {
  id: string
  title: string
  summary: string
  content: string
  feature_image?: string
  featured: boolean
  createTime: string
  updateTime: string
  reading_time: string
  viewCount: number
  favoriteCount: number
  authorName: string
  authorId: string
  authors?: Author[]
  tags: Tag[]
  primary_tag?: Tag
  url: string
  next_post?: Post
  prev_post?: Post
  published_at?: string
  excerpt?: string
}

export interface Site {
  title: string
  description?: string
  logo?: string
  cover_image?: string
  url: string
  locale: string
  members_enabled: boolean
  members_invite_only: boolean
  navigation?: NavigationItem[]
  secondary_navigation?: NavigationItem[]
}

export interface NavigationItem {
  label: string
  url: string
  slug: string
  current?: boolean
}

export interface Pagination {
  pages: number
  current: number
  size: number
  total: number
  prev?: number
  next?: number
  page?: number
  limit?: number
}

export interface PaginatedResponse<T> {
  records: T[]
  current: number
  size: number
  total: number
  pages: number
}

export interface ApiResponse<T = unknown> {
  code: string
  message: string
  data: T
  requestId?: string
}

export interface PostsResponse {
  posts: Post[]
  pagination: Pagination
}

export interface PostResponse {
  post: Post
}

export interface SearchPostsResponse {
  posts: Post[]
  pagination: Pagination
}

export interface AuthorResponse {
  author: Author
  posts: Post[]
  pagination: Pagination
}

export interface TagResponse {
  tag: Tag
  posts: Post[]
  pagination: Pagination
}

export interface Page {
  id: string
  slug: string
  title: string
  content: string
  feature_image?: string
  show_title_and_feature_image?: boolean
}

export interface CreatePostData {
  title: string
  content: string
  summary?: string
  authorId?: string
  published?: number
}

export interface UpdatePostData extends Partial<CreatePostData> {
  id: number
}

export interface User {
  id: number
  username: string
  realName: string
  sex: number
  email?: string
  phoneNumber?: string
  avatar?: string
  followingCount: number
  followerCount: number
  favorites?: Post[]
  histories?: Post[]
}

export interface ArticlePageQueryRespDTO {
  id: number
  title: string
  summary: string
  viewCount: number
  favoriteCount: number
  authorName: string
  createTime?: string
  updateTime?: string
}

export interface ArticleSelectRespDTO {
  id: number
  title: string
  content: string
  summary: string
  authorId: number
  published: number
  viewCount: number
  favoriteCount: number
  createTime: string
  updateTime: string
}

export interface UserSelectRespDTO {
  id: number
  username: string
  realName: string
  sex: number
  email?: string
  phoneNumber?: string
  followingCount: number
  followerCount: number
  createTime?: string
  updateTime?: string
}

export interface UserPageQueryFollowRespDTO {
  id: number
  username: string
  realName: string
  followingCount: number
  followerCount: number
}

export interface UserSearchRespDTO {
  id: number
  username: string
  realName: string
  followingCount: number
  followerCount: number
}
