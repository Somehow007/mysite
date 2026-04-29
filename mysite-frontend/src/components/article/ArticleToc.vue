<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import type { TocItem } from '@/composables/useMarkdown'

defineProps<{
  items: TocItem[]
}>()

const activeId = ref('')

function handleScroll() {
  const headings = document.querySelectorAll('.prose h1, .prose h2, .prose h3, .prose h4')
  let currentId = ''
  for (const heading of headings) {
    const rect = heading.getBoundingClientRect()
    if (rect.top <= 100) {
      currentId = heading.id
    }
  }
  activeId.value = currentId
}

function scrollTo(id: string) {
  const el = document.getElementById(id)
  if (el) {
    el.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }
}

onMounted(() => {
  window.addEventListener('scroll', handleScroll, { passive: true })
})

onUnmounted(() => {
  window.removeEventListener('scroll', handleScroll)
})
</script>

<template>
  <nav v-if="items.length > 0" class="toc hidden lg:block">
    <h4 class="text-xs font-semibold uppercase tracking-wider text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)] mb-3">
      目录
    </h4>
    <ul class="space-y-0.5 text-sm border-l border-[var(--color-border)] dark:border-[var(--color-dark-border)]">
      <li
        v-for="item in items"
        :key="item.id"
        :style="{ paddingLeft: `${(item.level - 1) * 12}px` }"
      >
        <button
          @click="scrollTo(item.id)"
          class="block w-full text-left py-1.5 pl-3 transition-all duration-200 border-l-2 -ml-px text-[13px] leading-snug"
          :class="[
            activeId === item.id
              ? 'border-[var(--color-accent)] dark:border-[var(--color-dark-accent)] text-[var(--color-accent)] dark:text-[var(--color-dark-accent)] font-medium'
              : 'border-transparent text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)] hover:text-[var(--color-text-body)] dark:hover:text-[var(--color-dark-text-body)] hover:border-[var(--color-border)] dark:hover:border-[var(--color-dark-border)]'
          ]"
        >
          {{ item.text }}
        </button>
      </li>
    </ul>
  </nav>
</template>
