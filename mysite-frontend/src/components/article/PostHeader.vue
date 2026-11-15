<script setup lang="ts">
import { computed } from 'vue'
import { toRomanNumeral, formatViewCount } from '@/utils/format'
import { formatChineseDate } from '@/utils/date'
import FavoriteButton from '@/components/article/FavoriteButton.vue'
import ShareButton from '@/components/article/ShareButton.vue'
import type { Article, ArticleNavInfo } from '@/types'

const props = defineProps<{
  article: Article
  navInfo: ArticleNavInfo | null
  readingTime: number
}>()

const emit = defineEmits<{
  toggle: [favorited: boolean]
  'login-required': []
}>()

// 合集徽标：navInfo.collectionArticles 给出可见文章序列，indexOf 出「第 N / M 篇」
const series = computed(() => {
  const nav = props.navInfo
  if (!nav?.inCollection || !nav.collectionId || !nav.collectionTitle) return null
  const list = nav.collectionArticles ?? []
  const idx = list.findIndex(a => a.id === props.article.id)
  return {
    collectionId: nav.collectionId,
    title: nav.collectionTitle,
    index: idx >= 0 ? idx + 1 : null,
    total: list.length || null,
    numeral: idx >= 0 ? toRomanNumeral(idx + 1) : null,
  }
})

const publishDate = computed(() =>
  formatChineseDate(props.article.createTime || props.article.updateTime, true),
)

const authorInitial = computed(() => props.article.authorName?.charAt(0) || '·')
</script>

<template>
  <div>
    <nav class="post-crumb" aria-label="面包屑">
      <RouterLink to="/">首页</RouterLink>
      <span aria-hidden="true">/</span>
      <RouterLink v-if="article.categorySlug" :to="`/category/${article.categorySlug}`">
        {{ article.categoryName || '未分类' }}
      </RouterLink>
      <span v-else>{{ article.categoryName || '未分类' }}</span>
    </nav>

    <header class="post-head">
      <div class="post-kicker-row">
        <span class="post-kicker">{{ article.categoryName || '未分类' }}</span>
        <RouterLink
          v-if="series"
          :to="`/collection/${series.collectionId}`"
          class="series-tag"
        >
          <b v-if="series.numeral">{{ series.numeral }}</b>
          {{ series.title }}<template v-if="series.index && series.total"> · 第 {{ series.index }} / {{ series.total }} 篇</template>
        </RouterLink>
      </div>

      <h1 class="post-title">{{ article.title }}</h1>
      <p v-if="article.summary" class="post-dek">{{ article.summary }}</p>

      <div class="post-by">
        <div class="post-by-avatar" aria-hidden="true">{{ authorInitial }}</div>
        <div class="post-by-who min-w-0">
          <RouterLink
            v-if="article.authorName"
            :to="`/user/${article.authorName}`"
            class="hover:text-accent transition-colors"
          >
            <b>{{ article.authorName }}</b>
          </RouterLink>
          <b v-else>匿名</b>
          <span class="post-by-meta">
            {{ publishDate }} · {{ readingTime }} 分钟<template v-if="article.viewCount > 0"> · {{ formatViewCount(article.viewCount) }} 阅读</template>
          </span>
        </div>
        <div class="post-by-acts">
          <FavoriteButton
            :article-id="article.id"
            :initial-favorited="article.isFavorited"
            :favorite-count="article.favoriteCount"
            variant="bar"
            show-count
            @toggle="emit('toggle', $event)"
            @login-required="emit('login-required')"
          />
          <ShareButton :title="article.title" />
        </div>
      </div>
    </header>
  </div>
</template>
