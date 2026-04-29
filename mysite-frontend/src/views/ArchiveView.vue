<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useHead } from '@unhead/vue'
import { getArchiveList } from '@/api/article'
import { Calendar } from 'lucide-vue-next'
import type { ArchiveItem, ArchiveArticle } from '@/types'

useHead(() => ({
  title: '归档 - MySite',
  meta: [
    { name: 'description', content: '所有文章按时间归档' },
  ],
}))

const archives = ref<ArchiveItem[]>([])
const loading = ref(false)

function flattenArticles(archive: ArchiveItem): ArchiveArticle[] {
  const articles: ArchiveArticle[] = []
  for (const month of archive.months) {
    articles.push(...month.articles)
  }
  return articles
}

onMounted(async () => {
  loading.value = true
  try {
    const res = await getArchiveList()
    archives.value = res as ArchiveItem[]
  } catch {
    archives.value = []
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div>
    <section class="mb-10 pb-8 border-b border-[var(--color-border)] dark:border-[var(--color-dark-border)]">
      <h1 class="text-3xl sm:text-4xl font-bold text-[var(--color-text-heading)] dark:text-[var(--color-dark-text-heading)] tracking-tight">
        归档
      </h1>
      <p class="mt-2 text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)]">
        按时间浏览所有文章
      </p>
    </section>

    <div v-if="loading" class="space-y-4">
      <div v-for="i in 3" :key="i" class="skeleton h-6 w-32 rounded" />
    </div>

    <div v-else-if="archives.length === 0" class="py-16 text-center text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)]">
      暂无文章
    </div>

    <div v-else class="space-y-10">
      <section v-for="archive in archives" :key="archive.year">
        <h2 class="text-lg font-semibold text-[var(--color-text-heading)] dark:text-[var(--color-dark-text-heading)] mb-4 flex items-center gap-2">
          <Calendar :size="16" class="text-[var(--color-accent)] dark:text-[var(--color-dark-accent)]" />
          {{ archive.year }}
          <span class="text-sm font-normal text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)]">
            ({{ flattenArticles(archive).length }})
          </span>
        </h2>
        <div v-for="month in archive.months" :key="month.month" class="mb-6">
          <h3 class="text-sm font-medium text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)] mb-3">
            {{ month.month }}月
          </h3>
          <ul class="space-y-2 ml-6 border-l-2 border-[var(--color-border)] dark:border-[var(--color-dark-border)] pl-6">
            <li v-for="article in month.articles" :key="article.id">
              <RouterLink
                :to="`/post/${article.id}`"
                class="flex items-center gap-3 text-sm text-[var(--color-text-body)] dark:text-[var(--color-dark-text-body)] hover:text-[var(--color-accent)] dark:hover:text-[var(--color-dark-accent)] transition-colors duration-200 py-1 group"
              >
                <span class="text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)] text-xs tabular-nums shrink-0">
                  {{ new Date(article.createTime).toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' }) }}
                </span>
                <span class="truncate group-hover:translate-x-0.5 transition-transform duration-200">{{ article.title }}</span>
              </RouterLink>
            </li>
          </ul>
        </div>
      </section>
    </div>
  </div>
</template>
