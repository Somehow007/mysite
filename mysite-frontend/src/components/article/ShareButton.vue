<script setup lang="ts">
import { Share2 } from 'lucide-vue-next'
import { useToast } from '@/composables/useToast'

const props = defineProps<{
  title?: string
}>()

const toast = useToast()

async function handleShare() {
  const url = window.location.href
  const data: ShareData = { title: props.title || document.title, url }
  if (typeof navigator.share === 'function') {
    try {
      await navigator.share(data)
      return
    } catch (e) {
      if (e instanceof DOMException && e.name === 'AbortError') return
      // 其他 share 失败（内容不支持等）继续走复制兜底
    }
  }
  try {
    await navigator.clipboard.writeText(url)
    toast.success('链接已复制')
  } catch {
    toast.error('复制失败，请手动复制地址栏链接')
  }
}
</script>

<template>
  <button
    type="button"
    class="fav-bar"
    aria-label="分享文章"
    @click="handleShare"
  >
    <Share2 :size="14" />
    <span class="fav-label">分享</span>
  </button>
</template>
