<script lang="ts" setup>
import Artalk from 'artalk'
import { onMounted, onBeforeUnmount, ref, watch, nextTick, computed } from 'vue'
import { useTheme } from '@/composables/useTheme'
import { getSystemTheme } from '@/utils/theme'
import { useUserStore } from '@/stores/user'
import 'artalk/dist/Artalk.css'

const props = defineProps<{
  pageKey: string
  pageTitle: string
}>()

const el = ref<HTMLElement>()
const { currentTheme } = useTheme()
const userStore = useUserStore()
let artalkInstance: Artalk | null = null

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

const initArtalk = () => {
  if (artalkInstance) {
    artalkInstance.destroy()
    artalkInstance = null
  }

  if (!el.value) return

  const config = {
    el: el.value,
    pageKey: props.pageKey,
    pageTitle: props.pageTitle,
    server: ARTALK_SERVER,
    site: ARTALK_SITE,
    darkMode: isDark.value,
    locale: 'zh-CN' as const,
    emoticons: 'https://cdn.jsdelivr.net/gh/ArtalkJS/Emoticons/grps/default.json',
    pagination: {
      pageSize: 15,
      readMore: true,
      autoLoad: true,
    },
    imgUpload: false,
    editorTravel: true,
    flatMode: 'auto' as const,
    nestMax: 3,
    placeholder: '写下你的评论...',
    noComment: '暂无评论，快来发表第一条评论吧！',
    sendBtn: '发送',
    heightLimit: {
      content: 400,
      children: 500,
      scrollable: false,
    },
    imgLazyLoad: 'native' as const,
    gravatar: {
      mirror: 'https://cravatar.cn/avatar/',
      params: 'd=mp&s=240',
    },
  }

  artalkInstance = Artalk.init(config)
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
  </div>
</template>

<style scoped>
.artalk-container {
  margin-top: 2rem;
  width: 100%;
}
</style>
