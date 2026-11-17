<script setup lang="ts">
import { useSeo } from '@/composables/useSeo'
import NotFoundPanel from '@/components/common/NotFoundPanel.vue'

useSeo({
  title: '页面不存在',
  description: '页面不存在',
  noindex: true,
})
</script>

<template>
  <NotFoundPanel />
</template>
