<script setup lang="ts">
import { ref } from 'vue'
import { Loader2 } from 'lucide-vue-next'
import * as authApi from '@/api/auth'
import { validateEmail } from '@/utils/emailValidator'

const emit = defineEmits<{
  success: []
}>()

const username = ref('')
const email = ref('')
const realName = ref('')
const phoneNumber = ref('')
const password = ref('')
const confirmPassword = ref('')
const error = ref('')
const emailError = ref('')
const loading = ref(false)

function validateEmailField() {
  if (!email.value.trim()) {
    emailError.value = ''
    return
  }
  const result = validateEmail(email.value)
  emailError.value = result.valid ? '' : result.message
}

async function handleSubmit() {
  error.value = ''
  emailError.value = ''

  if (!username.value.trim() || !email.value.trim() || !password.value.trim() || !realName.value.trim() || !phoneNumber.value.trim()) {
    error.value = '请填写所有必填字段'
    return
  }

  const emailResult = validateEmail(email.value)
  if (!emailResult.valid) {
    emailError.value = emailResult.message
    return
  }

  if (password.value !== confirmPassword.value) {
    error.value = '两次输入的密码不一致'
    return
  }

  if (password.value.length < 6) {
    error.value = '密码至少 6 位'
    return
  }

  loading.value = true
  try {
    await authApi.register({
      username: username.value.trim(),
      password: password.value,
      email: email.value.trim(),
      realName: realName.value.trim(),
      phoneNumber: phoneNumber.value.trim()
    })
    emit('success')
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : '注册失败，请重试'
    error.value = msg
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

    <p class="text-xs text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)]">
      <span class="text-red-500">*</span> 表示必填项
    </p>

    <div>
      <label for="reg-username" class="block text-sm font-medium text-[var(--color-text-heading)] dark:text-[var(--color-dark-text-heading)] mb-1.5">
        用户名 <span class="text-red-500">*</span>
      </label>
      <input
        id="reg-username"
        v-model="username"
        type="text"
        autocomplete="username"
        required
        class="w-full px-3 py-2.5 rounded-lg border border-[var(--color-border)] dark:border-[var(--color-dark-border)] bg-[var(--color-bg-card)] dark:bg-[var(--color-dark-bg-card)] text-[var(--color-text-heading)] dark:text-[var(--color-dark-text-heading)] placeholder:text-[var(--color-text-muted)] dark:placeholder:text-[var(--color-dark-text-muted)] focus:outline-none focus:ring-2 focus:ring-[var(--color-accent)] dark:focus:ring-[var(--color-dark-accent)] focus:border-transparent transition-shadow text-sm"
        placeholder="请输入用户名"
      />
    </div>

    <div>
      <label for="reg-email" class="block text-sm font-medium text-[var(--color-text-heading)] dark:text-[var(--color-dark-text-heading)] mb-1.5">
        邮箱 <span class="text-red-500">*</span>
      </label>
      <input
        id="reg-email"
        v-model="email"
        type="email"
        autocomplete="email"
        required
        @blur="validateEmailField"
        class="w-full px-3 py-2.5 rounded-lg border bg-[var(--color-bg-card)] dark:bg-[var(--color-dark-bg-card)] text-[var(--color-text-heading)] dark:text-[var(--color-dark-text-heading)] placeholder:text-[var(--color-text-muted)] dark:placeholder:text-[var(--color-dark-text-muted)] focus:outline-none focus:ring-2 focus:border-transparent transition-shadow text-sm"
        :class="emailError
          ? 'border-red-400 dark:border-red-500 focus:ring-red-400 dark:focus:ring-red-500'
          : 'border-[var(--color-border)] dark:border-[var(--color-dark-border)] focus:ring-[var(--color-accent)] dark:focus:ring-[var(--color-dark-accent)]'"
        placeholder="请输入邮箱"
      />
      <p v-if="emailError" class="mt-1 text-xs text-red-500 dark:text-red-400">
        {{ emailError }}
      </p>
    </div>

    <div>
      <label for="reg-realname" class="block text-sm font-medium text-[var(--color-text-heading)] dark:text-[var(--color-dark-text-heading)] mb-1.5">
        真实姓名 <span class="text-red-500">*</span>
      </label>
      <input
        id="reg-realname"
        v-model="realName"
        type="text"
        autocomplete="name"
        required
        class="w-full px-3 py-2.5 rounded-lg border border-[var(--color-border)] dark:border-[var(--color-dark-border)] bg-[var(--color-bg-card)] dark:bg-[var(--color-dark-bg-card)] text-[var(--color-text-heading)] dark:text-[var(--color-dark-text-heading)] placeholder:text-[var(--color-text-muted)] dark:placeholder:text-[var(--color-dark-text-muted)] focus:outline-none focus:ring-2 focus:ring-[var(--color-accent)] dark:focus:ring-[var(--color-dark-accent)] focus:border-transparent transition-shadow text-sm"
        placeholder="请输入真实姓名"
      />
    </div>

    <div>
      <label for="reg-phone" class="block text-sm font-medium text-[var(--color-text-heading)] dark:text-[var(--color-dark-text-heading)] mb-1.5">
        手机号 <span class="text-red-500">*</span>
      </label>
      <input
        id="reg-phone"
        v-model="phoneNumber"
        type="tel"
        autocomplete="tel"
        required
        class="w-full px-3 py-2.5 rounded-lg border border-[var(--color-border)] dark:border-[var(--color-dark-border)] bg-[var(--color-bg-card)] dark:bg-[var(--color-dark-bg-card)] text-[var(--color-text-heading)] dark:text-[var(--color-dark-text-heading)] placeholder:text-[var(--color-text-muted)] dark:placeholder:text-[var(--color-dark-text-muted)] focus:outline-none focus:ring-2 focus:ring-[var(--color-accent)] dark:focus:ring-[var(--color-dark-accent)] focus:border-transparent transition-shadow text-sm"
        placeholder="请输入手机号"
      />
    </div>

    <div>
      <label for="reg-password" class="block text-sm font-medium text-[var(--color-text-heading)] dark:text-[var(--color-dark-text-heading)] mb-1.5">
        密码 <span class="text-red-500">*</span>
      </label>
      <input
        id="reg-password"
        v-model="password"
        type="password"
        autocomplete="new-password"
        required
        class="w-full px-3 py-2.5 rounded-lg border border-[var(--color-border)] dark:border-[var(--color-dark-border)] bg-[var(--color-bg-card)] dark:bg-[var(--color-dark-bg-card)] text-[var(--color-text-heading)] dark:text-[var(--color-dark-text-heading)] placeholder:text-[var(--color-text-muted)] dark:placeholder:text-[var(--color-dark-text-muted)] focus:outline-none focus:ring-2 focus:ring-[var(--color-accent)] dark:focus:ring-[var(--color-dark-accent)] focus:border-transparent transition-shadow text-sm"
        placeholder="至少 6 位"
      />
    </div>

    <div>
      <label for="reg-confirm" class="block text-sm font-medium text-[var(--color-text-heading)] dark:text-[var(--color-dark-text-heading)] mb-1.5">
        确认密码 <span class="text-red-500">*</span>
      </label>
      <input
        id="reg-confirm"
        v-model="confirmPassword"
        type="password"
        autocomplete="new-password"
        required
        class="w-full px-3 py-2.5 rounded-lg border border-[var(--color-border)] dark:border-[var(--color-dark-border)] bg-[var(--color-bg-card)] dark:bg-[var(--color-dark-bg-card)] text-[var(--color-text-heading)] dark:text-[var(--color-dark-text-heading)] placeholder:text-[var(--color-text-muted)] dark:placeholder:text-[var(--color-dark-text-muted)] focus:outline-none focus:ring-2 focus:ring-[var(--color-accent)] dark:focus:ring-[var(--color-dark-accent)] focus:border-transparent transition-shadow text-sm"
        placeholder="再次输入密码"
      />
    </div>

    <button
      type="submit"
      :disabled="loading"
      class="w-full py-2.5 px-4 rounded-lg bg-[var(--color-accent)] dark:bg-[var(--color-dark-accent)] text-[var(--color-bg-card)] dark:text-[var(--color-dark-bg-card)] font-medium text-sm hover:opacity-90 transition-opacity disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center gap-2"
    >
      <Loader2 v-if="loading" :size="16" class="animate-spin" />
      {{ loading ? '注册中...' : '注册' }}
    </button>
  </form>
</template>
