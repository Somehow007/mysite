<template>
  <div class="author-page">
    <header class="blog-header" :class="{ 'has-cover': false }">
      <div class="inner">
        <a class="back-link" href="#" aria-label="返回" @click.prevent="goBack">
          <i class="icon icon-arrow-left">
            <IconArrowLeft />
          </i>
          <span>返回</span>
        </a>
        <div class="archive archive-author box archive-box">
          <span class="archive-info">
            <span class="archive-type">作者</span>
            <span class="archive-count">
              {{ pagination.total === 0 ? '无文章' : `${pagination.total} 篇文章` }}
            </span>
          </span>
          <figure class="archive-avatar avatar">
            <span class="avatar-placeholder">{{ (user?.username || user?.realName || '用户').charAt(0).toUpperCase() }}</span>
          </figure>

          <h2 class="archive-title">{{ user?.username || user?.realName || '用户' }}</h2>
          <span v-if="user" class="archive-stats">
            <span class="stat">{{ user.followingCount || 0 }} 关注</span>
            <span class="stat">{{ user.followerCount || 0 }} 粉丝</span>
          </span>
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
import { useRoute, useRouter } from 'vue-router'
import { getUserById } from '@/api/user'
import { searchArticles } from '@/api/article'
import type { User, Post, Pagination, ArticlePageQueryRespDTO } from '@/types/blog'
import PostList from '@/components/PostList.vue'
import IconArrowLeft from '@/components/icons/IconArrowLeft.vue'

const route = useRoute()
const router = useRouter()

const user = ref<User | null>(null)
const posts = ref<Post[]>([])
const pagination = ref<Pagination>({
  current: 1,
  size: 10,
  pages: 1,
  total: 0,
})
const loading = ref(false)

const goBack = () => {
  if (typeof window !== 'undefined' && window.history.length > 1) {
    router.back()
  } else {
    router.push('/')
  }
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

const fetchUser = async (id: string) => {
  try {
    const response = await getUserById(id)
    if (response && response.data) {
      user.value = {
        id: response.data.id,
        username: response.data.username,
        realName: response.data.realName,
        sex: response.data.sex,
        email: response.data.email,
        phoneNumber: response.data.phoneNumber,
        followingCount: response.data.followingCount || 0,
        followerCount: response.data.followerCount || 0,
      }
    }
  } catch (error) {
    console.error('获取用户信息失败:', error)
  }
}

const fetchUserPosts = async (page = 1) => {
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
  fetchUserPosts(page)
}

onMounted(async () => {
  const id = route.params.slug as string
  if (id) {
    await fetchUser(id)
    await fetchUserPosts()
  }
})
</script>

<style scoped>
.back-link {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  margin-bottom: 1rem;
  color: var(--color-content-secondary);
  text-decoration: none;
  font-size: 1.4rem;
  transition: color 0.15s ease;
}
.back-link:hover {
  color: var(--ghost-accent-color);
}
.back-link .icon {
  display: inline-flex;
}

.avatar-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  background: var(--ghost-accent-color);
  color: #fff;
  font-weight: 600;
  font-size: 2rem;
}

.archive-stats {
  display: flex;
  gap: 1.5rem;
  margin-top: 1rem;
  color: var(--color-content-secondary);
  font-size: 1.4rem;
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
