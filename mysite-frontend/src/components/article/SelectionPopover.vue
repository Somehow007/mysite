<script setup lang="ts">
// 选区操作条（UI 重构 T10）：选中 .prose 内正文 5–500 字时，
// 在选区上方浮出「✦ 问 AI 这段 / 引用 / 复制」。
// 仅桌面指针（pointer: fine）启用；移动端走系统选择菜单。
// 定位/显隐规则见 .trellis/tasks/11-16-ui-T10-selection/prd.md。
import { ref, computed, watch, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useChatLauncher } from '@/composables/useChatLauncher'
import { stashPendingQuote } from '@/composables/usePendingAsk'
import { useCommentDraft, stashCommentQuote } from '@/composables/useCommentDraft'
import { useUserStore } from '@/stores/user'
import { useSiteStore } from '@/stores/site'
import { useToast } from '@/composables/useToast'

const props = defineProps<{
  /** .prose 容器元素（ArticleContent 的 contentRef），选区必须完整落在其中 */
  container: HTMLElement | null
}>()

const MIN_LEN = 5
const MAX_LEN = 500
const GAP = 8
const VIEW_PAD = 8

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const siteStore = useSiteStore()
const launcher = useChatLauncher()
const { setCommentDraft } = useCommentDraft()
const toast = useToast()

const popRef = ref<HTMLElement | null>(null)
const visible = ref(false)
const pos = ref({ top: 0, left: 0 })
const selectedText = ref('')

// pointer: fine = 桌面鼠标/触控板；粗指针（触屏）交给系统菜单
const finePointer = ref(false)
let pointerMql: MediaQueryList | null = null
const onPointerChange = (e: MediaQueryListEvent) => {
  finePointer.value = e.matches
}

// 仅文章页启用：ArticleContent 也被 AboutView 复用，About 无评论/文章上下文
const enabled = computed(() => finePointer.value && route.name === 'post')
// 站点关闭评论时「引用」没有落点，不渲染该钮
const canQuoteComment = computed(() => siteStore.site.commentEnabled !== false)

let dragging = false
let rafId = 0

function cleanText(raw: string) {
  return raw.replace(/\r\n?/g, '\n').trim()
}

/** 当前选区是否可用：非折叠、两端都在 .prose 内、长度 5–500 */
function validSelection(): { text: string; range: Range } | null {
  const sel = window.getSelection()
  if (!sel || sel.isCollapsed || sel.rangeCount === 0) return null
  const container = props.container
  if (!container) return null
  const anchor = sel.anchorNode
  const focus = sel.focusNode
  if (!anchor || !focus) return null
  if (!container.contains(anchor) || !container.contains(focus)) return null
  const text = cleanText(sel.toString())
  if (text.length < MIN_LEN || text.length > MAX_LEN) return null
  return { text, range: sel.getRangeAt(0) }
}

/** 选区第一个有尺寸的 clientRect（跨段选择取首行片段） */
function anchorRect(range: Range): DOMRect | null {
  for (const r of range.getClientRects()) {
    if (r.width > 0 && r.height > 0) return r
  }
  const b = range.getBoundingClientRect()
  return b.width > 0 && b.height > 0 ? b : null
}

function inViewport(r: DOMRect) {
  return r.bottom > 0 && r.top < window.innerHeight && r.right > 0 && r.left < window.innerWidth
}

function update() {
  rafId = 0
  if (!enabled.value || dragging) {
    hide()
    return
  }
  const sel = validSelection()
  if (!sel) {
    hide()
    return
  }
  const rect = anchorRect(sel.range)
  // 选区滚出视口：隐藏（滚回时 scroll 事件会再次 update 并重定位）
  if (!rect || !inViewport(rect)) {
    hide()
    return
  }
  const el = popRef.value
  const pw = el?.offsetWidth || 300
  const ph = el?.offsetHeight || 40
  let left = rect.left + rect.width / 2 - pw / 2
  left = Math.max(VIEW_PAD, Math.min(left, window.innerWidth - pw - VIEW_PAD))
  // 上边界须让出 sticky header：贴 header 下缘的选区若把 popover 放上方会被 z-50 遮住
  const headerBottom = document.querySelector('header.sticky')?.getBoundingClientRect().bottom ?? 0
  const topClear = Math.max(VIEW_PAD, headerBottom)
  // 优先选区上方；顶部空间不足翻到下方；再不够则钳进视口
  let top = rect.top - ph - GAP
  if (top < topClear) top = rect.bottom + GAP
  if (top + ph > window.innerHeight - VIEW_PAD) {
    top = Math.max(topClear, window.innerHeight - ph - VIEW_PAD)
  }
  pos.value = { top, left }
  selectedText.value = sel.text
  visible.value = true
}

