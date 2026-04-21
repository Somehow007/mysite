<script setup lang="ts">
import { ref, watch, nextTick } from 'vue'
import { useMarkdown } from '@/composables/useMarkdown'
import type { TocItem } from '@/composables/useMarkdown'

const props = defineProps<{
  content: string
}>()

const emit = defineEmits<{
  'toc-ready': [items: TocItem[]]
}>()

const contentRef = ref<HTMLElement | null>(null)
const { renderedHtml, render, applyHighlighting, rendering, toc } = useMarkdown()

watch(
  () => props.content,
  async (newContent) => {
    if (newContent) {
      await render(newContent)
      emit('toc-ready', toc.value)
      await nextTick()
      if (contentRef.value) {
        await applyHighlighting(contentRef.value)
      }
    }
  },
  { immediate: true },
)
</script>

<template>
  <div v-if="rendering" class="animate-pulse space-y-4">
    <div class="skeleton h-8 w-3/4 rounded" />
    <div class="skeleton h-4 w-full rounded" />
    <div class="skeleton h-4 w-5/6 rounded" />
    <div class="skeleton h-4 w-4/5 rounded" />
    <div class="skeleton h-40 w-full rounded-lg mt-6" />
    <div class="skeleton h-4 w-full rounded" />
    <div class="skeleton h-4 w-3/4 rounded" />
  </div>
  <div
    v-else
    ref="contentRef"
    class="prose"
    v-html="renderedHtml"
  />
</template>
