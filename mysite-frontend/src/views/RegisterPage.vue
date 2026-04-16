<template>
  <div class="auth-page">
    <div class="auth-container">
      <div class="auth-header">
        <h1 class="auth-title">注册</h1>
        <p class="auth-subtitle">创建您的账号</p>
      </div>

      <form @submit.prevent="handleRegister" class="auth-form">
        <div class="form-group">
          <label for="username">用户名 <span class="required">*</span></label>
          <input 
            type="text" 
            id="username" 
            v-model="form.username" 
            placeholder="请输入用户名"
            required 
          />
        </div>
        <div class="form-group">
          <label for="password">密码 <span class="required">*</span></label>
          <input 
            type="password" 
            id="password" 
            v-model="form.password" 
            placeholder="请输入密码"
            required 
          />
        </div>
        <div class="form-group">
          <label for="realName">真实姓名 <span class="required">*</span></label>
          <input 
            type="text" 
            id="realName" 
            v-model="form.realName" 
            placeholder="请输入真实姓名"
            required 
          />
        </div>
        <div class="form-group">
          <label for="phoneNumber">手机号 <span class="required">*</span></label>
          <input 
            type="tel" 
            id="phoneNumber" 
            v-model="form.phoneNumber" 
            placeholder="请输入手机号"
            required 
          />
        </div>
        <div class="form-group">
          <label for="email">邮箱</label>
          <input 
            type="email" 
            id="email" 
            v-model="form.email" 
            placeholder="请输入邮箱（选填）"
          />
        </div>
        <button type="submit" class="submit-btn" :disabled="loading">
          {{ loading ? '注册中...' : '注册' }}
        </button>
        <div v-if="error" class="error-message">
          {{ error }}
        </div>
      </form>
      
      <p class="auth-link">
        已有账号？<router-link to="/login">去登录</router-link>
      </p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { registerUser } from '@/api/user'

const router = useRouter()

const form = ref({
  username: '',
  password: '',
  realName: '',
  email: '',
  phoneNumber: ''
})

const loading = ref(false)
const error = ref('')

const handleRegister = async () => {
  try {
    loading.value = true
    error.value = ''
    const response = await registerUser({
      username: form.value.username,
      password: form.value.password,
      realName: form.value.realName,
      email: form.value.email,
      phoneNumber: form.value.phoneNumber
    })
    
    if (response && response.code === '0') {
      alert('注册成功！请登录')
      router.push('/login')
    } else {
      error.value = response?.message || '注册失败'
    }
  } catch (err) {
    console.error('注册失败:', err)
    error.value = '注册失败，请检查输入信息'
  } finally {
    loading.value = false
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

.auth-link {
  margin-top: 2rem;
  text-align: center;
  font-size: 1.4rem;
  color: var(--color-content-secondary);
}

.auth-link a {
  color: var(--ghost-accent-color);
  text-decoration: none;
}

.auth-link a:hover {
  text-decoration: underline;
}
</style>
