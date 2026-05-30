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
    <section class="mb-10 pb-8 border-b border-border">
      <h1 class="text-3xl sm:text-4xl font-bold text-text-primary tracking-tight">
        关于
      </h1>
    </section>

    <div class="prose-custom max-w-none">
      <p class="text-lg text-text-secondary leading-relaxed mb-6">
        {{ siteStore.site.description }}
      </p>
      <div class="space-y-4 text-text-secondary">
        <p>这是一个基于 Vue 3 + Spring Boot 构建的个人博客系统。</p>
        <p>在这里，我记录技术思考、学习笔记和生活感悟。</p>
      </div>
    </div>
  </div>
</template>
