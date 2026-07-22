<script setup lang="ts">
import { ref, nextTick, watch, onScopeDispose } from 'vue'
import { Sparkles, X, RotateCw } from 'lucide-vue-next'
import { useChat } from '@/composables/useChat'
import ChatMessageItem from './ChatMessageItem.vue'
import ChatInput from './ChatInput.vue'

const SUGGESTED_QUESTIONS = [
  '介绍一下这个博客',
  '最近发布了哪些文章？',
  'Spring Security 怎么配置 JWT？',
]

const isOpen = ref(false)
const messagesContainer = ref<HTMLElement | null>(null)
const inputRef = ref<InstanceType<typeof ChatInput> | null>(null)

const chat = useChat()
const isUserScrolledUp = ref(false)

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

function handleEsc(e: KeyboardEvent) {
  if (e.key === 'Escape' && isOpen.value) {
    close()
  }
}

function open() {
  isOpen.value = true
  document.addEventListener('keydown', handleEsc)
  nextTick(() => scrollToBottom())
}

function close() {
  isOpen.value = false
  document.removeEventListener('keydown', handleEsc)
}

onScopeDispose(() => {
  document.removeEventListener('keydown', handleEsc)
  chat.cancelGeneration()
})
</script>

<template>
  <!-- FAB 按钮 -->
  <button
    v-if="!isOpen"
    class="fixed bottom-20 right-6 z-40 w-14 h-14
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
        class="relative w-full sm:w-[380px] h-full sm:h-[560px] sm:max-h-[70vh]
               glass glass-lg rounded-none sm:rounded-2xl
               border border-glass-border
               flex flex-col overflow-hidden
               animate-scale-in"
      >
        <!-- Header -->
        <div class="flex items-center justify-between px-4 py-3 border-b border-border shrink-0">
          <div class="flex items-center gap-2">
            <Sparkles :size="16" class="text-accent" />
            <span class="text-sm font-medium text-text-primary">AI 助手</span>
          </div>
          <div class="flex items-center gap-1.5">
            <button
              class="p-1.5 rounded-lg text-text-muted hover:text-text-primary
                     hover:bg-bg-secondary transition-colors"
              aria-label="新会话"
              title="新会话"
              @click="chat.newConversation()"
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

        <!-- 消息列表 -->
        <div
          ref="messagesContainer"
          class="flex-1 overflow-y-auto px-4 py-4 scrollbar-thin"
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

          <!-- 错误 toast（不渲染气泡的错误） -->
          <div
            v-if="chat.lastError.value && chat.messages.value.length > 0"
            class="flex items-center justify-center my-2"
          >
            <span class="text-xs text-text-muted bg-bg-elevated px-3 py-1 rounded-full">
              {{ chat.lastError.value.message }}
            </span>
          </div>
        </div>

        <!-- 输入框 -->
        <ChatInput
          :streaming="chat.isStreaming.value"
          @send="chat.sendMessage"
          @cancel="chat.cancelGeneration()"
        />
      </div>
    </div>
  </Teleport>
</template>
