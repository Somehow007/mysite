<script setup lang="ts">
import { ref } from 'vue'
import { Loader2 } from 'lucide-vue-next'
import * as authApi from '@/api/auth'
import { useFormValidation } from '@/composables/useFormValidation'

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
const loading = ref(false)

const {
  validateRequired,
  validateEmailField: validateEmail,
  validatePhoneField: validatePhone,
  validateMinLength,
  validateMatch,
  onFieldBlur,
  getFieldError,
  isFieldInvalid,
  clearAll,
  setFieldTouched,
} = useFormValidation()

function handleEmailBlur() {
  setFieldTouched('email')
  if (email.value.trim()) {
    validateEmail('email', email.value)
  }
}

function handlePhoneBlur() {
  setFieldTouched('phoneNumber')
  if (phoneNumber.value.trim()) {
    validatePhone('phoneNumber', phoneNumber.value)
  }
}

function handlePhoneInput() {
  if (isFieldInvalid('phoneNumber') || getFieldError('phoneNumber')) {
    validatePhone('phoneNumber', phoneNumber.value)
  }
}

function handleConfirmBlur() {
  setFieldTouched('confirmPassword')
  if (confirmPassword.value) {
    validateMatch('confirmPassword', password.value, confirmPassword.value, '两次输入的密码不一致')
  }
}

async function handleSubmit() {
  error.value = ''
  clearAll()

  const fields: [string, string, string][] = [
    ['username', username.value, '用户名'],
    ['realName', realName.value, '真实姓名'],
    ['password', password.value, '密码'],
    ['phoneNumber', phoneNumber.value, '手机号'],
    ['email', email.value, '邮箱'],
  ]

  for (const [field, value, label] of fields) {
    setFieldTouched(field)
    if (!validateRequired(field, value, label)) {
      error.value = '请填写所有必填字段'
      return
    }
  }

  if (!validateEmail('email', email.value)) return
  if (!validatePhone('phoneNumber', phoneNumber.value)) return
  if (!validateMinLength('password', password.value, 6, '密码')) return
  if (!validateMatch('confirmPassword', password.value, confirmPassword.value, '两次输入的密码不一致')) return

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

const inputBaseClass = 'w-full px-3 py-2.5 rounded-lg border bg-[var(--color-bg-card)] dark:bg-[var(--color-dark-bg-card)] text-[var(--color-text-heading)] dark:text-[var(--color-dark-text-heading)] placeholder:text-[var(--color-text-muted)] dark:placeholder:text-[var(--color-dark-text-muted)] focus:outline-none focus:ring-2 focus:border-transparent transition-shadow text-sm'
const inputNormalClass = `${inputBaseClass} border-[var(--color-border)] dark:border-[var(--color-dark-border)] focus:ring-[var(--color-accent)] dark:focus:ring-[var(--color-dark-accent)]`
const inputErrorClass = `${inputBaseClass} border-red-400 dark:border-red-500 focus:ring-red-400 dark:focus:ring-red-500`
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
        :class="inputNormalClass"
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
        @blur="handleEmailBlur"
        :class="isFieldInvalid('email') ? inputErrorClass : inputNormalClass"
        placeholder="请输入邮箱"
      />
      <p v-if="getFieldError('email')" class="mt-1 text-xs text-red-500 dark:text-red-400">
        {{ getFieldError('email') }}
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
        :class="inputNormalClass"
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
        @blur="handlePhoneBlur"
        @input="handlePhoneInput"
        :class="isFieldInvalid('phoneNumber') ? inputErrorClass : inputNormalClass"
        placeholder="请输入手机号"
        maxlength="11"
      />
      <p v-if="getFieldError('phoneNumber')" class="mt-1 text-xs text-red-500 dark:text-red-400">
        {{ getFieldError('phoneNumber') }}
      </p>
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
        :class="isFieldInvalid('password') ? inputErrorClass : inputNormalClass"
        placeholder="至少 6 位"
      />
      <p v-if="getFieldError('password')" class="mt-1 text-xs text-red-500 dark:text-red-400">
        {{ getFieldError('password') }}
      </p>
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
        @blur="handleConfirmBlur"
        :class="isFieldInvalid('confirmPassword') ? inputErrorClass : inputNormalClass"
        placeholder="再次输入密码"
      />
      <p v-if="getFieldError('confirmPassword')" class="mt-1 text-xs text-red-500 dark:text-red-400">
        {{ getFieldError('confirmPassword') }}
      </p>
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
