<script setup lang="ts">
import { ref, nextTick, watch, onScopeDispose, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { Sparkles, X, RotateCw, PanelLeftOpen } from 'lucide-vue-next'
import { useChat } from '@/composables/useChat'
import { useFabStack } from '@/composables/useFabStack'
import { getVisitorId } from '@/api/rag'
import { useUserStore } from '@/stores/user'
import ChatMessageItem from './ChatMessageItem.vue'
import ChatInput from './ChatInput.vue'
import ChatHistory from './ChatHistory.vue'

const SUGGESTED_QUESTIONS = [
  '介绍一下这个博客',
  '最近发布了哪些文章？',
  'Spring Security 怎么配置 JWT？',
]

const router = useRouter()
const userStore = useUserStore()
const isOpen = ref(false)
const messagesContainer = ref<HTMLElement | null>(null)
const inputComponentRef = ref<InstanceType<typeof ChatInput> | null>(null)

const chat = useChat()
const isUserScrolledUp = ref(false)

// ── 历史侧栏 ────────────────────────────────────────────────
const showHistory = ref(false)

function toggleHistory() {
  showHistory.value = !showHistory.value
}

function handleSelectConversation(id: number) {
  chat.switchConversation(id)
  showHistory.value = false
}

function handleNewConversation() {
  chat.newConversation()
  showHistory.value = false
}

// 面板打开时加载历史对话列表
watch(isOpen, (open) => {
  if (open) {
    chat.loadConversations(getVisitorId())
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

/** ESC 关闭面板 */
function handleEsc(e: KeyboardEvent) {
  if (e.key === 'Escape' && isOpen.value) {
    e.preventDefault()
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
  // 未登录 → 跳转登录页
  if (!userStore.isLoggedIn) {
    router.push({ name: 'login' })
    return
  }
  isOpen.value = true
  nextTick(() => {
    const textarea = inputComponentRef.value?.$el?.querySelector?.('textarea') as HTMLTextAreaElement | null
    textarea?.focus()
    scrollToBottom()
  })
}

function close() {
  isOpen.value = false
}

// ── 移动端全屏时阻止 body 背景滚动 ──
watch(isOpen, (open) => {
  document.body.style.overflow = open ? 'hidden' : ''
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

  <!-- 面板 -->
  <Teleport to="body">
    <div
      v-if="isOpen"
      class="fixed inset-0 z-50 flex items-end sm:items-center justify-end sm:justify-end
             p-0 sm:p-4"
    >
      <!-- 背景遮罩 -->
      <div
        class="absolute inset-0 bg-black/40 backdrop-blur-sm
               sm:bg-black/30 sm:backdrop-blur-sm"
        @click="close"
      />

      <!-- 面板主体 -->
      <div
        class="relative w-full h-full rounded-none
               sm:w-[520px] sm:h-[680px] sm:max-h-[85vh] sm:rounded-2xl
               glass glass-lg border border-glass-border
               flex flex-col overflow-hidden
               animate-scale-in"
      >
        <!-- Header -->
        <div class="flex items-center justify-between px-4 py-3 border-b border-border shrink-0">
          <div class="flex items-center gap-2">
            <button
              class="p-1 rounded-lg text-text-muted hover:text-text-primary
                     hover:bg-bg-secondary transition-colors"
              :class="showHistory ? 'text-accent bg-accent/10' : ''"
              aria-label="历史对话"
              title="历史对话"
              @click="toggleHistory"
            >
              <PanelLeftOpen :size="16" />
            </button>
            <Sparkles :size="16" class="text-accent" />
            <span class="text-sm font-medium text-text-primary">AI 助手</span>
          </div>
          <div class="flex items-center gap-1.5">
            <button
              class="p-1.5 rounded-lg text-text-muted hover:text-text-primary
                     hover:bg-bg-secondary transition-colors"
              aria-label="新会话"
              title="新会话"
              @click="handleNewConversation"
            >
              <RotateCw :size="15" />
            </button>
            <button
              class="p-1.5 rounded-lg text-text-muted hover:text-text-primary
                     hover:bg-bg-secondary transition-colors"
              aria-label="关闭"
              @click="close"
            >
              <X :size="16" />
            </button>
          </div>
        </div>

        <!-- 内容区（含侧栏） -->
        <div class="flex-1 relative overflow-hidden">

          <!-- 历史侧栏 -->
          <Transition name="slide-left">
            <div
              v-if="showHistory"
              class="absolute inset-0 z-10 bg-bg-primary/95 backdrop-blur-sm
                     border-r border-border flex flex-col"
              style="width: 72%"
            >
              <div class="flex items-center justify-between px-3 py-2.5 border-b border-border shrink-0">
                <span class="text-xs font-medium text-text-secondary">历史对话</span>
                <button
                  class="p-0.5 rounded text-text-muted hover:text-text-primary transition-colors"
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
              />
            </div>
          </Transition>

          <!-- 消息列表 -->
          <div
            ref="messagesContainer"
            class="h-full overflow-y-auto px-4 py-4 scrollbar-thin"
            role="log"
            aria-live="polite"
            @scroll="handleScroll"
          >
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
            <div class="flex flex-wrap justify-center gap-2 max-w-[280px]">
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

          <!-- 错误 toast -->
          <div
            v-if="chat.lastError.value && chat.messages.value.length > 0"
            class="flex items-center justify-center my-2"
          >
            <span class="text-xs text-text-muted bg-bg-elevated px-3 py-1 rounded-full">
              {{ chat.lastError.value.message }}
            </span>
          </div>
        </div>

        </div>
        <!-- 内容区（含侧栏）结束 -->

        <!-- 输入框 -->
        <ChatInput
          ref="inputComponentRef"
          :streaming="chat.isStreaming.value"
          @send="chat.sendMessage"
          @cancel="chat.cancelGeneration()"
        />
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.slide-left-enter-active,
.slide-left-leave-active {
  transition: transform 0.2s ease;
}
.slide-left-enter-from,
.slide-left-leave-to {
  transform: translateX(-100%);
}
</style>
