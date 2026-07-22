// composables/useChatMarkdown.ts
// 聊天专用轻量 Markdown 渲染 —— 流式中 rAF 节流渲染，
// 流结束后执行 Prism 高亮 + KaTeX 数学公式（一次性，不与流式渲染竞争）。
//
// 与 useMarkdown 的区别：
//   useMarkdown 面向文章页：async render、TOC 提取、callout、KaTeX 保护
//   本模块面向聊天流式：同步 marked.parse + rAF 节流 + DOMPurify 消毒

import { ref, watch, onScopeDispose, type Ref } from 'vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'

export function useChatMarkdown(source: Ref<string>, streaming: Ref<boolean>) {
  const renderedHtml = ref('')
  let rafId = 0

  function renderNow() {
    try {
      if (!source.value) {
        renderedHtml.value = ''
        return
      }
      const raw = marked.parse(source.value, { breaks: true }) as string
      renderedHtml.value = DOMPurify.sanitize(raw)
    } catch {
      // 渲染异常兜底：纯文本
      renderedHtml.value = `<pre>${escapeHtml(source.value)}</pre>`
    }
  }

  function scheduleRender() {
    if (rafId) return // 本帧已排队，合并
    rafId = requestAnimationFrame(() => {
      rafId = 0
      renderNow()
    })
  }

  // 流式中 rAF 节流渲染
  watch(source, () => {
    if (streaming.value) {
      scheduleRender()
    } else {
      // 非流式（已完成消息）：立即渲染
      renderNow()
    }
  }, { immediate: true })

  // 流结束后不再需要 rAF 节流
  watch(streaming, (isStreaming) => {
    if (!isStreaming) {
      if (rafId) {
        cancelAnimationFrame(rafId)
        rafId = 0
      }
      renderNow() // 最终渲染：确保不丢最后一个 token
    }
  })

  onScopeDispose(() => {
    if (rafId) cancelAnimationFrame(rafId)
  })

  return { renderedHtml }
}

function escapeHtml(str: string): string {
  return str
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}
