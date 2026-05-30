<script setup lang="ts">
import { useRouter, useRoute } from 'vue-router'
import RegisterForm from '@/components/auth/RegisterForm.vue'

const router = useRouter()
const route = useRoute()

function onRegisterSuccess() {
  const redirect = route.query.redirect as string
  router.push(redirect ? `/login?redirect=${encodeURIComponent(redirect)}` : '/login')
}
</script>

<template>
  <div class="min-h-[80vh] flex items-center justify-center px-4">
    <div class="w-full max-w-sm">
      <div class="text-center mb-8">
        <h1 class="text-2xl font-semibold text-text-primary">
          注册
        </h1>
        <p class="mt-2 text-sm text-text-muted">
          创建一个新账户
        </p>
      </div>

      <div class="glass glass-sm rounded-xl p-6">
        <RegisterForm @success="onRegisterSuccess" />
      </div>

      <p class="mt-6 text-center text-sm text-text-muted">
        已有账户？
        <RouterLink to="/login" class="text-accent hover:opacity-80 transition-opacity font-medium">
          登录
        </RouterLink>
      </p>
    </div>
  </div>
</template>
