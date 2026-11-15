<script setup lang="ts">
export interface RelatedItem {
  id: string
  title: string
  kicker: string
}

defineProps<{
  items: RelatedItem[]
}>()
</script>

<template>
  <section v-if="items.length" class="post-related" aria-label="继续读">
    <h2 class="post-section-title">继续读</h2>
    <div class="post-related-grid">
      <RouterLink
        v-for="item in items"
        :key="item.id"
        :to="`/post/${item.id}`"
        class="post-related-item"
      >
        <span class="post-related-kicker">{{ item.kicker }}</span>
        <span class="post-related-title">{{ item.title }}</span>
      </RouterLink>
    </div>
  </section>
</template>
