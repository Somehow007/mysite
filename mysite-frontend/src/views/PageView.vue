<template>
  <div class="page-view">
    <header
      v-if="page.show_title_and_feature_image"
      class="post-header"
      :class="{ 'has-cover': page.feature_image }"
    >
      <div class="inner">
        <h1 class="post-title">{{ page.title }}</h1>
        <div v-if="page.feature_image" class="post-cover cover">
          <img :src="page.feature_image" :alt="page.title" />
        </div>
      </div>
    </header>

    <main class="content" role="main">
      <article class="post">
        <div class="inner">
          <section class="post-content" v-html="page.content"></section>
        </div>
      </article>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import type { Page } from '@/types/blog'

const route = useRoute()

const page = ref<Page>({
  id: '1',
  slug: 'page',
  title: '页面标题',
  content: '<p>这是页面内容...</p>',
  show_title_and_feature_image: true,
})

onMounted(() => {
  const slug = route.params.slug as string
  if (slug) {
    page.value = {
      id: slug,
      slug: slug,
      title: '页面标题',
      content: '<p>页面内容...</p>',
      show_title_and_feature_image: true,
    }
  }
})
</script>

<style scoped>
</style>
