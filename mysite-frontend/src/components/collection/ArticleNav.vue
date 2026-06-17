<script setup lang="ts">
import { ChevronLeft, ChevronRight, BookOpen } from 'lucide-vue-next'
import type { ArticleNavInfo } from '@/types'

defineProps<{
  navInfo: ArticleNavInfo
}>()
</script>

<template>
  <nav class="border-t border-border pt-6 mt-10" aria-label="文章导航">
    <div v-if="navInfo.inCollection && navInfo.collectionTitle" class="mb-4 text-center">
      <RouterLink
        :to="`/collection/${navInfo.collectionId}`"
        class="inline-flex items-center gap-1.5 text-sm text-accent hover:underline"
      >
        <BookOpen :size="14" />
        {{ navInfo.collectionTitle }}
      </RouterLink>
    </div>

    <div class="flex items-stretch gap-4">
      <RouterLink
        v-if="navInfo.prev"
        :to="`/post/${navInfo.prev.id}`"
        class="flex-1 group flex items-center gap-3 p-4 rounded-lg border border-border hover:border-accent/30 hover:bg-accent-subtle/50 transition-all duration-200"
      >
        <ChevronLeft :size="18" class="text-text-muted group-hover:text-accent transition-colors shrink-0" />
        <div class="min-w-0">
          <span class="text-xs text-text-muted block mb-0.5">上一篇</span>
          <span class="text-sm font-medium text-text-primary group-hover:text-accent transition-colors line-clamp-1">
            {{ navInfo.prev.title }}
          </span>
        </div>
      </RouterLink>
      <div v-else class="flex-1" />

      <RouterLink
        v-if="navInfo.next"
        :to="`/post/${navInfo.next.id}`"
        class="flex-1 group flex items-center justify-end gap-3 p-4 rounded-lg border border-border hover:border-accent/30 hover:bg-accent-subtle/50 transition-all duration-200 text-right"
      >
        <div class="min-w-0">
          <span class="text-xs text-text-muted block mb-0.5">下一篇</span>
          <span class="text-sm font-medium text-text-primary group-hover:text-accent transition-colors line-clamp-1">
            {{ navInfo.next.title }}
          </span>
        </div>
        <ChevronRight :size="18" class="text-text-muted group-hover:text-accent transition-colors shrink-0" />
      </RouterLink>
      <div v-else class="flex-1" />
    </div>
  </nav>
</template>
