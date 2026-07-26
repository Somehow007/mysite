<script setup lang="ts">
import { ref, reactive, onMounted, computed, watch } from 'vue'
import { useHead } from '@unhead/vue'
import { useUserStore } from '@/stores/user'
import { changePassword } from '@/api/auth'
import { useToast } from '@/composables/useToast'
import { Eye, EyeOff, User, Lock, Mail, Phone, UserCircle, Camera, Loader2, ShieldCheck, BadgeCheck, Laptop } from 'lucide-vue-next'
import { Badge } from '@/components/ui'
import type { ChangePasswordRequest } from '@/types'
import { uploadAvatar } from '@/api/user'

useHead(() => ({
  title: '个人设置 - MySite',
}))

const userStore = useUserStore()
const { success, error: toastError } = useToast()

const profileForm = reactive({
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
const profileError = ref('')
const passwordError = ref('')
const profileSuccess = ref('')
const passwordSuccess = ref('')

const showOldPassword = ref(false)
const showNewPassword = ref(false)
const showConfirmPassword = ref(false)
const avatarUploading = ref(false)

const originalProfile = ref({ username: '', realName: '', email: '', phoneNumber: '', sex: 0 })
const isDirty = computed(() =>
  profileForm.username !== originalProfile.value.username ||
  profileForm.realName !== originalProfile.value.realName ||
  profileForm.email !== originalProfile.value.email ||
  profileForm.phoneNumber !== originalProfile.value.phoneNumber ||
  profileForm.sex !== originalProfile.value.sex
)

// Email verification status (display only for now)
const emailVerified = computed(() => {
  // Future: backend should provide emailVerified field
  return !!userStore.user?.email
})

onMounted(() => {
  if (userStore.user) {
    const data = {
      username: userStore.user.username || '',
      realName: userStore.user.realName || '',
      email: userStore.user.email || '',
      phoneNumber: userStore.user.phoneNumber || '',
      sex: userStore.user.sex || 0,
    }
    Object.assign(profileForm, data)
    originalProfile.value = { ...data }
  }
})

// Dirty form warning on leave
window.addEventListener('beforeunload', (e) => {
  if (isDirty.value) {
    e.preventDefault()
  }
})

async function handleUpdateProfile() {
  profileLoading.value = true
  profileError.value = ''
  profileSuccess.value = ''

  try {
    await userStore.updateUser({
      username: profileForm.username,
      realName: profileForm.realName,
      email: profileForm.email,
      phoneNumber: profileForm.phoneNumber,
      sex: profileForm.sex,
    })
    originalProfile.value = {
      username: profileForm.username,
      realName: profileForm.realName,
      email: profileForm.email,
      phoneNumber: profileForm.phoneNumber,
      sex: profileForm.sex,
    }
    profileSuccess.value = '个人资料更新成功'
    success('个人资料已更新')
    setTimeout(() => { profileSuccess.value = '' }, 3000)
  } catch (err) {
    const msg = err instanceof Error ? err.message : '更新失败，请重试'
    profileError.value = msg
    toastError(msg)
  } finally {
    profileLoading.value = false
  }
}

async function handleChangePassword() {
  passwordSuccess.value = ''
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
    passwordSuccess.value = '密码修改成功'
    success('密码已修改')
    passwordForm.value = { oldPassword: '', newPassword: '' }
    confirmPassword.value = ''
    setTimeout(() => { passwordSuccess.value = '' }, 3000)
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
    success('头像更新成功')
  } catch {
    profileError.value = '头像上传失败，请重试'
  } finally {
    avatarUploading.value = false
    input.value = ''
  }
}
</script>

<template>
  <div class="max-w-4xl">
    <h1 class="text-2xl font-semibold text-text-primary mb-8">
      个人设置
    </h1>

    <div class="space-y-8">
      <section class="card-solid rounded-2xl p-7 md:p-8">
        <h2 class="text-lg font-medium text-text-primary mb-6 flex items-center gap-2">
          <User :size="20" class="text-accent" />
          个人资料
        </h2>

        <form @submit.prevent="handleUpdateProfile" class="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div class="md:col-span-2">
            <label class="block text-sm font-medium text-text-secondary mb-1.5">
              头像
            </label>
            <div class="flex items-center gap-4">
              <div class="w-16 h-16 rounded-full bg-gradient-to-br from-indigo-500 to-purple-600 text-text-inverse flex items-center justify-center text-xl font-medium overflow-hidden shrink-0 shadow-[0_0_0_3px_var(--bg-elevated),0_0_0_4px_var(--accent-subtle)]">
                <img v-if="userStore.user?.avatar" :src="userStore.user.avatar" :alt="userStore.displayName" class="w-full h-full object-cover" />
                <span v-else>{{ userStore.displayName?.charAt(0)?.toUpperCase() || 'U' }}</span>
              </div>
              <div class="flex flex-col gap-1">
                <label class="inline-flex w-fit items-center gap-1.5 px-3.5 py-1.5 rounded-lg border border-border text-[13px] font-medium text-text-secondary hover:border-text-muted hover:text-text-primary transition-colors cursor-pointer">
                  <Loader2 v-if="avatarUploading" :size="14" class="animate-spin" />
                  <Camera v-else :size="14" />
                  {{ avatarUploading ? '上传中...' : '更换头像' }}
                  <input type="file" accept="image/*" class="hidden" @change="handleAvatarUpload" :disabled="avatarUploading" />
                </label>
                <span class="text-xs text-text-muted">支持 JPG / PNG，不超过 2MB</span>
              </div>
            </div>
          </div>

          <div>
            <label class="block text-sm font-medium text-text-secondary mb-1.5">
              昵称
            </label>
            <div class="relative">
              <UserCircle :size="16" class="absolute left-3 top-1/2 -translate-y-1/2 text-text-muted pointer-events-none" />
              <input
                v-model="profileForm.username"
                type="text"
                class="input-base pl-10"
                placeholder="输入昵称"
              />
            </div>
          </div>

          <div>
            <label class="block text-sm font-medium text-text-secondary mb-1.5">
              真实姓名
            </label>
            <div class="relative">
              <User :size="16" class="absolute left-3 top-1/2 -translate-y-1/2 text-text-muted pointer-events-none" />
              <input
                v-model="profileForm.realName"
                type="text"
                class="input-base pl-10"
                placeholder="输入真实姓名"
              />
            </div>
          </div>

          <div>
            <label class="block text-sm font-medium text-text-secondary mb-1.5">
              邮箱
            </label>
            <div class="flex items-center gap-2.5">
              <div class="relative flex-1 min-w-0">
                <Mail :size="16" class="absolute left-3 top-1/2 -translate-y-1/2 text-text-muted pointer-events-none" />
                <input
                  v-model="profileForm.email"
                  type="email"
                  class="input-base pl-10"
                  placeholder="输入邮箱"
                />
              </div>
              <Badge :variant="emailVerified ? 'success' : 'warning'" dot>
                {{ emailVerified ? '已验证' : '未验证' }}
              </Badge>
            </div>
          </div>

          <!-- 手机号 -->
          <div>
            <label class="block text-sm font-medium text-text-secondary mb-1.5">
              手机号
            </label>
            <div class="flex items-center gap-2.5">
              <div class="relative flex-1 min-w-0">
                <Phone :size="16" class="absolute left-3 top-1/2 -translate-y-1/2 text-text-muted pointer-events-none" />
                <input
                  v-model="profileForm.phoneNumber"
                  type="tel"
                  class="input-base pl-10"
                  placeholder="输入手机号"
                />
              </div>
              <Badge :variant="profileForm.phoneNumber ? 'success' : 'warning'" dot>
                {{ profileForm.phoneNumber ? '已绑定' : '未绑定' }}
              </Badge>
            </div>
          </div>

          <div>
            <label class="block text-sm font-medium text-text-secondary mb-1.5">
              性别
            </label>
            <select v-model="profileForm.sex" class="input-base">
              <option :value="0">男</option>
              <option :value="1">女</option>
              <option :value="2">保密</option>
            </select>
          </div>

          <div v-if="profileSuccess" class="text-sm text-success px-3 py-2 rounded-lg bg-success-subtle flex items-center gap-2">
            <BadgeCheck :size="16" />
            {{ profileSuccess }}
          </div>
          <div v-if="profileError" class="text-sm text-danger px-3 py-2 rounded-lg bg-danger-subtle">
            {{ profileError }}
          </div>

          <div class="pt-2 flex items-center gap-3">
            <button
              type="submit"
              :disabled="profileLoading || !isDirty"
              class="btn-primary"
              :class="{ 'disabled:opacity-50': !isDirty }"
            >
              <Loader2 v-if="profileLoading" :size="14" class="animate-spin" />
              {{ profileLoading ? '保存中...' : '保存修改' }}
            </button>
            <span v-if="!isDirty && !profileLoading" class="text-xs text-text-muted">
              没有未保存的更改
            </span>
          </div>
        </form>
      </section>

      <section class="card-solid rounded-2xl p-7 md:p-8">
        <h2 class="text-lg font-medium text-text-primary mb-6 flex items-center gap-2">
          <ShieldCheck :size="20" class="text-accent" />
          账号安全
        </h2>

        <h3 class="text-sm font-medium text-text-secondary mb-4 flex items-center gap-2">
          <Lock :size="14" />
          修改密码
        </h3>

        <form @submit.prevent="handleChangePassword" class="space-y-4">
          <div>
            <label class="block text-sm font-medium text-text-secondary mb-1.5">
              旧密码
            </label>
            <div class="relative">
              <Lock :size="16" class="absolute left-3 top-1/2 -translate-y-1/2 text-text-muted pointer-events-none" />
              <input
                v-model="passwordForm.oldPassword"
                :type="showOldPassword ? 'text' : 'password'"
                class="input-base pl-10 pr-10"
                placeholder="输入旧密码"
              />
              <button
                type="button"
                @click="showOldPassword = !showOldPassword"
                class="absolute right-3 top-1/2 -translate-y-1/2 text-text-muted hover:text-text-secondary transition-colors"
              >
                <Eye v-if="!showOldPassword" :size="16" />
                <EyeOff v-else :size="16" />
              </button>
            </div>
          </div>

          <div>
            <label class="block text-sm font-medium text-text-secondary mb-1.5">
              新密码
            </label>
            <div class="relative">
              <Lock :size="16" class="absolute left-3 top-1/2 -translate-y-1/2 text-text-muted pointer-events-none" />
              <input
                v-model="passwordForm.newPassword"
                :type="showNewPassword ? 'text' : 'password'"
                class="input-base pl-10 pr-10"
                placeholder="输入新密码"
              />
              <button
                type="button"
                @click="showNewPassword = !showNewPassword"
                class="absolute right-3 top-1/2 -translate-y-1/2 text-text-muted hover:text-text-secondary transition-colors"
              >
                <Eye v-if="!showNewPassword" :size="16" />
                <EyeOff v-else :size="16" />
              </button>
            </div>
          </div>

          <div>
            <label class="block text-sm font-medium text-text-secondary mb-1.5">
              确认新密码
            </label>
            <div class="relative">
              <Lock :size="16" class="absolute left-3 top-1/2 -translate-y-1/2 text-text-muted pointer-events-none" />
              <input
                v-model="confirmPassword"
                :type="showConfirmPassword ? 'text' : 'password'"
                class="input-base pl-10 pr-10"
                placeholder="再次输入新密码"
              />
              <button
                type="button"
                @click="showConfirmPassword = !showConfirmPassword"
                class="absolute right-3 top-1/2 -translate-y-1/2 text-text-muted hover:text-text-secondary transition-colors"
              >
                <Eye v-if="!showConfirmPassword" :size="16" />
                <EyeOff v-else :size="16" />
              </button>
            </div>
          </div>

          <div v-if="passwordSuccess" class="text-sm text-success px-3 py-2 rounded-lg bg-success-subtle flex items-center gap-2">
            <BadgeCheck :size="16" />
            {{ passwordSuccess }}
          </div>
          <div v-if="passwordError" class="text-sm text-danger px-3 py-2 rounded-lg bg-danger-subtle">
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

        <!-- 登录设备（静态占位，待后端接口） -->
        <div class="mt-8 pt-6 border-t border-border">
          <h3 class="text-sm font-medium text-text-secondary mb-1 flex items-center gap-2">
            <Laptop :size="14" />
            登录设备
          </h3>
          <p class="text-xs text-text-muted mb-4">以下为当前登录会话的设备列表，更多设备管理功能待后端接口支持。</p>
          <div class="space-y-2">
            <div class="flex items-center gap-3 px-4 py-3 rounded-lg bg-bg-secondary border border-border">
              <Laptop :size="18" class="text-accent shrink-0" />
              <div class="flex-1 min-w-0">
                <p class="text-sm text-text-primary font-medium">macOS · Safari</p>
                <p class="text-xs text-text-muted">当前设备 · 上次活跃：刚刚</p>
              </div>
              <Badge variant="success" dot>当前</Badge>
            </div>
            <div class="flex items-center gap-3 px-4 py-3 rounded-lg bg-bg-secondary border border-border opacity-60">
              <Laptop :size="18" class="text-text-muted shrink-0" />
              <div class="flex-1 min-w-0">
                <p class="text-sm text-text-primary font-medium">Windows · Chrome</p>
                <p class="text-xs text-text-muted">上次活跃：3 天前</p>
              </div>
              <span class="text-xs text-text-muted">已过期</span>
            </div>
          </div>
          <p class="text-[11px] text-text-muted mt-3 italic">※ 以上为静态占位数据，设备管理功能待后端接口支持。</p>
        </div>
      </section>
    </div>
  </div>
</template>
