<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useHead } from '@unhead/vue'
import { Calendar } from 'lucide-vue-next'
import { getArchiveList } from '@/api/article'
import { formatDate } from '@/utils/date'
import type { ArchiveItem } from '@/types'

useHead({
  title: '归档 - MySite',
})

const archives = ref<ArchiveItem[]>([])
const loading = ref(false)
const error = ref(false)

onMounted(async () => {
  loading.value = true
  try {
    const data = await getArchiveList()
    archives.value = data as ArchiveItem[]
  } catch {
    error.value = true
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div>
    <header class="mb-12 pb-8 border-b border-[var(--color-border)] dark:border-[var(--color-dark-border)]">
      <h1 class="text-3xl font-bold text-[var(--color-text-heading)] dark:text-[var(--color-dark-text-heading)]">
        归档
      </h1>
    </header>

    <div v-if="loading" class="animate-pulse space-y-8">
      <div v-for="i in 3" :key="i" class="space-y-3">
        <div class="skeleton h-6 w-16 rounded" />
        <div class="skeleton h-4 w-64 rounded" />
        <div class="skeleton h-4 w-56 rounded" />
        <div class="skeleton h-4 w-72 rounded" />
      </div>
    </div>

    <div v-else-if="error" class="py-16 text-center">
      <p class="text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)]">归档加载失败</p>
    </div>

    <div v-else-if="archives.length === 0" class="py-16 text-center">
      <p class="text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)]">暂无文章</p>
    </div>

    <div v-else class="space-y-12">
      <section v-for="yearItem in archives" :key="yearItem.year">
        <h2 class="text-2xl font-bold text-[var(--color-text-heading)] dark:text-[var(--color-dark-text-heading)] mb-6">
          {{ yearItem.year }}
        </h2>

        <div v-for="monthItem in yearItem.months" :key="monthItem.month" class="mb-8">
          <h3 class="text-sm font-medium text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)] uppercase tracking-wider mb-3">
            {{ monthItem.month }}月
          </h3>

          <ul class="space-y-2">
            <li v-for="article in monthItem.articles" :key="article.id">
              <RouterLink
                :to="`/post/${article.id}`"
                class="flex items-center gap-3 py-2 px-3 -mx-3 rounded-lg hover:bg-[var(--color-bg-code)] dark:hover:bg-[var(--color-dark-bg-code)] transition-colors group"
              >
                <span class="text-xs text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)] inline-flex items-center gap-1 shrink-0 w-24">
                  <Calendar :size="12" />
                  {{ formatDate(article.createTime) }}
                </span>
                <span class="text-sm text-[var(--color-text-body)] dark:text-[var(--color-dark-text-body)] group-hover:text-[var(--color-text-heading)] dark:group-hover:text-[var(--color-dark-text-heading)] transition-colors truncate">
                  {{ article.title }}
                </span>
              </RouterLink>
            </li>
          </ul>
        </div>
      </section>
    </div>
  </div>
</template>
