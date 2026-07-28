<script setup lang="ts">
import { ref, nextTick, watch, onScopeDispose, onBeforeUnmount, computed } from 'vue'
import { useRouter } from 'vue-router'
import { Sparkles, X, RotateCw, PanelLeftOpen, AlertTriangle, Clock, LogIn, Library } from 'lucide-vue-next'
import { useMediaQuery } from '@vueuse/core'
import { useChat } from '@/composables/useChat'
import { useFabStack } from '@/composables/useFabStack'
import { useVisualViewport } from '@/composables/useVisualViewport'
import { getVisitorId, getKnowledgeBases } from '@/api/rag'
import { useUserStore } from '@/stores/user'
import type { KnowledgeBase } from '@/types'
import ChatMessageItem from './ChatMessageItem.vue'
import ChatInput from './ChatInput.vue'
import ChatHistory from './ChatHistory.vue'

// 推荐问题：可通过知识库配置下发，数组为空则不渲染该区域
const SUGGESTED_QUESTIONS = [
  '介绍一下这个博客',
  '最近发布了哪些文章？',
  'Spring Security 怎么配置 JWT？',
]

const hasSuggestedQuestions = computed(() => SUGGESTED_QUESTIONS.length > 0)

const router = useRouter()
const userStore = useUserStore()
const isOpen = ref(false)
const messagesContainer = ref<HTMLElement | null>(null)
const inputComponentRef = ref<InstanceType<typeof ChatInput> | null>(null)

const chat = useChat()
const isUserScrolledUp = ref(false)

// ── 移动端适配：断点 + 可视视口高度 ──────────────────────────
// isMobile 对齐 Tailwind sm 断点（640px）：< 640px 走底部 sheet 形态
const isMobile = useMediaQuery('(max-width: 639px)')
// vvHeight：软键盘弹起时实时收缩的可视高度（iOS 布局视口不收缩，必须用它）
const { vvHeight } = useVisualViewport()

// ── 知识库选择 ────────────────────────────────────────────────
const kbs = ref<KnowledgeBase[]>([])
const kbsLoading = ref(false)

async function loadKbs() {
  kbsLoading.value = true
  try {
    kbs.value = await getKnowledgeBases()
  } catch {
    kbs.value = []
  } finally {
    kbsLoading.value = false
  }
}

function toggleKb(kbId: string) {
  chat.toggleKb(kbId)
}

const hasSelectedKbs = computed(() => chat.selectedKbIds.value.length > 0)
const subtitleText = computed(() => {
  if (!hasSelectedKbs.value) return '未选择知识库，直接对话'
  if (chat.selectedKbIds.value.length === 1 && kbs.value.length > 0) {
    const kb = kbs.value.find(k => k.id === chat.selectedKbIds.value[0])
    return kb ? `检索：${kb.name}` : '已选择 1 个知识库'
  }
  return `检索 ${chat.selectedKbIds.value.length} 个知识库`
})

// ── 历史侧栏 ────────────────────────────────────────────────
const showHistory = ref(false)

function toggleHistory() {
  showHistory.value = !showHistory.value
}

function handleSelectConversation(id: string) {
  chat.switchConversation(id)
  showHistory.value = false
}

function handleNewConversation() {
  chat.newConversation()
  showHistory.value = false
}

function handleDeleteConversation(id: string) {
  if (chat.conversationId.value === id) {
    chat.newConversation()
  }
  chat.conversations.value = chat.conversations.value.filter(c => c.id !== id)
}

function handleRenameConversation() {
  chat.loadConversations(getVisitorId())
}

// Rate limit countdown
const rateLimitSeconds = ref(0)
let rateLimitTimer: ReturnType<typeof setInterval> | null = null

