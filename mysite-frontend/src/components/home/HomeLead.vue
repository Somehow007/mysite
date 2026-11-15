<script setup lang="ts">
import { computed } from 'vue'
import OptimizedImage from '@/components/common/OptimizedImage.vue'
import { useSiteStore } from '@/stores/site'
import { formatChineseDate, calculateReadingTime } from '@/utils/date'
import { formatViewCount } from '@/utils/format'
import type { ArticleListItem } from '@/types'

const props = defineProps<{
  lead: ArticleListItem | null
  popular: ArticleListItem[]
}>()

const siteStore = useSiteStore()

// kicker 对应概念稿「置顶 · 分类」位：无置顶字段前用「最新」+ 合集/分类
const leadKicker = computed(() => {
  if (!props.lead) return ''
  const parts = ['最新']
  if (props.lead.collectionTitle) parts.push(props.lead.collectionTitle)
  if (props.lead.categoryName) parts.push(props.lead.categoryName)
  return parts.join(' · ')
})

const leadReadingTime = computed(() =>
  props.lead ? (props.lead.readingTime || calculateReadingTime(props.lead.summary || '')) : 0
)

const leadAuthor = computed(() => props.lead?.authorName || siteStore.site.author)

function readingTimeOf(article: ArticleListItem): number {
  return article.readingTime || calculateReadingTime(article.summary || '')
}
</script>

<template>
  <section
    v-if="lead || popular.length"
    class="grid gap-10 border-b border-border py-8 lg:gap-12 lg:py-11"
    :class="popular.length ? 'lg:grid-cols-[minmax(0,1.55fr)_minmax(0,1fr)]' : ''"
  >
    <article v-if="lead" class="min-w-0">
      <RouterLink :to="`/post/${lead.id}`" class="group block">
        <div class="text-[12px] font-bold uppercase tracking-[0.14em] text-accent">
          {{ leadKicker }}
        </div>
        <h2
          class="mt-3 font-serif text-[28px] font-extrabold leading-[1.25] text-text-primary transition-colors duration-200 group-hover:text-accent lg:text-[42px] lg:leading-[1.2]"
        >
          {{ lead.title }}
        </h2>
        <p
          v-if="lead.summary"
          class="mt-4 line-clamp-3 font-serif text-base leading-[1.8] text-text-secondary lg:text-lg"
        >
          {{ lead.summary }}
        </p>
      </RouterLink>

      <RouterLink
        v-if="lead.coverImage"
        :to="`/post/${lead.id}`"
        class="mt-6 block aspect-[16/10] overflow-hidden border border-border-strong lg:aspect-[16/8]"
        :aria-label="lead.title"
      >
        <OptimizedImage
          :src="lead.coverImage"
          :alt="lead.title"
          fetch-priority="high"
          :lazy="false"
          class="h-full w-full"
        />
      </RouterLink>

      <div class="mt-4 flex flex-wrap items-center gap-x-4 gap-y-1 text-[13px] text-text-muted">
        <b class="font-semibold text-text-primary">{{ leadAuthor }}</b>
        <span>{{ formatChineseDate(lead.updateTime) }}</span>
        <span>{{ leadReadingTime }} 分钟阅读</span>
        <span v-if="lead.viewCount > 0">{{ formatViewCount(lead.viewCount) }} 阅读</span>
      </div>
    </article>

    <aside v-if="popular.length" class="min-w-0">
      <h3 class="border-b-2 border-b-border-strong pb-2 text-[12px] font-semibold uppercase tracking-[0.16em] text-text-secondary">
        最受欢迎
      </h3>
      <ol>
        <li v-for="(item, i) in popular" :key="item.id">
          <RouterLink
            :to="`/post/${item.id}`"
            class="group flex gap-3.5 border-b border-border py-4"
          >
            <span class="w-8 shrink-0 font-serif text-[26px] leading-none text-accent">
              {{ String(i + 1).padStart(2, '0') }}
            </span>
            <span class="min-w-0 flex-1">
              <span class="line-clamp-2 font-serif text-[17px] font-semibold leading-[1.45] text-text-primary transition-colors duration-200 group-hover:text-accent">
                {{ item.title }}
              </span>
              <span class="mt-1 block text-xs text-text-muted">
                {{ item.categoryName || '未分类' }} · {{ readingTimeOf(item) }} 分钟
              </span>
            </span>
          </RouterLink>
        </li>
      </ol>
    </aside>
  </section>
</template>
