<script setup lang="ts">
import { ref, onMounted, watch, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useHead } from '@unhead/vue'
import { ArrowLeft, Calendar, Eye, Clock, Tag } from 'lucide-vue-next'
import { getArticleById } from '@/api/article'
import { formatDate, calculateReadingTime } from '@/utils/date'
import { useScrollProgress } from '@/composables/useScrollProgress'
import ArticleContent from '@/components/article/ArticleContent.vue'
import ArticleToc from '@/components/article/ArticleToc.vue'
import ArtalkComment from '@/components/comment/ArtalkComment.vue'
import type { Article } from '@/types'
import type { TocItem } from '@/composables/useMarkdown'

const route = useRoute()
const router = useRouter()
const { progress } = useScrollProgress()

const article = ref<Article | null>(null)
const loading = ref(false)
const error = ref(false)
const tocItems = ref<TocItem[]>([])

const readingTime = computed(() =>
  article.value ? calculateReadingTime(article.value.content || article.value.summary || '') : 0,
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

async function fetchArticle(id: string) {
  loading.value = true
  error.value = false
  try {
    article.value = await getArticleById(id)
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
      class="fixed top-0 left-0 h-[2px] bg-[var(--color-accent)] dark:bg-[var(--color-dark-accent)] z-50 transition-[width] duration-150"
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
      <p class="text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)] mb-4">文章加载失败</p>
      <button
        @click="router.push('/')"
        class="text-sm text-[var(--color-accent)] dark:text-[var(--color-dark-accent)] hover:opacity-70 transition-opacity"
      >
        返回首页
      </button>
    </div>

    <template v-else-if="article">
      <div class="flex gap-12">
        <article class="flex-1 min-w-0 max-w-[720px] mx-auto">
          <header class="mb-10">
            <button
              @click="router.back()"
              class="inline-flex items-center gap-1 text-sm text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)] hover:text-[var(--color-text-heading)] dark:hover:text-[var(--color-dark-text-heading)] transition-colors mb-6"
            >
              <ArrowLeft :size="14" />
              返回
            </button>

            <h1 class="text-3xl sm:text-4xl font-bold text-[var(--color-text-heading)] dark:text-[var(--color-dark-text-heading)] leading-tight tracking-tight mb-4">
              {{ article.title }}
            </h1>

            <div class="flex flex-wrap items-center gap-x-4 gap-y-2 text-sm text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)]">
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
                class="inline-flex items-center gap-1.5 hover:text-[var(--color-text-heading)] dark:hover:text-[var(--color-dark-text-heading)] transition-colors"
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
                class="text-xs px-2.5 py-1 rounded-full border border-[var(--color-border)] dark:border-[var(--color-dark-border)] text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)] hover:text-[var(--color-text-heading)] dark:hover:text-[var(--color-dark-text-heading)] hover:border-[var(--color-text-heading)] dark:hover:border-[var(--color-dark-text-heading)] transition-colors"
              >
                #{{ tag.name }}
              </RouterLink>
            </div>
          </header>

          <ArticleContent :content="article.content" @toc-ready="handleTocReady" />

          <ArtalkComment
            :page-key="`/post/${article.id}`"
            :page-title="article.title"
          />
        </article>

        <aside class="hidden lg:block w-56 shrink-0">
          <div class="sticky top-24">
            <ArticleToc :items="tocItems" />
          </div>
        </aside>
      </div>
    </template>
  </div>
</template>
