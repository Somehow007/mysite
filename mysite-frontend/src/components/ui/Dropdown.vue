<script setup lang="ts">
import { ref, computed, watch, onBeforeUnmount } from 'vue'
import { useFloating, offset, flip, shift, autoUpdate } from '@floating-ui/vue'

const props = withDefaults(defineProps<{
  /** 触发方式：click | hover | manual */
  trigger?: 'click' | 'hover' | 'manual'
  /** 弹出方向 */
  placement?: 'bottom-start' | 'bottom-end' | 'top-start' | 'top-end' | 'right-start'
  /** 偏移量 (px) */
  spacing?: number
  /** 手动控制时绑定 v-model:open */
  open?: boolean
  /** 内容宽度是否跟随触发器 */
  matchWidth?: boolean
}>(), {
  trigger: 'click',
  placement: 'bottom-end',
  spacing: 8,
  open: undefined,
  matchWidth: false,
})

const emit = defineEmits<{
  'update:open': [value: boolean]
}>()

const isManual = computed(() => props.trigger === 'manual')
const isOpen = ref(false)

// v-model support for manual trigger
const controlledOpen = computed({
  get: () => isManual.value ? (props.open ?? false) : isOpen.value,
  set: (val: boolean) => {
    isOpen.value = val
    if (isManual.value) emit('update:open', val)
  },
})

const triggerRef = ref<HTMLElement | null>(null)
const floatingRef = ref<HTMLElement | null>(null)

// eslint-disable-next-line @typescript-eslint/no-explicit-any
const { floatingStyles } = useFloating(triggerRef as any, floatingRef as any, {
  placement: props.placement as any,
  middleware: [
    offset(props.spacing),
    flip({ padding: 8 }),
    shift({ padding: 8 }),
  ] as any,
  whileElementsMounted: autoUpdate,
})

// ── Open / Close ──────────────────────────────────────

function open() {
  controlledOpen.value = true
}
function close() {
  controlledOpen.value = false
}
function toggle() {
  controlledOpen.value = !controlledOpen.value
}

// ── Click outside ─────────────────────────────────────

function handleClickOutside(e: MouseEvent) {
  if (!controlledOpen.value) return
  const target = e.target as HTMLElement
  const trigger = triggerRef.value
  const floating = floatingRef.value
  if (!floating) return
  // Click on trigger for click-mode is handled separately
  if (trigger && trigger.contains(target)) return
  if (!floating.contains(target)) close()
}

// ── ESC ────────────────────────────────────────────────

function handleEsc(e: KeyboardEvent) {
  if (e.key === 'Escape' && controlledOpen.value) {
    e.preventDefault()
    close()
  }
}

// ── Hover handlers ────────────────────────────────────

let hoverTimer: ReturnType<typeof setTimeout> | null = null
function handleTriggerEnter() {
  if (props.trigger !== 'hover') return
  if (hoverTimer) clearTimeout(hoverTimer)
  open()
}
function handleTriggerLeave() {
  if (props.trigger !== 'hover') return
  hoverTimer = setTimeout(close, 200)
}
function handleContentEnter() {
  if (props.trigger !== 'hover') return
  if (hoverTimer) clearTimeout(hoverTimer)
}
function handleContentLeave() {
  if (props.trigger !== 'hover') return
  hoverTimer = setTimeout(close, 150)
}

// ── Lifecycle ──────────────────────────────────────────

watch(controlledOpen, (val) => {
  if (val) {
    document.addEventListener('click', handleClickOutside, true)
    document.addEventListener('keydown', handleEsc)
  } else {
    document.removeEventListener('click', handleClickOutside, true)
    document.removeEventListener('keydown', handleEsc)
  }
})

onBeforeUnmount(() => {
  document.removeEventListener('click', handleClickOutside, true)
  document.removeEventListener('keydown', handleEsc)
  if (hoverTimer) clearTimeout(hoverTimer)
})
</script>

<template>
  <div class="dropdown-root" style="position: relative;">
    <!-- Trigger -->
    <div
      ref="triggerRef"
      class="dropdown-trigger"
      @click="trigger === 'click' ? toggle() : undefined"
      @mouseenter="handleTriggerEnter"
      @mouseleave="handleTriggerLeave"
    >
      <slot name="trigger" :open="controlledOpen" :close="close" />
    </div>

    <!-- Floating panel -->
    <Teleport to="body">
      <Transition name="dropdown-fade">
        <div
          v-if="controlledOpen"
          ref="floatingRef"
          :style="{ ...floatingStyles, zIndex: 9999, minWidth: matchWidth && triggerRef ? `${triggerRef.offsetWidth}px` : undefined }"
          class="dropdown-panel"
          @mouseenter="handleContentEnter"
          @mouseleave="handleContentLeave"
        >
          <slot :close="close" />
        </div>
      </Transition>
    </Teleport>
  </div>
</template>

<style scoped>
.dropdown-root {
  display: inline-flex;
}
.dropdown-trigger {
  display: inline-flex;
  cursor: pointer;
}
.dropdown-panel {
  /* base styling left to consumer; animation handled here */
}
.dropdown-fade-enter-active {
  transition: opacity 0.2s cubic-bezier(0.16, 1, 0.3, 1), transform 0.2s cubic-bezier(0.16, 1, 0.3, 1);
}
.dropdown-fade-leave-active {
  transition: opacity 0.15s ease-in, transform 0.15s ease-in;
}
.dropdown-fade-enter-from {
  opacity: 0;
  transform: scale(0.95) translateY(-6px);
}
.dropdown-fade-leave-to {
  opacity: 0;
  transform: scale(0.97) translateY(-2px);
}
</style>
