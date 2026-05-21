<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useHead } from '@unhead/vue'
import { useUserStore } from '@/stores/user'
import { changePassword } from '@/api/auth'
import { Eye, EyeOff, User, Lock, Mail, Phone, UserCircle, Camera, Loader2 } from 'lucide-vue-next'
import type { ChangePasswordRequest } from '@/types'
import { uploadAvatar } from '@/api/user'

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
const avatarUploading = ref(false)

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

async function handleAvatarUpload(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  if (!file.type.startsWith('image/')) return
  avatarUploading.value = true
  try {
    await uploadAvatar(file)
    profileMessage.value = '头像更新成功'
  } catch {
    profileError.value = '头像上传失败，请重试'
  } finally {
    avatarUploading.value = false
    input.value = ''
  }
}
</script>

<template>
  <div class="max-w-2xl">
    <h1 class="text-2xl font-semibold text-[var(--color-text-heading)] dark:text-[var(--color-dark-text-heading)] mb-8">
      个人设置
    </h1>

    <div class="space-y-8">
      <section class="bg-[var(--color-bg-card)] dark:bg-[var(--color-dark-bg-card)] rounded-xl border border-[var(--color-border)] dark:border-[var(--color-dark-border)] p-6 card-shadow">
        <h2 class="text-lg font-medium text-[var(--color-text-heading)] dark:text-[var(--color-dark-text-heading)] mb-6 flex items-center gap-2">
          <User :size="20" class="text-[var(--color-accent)] dark:text-[var(--color-dark-accent)]" />
          个人资料
        </h2>

        <form @submit.prevent="handleUpdateProfile" class="space-y-4">
          <div>
            <label class="block text-sm font-medium text-[var(--color-text-body)] dark:text-[var(--color-dark-text-body)] mb-1.5">
              头像
            </label>
            <div class="flex items-center gap-4">
              <div class="relative group shrink-0">
                <div class="w-20 h-20 rounded-full bg-[var(--color-accent)] dark:bg-[var(--color-dark-accent)] text-white dark:text-[var(--color-dark-bg-primary)] flex items-center justify-center text-2xl font-medium overflow-hidden">
                  <img v-if="userStore.user?.avatar" :src="userStore.user.avatar" :alt="userStore.displayName" class="w-full h-full object-cover" />
                  <span v-else>{{ userStore.displayName?.charAt(0)?.toUpperCase() || 'U' }}</span>
                </div>
                <label class="absolute inset-0 rounded-full bg-black/40 flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity cursor-pointer">
                  <Loader2 v-if="avatarUploading" :size="20" class="animate-spin text-white" />
                  <Camera v-else :size="20" class="text-white" />
                  <input type="file" accept="image/*" class="hidden" @change="handleAvatarUpload" :disabled="avatarUploading" />
                </label>
              </div>
              <div class="text-sm text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)]">
                <p>点击头像更换</p>
                <p class="mt-1">支持 JPG、PNG 格式</p>
              </div>
            </div>
          </div>

          <div>
            <label class="block text-sm font-medium text-[var(--color-text-body)] dark:text-[var(--color-dark-text-body)] mb-1.5">
              昵称
            </label>
            <div class="relative">
              <UserCircle :size="16" class="absolute left-3 top-1/2 -translate-y-1/2 text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)] pointer-events-none" />
              <input
                v-model="profileForm.username"
                type="text"
                class="input-base pl-10"
                placeholder="输入昵称"
              />
            </div>
          </div>

          <div>
            <label class="block text-sm font-medium text-[var(--color-text-body)] dark:text-[var(--color-dark-text-body)] mb-1.5">
              真实姓名
            </label>
            <div class="relative">
              <User :size="16" class="absolute left-3 top-1/2 -translate-y-1/2 text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)] pointer-events-none" />
              <input
                v-model="profileForm.realName"
                type="text"
                class="input-base pl-10"
                placeholder="输入真实姓名"
              />
            </div>
          </div>

          <div>
            <label class="block text-sm font-medium text-[var(--color-text-body)] dark:text-[var(--color-dark-text-body)] mb-1.5">
              邮箱
            </label>
            <div class="relative">
              <Mail :size="16" class="absolute left-3 top-1/2 -translate-y-1/2 text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)] pointer-events-none" />
              <input
                v-model="profileForm.email"
                type="email"
                class="input-base pl-10"
                placeholder="输入邮箱"
              />
            </div>
          </div>

          <div>
            <label class="block text-sm font-medium text-[var(--color-text-body)] dark:text-[var(--color-dark-text-body)] mb-1.5">
              手机号
            </label>
            <div class="relative">
              <Phone :size="16" class="absolute left-3 top-1/2 -translate-y-1/2 text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)] pointer-events-none" />
              <input
                v-model="profileForm.phoneNumber"
                type="tel"
                class="input-base pl-10"
                placeholder="输入手机号"
              />
            </div>
          </div>

          <div>
            <label class="block text-sm font-medium text-[var(--color-text-body)] dark:text-[var(--color-dark-text-body)] mb-1.5">
              性别
            </label>
            <select v-model="profileForm.sex" class="input-base">
              <option :value="0">男</option>
              <option :value="1">女</option>
              <option :value="2">保密</option>
            </select>
          </div>

          <div v-if="profileMessage" class="text-sm text-green-600 dark:text-green-400 px-3 py-2 rounded-lg bg-green-50 dark:bg-green-900/20">
            {{ profileMessage }}
          </div>
          <div v-if="profileError" class="text-sm text-red-500 px-3 py-2 rounded-lg bg-red-50 dark:bg-red-900/20">
            {{ profileError }}
          </div>

          <div class="pt-2">
            <button
              type="submit"
              :disabled="profileLoading"
              class="btn-primary"
            >
              {{ profileLoading ? '保存中...' : '保存修改' }}
            </button>
          </div>
        </form>
      </section>

      <section class="bg-[var(--color-bg-card)] dark:bg-[var(--color-dark-bg-card)] rounded-xl border border-[var(--color-border)] dark:border-[var(--color-dark-border)] p-6 card-shadow">
        <h2 class="text-lg font-medium text-[var(--color-text-heading)] dark:text-[var(--color-dark-text-heading)] mb-6 flex items-center gap-2">
          <Lock :size="20" class="text-[var(--color-accent)] dark:text-[var(--color-dark-accent)]" />
          修改密码
        </h2>

        <form @submit.prevent="handleChangePassword" class="space-y-4">
          <div>
            <label class="block text-sm font-medium text-[var(--color-text-body)] dark:text-[var(--color-dark-text-body)] mb-1.5">
              旧密码
            </label>
            <div class="relative">
              <Lock :size="16" class="absolute left-3 top-1/2 -translate-y-1/2 text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)] pointer-events-none" />
              <input
                v-model="passwordForm.oldPassword"
                :type="showOldPassword ? 'text' : 'password'"
                class="input-base pl-10 pr-10"
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
              <Lock :size="16" class="absolute left-3 top-1/2 -translate-y-1/2 text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)] pointer-events-none" />
              <input
                v-model="passwordForm.newPassword"
                :type="showNewPassword ? 'text' : 'password'"
                class="input-base pl-10 pr-10"
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
              <Lock :size="16" class="absolute left-3 top-1/2 -translate-y-1/2 text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)] pointer-events-none" />
              <input
                v-model="confirmPassword"
                :type="showConfirmPassword ? 'text' : 'password'"
                class="input-base pl-10 pr-10"
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

          <div v-if="passwordMessage" class="text-sm text-green-600 dark:text-green-400 px-3 py-2 rounded-lg bg-green-50 dark:bg-green-900/20">
            {{ passwordMessage }}
          </div>
          <div v-if="passwordError" class="text-sm text-red-500 px-3 py-2 rounded-lg bg-red-50 dark:bg-red-900/20">
            {{ passwordError }}
          </div>

          <div class="pt-2">
            <button
              type="submit"
              :disabled="passwordLoading"
              class="btn-primary"
            >
              {{ passwordLoading ? '修改中...' : '修改密码' }}
            </button>
          </div>
        </form>
      </section>
    </div>
  </div>
</template>
