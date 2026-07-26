<script setup lang="ts">
import { watch, onBeforeUnmount } from 'vue'
import { X } from 'lucide-vue-next'
import { gsap } from 'gsap'

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

// GSAP-powered enter/leave hooks
const isLeft = props.placement === 'left'
const xFrom = isLeft ? -60 : 60

function onBeforeEnter(el: Element) {
  const target = el as HTMLElement
  const backdrop = target.querySelector('.drawer-backdrop') as HTMLElement
  const panel = target.querySelector('.drawer-panel') as HTMLElement
  gsap.set(target, { autoAlpha: 0 })
  gsap.set(backdrop, { autoAlpha: 0 })
  gsap.set(panel, { autoAlpha: 0, x: xFrom })
}

function onEnter(el: Element, done: () => void) {
  const target = el as HTMLElement
  const backdrop = target.querySelector('.drawer-backdrop') as HTMLElement
  const panel = target.querySelector('.drawer-panel') as HTMLElement
  gsap.timeline({ onComplete: done })
    .to(target, { autoAlpha: 1, duration: 0.25, ease: 'power2.out' })
    .to(backdrop, { autoAlpha: 1, duration: 0.2, ease: 'power2.out' }, 0)
    .to(panel, {
      autoAlpha: 1,
      x: 0,
      duration: 0.35,
      ease: 'power3.out',
    }, 0)
}

function onLeave(el: Element, done: () => void) {
  const target = el as HTMLElement
  const backdrop = target.querySelector('.drawer-backdrop') as HTMLElement
  const panel = target.querySelector('.drawer-panel') as HTMLElement
  gsap.timeline({ onComplete: done })
    .to(panel, {
      autoAlpha: 0,
      x: xFrom,
      duration: 0.2,
      ease: 'power2.in',
    }, 0)
    .to([target, backdrop], { autoAlpha: 0, duration: 0.15, ease: 'power2.in' }, 0)
}
</script>

<template>
  <Teleport to="body">
    <Transition
      @before-enter="onBeforeEnter"
      @enter="onEnter"
      @leave="onLeave"
    >
      <div v-if="open" class="fixed inset-0 z-50 md:hidden">
        <!-- Backdrop -->
        <div
          class="drawer-backdrop absolute inset-0 bg-black/40 backdrop-blur-sm"
          @click="close"
        />

        <!-- Drawer panel -->
        <div
          :style="{ width }"
          class="drawer-panel absolute top-0 bottom-0 glass shadow-xl overflow-y-auto"
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
