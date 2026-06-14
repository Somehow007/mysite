<script setup lang="ts">
import { ref, watch, nextTick } from 'vue'
import { useMarkdown } from '@/composables/useMarkdown'
import { useImageOptimizer } from '@/composables/useImageOptimizer'
import type { TocItem } from '@/composables/useMarkdown'

const props = defineProps<{
  content: string
}>()

const emit = defineEmits<{
  'toc-ready': [items: TocItem[]]
}>()

const contentRef = ref<HTMLElement | null>(null)
const { renderedHtml, render, rendering, toc } = useMarkdown()
const { processContainer } = useImageOptimizer(contentRef)

function enhanceCodeBlocks(container: HTMLElement) {
  const pres = container.querySelectorAll('pre code[data-language]')
  for (const code of pres) {
    const lang = code.getAttribute('data-language') || ''
    if (!lang) continue

    const pre = code.parentElement
    if (!pre) continue

    // Skip if already wrapped
    if (pre.parentElement?.classList.contains('code-block-wrapper')) continue

    const wrapper = document.createElement('div')
    wrapper.className = 'code-block-wrapper'

    const header = document.createElement('div')
    header.className = 'code-block-header'

    const langLabel = document.createElement('span')
    langLabel.className = 'code-block-lang'
    langLabel.setAttribute('data-lang', lang)
    langLabel.textContent = lang

    const copyBtn = document.createElement('button')
    copyBtn.className = 'code-block-copy'
    copyBtn.textContent = '复制'
    copyBtn.addEventListener('click', async () => {
      const text = code.textContent || ''
      try {
        await navigator.clipboard.writeText(text)
        copyBtn.textContent = '已复制'
        copyBtn.classList.add('copied')
        setTimeout(() => {
          copyBtn.textContent = '复制'
          copyBtn.classList.remove('copied')
        }, 2000)
      } catch {
        copyBtn.textContent = '复制失败'
        setTimeout(() => {
          copyBtn.textContent = '复制'
        }, 2000)
      }
    })

    header.appendChild(langLabel)
    header.appendChild(copyBtn)

    pre.parentNode?.insertBefore(wrapper, pre)
    wrapper.appendChild(header)
    wrapper.appendChild(pre)
  }
}

watch(
  () => props.content,
  async (newContent) => {
    if (newContent) {
      await render(newContent)
      emit('toc-ready', toc.value)
      await nextTick()
      if (contentRef.value) {
        processContainer(contentRef.value)
        enhanceCodeBlocks(contentRef.value)
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
