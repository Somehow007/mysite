<template>
  <div class="tag-page">
    <header class="blog-header" :class="{ 'has-cover': false }">
      <div class="inner">
        <div class="archive archive-tag box archive-box">
          <span class="archive-info">
            <span class="archive-type">话题</span>
            <span class="archive-count">
              {{ pagination.total === 0 ? '无文章' : `${pagination.total} 篇文章` }}
            </span>
          </span>
          <h2 class="archive-title">{{ tag.name }}</h2>
          <span v-if="tag.description" class="archive-description" v-html="tag.description"></span>
        </div>
      </div>
    </header>

    <div id="index" class="container">
      <main class="content" role="main">
        <div v-if="loading" class="loading">
          <p>加载中...</p>
        </div>
        <div v-else-if="posts.length === 0" class="no-posts">
          <p>暂无文章</p>
        </div>
        <PostList v-else :posts="posts" :pagination="pagination" @page-change="handlePageChange" />
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import type { Tag, Post, Pagination, ArticlePageQueryRespDTO } from '@/types/blog'
import PostList from '@/components/PostList.vue'
import { searchArticles } from '@/api/article'

const route = useRoute()

const tag = ref<Tag>({
  id: '1',
  name: '标签名',
  slug: 'tag',
  description: '这是标签的描述...',
})

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

const fetchTagPosts = async (page = 1) => {
  try {
    loading.value = true
    const response = await searchArticles({ page, size: 10 })
    if (response && response.data) {
      posts.value = (response.data.records || []).map(mapArticleToPost)
      pagination.value = {
        current: response.data.current || 1,
        size: response.data.size || 10,
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

const handlePageChange = (page: number) => {
  fetchTagPosts(page)
}

onMounted(async () => {
  const slug = route.params.slug as string
  if (slug) {
    tag.value = {
      id: slug,
      name: slug,
      slug: slug,
      description: '',
    }
    await fetchTagPosts()
  }
})
</script>

<style scoped>
.loading {
  text-align: center;
  padding: 2rem;
  color: var(--color-content-secondary);
}

.no-posts {
  text-align: center;
  padding: 4rem;
  color: var(--color-content-secondary);
}
</style>
