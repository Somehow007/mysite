<script setup lang="ts">
import ArticleMeta from './ArticleMeta.vue'
import type { ArticleListItem } from '@/types'

defineProps<{
  article: ArticleListItem
}>()
</script>

<template>
  <article class="group">
    <RouterLink :to="`/post/${article.id}`" class="block">
      <div
        v-if="article.coverImage"
        class="aspect-[2/1] rounded-lg overflow-hidden mb-4 bg-[var(--color-bg-code)] dark:bg-[var(--color-dark-bg-code)]"
      >
        <img
          :src="article.coverImage"
          :alt="article.title"
          class="w-full h-full object-cover transition-transform duration-300 group-hover:scale-[1.02]"
          loading="lazy"
        />
      </div>

      <h2 class="text-xl font-semibold text-[var(--color-text-heading)] dark:text-[var(--color-dark-text-heading)] group-hover:opacity-70 transition-opacity mb-2 leading-snug">
        {{ article.title }}
      </h2>

      <p
        v-if="article.summary"
        class="text-sm text-[var(--color-text-body)] dark:text-[var(--color-dark-text-body)] line-clamp-2 mb-3 leading-relaxed"
      >
        {{ article.summary }}
      </p>

      <ArticleMeta :article="article" show-category />
    </RouterLink>
  </article>
</template>
