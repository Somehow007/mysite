import { ref } from 'vue'
import { Marked } from 'marked'
import Prism from 'prismjs'
import katex from 'katex'

// Prism language grammars — loaded at build time, tree-shaken by Vite
import 'prismjs/components/prism-javascript'
import 'prismjs/components/prism-typescript'
import 'prismjs/components/prism-python'
import 'prismjs/components/prism-java'
import 'prismjs/components/prism-css'
import 'prismjs/components/prism-markup' // html / xml
import 'prismjs/components/prism-json'
import 'prismjs/components/prism-yaml'
import 'prismjs/components/prism-bash'
import 'prismjs/components/prism-markdown'
import 'prismjs/components/prism-sql'
import 'prismjs/components/prism-go'
import 'prismjs/components/prism-rust'
import 'prismjs/components/prism-c'
import 'prismjs/components/prism-cpp'
import 'prismjs/components/prism-diff'
import 'prismjs/components/prism-docker'
import 'prismjs/components/prism-nginx'

export interface TocItem {
  id: string
  text: string
  level: number
  hasChildren?: boolean
}

// ── LaTeX math protection ──────────────────────────────────────────────

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

  // Step 1: Protect fenced code blocks (``` ... ```), including those
  // nested inside blockquotes (each line prefixed with "> ").
  const fencedBlocks: string[] = []
  let processed = markdown.replace(/(^|\n)((?:> )*```[\s\S]*?\n(?:> )*```)/g, (_match, newline: string, code: string) => {
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

  // Step 4: Protect display math ($$...$$) — can span multiple lines
  processed = processed.replace(/\$\$([\s\S]*?)\$\$/g, (_match, math: string) => {
    const key = `\x00MATH\x00${mathId++}\x00`
    mathBlocks.set(key, { math: math.trim(), display: true })
    return `\n${key}\n`
  })

  // Step 5: Protect inline math ($...$) — single line only
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

// ── Callout rendering ─────────────────────────────────────────────────

interface CalloutConfig {
  icon: string
  color: string
}

const CALLOUT_CONFIG: Record<string, CalloutConfig> = {
  note: { icon: '📝', color: '#448aff' },
  info: { icon: 'ℹ️', color: '#448aff' },
  todo: { icon: '☑️', color: '#448aff' },
  tip: { icon: '💡', color: '#00c853' },
  success: { icon: '✅', color: '#00c853' },
  check: { icon: '✔️', color: '#00c853' },
  done: { icon: '🏁', color: '#00c853' },
  warning: { icon: '⚠️', color: '#ff9100' },
  caution: { icon: '⚠️', color: '#ff9100' },
  question: { icon: '❓', color: '#ff9100' },
  attention: { icon: '👀', color: '#ff9100' },
  error: { icon: '❌', color: '#ff1744' },
  danger: { icon: '⚡', color: '#ff1744' },
  failure: { icon: '🚫', color: '#ff1744' },
  bug: { icon: '🐛', color: '#ff1744' },
  example: { icon: '📋', color: '#7c4dff' },
  quote: { icon: '💬', color: '#9e9e9e' },
  cite: { icon: '📖', color: '#9e9e9e' },
  abstract: { icon: '📄', color: '#9e9e9e' },
  summary: { icon: '📊', color: '#9e9e9e' },
  tldr: { icon: '⚡', color: '#9e9e9e' },
}

/**
 * Transform <blockquote> elements that start with [!TYPE] into styled callout divs.
 *
 * The first <p> inside such a blockquote contains both the callout title
 * (first line after [!TYPE]) and optionally body text (subsequent lines).
 * Subsequent block elements (<pre>, <p>, <ul>, etc.) are also part of the body.
 */
function renderCallouts(html: string): string {
  // HTML entity decoder — reverses only the entities that escapeHtml & marked produce
  const decodeEntities = (s: string): string =>
    s
      .replace(/&amp;/g, '&')
      .replace(/&lt;/g, '<')
      .replace(/&gt;/g, '>')
      .replace(/&quot;/g, '"')
      .replace(/&#39;/g, "'")

  const CALLOUT_TYPE_RE =
    /^\s*<p>\[!(NOTE|INFO|TODO|TIP|SUCCESS|CHECK|DONE|QUESTION|WARNING|CAUTION|ATTENTION|FAILURE|FAIL|MISSING|ERROR|DANGER|BUG|EXAMPLE|QUOTE|CITE|ABSTRACT|SUMMARY|TLDR)\]([\s\S]*?)<\/p>([\s\S]*)$/i

  return html.replace(
    /<blockquote>([\s\S]*?)<\/blockquote>/g,
    (match: string, content: string) => {
      const calloutMatch = content.match(CALLOUT_TYPE_RE)
      if (!calloutMatch) return match // Regular blockquote — leave as-is

      const [, type, titlePart, rest] = calloutMatch
      const typeLower = type!.toLowerCase()
      const config = CALLOUT_CONFIG[typeLower] ?? CALLOUT_CONFIG['note']!

      // The titlePart contains everything from "] " to the closing </p>,
      // possibly including newlines. Split at the first newline:
      //   line 0 → title,  lines 1+ → body text (re-wrapped in <p>).
      const afterBracket = titlePart!.replace(/^\]\s?/, '') // strip "] " or "]"
      const firstBr = afterBracket.indexOf('\n')
      let rawTitle: string
      let bodyFromFirstP = ''
      if (firstBr >= 0) {
        rawTitle = afterBracket.slice(0, firstBr)
        bodyFromFirstP = afterBracket.slice(firstBr + 1)
      } else {
        rawTitle = afterBracket
      }
      rawTitle = rawTitle.trim()

      // Decode HTML entities that marked produced (e.g. &quot; → ")
      // before re-escaping for safe HTML output.
      const title = rawTitle ? decodeEntities(rawTitle) : ''

      const titleHtml = title
        ? `<span class="callout-title-text">${escapeHtml(title)}</span>`
        : `<span class="callout-title-text callout-title-placeholder">${type}</span>`

      // Assemble body: inline text from the first <p> + any trailing block elements
      const bodyParts: string[] = []
      if (bodyFromFirstP.trim()) {
        bodyParts.push(`<p>${bodyFromFirstP.trim()}</p>`)
      }
      if (rest && rest.trim()) {
        bodyParts.push(rest.trim())
      }
      const bodyHtml = bodyParts.join('\n')

      return `<div class="callout callout-${typeLower}" style="--callout-color: ${config.color}">
        <div class="callout-header">
          <span class="callout-icon">${config.icon}</span>
          ${titleHtml}
        </div>
        <div class="callout-body">${bodyHtml}</div>
      </div>`
    },
  )
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

// ── Marked setup ──────────────────────────────────────────────────────

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
    .replace(/[^\w一-龥]+/g, '-')
    .replace(/^-+|-+$/g, '')
  return `<h${depth} id="${escapeHtml(id)}">${escapeHtml(rawText)}</h${depth}>`
}

// Map common markdown fence names to Prism language keys
const langAliases: Record<string, string> = {
  'sh': 'bash',
  'shell': 'bash',
  'js': 'javascript',
  'jsx': 'javascript',
  'ts': 'typescript',
  'tsx': 'typescript',
  'py': 'python',
  'yml': 'yaml',
  'c++': 'cpp',
}

renderer.code = function ({ text, lang }) {
  const language = lang || ''
  const prismLang = langAliases[language] || language
  let highlighted: string
  try {
    const grammar = prismLang ? Prism.languages[prismLang] : undefined
    if (grammar) {
      highlighted = Prism.highlight(text, grammar, prismLang)
    } else {
      highlighted = escapeHtml(text)
    }
  } catch {
    highlighted = escapeHtml(text)
  }
  return `<pre class="code-block"><code class="language-${escapeHtml(language)}" data-language="${escapeHtml(language)}">${highlighted}</code></pre>`
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

// ── Public composable ─────────────────────────────────────────────────

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
    // Mark items that have children (next item has a deeper level)
    for (let i = 0; i < items.length - 1; i++) {
      const current = items[i]
      const next = items[i + 1]
      if (current && next && next.level > current.level) {
        current.hasChildren = true
      }
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
      result = renderCallouts(result)            // Step 1: transform [!TYPE] blockquotes → callout divs
      result = renderMathInHtml(result, mathBlocks) // Step 2: render LaTeX
      renderedHtml.value = result
      toc.value = extractToc(renderedHtml.value)
    } catch {
      renderedHtml.value = ''
      toc.value = []
    } finally {
      rendering.value = false
    }
  }

  return {
    renderedHtml,
    toc,
    rendering,
    render,
  }
}
