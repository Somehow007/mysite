import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { Site } from '@/types/blog'

export const useSiteStore = defineStore('site', () => {
  const site = ref<Site>({
    title: '我的博客',
    description: '个人博客网站',
    url: '/',
    locale: 'zh',
    members_enabled: true,
    members_invite_only: false,
  })
  const loading = ref(false)

  const fetchSite = async () => {
    site.value = {
      title: '我的博客',
      description: '个人博客网站',
      url: '/',
      locale: 'zh',
      members_enabled: true,
      members_invite_only: false,
    }
  }

  return {
    site,
    loading,
    fetchSite,
  }
})
