import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { SiteConfig } from '@/types'
import { get } from '@/api/client'

export const useSiteStore = defineStore('site', () => {
  const site = ref<SiteConfig>({
    title: 'MySite',
    description: '一个极简的个人博客',
    author: 'Admin',
    url: '',
    commentEnabled: true,
    navigation: [
      { label: '首页', path: '/' },
      { label: '归档', path: '/archive' },
      { label: '关于', path: '/about' },
    ],
  })
  const loading = ref(false)

  async function fetchSite() {
    loading.value = true
    try {
      const data = await get<SiteConfig>('/v1/site/config')
      if (data) {
        site.value = data
      }
    } catch {
    } finally {
      loading.value = false
    }
  }

  return {
    site,
    loading,
    fetchSite,
  }
})
