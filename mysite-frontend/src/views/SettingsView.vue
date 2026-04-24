<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useHead } from '@unhead/vue'
import { useUserStore } from '@/stores/user'
import { changePassword } from '@/api/auth'
import { Eye, EyeOff, User, Lock, Mail } from 'lucide-vue-next'
import type { ChangePasswordRequest } from '@/types'

useHead(() => ({
  title: '个人设置 - MySite',
}))

const userStore = useUserStore()

const profileForm = ref({
  username: '',
  realName: '',
  email: '',
  phoneNumber: '',
  sex: 0,
})

const passwordForm = ref<ChangePasswordRequest>({
  oldPassword: '',
  newPassword: '',
})

const confirmPassword = ref('')

const profileLoading = ref(false)
const passwordLoading = ref(false)
const profileMessage = ref('')
const passwordMessage = ref('')
const profileError = ref('')
const passwordError = ref('')

const showOldPassword = ref(false)
const showNewPassword = ref(false)
const showConfirmPassword = ref(false)

onMounted(() => {
  if (userStore.user) {
    profileForm.value = {
      username: userStore.user.username || '',
      realName: userStore.user.realName || '',
      email: userStore.user.email || '',
      phoneNumber: '',
      sex: userStore.user.sex || 0,
    }
  }
})

async function handleUpdateProfile() {
  profileLoading.value = true
  profileMessage.value = ''
  profileError.value = ''
  
  try {
    await userStore.updateUser({
      username: profileForm.value.username,
      realName: profileForm.value.realName,
      email: profileForm.value.email,
      sex: profileForm.value.sex,
    })
    profileMessage.value = '个人资料更新成功'
  } catch (err) {
    profileError.value = err instanceof Error ? err.message : '更新失败，请重试'
  } finally {
    profileLoading.value = false
  }
}

async function handleChangePassword() {
  passwordMessage.value = ''
  passwordError.value = ''
  
  if (passwordForm.value.newPassword !== confirmPassword.value) {
    passwordError.value = '两次输入的新密码不一致'
    return
  }
  
  if (passwordForm.value.newPassword.length < 6) {
    passwordError.value = '新密码长度至少为 6 位'
    return
  }
  
  passwordLoading.value = true
  
  try {
    await changePassword(passwordForm.value)
    passwordMessage.value = '密码修改成功'
    passwordForm.value = { oldPassword: '', newPassword: '' }
    confirmPassword.value = ''
  } catch (err) {
    passwordError.value = err instanceof Error ? err.message : '修改密码失败，请重试'
  } finally {
    passwordLoading.value = false
  }
}
</script>

