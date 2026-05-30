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
</script>

<template>
  <form @submit.prevent="handleSubmit" class="space-y-5">
    <div v-if="error" class="p-3 rounded-lg bg-red-50 text-red-600 text-sm">
      {{ error }}
    </div>

    <p class="text-xs text-text-muted">
      <span class="text-red-500">*</span> 表示必填项
    </p>

    <div>
      <label for="reg-username" class="block text-sm font-medium text-text-primary mb-1.5">
        用户名 <span class="text-red-500">*</span>
      </label>
      <input
        id="reg-username"
        v-model="username"
        type="text"
        autocomplete="username"
        required
        class="input-base"
        placeholder="请输入用户名"
      />
    </div>

    <div>
      <label for="reg-email" class="block text-sm font-medium text-text-primary mb-1.5">
        邮箱 <span class="text-red-500">*</span>
      </label>
      <input
        id="reg-email"
        v-model="email"
        type="email"
        autocomplete="email"
        required
        @blur="handleEmailBlur"
        class="input-base"
        :class="{ 'border-red-400 focus:ring-red-400': isFieldInvalid('email') }"
        placeholder="请输入邮箱"
      />
      <p v-if="getFieldError('email')" class="mt-1 text-xs text-red-500">
        {{ getFieldError('email') }}
      </p>
    </div>

    <div>
      <label for="reg-realname" class="block text-sm font-medium text-text-primary mb-1.5">
        真实姓名 <span class="text-red-500">*</span>
      </label>
      <input
        id="reg-realname"
        v-model="realName"
        type="text"
        autocomplete="name"
        required
        class="input-base"
        placeholder="请输入真实姓名"
      />
    </div>

    <div>
      <label for="reg-phone" class="block text-sm font-medium text-text-primary mb-1.5">
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
        class="input-base"
        :class="{ 'border-red-400 focus:ring-red-400': isFieldInvalid('phoneNumber') }"
        placeholder="请输入手机号"
        maxlength="11"
      />
      <p v-if="getFieldError('phoneNumber')" class="mt-1 text-xs text-red-500">
        {{ getFieldError('phoneNumber') }}
      </p>
    </div>

    <div>
      <label for="reg-password" class="block text-sm font-medium text-text-primary mb-1.5">
        密码 <span class="text-red-500">*</span>
      </label>
      <input
        id="reg-password"
        v-model="password"
        type="password"
        autocomplete="new-password"
        required
        class="input-base"
        :class="{ 'border-red-400 focus:ring-red-400': isFieldInvalid('password') }"
        placeholder="至少 6 位"
      />
      <p v-if="getFieldError('password')" class="mt-1 text-xs text-red-500">
        {{ getFieldError('password') }}
      </p>
    </div>

    <div>
      <label for="reg-confirm" class="block text-sm font-medium text-text-primary mb-1.5">
        确认密码 <span class="text-red-500">*</span>
      </label>
      <input
        id="reg-confirm"
        v-model="confirmPassword"
        type="password"
        autocomplete="new-password"
        required
        @blur="handleConfirmBlur"
        class="input-base"
        :class="{ 'border-red-400 focus:ring-red-400': isFieldInvalid('confirmPassword') }"
        placeholder="再次输入密码"
      />
      <p v-if="getFieldError('confirmPassword')" class="mt-1 text-xs text-red-500">
        {{ getFieldError('confirmPassword') }}
      </p>
    </div>

    <button
      type="submit"
      :disabled="loading"
      class="btn-primary w-full py-2.5 disabled:opacity-50 disabled:cursor-not-allowed"
    >
      <Loader2 v-if="loading" :size="16" class="animate-spin" />
      {{ loading ? '注册中...' : '注册' }}
    </button>
  </form>
</template>
