<script setup lang="ts">
import { useHead } from '@unhead/vue'
import ArticleContent from '@/components/article/ArticleContent.vue'

useHead({
  title: '关于 - MySite',
})

const aboutContent = `
## 关于我

欢迎来到我的博客。这里是我记录技术思考和生活感悟的地方。

我是一名热爱编程的开发者，对 Web 技术、系统设计和开源软件充满热情。

## 关于本站

本站使用以下技术构建：

- **后端**：Spring Boot + MyBatis-Plus + Elasticsearch
- **前端**：Vue 3 + TypeScript + TailwindCSS
- **评论**：Artalk
- **部署**：Docker Compose

## 联系方式

- GitHub: [github.com](https://github.com)
- Email: hello@example.com

---

*如果你有任何问题或建议，欢迎在文章下方留言。*
`
</script>

<template>
  <div class="max-w-[720px] mx-auto">
    <header class="mb-10">
      <h1 class="text-3xl font-bold text-[var(--color-text-heading)] dark:text-[var(--color-dark-text-heading)]">
        关于
      </h1>
    </header>

    <ArticleContent :content="aboutContent" />
  </div>
</template>
