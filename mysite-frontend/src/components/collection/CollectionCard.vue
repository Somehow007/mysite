<script setup lang="ts">
import { BookOpen, Library, Eye } from 'lucide-vue-next'
import OptimizedImage from '@/components/common/OptimizedImage.vue'
import type { Collection } from '@/types'

defineProps<{
  collection: Collection
}>()

function formatViewCount(count?: number): string {
  if (!count || count <= 0) return '0'
  if (count >= 10000) return (count / 10000).toFixed(1) + '万'
  if (count >= 1000) return (count / 1000).toFixed(1) + 'k'
  return String(count)
}
</script>

<template>
  <RouterLink
    :to="`/collection/${collection.id}`"
    class="group block rounded-xl border border-border border-l-[3px] border-l-accent overflow-hidden transition-all duration-300 hover:shadow-lg hover:border-accent/40 hover:-translate-y-0.5 bg-accent-subtle/30"
  >
    <div v-if="collection.coverImage" class="aspect-[2/1] overflow-hidden relative">
      <OptimizedImage
        :src="collection.coverImage"
        :alt="collection.title"
        class="w-full h-full transition-transform duration-500 group-hover:scale-[1.05]"
      />
      <span class="absolute top-2 right-2 inline-flex items-center gap-1 text-xs px-2 py-0.5 rounded-full bg-black/60 text-white backdrop-blur-sm">
        <Library :size="10" />
        合集
      </span>
    </div>
    <div v-else class="aspect-[2/1] bg-gradient-to-br from-accent/10 to-accent/5 flex items-center justify-center relative">
      <BookOpen :size="40" class="text-accent/30" />
      <span class="absolute top-2 right-2 inline-flex items-center gap-1 text-xs px-2 py-0.5 rounded-full bg-accent/20 text-accent backdrop-blur-sm">
        <Library :size="10" />
        合集
      </span>
    </div>

    <div class="p-4">
      <h3 class="text-lg font-semibold text-text-primary group-hover:text-accent transition-colors duration-200 mb-1 line-clamp-1 flex items-center gap-1.5">
        <Library :size="16" class="text-accent shrink-0" />
        {{ collection.title }}
      </h3>
      <p v-if="collection.description" class="text-sm text-text-secondary line-clamp-2 mb-3 leading-relaxed">
        {{ collection.description }}
      </p>
      <div class="flex items-center justify-between text-xs text-text-muted">
        <span class="inline-flex items-center gap-1 px-1.5 py-0.5 rounded bg-accent-subtle text-accent font-medium">
          <BookOpen :size="10" />
          {{ collection.articleCount ?? 0 }} 篇文章
        </span>
        <span class="inline-flex items-center gap-1">
          <Eye :size="10" />
          {{ formatViewCount(collection.totalViewCount) }}
        </span>
      </div>
    </div>
  </RouterLink>
</template>
