<script setup lang="ts">
import { ref, onMounted, watch, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useHead } from '@unhead/vue'
import { ArrowLeft, Calendar, Eye, Clock, Tag } from 'lucide-vue-next'
import { getArticleById } from '@/api/article'
import { formatDate, calculateReadingTime } from '@/utils/date'
import { useScrollProgress } from '@/composables/useScrollProgress'
import { useFavorite } from '@/composables/useFavorite'
import ArticleContent from '@/components/article/ArticleContent.vue'
import ArticleToc from '@/components/article/ArticleToc.vue'
import FavoriteButton from '@/components/article/FavoriteButton.vue'
import CommentSection from '@/components/comment/CommentSection.vue'
import type { Article } from '@/types'
import type { TocItem } from '@/composables/useMarkdown'

const route = useRoute()
const router = useRouter()
const { progress } = useScrollProgress()
const { setFavoriteStatus } = useFavorite()

const article = ref<Article | null>(null)
const loading = ref(false)
const error = ref(false)
const tocItems = ref<TocItem[]>([])

const readingTime = computed(() =>
  article.value?.readingTime || calculateReadingTime(article.value?.content || article.value?.summary || ''),
)

useHead(() => ({
  title: article.value ? `${article.value.title} - MySite` : '加载中...',
  meta: article.value
    ? [
        { name: 'description', content: article.value.summary || '' },
        { property: 'og:title', content: article.value.title },
        { property: 'og:description', content: article.value.summary || '' },
        { property: 'og:type', content: 'article' },
        ...(article.value.coverImage ? [{ property: 'og:image', content: article.value.coverImage }] : []),
      ]
    : [],
}))

function handleTocReady(items: TocItem[]) {
  tocItems.value = items
}

function handleFavoriteToggle(favorited: boolean) {
  if (article.value) {
    article.value.isFavorited = favorited
    article.value.favoriteCount += favorited ? 1 : -1
  }
}

function handleLoginRequired() {
  router.push('/login')
}

async function fetchArticle(id: string) {
  loading.value = true
  error.value = false
  try {
    article.value = await getArticleById(id)
    if (article.value && article.value.isFavorited !== undefined) {
      setFavoriteStatus(id, article.value.isFavorited)
    }
  } catch {
    error.value = true
    article.value = null
  } finally {
    loading.value = false
  }
}

watch(
  () => route.params.id,
  (newId) => {
    if (newId) fetchArticle(newId as string)
  },
)

onMounted(() => {
  if (route.params.id) {
    fetchArticle(route.params.id as string)
  }
})
</script>

<template>
  <div class="relative">
    <div
      v-if="progress > 0"
      class="fixed top-0 left-0 h-[2px] bg-accent z-50 transition-[width] duration-150"
      :style="{ width: `${progress * 100}%` }"
    />

    <div v-if="loading" class="animate-pulse space-y-6">
      <div class="skeleton h-8 w-3/4 rounded" />
      <div class="flex gap-4">
        <div class="skeleton h-4 w-24 rounded" />
        <div class="skeleton h-4 w-20 rounded" />
        <div class="skeleton h-4 w-16 rounded" />
      </div>
      <div class="skeleton h-4 w-full rounded" />
      <div class="skeleton h-4 w-5/6 rounded" />
      <div class="skeleton h-4 w-4/5 rounded" />
      <div class="skeleton h-64 w-full rounded-lg" />
      <div class="skeleton h-4 w-full rounded" />
      <div class="skeleton h-4 w-3/4 rounded" />
    </div>

    <div v-else-if="error" class="py-16 text-center">
      <p class="text-text-muted mb-4">文章加载失败</p>
      <button
        @click="router.push('/')"
        class="btn-primary"
      >
        返回首页
      </button>
    </div>

    <template v-else-if="article">
      <div class="flex gap-12 items-start">
        <article class="flex-1 min-w-0 max-w-[720px] mx-auto">
          <header class="mb-10">
            <button
              @click="router.back()"
              class="inline-flex items-center gap-1.5 text-sm text-text-muted hover:text-accent transition-colors duration-200 mb-6 group"
            >
              <ArrowLeft :size="14" class="group-hover:-translate-x-0.5 transition-transform duration-200" />
              返回
            </button>

            <div class="flex items-start justify-between gap-4 mb-4">
              <h1 class="text-3xl sm:text-4xl font-bold text-text-primary leading-tight tracking-tight">
                {{ article.title }}
              </h1>
              <FavoriteButton
                :article-id="article.id"
                :initial-favorited="article.isFavorited"
                :favorite-count="article.favoriteCount"
                size="lg"
                show-count
                @toggle="handleFavoriteToggle"
                @login-required="handleLoginRequired"
              />
            </div>

            <div class="flex flex-wrap items-center gap-x-4 gap-y-2 text-sm text-text-muted">
              <span class="inline-flex items-center gap-1.5">
                <Calendar :size="14" />
                <time :datetime="article.updateTime">{{ formatDate(article.updateTime) }}</time>
              </span>
              <span v-if="article.viewCount > 0" class="inline-flex items-center gap-1.5">
                <Eye :size="14" />
                <span>{{ article.viewCount }} 阅读</span>
              </span>
              <span class="inline-flex items-center gap-1.5">
                <Clock :size="14" />
                <span>{{ readingTime }} 分钟</span>
              </span>
              <RouterLink
                v-if="article.categorySlug"
                :to="`/category/${article.categorySlug}`"
                class="inline-flex items-center gap-1.5 hover:text-accent transition-colors duration-200"
              >
                <Tag :size="14" />
                <span>{{ article.categoryName }}</span>
              </RouterLink>
            </div>

            <div v-if="article.tags && article.tags.length > 0" class="flex flex-wrap gap-2 mt-3">
              <RouterLink
                v-for="tag in article.tags"
                :key="tag.id"
                :to="`/tag/${tag.slug}`"
                class="text-xs px-2.5 py-1 rounded-full border border-border text-text-muted hover:text-accent hover:border-accent hover:bg-accent-subtle transition-all duration-200"
              >
                #{{ tag.name }}
              </RouterLink>
            </div>
          </header>

          <ArticleContent :content="article.content" @toc-ready="handleTocReady" />

          <div class="mt-10 pt-6 border-t border-border flex items-center justify-center gap-3">
            <span class="text-sm text-text-muted">觉得有用？收藏起来吧</span>
            <FavoriteButton
              :article-id="article.id"
              :initial-favorited="article.isFavorited"
              :favorite-count="article.favoriteCount"
              size="md"
              show-count
              @toggle="handleFavoriteToggle"
              @login-required="handleLoginRequired"
            />
          </div>

          <CommentSection
            :article-id="article.id"
          />
        </article>

        <aside class="hidden lg:block w-56 shrink-0 sticky top-20 h-[calc(100vh-6rem)]">
          <ArticleToc :items="tocItems" />
        </aside>
      </div>
    </template>
  </div>
</template>
