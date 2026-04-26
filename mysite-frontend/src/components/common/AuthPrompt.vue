<script setup lang="ts">
import { LogIn } from 'lucide-vue-next'
import { setRedirectUrl } from '@/utils/redirect'
import { useRoute } from 'vue-router'

const props = defineProps<{
  action?: string
}>()

const route = useRoute()
const actionText = props.action || '进行此操作'

function handleLoginClick() {
  setRedirectUrl(route.fullPath)
}
</script>

<template>
  <div class="flex flex-col items-center justify-center py-12 px-4 text-center">
    <div class="w-14 h-14 rounded-full bg-[var(--color-bg-code)] dark:bg-[var(--color-dark-bg-code)] flex items-center justify-center mb-4">
      <LogIn :size="24" class="text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)]" />
    </div>
    <p class="text-[var(--color-text-heading)] dark:text-[var(--color-dark-text-heading)] font-medium mb-2">
      需要登录才能{{ actionText }}
    </p>
    <p class="text-sm text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)] mb-6 max-w-xs">
      登录后即可享受完整功能，包括发布文章、评论互动等
    </p>
    <RouterLink
      to="/login"
      @click="handleLoginClick"
      class="inline-flex items-center gap-2 px-5 py-2.5 rounded-lg bg-[var(--color-accent)] dark:bg-[var(--color-dark-accent)] text-[var(--color-bg-card)] dark:text-[var(--color-dark-bg-card)] font-medium text-sm hover:opacity-90 transition-opacity"
    >
      <LogIn :size="16" />
      立即登录
    </RouterLink>
  </div>
</template>
