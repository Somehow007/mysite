<script setup lang="ts">
import { useSiteStore } from '@/stores/site'

const siteStore = useSiteStore()
</script>

<template>
  <footer class="border-t border-border mt-auto">
    <div class="max-w-[1080px] mx-auto px-6 py-10 flex flex-col items-center gap-3">
      <div class="flex flex-col sm:flex-row items-center gap-x-6 gap-y-1 text-sm text-text-muted">
        <p>
          &copy; {{ new Date().getFullYear() }} {{ siteStore.site.author }}. All rights reserved.
        </p>
        <p>
          Built with Vue & TailwindCSS
        </p>
      </div>
      <div class="flex flex-wrap items-center justify-center gap-x-4 gap-y-1 text-xs text-text-muted">
        <a
          href="https://beian.miit.gov.cn/"
          target="_blank"
          rel="noopener noreferrer"
          class="hover:text-accent transition-colors"
        >
          苏ICP备2026038560号-1
        </a>
        <span class="inline-flex items-center gap-1 opacity-50">
          <span>|</span>
          <span>公安备案号申请中，预留位置</span>
        </span>
      </div>
    </div>
  </footer>
</template>