<template>
  <div class="max-w-2xl">
    <h1 class="text-2xl font-semibold text-[var(--color-text-heading)] dark:text-[var(--color-dark-text-heading)] mb-8">
      个人设置
    </h1>

    <div class="space-y-8">
      <section class="bg-[var(--color-bg-card)] dark:bg-[var(--color-dark-bg-card)] rounded-xl border border-[var(--color-border)] dark:border-[var(--color-dark-border)] p-6">
        <h2 class="text-lg font-medium text-[var(--color-text-heading)] dark:text-[var(--color-dark-text-heading)] mb-6 flex items-center gap-2">
          <User :size="20" />
          个人资料
        </h2>

        <form @submit.prevent="handleUpdateProfile" class="space-y-4">
          <div>
            <label class="block text-sm font-medium text-[var(--color-text-body)] dark:text-[var(--color-dark-text-body)] mb-1.5">
              昵称
            </label>
            <input
              v-model="profileForm.username"
              type="text"
              class="w-full px-3 py-2 rounded-lg border border-[var(--color-border)] dark:border-[var(--color-dark-border)] bg-[var(--color-bg)] dark:bg-[var(--color-dark-bg)] text-[var(--color-text-body)] dark:text-[var(--color-dark-text-body)] focus:outline-none focus:ring-2 focus:ring-[var(--color-accent)] dark:focus:ring-[var(--color-dark-accent)] focus:border-transparent transition-colors"
              placeholder="输入昵称"
            />
          </div>

          <div>
            <label class="block text-sm font-medium text-[var(--color-text-body)] dark:text-[var(--color-dark-text-body)] mb-1.5">
              真实姓名
            </label>
            <input
              v-model="profileForm.realName"
              type="text"
              class="w-full px-3 py-2 rounded-lg border border-[var(--color-border)] dark:border-[var(--color-dark-border)] bg-[var(--color-bg)] dark:bg-[var(--color-dark-bg)] text-[var(--color-text-body)] dark:text-[var(--color-dark-text-body)] focus:outline-none focus:ring-2 focus:ring-[var(--color-accent)] dark:focus:ring-[var(--color-dark-accent)] focus:border-transparent transition-colors"
              placeholder="输入真实姓名"
            />
          </div>

          <div>
            <label class="block text-sm font-medium text-[var(--color-text-body)] dark:text-[var(--color-dark-text-body)] mb-1.5">
              邮箱
            </label>
            <div class="relative">
              <Mail :size="16" class="absolute left-3 top-1/2 -translate-y-1/2 text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)]" />
              <input
                v-model="profileForm.email"
                type="email"
                class="w-full pl-10 pr-3 py-2 rounded-lg border border-[var(--color-border)] dark:border-[var(--color-dark-border)] bg-[var(--color-bg)] dark:bg-[var(--color-dark-bg)] text-[var(--color-text-body)] dark:text-[var(--color-dark-text-body)] focus:outline-none focus:ring-2 focus:ring-[var(--color-accent)] dark:focus:ring-[var(--color-dark-accent)] focus:border-transparent transition-colors"
                placeholder="输入邮箱"
              />
            </div>
          </div>

          <div>
            <label class="block text-sm font-medium text-[var(--color-text-body)] dark:text-[var(--color-dark-text-body)] mb-1.5">
              性别
            </label>
            <select
              v-model="profileForm.sex"
              class="w-full px-3 py-2 rounded-lg border border-[var(--color-border)] dark:border-[var(--color-dark-border)] bg-[var(--color-bg)] dark:bg-[var(--color-dark-bg)] text-[var(--color-text-body)] dark:text-[var(--color-dark-text-body)] focus:outline-none focus:ring-2 focus:ring-[var(--color-accent)] dark:focus:ring-[var(--color-dark-accent)] focus:border-transparent transition-colors"
            >
              <option :value="0">男</option>
              <option :value="1">女</option>
              <option :value="2">保密</option>
            </select>
          </div>

          <div v-if="profileMessage" class="text-sm text-green-600 dark:text-green-400">
            {{ profileMessage }}
          </div>
          <div v-if="profileError" class="text-sm text-red-500">
            {{ profileError }}
          </div>

          <div class="pt-2">
            <button
              type="submit"
              :disabled="profileLoading"
              class="px-4 py-2 text-sm rounded-lg bg-[var(--color-accent)] dark:bg-[var(--color-dark-accent)] text-[var(--color-bg-card)] dark:text-[var(--color-dark-bg-card)] hover:opacity-90 transition-opacity disabled:opacity-50"
            >
              {{ profileLoading ? '保存中...' : '保存修改' }}
            </button>
          </div>
        </form>
      </section>

      <section class="bg-[var(--color-bg-card)] dark:bg-[var(--color-dark-bg-card)] rounded-xl border border-[var(--color-border)] dark:border-[var(--color-dark-border)] p-6">
        <h2 class="text-lg font-medium text-[var(--color-text-heading)] dark:text-[var(--color-dark-text-heading)] mb-6 flex items-center gap-2">
          <Lock :size="20" />
          修改密码
        </h2>

        <form @submit.prevent="handleChangePassword" class="space-y-4">
          <div>
            <label class="block text-sm font-medium text-[var(--color-text-body)] dark:text-[var(--color-dark-text-body)] mb-1.5">
              旧密码
            </label>
            <div class="relative">
              <input
                v-model="passwordForm.oldPassword"
                :type="showOldPassword ? 'text' : 'password'"
                class="w-full px-3 py-2 pr-10 rounded-lg border border-[var(--color-border)] dark:border-[var(--color-dark-border)] bg-[var(--color-bg)] dark:bg-[var(--color-dark-bg)] text-[var(--color-text-body)] dark:text-[var(--color-dark-text-body)] focus:outline-none focus:ring-2 focus:ring-[var(--color-accent)] dark:focus:ring-[var(--color-dark-accent)] focus:border-transparent transition-colors"
                placeholder="输入旧密码"
              />
              <button
                type="button"
                @click="showOldPassword = !showOldPassword"
                class="absolute right-3 top-1/2 -translate-y-1/2 text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)] hover:text-[var(--color-text-body)] dark:hover:text-[var(--color-dark-text-body)] transition-colors"
              >
                <Eye v-if="!showOldPassword" :size="16" />
                <EyeOff v-else :size="16" />
              </button>
            </div>
          </div>

          <div>
            <label class="block text-sm font-medium text-[var(--color-text-body)] dark:text-[var(--color-dark-text-body)] mb-1.5">
              新密码
            </label>
            <div class="relative">
              <input
                v-model="passwordForm.newPassword"
                :type="showNewPassword ? 'text' : 'password'"
                class="w-full px-3 py-2 pr-10 rounded-lg border border-[var(--color-border)] dark:border-[var(--color-dark-border)] bg-[var(--color-bg)] dark:bg-[var(--color-dark-bg)] text-[var(--color-text-body)] dark:text-[var(--color-dark-text-body)] focus:outline-none focus:ring-2 focus:ring-[var(--color-accent)] dark:focus:ring-[var(--color-dark-accent)] focus:border-transparent transition-colors"
                placeholder="输入新密码"
              />
              <button
                type="button"
                @click="showNewPassword = !showNewPassword"
                class="absolute right-3 top-1/2 -translate-y-1/2 text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)] hover:text-[var(--color-text-body)] dark:hover:text-[var(--color-dark-text-body)] transition-colors"
              >
                <Eye v-if="!showNewPassword" :size="16" />
                <EyeOff v-else :size="16" />
              </button>
            </div>
          </div>

          <div>
            <label class="block text-sm font-medium text-[var(--color-text-body)] dark:text-[var(--color-dark-text-body)] mb-1.5">
              确认新密码
            </label>
            <div class="relative">
              <input
                v-model="confirmPassword"
                :type="showConfirmPassword ? 'text' : 'password'"
                class="w-full px-3 py-2 pr-10 rounded-lg border border-[var(--color-border)] dark:border-[var(--color-dark-border)] bg-[var(--color-bg)] dark:bg-[var(--color-dark-bg)] text-[var(--color-text-body)] dark:text-[var(--color-dark-text-body)] focus:outline-none focus:ring-2 focus:ring-[var(--color-accent)] dark:focus:ring-[var(--color-dark-accent)] focus:border-transparent transition-colors"
                placeholder="再次输入新密码"
              />
              <button
                type="button"
                @click="showConfirmPassword = !showConfirmPassword"
                class="absolute right-3 top-1/2 -translate-y-1/2 text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)] hover:text-[var(--color-text-body)] dark:hover:text-[var(--color-dark-text-body)] transition-colors"
              >
                <Eye v-if="!showConfirmPassword" :size="16" />
                <EyeOff v-else :size="16" />
              </button>
            </div>
          </div>

          <div v-if="passwordMessage" class="text-sm text-green-600 dark:text-green-400">
            {{ passwordMessage }}
          </div>
          <div v-if="passwordError" class="text-sm text-red-500">
            {{ passwordError }}
          </div>

          <div class="pt-2">
            <button
              type="submit"
              :disabled="passwordLoading"
              class="px-4 py-2 text-sm rounded-lg bg-[var(--color-accent)] dark:bg-[var(--color-dark-accent)] text-[var(--color-bg-card)] dark:text-[var(--color-dark-bg-card)] hover:opacity-90 transition-opacity disabled:opacity-50"
            >
              {{ passwordLoading ? '修改中...' : '修改密码' }}
            </button>
          </div>
        </form>
      </section>
    </div>
  </div>
</template>
