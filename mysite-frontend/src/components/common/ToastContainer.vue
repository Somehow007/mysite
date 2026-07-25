<script setup lang="ts">
import { useToast } from '@/composables/useToast'
import type { Toast } from '@/composables/useToast'
import { CheckCircle, XCircle, Info, X } from 'lucide-vue-next'

const { toasts, remove } = useToast()

function icon(type: Toast['type']) {
  switch (type) {
    case 'success': return CheckCircle
    case 'error': return XCircle
    default: return Info
  }
}

function iconClass(type: Toast['type']) {
  switch (type) {
    case 'success':
      return 'bg-success-subtle text-success'
    case 'error':
      return 'bg-danger-subtle text-danger'
    default:
      return 'bg-info-subtle text-info'
  }
}

function title(type: Toast['type']) {
  switch (type) {
    case 'success': return '成功'
    case 'error': return '错误'
    default: return '提示'
  }
}
</script>

<template>
  <Teleport to="body">
    <div class="fixed bottom-6 right-6 z-[100] flex flex-col gap-2.5 w-[340px] max-w-[calc(100vw-3rem)] pointer-events-none">
      <TransitionGroup name="toast-slide">
        <div
          v-for="toast in toasts"
          :key="toast.id"
          class="pointer-events-auto flex items-start gap-2.5 px-3.5 py-3 rounded-lg bg-bg-elevated border border-border shadow-lg"
          role="status"
        >
          <span
            class="flex items-center justify-center w-5 h-5 rounded-full shrink-0 mt-0.5"
            :class="iconClass(toast.type)"
          >
            <component :is="icon(toast.type)" :size="12" />
          </span>
          <div class="flex-1 min-w-0">
            <div class="text-[13.5px] font-semibold text-text-primary">{{ title(toast.type) }}</div>
            <div class="text-[12.5px] text-text-muted mt-0.5 break-words">{{ toast.message }}</div>
          </div>
          <button
            @click="remove(toast.id)"
            class="shrink-0 p-0.5 rounded text-text-muted hover:text-text-primary transition-colors"
            aria-label="关闭"
          >
            <X :size="14" />
          </button>
        </div>
      </TransitionGroup>
    </div>
  </Teleport>
</template>

<style scoped>
.toast-slide-enter-active {
  transition: all 0.3s ease-out;
}
.toast-slide-leave-active {
  transition: all 0.2s ease-in;
}
.toast-slide-enter-from {
  opacity: 0;
  transform: translateX(100%);
}
.toast-slide-leave-to {
  opacity: 0;
  transform: translateX(100%);
}
</style>
