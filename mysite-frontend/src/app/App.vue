<script setup lang="ts">
import { onMounted } from 'vue'
import { RouterView } from 'vue-router'
import { useSiteStore } from '@/stores/site'
import { useUserStore } from '@/stores/user'

const siteStore = useSiteStore()
const userStore = useUserStore()

onMounted(async () => {
  await siteStore.fetchSite()
  userStore.init()
})
</script>

<template>
  <RouterView />
</template>
