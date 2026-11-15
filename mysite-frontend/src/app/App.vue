<script setup lang="ts">
import { onMounted } from 'vue'
import { RouterView } from 'vue-router'
import { useHead } from '@unhead/vue'
import { useSiteStore } from '@/stores/site'
import { useUserStore } from '@/stores/user'
import ToastContainer from '@/components/common/ToastContainer.vue'
import GlobalConfirmDialog from '@/components/GlobalConfirmDialog.vue'

const siteStore = useSiteStore()
const userStore = useUserStore()

// 页面标题统一在这里拼「页面 - 站名」；站名未加载时退化为纯页面标题，
// 页标题也没有就回落 index.html 的静态标题（避免 fetchSite 间隙闪空标题）
const initialTitle = document.title
useHead(() => {
  const siteTitle = siteStore.site.title
  return {
    titleTemplate: (title?: string) => {
      if (!siteTitle) return title || initialTitle
      return title ? `${title} - ${siteTitle}` : siteTitle
    },
  }
})

onMounted(async () => {
  await siteStore.fetchSite()
  userStore.init()
})
</script>

<template>
  <RouterView />
  <ToastContainer />
  <GlobalConfirmDialog />
</template>
