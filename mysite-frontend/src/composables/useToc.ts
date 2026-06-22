import { ref, watch, onMounted, onUnmounted, nextTick, computed, shallowRef } from 'vue'
import type { TocItem } from '@/composables/useMarkdown'

export interface TocNode extends TocItem {
  children: TocNode[]
  parent: TocNode | null
}

export function useToc(items: () => TocItem[]) {
  const activeId = ref('')
  const isClickScrolling = ref(false)
  let clickScrollTimer: ReturnType<typeof setTimeout> | null = null

  // ---- Tree structure ----
  const tocTree = ref<TocNode[]>([])
  let nodeMap = new Map<string, TocNode>()
  const expandedIds = ref(new Set<string>())
  const isCollapsingAll = ref(false)
  const staggerDelay = ref(false)

  function buildTree(flatItems: TocItem[]): TocNode[] {
    const root: TocNode[] = []
    const stack: TocNode[] = []
    nodeMap = new Map()

    for (const item of flatItems) {
      const node: TocNode = { ...item, children: [], parent: null }
      nodeMap.set(node.id, node)

      let top = stack[stack.length - 1]
      while (top && top.level >= node.level) {
        stack.pop()
        top = stack[stack.length - 1]
      }

      if (stack.length === 0) {
        root.push(node)
      } else {
        const parent = stack[stack.length - 1]!
        node.parent = parent
        parent.children.push(node)
      }

      stack.push(node)
    }

    return root
  }

  const minLevel = computed(() => {
    const currentItems = items()
    if (currentItems.length === 0) return 1
    return Math.min(...currentItems.map(i => i.level))
  })

  const hasCollapsibleNodes = computed(() => items().some(i => i.hasChildren))

  const allExpanded = computed(() => {
    if (nodeMap.size === 0) return false
    for (const [id, node] of nodeMap) {
      if (node.hasChildren && !expandedIds.value.has(id)) return false
    }
    return true
  })

  function isGroupExpanded(id: string): boolean {
    return expandedIds.value.has(id)
  }

  // ---- IntersectionObserver ----
  let observer: IntersectionObserver | null = null
  const visibleHeadings = ref(new Set<string>())

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

    const itemMap = new Map(items().map(i => [i.id, i]))
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
      const first = items().find(i => visibleHeadings.value.has(i.id))
      if (first) activeId.value = first.id
    }
  }

  // ---- Scroll: click-to-scroll with priority ----
  function scrollToElement(id: string) {
    const el = document.getElementById(id)
    if (!el) return

    isClickScrolling.value = true
    if (clickScrollTimer) clearTimeout(clickScrollTimer)
    clickScrollTimer = setTimeout(() => {
      isClickScrolling.value = false
    }, 1200)

    el.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }

  /** Expand ancestors first, then scroll after DOM updates. */
  function navigateTo(id: string) {
    const node = nodeMap.get(id)
    if (node) {
      let current = node.parent
      while (current) {
        if (!expandedIds.value.has(current.id)) {
          const newSet = new Set(expandedIds.value)
          newSet.add(current.id)
          expandedIds.value = newSet
        }
        current = current.parent
      }
    }

    activeId.value = id

    nextTick(() => {
      scrollToElement(id)
    })
  }

  // ---- Auto-scroll TOC panel to keep active item visible ----
  function scrollTocToActive(container: HTMLElement | null) {
    if (!container) return
    const activeEl = container.querySelector('[data-active="true"]') as HTMLElement
    if (!activeEl) return

    const containerRect = container.getBoundingClientRect()
    const elRect = activeEl.getBoundingClientRect()

    if (elRect.top < containerRect.top || elRect.bottom > containerRect.bottom) {
      activeEl.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
    }
  }

  // ---- Expand/collapse logic ----
  function toggleNode(id: string) {
    const node = nodeMap.get(id)
    if (!node?.hasChildren) return

    const newSet = new Set(expandedIds.value)
    if (newSet.has(id)) {
      newSet.delete(id)
    } else {
      newSet.add(id)
    }
    expandedIds.value = newSet
  }

  function toggleAll() {
    if (allExpanded.value) {
      isCollapsingAll.value = true
      staggerDelay.value = true
      expandedIds.value = new Set()
      nextTick(() => {
        setTimeout(() => {
          isCollapsingAll.value = false
          staggerDelay.value = false
        }, 400)
      })
    } else {
      staggerDelay.value = false
      expandedIds.value = new Set(nodeMap.keys())
    }
  }

  function ensureActiveVisible() {
    if (isCollapsingAll.value) return
    const node = nodeMap.get(activeId.value)
    if (!node) return
    let current = node.parent
    while (current) {
      if (!expandedIds.value.has(current.id)) {
        const newSet = new Set(expandedIds.value)
        newSet.add(current.id)
        expandedIds.value = newSet
      }
      current = current.parent
    }
  }

  // ---- Wheel event isolation ----
  function handleWheel(e: WheelEvent) {
    const el = e.currentTarget as HTMLElement
    const scrollable = el.querySelector('.overflow-y-auto') as HTMLElement | null
    if (!scrollable) return

    const { scrollTop, scrollHeight, clientHeight } = scrollable
    const atTop = scrollTop <= 0
    const atBottom = scrollTop + clientHeight >= scrollHeight - 1

    if ((e.deltaY < 0 && !atTop) || (e.deltaY > 0 && !atBottom)) {
      e.stopPropagation()
    }
  }

  // ---- Watchers ----
  watch(activeId, () => {
    if (!isClickScrolling.value) {
      ensureActiveVisible()
    }
  })

  watch(items, (newItems) => {
    tocTree.value = buildTree(newItems)
    expandedIds.value = new Set(nodeMap.keys())
    isCollapsingAll.value = false
    staggerDelay.value = false

    nextTick(() => {
      setupObserver()
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
  })

  onMounted(() => {
    tocTree.value = buildTree(items())
    expandedIds.value = new Set(nodeMap.keys())
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

  return {
    activeId: computed(() => activeId.value),
    tocTree,
    expandedIds,
    minLevel,
    hasCollapsibleNodes,
    allExpanded,
    isGroupExpanded,
    staggerDelay,
    isCollapsingAll,
    navigateTo,
    toggleNode,
    toggleAll,
    handleWheel,
    scrollTocToActive,
  }
}
