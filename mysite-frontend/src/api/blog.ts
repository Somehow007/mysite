import apiClient from "./config";
import type { Site, Post, Tag, Page, Pagination } from "@/types/blog";

export const getSite = async (): Promise<Site> => {
  return {
    title: '我的博客',
    description: '个人博客网站',
    url: '/',
    locale: 'zh',
    members_enabled: true,
    members_invite_only: false,
  };
};

export interface BlogListResponse {
  data: {
    content: Post[];
  };
}

export const getBlogs = async (page: number = 0, size: number = 10): Promise<BlogListResponse> => {
  const response = await apiClient.get('/api/article/search', {
    params: { page: page + 1, size }
  });
  const records = (response as { data?: { records?: Record<string, unknown>[] } })?.data?.records || []
  return {
    data: {
      content: records.map(mapArticleToPost)
    }
  };
};

export const getPosts = async (page: number = 1, limit: number = 10) => {
  const response = await apiClient.get('/api/article/search', {
    params: { page, size: limit }
  });
  const data = (response as { data?: { records?: Record<string, unknown>[]; current?: number; size?: number; total?: number; pages?: number } })?.data;
  return {
    posts: (data?.records || []).map(mapArticleToPost),
    pagination: {
      current: data?.current || 1,
      size: data?.size || limit,
      total: data?.total || 0,
      pages: data?.pages || 1,
    } as Pagination
  };
};

export const searchPosts = async (keyword: string, page: number = 1, limit: number = 10) => {
  const response = await apiClient.get('/api/article/search', {
    params: { keyword, page, size: limit }
  });
  const data = (response as { data?: { records?: Record<string, unknown>[]; current?: number; size?: number; total?: number; pages?: number } })?.data;
  return {
    posts: (data?.records || []).map(mapArticleToPost),
    pagination: {
      current: data?.current || 1,
      size: data?.size || limit,
      total: data?.total || 0,
      pages: data?.pages || 1,
    } as Pagination
  };
};

export const getTag = async (slug: string) => {
  return {
    tag: {
      id: slug,
      name: slug,
      slug: slug,
      description: '',
    } as Tag,
    posts: [] as Post[],
    pagination: {
      current: 1,
      size: 10,
      total: 0,
      pages: 1,
    } as Pagination
  };
};

export const getPage = async (slug: string): Promise<Page> => {
  return {
    id: slug,
    slug: slug,
    title: '页面标题',
    content: '<p>页面内容</p>',
    show_title_and_feature_image: true,
  };
};

function mapArticleToPost(raw: Record<string, unknown>): Post {
  return {
    id: String(raw.id ?? ''),
    title: String(raw.title ?? ''),
    summary: String(raw.summary ?? ''),
    content: '',
    featured: false,
    createTime: '',
    updateTime: '',
    reading_time: '1 min read',
    viewCount: Number(raw.viewCount ?? 0),
    favoriteCount: Number(raw.favoriteCount ?? 0),
    authorName: String(raw.authorName ?? ''),
    authorId: '',
    tags: [],
    url: `/post/${raw.id}`,
    authors: [],
    published_at: '',
    excerpt: String(raw.summary ?? ''),
  };
}
