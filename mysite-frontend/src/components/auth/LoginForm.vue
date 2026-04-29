<script setup lang="ts">
import { ref } from 'vue'
import { Loader2 } from 'lucide-vue-next'
import { useUserStore } from '@/stores/user'
import * as authApi from '@/api/auth'

const emit = defineEmits<{
  success: []
}>()

const userStore = useUserStore()

const username = ref('')
const password = ref('')
const error = ref('')
const loading = ref(false)

async function handleSubmit() {
  error.value = ''

  if (!username.value.trim() || !password.value.trim()) {
    error.value = '请输入用户名和密码'
    return
  }

  loading.value = true
  try {
    const tokens = await authApi.login({
      username: username.value.trim(),
      password: password.value,
    })
    userStore.setTokens(tokens.accessToken, tokens.refreshToken)
    emit('success')
  } catch (e: unknown) {
    if (e instanceof Error) {
      error.value = e.message
    } else {
      error.value = '登录失败，请重试'
    }
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <form @submit.prevent="handleSubmit" class="space-y-5">
    <div v-if="error" class="p-3 rounded-lg bg-red-50 dark:bg-red-900/20 text-red-600 dark:text-red-400 text-sm">
      {{ error }}
    </div>

    <div>
      <label for="login-username" class="block text-sm font-medium text-[var(--color-text-heading)] dark:text-[var(--color-dark-text-heading)] mb-1.5">
        用户名
      </label>
      <input
        id="login-username"
        v-model="username"
        type="text"
        autocomplete="username"
        required
        class="input-base"
        placeholder="请输入用户名"
      />
    </div>

    <div>
      <label for="login-password" class="block text-sm font-medium text-[var(--color-text-heading)] dark:text-[var(--color-dark-text-heading)] mb-1.5">
        密码
      </label>
      <input
        id="login-password"
        v-model="password"
        type="password"
        autocomplete="current-password"
        required
        class="input-base"
        placeholder="请输入密码"
      />
    </div>

    <button
      type="submit"
      :disabled="loading"
      class="btn-primary w-full py-2.5 disabled:opacity-50 disabled:cursor-not-allowed"
    >
      <Loader2 v-if="loading" :size="16" class="animate-spin" />
      {{ loading ? '登录中...' : '登录' }}
    </button>
  </form>
</template>
