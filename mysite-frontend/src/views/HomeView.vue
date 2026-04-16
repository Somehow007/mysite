<template>
  <div class="home">
    <h1>我的个人博客</h1>
    <div v-if="loading">加载中...</div>
    <div v-else>
      <div v-for="blog in blogs" :key="blog.id" class="blog-card">
        <h2>{{ blog.title }}</h2>
        <p>{{ blog.summary }}</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { getBlogs } from '@/api/blog'
import { ref, onMounted } from 'vue'
import type { Post } from '@/types/blog'

const blogs = ref<Post[]>([])
const loading = ref(true)

onMounted(async () => {
  try {
    const data = await getBlogs(0, 10)
    blogs.value = data.data.content
    console.log('加载成功', data)
  } catch (error) {
    console.log('加载失败', error)
  } finally {
    loading.value = false
    console.log('finally')
  }
})
</script>

<style scoped>
.home {
  padding: 2rem;
}

.home h1 {
  font-size: 2.5rem;
  margin-bottom: 2rem;
  color: var(--color-content-lead);
}

.blog-card {
  padding: 1.5rem;
  margin-bottom: 1rem;
  border: 1px solid var(--color-background-contrast);
  border-radius: 8px;
}

.blog-card h2 {
  font-size: 1.8rem;
  margin-bottom: 0.5rem;
  color: var(--color-content-lead);
}

.blog-card p {
  color: var(--color-content-secondary);
}
</style>