function scheduleUpdate() {
  if (rafId) return
  rafId = requestAnimationFrame(update)
}

function hide() {
  visible.value = false
  selectedText.value = ''
}

// ── 事件 ──────────────────────────────────────────────────
function onSelectionChange() {
  // 拖选过程中不显示，等 mouseup 定稿
  if (dragging) {
    hide()
    return
  }
  scheduleUpdate()
}

function onMouseDown(e: MouseEvent) {
  const pop = popRef.value
  if (pop && e.target instanceof Node && pop.contains(e.target)) return
  dragging = true
  hide()
}

function onMouseUp(e: MouseEvent) {
  dragging = false
  // popover 内松开（点按钮）不重算：避免 click 前一瞬的闪现
  const pop = popRef.value
  if (pop && e.target instanceof Node && pop.contains(e.target)) return
  update()
}

function onScrollOrResize() {
  scheduleUpdate()
}

function onKeyDown(e: KeyboardEvent) {
  if (e.key === 'Escape' && visible.value) {
    window.getSelection()?.removeAllRanges()
    hide()
  }
}

function resetDragging() {
  dragging = false
}

// ── 动作 ──────────────────────────────────────────────────
function askAi() {
  const text = selectedText.value
  hide()
  if (!text) return
  if (!userStore.isLoggedIn) {
    // 匿名：暂存引用，登录回跳后 BlogPost 自动打开面板（对齐 AskArticleCard 模式）
    stashPendingQuote(text, route.fullPath)
    router.push({ name: 'login', query: { redirect: route.fullPath } })
    return
  }
  launcher.open({ quote: text })
}

function quoteComment() {
  const text = selectedText.value
  const articleId = String(route.params.id ?? '')
  hide()
  if (!text || !articleId) return
  if (!userStore.isLoggedIn) {
    stashCommentQuote(articleId, text)
    router.push({ name: 'login', query: { redirect: route.fullPath } })
    return
  }
  setCommentDraft({ quote: text, articleId })
}

async function copyText() {
  const text = selectedText.value
  hide()
  if (!text) return
  try {
    await navigator.clipboard.writeText(text)
    toast.success('已复制')
  } catch {
    toast.error('复制失败')
  }
}

onMounted(() => {
  pointerMql = window.matchMedia('(pointer: fine)')
  finePointer.value = pointerMql.matches
  pointerMql.addEventListener('change', onPointerChange)

  document.addEventListener('selectionchange', onSelectionChange)
  document.addEventListener('mousedown', onMouseDown)
  document.addEventListener('mouseup', onMouseUp)
  window.addEventListener('scroll', onScrollOrResize, { capture: true, passive: true })
  window.addEventListener('resize', onScrollOrResize, { passive: true })
  window.addEventListener('blur', resetDragging)
  document.addEventListener('keydown', onKeyDown)
})

onBeforeUnmount(() => {
  pointerMql?.removeEventListener('change', onPointerChange)
  document.removeEventListener('selectionchange', onSelectionChange)
  document.removeEventListener('mousedown', onMouseDown)
  document.removeEventListener('mouseup', onMouseUp)
  window.removeEventListener('scroll', onScrollOrResize, { capture: true })
  window.removeEventListener('resize', onScrollOrResize)
  window.removeEventListener('blur', resetDragging)
  document.removeEventListener('keydown', onKeyDown)
  if (rafId) cancelAnimationFrame(rafId)
})

// 聊天面板打开（任何入口）时收起选区条
watch(() => launcher.isOpen.value, (open) => {
  if (open) hide()
})

// 容器卸载/重建（骨架屏切换）时收起
watch(() => props.container, (el) => {
  if (!el) hide()
})
</script>

<template>
  <Teleport to="body">
    <div
      ref="popRef"
      class="sel-pop"
      :class="{ 'is-visible': visible }"
      :style="{ top: `${pos.top}px`, left: `${pos.left}px` }"
      role="toolbar"
      aria-label="选区操作"
      @mousedown.prevent
    >
      <button type="button" class="sel-pop-btn sel-pop-btn--primary" @click="askAi">
        ✦ 问 AI 这段
      </button>
      <button
        v-if="canQuoteComment"
        type="button"
        class="sel-pop-btn"
        @click="quoteComment"
      >
        引用
      </button>
      <button type="button" class="sel-pop-btn" @click="copyText">
        复制
      </button>
    </div>
  </Teleport>
</template>
