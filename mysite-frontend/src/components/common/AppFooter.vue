<script setup lang="ts">
import { RouterLink } from 'vue-router'
import { useSiteStore } from '@/stores/site'

const siteStore = useSiteStore()
const year = new Date().getFullYear()
</script>

<template>
  <footer class="mt-auto border-t border-border-strong">
    <div class="mx-auto max-w-[1180px] px-4 py-8 sm:px-6 sm:py-10">
      <div class="flex flex-col gap-6 sm:flex-row sm:items-start sm:justify-between">
        <div class="text-sm text-text-muted">
          <p>
            &copy; {{ year }} {{ siteStore.site.author }}<template v-if="siteStore.site.description"> · {{ siteStore.site.description }}</template>
          </p>
          <div class="mt-3 flex flex-wrap items-center gap-x-4 gap-y-1.5 text-xs">
            <a
              href="https://beian.miit.gov.cn/"
              target="_blank"
              rel="noopener noreferrer"
              class="text-text-muted transition-colors hover:text-accent"
            >
              苏ICP备2026038560号-1
            </a>
            <a
              href="https://beian.mps.gov.cn/#/query/webSearch?code=32110202000819"
              target="_blank"
              rel="noopener noreferrer"
              class="inline-flex items-center gap-1 text-text-muted transition-colors hover:text-accent"
            >
              <img
                src="/beian-icon.png"
                alt="公安备案图标"
                class="w-[18px] h-[20px] inline-block align-middle"
              />
              苏公网安备32110202000819号
            </a>
          </div>
        </div>

        <nav class="flex flex-wrap items-center gap-x-5 gap-y-2 text-sm">
          <RouterLink
            to="/archive"
            class="text-text-muted transition-colors hover:text-accent"
          >
            归档
          </RouterLink>
          <a
            v-if="siteStore.site.github"
            :href="siteStore.site.github"
            target="_blank"
            rel="noopener noreferrer"
            class="text-text-muted transition-colors hover:text-accent"
          >
            GitHub
          </a>
          <a
            v-if="siteStore.site.rss"
            :href="siteStore.site.rss"
            class="text-text-muted transition-colors hover:text-accent"
          >
            RSS
          </a>
          <RouterLink
            to="/about"
            class="text-text-muted transition-colors hover:text-accent"
          >
            关于
          </RouterLink>
        </nav>
      </div>
    </div>
  </footer>
</template>
