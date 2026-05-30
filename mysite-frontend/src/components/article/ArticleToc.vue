<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import type { TocItem } from '@/composables/useMarkdown'

defineProps<{
  items: TocItem[]
}>()

const activeId = ref('')

const headingElements = ref<HTMLElement[]>([])

function updateHeadingElements() {
  headingElements.value = Array.from(
    document.querySelectorAll('.prose h1, .prose h2, .prose h3, .prose h4')
  )
}

let rafId: number | null = null
function handleScroll() {
  if (rafId) return
  rafId = requestAnimationFrame(() => {
    rafId = null
    let currentId = ''
    for (const heading of headingElements.value) {
      const rect = heading.getBoundingClientRect()
      if (rect.top <= 100) {
        currentId = heading.id
      }
    }
    activeId.value = currentId
  })
}

function scrollTo(id: string) {
  const el = document.getElementById(id)
  if (el) {
    el.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }
}

onMounted(() => {
  updateHeadingElements()
  window.addEventListener('scroll', handleScroll, { passive: true })
})

onUnmounted(() => {
  if (rafId) {
    cancelAnimationFrame(rafId)
    rafId = null
  }
  window.removeEventListener('scroll', handleScroll)
})
</script>

<template>
  <nav v-if="items.length > 0" class="toc hidden lg:block">
    <h4 class="text-xs font-semibold uppercase tracking-wider text-text-muted mb-3">
      目录
    </h4>
    <ul class="space-y-0.5 text-sm border-l border-border">
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
              ? 'border-accent text-accent font-medium'
              : 'border-transparent text-text-muted hover:text-text-secondary hover:border-border'
          ]"
        >
          {{ item.text }}
        </button>
      </li>
    </ul>
  </nav>
</template>
