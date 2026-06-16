<script setup lang="ts">
import { useRouter } from 'vue-router'
import { BookOpen } from 'lucide-vue-next'
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

        <h2 class="text-xl font-semibold text-text-primary group-hover:text-accent transition-colors duration-200 mb-2 leading-snug">
          {{ article.title }}
        </h2>

        <RouterLink
          v-if="article.collectionTitle"
          :to="`/collection/${article.collectionId}`"
          class="inline-flex items-center gap-1 text-xs px-2 py-0.5 rounded-full bg-accent-subtle text-accent mb-2 hover:bg-accent hover:text-text-inverse transition-all duration-200"
          @click.stop
        >
          <BookOpen :size="10" />
          {{ article.collectionTitle }}
        </RouterLink>

        <p
          v-if="article.summary"
          class="text-sm text-text-secondary line-clamp-2 mb-3 leading-relaxed"
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
