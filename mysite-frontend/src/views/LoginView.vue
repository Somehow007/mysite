<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import LoginForm from '@/components/auth/LoginForm.vue'
import { useUserStore } from '@/stores/user'
import { getRedirectUrl, getRedirectUrlFromQuery, setRedirectUrl, clearRedirectUrl } from '@/utils/redirect'
import { getItem } from '@/utils/storage'

const router = useRouter()
const userStore = useUserStore()

onMounted(async () => {
  const token = getItem<string>('access_token')

  if (!token) {
    const queryRedirect = getRedirectUrlFromQuery()
    if (queryRedirect) {
      setRedirectUrl(queryRedirect)
    }
    return
  }

  if (!userStore.user) {
    try {
      await userStore.fetchCurrentUser()
    } catch {
      const queryRedirect = getRedirectUrlFromQuery()
      if (queryRedirect) {
        setRedirectUrl(queryRedirect)
      }
      return
    }
  }

  const redirectUrl = getRedirectUrl() || '/'
  clearRedirectUrl()
  router.replace(redirectUrl)
})

async function onLoginSuccess() {
  await userStore.fetchCurrentUser()
  const redirect = getRedirectUrl() || '/'
  clearRedirectUrl()
  router.replace(redirect)
}
</script>

<template>
  <div class="min-h-[80vh] flex items-center justify-center px-4">
    <div class="w-full max-w-sm">
      <div class="text-center mb-8">
        <h1 class="text-2xl font-semibold text-text-primary">
          登录
        </h1>
        <p class="mt-2 text-sm text-text-muted">
          登录你的账户以继续
        </p>
      </div>

      <div class="glass glass-sm rounded-xl p-6">
        <LoginForm @success="onLoginSuccess" />
      </div>

      <p class="mt-6 text-center text-sm text-text-muted">
        还没有账户？
        <RouterLink to="/register" class="text-accent hover:opacity-80 transition-opacity font-medium">
          注册
        </RouterLink>
      </p>
    </div>
  </div>
</template>