watch(() => chat.lastError.value, (err) => {
  if (err && err.kind === 'http' && err.status === 429) {
    rateLimitSeconds.value = 60
    if (rateLimitTimer) clearInterval(rateLimitTimer)
    rateLimitTimer = setInterval(() => {
      rateLimitSeconds.value--
      if (rateLimitSeconds.value <= 0) {
        if (rateLimitTimer) clearInterval(rateLimitTimer)
        rateLimitTimer = null
      }
    }, 1000)
  }
})

onBeforeUnmount(() => {
  if (rateLimitTimer) clearInterval(rateLimitTimer)
})

// 面板打开时加载历史对话列表 + KB 列表
watch(isOpen, (open) => {
  if (open) {
    chat.loadConversations(getVisitorId())
    loadKbs()
    showHistory.value = false
  }
})

// ── FAB 动态堆叠 ─────────────────────────────────────────────
const { bottomStyle: fabBottom, setVisible: setFabVisible } = useFabStack('chat-widget')
// 面板打开时 FAB 隐藏，其他 FAB 自动下移
watch(isOpen, (open) => setFabVisible(!open))

// ── 智能滚动 ────────────────────────────────────────────────

function isNearBottom(el: HTMLElement): boolean {
  return el.scrollHeight - el.scrollTop - el.clientHeight < 80
}

function scrollToBottom(smooth = false) {
  if (messagesContainer.value) {
    messagesContainer.value.scrollTo({
      top: messagesContainer.value.scrollHeight,
      behavior: smooth ? 'smooth' : 'instant',
    })
    isUserScrolledUp.value = false
  }
}

// 监听消息变化：用户贴底时自动滚，用户上翻时不打扰
watch(
  () => chat.messages.value.length,
  () => nextTick(() => {
    if (messagesContainer.value && !isUserScrolledUp.value) {
      scrollToBottom()
    }
  }),
)

// 监听流式 token 追加：始终保持滚动（除非用户上翻）
watch(
  () => {
    const msgs = chat.messages.value
    const last = msgs[msgs.length - 1]
    return last?.pending ? last.content.length : 0
  },
  () => {
    if (messagesContainer.value && !isUserScrolledUp.value) {
      messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
    }
  },
)

function handleScroll() {
  if (messagesContainer.value) {
    isUserScrolledUp.value = !isNearBottom(messagesContainer.value)
  }
}

// ── 开合控制 ────────────────────────────────────────────────

/** ESC 分级退出：先收历史抽屉，再关面板 */
function handleEsc(e: KeyboardEvent) {
  if (e.key === 'Escape' && isOpen.value) {
    e.preventDefault()
    if (showHistory.value) {
      showHistory.value = false
      return
    }
    close()
  }
}

/** ESC 监听与 isOpen 状态同步绑定，面板关闭即解绑，防止泄漏 */
watch(isOpen, (open) => {
  if (open) {
    document.addEventListener('keydown', handleEsc)
  } else {
    document.removeEventListener('keydown', handleEsc)
  }
})

function open() {
  isOpen.value = true
  closingByDrag = false // 重置下滑关闭标记（面板重建后需允许再次拖拽）
  nextTick(() => {
    // 移动端不自动聚焦输入框 —— 打开面板瞬间弹软键盘会直接盖住半个面板，
    // 用户点输入框时才弹键盘；桌面端保留自动聚焦
    if (!isMobile.value) {
      const textarea = inputComponentRef.value?.$el?.querySelector?.('textarea') as HTMLTextAreaElement | null
      textarea?.focus()
    }
    scrollToBottom()
  })
}

function close() {
  isOpen.value = false
}

// 键盘弹起/收起 → 面板高度（--vvh 驱动）变化后，把最新消息滚回视野
// （用户主动上翻时不打扰，沿用 isUserScrolledUp 守卫）
watch(vvHeight, () => {
  if (!isOpen.value || isUserScrolledUp.value) return
  nextTick(() => scrollToBottom())
})

// ── 移动端下滑关闭 ──────────────────────────────────────────
const panelRef = ref<HTMLElement | null>(null)
let dragStartY = 0
let dragDy = 0
let closingByDrag = false

