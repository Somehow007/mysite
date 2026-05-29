<script setup lang="ts">
import { useRouter } from 'vue-router'
import ArticleMeta from './ArticleMeta.vue'
import FavoriteButton from './FavoriteButton.vue'
import OptimizedImage from '@/components/common/OptimizedImage.vue'
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

function handleFavoriteToggle(favorited: boolean) {
  emit('favorite-toggle', props.article.id, favorited)
}

function handleLoginRequired() {
  router.push('/login')
}
</script>

<template>
  <article class="group relative">
    <div class="flex items-start justify-between gap-3">
      <RouterLink :to="`/post/${article.id}`" class="flex-1 min-w-0">
        <div
          v-if="article.coverImage"
          class="aspect-[2/1] rounded-xl overflow-hidden mb-4"
        >
          <OptimizedImage
            :src="article.coverImage"
            :alt="article.title"
            :fetch-priority="priority ? 'high' : 'auto'"
            :lazy="!priority"
            class="w-full h-full transition-transform duration-500 group-hover:scale-[1.03]"
          />
        </div>

        <h2 class="text-xl font-semibold text-[var(--color-text-heading)] dark:text-[var(--color-dark-text-heading)] group-hover:text-[var(--color-accent)] dark:group-hover:text-[var(--color-dark-accent)] transition-colors duration-200 mb-2 leading-snug">
          {{ article.title }}
        </h2>

        <p
          v-if="article.summary"
          class="text-sm text-[var(--color-text-body)] dark:text-[var(--color-dark-text-body)] line-clamp-2 mb-3 leading-relaxed"
        >
          {{ article.summary }}
        </p>
      </RouterLink>

      <FavoriteButton
        v-if="showFavorite"
        :article-id="article.id"
        :initial-favorited="article.isFavorited"
        :favorite-count="article.favoriteCount"
        size="sm"
        @toggle="handleFavoriteToggle"
        @login-required="handleLoginRequired"
      />
    </div>

    <ArticleMeta :article="article" show-category show-favorite />
  </article>
</template>
