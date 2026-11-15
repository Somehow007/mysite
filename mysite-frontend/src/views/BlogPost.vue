<script setup lang="ts">
import { ref, onMounted, watch, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useHead } from '@unhead/vue'
import { Lock } from 'lucide-vue-next'
import { getArticleById, getArticles } from '@/api/article'
import { getArticleNavigation } from '@/api/collection'
import { calculateReadingTime } from '@/utils/date'
import { useScrollProgress } from '@/composables/useScrollProgress'
import { useFavorite } from '@/composables/useFavorite'
import { useChatLauncher } from '@/composables/useChatLauncher'
import { takePendingAsk, hasAuthSession } from '@/composables/usePendingAsk'
import { useSiteStore } from '@/stores/site'
import ArticleContent from '@/components/article/ArticleContent.vue'
import ArticleToc from '@/components/article/ArticleToc.vue'
import MobileTocDrawer from '@/components/article/MobileTocDrawer.vue'
import PostHeader from '@/components/article/PostHeader.vue'
import FavoriteButton from '@/components/article/FavoriteButton.vue'
import ShareButton from '@/components/article/ShareButton.vue'
import AskArticleCard from '@/components/article/AskArticleCard.vue'
import PostRelated from '@/components/article/PostRelated.vue'
import type { RelatedItem } from '@/components/article/PostRelated.vue'
import CommentSection from '@/components/comment/CommentSection.vue'
import ArticleNav from '@/components/collection/ArticleNav.vue'
import OptimizedImage from '@/components/common/OptimizedImage.vue'
import type { Article, ArticleNavInfo } from '@/types'
import type { TocItem } from '@/composables/useMarkdown'

const route = useRoute()
const router = useRouter()
const { progress } = useScrollProgress()
const { setFavoriteStatus } = useFavorite()
const launcher = useChatLauncher()
const siteStore = useSiteStore()

const article = ref<Article | null>(null)
const loading = ref(false)
const error = ref(false)
const tocItems = ref<TocItem[]>([])
const navInfo = ref<ArticleNavInfo | null>(null)
const relatedItems = ref<RelatedItem[]>([])

const readingTime = computed(() =>
  article.value?.readingTime || calculateReadingTime(article.value?.content || article.value?.summary || ''),
)

const readPct = computed(() => Math.round(progress.value * 100))
const remainingMinutes = computed(() =>
  Math.max(1, Math.ceil(readingTime.value * (1 - progress.value))),
)

// 合集文章或有 prev/next 时才渲染连续阅读条，避免空壳
const showArticleNav = computed(() => {
  const nav = navInfo.value
  return !!nav && (
    nav.prev != null ||
    nav.next != null ||
    (nav.inCollection && !!nav.collectionTitle)
  )
})

