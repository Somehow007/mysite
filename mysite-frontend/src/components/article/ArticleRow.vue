<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { Lock } from 'lucide-vue-next'
import OptimizedImage from '@/components/common/OptimizedImage.vue'
import FavoriteButton from './FavoriteButton.vue'
import { formatChineseDate, calculateReadingTime } from '@/utils/date'
import { formatViewCount } from '@/utils/format'
import type { ArticleListItem } from '@/types'

const props = defineProps<{
  article: ArticleListItem
  showFavorite?: boolean
  priority?: boolean
}>()

const emit = defineEmits<{
  'favorite-toggle': [articleId: string, favorited: boolean]
}>()

const router = useRouter()

const readingTime = computed(() =>
  props.article.readingTime || calculateReadingTime(props.article.summary || '')
)

// kicker：合集名（若有）+ 分类名；无分类兜底「未分类」
const kicker = computed(() => {
  const parts: string[] = []
  if (props.article.collectionTitle) parts.push(props.article.collectionTitle)
  parts.push(props.article.categoryName || '未分类')
  return parts.join(' · ')
})

function handleFavoriteToggle(favorited: boolean) {
  emit('favorite-toggle', props.article.id, favorited)
}

function handleLoginRequired() {
  router.push('/login')
}
</script>

<template>
  <article class="group border-b border-border py-5 lg:py-6">
    <div class="flex gap-4 lg:gap-6">
      <div class="min-w-0 flex-1">
        <RouterLink :to="`/post/${article.id}`" class="block">
          <div class="text-[12px] font-bold uppercase tracking-[0.14em] text-accent">
            {{ kicker }}
          </div>
          <h3
            class="mt-1.5 font-serif text-lg font-bold leading-snug text-text-primary transition-colors duration-200 group-hover:text-accent lg:text-[22px] lg:leading-[1.4]"
          >
            {{ article.title }}
          </h3>
          <p
            v-if="article.summary"
            class="mt-1.5 text-sm leading-relaxed text-text-secondary max-lg:hidden lg:line-clamp-2"
          >
            {{ article.summary }}
          </p>
        </RouterLink>

        <div class="mt-2.5 flex flex-wrap items-center gap-x-2 gap-y-1 text-xs text-text-muted">
          <span>{{ formatChineseDate(article.updateTime) }}</span>
          <span aria-hidden="true" class="text-text-muted/50">·</span>
          <span>{{ readingTime }} 分钟</span>
          <template v-if="article.viewCount > 0">
            <span aria-hidden="true" class="text-text-muted/50">·</span>
            <span>{{ formatViewCount(article.viewCount) }} 阅读</span>
          </template>
          <span
            v-if="article.visibility === 1"
            class="inline-flex items-center gap-1 text-accent"
            title="仅自己可见"
          >
            <Lock :size="11" />
            <span class="hidden sm:inline">仅自己可见</span>
          </span>
          <FavoriteButton
            v-if="showFavorite"
            :article-id="article.id"
            :initial-favorited="article.isFavorited"
            :favorite-count="article.favoriteCount"
            size="sm"
            class="ml-auto"
            @toggle="handleFavoriteToggle"
            @login-required="handleLoginRequired"
          />
        </div>
      </div>

      <RouterLink
        v-if="article.coverImage"
        :to="`/post/${article.id}`"
        class="shrink-0"
        :aria-label="article.title"
      >
        <div class="aspect-[4/3] w-24 overflow-hidden border border-border lg:w-[168px]">
          <OptimizedImage
            :src="article.coverImage"
            :alt="article.title"
            :fetch-priority="priority ? 'high' : 'auto'"
            :lazy="!priority"
            class="h-full w-full"
          />
        </div>
      </RouterLink>
    </div>
  </article>
</template>
