<script setup lang="ts">
defineProps<{
  lines?: number
  variant?: 'card' | 'text' | 'article'
}>()
</script>

<template>
  <div v-if="variant === 'article'" class="animate-pulse space-y-6">
    <div class="skeleton h-8 w-3/4 rounded" />
    <div class="flex gap-4">
      <div class="skeleton h-4 w-24 rounded" />
      <div class="skeleton h-4 w-20 rounded" />
      <div class="skeleton h-4 w-16 rounded" />
    </div>
    <div class="skeleton h-64 w-full rounded-lg" />
    <div class="space-y-2">
      <div class="skeleton h-4 w-full rounded" />
      <div class="skeleton h-4 w-5/6 rounded" />
      <div class="skeleton h-4 w-4/5 rounded" />
    </div>
  </div>

  <div v-else-if="variant === 'text'" class="animate-pulse space-y-2">
    <div class="skeleton h-4 w-full rounded" />
    <div class="skeleton h-4 w-5/6 rounded" />
    <div class="skeleton h-4 w-4/5 rounded" />
    <div v-for="i in (lines || 0)" :key="i" class="skeleton h-4 w-full rounded" />
  </div>

  <div v-else class="animate-pulse">
    <div class="skeleton h-5 w-3/4 rounded mb-3" />
    <div class="skeleton h-3 w-full rounded mb-2" />
    <div class="skeleton h-3 w-5/6 rounded mb-2" />
    <div v-for="i in (lines || 0)" :key="i" class="skeleton h-3 w-full rounded mb-2" />
    <div class="flex gap-4 mt-4">
      <div class="skeleton h-3 w-20 rounded" />
      <div class="skeleton h-3 w-16 rounded" />
    </div>
  </div>
</template>
