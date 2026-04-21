<script setup lang="ts">
import { useRouter } from 'vue-router'
import { RouterLink } from 'vue-router'
import RegisterForm from '@/components/auth/RegisterForm.vue'

const router = useRouter()

function onRegisterSuccess() {
  router.push('/login')
}
</script>

<template>
  <div class="min-h-[80vh] flex items-center justify-center">
    <div class="w-full max-w-sm">
      <div class="text-center mb-8">
        <h1 class="text-2xl font-semibold text-[var(--color-text-heading)] dark:text-[var(--color-dark-text-heading)]">
          注册
        </h1>
        <p class="mt-2 text-sm text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)]">
          创建一个新账户
        </p>
      </div>

      <div class="bg-[var(--color-bg-card)] dark:bg-[var(--color-dark-bg-card)] rounded-xl border border-[var(--color-border)] dark:border-[var(--color-dark-border)] p-6">
        <RegisterForm @success="onRegisterSuccess" />
      </div>

      <p class="mt-6 text-center text-sm text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)]">
        已有账户？
        <RouterLink to="/login" class="text-[var(--color-accent)] dark:text-[var(--color-dark-accent)] hover:opacity-70 transition-opacity">
          登录
        </RouterLink>
      </p>
    </div>
  </div>
</template>
