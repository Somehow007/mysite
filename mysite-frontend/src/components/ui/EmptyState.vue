<script setup lang="ts">
import { Inbox } from 'lucide-vue-next'

withDefaults(defineProps<{
  icon?: any
  title?: string
  description?: string
  actionLabel?: string
}>(), {
  icon: () => Inbox,
  title: '暂无数据',
  description: '',
})

defineEmits<{
  action: []
}>()
</script>

<template>
  <div class="flex flex-col items-center justify-center py-16 px-4 text-center">
    <div class="w-16 h-16 rounded-2xl bg-bg-code flex items-center justify-center mb-4">
      <component :is="icon" :size="28" class="text-text-muted" />
    </div>
    <h3 class="text-base font-medium text-text-primary mb-1">{{ title }}</h3>
    <p v-if="description" class="text-sm text-text-muted max-w-sm mb-6">{{ description }}</p>
    <button
      v-if="actionLabel"
      class="btn-primary"
      @click="$emit('action')"
    >
      {{ actionLabel }}
    </button>
  </div>
</template>
