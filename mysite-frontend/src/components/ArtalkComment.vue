<script lang="ts" setup>
import Artalk from 'artalk'
import { onMounted, onBeforeUnmount, ref, watch, nextTick, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useTheme } from '@/composables/useTheme'
import { getSystemTheme } from '@/utils/theme'
import { useUserStore } from '@/stores/user'
import 'artalk/dist/Artalk.css'

const props = defineProps<{
  pageKey: string
  pageTitle: string
}>()

const router = useRouter()
const el = ref<HTMLElement>()
const { currentTheme } = useTheme()
const userStore = useUserStore()
let artalkInstance: Artalk | null = null

const showLoginPrompt = ref(false)

const ARTALK_SERVER = import.meta.env.VITE_ARTALK_SERVER ?? ''
const ARTALK_SITE = import.meta.env.VITE_ARTALK_SITE || 'MySite博客'

const isDark = computed(() => {
  if (currentTheme.value === 'dark') return true
  if (currentTheme.value === 'light') return false
  return getSystemTheme() === 'dark'
})

const userInfo = computed(() => {
  if (!userStore.isLoggedIn || !userStore.user) {
    return undefined
  }
  
  const name = userStore.displayName || userStore.user.username || ''
  const email = userStore.user.email || ''
  
  if (!name || !email) {
    return undefined
  }
  
  return {
    name,
    email,
    link: userStore.user.id ? `/author/${userStore.user.id}` : '',
  }
})

const goToLogin = () => {
  showLoginPrompt.value = false
  router.push('/login')
}

const closeLoginPrompt = () => {
  showLoginPrompt.value = false
}

const initArtalk = () => {
  if (artalkInstance) {
    artalkInstance.destroy()
    artalkInstance = null
  }

  if (!el.value) return

  const config: Record<string, unknown> = {
    el: el.value,
    pageKey: props.pageKey,
    pageTitle: props.pageTitle,
    server: ARTALK_SERVER,
    site: ARTALK_SITE,
    darkMode: isDark.value,
    locale: 'zh-CN',
    emoticons: 'https://cdn.jsdelivr.net/gh/ArtalkJS/Emoticons/grps/default.json',
    pagination: {
      pageSize: 15,
      readMore: true,
      autoLoad: true,
    },
    imgUpload: false,
    editorTravel: true,
    flatMode: 'auto',
    nestMax: 3,
    placeholder: '写下你的评论...',
    noComment: '暂无评论，快来发表第一条评论吧！',
    sendBtn: '发送',
    heightLimit: {
      content: 400,
      children: 500,
      scrollable: false,
    },
    imgLazyLoad: 'native',
    gravatar: {
      mirror: 'https://cravatar.cn/avatar/',
      params: 'd=mp&s=240',
    },
    beforeSubmit: (_editor: unknown, next: () => void) => {
      if (!userStore.isLoggedIn) {
        showLoginPrompt.value = true
        return
      }
      next()
    },
  }

  if (userInfo.value) {
    config.user = userInfo.value
  }

  artalkInstance = Artalk.init(config as Parameters<typeof Artalk.init>[0])
}

watch(
  () => props.pageKey,
  (newKey) => {
    if (artalkInstance && newKey) {
      nextTick(() => {
        artalkInstance!.update({
          pageKey: newKey,
          pageTitle: props.pageTitle,
        })
        artalkInstance!.reload()
      })
    }
  }
)

watch(isDark, (dark) => {
  if (artalkInstance) {
    artalkInstance.setDarkMode(dark)
  }
})

watch(userInfo, () => {
  initArtalk()
})

onMounted(() => {
  initArtalk()
})

onBeforeUnmount(() => {
  if (artalkInstance) {
    artalkInstance.destroy()
    artalkInstance = null
  }
})
</script>

<template>
  <div class="artalk-container">
    <div ref="el"></div>
    
    <Teleport to="body">
      <Transition name="fade">
        <div v-if="showLoginPrompt" class="login-prompt-overlay" @click.self="closeLoginPrompt">
          <div class="login-prompt-modal">
            <div class="login-prompt-icon">
              <svg xmlns="http://www.w3.org/2000/svg" width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"></path>
                <circle cx="12" cy="7" r="4"></circle>
              </svg>
            </div>
            <h3 class="login-prompt-title">请先登录</h3>
            <p class="login-prompt-text">登录后才能发表评论</p>
            <div class="login-prompt-actions">
              <button class="login-prompt-btn cancel" @click="closeLoginPrompt">取消</button>
              <button class="login-prompt-btn confirm" @click="goToLogin">去登录</button>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>

<style scoped>
.artalk-container {
  margin-top: 2rem;
  width: 100%;
  text-align: left;
}

.login-prompt-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 9999;
}

.login-prompt-modal {
  background: var(--color-background, #fff);
  border-radius: 12px;
  padding: 2rem;
  max-width: 360px;
  width: 90%;
  text-align: center;
  box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 10px 10px -5px rgba(0, 0, 0, 0.04);
}

.login-prompt-icon {
  color: var(--color-primary, #3b82f6);
  margin-bottom: 1rem;
}

.login-prompt-title {
  margin: 0 0 0.5rem;
  font-size: 1.25rem;
  font-weight: 600;
  color: var(--color-text, #1f2937);
}

.login-prompt-text {
  margin: 0 0 1.5rem;
  color: var(--color-text-secondary, #6b7280);
  font-size: 0.9rem;
}

.login-prompt-actions {
  display: flex;
  gap: 0.75rem;
  justify-content: center;
}

.login-prompt-btn {
  padding: 0.625rem 1.5rem;
  border-radius: 8px;
  font-size: 0.9rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
  border: none;
}

.login-prompt-btn.cancel {
  background: var(--color-background-secondary, #f3f4f6);
  color: var(--color-text-secondary, #6b7280);
}

.login-prompt-btn.cancel:hover {
  background: var(--color-background-tertiary, #e5e7eb);
}

.login-prompt-btn.confirm {
  background: var(--color-primary, #3b82f6);
  color: #fff;
}

.login-prompt-btn.confirm:hover {
  opacity: 0.9;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>

<style>
.artalk-container .atk-main-editor .atk-header {
  display: none !important;
}
</style>
