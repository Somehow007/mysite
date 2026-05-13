<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch, nextTick, computed } from 'vue'
import Artalk from 'artalk'
import 'artalk/dist/Artalk.css'
import { useUserStore } from '@/stores/user'

const props = defineProps<{
  pageKey: string
  pageTitle: string
}>()

const userStore = useUserStore()

const wrapperRef = ref<HTMLElement | null>(null)
const containerRef = ref<HTMLElement | null>(null)
const loadError = ref(false)
let artalkInstance: Artalk | null = null
let observer: MutationObserver | null = null

const isLoggedIn = computed(() => userStore.isLoggedIn)
const showLoading = computed(() => userStore.loading && !userStore.user)
const showContent = computed(() => !userStore.loading || userStore.user)

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

function applyLoggedInState() {
  if (!artalkInstance || !containerRef.value) return

  const user = userStore.user
  if (!user || !isLoggedIn.value) return

  try {
    const editor = artalkInstance.ctx.get('editor')
    if (!editor) return

    const inputs = editor.getHeaderInputEls()

    if (inputs.name) {
      inputs.name.value = user.username
      inputs.name.readOnly = true
      inputs.name.dispatchEvent(new Event('input', { bubbles: true }))
      inputs.name.dispatchEvent(new Event('change', { bubbles: true }))
      const nameItem = inputs.name.closest('.atk-item')
      if (nameItem) nameItem.classList.add('atk-item-hidden')
    }

    if (inputs.email) {
      inputs.email.value = user.email || ''
      inputs.email.readOnly = true
      inputs.email.dispatchEvent(new Event('input', { bubbles: true }))
      inputs.email.dispatchEvent(new Event('change', { bubbles: true }))
      const emailItem = inputs.email.closest('.atk-item')
      if (emailItem) emailItem.classList.add('atk-item-hidden')
    }
  } catch {
    // Editor might not be ready yet
  }
}

async function initArtalk() {
  if (artalkInstance) {
    artalkInstance.destroy()
    artalkInstance = null
  }

  const server = import.meta.env.VITE_ARTALK_SERVER || '/artalk-api'

  loadError.value = false

  const healthy = await checkServerHealth(server)
  if (!healthy) {
    loadError.value = true
    return
  }

  await nextTick()

  if (!containerRef.value) {
    setTimeout(() => initArtalk(), 100)
    return
  }

  artalkInstance = Artalk.init({
    el: containerRef.value,
    pageKey: props.pageKey,
    pageTitle: props.pageTitle,
    server,
    site: import.meta.env.VITE_ARTALK_SITE || 'MySite博客',
    darkMode: document.documentElement.classList.contains('dark'),
    locale: 'zh-CN',
  })

  artalkInstance.on('list-loaded', () => {
    if (isLoggedIn.value) {
      applyLoggedInState()
    }
  })

  if (isLoggedIn.value) {
    setTimeout(() => {
      applyLoggedInState()
    }, 300)
  }
}

watch(
  () => props.pageKey,
  () => {
    initArtalk()
  },
)

watch(showContent, async (show) => {
  if (show && !artalkInstance && !loadError.value) {
    await nextTick()
    initArtalk()
  }
})

onMounted(async () => {
  await nextTick()

  if (showContent.value) {
    initArtalk()
  }

  observer = new MutationObserver(() => {
    if (artalkInstance) {
      artalkInstance.setDarkMode(document.documentElement.classList.contains('dark'))
    }
  })

  observer.observe(document.documentElement, {
    attributes: true,
    attributeFilter: ['class'],
  })
})

onUnmounted(() => {
  if (observer) {
    observer.disconnect()
    observer = null
  }
  if (artalkInstance) {
    artalkInstance.destroy()
    artalkInstance = null
  }
})
</script>

<template>
  <div
    ref="wrapperRef"
    class="artalk-custom mt-12 pt-8 border-t border-[var(--color-border)] dark:border-[var(--color-dark-border)]"
  >
    <div v-if="showLoading" class="flex items-center justify-center py-8 gap-2">
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
    <template v-else>
      <div ref="containerRef" />
    </template>
  </div>
</template>
