<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{
  variant?: 'success' | 'warning' | 'danger' | 'info' | 'muted' | 'accent'
  size?: 'sm' | 'md'
  /** 在文字前显示 6px 语义色圆点 */
  dot?: boolean
  /** 圆点呼吸动画 */
  dotPulse?: boolean
}>(), {
  variant: 'muted',
  size: 'sm',
  dot: false,
  dotPulse: false,
})

const classes = computed(() => {
  const base = 'inline-flex items-center font-medium rounded-full'
  const gap = props.dot ? 'gap-1.5' : ''
  const sizeClasses = props.size === 'sm' ? 'px-2 py-0.5 text-xs' : 'px-2.5 py-1 text-sm'

  const variantMap: Record<string, string> = {
    success: 'bg-success-subtle text-success',
    warning: 'bg-warning-subtle text-warning',
    danger: 'bg-danger-subtle text-danger',
    info: 'bg-info-subtle text-info',
    muted: 'bg-bg-code text-text-muted',
    accent: 'bg-accent-subtle text-accent',
  }

  return `${base} ${gap} ${sizeClasses} ${variantMap[props.variant]}`
})

const dotClass = computed(() => {
  const colorMap: Record<string, string> = {
    success: 'bg-success',
    warning: 'bg-warning',
    danger: 'bg-danger',
    info: 'bg-info',
    muted: 'bg-text-muted',
    accent: 'bg-accent',
  }
  return colorMap[props.variant]
})
</script>

<template>
  <span :class="classes">
    <span v-if="dot" class="relative inline-flex shrink-0">
      <span :class="dotClass" class="w-1.5 h-1.5 rounded-full" />
      <span
        v-if="dotPulse"
        :class="dotClass"
        class="absolute inset-0 w-1.5 h-1.5 rounded-full animate-ping opacity-75"
      />
    </span>
    <slot />
  </span>
</template>
