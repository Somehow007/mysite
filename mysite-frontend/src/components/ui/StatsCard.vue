<script setup lang="ts">
import { computed } from 'vue'
import type { Component } from 'vue'

type SemanticColor = 'accent' | 'success' | 'warning' | 'danger' | 'info'

const props = withDefaults(defineProps<{
  icon?: Component
  label: string
  value: number | string
  /** @deprecated 保留兼容，等价于 iconColor（default 映射为 accent） */
  accent?: 'default' | 'success' | 'warning' | 'danger' | 'info'
  /** 图标色块语义色，优先级高于 accent */
  iconColor?: SemanticColor
  /** 数值着色，等价于 status */
  color?: 'success' | 'warning' | 'danger' | 'accent'
  /** 数值着色，等价于 color */
  status?: 'success' | 'warning' | 'danger' | 'accent'
}>(), {})

const iconClasses: Record<SemanticColor, string> = {
  accent: 'bg-accent-subtle text-accent',
  success: 'bg-success-subtle text-success',
  warning: 'bg-warning-subtle text-warning',
  danger: 'bg-danger-subtle text-danger',
  info: 'bg-info-subtle text-info',
}

const resolvedIconColor = computed<SemanticColor>(() => {
  if (props.iconColor) return props.iconColor
  if (props.accent && props.accent !== 'default') return props.accent
  return 'accent'
})

const valueClass = computed(() => {
  const c = props.color || props.status
  const map: Record<string, string> = {
    success: 'text-success',
    warning: 'text-warning',
    danger: 'text-danger',
    accent: 'text-accent',
  }
  return (c && map[c]) || 'text-text-primary'
})
</script>

<template>
  <div class="card-solid flex items-start gap-3.5 px-5 py-4 transition-all duration-200">
    <div
      v-if="icon"
      class="flex items-center justify-center w-10 h-10 rounded-[10px] shrink-0"
      :class="iconClasses[resolvedIconColor]"
    >
      <component :is="icon" :size="18" />
    </div>
    <div class="min-w-0">
      <div class="text-[12.5px] text-text-secondary">{{ label }}</div>
      <div class="text-2xl md:text-[26px] font-bold tabular-nums leading-tight mt-0.5" :class="valueClass">{{ value }}</div>
      <div v-if="$slots.trend" class="text-xs mt-1">
        <slot name="trend" />
      </div>
    </div>
  </div>
</template>
