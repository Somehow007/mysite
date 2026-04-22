<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch } from 'vue'
import Artalk from 'artalk'
import 'artalk/dist/Artalk.css'

const props = defineProps<{
  pageKey: string
  pageTitle: string
}>()

const containerRef = ref<HTMLElement | null>(null)
const loadError = ref(false)
const loading = ref(true)
let artalkInstance: Artalk | null = null

async function checkServerHealth(server: string): Promise<boolean> {
  try {
    const siteName = import.meta.env.VITE_ARTALK_SITE || 'MySite博客'
    const resp = await fetch(
      `${server}/api/v2/comments?page_key=/health-check&site_name=${encodeURIComponent(siteName)}&limit=0`,
      { method: 'GET', signal: AbortSignal.timeout(5000) },
    )
    return resp.ok
  } catch {
    return false
  }
}

async function initArtalk() {
  if (!containerRef.value) return

  if (artalkInstance) {
    artalkInstance.destroy()
    artalkInstance = null
  }

  const server = import.meta.env.VITE_ARTALK_SERVER || '/artalk-api'

  loading.value = true
  loadError.value = false

  const healthy = await checkServerHealth(server)
  if (!healthy) {
    loadError.value = true
    loading.value = false
    return
  }

  loading.value = false

  artalkInstance = Artalk.init({
    el: containerRef.value,
    pageKey: props.pageKey,
    pageTitle: props.pageTitle,
    server,
    site: import.meta.env.VITE_ARTALK_SITE || 'MySite博客',
    darkMode: document.documentElement.classList.contains('dark'),
    locale: 'zh-CN',
  })
}

watch(
  () => props.pageKey,
  () => {
    initArtalk()
  },
)

onMounted(() => {
  initArtalk()

  const observer = new MutationObserver(() => {
    if (artalkInstance) {
      artalkInstance.setDarkMode(document.documentElement.classList.contains('dark'))
    }
  })

  observer.observe(document.documentElement, {
    attributes: true,
    attributeFilter: ['class'],
  })

  onUnmounted(() => {
    observer.disconnect()
  })
})

onUnmounted(() => {
  if (artalkInstance) {
    artalkInstance.destroy()
    artalkInstance = null
  }
})
</script>

<template>
  <div class="artalk-custom mt-12 pt-8 border-t border-[var(--color-border)] dark:border-[var(--color-dark-border)]">
    <div v-if="loading" class="flex items-center justify-center py-8 gap-2">
      <div class="w-4 h-4 border-2 border-[var(--color-accent)] dark:border-[var(--color-dark-accent)] border-t-transparent rounded-full animate-spin" />
      <span class="text-sm text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)]">评论加载中...</span>
    </div>
    <div v-else-if="loadError" class="text-center py-8">
      <p class="text-sm text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)]">
        评论服务暂不可用
      </p>
      <button
        @click="initArtalk"
        class="mt-2 text-xs text-[var(--color-accent)] dark:text-[var(--color-dark-accent)] hover:opacity-70 transition-opacity"
      >
        点击重试
      </button>
    </div>
    <div v-show="!loading && !loadError" ref="containerRef" />
  </div>
</template>
