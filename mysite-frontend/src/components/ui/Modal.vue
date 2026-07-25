<script setup lang="ts">
import { watch, onBeforeUnmount } from 'vue'
import { X } from 'lucide-vue-next'

const props = withDefaults(defineProps<{
  open: boolean
  title?: string
  description?: string
  maxWidth?: string
  closable?: boolean
}>(), {
  maxWidth: 'max-w-lg',
  closable: true,
})

const emit = defineEmits<{
  'update:open': [value: boolean]
  close: []
}>()

function close() {
  if (!props.closable) return
  emit('update:open', false)
  emit('close')
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape') close()
}

watch(() => props.open, (val) => {
  if (val) {
    document.addEventListener('keydown', onKeydown)
    document.body.style.overflow = 'hidden'
  } else {
    document.removeEventListener('keydown', onKeydown)
    document.body.style.overflow = ''
  }
}, { immediate: true })

onBeforeUnmount(() => {
  document.removeEventListener('keydown', onKeydown)
  document.body.style.overflow = ''
})
</script>

<template>
  <Teleport to="body">
    <Transition name="modal">
      <div
        v-if="open"
        class="fixed inset-0 z-[60] flex items-center justify-center p-4"
        @click.self="close"
      >
        <!-- Backdrop -->
        <div class="absolute inset-0 bg-black/40 backdrop-blur-sm" />

        <!-- Panel -->
        <div
          :class="maxWidth"
          class="relative w-full glass rounded-xl shadow-lg max-h-[85vh] flex flex-col"
        >
          <!-- Header -->
          <div v-if="title || closable" class="flex items-center justify-between px-6 pt-5 pb-0">
            <div>
              <h2 v-if="title" class="text-lg font-semibold text-text-primary">{{ title }}</h2>
              <p v-if="description" class="text-sm text-text-muted mt-1">{{ description }}</p>
            </div>
            <button
              v-if="closable"
              @click="close"
              class="p-1.5 rounded-lg hover:bg-bg-code text-text-muted hover:text-text-primary transition-colors"
            >
              <X :size="18" />
            </button>
          </div>

          <!-- Body -->
          <div class="px-6 py-5 overflow-y-auto">
            <slot />
          </div>

          <!-- Footer -->
          <div v-if="$slots.footer" class="px-6 pb-5 pt-0 flex items-center justify-end gap-3">
            <slot name="footer" />
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.modal-enter-active {
  transition: all 200ms var(--ease-out);
}
.modal-leave-active {
  transition: all 150ms var(--ease-smooth);
}
.modal-enter-from {
  opacity: 0;
}
.modal-enter-from > div:last-child {
  opacity: 0;
  transform: scale(0.95) translateY(8px);
}
.modal-leave-to {
  opacity: 0;
}
.modal-leave-to > div:last-child {
  opacity: 0;
  transform: scale(0.95) translateY(8px);
}
</style>