useHead(() => ({
  title: article.value ? article.value.title : '加载中...',
  meta: article.value
    ? [
        { name: 'description', content: article.value.summary || '' },
        { property: 'og:title', content: article.value.title },
        { property: 'og:description', content: article.value.summary || '' },
        { property: 'og:type', content: 'article' },
        ...(article.value.coverImage ? [{ property: 'og:image', content: article.value.coverImage }] : []),
        ...(article.value.createTime
          ? [{ property: 'article:published_time', content: article.value.createTime }]
          : []),
        { property: 'article:modified_time', content: article.value.updateTime },
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
  router.push({ name: 'login', query: { redirect: route.fullPath } })
}

// 「继续读」：合集内优先取本篇之后的文章（循环），不足 3 篇用最新文章补齐
async function loadRelated() {
  const current = article.value
  if (!current) return
  const items: RelatedItem[] = []
  const seen = new Set<string>([current.id])
  const nav = navInfo.value

  const siblings = nav?.collectionArticles ?? []
  if (nav?.inCollection && siblings.length > 1) {
    const idx = siblings.findIndex(a => a.id === current.id)
    const ordered = idx >= 0
      ? [...siblings.slice(idx + 1), ...siblings.slice(0, idx)]
      : siblings
    for (const s of ordered) {
      if (items.length >= 3) break
      if (seen.has(s.id)) continue
      seen.add(s.id)
      items.push({ id: s.id, title: s.title, kicker: nav.collectionTitle || '合集' })
    }
  }

  if (items.length < 3) {
    try {
      const res = await getArticles({ size: 8 })
      for (const a of res.list) {
        if (items.length >= 3) break
        if (seen.has(a.id)) continue
        seen.add(a.id)
        items.push({ id: a.id, title: a.title, kicker: a.categoryName || '文章' })
      }
    } catch {
      // 「继续读」是增强项，失败静默
    }
  }
  relatedItems.value = items
}

async function fetchArticle(id: string) {
  loading.value = true
  error.value = false
  relatedItems.value = []
  try {
    const [articleData, navData] = await Promise.all([
      getArticleById(id),
      getArticleNavigation(id).catch(() => null),
    ])
    article.value = articleData
    navInfo.value = navData
    if (article.value && article.value.isFavorited !== undefined) {
      setFavoriteStatus(id, article.value.isFavorited)
    }
    void loadRelated()
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
  // 未登录提问回跳恢复：暂存的问题在登录后自动打开面板并发送
  const pending = takePendingAsk(route.fullPath)
  if (pending && hasAuthSession()) {
    launcher.open({ question: pending, send: true })
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

    <div v-if="loading" class="animate-pulse">
      <div class="max-w-[820px] space-y-4">
        <div class="skeleton h-3 w-32 rounded" />
        <div class="skeleton h-12 w-4/5 rounded" />
        <div class="skeleton h-5 w-full rounded" />
        <div class="skeleton h-5 w-3/5 rounded" />
        <div class="mt-7 flex items-center gap-3.5">
          <div class="skeleton h-9 w-9 rounded-full" />
          <div class="space-y-2">
            <div class="skeleton h-3.5 w-32 rounded" />
            <div class="skeleton h-3 w-52 rounded" />
          </div>
        </div>
      </div>
      <div class="skeleton mt-6 aspect-[16/10] w-full lg:mt-9 lg:aspect-[21/9]" />
      <div class="mt-10 max-w-[700px] space-y-4">
        <div class="skeleton h-4 w-full rounded" />
        <div class="skeleton h-4 w-5/6 rounded" />
        <div class="skeleton h-4 w-4/5 rounded" />
        <div class="skeleton h-40 w-full rounded-lg" />
        <div class="skeleton h-4 w-full rounded" />
        <div class="skeleton h-4 w-3/4 rounded" />
      </div>
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
      <PostHeader
        :article="article"
        :nav-info="navInfo"
        :reading-time="readingTime"
        @toggle="handleFavoriteToggle"
        @login-required="handleLoginRequired"
      />

      <figure v-if="article.coverImage" class="post-cover">
        <OptimizedImage
          :src="article.coverImage"
          :alt="article.title"
          fetch-priority="high"
          :lazy="false"
          class="h-full w-full"
        />
      </figure>

      <div class="post-layout">
        <article class="min-w-0">
          <div v-if="article.visibility === 1" class="post-private-note">
            <Lock :size="14" />
            <span>这篇文章仅自己可见</span>
          </div>

          <ArticleContent :content="article.content" @toc-ready="handleTocReady" />

          <div class="post-end">
            <RouterLink
              v-for="tag in article.tags ?? []"
              :key="tag.id"
              :to="`/tag/${tag.slug}`"
              class="post-tag"
            >
              #{{ tag.name }}
            </RouterLink>
            <div class="post-end-acts">
              <FavoriteButton
                :article-id="article.id"
                :initial-favorited="article.isFavorited"
                :favorite-count="article.favoriteCount"
                variant="bar"
                show-count
                @toggle="handleFavoriteToggle"
                @login-required="handleLoginRequired"
              />
              <ShareButton :title="article.title" />
            </div>
          </div>

          <ArticleNav
            v-if="showArticleNav && navInfo"
            :nav-info="navInfo"
            :article-id="article.id"
          />

          <PostRelated :items="relatedItems" />

          <CommentSection
            v-if="siteStore.site.commentEnabled !== false"
            :article-id="article.id"
          />
        </article>

        <aside class="post-rail">
          <div class="post-toc-wrap">
            <ArticleToc :items="tocItems" />
            <div v-if="tocItems.length" class="post-rail-pct">
              已读 {{ readPct }}% · 剩约 {{ remainingMinutes }} 分钟
            </div>
          </div>
          <AskArticleCard />
        </aside>
      </div>

      <MobileTocDrawer :items="tocItems" />
    </template>
  </div>
</template>