function onDragStart(e: TouchEvent) {
  if (closingByDrag) return
  const touch = e.touches[0]
  if (!touch) return // 防御：touches 为空（极端边界事件）
  dragStartY = touch.clientY
  dragDy = 0
  if (panelRef.value) panelRef.value.style.transition = 'none'
}

function onDragMove(e: TouchEvent) {
  if (closingByDrag) return
  const touch = e.touches[0]
  if (!touch) return // 防御：手指已全部离开（此时应触发 touchend/touchcancel）
  dragDy = Math.max(0, touch.clientY - dragStartY) // 只响应下拉
  if (panelRef.value && dragDy > 0) {
    panelRef.value.style.transform = `translateY(${dragDy}px)`
  }
}

function onDragEnd() {
  const panel = panelRef.value
  if (!panel || closingByDrag) return
  if (dragDy > 100) {
    // 下拉超过阈值：手动滑出屏幕后再卸载，避免与 leave 过渡的 transform 冲突
    closingByDrag = true
    panel.style.transition = 'transform 200ms ease-in'
    panel.style.transform = 'translateY(100%)'
    window.setTimeout(() => close(), 200)
  } else {
    // 未达阈值（或未移动）：回弹并恢复 stylesheet 的 height transition
    // 注意必须恢复 —— onDragStart 设的 inline transition:none 会吞掉键盘联动的高度过渡
    if (dragDy > 0) {
      panel.style.transition = 'transform 150ms ease-out'
    }
    panel.style.transform = ''
    window.setTimeout(() => {
      if (panelRef.value) panelRef.value.style.transition = ''
    }, 200)
  }
  dragDy = 0
}

// ── 移动端打开 sheet 时阻止 body 背景滚动 ──
watch(isOpen, (open) => {
  document.body.style.overflow = (open && isMobile.value) ? 'hidden' : ''
})

onBeforeUnmount(() => {
  document.body.style.overflow = ''
})

onScopeDispose(() => {
  chat.cancelGeneration()
  document.removeEventListener('keydown', handleEsc)
  document.body.style.overflow = ''
})
</script>

