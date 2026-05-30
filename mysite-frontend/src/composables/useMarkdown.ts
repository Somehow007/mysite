import { ref } from 'vue'
import { Marked } from 'marked'
import { createHighlighter } from 'shiki'

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
  return `<pre><code class="language-${escapeHtml(language)}" data-language="${escapeHtml(language)}">${escapeHtml(text)}</code></pre>`
}

renderer.image = function ({ href, title: _title, text }) {
  const src = escapeHtml(href || '')
  const alt = escapeHtml(text || '')
  return `<span class="img-wrapper"><img src="${src}" alt="${alt}" loading="lazy" decoding="async" /></span>`
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

type ShikiHighlighter = Awaited<ReturnType<typeof createHighlighter>>

let highlighterInstance: ShikiHighlighter | null = null
let highlighterInitPromise: Promise<ShikiHighlighter> | null = null

async function getHighlighter(): Promise<ShikiHighlighter> {
  if (highlighterInstance) return highlighterInstance
  if (highlighterInitPromise) return highlighterInitPromise

  highlighterInitPromise = createHighlighter({
    themes: ['github-light', 'github-dark'],
    langs: [
      'javascript', 'typescript', 'python', 'java', 'css', 'html',
      'json', 'bash', 'markdown', 'xml', 'yaml', 'sql', 'go', 'rust',
      'c', 'cpp', 'shell', 'diff', 'plaintext',
    ],
  }).then((h) => {
    highlighterInstance = h
    return h
  })

  return highlighterInitPromise
}

async function highlightCodeBlocks(container: HTMLElement) {
  const highlighter = await getHighlighter()
  const isDark = document.documentElement.classList.contains('dark')
  const theme = isDark ? 'github-dark' : 'github-light'

  const codeBlocks = container.querySelectorAll('pre code[data-language]')
  const promises: Promise<void>[] = []

  for (const block of codeBlocks) {
    const lang = block.getAttribute('data-language') || ''
    const code = block.textContent || ''

    promises.push(
      (async () => {
        try {
          const loadedLangs = highlighter.getLoadedLanguages()
          const effectiveLang = loadedLangs.includes(lang) ? lang : 'text'
          const html = highlighter.codeToHtml(code, { lang: effectiveLang, theme })
          const pre = block.parentElement
          if (pre) {
            pre.innerHTML = html
            pre.classList.add('shiki')
          }
        } catch {
          block.classList.add('shiki')
        }
      })(),
    )
  }

  await Promise.all(promises)
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
