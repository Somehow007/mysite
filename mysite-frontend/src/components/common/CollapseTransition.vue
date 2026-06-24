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
    <div class="collapse-inner">
      <slot />
    </div>
  </div>
</template>

<style scoped>
.collapse-wrapper {
  display: grid;
  grid-template-rows: 1fr;
  transition:
    grid-template-rows 250ms cubic-bezier(0.4, 0, 0.2, 1),
    opacity 200ms cubic-bezier(0.4, 0, 0.2, 1);
  transition-delay: var(--collapse-delay, 0ms);
}

.collapse-inner {
  overflow: hidden;
  min-height: 0;
}

.collapse-wrapper.collapsed {
  grid-template-rows: 0fr;
  opacity: 0;
  transition-delay: var(--collapse-delay, 0ms);
}
</style>
