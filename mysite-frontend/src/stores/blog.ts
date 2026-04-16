import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { Post, Pagination, ArticlePageQueryRespDTO } from '@/types/blog'
import { searchArticles } from '@/api/article'

export const useBlogStore = defineStore('blog', () => {
  const posts = ref<Post[]>([])
  const pagination = ref<Pagination>({
    current: 1,
    size: 10,
    pages: 1,
    total: 0,
  })
  const loading = ref(false)

  const mapArticleToPost = (article: ArticlePageQueryRespDTO): Post => {
    return {
      id: String(article.id),
      title: article.title,
      summary: article.summary,
      content: '',
      featured: false,
      createTime: article.createTime || '',
      updateTime: article.updateTime || '',
      reading_time: '1 min read',
      viewCount: article.viewCount,
      favoriteCount: article.favoriteCount,
      authorName: article.authorName,
      authorId: '',
      tags: [],
      url: `/post/${article.id}`,
      authors: [],
      published_at: article.createTime || '',
      excerpt: article.summary,
    }
  }

  const fetchPosts = async (page = 1, size = 10) => {
    try {
      loading.value = true
      const response = await searchArticles({ page, size })
      if (response && response.data) {
        posts.value = (response.data.records || []).map(mapArticleToPost)
        pagination.value = {
          current: response.data.current || 1,
          size: response.data.size || size,
          total: response.data.total || 0,
          pages: response.data.pages || 1,
        }
      }
    } catch (error) {
      console.error('获取文章列表失败:', error)
    } finally {
      loading.value = false
    }
  }

  return {
    posts,
    pagination,
    loading,
    fetchPosts,
  }
})
