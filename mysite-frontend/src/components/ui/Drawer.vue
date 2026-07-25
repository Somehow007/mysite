<script setup lang="ts">
import { watch, onBeforeUnmount } from 'vue'
import { X } from 'lucide-vue-next'

const props = withDefaults(defineProps<{
  open: boolean
  placement?: 'left' | 'right'
  width?: string
}>(), {
  placement: 'left',
  width: '280px',
})

const emit = defineEmits<{
  'update:open': [value: boolean]
}>()

function close() {
  emit('update:open', false)
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
    <Transition name="drawer">
      <div v-if="open" class="fixed inset-0 z-50 md:hidden">
        <!-- Backdrop -->
        <div
          class="absolute inset-0 bg-black/40 backdrop-blur-sm"
          @click="close"
        />

        <!-- Drawer panel -->
        <div
          :style="{ width }"
          class="absolute top-0 bottom-0 glass shadow-xl overflow-y-auto"
          :class="placement === 'left' ? 'left-0' : 'right-0'"
        >
          <!-- Close button -->
          <div class="sticky top-0 flex justify-end p-3 z-10">
            <button
              @click="close"
              class="p-2 rounded-lg hover:bg-bg-code text-text-muted hover:text-text-primary transition-colors"
            >
              <X :size="18" />
            </button>
          </div>

          <slot />
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.drawer-enter-active {
  transition: all 250ms var(--ease-out);
}
.drawer-leave-active {
  transition: all 200ms var(--ease-smooth);
}
.drawer-enter-from {
  opacity: 0;
}
.drawer-enter-from > div:last-child {
  transform: translateX(-100%);
}
.drawer-leave-to {
  opacity: 0;
}
.drawer-leave-to > div:last-child {
  transform: translateX(-100%);
}
</style>
