import { ref } from 'vue'
import { Marked } from 'marked'
import { createHighlighter } from 'shiki'
import katex from 'katex'

export interface TocItem {
  id: string
  text: string
  level: number
}

// \u2500\u2500 LaTeX math protection \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500

interface MathEntry {
  math: string
  display: boolean
}

/**
 * Protect code blocks and math formulas with placeholders before marked parsing.
 * This prevents marked from treating $ inside code as math delimiters,
 * and prevents marked from interfering with LaTeX syntax.
 */
function protectMath(markdown: string): { processed: string; mathBlocks: Map<string, MathEntry> } {
  const mathBlocks = new Map<string, MathEntry>()
  let mathId = 0

  // Step 1: Protect fenced code blocks (``` ... ```)
  const fencedBlocks: string[] = []
  let processed = markdown.replace(/(^|\n)(```[\s\S]*?\n```)/g, (_match, newline: string, code: string) => {
    const key = `\x00FENCED\x00${fencedBlocks.length}\x00`
    fencedBlocks.push(code)
    return `${newline}${key}`
  })

  // Step 2: Protect inline code spans (`...`)
  const inlineCodes: string[] = []
  processed = processed.replace(/`([^`]+)`/g, (_match, code: string) => {
    const key = `\x00CODE\x00${inlineCodes.length}\x00`
    inlineCodes.push(code)
    return key
  })

  // Step 3: Protect escaped dollar signs (\$)
  let escapedCount = 0
  processed = processed.replace(/\\\$/g, () => {
    const key = `\x00ESCDOLLAR\x00${escapedCount++}\x00`
    return key
  })

  // Step 4: Protect display math ($$...$$) \u2014 can span multiple lines
  processed = processed.replace(/\$\$([\s\S]*?)\$\$/g, (_match, math: string) => {
    const key = `\x00MATH\x00${mathId++}\x00`
    mathBlocks.set(key, { math: math.trim(), display: true })
    return `\n${key}\n`
  })

  // Step 5: Protect inline math ($...$) \u2014 single line only
  processed = processed.replace(/(?<!\$)\$(?!\$)([^$\n]+?)(?<!\$)\$(?!\$)/g, (_match, math: string) => {
    if (!math.trim()) return _match // skip empty math like $$
    const key = `\x00MATH\x00${mathId++}\x00`
    mathBlocks.set(key, { math: math.trim(), display: false })
    return key
  })

  // Step 6: Restore code blocks and inline code (their $ signs are now safe)
  processed = processed.replace(/\x00FENCED\x00(\d+)\x00/g, (_m, i: string) => fencedBlocks[parseInt(i)] ?? '')
  processed = processed.replace(/\x00CODE\x00(\d+)\x00/g, (_m, i: string) => inlineCodes[parseInt(i)] ?? '')

  // Step 7: Restore escaped dollar signs as literal $
  processed = processed.replace(/\x00ESCDOLLAR\x00\d+\x00/g, '$')

  return { processed, mathBlocks }
}

/**
 * Render LaTeX math placeholders in HTML with KaTeX.
 */
function renderMathInHtml(html: string, mathBlocks: Map<string, MathEntry>): string {
  let result = html
  for (const [key, { math, display }] of mathBlocks) {
    try {
      const rendered = katex.renderToString(math, {
        displayMode: display,
        throwOnError: false,
        trust: false,
        strict: false,
      })
      result = result.replace(key, rendered)
    } catch {
      // If KaTeX fails, show the raw LaTeX
      result = result.replace(key, escapeHtml(math))
    }
  }
  return result
}

// \u2500\u2500 Marked setup \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500

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
      const { processed, mathBlocks } = protectMath(markdown)
      const html = await markedInstance.parse(processed)
      let result = typeof html === 'string' ? html : ''
      result = renderMathInHtml(result, mathBlocks)
      renderedHtml.value = result
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
