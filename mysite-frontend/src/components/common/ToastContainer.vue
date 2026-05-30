<script setup lang="ts">
import { useToast } from '@/composables/useToast'
import { CheckCircle, XCircle, Info, X } from 'lucide-vue-next'

const { toasts, remove } = useToast()

function icon(type: 'success' | 'error' | 'info') {
  switch (type) {
    case 'success': return CheckCircle
    case 'error': return XCircle
    default: return Info
  }
}

function bgClass(type: 'success' | 'error' | 'info') {
  switch (type) {
    case 'success':
      return 'bg-green-50 border-green-200 text-green-800'
    case 'error':
      return 'bg-red-50 border-red-200 text-red-800'
    default:
      return 'bg-blue-50 border-blue-200 text-blue-800'
  }
}
</script>

<template>
  <Teleport to="body">
    <div class="fixed top-4 right-4 z-[100] flex flex-col gap-2 max-w-sm w-full pointer-events-none">
      <TransitionGroup name="toast-slide">
        <div
          v-for="toast in toasts"
          :key="toast.id"
          class="pointer-events-auto glass glass-sm flex items-start gap-3 px-4 py-3 rounded-lg"
          :class="bgClass(toast.type)"
        >
          <component :is="icon(toast.type)" :size="18" class="shrink-0 mt-0.5" />
          <span class="text-sm flex-1">{{ toast.message }}</span>
          <button
            @click="remove(toast.id)"
            class="shrink-0 opacity-60 hover:opacity-100 transition-opacity"
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
