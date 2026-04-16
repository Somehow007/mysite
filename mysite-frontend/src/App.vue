<script setup lang="ts">
import { onMounted } from 'vue'
import { RouterView } from 'vue-router'
import BlogLayout from './components/BlogLayout.vue'
import { useSiteStore } from './stores/site'
import { initTheme } from './utils/theme'

const siteStore = useSiteStore()

// 初始化
onMounted(async () => {
  // 初始化主题
  initTheme()

  // 获取站点信息
  await siteStore.fetchSite()
})
</script>

<template>
  <BlogLayout
    :site="siteStore.site"
    :navigation="siteStore.site.navigation || []"
    :secondary-navigation="siteStore.site.secondary_navigation || []"
  >
    <RouterView />
  </BlogLayout>
</template>

<style scoped>
/* 样式已在 blog.css 中定义 */
</style>
