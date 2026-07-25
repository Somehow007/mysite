<script setup lang="ts">
import { ref, computed, nextTick } from 'vue'
import { ArrowUp, Square } from 'lucide-vue-next'
import { useUserStore } from '@/stores/user'
import { usePermission } from '@/composables/usePermission'

const props = defineProps<{
  streaming: boolean
}>()

const emit = defineEmits<{
  send: [question: string]
  cancel: []
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
</script>

<template>
  <div class="border-t border-border p-3 shrink-0">
    <!-- 输入行：999px 胶囊 -->
    <div
      class="flex items-end gap-2 px-3 py-1.5 rounded-full border border-border bg-bg-secondary transition-all duration-200"
      :class="overLimit ? '!border-danger' : 'focus-within:border-accent focus-within:shadow-[0_0_0_3px_rgba(79,70,229,0.14)]'"
    >
      <textarea
        ref="textareaRef"
        v-model="input"
        :maxlength="MAX_LENGTH + 50"
        rows="1"
        class="flex-1 resize-none bg-transparent text-sm text-text-primary placeholder:text-text-muted/60 outline-none max-h-[120px] py-1.5"
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
