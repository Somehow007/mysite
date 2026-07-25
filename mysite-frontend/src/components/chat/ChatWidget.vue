<script setup lang="ts">
import { ref, nextTick, watch, onScopeDispose, onBeforeUnmount, computed } from 'vue'
import { useRouter } from 'vue-router'
import { Sparkles, X, RotateCw, PanelLeftOpen, AlertTriangle, Clock, LogIn, Bot } from 'lucide-vue-next'
import { useChat } from '@/composables/useChat'
import { useFabStack } from '@/composables/useFabStack'
import { getVisitorId } from '@/api/rag'
import { useUserStore } from '@/stores/user'
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

function handleRenameConversation(_id: string, _title: string) {
  chat.loadConversations(getVisitorId())
}

// Rate limit countdown
const rateLimitSeconds = ref(0)
let rateLimitTimer: ReturnType<typeof setInterval> | null = null

watch(() => chat.lastError.value, (err) => {
  if (err && err.kind === 'http' && (err as any).status === 429) {
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

// ── Phase 6：歧义引导选择 ──
function handleSelectIntent(intentId: string) {
  if (!chat.lastQuestion.value) return
  chat.sendWithIntent(chat.lastQuestion.value, intentId)
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
  // Only prevent scroll on mobile (sm breakpoint and below)
  const isMobile = window.innerWidth < 640
  document.body.style.overflow = (open && isMobile) ? 'hidden' : ''
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
      class="fixed inset-0 z-50 flex items-end sm:items-end justify-end
             p-0 sm:p-0"
    >
      <!-- 桌面端：仅面板，无全屏遮罩 -->
      <!-- 移动端遮罩 -->
      <div
        class="absolute inset-0 bg-black/40 sm:hidden"
        @click="close"
      />

      <!-- 面板主体：桌面端锚定右下角 -->
      <div
        class="relative w-full h-full rounded-none
               sm:fixed sm:right-10 sm:bottom-10
               sm:w-[520px] sm:h-[680px] sm:max-h-[calc(100vh-80px)] sm:rounded-2xl
               bg-bg-elevated border border-border
               flex flex-col overflow-hidden
               sm:shadow-[0_24px_64px_-12px_rgba(17,24,39,0.22),0_8px_24px_-8px_rgba(79,70,229,0.12)]
               animate-scale-in"
      >
        <!-- Header：60px -->
        <div class="flex items-center gap-2.5 px-3.5 shrink-0 border-b border-border"
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
              <span class="w-1.5 h-1.5 rounded-full bg-success shrink-0" />
              基于站内知识库
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

        <!-- 内容区（含侧栏） -->
        <div class="flex-1 relative overflow-hidden">

          <!-- 历史侧栏 236px 抽屉 + 遮罩 -->
          <Transition name="slide-left">
            <div
              v-if="showHistory"
              class="absolute inset-y-0 left-0 z-10 bg-bg-elevated border-r border-border flex flex-col shadow-lg"
              style="width: 236px;"
            >
              <div class="flex items-center justify-between px-3 py-2.5 border-b border-border shrink-0">
                <span class="text-xs font-semibold text-text-secondary">历史对话 · {{ chat.conversations.value.length }} 条</span>
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
                @deleted="handleDeleteConversation"
                @renamed="handleRenameConversation"
              />
            </div>
          </Transition>

          <!-- 侧栏打开时的半透明遮罩 -->
          <div
            v-if="showHistory"
            class="absolute inset-0 bg-black/15 z-[5]"
            @click="showHistory = false"
          />

          <!-- 消息列表 -->
          <div
            ref="messagesContainer"
            class="h-full overflow-y-auto px-4 py-4 scrollbar-thin"
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
              @select-intent="handleSelectIntent"
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