<template>
  <!-- FAB 按钮 -->
  <button
    v-if="!isOpen"
    :style="{ bottom: fabBottom }"
    class="fixed right-6 z-40 w-14 h-14
           rounded-full bg-accent text-white
           shadow-lg hover:shadow-xl hover:-translate-y-0.5
           flex items-center justify-center
           transition-all duration-200"
    aria-label="打开 AI 助手"
    @click="open"
  >
    <Sparkles :size="22" />
  </button>

  <!-- 遮罩 + 面板：独立 Transition，各自播放进出场动画 -->
  <Teleport to="body">
    <!-- 移动端遮罩（桌面端面板不挡页面，无遮罩）；
         touchmove.prevent 拦截 iOS 背景滚动穿透 -->
    <Transition name="fade">
      <div
        v-if="isOpen"
        class="fixed inset-0 z-40 bg-black/40 sm:hidden"
        @click="close"
        @touchmove.stop.prevent
      />
    </Transition>

    <!-- 面板主体：移动端底部 sheet（顶部留缝、圆角、可下滑关闭），
         桌面端锚定右下角 520×680；高度逻辑见 scoped style .chat-panel。
         定位统一用物理属性（left/right），避免 Tailwind v4 inset-x 的逻辑属性
         （inset-inline）与 sm:left-auto 等物理覆盖产生级联歧义 -->
    <Transition :name="isMobile ? 'sheet' : 'zoom'">
      <div
        v-if="isOpen"
        ref="panelRef"
        class="chat-panel fixed left-0 right-0 bottom-0 z-50
               sm:left-auto sm:right-10 sm:bottom-10 sm:w-[520px]
               rounded-t-2xl sm:rounded-2xl
               bg-bg-elevated border border-border
               flex flex-col overflow-hidden
               sm:shadow-[0_24px_64px_-12px_rgba(17,24,39,0.22),0_8px_24px_-8px_rgba(79,70,229,0.12)]"
      >
        <!-- 拖拽区：把手 + Header（移动端下滑关闭；touch-action: none 独占手势） -->
        <div
          class="shrink-0 touch-none select-none sm:touch-auto"
          @touchstart.passive="onDragStart"
          @touchmove.passive="onDragMove"
          @touchend="onDragEnd"
          @touchcancel="onDragEnd"
        >
          <!-- 拖拽把手（仅移动端） -->
          <div class="sm:hidden flex justify-center pt-2 pb-1">
            <div class="w-9 h-1 rounded-full bg-border" />
          </div>

          <!-- Header：60px -->
          <div class="flex items-center gap-2.5 px-3.5 border-b border-border"
               style="height: 60px;">
          <!-- 历史按钮 -->
          <button
            class="icon-btn w-[34px] h-[34px] rounded-lg flex items-center justify-center shrink-0
                   text-text-secondary hover:bg-accent-subtle hover:text-accent transition-colors"
            :class="showHistory ? 'bg-accent-subtle text-accent' : ''"
            aria-label="历史对话"
            title="历史对话"
            @click="toggleHistory"
          >
            <PanelLeftOpen :size="16" />
          </button>

          <!-- 标题区 -->
          <div class="flex-1 min-w-0">
            <h1 class="text-[15px] font-semibold text-text-primary leading-tight">AI 助手</h1>
            <p class="text-[11.5px] text-text-muted mt-0.5 flex items-center gap-1.5">
              <span
                class="w-1.5 h-1.5 rounded-full shrink-0"
                :class="hasSelectedKbs ? 'bg-accent' : 'bg-text-muted'"
              />
              {{ subtitleText }}
            </p>
          </div>

          <!-- 操作按钮 -->
          <button
            class="icon-btn w-[34px] h-[34px] rounded-lg flex items-center justify-center shrink-0
                   text-text-secondary hover:bg-accent-subtle hover:text-accent transition-colors"
            aria-label="新会话"
            title="新会话"
            @click="handleNewConversation"
          >
            <RotateCw :size="15" />
          </button>
          <button
            class="icon-btn w-[34px] h-[34px] rounded-lg flex items-center justify-center shrink-0
                   text-text-secondary hover:bg-accent-subtle hover:text-accent transition-colors"
            aria-label="关闭"
            @click="close"
          >
            <X :size="17" />
          </button>
          </div>
        </div>
        <!-- 拖拽区结束 -->

        <!-- KB 选择标签栏 -->
        <div
          v-if="kbs.length > 0"
          class="shrink-0 px-3.5 py-2 border-b border-border flex items-center gap-1.5 overflow-x-auto scrollbar-thin"
        >
          <Library :size="13" class="text-text-muted shrink-0" />
          <button
            v-for="kb in kbs"
            :key="kb.id"
            @click="toggleKb(kb.id)"
            class="shrink-0 inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-medium transition-all duration-200"
            :class="chat.selectedKbIds.value.includes(kb.id)
              ? 'bg-accent text-text-inverse shadow-sm'
              : 'bg-bg-code text-text-muted hover:bg-accent-subtle hover:text-accent'"
          >
            {{ kb.name }}
          </button>
          <span v-if="kbsLoading" class="text-[11px] text-text-muted ml-1">加载中...</span>
        </div>

        <!-- 内容区（历史抽屉已提升为面板级覆盖层，见 ChatInput 之后） -->
        <div class="flex-1 relative overflow-hidden">

          <!-- 消息列表（overscroll-contain：滚动到顶/底不链到背后页面） -->
          <div
            ref="messagesContainer"
            class="h-full overflow-y-auto overscroll-contain px-4 py-4 scrollbar-thin"
            role="log"
            aria-live="polite"
            @scroll="handleScroll"
          >
            <!-- Anonymous user tip -->
            <div
              v-if="!userStore.isLoggedIn"
              class="mb-4 px-3 py-2 rounded-lg bg-info-subtle border border-info/20 text-xs text-info flex items-center gap-2"
            >
              <AlertTriangle :size="14" class="shrink-0" />
              <span class="flex-1">登录后可保存对话历史，享受更多额度</span>
              <button class="btn-ghost text-xs shrink-0 px-2 py-1" @click="router.push({ name: 'login' })">
                <LogIn :size="12" />登录
              </button>
            </div>

            <!-- Rate limit banner -->
            <div
              v-if="rateLimitSeconds > 0"
              class="mb-4 px-3 py-2 rounded-lg bg-warning-subtle border border-warning/20 text-xs text-warning flex items-center gap-2"
            >
              <Clock :size="14" class="shrink-0" />
              <span>提问过于频繁，请 {{ rateLimitSeconds }} 秒后重试</span>
            </div>

            <!-- 空状态 -->
            <div
              v-if="chat.messages.value.length === 0"
              class="flex flex-col items-center justify-center h-full text-center pt-12"
            >
              <div class="text-4xl mb-3">👋</div>
              <p class="text-sm text-text-secondary mb-1 font-medium">
                你好，我是博客 AI 助手
              </p>
              <p class="text-xs text-text-muted mb-6">
                我可以基于博客文章内容回答你的问题
              </p>
              <div
                v-if="hasSuggestedQuestions"
                class="flex flex-wrap justify-center gap-2 max-w-[280px]"
              >
                <button
                  v-for="q in SUGGESTED_QUESTIONS"
                  :key="q"
                  class="bg-accent-subtle text-accent rounded-full px-3 py-1.5
                         text-xs hover:opacity-80 transition-opacity"
                  @click="chat.sendMessage(q)"
                >
                  {{ q }}
                </button>
              </div>
            </div>

            <!-- 消息 -->
            <ChatMessageItem
              v-for="msg in chat.messages.value"
              :key="msg.id"
              :message="msg"
              @retry="chat.retry()"
            />

            <!-- 流式状态行 -->
            <div
              v-if="chat.isStreaming.value"
              class="flex items-center gap-2 px-1 py-2 text-xs text-text-muted border-t border-dashed border-border mt-2"
            >
              <span class="w-1.5 h-1.5 rounded-full bg-accent animate-pulse" />
              正在生成…
              <button class="ml-2 text-accent hover:underline" @click="chat.cancelGeneration()">点击停止</button>
            </div>

            <!-- 错误横幅 -->
            <div
              v-if="chat.lastError.value && chat.messages.value.length > 0"
              class="flex items-center justify-center my-2"
            >
              <div
                class="flex items-center gap-2 text-xs px-3 py-2 rounded-lg w-full"
                :class="chat.lastError.value.kind === 'network'
                  ? 'bg-warning-subtle text-warning'
                  : 'bg-danger-subtle text-danger'"
              >
                <AlertTriangle :size="14" class="shrink-0" />
                <span class="flex-1">{{ chat.lastError.value.message }}</span>
                <button
                  v-if="chat.lastError.value.kind !== 'http'"
                  class="underline shrink-0 hover:opacity-80"
                  @click="chat.retry()"
                >
                  重试
                </button>
              </div>
            </div>
          </div>

        </div>
        <!-- 内容区结束 -->

        <!-- 输入框 -->
        <ChatInput
          ref="inputComponentRef"
          :streaming="chat.isStreaming.value"
          @send="chat.sendMessage"
          @cancel="chat.cancelGeneration()"
        />

        <!-- 历史对话抽屉：面板内最上层（z-20/30），覆盖 header 到输入框的完整高度。
             旧实现内嵌在内容区：头部/输入框外露可误操作，且列表底部被裁切导致
             历史"显示不全"。提升为面板级覆盖层后，浏览历史时优先展示历史。 -->
        <Transition name="fade">
          <div
            v-if="showHistory"
            class="absolute inset-0 z-20 bg-black/15"
            @click="showHistory = false"
          />
        </Transition>
        <Transition name="slide-left">
          <div
            v-if="showHistory"
            class="absolute inset-y-0 left-0 z-30 w-[min(236px,80vw)] bg-bg-elevated border-r border-border flex flex-col shadow-lg"
          >
            <div class="flex items-center justify-between px-3 py-2.5 border-b border-border shrink-0">
              <span class="text-xs font-semibold text-text-secondary">历史对话 · {{ chat.conversations.value.length }} 条</span>
              <button
                class="p-0.5 rounded text-text-muted hover:text-text-primary transition-colors"
                aria-label="关闭历史对话"
                @click="showHistory = false"
              >
                <X :size="14" />
              </button>
            </div>
            <ChatHistory
              :conversations="chat.conversations.value"
              :current-id="chat.conversationId.value"
              :loading="chat.loadingHistory.value"
              @select="handleSelectConversation"
              @new="handleNewConversation"
              @deleted="handleDeleteConversation"
              @renamed="handleRenameConversation"
            />
          </div>
        </Transition>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
