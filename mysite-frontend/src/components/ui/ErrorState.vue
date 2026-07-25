<script setup lang="ts">
import { AlertCircle, RefreshCw } from 'lucide-vue-next'

withDefaults(defineProps<{
  title?: string
  message?: string
  retryLabel?: string
  loading?: boolean
}>(), {
  title: '加载失败',
  message: '请检查网络连接后重试',
  retryLabel: '重试',
  loading: false,
})

defineEmits<{
  retry: []
}>()
</script>

<template>
  <div class="flex flex-col items-center justify-center py-16 px-4 text-center">
    <div class="w-16 h-16 rounded-2xl bg-danger-subtle flex items-center justify-center mb-4">
      <AlertCircle :size="28" class="text-danger" />
    </div>
    <h3 class="text-base font-medium text-text-primary mb-1">{{ title }}</h3>
    <p class="text-sm text-text-muted max-w-sm mb-6">{{ message }}</p>
    <button
      class="btn-primary inline-flex items-center gap-2"
      :disabled="loading"
      @click="$emit('retry')"
    >
      <RefreshCw :size="16" :class="{ 'animate-spin': loading }" />
      {{ loading ? '重试中...' : retryLabel }}
    </button>
  </div>
</template>
