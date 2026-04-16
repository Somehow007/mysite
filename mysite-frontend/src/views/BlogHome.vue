<template>
  <div class="blog-home">
    <header class="blog-header" :class="{ 'has-cover': hasCoverImage }">
      <div class="inner">
        <div class="box blog-box">
          <h1 class="blog-name">{{ site.title }}</h1>
          <span v-if="site.description" class="blog-description">
            {{ site.description }}
          </span>
        </div>
        <div v-if="site.cover_image && !coverLoadFailed" class="blog-cover cover">
          <img
            :src="site.cover_image"
            :alt="site.title"
            @error="handleCoverError"
          />
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
        <PostList
          v-else
          :posts="posts"
          :pagination="pagination"
          @page-change="handlePageChange"
        />
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { searchArticles } from '@/api/article'
import type { Pagination, Post, ArticlePageQueryRespDTO } from '@/types/blog'
import PostList from '@/components/PostList.vue'

const route = useRoute()
const router = useRouter()

const site = ref({
  title: '我的博客',
  description: '个人博客网站',
  cover_image: '',
})

const posts = ref<Post[]>([])
const pagination = ref<Pagination>({
  current: 1,
  size: 10,
  pages: 1,
  total: 0,
})

const loading = ref(false)
const coverLoadFailed = ref(false)

const hasCoverImage = computed(() => {
  return site.value.cover_image && !coverLoadFailed.value
})

const handleCoverError = () => {
  coverLoadFailed.value = true
}

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

const fetchPosts = async (page = 1) => {
  try {
    loading.value = true
    const response = await searchArticles({ page, size: 10 })
    if (response && response.data) {
      posts.value = (response.data.records || []).map(mapArticleToPost)
      pagination.value.current = response.data.current || 1
      pagination.value.size = response.data.size || 10
      pagination.value.total = response.data.total || 0
      pagination.value.pages = response.data.pages || 1
    }
  } catch (error) {
    console.error('获取文章列表失败:', error)
  } finally {
    loading.value = false
  }
}

const handlePageChange = (page: number) => {
  if (page === 1) {
    router.push('/')
  } else {
    router.push(`/page/${page}`)
  }
  fetchPosts(page)
}

watch(
  () => route.params.page,
  (newPage) => {
    const page = newPage ? Number(newPage) : 1
    fetchPosts(page)
  }
)

onMounted(async () => {
  const pageParam = route.params.page
  const page = pageParam ? Number(pageParam) : 1
  await fetchPosts(page)
})
</script>

<style scoped>
.blog-header {
  background: var(--color-background-secondary);
}

.blog-header.has-cover {
  background: #000;
}

.blog-header:not(.has-cover) .blog-name {
  color: var(--color-content-lead);
}

.blog-header:not(.has-cover) .blog-description {
  color: var(--color-content-secondary);
}

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
