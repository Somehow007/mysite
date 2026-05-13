import { ref } from 'vue'
import { Marked } from 'marked'
import { codeToHtml } from 'shiki'

export interface TocItem {
  id: string
  text: string
  level: number
}

const markedInstance = new Marked()

markedInstance.setOptions({
  gfm: true,
  breaks: false,
})

const renderer = new markedInstance.Renderer()

renderer.heading = function ({ text, depth }) {
  const rawText = String(text)
  const id = rawText
    .toLowerCase()
    .replace(/[^\w\u4e00-\u9fa5]+/g, '-')
    .replace(/^-+|-+$/g, '')
  return `<h${depth} id="${escapeHtml(id)}">${escapeHtml(rawText)}</h${depth}>`
}

renderer.code = function ({ text, lang }) {
  const language = lang || ''
  return `<pre><code class="language-${language}" data-language="${language}">${escapeHtml(text)}</code></pre>`
}

function escapeHtml(str: string): string {
  return str
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

markedInstance.use({ renderer })

async function highlightCodeBlocks(container: HTMLElement) {
  const codeBlocks = container.querySelectorAll('pre code[data-language]')
  for (const block of codeBlocks) {
    const lang = block.getAttribute('data-language') || ''
    const code = block.textContent || ''
    try {
      const html = await codeToHtml(code, {
        lang: lang || 'text',
        theme: document.documentElement.classList.contains('dark') ? 'github-dark' : 'github-light',
      })
      const pre = block.parentElement
      if (pre) {
        pre.innerHTML = html
        pre.classList.add('shiki')
      }
    } catch {
      block.classList.add('shiki')
    }
  }
}

export function useMarkdown() {
  const renderedHtml = ref('')
  const toc = ref<TocItem[]>([])
  const rendering = ref(false)

  function extractToc(html: string) {
    const headingRegex = /<h([1-6])\s+id="([^"]*)">(.*?)<\/h\1>/g
    const items: TocItem[] = []
    let match: RegExpExecArray | null
    while ((match = headingRegex.exec(html)) !== null) {
      items.push({
        level: Number(match[1]),
        id: match[2] ?? '',
        text: (match[3] ?? '').replace(/<[^>]*>/g, ''),
      })
    }
    return items
  }

  async function render(markdown: string) {
    if (!markdown) {
      renderedHtml.value = ''
      toc.value = []
      return
    }

    rendering.value = true
    try {
      const html = await markedInstance.parse(markdown)
      renderedHtml.value = typeof html === 'string' ? html : ''
      toc.value = extractToc(renderedHtml.value)
    } catch {
      renderedHtml.value = ''
      toc.value = []
    } finally {
      rendering.value = false
    }
  }

  async function applyHighlighting(container: HTMLElement) {
    await highlightCodeBlocks(container)
  }

  return {
    renderedHtml,
    toc,
    rendering,
    render,
    applyHighlighting,
  }
}
