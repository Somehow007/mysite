/**
 * CodeMirror 6 Live Preview / WYSIWYG extension.
 *
 * Design principles:
 *   1. NEVER use Decoration.replace({ block: true }) — it causes crashes
 *   2. NEVER mix Decoration.line with Decoration.replace on the same line
 *      when the replace covers the entire line content
 *   3. Use Decoration.line for line-level styling (backgrounds, borders)
 *   4. Use Decoration.replace({}) (without block) for hiding syntax markers
 *   5. Use Decoration.replace({ widget }) (without block) for inline widgets
 *   6. Use Decoration.set(ranges, true) for auto-sorting
 *   7. Wrap build() in try-catch for graceful degradation
 *
 * Cursor behaviour:
 *   - Block-level (code/callout/math): cursor in block → entire block raw
 *   - Line-level (heading/list/bq): cursor on line → line raw
 *   - Inline (bold/italic/code/link/LaTeX): cursor in span → only that span raw
 */

import {
  ViewPlugin,
  ViewUpdate,
  Decoration,
  WidgetType,
  EditorView,
  type DecorationSet,
} from '@codemirror/view'
import { Text } from '@codemirror/state'
import katex from 'katex'

// ═══════════════════════════════════════════════════════════════
// Types
// ═══════════════════════════════════════════════════════════════

interface CodeBlock { type: 'code'; startLine: number; endLine: number; lang: string }
interface CalloutBlock { type: 'callout'; startLine: number; endLine: number; calloutType: string }
interface MathBlock { type: 'math'; startLine: number; endLine: number; content: string }
type Block = CodeBlock | CalloutBlock | MathBlock

interface PendingDeco { from: number; to: number; deco: Decoration }
interface InlineSpan { from: number; to: number; decos: PendingDeco[] }

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
// Widgets (all inline — NO block:true anywhere)
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
// Inline decoration cache
// ═══════════════════════════════════════════════════════════════

class InlineDecoCache {
  private cache = new Map<string, InlineSpan[]>()
  private static MAX_SIZE = 256
  get(text: string): InlineSpan[] | undefined { return this.cache.get(text) }
  set(text: string, spans: InlineSpan[]): void {
    if (this.cache.size >= InlineDecoCache.MAX_SIZE) { const k = this.cache.keys().next().value; if (k !== undefined) this.cache.delete(k) }
    this.cache.set(text, spans)
  }
  invalidate(): void { this.cache.clear() }
}

// ═══════════════════════════════════════════════════════════════
// Block precomputation
// ═══════════════════════════════════════════════════════════════