/* ── 面板高度 ────────────────────────────────────────────────
   移动端底部 sheet：min(100dvh - 24px, var(--vvh) - 8px)
   - 100dvh - 24px：顶部留 24px 缝隙可见背后页面，不再全屏吞掉内容
   - var(--vvh) - 8px：useVisualViewport 注入的实时可视高度；
     软键盘弹起时 --vvh 收缩，面板随之压缩到键盘上方，输入框始终可见
   桌面端（≥640px）：固定 680px，受视口封顶。
   注意：scoped style 不带 @layer，优先级高于 Tailwind utilities，
   高度统一在此管理（模板不再写 sm:h-[680px]）。 */
.chat-panel {
  height: min(calc(100vh - 24px), calc(var(--vvh, 100vh) - 8px));
  transition: height 200ms ease-out;
}
@supports (height: 100dvh) {
  .chat-panel {
    height: min(calc(100dvh - 24px), calc(var(--vvh, 100dvh) - 8px));
  }
}
@media (min-width: 640px) {
  .chat-panel {
    height: 680px;
    max-height: calc(100vh - 80px);
  }
}

/* 移动端底部 sheet 滑入/滑出 */
.sheet-enter-active {
  transition: transform 300ms cubic-bezier(0.16, 1, 0.3, 1);
}
.sheet-leave-active {
  transition: transform 200ms ease-in;
}
.sheet-enter-from,
.sheet-leave-to {
  transform: translateY(100%);
}

/* 桌面端缩放入/出（替代原单向 animate-scale-in，补上退场动画） */
.zoom-enter-active {
  transition:
    opacity 200ms var(--ease-out),
    transform 200ms var(--ease-out);
}
.zoom-leave-active {
  transition:
    opacity 150ms ease-in,
    transform 150ms ease-in;
}
.zoom-enter-from,
.zoom-leave-to {
  opacity: 0;
  transform: scale(0.96) translateY(8px);
}

/* 遮罩淡入/淡出 */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 200ms ease;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

@media (prefers-reduced-motion: reduce) {
  .sheet-enter-active,
  .sheet-leave-active,
  .zoom-enter-active,
  .zoom-leave-active,
  .fade-enter-active,
  .fade-leave-active {
    transition-duration: 1ms !important;
  }
  .chat-panel {
    transition: none;
  }
}

.slide-left-enter-active,
.slide-left-leave-active {
  transition: transform 0.2s ease;
}
.slide-left-enter-from,
.slide-left-leave-to {
  transform: translateX(-100%);
}
</style>
