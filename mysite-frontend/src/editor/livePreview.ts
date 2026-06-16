/**
 * CodeMirror 6 Live Preview / WYSIWYG extension.
 *
 * Obsidian-style: hides Markdown syntax, replacing it with styled visual
 * representations that match the published browser output.
 *
 * Key behaviour:
 *   - Block-level elements (code/callout/math): when cursor is inside the
 *     block, the entire block shows raw syntax
 *   - Line-level elements (headings/lists/blockquotes): when cursor is on
 *     that line, the line shows raw syntax
 *   - Inline elements (bold/italic/code/links/LaTeX): when cursor is INSIDE
 *     a specific inline span, only THAT span shows raw syntax; all other
 *     inline spans on the same line are still rendered
 *
 * Architecture:
 *   1. computeBlocks()  — single-pass block detection
 *   2. *Decos()         — pure functions returning PendingDeco[]
 *   3. parseInlineSpans — returns spans with {from,to} for cursor filtering
 *   4. build()          — collects, filters by cursor, sorts, writes builder
 *   5. InlineDecoCache  — LRU cache keyed by line text hash
 */

import {
  ViewPlugin,
  ViewUpdate,
  Decoration,
  WidgetType,
  EditorView,
} from '@codemirror/view'
import { RangeSetBuilder, Text } from '@codemirror/state'
import katex from 'katex'

// ═══════════════════════════════════════════════════════════════
// Types
// ═══════════════════════════════════════════════════════════════

interface CodeBlock { type: 'code'; startLine: number; endLine: number; lang: string }
interface CalloutBlock { type: 'callout'; startLine: number; endLine: number; calloutType: string }
interface MathBlock { type: 'math'; startLine: number; endLine: number; content: string }
type Block = CodeBlock | CalloutBlock | MathBlock

interface PendingDeco {
  from: number
  to: number
  deco: Decoration
}

/** An inline syntax span with its absolute position range. */
interface InlineSpan {
  from: number
  to: number
  decos: PendingDeco[]
}

// ═══════════════════════════════════════════════════════════════
// Callout maps
// ═══════════════════════════════════════════════════════════════

const CALLOUT_COLORS: Record<string, string> = {
  NOTE: '#448aff', INFO: '#448aff', TODO: '#448aff',
  TIP: '#00c853', SUCCESS: '#00c853', CHECK: '#00c853', DONE: '#00c853',
  WARNING: '#ff9100', CAUTION: '#ff9100', QUESTION: '#ff9100', ATTENTION: '#ff9100',
  ERROR: '#ff1744', DANGER: '#ff1744', FAILURE: '#ff1744', BUG: '#ff1744',
  EXAMPLE: '#7c4dff', QUOTE: '#9e9e9e', CITE: '#9e9e9e', ABSTRACT: '#9e9e9e', SUMMARY: '#9e9e9e', TLDR: '#9e9e9e',
}

const CALLOUT_ICONS: Record<string, string> = {
  NOTE: '📝', INFO: 'ℹ️', TODO: '☑️',
  TIP: '💡', SUCCESS: '✅', CHECK: '✔️', DONE: '🏁',
  WARNING: '⚠️', CAUTION: '⚠️', QUESTION: '❓', ATTENTION: '👀',
  ERROR: '❌', DANGER: '⚡', FAILURE: '🚫', BUG: '🐛',
  EXAMPLE: '📋', QUOTE: '💬', CITE: '📖', ABSTRACT: '📄', SUMMARY: '📊', TLDR: '⚡',
}

// ═══════════════════════════════════════════════════════════════
// Widgets
// ═══════════════════════════════════════════════════════════════

