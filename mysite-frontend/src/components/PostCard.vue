<template>
  <article :class="['post', { featured: post.featured }]">
    <div class="inner">
      <div class="box post-box">
        <span v-if="post.featured" class="post-feature">
          <i class="icon icon-star">
            <IconStar />
          </i>
        </span>
        <h2 class="post-title">
          <router-link :to="`/post/${post.id}`">{{ post.title }}</router-link>
        </h2>
        <span class="post-meta">
          作者：
            <router-link :to="`/author/${post.authorId}`">{{ post.authorName }}</router-link>
          <template v-if="post.primary_tag">
            ，分类：
            <router-link class="post-meta-tag" :to="`/tag/${post.primary_tag.slug}`">
              {{ post.primary_tag.name }}
            </router-link>
          </template>
          ，日期：
          <time :datetime="formatDateISO(post.updateTime)">
            {{ formatDate(post.updateTime) }}
          </time>
        </span>
        <p class="post-excerpt" v-html="post.summary"></p>
      </div>
    </div>
  </article>
</template>

<script setup lang="ts">
import type { Post } from '@/types/blog'
import { formatDate, formatDateISO } from '@/utils/date'
import IconStar from './icons/IconStar.vue'

defineProps<{
  post: Post
}>()
</script>

<style scoped>
/* 样式已在 blog.css 中定义 */
</style>

