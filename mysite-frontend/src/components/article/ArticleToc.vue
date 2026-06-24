<script setup lang="ts">
import { ref, watch, onMounted, onUnmounted, nextTick, computed } from 'vue'
import { ChevronRight, ChevronsDownUp, ChevronsUpDown } from 'lucide-vue-next'
import type { TocItem } from '@/composables/useMarkdown'
import CollapseTransition from '@/components/common/CollapseTransition.vue'

interface TocNode extends TocItem {
  children: TocNode[]
  parent: TocNode | null
}

const props = defineProps<{
  items: TocItem[]
}>()

const activeId = ref('')
const tocNavRef = ref<HTMLElement | null>(null)
const isClickScrolling = ref(false)
let clickScrollTimer: ReturnType<typeof setTimeout> | null = null

// ---- Tree structure ----
const tocTree = ref<TocNode[]>([])
let nodeMap = new Map<string, TocNode>()
const expandedIds = ref(new Set<string>())
const isCollapsingAll = ref(false)
const staggerDelay = ref(false)

function buildTree(items: TocItem[]): TocNode[] {
  const root: TocNode[] = []
  const stack: TocNode[] = []
  nodeMap = new Map()

  for (const item of items) {
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
  if (props.items.length === 0) return 1
  return Math.min(...props.items.map(i => i.level))
})

const hasCollapsibleNodes = computed(() => props.items.some(i => i.hasChildren))

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
    const first = props.items.find(i => visibleHeadings.value.has(i.id))
    if (first) activeId.value = first.id
  }
}

// ---- Scroll priority: click-to-scroll gets highest priority ----
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

/** Expand ancestors first, then scroll after DOM updates. Highest priority. */
function navigateTo(id: string) {
  // Expand all ancestors before scrolling so the target is in the DOM
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

  // Wait for DOM update (expanded content rendered) before scrolling
  nextTick(() => {
    scrollToElement(id)
  })
}

