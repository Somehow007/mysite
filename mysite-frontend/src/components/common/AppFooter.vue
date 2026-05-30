<script setup lang="ts">
import { useSiteStore } from '@/stores/site'

const siteStore = useSiteStore()
</script>

<template>
  <footer class="border-t border-border mt-auto">
    <div class="max-w-[1080px] mx-auto px-6 py-10 flex flex-col sm:flex-row items-center justify-between gap-4">
      <p class="text-sm text-text-muted">
        &copy; {{ new Date().getFullYear() }} {{ siteStore.site.author }}. All rights reserved.
      </p>
      <p class="text-sm text-text-muted">
        Built with Vue & TailwindCSS
      </p>
    </div>
  </footer>
</template>
