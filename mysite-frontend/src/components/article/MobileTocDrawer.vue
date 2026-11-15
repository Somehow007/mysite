<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { X, ChevronsDownUp, ChevronsUpDown } from 'lucide-vue-next'
import type { TocItem } from '@/composables/useMarkdown'
import { useToc } from '@/composables/useToc'
import TocTree from './TocTree.vue'

// 受控底部 sheet（T9）：由 PostDock 以 v-model:open 控制，不再自带 FAB。
const props = defineProps<{
  items: TocItem[]
  open: boolean
  /** 页面级已读进度（0-100），显示在头部右侧 */
  readPct: number
}>()

const emit = defineEmits<{
  'update:open': [value: boolean]
}>()

const sheetRef = ref<HTMLElement | null>(null)

const {
  activeId,
  tocTree,
  minLevel,
  navigateTo: tocNavigate,
} = useToc(() => props.items)

// ---- Local tree for sheet (always starts all-expanded) ----
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
function close() {
  emit('update:open', false)
}

function navigateTo(id: string) {
  close()
  nextTick(() => {
    tocNavigate(id)
  })
}

function handleKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape' && props.open) {
    close()
  }
}

// ---- 下滑关闭（grab + 头部拖拽区）----
let dragStartY = 0
let dragDy = 0
let closingByDrag = false

function onDragStart(e: TouchEvent) {
  if (closingByDrag) return
  const touch = e.touches[0]
  if (!touch) return
  dragStartY = touch.clientY
  dragDy = 0
  if (sheetRef.value) sheetRef.value.style.transition = 'none'
}

function onDragMove(e: TouchEvent) {
  if (closingByDrag) return
  const touch = e.touches[0]
  if (!touch) return
  dragDy = Math.max(0, touch.clientY - dragStartY) // 只响应下拉
  if (sheetRef.value && dragDy > 0) {
    sheetRef.value.style.transform = `translateY(${dragDy}px)`
  }
}

function onDragEnd() {
  const sheet = sheetRef.value
  if (!sheet || closingByDrag) return
  if (dragDy > 100) {
    // 下拉超过阈值：手动滑出屏幕后再卸载，避免与 leave 过渡的 transform 冲突
    closingByDrag = true
    sheet.style.transition = 'transform 200ms ease-in'
    sheet.style.transform = 'translateY(100%)'
    window.setTimeout(() => close(), 200)
  } else {
    if (dragDy > 0) {
      sheet.style.transition = 'transform 150ms ease-out'
    }
    sheet.style.transform = ''
    window.setTimeout(() => {
      if (sheetRef.value) sheetRef.value.style.transition = ''
    }, 200)
  }
  dragDy = 0
}

// ---- Lifecycle ----
onMounted(() => {
  document.addEventListener('keydown', handleKeydown)
})

onUnmounted(() => {
  document.removeEventListener('keydown', handleKeydown)
  if (props.open) unlockBodyScroll()
})

watch(() => props.open, (open) => {
  if (open) {
    closingByDrag = false
    rebuildDrawerTree()
    lockBodyScroll()
  } else {
    unlockBodyScroll()
  }
})

watch(tocTree, () => {
  if (props.open) rebuildDrawerTree()
})
</script>

<template>
  <Teleport to="body">
    <!-- 遮罩 -->
    <Transition name="toc-fade">
      <div
        v-if="open"
        class="toc-sheet-mask"
        @click="close"
        @touchmove.stop.prevent
      />
    </Transition>

    <!-- 底部 sheet -->
    <Transition name="toc-sheet">
      <div
        v-if="open"
        ref="sheetRef"
        class="toc-sheet"
        role="dialog"
        aria-modal="true"
        aria-label="文章目录"
      >
        <!-- 拖拽区：抓手 + 头部（下滑关闭；touch-action: none 独占手势） -->
        <div
          class="toc-sheet-dragzone"
          @touchstart.passive="onDragStart"
          @touchmove.passive="onDragMove"
          @touchend="onDragEnd"
          @touchcancel="onDragEnd"
        >
          <div class="toc-sheet-grab" />
          <header class="toc-sheet-header">
            <b>目录</b>
            <div class="toc-sheet-header-acts">
              <button
                v-if="drawerHasCollapsible"
                @click="toggleDrawerAll"
                class="toc-sheet-icon"
                :title="drawerAllExpanded ? '收起全部' : '展开全部'"
                :aria-label="drawerAllExpanded ? '收起全部' : '展开全部'"
              >
                <ChevronsDownUp v-if="drawerAllExpanded" :size="15" />
                <ChevronsUpDown v-else :size="15" />
              </button>
              <span class="toc-sheet-pct">已读 {{ readPct }}%</span>
              <button
                @click="close"
                class="toc-sheet-icon"
                aria-label="关闭目录"
              >
                <X :size="16" />
              </button>
            </div>
          </header>
        </div>

        <!-- TOC List -->
        <ul class="toc-sheet-body" role="list">
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
    </Transition>
  </Teleport>
</template>

<style scoped>
/* 遮罩：与聊天 sheet 同层（z-40），dock（z-35）被盖住 */
.toc-sheet-mask {
  position: fixed;
  inset: 0;
  z-index: 40;
  background: rgba(0, 0, 0, 0.35);
}

/* 底部 sheet：概念稿 .sheet —— 纸面底 + 顶部墨线 + 抓手 */
.toc-sheet {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 45;
  display: flex;
  flex-direction: column;
  max-height: 78vh;
  background: var(--bg-primary);
  border-top: 1px solid var(--border-strong);
}

.toc-sheet-dragzone {
  flex-shrink: 0;
  touch-action: none;
  user-select: none;
}

.toc-sheet-grab {
  width: 40px;
  height: 4px;
  margin: 8px auto 4px;
  border-radius: 2px;
  background: var(--border);
}

.toc-sheet-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 6px 18px 10px;
  border-bottom: 1px solid var(--border);
}

.toc-sheet-header b {
  font-family: var(--font-serif);
  font-size: 17px;
  font-weight: 700;
  color: var(--text-primary);
}

.toc-sheet-header-acts {
  display: flex;
  align-items: center;
  gap: 6px;
}

.toc-sheet-pct {
  font-size: 12px;
  color: var(--text-muted);
  white-space: nowrap;
}

.toc-sheet-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 6px;
  border-radius: var(--radius-sm);
  color: var(--text-muted);
  transition: color 150ms ease;
}

.toc-sheet-icon:hover {
  color: var(--accent);
}

.toc-sheet-body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
  padding: 8px 10px max(10px, env(safe-area-inset-bottom));
  font-size: 15px;
}

/* 遮罩淡入/淡出 */
.toc-fade-enter-active,
.toc-fade-leave-active {
  transition: opacity 200ms ease;
}
.toc-fade-enter-from,
.toc-fade-leave-to {
  opacity: 0;
}

/* sheet 滑入/滑出 */
.toc-sheet-enter-active {
  transition: transform 300ms cubic-bezier(0.16, 1, 0.3, 1);
}
.toc-sheet-leave-active {
  transition: transform 200ms ease-in;
}
.toc-sheet-enter-from,
.toc-sheet-leave-to {
  transform: translateY(100%);
}

@media (prefers-reduced-motion: reduce) {
  .toc-fade-enter-active,
  .toc-fade-leave-active,
  .toc-sheet-enter-active,
  .toc-sheet-leave-active {
    transition-duration: 1ms !important;
  }
}

/* 纸本专属：方角 + 墨线（scoped unlayered，与 chat.css 同理压过 utilities） */
html[data-theme^="paper"] .toc-sheet-icon {
  border-radius: 0;
}
</style>
