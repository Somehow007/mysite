<script setup lang="ts">
import { BookOpen } from 'lucide-vue-next'
import OptimizedImage from '@/components/common/OptimizedImage.vue'
import type { Collection } from '@/types'

defineProps<{
  collection: Collection
}>()
</script>

<template>
  <RouterLink
    :to="`/collection/${collection.id}`"
    class="group block rounded-xl border border-border overflow-hidden transition-all duration-300 hover:shadow-lg hover:border-accent/30 hover:-translate-y-0.5 bg-bg-primary"
  >
    <div v-if="collection.coverImage" class="aspect-[2/1] overflow-hidden">
      <OptimizedImage
        :src="collection.coverImage"
        :alt="collection.title"
        class="w-full h-full transition-transform duration-500 group-hover:scale-[1.05]"
      />
    </div>
    <div v-else class="aspect-[2/1] bg-gradient-to-br from-accent/10 to-accent/5 flex items-center justify-center">
      <BookOpen :size="40" class="text-accent/30" />
    </div>

    <div class="p-4">
      <h3 class="text-lg font-semibold text-text-primary group-hover:text-accent transition-colors duration-200 mb-1 line-clamp-1">
        {{ collection.title }}
      </h3>
      <p v-if="collection.description" class="text-sm text-text-secondary line-clamp-2 mb-3 leading-relaxed">
        {{ collection.description }}
      </p>
      <div class="flex items-center justify-between text-xs text-text-muted">
        <span class="inline-flex items-center gap-1">
          <BookOpen :size="12" />
          {{ collection.articleCount }} 篇文章
        </span>
        <span>{{ collection.authorName }}</span>
      </div>
    </div>
  </RouterLink>
</template>
