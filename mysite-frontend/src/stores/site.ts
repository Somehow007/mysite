import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { SiteConfig } from '@/types'
import { get } from '@/api/client'

export const useSiteStore = defineStore('site', () => {
  const site = ref<SiteConfig>({
    title: '',
    description: '',
    author: '',
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
