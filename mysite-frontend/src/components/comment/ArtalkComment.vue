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
let artalkInstance: Artalk | null = null

async function initArtalk() {
  if (!containerRef.value) return

  if (artalkInstance) {
    artalkInstance.destroy()
    artalkInstance = null
  }

  const server = import.meta.env.VITE_ARTALK_SERVER || 'http://localhost:23366'

  try {
    const resp = await fetch(`${server}/api/stat`, { method: 'GET', signal: AbortSignal.timeout(3000) })
    if (!resp.ok) throw new Error('Artalk server not available')
  } catch {
    loadError.value = true
    return
  }

  loadError.value = false

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
    <div v-if="loadError" class="text-center py-8">
      <p class="text-sm text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)]">
        评论服务暂不可用
      </p>
    </div>
    <div v-show="!loadError" ref="containerRef" />
  </div>
</template>
