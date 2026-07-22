<script setup lang="ts">
import { ref, computed, nextTick } from 'vue'
import { ArrowUp, Square } from 'lucide-vue-next'

const props = defineProps<{
  streaming: boolean
}>()

const emit = defineEmits<{
  send: [question: string]
  cancel: []
}>()

const MAX_LENGTH = 500

const input = ref('')
const textareaRef = ref<HTMLTextAreaElement | null>(null)

const charCount = computed(() => input.value.length)
const overLimit = computed(() => charCount.value > MAX_LENGTH)
const canSend = computed(() => input.value.trim().length > 0 && !overLimit.value && !props.streaming)

function handleSend() {
  // 流式中：停止按钮优先，不受输入框内容影响
  if (props.streaming) {
    emit('cancel')
    return
  }

  const q = input.value.trim()
  if (!q || overLimit.value) return

  if (canSend.value) {
    emit('send', q)
    input.value = ''
    // 重置 textarea 高度
    nextTick(() => {
      if (textareaRef.value) {
        textareaRef.value.style.height = 'auto'
      }
    })
  }
}

function handleKeydown(e: KeyboardEvent) {
  // IME 组合输入中（拼音未确认）：不拦截 Enter，交给输入法处理候选词选择
  if (e.isComposing) return
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    handleSend()
  }
}

// 自适应高度
function autoResize() {
  const el = textareaRef.value
  if (el) {
    el.style.height = 'auto'
    el.style.height = Math.min(el.scrollHeight, 120) + 'px'
  }
}
</script>

<template>
  <div class="border-t border-border p-3">
    <div class="flex items-end gap-2">
      <textarea
        ref="textareaRef"
        v-model="input"
        :maxlength="MAX_LENGTH + 50"
        rows="1"
        class="flex-1 resize-none bg-transparent text-sm text-text-primary placeholder:text-text-muted/60 outline-none max-h-[120px]"
        :class="overLimit ? 'text-red-500' : ''"
        placeholder="输入你的问题..."
        :disabled="streaming"
        @keydown="handleKeydown"
        @input="autoResize"
      />
      <button
        class="shrink-0 w-8 h-8 rounded-full flex items-center justify-center transition-all duration-200"
        :class="streaming
          ? 'bg-red-500 text-white hover:bg-red-600'
          : canSend
            ? 'bg-accent text-white hover:bg-accent-hover'
            : 'bg-bg-code text-text-muted cursor-not-allowed'"
        :disabled="!canSend && !streaming"
        :aria-label="streaming ? '停止生成' : '发送消息'"
        @click="handleSend"
      >
        <Square v-if="streaming" :size="14" />
        <ArrowUp v-else :size="16" />
      </button>
    </div>
    <div class="flex items-center justify-between mt-2">
      <p class="text-[11px] text-text-muted/60">
        由博客内容驱动 · 回答仅供参考
      </p>
      <span
        class="text-[11px]"
        :class="overLimit ? 'text-red-500' : 'text-text-muted/60'"
      >
        {{ charCount }}/{{ MAX_LENGTH }}
      </span>
    </div>
  </div>
</template>
