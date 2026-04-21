<script setup lang="ts">
import { useSiteStore } from '@/stores/site'

const siteStore = useSiteStore()
</script>

<template>
  <footer class="border-t border-[var(--color-border)] dark:border-[var(--color-dark-border)] mt-auto">
    <div class="max-w-[1080px] mx-auto px-6 py-8 flex flex-col sm:flex-row items-center justify-between gap-4">
      <p class="text-sm text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)]">
        &copy; {{ new Date().getFullYear() }} {{ siteStore.site.author }}. All rights reserved.
      </p>
      <p class="text-sm text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)]">
        Built with Vue & TailwindCSS
      </p>
    </div>
  </footer>
</template>
