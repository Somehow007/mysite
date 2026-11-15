<script setup lang="ts">
import { ref, computed, nextTick } from 'vue'
import { ArrowUp, Square, X } from 'lucide-vue-next'
import { useUserStore } from '@/stores/user'
import { usePermission } from '@/composables/usePermission'

const props = defineProps<{
  streaming: boolean
  /** 输入框上方的引用条（launcher.open({ quote }) 注入），发送时由父级拼进问题 */
  quote?: string | null
}>()

const emit = defineEmits<{
  send: [question: string]
  cancel: []
  'clear-quote': []
}>()

const MAX_LENGTH = 500
const userStore = useUserStore()
const { isAdmin } = usePermission()

const input = ref('')
const textareaRef = ref<HTMLTextAreaElement | null>(null)

const charCount = computed(() => input.value.length)
const overLimit = computed(() => charCount.value > MAX_LENGTH)
const canSend = computed(() => input.value.trim().length > 0 && !overLimit.value && !props.streaming)

// Quota text
const quotaText = computed(() => {
  if (isAdmin.value) return '无限额度'
  const role = userStore.user?.role
  if (role === 'CREATOR') return '剩余额度 20 次/小时'
  return userStore.isLoggedIn ? '剩余额度 10 次/小时' : '游客额度 10 次/小时'
})

function handleSend() {
  if (props.streaming) {
    emit('cancel')
    return
  }

  const q = input.value.trim()
  if (!q || overLimit.value) return

  if (canSend.value) {
    emit('send', q)
    input.value = ''
    nextTick(() => {
      if (textareaRef.value) {
        textareaRef.value.style.height = 'auto'
      }
    })
  }
}

function handleKeydown(e: KeyboardEvent) {
  if (e.isComposing) return
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    handleSend()
  }
}

function autoResize() {
  const el = textareaRef.value
  if (el) {
    el.style.height = 'auto'
    el.style.height = Math.min(el.scrollHeight, 120) + 'px'
  }
}

/** 供 ChatWidget 通过 launcher 预填问题；光标落到末尾（不自动聚焦，移动端避免弹键盘） */
function setInput(text: string) {
  input.value = text
  nextTick(() => {
    autoResize()
    const el = textareaRef.value
    if (el) el.setSelectionRange(text.length, text.length)
  })
}

function focus() {
  textareaRef.value?.focus()
}

defineExpose({ setInput, focus })
</script>

<template>
  <!-- 底部留白取 max(常规间距, safe-area)：iPhone home indicator 机型不被遮挡
       （main.css 的 body safe-area padding 对 fixed 弹层无效，需在此单独处理） -->
  <div class="border-t border-border px-3 pt-3 pb-[max(0.75rem,env(safe-area-inset-bottom))] shrink-0">
    <!-- 引用条：launcher.open({ quote }) 注入，发送时父级拼成「> 引用\n\n问题」 -->
    <div
      v-if="quote"
      class="mb-2 flex items-start gap-2 border-l-2 border-accent bg-bg-secondary px-2.5 py-1.5"
    >
      <span class="min-w-0 flex-1 whitespace-pre-line text-xs leading-relaxed text-text-secondary line-clamp-2">
        {{ quote }}
      </span>
      <button
        type="button"
        class="shrink-0 text-text-muted transition-colors hover:text-text-primary"
        aria-label="删除引用"
        @click="emit('clear-quote')"
      >
        <X :size="13" />
      </button>
    </div>

    <!-- 输入行：999px 胶囊 -->
    <div
      class="flex items-end gap-2 px-3 py-1.5 rounded-full border border-border bg-bg-secondary transition-all duration-200"
      :class="overLimit ? '!border-danger' : 'focus-within:border-accent focus-within:shadow-[0_0_0_3px_rgba(79,70,229,0.14)]'"
    >
      <!-- 移动端字号 16px（text-base）：iOS 对 < 16px 的输入框聚焦时会自动缩放页面；
           桌面端恢复 14px。enterkeyhint 让软键盘回车键显示"发送" -->
      <textarea
        ref="textareaRef"
        v-model="input"
        :maxlength="MAX_LENGTH + 50"
        rows="1"
        enterkeyhint="send"
        class="flex-1 resize-none bg-transparent text-base sm:text-sm text-text-primary placeholder:text-text-muted/60 outline-none max-h-[120px] py-1.5"
        :class="overLimit ? 'text-danger' : ''"
        placeholder="输入你的问题..."
        :disabled="streaming"
        @keydown="handleKeydown"
        @input="autoResize"
      />
      <button
        class="shrink-0 w-9 h-9 rounded-full flex items-center justify-center transition-all duration-200"
        :class="streaming
          ? 'bg-danger text-white hover:opacity-90'
          : canSend
            ? 'bg-accent text-white shadow-[0_4px_12px_-4px_rgba(79,70,229,0.45)] hover:scale-105 hover:shadow-[0_6px_16px_-4px_rgba(79,70,229,0.55)]'
            : 'bg-bg-code text-text-muted cursor-not-allowed'"
        :disabled="!canSend && !streaming"
        :aria-label="streaming ? '停止生成' : '发送消息'"
        @click="handleSend"
      >
        <Square v-if="streaming" :size="14" />
        <ArrowUp v-else :size="16" />
      </button>
    </div>

    <!-- 底部提示行 -->
    <div class="flex items-center justify-between mt-2 px-2">
      <p class="text-[11px] text-text-muted/70 flex items-center gap-1.5">
        <span class="w-1.5 h-1.5 rounded-full bg-success" />
        {{ quotaText }}
      </p>
      <span
        class="text-[11px]"
        :class="overLimit ? 'text-danger' : 'text-text-muted/60'"
      >
        {{ charCount }}/{{ MAX_LENGTH }}
      </span>
    </div>
  </div>
</template>
