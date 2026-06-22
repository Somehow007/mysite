<script setup lang="ts">
defineProps<{
  show: boolean
  delay?: number
}>()
</script>

<template>
  <div
    class="collapse-wrapper"
    :class="{ collapsed: !show }"
    :style="delay ? { '--collapse-delay': `${delay}ms` } : undefined"
  >
    <slot />
  </div>
</template>

<style scoped>
.collapse-wrapper {
  overflow: hidden;
  max-height: 500px;
  opacity: 1;
  transform: translateY(0);
  transition:
    max-height 250ms cubic-bezier(0.4, 0, 0.2, 1),
    opacity 200ms cubic-bezier(0.4, 0, 0.2, 1),
    transform 200ms cubic-bezier(0.4, 0, 0.2, 1);
  transition-delay: var(--collapse-delay, 0ms);
}

.collapse-wrapper.collapsed {
  max-height: 0;
  opacity: 0;
  transform: translateY(-4px);
  transition-delay: var(--collapse-delay, 0ms);
}
</style>