// ---- Auto-scroll TOC panel to keep active item visible ----
function scrollTocToActive() {
  if (!tocNavRef.value) return
  const activeEl = tocNavRef.value.querySelector('[data-active="true"]') as HTMLElement
  if (!activeEl) return

  const container = tocNavRef.value
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

// ---- Prevent TOC scroll from propagating to the main page ----
function handleWheel(e: WheelEvent) {
  const el = e.currentTarget as HTMLElement
  const scrollable = el.querySelector('.overflow-y-auto') as HTMLElement | null
  if (!scrollable) return

  const { scrollTop, scrollHeight, clientHeight } = scrollable
  const atTop = scrollTop <= 0
  const atBottom = scrollTop + clientHeight >= scrollHeight - 1

  // Only intercept when the TOC list can still scroll in the event direction
  if ((e.deltaY < 0 && !atTop) || (e.deltaY > 0 && !atBottom)) {
    e.stopPropagation()
  }
}

// ---- Watchers ----
watch(activeId, () => {
  nextTick(scrollTocToActive)
  // Only auto-expand during natural scrolling, not during click navigation
  // (click navigation already expands ancestors in navigateTo before scrolling)
  if (!isClickScrolling.value) {
    ensureActiveVisible()
  }
})

watch(
  () => props.items,
  () => {
    tocTree.value = buildTree(props.items)
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
  },
)

onMounted(() => {
  tocTree.value = buildTree(props.items)
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
</script>

<template>
  <nav
    v-if="items.length > 0"
    ref="tocNavRef"
    aria-label="文章目录"
    class="toc hidden lg:flex flex-col flex-1 min-h-0"
    @wheel="handleWheel"
  >
    <div class="flex items-center justify-between mb-3 px-1 shrink-0">
      <h4 class="text-xs font-semibold uppercase tracking-wider text-text-muted">
        目录
      </h4>
      <button
        v-if="hasCollapsibleNodes"
        @click="toggleAll"
        class="p-0.5 rounded text-text-muted hover:text-accent transition-colors duration-200"
        :title="allExpanded ? '收起全部' : '展开全部'"
      >
        <ChevronsDownUp v-if="allExpanded" :size="14" />
        <ChevronsUpDown v-else :size="14" />
      </button>
    </div>
    <ul
      class="text-sm border-l border-border overflow-y-auto flex-1 min-h-0 scrollbar-thin pr-1"
      role="list"
    >
      <template v-for="(node, idx0) in tocTree" :key="node.id">
        <li :style="{ paddingLeft: `${(node.level - minLevel) * 12}px` }">
          <button
            :data-active="activeId === node.id"
            @click="navigateTo(node.id)"
            class="group/toc flex items-center w-full text-left py-1.5 pl-3 transition-all duration-200 border-l-2 -ml-px text-[13px] leading-snug"
            :class="[
              activeId === node.id
                ? 'border-accent text-accent font-medium bg-accent-subtle/50'
                : 'border-transparent text-text-muted hover:text-text-secondary hover:border-border',
            ]"
            :aria-current="activeId === node.id ? 'true' : undefined"
          >
            <span
              v-if="node.hasChildren"
              @click.stop="toggleNode(node.id)"
              class="toc-toggle shrink-0"
              :class="isGroupExpanded(node.id) ? 'rotate-90' : ''"
              role="button"
              :aria-label="isGroupExpanded(node.id) ? '收起' : '展开'"
            >
              <ChevronRight :size="12" />
            </span>
            {{ node.text }}
          </button>
          <CollapseTransition
            v-if="node.hasChildren"
            :show="isGroupExpanded(node.id)"
            :delay="staggerDelay ? idx0 * 40 : undefined"
          >
            <ul>
              <template v-for="child in node.children" :key="child.id">
                <li :style="{ paddingLeft: `${(child.level - minLevel) * 12}px` }">
                  <button
                    :data-active="activeId === child.id"
                    @click="navigateTo(child.id)"
                    class="group/toc flex items-center w-full text-left py-1.5 pl-3 transition-all duration-200 border-l-2 -ml-px text-[13px] leading-snug"
                    :class="[
                      activeId === child.id
                        ? 'border-accent text-accent font-medium bg-accent-subtle/50'
                        : 'border-transparent text-text-muted hover:text-text-secondary hover:border-border',
                    ]"
                    :aria-current="activeId === child.id ? 'true' : undefined"
                  >
                    <span
                      v-if="child.hasChildren"
                      @click.stop="toggleNode(child.id)"
                      class="toc-toggle shrink-0"
                      :class="isGroupExpanded(child.id) ? 'rotate-90' : ''"
                      role="button"
                      :aria-label="isGroupExpanded(child.id) ? '收起' : '展开'"
                    >
                      <ChevronRight :size="12" />
                    </span>
                    {{ child.text }}
                  </button>
                  <CollapseTransition
                    v-if="child.hasChildren"
                    :show="isGroupExpanded(child.id)"
                    :delay="staggerDelay ? idx0 * 40 : undefined"
                  >
                    <ul>
                      <template v-for="grandchild in child.children" :key="grandchild.id">
                        <li
                          :style="{
                            paddingLeft: `${(grandchild.level - minLevel) * 12}px`,
                          }"
                        >
                          <button
                            :data-active="activeId === grandchild.id"
                            @click="navigateTo(grandchild.id)"
                            class="group/toc flex items-center w-full text-left py-1.5 pl-3 transition-all duration-200 border-l-2 -ml-px text-[13px] leading-snug"
                            :class="[
                              activeId === grandchild.id
                                ? 'border-accent text-accent font-medium bg-accent-subtle/50'
                                : 'border-transparent text-text-muted hover:text-text-secondary hover:border-border',
                            ]"
                            :aria-current="
                              activeId === grandchild.id ? 'true' : undefined
                            "
                          >
                            <span
                              v-if="grandchild.hasChildren"
                              @click.stop="toggleNode(grandchild.id)"
                              class="toc-toggle shrink-0"
                              :class="
                                isGroupExpanded(grandchild.id) ? 'rotate-90' : ''
                              "
                              role="button"
                              :aria-label="
                                isGroupExpanded(grandchild.id) ? '收起' : '展开'
                              "
                            >
                              <ChevronRight :size="12" />
                            </span>
                            {{ grandchild.text }}
                          </button>
                          <CollapseTransition
                            v-if="grandchild.hasChildren"
                            :show="isGroupExpanded(grandchild.id)"
                            :delay="staggerDelay ? idx0 * 40 : undefined"
                          >
                            <ul>
                              <li
                                v-for="gg in grandchild.children"
                                :key="gg.id"
                                :style="{
                                  paddingLeft: `${(gg.level - minLevel) * 12}px`,
                                }"
                              >
                                <button
                                  :data-active="activeId === gg.id"
                                  @click="navigateTo(gg.id)"
                                  class="block w-full text-left py-1.5 pl-3 transition-all duration-200 border-l-2 -ml-px text-[13px] leading-snug"
                                  :class="[
                                    activeId === gg.id
                                      ? 'border-accent text-accent font-medium bg-accent-subtle/50'
                                      : 'border-transparent text-text-muted hover:text-text-secondary hover:border-border',
                                  ]"
                                  :aria-current="
                                    activeId === gg.id ? 'true' : undefined
                                  "
                                >
                                  {{ gg.text }}
                                </button>
                              </li>
                            </ul>
                          </CollapseTransition>
                        </li>
                      </template>
                    </ul>
                  </CollapseTransition>
                </li>
              </template>
            </ul>
          </CollapseTransition>
        </li>
      </template>
    </ul>
  </nav>
</template>

<style scoped>
.toc-toggle {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 20px;
  min-height: 20px;
  margin: -4px 2px -4px -4px;
  border-radius: 4px;
  color: currentColor;
  opacity: 0.5;
  cursor: pointer;
  transition:
    opacity 150ms ease,
    transform 200ms ease,
    background-color 150ms ease;
}

.toc-toggle:hover {
  opacity: 1;
  background-color: var(--border, #e5e7eb);
}
</style>
