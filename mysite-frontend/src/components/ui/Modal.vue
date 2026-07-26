<script setup lang="ts">
import { watch, onBeforeUnmount, ref } from 'vue'
import { X } from 'lucide-vue-next'
import { gsap } from 'gsap'

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

const panelRef = ref<HTMLElement | null>(null)

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

// GSAP-powered enter/leave hooks for Vue Transition
function onBeforeEnter(el: Element) {
  const target = el as HTMLElement
  const backdrop = target.querySelector('.modal-backdrop') as HTMLElement
  const panel = target.querySelector('.modal-panel') as HTMLElement
  gsap.set(target, { autoAlpha: 0 })
  gsap.set(backdrop, { autoAlpha: 0 })
  gsap.set(panel, { autoAlpha: 0, scale: 0.92, y: 16 })
}

function onEnter(el: Element, done: () => void) {
  const target = el as HTMLElement
  const backdrop = target.querySelector('.modal-backdrop') as HTMLElement
  const panel = target.querySelector('.modal-panel') as HTMLElement
  gsap.timeline({ onComplete: done })
    .to(target, { autoAlpha: 1, duration: 0.2, ease: 'power2.out' })
    .to(backdrop, { autoAlpha: 1, duration: 0.15, ease: 'power2.out' }, 0)
    .to(panel, {
      autoAlpha: 1,
      scale: 1,
      y: 0,
      duration: 0.35,
      ease: 'back.out(1.4)',
    }, 0.05)
}

function onLeave(el: Element, done: () => void) {
  const target = el as HTMLElement
  const backdrop = target.querySelector('.modal-backdrop') as HTMLElement
  const panel = target.querySelector('.modal-panel') as HTMLElement
  gsap.timeline({ onComplete: done })
    .to(panel, {
      autoAlpha: 0,
      scale: 0.95,
      y: 8,
      duration: 0.15,
      ease: 'power2.in',
    })
    .to([target, backdrop], { autoAlpha: 0, duration: 0.12, ease: 'power2.in' }, 0)
}
</script>

<template>
  <Teleport to="body">
    <Transition
      @before-enter="onBeforeEnter"
      @enter="onEnter"
      @leave="onLeave"
    >
      <div
        v-if="open"
        class="fixed inset-0 z-[60] flex items-center justify-center p-4"
        @click.self="close"
      >
        <!-- Backdrop -->
        <div class="modal-backdrop absolute inset-0 bg-black/40 backdrop-blur-sm" />

        <!-- Panel -->
        <div
          ref="panelRef"
          :class="maxWidth"
          class="modal-panel relative w-full glass rounded-xl shadow-lg max-h-[85vh] flex flex-col"
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
