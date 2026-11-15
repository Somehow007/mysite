<script setup lang="ts">
import { computed } from 'vue'
import type { ArticleNavInfo } from '@/types'

const props = defineProps<{
  navInfo: ArticleNavInfo
  /** 当前文章 id：用于在 collectionArticles 中定位「第 N / M 篇」与进度点 */
  articleId?: string
}>()

const series = computed(() => {
  const nav = props.navInfo
  if (!nav.inCollection || !nav.collectionTitle) return null
  const list = nav.collectionArticles ?? []
  const idx = props.articleId ? list.findIndex(a => a.id === props.articleId) : -1
  return {
    title: nav.collectionTitle,
    collectionId: nav.collectionId,
    total: list.length || null,
    index: idx >= 0 ? idx + 1 : null,
  }
})

function dotClass(i: number): string {
  const index = series.value?.index
  if (!index) return ''
  if (i + 1 === index) return 'is-cur'
  if (i + 1 < index) return 'is-read'
  return ''
}
</script>

<template>
  <nav class="post-snav" aria-label="文章导航">
    <div v-if="series" class="post-snav-header">
      <RouterLink
        v-if="series.collectionId"
        :to="`/collection/${series.collectionId}`"
        class="min-w-0 hover:text-accent transition-colors"
      >
        <b class="truncate">{{ series.title }}</b>
      </RouterLink>
      <b v-else class="truncate">{{ series.title }}</b>
      <span v-if="series.index && series.total">第 {{ series.index }} / {{ series.total }} 篇</span>
    </div>

    <div v-if="series?.total" class="post-snav-dots" aria-hidden="true">
      <i v-for="n in series.total" :key="n" :class="dotClass(n - 1)" />
    </div>

    <div v-if="navInfo.prev || navInfo.next" class="post-snav-pn">
      <RouterLink
        v-if="navInfo.prev"
        :to="`/post/${navInfo.prev.id}`"
        class="group"
      >
        <small>← 上一篇</small>
        <b class="transition-colors group-hover:text-accent">{{ navInfo.prev.title }}</b>
      </RouterLink>
      <RouterLink
        v-if="navInfo.next"
        :to="`/post/${navInfo.next.id}`"
        class="pn-next group"
      >
        <small>下一篇 →</small>
        <b class="transition-colors group-hover:text-accent">{{ navInfo.next.title }}</b>
      </RouterLink>
    </div>
  </nav>
</template>