/** Exported for unit testing. */
export function computeBlocks(doc: Text): Block[] {
  const blocks: Block[] = []
  let inCode = false, codeStart = 0, codeLang = ''
  let inMath = false, mathStart = 0, mathLines: string[] = []
  let inCallout = false, calloutStart = 0, calloutType = ''

  for (let i = 1; i <= doc.lines; i++) {
    const text = doc.line(i).text
    const trimmed = text.trim()

    if (inCode) {
      if (/^\s*(```|~~~)/.test(text)) { blocks.push({ type: 'code', startLine: codeStart, endLine: i, lang: codeLang }); inCode = false }
      continue
    }
    if (inMath) {
      if (trimmed === '$$') { blocks.push({ type: 'math', startLine: mathStart, endLine: i, content: mathLines.join('\n') }); inMath = false; mathLines = [] }
      else mathLines.push(text)
      continue
    }
    if (inCallout) {
      if (text.startsWith('>')) {
        const nc = text.match(/^>\s*\[!(\w+)\]/)
        if (nc) { blocks.push({ type: 'callout', startLine: calloutStart, endLine: i - 1, calloutType }); calloutStart = i; calloutType = nc[1]!.toUpperCase() }
        continue
      }
      blocks.push({ type: 'callout', startLine: calloutStart, endLine: i - 1, calloutType }); inCallout = false
    }

    const fm = text.match(/^\s*(```|~~~)(\w*)\s*$/)
    if (fm) { codeStart = i; codeLang = fm[2] ?? ''; inCode = true; continue }
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
// Per-line decoration functions
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
  return [
    { from, to: from, deco: Decoration.line({ attributes: { style: 'margin-top:2em;margin-bottom:0.5em' } }) },
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

/**
 * Code block decorations.
 *
 * SAFETY (crash fix): NEVER combine Decoration.line with a Decoration.replace
 * that covers the ENTIRE line content — CM6 produces an empty line element that
 * conflicts with the line decoration and crashes on input. Fence lines (```js)
 * are entirely marker text, so replacing them wholly is the dangerous case.
 *
 * Strategy:
 *   - ALL lines (including fences) get Decoration.line for background/borders.
 *   - Opening fence: hide only the ```/~~~ backticks via PARTIAL replace (the
 *     remaining language token keeps the line non-empty → safe), and style the
 *     language as a label via Decoration.mark.
 *   - Closing fence / lang-less opening fence: dim the marker via Decoration.mark
 *     (no replace at all) so the line always retains content.
 *   - No Decoration.replace ever covers an entire fence line.
 */
/** Exported for unit testing. */
export function codeBlockDecos(text: string, from: number, block: CodeBlock, ln: number): PendingDeco[] {
  const isFirst = ln === block.startLine
  const isLast = ln === block.endLine
  const result: PendingDeco[] = []

  // Line decoration for ALL lines in the code block
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

  const fenceLabelStyle = 'font-size:0.7em;color:var(--text-muted);text-transform:uppercase;letter-spacing:0.05em;font-weight:500'
  const dimMarkerStyle = 'color:var(--text-muted);opacity:0.45'

  if (isFirst) {
    // Opening fence: ```lang  or  ~~~lang
    const fm = text.match(/^(\s*)(```+|~~~+)(\w*)/)
    if (fm) {
      const leadingLen = fm[1]!.length
      const fenceLen = fm[2]!.length
      const langLen = fm[3]!.length
      const fenceStart = from + leadingLen
      if (langLen > 0) {
        // Partial replace of backticks only — line still has the lang token, safe.
        result.push({ from: fenceStart, to: fenceStart + fenceLen, deco: Decoration.replace({}) })
        result.push({ from: fenceStart + fenceLen, to: fenceStart + fenceLen + langLen, deco: Decoration.mark({ attributes: { style: fenceLabelStyle } }) })
      } else {
        // No lang: dim the marker (mark, not replace) so the line keeps content.
        result.push({ from: fenceStart, to: fenceStart + fenceLen, deco: Decoration.mark({ attributes: { style: dimMarkerStyle } }) })
      }
    }
  } else if (isLast) {
    // Closing fence: ``` or ~~~
    const fm = text.match(/^(\s*)(```+|~~~+)/)
    if (fm) {
      const leadingLen = fm[1]!.length
      const fenceLen = fm[2]!.length
      const fenceStart = from + leadingLen
      const trailingLen = text.length - leadingLen - fenceLen
      if (trailingLen > 0) {
        // Trailing content exists → partial replace is safe.
        result.push({ from: fenceStart, to: fenceStart + fenceLen, deco: Decoration.replace({}) })
      } else {
        // Entire line is the closing fence → dim it (mark, not replace).
        result.push({ from: fenceStart, to: fenceStart + fenceLen, deco: Decoration.mark({ attributes: { style: dimMarkerStyle } }) })
      }
    }
  }

  return result
}

/**
 * Math block decorations.
 *
 * Single-line $$ E=mc^2 $$: replace entire line with KaTeX widget.
 * Multi-line: hide $$ fence lines, render content on first content line.
 * All uses inline replace (no block:true).
 */
function mathBlockDecos(text: string, from: number, block: MathBlock, ln: number): PendingDeco[] {
  if (block.startLine === block.endLine) {
    return [{ from, to: from + text.length, deco: Decoration.replace({ widget: new KaTeXDisplayWidget(block.content) }) }]
  }
  // 2-line math block: $$ \n $$ — no content, render empty KaTeX on startLine
  if (block.startLine + 1 === block.endLine) {
    if (ln === block.startLine) {
      return [{ from, to: from + text.length, deco: Decoration.replace({ widget: new KaTeXDisplayWidget(block.content) }) }]
    }
    return [{ from, to: from + text.length, deco: Decoration.replace({}) }]
  }
  // 3+ line math block
  if (ln === block.startLine || ln === block.endLine) {
    return [{ from, to: from + text.length, deco: Decoration.replace({}) }]
  }
  if (ln === block.startLine + 1) {
    return [{ from, to: from + text.length, deco: Decoration.replace({ widget: new KaTeXDisplayWidget(block.content) }) }]
  }
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
// Inline span parsing
// ═══════════════════════════════════════════════════════════════

function parseInlineSpans(text: string, lineFrom: number): InlineSpan[] {
  const spans: InlineSpan[] = []

  for (const m of text.matchAll(/\*\*(.+?)\*\*/g)) {
    const s = lineFrom + m.index!, e = s + m[0].length
    spans.push({ from: s, to: e, decos: [
      { from: s, to: s + 2, deco: Decoration.replace({}) },
      { from: s + 2, to: e - 2, deco: Decoration.mark({ attributes: { style: 'font-weight:600;color:var(--text-primary)' } }) },
      { from: e - 2, to: e, deco: Decoration.replace({}) },
    ] })
  }

  for (const m of text.matchAll(/(?<!\*)\*(?!\*)(.+?)(?<!\*)\*(?!\*)/g)) {
    const s = lineFrom + m.index!, e = s + m[0].length
    spans.push({ from: s, to: e, decos: [
      { from: s, to: s + 1, deco: Decoration.replace({}) },
      { from: s + 1, to: e - 1, deco: Decoration.mark({ attributes: { style: 'font-style:italic' } }) },
      { from: e - 1, to: e, deco: Decoration.replace({}) },
    ] })
  }

  for (const m of text.matchAll(/`([^`]+)`/g)) {
    const s = lineFrom + m.index!, e = s + m[0].length
    spans.push({ from: s, to: e, decos: [
      { from: s, to: s + 1, deco: Decoration.replace({}) },
      { from: s + 1, to: e - 1, deco: Decoration.mark({ attributes: { style: 'font-family:var(--font-mono,ui-monospace,monospace);font-size:0.875em;padding:0.2em 0.4em;border-radius:4px;background-color:var(--bg-code)' } }) },
      { from: e - 1, to: e, deco: Decoration.replace({}) },
    ] })
  }

  for (const m of text.matchAll(/\[([^\]]+)\]\(([^)]+)\)/g)) {
    const s = lineFrom + m.index!, e = s + m[0].length
    const ts = s + 1, te = ts + m[1]!.length
    spans.push({ from: s, to: e, decos: [
      { from: s, to: s + 1, deco: Decoration.replace({}) },
      { from: ts, to: te, deco: Decoration.mark({ attributes: { style: 'text-decoration:underline;text-underline-offset:2px;text-decoration-color:var(--border);color:var(--accent)' } }) },
      { from: te, to: e, deco: Decoration.replace({}) },
    ] })
  }

  for (const m of text.matchAll(/(?<!\$)\$(?!\$)(.+?)(?<!\$)\$(?!\$)/g)) {
    const s = lineFrom + m.index!, e = s + m[0].length
    spans.push({ from: s, to: e, decos: [
      { from: s, to: e, deco: Decoration.replace({ widget: new KaTeXInlineWidget(m[1]!) }) },
    ] })
  }

  return spans
}

function cursorInSpan(cursor: number, span: { from: number; to: number }): boolean {
  return cursor >= span.from && cursor < span.to
}

// ═══════════════════════════════════════════════════════════════
// ViewPlugin
// ═══════════════════════════════════════════════════════════════

export function livePreview() {
  return ViewPlugin.fromClass(
    class {
      decorations: DecorationSet = Decoration.none
      private _blocks: Block[] = []
      private _lastDoc: Text | null = null
      private _inlineCache = new InlineDecoCache()
      private _errorCount = 0
      // Cursor-aware rebuild skip: avoid full rebuild when the cursor moves
      // within the same line without crossing an inline span boundary.
      private _lastCursorLine = -1
      private _lastCursorInSpan = false

      constructor(view: EditorView) {
        this.decorations = this.build(view)
      }

      update(update: ViewUpdate) {
        if (update.docChanged) {
          this._inlineCache.invalidate()
          this._lastDoc = null // force recompute blocks
          this._lastCursorLine = -1
          this.decorations = this.build(update.view)
          return
        }
        if (update.viewportChanged) {
          this.decorations = this.build(update.view)
          return
        }
        if (update.selectionSet) {
          const cursor = update.state.selection.main.head
          const doc = update.state.doc
          const cursorLine = doc.lineAt(cursor).number
          if (cursorLine !== this._lastCursorLine) {
            // Cursor moved to a different line — block/line context may differ.
            this.decorations = this.build(update.view)
            return
          }
          // Same line: only rebuild if cursor crossed into/out of an inline span.
          const line = doc.line(cursorLine)
          const spans = this._inlineCache.get(line.text)
          const inSpan = spans
            ? spans.some(s => cursor >= line.from + s.from && cursor < line.from + s.to)
            : false
          if (inSpan !== this._lastCursorInSpan) {
            this.decorations = this.build(update.view)
          }
        }
      }

      build(view: EditorView): DecorationSet {
        try {
          const result = this._build(view)
          this._errorCount = 0
          return result
        } catch (e) {
          this._errorCount++
          if (this._errorCount <= 3) console.warn('[livePreview] build error:', e)
          if (this._errorCount >= 5) return Decoration.none // too many errors, disable
          return this.decorations
        }
      }

      _build(view: EditorView): DecorationSet {
        const doc = view.state.doc
        const cursor = view.state.selection.main.head
        const cursorLine = doc.lineAt(cursor).number

        // Recompute blocks whenever doc reference changes (covers all content changes)
        if (this._lastDoc !== doc) {
          this._blocks = computeBlocks(doc)
          this._lastDoc = doc
        }

        const cursorBlock = findBlock(cursorLine, this._blocks)

        // Record cursor-line span membership for the rebuild-skip optimisation.
        const cursorLineObj = doc.line(cursorLine)
        const cursorSpans = this._inlineCache.get(cursorLineObj.text)
        this._lastCursorLine = cursorLine
        this._lastCursorInSpan = cursorSpans
          ? cursorSpans.some(s => cursor >= cursorLineObj.from + s.from && cursor < cursorLineObj.from + s.to)
          : false

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

          if (block && cursorBlock && block === cursorBlock) continue

          if (block) {
            switch (block.type) {
              case 'code': pending.push(...codeBlockDecos(text, from, block, ln)); break
              case 'math': pending.push(...mathBlockDecos(text, from, block, ln)); break
              case 'callout': pending.push(...calloutDecos(text, from, block, ln)); break
            }
            continue
          }

          if (ln !== cursorLine) {
            let matched = false
            const hd = headingDecos(text, from)
            if (hd.length > 0) { pending.push(...hd); matched = true }
            if (!matched) { const ld = listDecos(text, from); if (ld.length > 0) { pending.push(...ld); matched = true } }
            if (!matched) { const bd = blockquoteDecos(text, from); if (bd.length > 0) { pending.push(...bd); matched = true } }
            if (matched) continue
          }

          const isCursorLine = ln === cursorLine
          let spans = this._inlineCache.get(text)
          if (!spans) { spans = parseInlineSpans(text, 0); this._inlineCache.set(text, spans) }

          for (const span of spans) {
            if (isCursorLine && cursorInSpan(cursor, { from: span.from + from, to: span.to + from })) continue
            for (const d of span.decos) {
              pending.push({ from: d.from + from, to: d.to + from, deco: d.deco })
            }
          }
        }

        return Decoration.set(
          pending.map(p => p.deco.range(p.from, p.to)),
          true,
        )
      }
    },
    { decorations: (v) => v.decorations },
  )
}
