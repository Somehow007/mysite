<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { Search } from 'lucide-vue-next'
import { getArticles } from '@/api/article'
import { useSearch } from '@/composables/useSearch'
import { calculateReadingTime } from '@/utils/date'
import type { ArticleListItem } from '@/types'

// 404 面板：NotFoundView（不存在路径）与 BlogPost 文章加载失败共用，
// 「最受欢迎」列表样式对齐 HomeLead 侧栏编号列表。
const { open: openSearch } = useSearch()
const popular = ref<ArticleListItem[]>([])

function readingTimeOf(article: ArticleListItem): number {
  return article.readingTime || calculateReadingTime(article.summary || '')
}

onMounted(async () => {
  try {
    const res = await getArticles({ page: 1, size: 3, sortField: 'viewCount', sortOrder: 'desc' })
    popular.value = res.list
  } catch {
    popular.value = []
  }
})
</script>

<template>
  <div class="mx-auto w-full max-w-[720px] py-6 sm:py-10">
    <p class="text-[12px] font-bold uppercase tracking-[0.16em] text-accent">
      Error · Page not found
    </p>
    <p class="mt-4 font-serif text-[clamp(72px,18vw,150px)] font-black leading-[0.95] text-text-primary">
      404
    </p>
    <p class="mt-6 max-w-md font-serif text-lg leading-relaxed text-text-secondary">
      这一页不在本站。它可能已被移动或删除，也可能是地址输错了。
    </p>
    <div class="mt-8 flex flex-wrap items-center gap-3">
      <RouterLink to="/" class="btn-primary paper-flat">
        回到首页
      </RouterLink>
      <button
        type="button"
        class="btn-secondary paper-flat"
        @click="openSearch"
      >
        <Search :size="15" />
        搜索一下
      </button>
    </div>

    <section v-if="popular.length" class="mt-14">
      <h2 class="border-b-2 border-b-border-strong pb-2 text-[12px] font-semibold uppercase tracking-[0.16em] text-text-secondary">
        最受欢迎
      </h2>
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
    </section>
  </div>
</template>
