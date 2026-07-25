<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{
  status: 'success' | 'warning' | 'danger' | 'info' | 'muted'
  pulse?: boolean
}>(), {
  pulse: false,
})

const dotClass = computed(() => {
  const colorMap: Record<string, string> = {
    success: 'bg-success',
    warning: 'bg-warning',
    danger: 'bg-danger',
    info: 'bg-info',
    muted: 'bg-text-muted',
  }
  return colorMap[props.status]
})
</script>

<template>
  <span class="relative inline-flex shrink-0">
    <span :class="dotClass" class="w-2 h-2 rounded-full" />
    <span
      v-if="pulse"
      :class="dotClass"
      class="absolute inset-0 w-2 h-2 rounded-full animate-ping opacity-75"
    />
  </span>
</template>
