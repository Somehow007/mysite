<script setup lang="ts">
import { useRouter } from 'vue-router'
import { RouterLink } from 'vue-router'
import LoginForm from '@/components/auth/LoginForm.vue'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

async function onLoginSuccess() {
  await userStore.fetchCurrentUser()
  router.push('/')
}
</script>

<template>
  <div class="min-h-[80vh] flex items-center justify-center">
    <div class="w-full max-w-sm">
      <div class="text-center mb-8">
        <h1 class="text-2xl font-semibold text-[var(--color-text-heading)] dark:text-[var(--color-dark-text-heading)]">
          登录
        </h1>
        <p class="mt-2 text-sm text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)]">
          登录你的账户以继续
        </p>
      </div>

      <div class="bg-[var(--color-bg-card)] dark:bg-[var(--color-dark-bg-card)] rounded-xl border border-[var(--color-border)] dark:border-[var(--color-dark-border)] p-6">
        <LoginForm @success="onLoginSuccess" />
      </div>

      <p class="mt-6 text-center text-sm text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)]">
        还没有账户？
        <RouterLink to="/register" class="text-[var(--color-accent)] dark:text-[var(--color-dark-accent)] hover:opacity-70 transition-opacity">
          注册
        </RouterLink>
      </p>
    </div>
  </div>
</template>
