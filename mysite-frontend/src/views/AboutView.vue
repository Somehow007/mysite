<script setup lang="ts">
import { useHead } from '@unhead/vue'
import { useSiteStore } from '@/stores/site'

useHead(() => ({
  title: '关于 - MySite',
  meta: [
    { name: 'description', content: '关于本站' },
  ],
}))

const siteStore = useSiteStore()
</script>

<template>
  <div>
    <section class="mb-10 pb-8 border-b border-[var(--color-border)] dark:border-[var(--color-dark-border)]">
      <h1 class="text-3xl sm:text-4xl font-bold text-[var(--color-text-heading)] dark:text-[var(--color-dark-text-heading)] tracking-tight">
        关于
      </h1>
    </section>

    <div class="prose-custom max-w-none">
      <p class="text-lg text-[var(--color-text-body)] dark:text-[var(--color-dark-text-body)] leading-relaxed mb-6">
        {{ siteStore.site.description }}
      </p>
      <div class="space-y-4 text-[var(--color-text-body)] dark:text-[var(--color-dark-text-body)]">
        <p>这是一个基于 Vue 3 + Spring Boot 构建的个人博客系统。</p>
        <p>在这里，我记录技术思考、学习笔记和生活感悟。</p>
      </div>
    </div>
  </div>
</template>
