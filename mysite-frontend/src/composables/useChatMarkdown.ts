// composables/useChatMarkdown.ts
// 聊天专用 Markdown 渲染 —— 复用项目文章渲染管线（Prism 高亮、KaTeX 数学、GFM 表格、
// Obsidian Callout），适配聊天流式场景。流式中 rAF 节流 + 数学占位符回退为纯文本；
// 流结束后一次性执行 KaTeX 渲染。
//
// 与 useMarkdown 的关系：
//   useMarkdown 面向文章页：async render、TOC 提取、完整后处理
//   本模块面向聊天流式：同步渲染 + rAF 节流 + 两阶段渲染（流式中轻量 / 完成后完整）

import { ref, watch, onScopeDispose, type Ref } from 'vue'
import { Marked } from 'marked'
import Prism from 'prismjs'
import katex from 'katex'
import DOMPurify from 'dompurify'

// ── Prism 语言包（与 useMarkdown.ts 保持一致）────────────────────

import 'prismjs/components/prism-javascript'
import 'prismjs/components/prism-typescript'
import 'prismjs/components/prism-python'
import 'prismjs/components/prism-java'
import 'prismjs/components/prism-css'
import 'prismjs/components/prism-markup'
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

// ── 工具函数 ──────────────────────────────────────────────────────

