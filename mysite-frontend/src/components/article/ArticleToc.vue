<script setup lang="ts">
import { ref, watch, onMounted, onUnmounted, nextTick, computed } from 'vue'
import type { TocItem } from '@/composables/useMarkdown'

const props = defineProps<{
  items: TocItem[]
}>()

const activeId = ref('')
const tocNavRef = ref<HTMLElement | null>(null)
const isClickScrolling = ref(false)
let clickScrollTimer: ReturnType<typeof setTimeout> | null = null

// Calculate min level for relative indentation
const minLevel = computed(() => {
  if (props.items.length === 0) return 1
  return Math.min(...props.items.map(i => i.level))
})

// ---- IntersectionObserver for accurate active heading detection ----
let observer: IntersectionObserver | null = null
const visibleHeadings = ref<Set<string>>(new Set())

function setupObserver() {
  if (observer) observer.disconnect()

  observer = new IntersectionObserver(
    (entries) => {
      for (const entry of entries) {
        const id = entry.target.id
        if (entry.isIntersecting) {
          visibleHeadings.value.add(id)
        } else {
          visibleHeadings.value.delete(id)
        }
      }
      updateActiveId()
    },
    {
      // Observe slightly above viewport top to account for sticky header
      rootMargin: '-80px 0px -60% 0px',
      threshold: 0,
    },
  )

  const headings = document.querySelectorAll('.prose h1, .prose h2, .prose h3, .prose h4')
  for (const h of headings) {
    if (h.id) observer!.observe(h)
  }
}

function updateActiveId() {
  if (isClickScrolling.value) return

  if (visibleHeadings.value.size === 0) {
    // Fallback: find the last heading that has scrolled past the top
    const headings = Array.from(
      document.querySelectorAll('.prose h1, .prose h2, .prose h3, .prose h4'),
    )
    let lastId = ''
    for (const h of headings) {
      if ((h as HTMLElement).getBoundingClientRect().top <= 100) {
        lastId = h.id
      }
    }
    if (lastId) activeId.value = lastId
    return
  }

  // Among visible headings, pick the one with the smallest level (highest rank),
  // and among same level, the first one in document order
  const itemMap = new Map(props.items.map(i => [i.id, i]))
  let best: TocItem | null = null
  for (const id of visibleHeadings.value) {
    const item = itemMap.get(id)
    if (!item) continue
    if (!best || item.level < best.level) {
      best = item
    }
  }
  if (best) {
    activeId.value = best.id
  } else {
    // Fallback to first visible
    const first = props.items.find(i => visibleHeadings.value.has(i.id))
    if (first) activeId.value = first.id
  }
}

// ---- Click to scroll ----
function scrollTo(id: string) {
  const el = document.getElementById(id)
  if (!el) return

  isClickScrolling.value = true
  activeId.value = id

  el.scrollIntoView({ behavior: 'smooth', block: 'start' })

  // Keep isClickScrolling true during smooth scroll to prevent observer from overriding
  if (clickScrollTimer) clearTimeout(clickScrollTimer)
  clickScrollTimer = setTimeout(() => {
    isClickScrolling.value = false
  }, 800)
}

// ---- Auto-scroll TOC panel to keep active item visible ----
function scrollTocToActive() {
  if (!tocNavRef.value) return
  const activeEl = tocNavRef.value.querySelector('[data-active="true"]') as HTMLElement
  if (!activeEl) return

  const container = tocNavRef.value
  const containerRect = container.getBoundingClientRect()
  const elRect = activeEl.getBoundingClientRect()

  // If active item is outside visible area of the TOC panel
  if (elRect.top < containerRect.top || elRect.bottom > containerRect.bottom) {
    activeEl.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
  }
}

watch(activeId, () => {
  nextTick(scrollTocToActive)
})

// ---- Re-observe when items change (article loaded) ----
watch(
  () => props.items,
  () => {
    nextTick(() => {
      setupObserver()
      // Initialize active heading
      const headings = Array.from(
        document.querySelectorAll('.prose h1, .prose h2, .prose h3, .prose h4'),
      )
      let lastId = ''
      for (const h of headings) {
        if ((h as HTMLElement).getBoundingClientRect().top <= 100) {
          lastId = h.id
        }
      }
      if (lastId) activeId.value = lastId
    })
  },
)

onMounted(() => {
  setupObserver()
})

onUnmounted(() => {
  if (observer) {
    observer.disconnect()
    observer = null
  }
  if (clickScrollTimer) {
    clearTimeout(clickScrollTimer)
    clickScrollTimer = null
  }
})
</script>

<template>
  <nav
    v-if="items.length > 0"
    ref="tocNavRef"
    aria-label="文章目录"
    class="toc hidden lg:flex flex-col h-full min-h-0"
  >
    <h4 class="text-xs font-semibold uppercase tracking-wider text-text-muted mb-3 px-1 shrink-0">
      目录
    </h4>
    <ul
      class="space-y-0.5 text-sm border-l border-border overflow-y-auto flex-1 min-h-0 scrollbar-thin pr-1"
      role="list"
    >
      <li
        v-for="item in items"
        :key="item.id"
        :style="{ paddingLeft: `${(item.level - minLevel) * 12}px` }"
      >
        <button
          :data-active="activeId === item.id"
          @click="scrollTo(item.id)"
          class="block w-full text-left py-1.5 pl-3 transition-all duration-200 border-l-2 -ml-px text-[13px] leading-snug"
          :class="[
            activeId === item.id
              ? 'border-accent text-accent font-medium bg-accent-subtle/50'
              : 'border-transparent text-text-muted hover:text-text-secondary hover:border-border'
          ]"
          :aria-current="activeId === item.id ? 'true' : undefined"
        >
          {{ item.text }}
        </button>
      </li>
    </ul>
  </nav>
</template>
