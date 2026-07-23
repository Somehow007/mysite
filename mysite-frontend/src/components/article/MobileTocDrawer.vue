<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { List, X, ChevronsDownUp, ChevronsUpDown } from 'lucide-vue-next'
import type { TocItem } from '@/composables/useMarkdown'
import { useToc } from '@/composables/useToc'
import { useFabStack } from '@/composables/useFabStack'
import TocTree from './TocTree.vue'

const props = defineProps<{
  items: TocItem[]
}>()

const isOpen = ref(false)

const { bottomStyle: fabBottom, setVisible } = useFabStack('mobile-toc')
// FAB 可见性：有目录项 且 抽屉未打开
const fabVisible = computed(() => props.items.length > 0 && !isOpen.value)
watch(fabVisible, setVisible, { immediate: true })

const tocListRef = ref<HTMLElement | null>(null)

const {
  activeId,
  tocTree,
  minLevel,
  navigateTo: tocNavigate,
} = useToc(() => props.items)

// ---- Local tree for drawer (always starts all-expanded) ----
const drawerTree = ref<typeof tocTree.value>([])
const drawerCollapsedIds = ref<Record<string, boolean>>({})

function rebuildDrawerTree() {
  drawerTree.value = tocTree.value.map(node => expandAll(cloneNode(node)))
  drawerCollapsedIds.value = {}
}

function cloneNode(node: (typeof tocTree.value)[0]): (typeof tocTree.value)[0] {
  return { ...node, children: node.children.map(c => cloneNode(c)) }
}

function expandAll(node: (typeof tocTree.value)[0]): (typeof tocTree.value)[0] {
  node.hasChildren = node.children.length > 0
  node.children = node.children.map(c => expandAll(c))
  return node
}

function isDrawerGroupExpanded(id: string) {
  return !drawerCollapsedIds.value[id]
}

function toggleDrawerNode(id: string) {
  drawerCollapsedIds.value[id] = !drawerCollapsedIds.value[id]
}

const drawerAllExpanded = computed(() => {
  return Object.keys(drawerCollapsedIds.value).length === 0
})

const drawerHasCollapsible = computed(() => {
  return drawerTree.value.some(n => n.hasChildren)
})

function toggleDrawerAll() {
  if (drawerAllExpanded.value) {
    const collapsed: Record<string, boolean> = {}
    function collect(nodes: typeof tocTree.value) {
      for (const n of nodes) {
        if (n.hasChildren) collapsed[n.id] = true
        collect(n.children)
      }
    }
    collect(drawerTree.value)
    drawerCollapsedIds.value = collapsed
  } else {
    drawerCollapsedIds.value = {}
  }
}

// ---- Body scroll lock ----
let scrollLockCount = 0
function lockBodyScroll() {
  scrollLockCount++
  if (scrollLockCount === 1) {
    const scrollbarWidth = window.innerWidth - document.documentElement.clientWidth
    document.body.style.overflow = 'hidden'
    document.body.style.paddingRight = `${scrollbarWidth}px`
  }
}

function unlockBodyScroll() {
  scrollLockCount = Math.max(0, scrollLockCount - 1)
  if (scrollLockCount === 0) {
    document.body.style.overflow = ''
    document.body.style.paddingRight = ''
  }
}

// ---- Open/close ----
function open() {
  rebuildDrawerTree()
  isOpen.value = true
}

function close() {
  isOpen.value = false
}

function navigateTo(id: string) {
  close()
  nextTick(() => {
    tocNavigate(id)
  })
}

function handleKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape' && isOpen.value) {
    close()
  }
}

// ---- Progress info ----
const activeIndex = computed(() => {
  const idx = props.items.findIndex(i => i.id === activeId.value)
  return idx >= 0 ? idx + 1 : 0
})

const progressPercent = computed(() => {
  if (props.items.length === 0) return 0
  return Math.round((activeIndex.value / props.items.length) * 100)
})

// ---- Lifecycle ----
onMounted(() => {
  document.addEventListener('keydown', handleKeydown)
})

onUnmounted(() => {
  document.removeEventListener('keydown', handleKeydown)
  if (isOpen.value) unlockBodyScroll()
})

watch(isOpen, (open) => {
  if (open) {
    lockBodyScroll()
    rebuildDrawerTree()
  } else {
    unlockBodyScroll()
  }
})

watch(tocTree, () => {
  if (isOpen.value) rebuildDrawerTree()
})
</script>

