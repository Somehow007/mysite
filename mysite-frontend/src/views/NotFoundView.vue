<script setup lang="ts">
import { useHead } from '@unhead/vue'
import NotFoundPanel from '@/components/common/NotFoundPanel.vue'

useHead(() => ({
  title: '页面不存在',
  meta: [
    { name: 'description', content: '页面不存在' },
    { name: 'robots', content: 'noindex' },
  ],
}))
</script>

<template>
  <NotFoundPanel />
</template>