function escapeHtml(str: string): string {
  return str
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

// ── LaTeX 数学保护（与 useMarkdown.ts 完全一致的逻辑）────────────

interface MathEntry {
  math: string
  display: boolean
}

/**
 * 保护代码块与数学公式，用占位符替换，防止 marked 将 $ 当作数学分隔符、
 * 或将下划线等 LaTeX 语法误解析为 Markdown 格式。
 */
function protectMath(markdown: string): { processed: string; mathBlocks: Map<string, MathEntry> } {
  const mathBlocks = new Map<string, MathEntry>()
  let mathId = 0

  // Step 1: 保护 fenced code blocks（``` ... ```），含嵌套在 blockquote 中的
  const fencedBlocks: string[] = []
  let processed = markdown.replace(
    /(^|\n)((?:> )*```[\s\S]*?\n(?:> )*```)/g,
    (_match, newline: string, code: string) => {
      const key = `\x00FENCED\x00${fencedBlocks.length}\x00`
      fencedBlocks.push(code)
      return `${newline}${key}`
    },
  )

  // Step 2: 保护 inline code spans（`...`）
  const inlineCodes: string[] = []
  processed = processed.replace(/`([^`]+)`/g, (_match, code: string) => {
    const key = `\x00CODE\x00${inlineCodes.length}\x00`
    inlineCodes.push(code)
    return key
  })

  // Step 3: 保护转义美元符号（\$）
  let escapedCount = 0
  processed = processed.replace(/\\\$/g, () => {
    const key = `\x00ESCDOLLAR\x00${escapedCount++}\x00`
    return key
  })

  // Step 4: 保护 display math（$$...$$）—— 可跨行
  processed = processed.replace(/\$\$([\s\S]*?)\$\$/g, (_match, math: string) => {
    const key = `\x00MATH\x00${mathId++}\x00`
    mathBlocks.set(key, { math: math.trim(), display: true })
    return `\n${key}\n`
  })

  // Step 5: 保护 inline math（$...$）—— 仅单行
  processed = processed.replace(
    /(?<!\$)\$(?!\$)([^$\n]+?)(?<!\$)\$(?!\$)/g,
    (_match, math: string) => {
      if (!math.trim()) return _match
      const key = `\x00MATH\x00${mathId++}\x00`
      mathBlocks.set(key, { math: math.trim(), display: false })
      return key
    },
  )

  // Step 6-7: 恢复代码块与 inline code（它们的 $ 现在安全了）
  processed = processed.replace(/\x00FENCED\x00(\d+)\x00/g, (_m, i: string) => fencedBlocks[parseInt(i)] ?? '')
  processed = processed.replace(/\x00CODE\x00(\d+)\x00/g, (_m, i: string) => inlineCodes[parseInt(i)] ?? '')
  processed = processed.replace(/\x00ESCDOLLAR\x00\d+\x00/g, '$')

  return { processed, mathBlocks }
}

/**
 * 将数学占位符恢复为人类可读的纯文本（用于流式中，此时不能跑 KaTeX）。
 * display math 用 $$...$$ 包裹，inline math 用 $...$ 包裹。
 */
function restoreMathPlaceholders(html: string, mathBlocks: Map<string, MathEntry>): string {
  let result = html
  for (const [key, { math, display }] of mathBlocks) {
    const escaped = escapeHtml(math)
    const replacement = display
      ? `<span class="chat-math chat-math-display">$$${escaped}$$</span>`
      : `<span class="chat-math chat-math-inline">$${escaped}$</span>`
    result = result.replace(key, replacement)
  }
  return result
}

/**
 * 用 KaTeX 渲染数学占位符（仅用于流结束后的最终渲染）。
 * 失败时回退为原始 LaTeX 文本。
 */
function renderMathWithKatex(html: string, mathBlocks: Map<string, MathEntry>): string {
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
      // KaTeX 失败时回退为纯文本
      const escaped = escapeHtml(math)
      result = result.replace(key, display ? `$$${escaped}$$` : `$${escaped}$`)
    }
  }
  return result
}

// ── Callout 渲染（与 useMarkdown.ts 一致）────────────────────────

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

function renderCallouts(html: string): string {
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
      if (!calloutMatch) return match

      const [, type, titlePart, rest] = calloutMatch
      const typeLower = type!.toLowerCase()
      const config = CALLOUT_CONFIG[typeLower] ?? CALLOUT_CONFIG['note']!

      const afterBracket = titlePart!.replace(/^\]\s?/, '')
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

      const title = rawTitle ? decodeEntities(rawTitle) : ''
      const titleHtml = title
        ? `<span class="callout-title-text">${escapeHtml(title)}</span>`
        : `<span class="callout-title-text callout-title-placeholder">${type}</span>`

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

// ── Marked 实例（与文章渲染共享配置）─────────────────────────────

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

const markedInstance = new Marked()

markedInstance.setOptions({
  gfm: true,
  breaks: true,
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

markedInstance.use({ renderer })

// ── 公开 composable ────────────────────────────────────────────────

export function useChatMarkdown(source: Ref<string>, streaming: Ref<boolean>) {
  const renderedHtml = ref('')
  let rafId = 0

  /**
   * 轻量渲染（流式中使用）：protectMath → marked → 恢复数学占位符为纯文本 → DOMPurify。
   * 目的：让用户看到可读的数学源码（$x^2$），而不是占位符乱码。
   */
  function renderLightweight() {
    try {
      if (!source.value) {
        renderedHtml.value = ''
        return
      }
      const { processed, mathBlocks } = protectMath(source.value)
      const raw = markedInstance.parse(processed, { breaks: true }) as string
      const withMath = restoreMathPlaceholders(raw, mathBlocks)
      renderedHtml.value = DOMPurify.sanitize(withMath)
    } catch {
      renderedHtml.value = `<pre>${escapeHtml(source.value)}</pre>`
    }
  }

  /**
   * 完整渲染（流结束后使用）：protectMath → marked → callout → KaTeX → DOMPurify。
   * 一次性执行 Prism（已在 marked renderer 中完成）和 KaTeX 数学渲染。
   */
  function renderFull() {
    try {
      if (!source.value) {
        renderedHtml.value = ''
        return
      }
      const { processed, mathBlocks } = protectMath(source.value)
      const raw = markedInstance.parse(processed, { breaks: true }) as string
      const withCallouts = renderCallouts(raw)
      const withMath = renderMathWithKatex(withCallouts, mathBlocks)
      renderedHtml.value = DOMPurify.sanitize(withMath)
    } catch {
      renderedHtml.value = `<pre>${escapeHtml(source.value)}</pre>`
    }
  }

  function scheduleRender() {
    if (rafId) return // 本帧已排队，合并
    rafId = requestAnimationFrame(() => {
      rafId = 0
      renderLightweight()
    })
  }

  // ── 响应式触发 ─────────────────────────────────────────────────

  // 流式中：rAF 节流渲染（轻量模式：有 Prism 高亮，无 KaTeX）
  // 非流式：立即完整渲染（Prism + KaTeX + Callout）
  watch(source, () => {
    if (streaming.value) {
      scheduleRender()
    } else {
      renderFull()
    }
  }, { immediate: true })

  // 流结束：取消未执行的 rAF，执行最终完整渲染
  watch(streaming, (isStreaming) => {
    if (!isStreaming) {
      if (rafId) {
        cancelAnimationFrame(rafId)
        rafId = 0
      }
      renderFull()
    }
  })

  onScopeDispose(() => {
    if (rafId) cancelAnimationFrame(rafId)
  })

  return { renderedHtml }
}
