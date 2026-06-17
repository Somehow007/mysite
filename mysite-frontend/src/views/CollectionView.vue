<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useHead } from '@unhead/vue'
import { ArrowLeft, BookOpen, Calendar, Eye, Clock } from 'lucide-vue-next'
import { getCollectionById } from '@/api/collection'
import { formatDate, calculateReadingTime } from '@/utils/date'
import OptimizedImage from '@/components/common/OptimizedImage.vue'
import SkeletonCard from '@/components/common/SkeletonCard.vue'
import type { CollectionDetail } from '@/types'

const route = useRoute()

const collection = ref<CollectionDetail | null>(null)
const loading = ref(false)
const error = ref(false)
const currentPage = ref(1)
const pageSize = 10

useHead(() => ({
  title: collection.value ? `${collection.value.title} - MySite` : '合集 - MySite',
  meta: collection.value
    ? [
        { name: 'description', content: collection.value.description || '' },
        { property: 'og:title', content: collection.value.title },
        { property: 'og:description', content: collection.value.description || '' },
        ...(collection.value.coverImage ? [{ property: 'og:image', content: collection.value.coverImage }] : []),
      ]
    : [],
}))

async function fetchCollection(id: string, page = 1) {
  loading.value = true
  error.value = false
  try {
    collection.value = await getCollectionById(id, page, pageSize)
    currentPage.value = page
  } catch {
    error.value = true
    collection.value = null
  } finally {
    loading.value = false
  }
}

function handlePageChange(page: number) {
  if (collection.value) {
    fetchCollection(collection.value.id, page)
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }
}

watch(
  () => route.params.id,
  (newId) => {
    if (newId) fetchCollection(newId as string)
  },
)

onMounted(() => {
  if (route.params.id) {
    fetchCollection(route.params.id as string)
  }
})
</script>

<template>
  <div>
    <div v-if="loading" class="space-y-6">
      <SkeletonCard :lines="3" />
      <div class="space-y-4">
        <SkeletonCard v-for="i in 3" :key="i" :lines="2" />
      </div>
    </div>

    <div v-else-if="error" class="py-16 text-center">
      <p class="text-text-muted mb-4">合集加载失败</p>
      <button @click="$router.push('/')" class="btn-primary">返回首页</button>
    </div>

    <template v-else-if="collection">
      <header class="mb-10">
        <button
          @click="$router.back()"
          class="inline-flex items-center gap-1.5 text-sm text-text-muted hover:text-accent transition-colors duration-200 mb-6 group"
        >
          <ArrowLeft :size="14" class="group-hover:-translate-x-0.5 transition-transform duration-200" />
          返回
        </button>

        <div v-if="collection.coverImage" class="aspect-[3/1] rounded-xl overflow-hidden mb-6">
          <OptimizedImage :src="collection.coverImage" :alt="collection.title" class="w-full h-full object-cover" />
        </div>

        <div class="flex items-center gap-2 mb-3">
          <BookOpen :size="20" class="text-accent" />
          <span class="text-xs font-medium text-accent uppercase tracking-wider">合集</span>
        </div>

        <h1 class="text-3xl sm:text-4xl font-bold text-text-primary leading-tight tracking-tight mb-3">
          {{ collection.title }}
        </h1>

        <p v-if="collection.description" class="text-lg text-text-secondary leading-relaxed mb-4 max-w-2xl">
          {{ collection.description }}
        </p>

        <div class="flex items-center gap-4 text-sm text-text-muted">
          <span class="inline-flex items-center gap-1.5">
            <BookOpen :size="14" />
            {{ collection.articleCount }} 篇文章
          </span>
          <span class="inline-flex items-center gap-1.5">
            <Calendar :size="14" />
            {{ formatDate(collection.createTime) }}
          </span>
          <span>{{ collection.authorName }}</span>
        </div>
      </header>

      <section v-if="collection.articles && collection.articles.length > 0" class="space-y-6">
        <article
          v-for="article in collection.articles"
          :key="article.id"
          class="group"
        >
          <RouterLink :to="`/post/${article.id}`" class="block">
            <div class="flex gap-4 items-start">
              <div v-if="article.coverImage" class="w-28 h-20 rounded-lg overflow-hidden shrink-0">
                <OptimizedImage
                  :src="article.coverImage"
                  :alt="article.title"
                  class="w-full h-full object-cover transition-transform duration-500 group-hover:scale-[1.05]"
                />
              </div>
              <div class="flex-1 min-w-0">
                <h2 class="text-lg font-semibold text-text-primary group-hover:text-accent transition-colors duration-200 mb-1 line-clamp-1">
                  {{ article.title }}
                </h2>
                <p v-if="article.summary" class="text-sm text-text-secondary line-clamp-2 mb-2 leading-relaxed">
                  {{ article.summary }}
                </p>
                <div class="flex items-center gap-3 text-xs text-text-muted">
                  <span class="inline-flex items-center gap-1">
                    <Eye :size="12" />
                    {{ article.viewCount }}
                  </span>
                  <span class="inline-flex items-center gap-1">
                    <Clock :size="12" />
                    {{ article.readingTime || calculateReadingTime(article.summary || '') }} 分钟
                  </span>
                  <span>{{ article.authorName }}</span>
                </div>
              </div>
            </div>
          </RouterLink>
        </article>
      </section>

      <div v-else class="py-16 text-center">
        <BookOpen :size="48" class="mx-auto mb-4 text-text-muted opacity-30" />
        <p class="text-text-muted">该合集暂无文章</p>
      </div>

      <div v-if="collection.articleCount > pageSize" class="flex items-center justify-center gap-2 mt-8">
        <button
          :disabled="currentPage <= 1"
          @click="handlePageChange(currentPage - 1)"
          class="px-3 py-1.5 rounded-lg text-sm border border-border text-text-muted hover:bg-accent-subtle hover:text-accent hover:border-accent/30 transition-all duration-200 disabled:opacity-40 disabled:cursor-not-allowed"
        >
          上一页
        </button>
        <span class="text-sm text-text-muted">
          {{ currentPage }} / {{ Math.ceil(collection.articleCount / pageSize) }}
        </span>
        <button
          :disabled="currentPage >= Math.ceil(collection.articleCount / pageSize)"
          @click="handlePageChange(currentPage + 1)"
          class="px-3 py-1.5 rounded-lg text-sm border border-border text-text-muted hover:bg-accent-subtle hover:text-accent hover:border-accent/30 transition-all duration-200 disabled:opacity-40 disabled:cursor-not-allowed"
        >
          下一页
        </button>
      </div>
    </template>
  </div>
</template>
