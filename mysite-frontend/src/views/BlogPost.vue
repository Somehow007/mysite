<template>
  <div class="blog-post">
    <div class="progress-container">
      <span class="progress-bar" :style="{ width: `${progress}%` }"></span>
    </div>

    <template v-if="post">
      <header class="post-header" :class="{ 'has-cover': post.feature_image }">
        <div class="inner">
          <a class="back-link" href="#" aria-label="返回" @click.prevent="goBack">
            <i class="icon icon-arrow-left">
              <IconArrowLeft />
            </i>
            <span>返回</span>
          </a>
          <span class="post-info">
            <span class="post-type">文章</span>
          </span>
          <h1 class="post-title">{{ post.title }}</h1>
          <div class="post-meta">
            <div class="post-meta-avatars">
              <figure class="post-meta-avatar avatar"></figure>
            </div>
            <h4 class="post-meta-author">
              <router-link :to="`/author/${post.authorId}`">{{ post.authorName || '匿名' }}</router-link>
            </h4>
            <time v-if="post.createTime" :datetime="formatDateISO(post.createTime)">
              {{ formatDate(post.createTime) }}
            </time>
            &bull; {{ post.reading_time }}
          </div>
          <div v-if="post.feature_image" class="post-cover cover">
            <img :src="post.feature_image" :alt="post.title" />
          </div>
        </div>
      </header>

      <main class="content" role="main">
        <article class="post">
          <div class="inner">
            <section class="post-content" v-html="post.content"></section>

            <section class="post-footer">
              <div class="post-share">
                <span class="post-info-label">分享</span>
                <a title="Twitter" aria-label="Twitter" class="twitter" :href="getTwitterShareUrl()" @click.prevent="shareToTwitter">
                  <i class="icon icon-twitter"><IconTwitter /></i>
                </a>
                <a title="Facebook" aria-label="Facebook" class="facebook" :href="getFacebookShareUrl()" @click.prevent="shareToFacebook">
                  <i class="icon icon-facebook"><IconFacebook /></i>
                </a>
                <a title="LinkedIn" aria-label="LinkedIn" class="linkedin" :href="getLinkedInShareUrl()" @click.prevent="shareToLinkedIn">
                  <i class="icon icon-linkedin"><IconLinkedin /></i>
                </a>
                <a title="Email" aria-label="Email" class="email" :href="getEmailShareUrl()">
                  <i class="icon icon-mail"><IconMail /></i>
                </a>
              </div>

              <div class="post-stats">
                <span class="stat-item">
                  <i class="icon"><IconEye /></i>
                  {{ post.viewCount }} 次阅读
                </span>
                <span class="stat-item">
                  <i class="icon"><IconStar /></i>
                  {{ post.favoriteCount }} 次收藏
                </span>
              </div>
            </section>

            <section class="post-comments">
              <h3 class="comments-title">评论</h3>
              <ArtalkComment
                v-if="post.id"
                :page-key="`/post/${post.id}`"
                :page-title="post.title"
              />
            </section>
          </div>
        </article>
      </main>
    </template>
    <div v-else-if="loading" class="inner" style="padding: 4rem 0; text-align: center;">
      加载中...
    </div>
    <div v-else class="inner" style="padding: 4rem 0; text-align: center;">
      文章不存在或已被删除。
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useReadingProgress } from '@/composables/useReadingProgress'
import { formatDate, formatDateISO } from '@/utils/date'
import { initCodeHighlight } from '@/utils/codeHighlight'
import { initResponsiveVideos } from '@/utils/reframe'
import { getArticleById } from '@/api/article'
import type { Post, ArticleSelectRespDTO } from '@/types/blog'
import IconTwitter from '@/components/icons/IconTwitter.vue'
import IconFacebook from '@/components/icons/IconFacebook.vue'
import IconLinkedin from '@/components/icons/IconLinkedin.vue'
import IconMail from '@/components/icons/IconMail.vue'
import IconArrowLeft from '@/components/icons/IconArrowLeft.vue'
import IconStar from '@/components/icons/IconStar.vue'
import IconEye from '@/components/icons/IconEye.vue'
import ArtalkComment from '@/components/ArtalkComment.vue'

const route = useRoute()
const router = useRouter()
const { progress } = useReadingProgress()

const post = ref<Post | null>(null)
const loading = ref(false)

const goBack = () => {
  if (typeof window !== 'undefined' && window.history.length > 1) {
    router.back()
  } else {
    router.push('/')
  }
}

const getPostUrl = (): string => {
  return typeof window !== 'undefined' ? window.location.href : ''
}

const getTwitterShareUrl = (): string => {
  return `https://twitter.com/share?text=${encodeURIComponent(post.value?.title ?? '')}&url=${encodeURIComponent(getPostUrl())}`
}

const getFacebookShareUrl = (): string => {
  return `https://www.facebook.com/sharer/sharer.php?u=${encodeURIComponent(getPostUrl())}`
}

const getLinkedInShareUrl = (): string => {
  return `https://www.linkedin.com/shareArticle?mini=true&url=${encodeURIComponent(getPostUrl())}&title=${encodeURIComponent(post.value?.title ?? '')}`
}

const getEmailShareUrl = (): string => {
  return `mailto:?subject=${encodeURIComponent(post.value?.title ?? '')}&body=${encodeURIComponent(getPostUrl())}`
}

const shareToTwitter = () => {
  window.open(getTwitterShareUrl(), 'twitter-share', 'width=550,height=235')
}

const shareToFacebook = () => {
  window.open(getFacebookShareUrl(), 'facebook-share', 'width=580,height=296')
}

const shareToLinkedIn = () => {
  window.open(getLinkedInShareUrl(), 'linkedin-share', 'width=930,height=720')
}

const mapArticleToPost = (article: ArticleSelectRespDTO): Post => {
  return {
    id: String(article.id),
    title: article.title,
    summary: article.summary,
    content: article.content,
    featured: false,
    createTime: article.createTime,
    updateTime: article.updateTime,
    reading_time: '1 min read',
    viewCount: article.viewCount,
    favoriteCount: article.favoriteCount,
    authorName: '',
    authorId: String(article.authorId),
    tags: [],
    url: `/post/${article.id}`,
    authors: [],
    published_at: article.createTime,
    excerpt: article.summary,
  }
}

const fetchPost = async (id: string) => {
  try {
    loading.value = true
    const response = await getArticleById(id)
    if (response && response.data) {
      post.value = mapArticleToPost(response.data)
    }
  } catch (error) {
    console.error('获取文章详情失败:', error)
    post.value = null
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  const id = route.params.id as string
  if (id) {
    await fetchPost(id)
  }

  setTimeout(() => {
    initResponsiveVideos()
    initCodeHighlight()
  }, 300)
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

.comments-title {
  font-size: 1.8rem;
  margin-bottom: 2rem;
  color: var(--color-content-lead);
}

.post-stats {
  display: flex;
  gap: 2rem;
  margin-top: 1rem;
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  color: var(--color-content-secondary);
  font-size: 1.4rem;
}

.loading {
  text-align: center;
  padding: 4rem;
  color: var(--color-content-secondary);
}
</style>