class BulletWidget extends WidgetType {
  toDOM() { const s = document.createElement('span'); s.className = 'cm-lp-bullet'; s.textContent = '•'; s.style.cssText = 'margin-right:0.5em;color:var(--text-muted)'; return s }
}
class OrderedNumberWidget extends WidgetType {
  constructor(private n: number) { super() }
  toDOM() { const s = document.createElement('span'); s.className = 'cm-lp-ol'; s.textContent = `${this.n}.`; s.style.cssText = 'margin-right:0.5em;color:var(--text-muted);font-variant-numeric:tabular-nums'; return s }
}
class TaskCheckboxWidget extends WidgetType {
  constructor(private checked: boolean) { super() }
  toDOM() { const s = document.createElement('span'); s.className = 'cm-lp-task' + (this.checked ? ' checked' : ''); s.innerHTML = `<input type="checkbox" ${this.checked ? 'checked ' : ''}disabled tabindex="-1">`; s.style.cssText = 'margin-right:0.5em'; return s }
}
class CalloutHeaderWidget extends WidgetType {
  constructor(private cType: string, private color: string, private icon: string) { super() }
  toDOM() {
    const s = document.createElement('span')
    s.className = 'cm-lp-callout-hdr'
    s.style.cssText = 'display:inline-flex;align-items:center;gap:0.5rem;font-family:var(--font-sans,sans-serif)'
    const iconSpan = document.createElement('span')
    iconSpan.style.cssText = `font-size:1rem;line-height:1;flex-shrink:0;color:${this.color}`
    iconSpan.textContent = this.icon
    const titleSpan = document.createElement('span')
    titleSpan.className = 'cm-lp-callout-title'
    titleSpan.style.cssText = 'font-weight:600;font-size:0.9375rem;color:var(--text-primary)'
    titleSpan.textContent = this.cType
    s.appendChild(iconSpan)
    s.appendChild(titleSpan)
    return s
  }
}
class CodeFenceLabelWidget extends WidgetType {
  constructor(private lang: string) { super() }
  toDOM() { const s = document.createElement('span'); s.className = 'cm-lp-fence-label'; if (this.lang) { s.textContent = this.lang; s.style.cssText = 'font-size:0.7em;color:var(--text-muted);text-transform:uppercase;letter-spacing:0.05em;font-weight:500' } else { s.style.cssText = 'display:inline-block;height:0.4em' }; return s }
}
class KaTeXInlineWidget extends WidgetType {
  constructor(private latex: string) { super() }
  eq(other: KaTeXInlineWidget) { return this.latex === other.latex }
  toDOM() { const s = document.createElement('span'); s.className = 'cm-lp-katex-inline'; s.style.cssText = 'display:inline;vertical-align:middle;font-size:1.1em;line-height:1.8'; try { katex.render(this.latex, s, { throwOnError: false, displayMode: false, strict: false }) } catch { s.textContent = `$${this.latex}$` }; return s }
}
class KaTeXDisplayWidget extends WidgetType {
  constructor(private latex: string) { super() }
  eq(other: KaTeXDisplayWidget) { return this.latex === other.latex }
  get estimatedHeight() { return 40 }
  toDOM() { const d = document.createElement('div'); d.className = 'cm-lp-katex-display'; d.style.cssText = 'text-align:center;padding:0.5em 0;overflow-x:auto;overflow-y:visible;margin:1.5em 0 2.2em'; try { katex.render(this.latex, d, { throwOnError: false, displayMode: true, strict: false }) } catch { d.textContent = `$$${this.latex}$$` }; return d }
}

// ═══════════════════════════════════════════════════════════════
// Inline decoration cache — keyed by line text
// ═══════════════════════════════════════════════════════════════

class InlineDecoCache {
  private cache = new Map<string, InlineSpan[]>()
  private static MAX_SIZE = 256

  get(text: string): InlineSpan[] | undefined {
    return this.cache.get(text)
  }

  set(text: string, spans: InlineSpan[]): void {
    if (this.cache.size >= InlineDecoCache.MAX_SIZE) {
      const firstKey = this.cache.keys().next().value
      if (firstKey !== undefined) this.cache.delete(firstKey)
    }
    this.cache.set(text, spans)
  }

  invalidate(): void {
    this.cache.clear()
  }
}

// ═══════════════════════════════════════════════════════════════
// Block precomputation
// ═══════════════════════════════════════════════════════════════

