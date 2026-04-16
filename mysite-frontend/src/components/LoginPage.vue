<template>
  <div class="auth-page">
    <div class="auth-container">
      <div class="auth-header">
        <h1 class="auth-title">{{ isLogin ? '登录' : '注册' }}</h1>
        <p class="auth-subtitle">{{ isLogin ? '欢迎回来' : '创建您的账号' }}</p>
      </div>

      <div class="auth-tabs">
        <button 
          class="tab-btn" 
          :class="{ active: isLogin }" 
          @click="switchToLogin"
        >
          登录
        </button>
        <button 
          class="tab-btn" 
          :class="{ active: !isLogin }" 
          @click="switchToRegister"
        >
          注册
        </button>
      </div>

      <form v-if="isLogin" class="auth-form" @submit.prevent="handleLogin">
        <div class="form-group">
          <label for="login-username">用户名</label>
          <input 
            type="text" 
            id="login-username" 
            v-model="formState.login.username" 
            placeholder="请输入用户名"
            required 
          />
        </div>
        <div class="form-group">
          <label for="login-password">密码</label>
          <input 
            type="password" 
            id="login-password" 
            v-model="formState.login.password" 
            placeholder="请输入密码"
            required 
          />
        </div>
        <button type="submit" class="submit-btn" :disabled="loading.login">
          {{ loading.login ? '登录中...' : '登录' }}
        </button>
      </form>

      <form v-else class="auth-form" @submit.prevent="handleRegister">
        <div class="form-group">
          <label for="signup-username">用户名 <span class="required">*</span></label>
          <input 
            type="text" 
            id="signup-username" 
            v-model="formState.signup.username" 
            placeholder="请输入用户名"
            required 
          />
        </div>
        <div class="form-group">
          <label for="signup-realName">真实姓名 <span class="required">*</span></label>
          <input 
            type="text" 
            id="signup-realName" 
            v-model="formState.signup.realName" 
            placeholder="请输入真实姓名"
            required 
          />
        </div>
        <div class="form-group">
          <label for="signup-phoneNumber">手机号 <span class="required">*</span></label>
          <input 
            type="tel" 
            id="signup-phoneNumber" 
            v-model="formState.signup.phoneNumber" 
            placeholder="请输入手机号"
            required 
          />
        </div>
        <div class="form-group">
          <label for="signup-email">邮箱</label>
          <input 
            type="email" 
            id="signup-email" 
            v-model="formState.signup.email" 
            placeholder="请输入邮箱（选填）"
          />
        </div>
        <div class="form-group">
          <label for="signup-password">密码 <span class="required">*</span></label>
          <input 
            type="password" 
            id="signup-password" 
            v-model="formState.signup.password" 
            placeholder="请输入密码"
            required 
          />
        </div>
        <button type="submit" class="submit-btn" :disabled="loading.signup">
          {{ loading.signup ? '注册中...' : '注册' }}
        </button>
      </form>

      <div v-if="errorMessage" class="error-message">
        {{ errorMessage }}
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { loginUser, registerUser } from '@/api/user'
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

const isLogin = ref(true)
const errorMessage = ref('')

const formState = reactive({
  login: {
    username: '',
    password: ''
  },
  signup: {
    username: '',
    email: '',
    password: '',
    realName: '',
    phoneNumber: ''
  }
})

const loading = reactive({
  login: false,
  signup: false
})

const switchToLogin = () => {
  isLogin.value = true
  errorMessage.value = ''
}

const switchToRegister = () => {
  isLogin.value = false
  errorMessage.value = ''
}

const handleLogin = async () => {
  try {
    loading.login = true
    errorMessage.value = ''
    
    const response = await loginUser({
      username: formState.login.username,
      password: formState.login.password
    })
    
    if (response && response.code === '0') {
      await userStore.fetchCurrentUser()
      router.push('/')
    } else {
      errorMessage.value = response?.message || '登录失败'
    }
  } catch (err: unknown) {
    const e = err as { response?: { data?: { message?: string } }; message?: string }
    errorMessage.value = e?.response?.data?.message ?? e?.message ?? '登录失败，请稍后重试'
  } finally {
    loading.login = false
  }
}

const handleRegister = async () => {
  try {
    loading.signup = true
    errorMessage.value = ''
    
    const response = await registerUser({
      username: formState.signup.username,
      password: formState.signup.password,
      realName: formState.signup.realName,
      email: formState.signup.email,
      phoneNumber: formState.signup.phoneNumber
    })
    
    if (response && response.code === '0') {
      alert('注册成功！请登录')
      switchToLogin()
    } else {
      errorMessage.value = response?.message || '注册失败'
    }
  } catch (err: unknown) {
    const e = err as { response?: { data?: { message?: string } }; message?: string }
    errorMessage.value = e?.response?.data?.message ?? e?.message ?? '注册失败，请稍后重试'
  } finally {
    loading.signup = false
  }
}
</script>

<style scoped>
.auth-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 2rem;
  background: var(--color-background-main);
}

.auth-container {
  width: 100%;
  max-width: 420px;
  background: var(--color-background-main);
  border: 1px solid var(--color-background-contrast);
  border-radius: 8px;
  padding: 2.5rem;
}

.auth-header {
  text-align: center;
  margin-bottom: 2rem;
}

.auth-title {
  font-size: 2.4rem;
  font-weight: 600;
  color: var(--color-content-lead);
  margin-bottom: 0.5rem;
}

.auth-subtitle {
  font-size: 1.5rem;
  color: var(--color-content-secondary);
}

.auth-tabs {
  display: flex;
  margin-bottom: 2rem;
  border-bottom: 1px solid var(--color-background-contrast);
}

.tab-btn {
  flex: 1;
  padding: 1rem;
  background: transparent;
  border: none;
  font-size: 1.5rem;
  color: var(--color-content-secondary);
  cursor: pointer;
  transition: all 0.2s ease;
  position: relative;
}

.tab-btn:hover {
  color: var(--color-content-main);
}

.tab-btn.active {
  color: var(--ghost-accent-color);
}

.tab-btn.active::after {
  content: '';
  position: absolute;
  bottom: -1px;
  left: 0;
  right: 0;
  height: 2px;
  background: var(--ghost-accent-color);
}

.auth-form {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.form-group label {
  font-size: 1.4rem;
  font-weight: 500;
  color: var(--color-content-main);
}

.required {
  color: var(--ghost-accent-color);
}

.form-group input {
  width: 100%;
  padding: 1rem 1.2rem;
  font-size: 1.5rem;
  border: 1px solid var(--color-background-contrast);
  border-radius: 4px;
  background: var(--color-background-main);
  color: var(--color-content-main);
  transition: border-color 0.2s ease;
}

.form-group input::placeholder {
  color: var(--color-content-secondary);
}

.form-group input:focus {
  outline: none;
  border-color: var(--ghost-accent-color);
}

.submit-btn {
  width: 100%;
  padding: 1.2rem;
  margin-top: 1rem;
  font-size: 1.5rem;
  font-weight: 600;
  color: #fff;
  background: var(--ghost-accent-color);
  border: none;
  border-radius: 4px;
  cursor: pointer;
  transition: opacity 0.2s ease;
}

.submit-btn:hover:not(:disabled) {
  opacity: 0.9;
}

.submit-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.error-message {
  margin-top: 1.5rem;
  padding: 1rem;
  font-size: 1.4rem;
  text-align: center;
  color: #e74c3c;
  background: var(--color-background-secondary);
  border-radius: 4px;
}
</style>
