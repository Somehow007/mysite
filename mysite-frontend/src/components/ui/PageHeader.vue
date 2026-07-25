<script setup lang="ts">
defineProps<{
  title: string
  subtitle?: string
}>()
</script>

<template>
  <div class="flex items-center justify-between gap-4 mb-6">
    <div class="min-w-0">
      <h1 class="text-xl md:text-2xl font-bold text-text-primary">{{ title }}</h1>
      <p v-if="subtitle" class="text-sm text-text-secondary mt-1">{{ subtitle }}</p>
    </div>
    <div v-if="$slots.actions" class="flex items-center gap-2 shrink-0">
      <slot name="actions" />
    </div>
  </div>
</template>