function computeBlocks(doc: Text): Block[] {
  const blocks: Block[] = []
  let inCode = false, codeStart = 0, codeLang = ''
  let inMath = false, mathStart = 0, mathLines: string[] = []
  let inCallout = false, calloutStart = 0, calloutType = ''

  for (let i = 1; i <= doc.lines; i++) {
    const text = doc.line(i).text
    const trimmed = text.trim()

    if (inCode) {
      if (/^\s*```/.test(text)) { blocks.push({ type: 'code', startLine: codeStart, endLine: i, lang: codeLang }); inCode = false }
      continue
    }
    if (inMath) {
      if (trimmed === '$$') { blocks.push({ type: 'math', startLine: mathStart, endLine: i, content: mathLines.join('\n') }); inMath = false; mathLines = [] }
      else mathLines.push(text)
      continue
    }
    if (inCallout) {
      if (/^>/.test(text)) {
        const nc = text.match(/^>\s*\[!(\w+)\]/)
        if (nc) { blocks.push({ type: 'callout', startLine: calloutStart, endLine: i - 1, calloutType }); calloutStart = i; calloutType = nc[1]!.toUpperCase() }
        continue
      }
      blocks.push({ type: 'callout', startLine: calloutStart, endLine: i - 1, calloutType }); inCallout = false
    }

    const fm = text.match(/^\s*```(\w*)\s*$/)
    if (fm) { codeStart = i; codeLang = fm[1] ?? ''; inCode = true; continue }
    const sm = text.match(/^\$\$\s+(.+?)\s+\$\$$/)
    if (sm) { blocks.push({ type: 'math', startLine: i, endLine: i, content: sm[1]! }); continue }
    if (trimmed === '$$') { mathStart = i; inMath = true; mathLines = []; continue }
    const cm = text.match(/^>\s*\[!(\w+)\]/)
    if (cm) { calloutStart = i; calloutType = cm[1]!.toUpperCase(); inCallout = true; continue }
  }

  const last = doc.lines
  if (inCode) blocks.push({ type: 'code', startLine: codeStart, endLine: last, lang: codeLang })
  if (inMath) blocks.push({ type: 'math', startLine: mathStart, endLine: last, content: mathLines.join('\n') })
  if (inCallout) blocks.push({ type: 'callout', startLine: calloutStart, endLine: last, calloutType })
  return blocks
}

function findBlock(lineNum: number, blocks: Block[]): Block | null {
  let lo = 0, hi = blocks.length - 1, idx = -1
  while (lo <= hi) { const mid = (lo + hi) >>> 1; if (blocks[mid]!.startLine <= lineNum) { idx = mid; lo = mid + 1 } else { hi = mid - 1 } }
  if (idx === -1) return null
  const b = blocks[idx]!
  return lineNum <= b.endLine ? b : null
}

// ═══════════════════════════════════════════════════════════════
// Per-line decoration — pure functions returning PendingDeco[]
// ═══════════════════════════════════════════════════════════════

const HEADING_SIZES = [1.875, 1.5, 1.25, 1.125, 1.0, 0.875]

function headingDecos(text: string, from: number): PendingDeco[] {
  const m = text.match(/^(#{1,6})\s/)
  if (!m) return []
  const level = m[1]!.length
  const markerLen = m[0].length
  const size = HEADING_SIZES[level - 1]!
  const markParts = [`font-size:${size}rem`, 'font-weight:600', 'color:var(--text-primary)', 'line-height:1.3', 'font-family:var(--font-sans,sans-serif)', 'text-decoration:none', 'border-bottom:none']
  if (level === 2) markParts.push('padding-bottom:0.3rem', 'border-bottom:1px solid var(--border)')
  const lineParts = ['margin-top:2em', 'margin-bottom:0.5em']
  return [
    { from, to: from, deco: Decoration.line({ attributes: { style: lineParts.join(';') } }) },
    { from, to: from + markerLen, deco: Decoration.replace({}) },
    { from: from + markerLen, to: from + text.length, deco: Decoration.mark({ attributes: { style: markParts.join(';') } }) },
  ]
}

function listDecos(text: string, from: number): PendingDeco[] {
  const task = text.match(/^(\s*)([-*+])\s\[([ xX])\]\s/)
  if (task) return [{ from, to: from + task[0].length, deco: Decoration.replace({ widget: new TaskCheckboxWidget(task[3]!.toLowerCase() === 'x') }) }]
  const ol = text.match(/^(\s*)(\d+)\.\s/)
  if (ol) return [{ from, to: from + ol[0].length, deco: Decoration.replace({ widget: new OrderedNumberWidget(parseInt(ol[2]!, 10)) }) }]
  const ul = text.match(/^(\s*)([-*+])\s/)
  if (ul) return [{ from, to: from + ul[0].length, deco: Decoration.replace({ widget: new BulletWidget() }) }]
  return []
}

function blockquoteDecos(text: string, from: number): PendingDeco[] {
  const m = text.match(/^(\s*)>\s?/)
  if (!m) return []
  const markerLen = m[0].length
  return [
    { from, to: from, deco: Decoration.line({ attributes: { style: 'border-left:3px solid var(--accent);padding-left:1rem' } }) },
    { from, to: from + markerLen, deco: Decoration.replace({}) },
    { from: from + markerLen, to: from + text.length, deco: Decoration.mark({ attributes: { style: 'color:var(--text-muted);font-style:italic' } }) },
  ]
}

function codeBlockDecos(text: string, from: number, block: CodeBlock, ln: number): PendingDeco[] {
  const isFirst = ln === block.startLine, isLast = ln === block.endLine
  const result: PendingDeco[] = []

  // Line decoration: background + borders on ALL lines (including fence lines)
  const lineParts = [
    'background-color:var(--code-bg)',
    'border-left:1px solid var(--code-border)',
    'border-right:1px solid var(--code-border)',
    'padding-left:1.5rem',
    'padding-right:1.5rem',
    'font-family:var(--font-mono,ui-monospace,monospace)',
    'font-size:0.875rem',
    'line-height:1.7',
  ]
  if (isFirst) lineParts.push('border-top:1px solid var(--code-border)', 'border-top-left-radius:8px', 'border-top-right-radius:8px', 'padding-top:1.25rem')
  if (isLast) lineParts.push('border-bottom:1px solid var(--code-border)', 'border-bottom-left-radius:8px', 'border-bottom-right-radius:8px', 'padding-bottom:1.25rem')
  result.push({ from, to: from, deco: Decoration.line({ attributes: { style: lineParts.join(';') } }) })

  // Replace fence markers with label / hidden
  if (isFirst) {
    result.push({ from, to: from + text.length, deco: Decoration.replace({ widget: new CodeFenceLabelWidget(block.lang) }) })
  } else if (isLast) {
    result.push({ from, to: from + text.length, deco: Decoration.replace({ widget: new CodeFenceLabelWidget('') }) })
  }

  return result
}

function mathBlockDecos(text: string, from: number, block: MathBlock, ln: number): PendingDeco[] {
  if (block.startLine === block.endLine) return [{ from, to: from + text.length, deco: Decoration.replace({ widget: new KaTeXDisplayWidget(block.content) }) }]
  if (ln === block.startLine || ln === block.endLine) return [{ from, to: from + text.length, deco: Decoration.replace({}) }]
  if (ln === block.startLine + 1) return [{ from, to: from + text.length, deco: Decoration.replace({ widget: new KaTeXDisplayWidget(block.content) }) }]
  return [{ from, to: from + text.length, deco: Decoration.replace({}) }]
}

function calloutDecos(text: string, from: number, block: CalloutBlock, ln: number): PendingDeco[] {
  const color = CALLOUT_COLORS[block.calloutType] ?? '#448aff'
  const icon = CALLOUT_ICONS[block.calloutType] ?? '📝'
  const result: PendingDeco[] = []
  const isFirst = ln === block.startLine, isLast = ln === block.endLine
  const lineParts = [
    `border-left:4px solid ${color}`,
    `border-right:1px solid color-mix(in srgb, ${color} 20%, transparent)`,
    `background-color:color-mix(in srgb, ${color} 6%, transparent)`,
    'padding-left:1rem',
    'padding-right:1rem',
  ]
  if (isFirst) lineParts.push('border-top:1px solid color-mix(in srgb, ' + color + ' 20%, transparent)', 'border-top-left-radius:8px', 'border-top-right-radius:8px', 'padding-top:0.75rem')
  if (isLast) lineParts.push('border-bottom:1px solid color-mix(in srgb, ' + color + ' 20%, transparent)', 'border-bottom-left-radius:8px', 'border-bottom-right-radius:8px', 'padding-bottom:0.75rem')
  result.push({ from, to: from, deco: Decoration.line({ attributes: { style: lineParts.join(';') } }) })
  if (isFirst) {
    const hm = text.match(/^>\s*\[!(\w+)\]\s?(.*)$/)
    if (hm) {
      const type = hm[1]!.toUpperCase()
      const titleText = hm[2] ?? ''
      const bracketEnd = text.indexOf(']')
      const prefixEnd = bracketEnd + 1 + (text[bracketEnd + 1] === ' ' ? 1 : 0)
      result.push({ from, to: from + prefixEnd, deco: Decoration.replace({ widget: new CalloutHeaderWidget(type, color, icon) }) })
      if (titleText.trim()) result.push({ from: from + prefixEnd, to: from + text.length, deco: Decoration.mark({ attributes: { style: 'font-weight:600;font-size:0.9375rem;color:var(--text-primary)' } }) })
    } else {
      const m = text.match(/^>\s?/)
      if (m) result.push({ from, to: from + m[0].length, deco: Decoration.replace({}) })
    }
  } else {
    const m = text.match(/^(\s*)>\s?/)
    if (m) {
      result.push({ from, to: from + m[0].length, deco: Decoration.replace({}) })
      result.push({ from: from + m[0].length, to: from + text.length, deco: Decoration.mark({ attributes: { style: 'color:var(--text-secondary)' } }) })
    }
  }
  return result
}

// ═══════════════════════════════════════════════════════════════
// Inline span parsing — returns spans with absolute positions
// ═══════════════════════════════════════════════════════════════

function parseInlineSpans(text: string, lineFrom: number): InlineSpan[] {
  const spans: InlineSpan[] = []

  // Bold: **text**
  for (const m of text.matchAll(/\*\*(.+?)\*\*/g)) {
    const s = lineFrom + m.index!
    const e = s + m[0].length
    spans.push({
      from: s, to: e,
      decos: [
        { from: s, to: s + 2, deco: Decoration.replace({}) },
        { from: s + 2, to: e - 2, deco: Decoration.mark({ attributes: { style: 'font-weight:600;color:var(--text-primary)' } }) },
        { from: e - 2, to: e, deco: Decoration.replace({}) },
      ],
    })
  }

  // Italic: *text*
  for (const m of text.matchAll(/(?<!\*)\*(?!\*)(.+?)(?<!\*)\*(?!\*)/g)) {
    const s = lineFrom + m.index!
    const e = s + m[0].length
    spans.push({
      from: s, to: e,
      decos: [
        { from: s, to: s + 1, deco: Decoration.replace({}) },
        { from: s + 1, to: e - 1, deco: Decoration.mark({ attributes: { style: 'font-style:italic' } }) },
        { from: e - 1, to: e, deco: Decoration.replace({}) },
      ],
    })
  }

  // Inline code: `code`
  for (const m of text.matchAll(/`([^`]+)`/g)) {
    const s = lineFrom + m.index!
    const e = s + m[0].length
    spans.push({
      from: s, to: e,
      decos: [
        { from: s, to: s + 1, deco: Decoration.replace({}) },
        { from: s + 1, to: e - 1, deco: Decoration.mark({ attributes: { style: 'font-family:var(--font-mono,ui-monospace,monospace);font-size:0.875em;padding:0.2em 0.4em;border-radius:4px;background-color:var(--bg-code)' } }) },
        { from: e - 1, to: e, deco: Decoration.replace({}) },
      ],
    })
  }

  // Links: [text](url)
  for (const m of text.matchAll(/\[([^\]]+)\]\(([^)]+)\)/g)) {
    const s = lineFrom + m.index!
    const e = s + m[0].length
    const textStart = s + 1
    const textEnd = textStart + m[1]!.length
    spans.push({
      from: s, to: e,
      decos: [
        { from: s, to: s + 1, deco: Decoration.replace({}) },
        { from: textStart, to: textEnd, deco: Decoration.mark({ attributes: { style: 'text-decoration:underline;text-underline-offset:2px;text-decoration-color:var(--border);color:var(--accent)' } }) },
        { from: textEnd, to: e, deco: Decoration.replace({}) },
      ],
    })
  }

  // Inline math: $...$
  for (const m of text.matchAll(/(?<!\$)\$(?!\$)(.+?)(?<!\$)\$(?!\$)/g)) {
    const s = lineFrom + m.index!
    const e = s + m[0].length
    spans.push({
      from: s, to: e,
      decos: [
        { from: s, to: e, deco: Decoration.replace({ widget: new KaTeXInlineWidget(m[1]!) }) },
      ],
    })
  }

  return spans
}

/** Check if cursor position is inside an inline span (with 1-char padding for markers). */
function cursorInSpan(cursor: number, span: InlineSpan): boolean {
  return cursor >= span.from && cursor <= span.to
}

// ═══════════════════════════════════════════════════════════════
// ViewPlugin
// ═══════════════════════════════════════════════════════════════

export function livePreview() {
  return ViewPlugin.fromClass(
    class {
      decorations: RangeSet<Decoration>
      private _blocks: Block[] = []
      private _docId = -1
      private _inlineCache = new InlineDecoCache()

      constructor(view: EditorView) {
        this.decorations = this.build(view)
      }

      update(update: ViewUpdate) {
        if (update.docChanged) {
          this._inlineCache.invalidate()
        }
        if (update.docChanged || update.selectionSet || update.viewportChanged) {
          this.decorations = this.build(update.view)
        }
      }

      build(view: EditorView): RangeSet<Decoration> {
        const doc = view.state.doc
        const cursor = view.state.selection.main.head
        const cursorLine = doc.lineAt(cursor).number

        // Recompute blocks only when document changes
        const docId = doc.length * 1000003 + doc.lines
        if (this._docId !== docId) {
          this._blocks = computeBlocks(doc)
          this._docId = docId
        }

        const cursorBlock = findBlock(cursorLine, this._blocks)

        // Determine visible range
        const vr = view.visibleRanges
        let visStart = 1, visEnd = doc.lines
        if (vr.length > 0) {
          visStart = doc.lineAt(vr[0]!.from).number
          visEnd = doc.lineAt(vr[vr.length - 1]!.to).number
        }
        const pStart = Math.max(1, Math.min(visStart, cursorLine))
        const pEnd = Math.min(doc.lines, Math.max(visEnd, cursorLine))

        const pending: PendingDeco[] = []

        for (let ln = pStart; ln <= pEnd; ln++) {
          const line = doc.line(ln)
          const { from, text } = line
          const block = findBlock(ln, this._blocks)

          // If cursor is inside this block, show raw syntax for the entire block
          if (block && cursorBlock && block === cursorBlock) continue

          if (block) {
            switch (block.type) {
              case 'code': pending.push(...codeBlockDecos(text, from, block, ln)); break
              case 'math': pending.push(...mathBlockDecos(text, from, block, ln)); break
              case 'callout': pending.push(...calloutDecos(text, from, block, ln)); break
            }
            continue
          }

          // ── Line-level patterns: skip entire line if cursor is here ──
          if (ln !== cursorLine) {
            let matched = false
            const hd = headingDecos(text, from)
            if (hd.length > 0) { pending.push(...hd); matched = true }
            if (!matched) {
              const ld = listDecos(text, from)
              if (ld.length > 0) { pending.push(...ld); matched = true }
            }
            if (!matched) {
              const bd = blockquoteDecos(text, from)
              if (bd.length > 0) { pending.push(...bd); matched = true }
            }
            if (matched) continue
          }

          // ── Inline patterns: cursor-aware span filtering ──
          // On the cursor line, only render spans that DON'T contain the cursor.
          // On other lines, render all spans.
          const isCursorLine = ln === cursorLine

          // Use cache for inline span parsing (text-based, position-independent)
          let spans = this._inlineCache.get(text)
          if (!spans) {
            // Parse with offset 0, then adjust — cache is text-based
            const rawSpans = parseInlineSpans(text, 0)
            spans = rawSpans
            this._inlineCache.set(text, rawSpans)
          }

          // Rebase spans from cached (offset 0) to actual line position
          for (const span of spans) {
            if (isCursorLine && cursorInSpan(cursor, { from: span.from + from, to: span.to + from })) {
              // Cursor is inside this span — show raw syntax, skip decoration
              continue
            }
            // Add decorations with rebased positions
            for (const d of span.decos) {
              pending.push({
                from: d.from + from,
                to: d.to + from,
                deco: d.deco,
              })
            }
          }
        }

        // ── Sort by position, then write to builder ──
        pending.sort((a, b) => a.from - b.from || a.to - b.to)

        const builder = new RangeSetBuilder<Decoration>()
        for (const p of pending) {
          builder.add(p.from, p.to, p.deco)
        }
        return builder.finish()
      }
    },
    { decorations: (v) => v.decorations },
  )
}