<template>
  <!-- Floating Action Button -->
  <Transition name="fab">
    <button
      v-if="items.length > 0 && !isOpen"
      @click="open"
      :style="{ bottom: fabBottom }"
      class="fixed right-8 z-30 lg:hidden flex items-center justify-center w-12 h-12 rounded-full glass glass-sm shadow-lg border border-border/50 text-text-secondary hover:text-accent hover:border-accent/30 active:scale-90 transition-all duration-200"
      aria-label="打开文章目录"
      title="目录"
    >
      <List :size="18" />
    </button>
  </Transition>

  <!-- Drawer overlay + content -->
  <Teleport to="body">
    <Transition
      name="drawer"
      @after-leave="unlockBodyScroll"
    >
      <div
        v-if="isOpen"
        class="fixed inset-0 z-40 lg:hidden"
        role="dialog"
        aria-modal="true"
        aria-label="文章目录"
      >
        <!-- Backdrop -->
        <div
          class="absolute inset-0 bg-black/30 backdrop-blur-[2px]"
          @click="close"
        />

        <!-- Drawer panel -->
        <div class="absolute top-0 right-0 bottom-0 w-[min(280px,80vw)] flex flex-col bg-bg-secondary border-l border-border shadow-2xl">
          <!-- Header -->
          <div class="shrink-0 px-4 pt-[max(4rem,env(safe-area-inset-top))] pb-3 border-b border-border">
            <div class="flex items-center justify-between mb-2.5">
              <h3 class="text-sm font-semibold text-text-primary">目录</h3>
              <div class="flex items-center gap-1">
                <button
                  v-if="drawerHasCollapsible"
                  @click="toggleDrawerAll"
                  class="p-1.5 rounded-lg text-text-muted hover:text-accent hover:bg-bg-primary transition-colors duration-150"
                  :title="drawerAllExpanded ? '收起全部' : '展开全部'"
                >
                  <ChevronsDownUp v-if="drawerAllExpanded" :size="14" />
                  <ChevronsUpDown v-else :size="14" />
                </button>
                <button
                  @click="close"
                  class="p-1.5 -mr-1 rounded-lg text-text-muted hover:text-text-secondary hover:bg-bg-primary transition-colors duration-150"
                  aria-label="关闭目录"
                >
                  <X :size="16" />
                </button>
              </div>
            </div>

            <!-- Progress indicator -->
            <div class="flex items-center gap-3">
              <div class="flex-1 h-1 rounded-full bg-border/50 overflow-hidden">
                <div
                  class="h-full rounded-full bg-accent transition-all duration-300 ease-out"
                  :style="{ width: `${progressPercent}%` }"
                />
              </div>
              <span class="text-[11px] text-text-muted tabular-nums whitespace-nowrap">
                {{ activeIndex }}/{{ items.length }}
              </span>
            </div>
          </div>

          <!-- TOC List -->
          <ul
            ref="tocListRef"
            class="flex-1 overflow-y-auto overscroll-contain py-2 px-1 border-l-0 text-sm"
            role="list"
          >
            <TocTree
              :nodes="drawerTree"
              :active-id="activeId"
              :min-level="minLevel"
              :is-group-expanded="isDrawerGroupExpanded"
              :stagger-delay="false"
              text-muted-class="text-text-secondary"
              @navigate="navigateTo"
              @toggle="toggleDrawerNode"
            />
          </ul>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
/* FAB enter/leave */
.fab-enter-active,
.fab-leave-active {
  transition:
    opacity 200ms ease,
    transform 200ms ease;
}
.fab-enter-from,
.fab-leave-to {
  opacity: 0;
  transform: scale(0.8) translateY(8px);
}

/* Drawer transition */
.drawer-enter-active {
  transition: opacity 250ms ease;
}
.drawer-enter-active > div:last-child {
  transition: transform 300ms cubic-bezier(0.16, 1, 0.3, 1);
}
.drawer-leave-active {
  transition: opacity 200ms ease;
}
.drawer-leave-active > div:last-child {
  transition: transform 200ms ease-in;
}

.drawer-enter-from {
  opacity: 0;
}
.drawer-enter-from > div:last-child {
  transform: translateX(100%);
}
.drawer-leave-to {
  opacity: 0;
}
.drawer-leave-to > div:last-child {
  transform: translateX(100%);
}

@media (prefers-reduced-motion: reduce) {
  .fab-enter-active,
  .fab-leave-active,
  .drawer-enter-active,
  .drawer-leave-active {
    transition-duration: 1ms !important;
  }
  .drawer-enter-active > div:last-child,
  .drawer-leave-active > div:last-child {
    transition-duration: 1ms !important;
  }
}
</style>
