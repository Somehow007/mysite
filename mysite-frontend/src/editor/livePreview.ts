/**
 * CodeMirror 6 Live Preview / WYSIWYG extension.
 *
 * Hides Markdown syntax markers on lines that don't have the cursor,
 * replacing them with styled visual representations via Decoration.replace({widget}).
 *
 * Supported decorations:
 *   - Headings: hide hash markers, scale text
 *   - Unordered lists: hide bullet markers, show bullet widget
 *   - Ordered lists: hide number markers, show styled number
 *   - Task lists: hide task markers, show checkbox widget
 *   - Blockquotes: hide > markers, apply left-border style
 *   - Bold/italic: hide marker symbols, apply font-weight/style
 *   - Inline code: hide backtick markers, apply background
 */

import {
  ViewPlugin,
  ViewUpdate,
  Decoration,
  WidgetType,
} from '@codemirror/view'
import { RangeSet, RangeSetBuilder, Text } from '@codemirror/state'

// ── Widgets ──

class BulletWidget extends WidgetType {
  get estimatedHeight(): number { return 22 }
  toDOM(): HTMLElement {
    const span = document.createElement('span')
    span.className = 'cm-live-bullet'
    span.textContent = '•'
    span.style.cssText = 'margin-right:0.5em;color:var(--text-muted)'
    return span
  }
}

class OrderedNumberWidget extends WidgetType {
  constructor(private readonly num: number) { super() }
  get estimatedHeight(): number { return 22 }
  toDOM(): HTMLElement {
    const span = document.createElement('span')
    span.className = 'cm-live-ol-num'
    span.textContent = `${this.num}.`
    span.style.cssText = 'margin-right:0.5em;color:var(--text-muted);font-variant-numeric:tabular-nums'
    return span
  }
}

class TaskUncheckedWidget extends WidgetType {
  get estimatedHeight(): number { return 22 }
  toDOM(): HTMLElement {
    const span = document.createElement('span')
    span.className = 'cm-live-task'
    span.innerHTML = '<input type="checkbox" disabled tabindex="-1">'
    span.style.cssText = 'margin-right:0.5em'
    return span
  }
}

class TaskCheckedWidget extends WidgetType {
  get estimatedHeight(): number { return 22 }
  toDOM(): HTMLElement {
    const span = document.createElement('span')
    span.className = 'cm-live-task checked'
    span.innerHTML = '<input type="checkbox" checked disabled tabindex="-1">'
    span.style.cssText = 'margin-right:0.5em'
    return span
  }
}

class BlockquoteGutterWidget extends WidgetType {
  get estimatedHeight(): number { return 22 }
  toDOM(): HTMLElement {
    const div = document.createElement('div')
    div.className = 'cm-live-bq-gutter'
    div.style.cssText = 'display:inline-block;width:3px;background:var(--accent, #448aff);border-radius:2px;margin-right:0.75em;vertical-align:top;min-height:1.2em'
    return div
  }
}

// ── Block-level decorations ──
// Use Decoration.replace({widget}) to hide the syntax marker range and
// show a visual widget in its place. This avoids the layout issues of
// separate replace + widget decorations at overlapping positions.

function decorateHeading(
  lineText: string,
  from: number,
  cursorLine: number,
  currentLine: number,
  builder: RangeSetBuilder<Decoration>,
): boolean {
  const m = lineText.match(/^(#{1,6})\s/)
  if (!m) return false
  const level = m[1]!.length
  const markerLen = m[0].length // e.g. "### " = 4

  if (currentLine === cursorLine) return true

  // Hide the "#" marker range
  builder.add(from, from + markerLen, Decoration.replace({}))
  // Style the heading text
  const fontSizes = [2.05, 1.75, 1.5, 1.3, 1.15, 1.05] // h1..h6
  builder.add(
    from + markerLen,
    from + lineText.length,
    Decoration.mark({
      attributes: {
        style: `font-size:${fontSizes[level - 1]!}em;font-weight:700;color:var(--text-primary);line-height:1.3`,
      },
    }),
  )
  return true
}

function decorateList(
  lineText: string,
  from: number,
  cursorLine: number,
  currentLine: number,
  builder: RangeSetBuilder<Decoration>,
): boolean {
  // Task list: "- [ ] " or "- [x] "
  const taskMatch = lineText.match(/^(\s*)([-*+])\s\[([ xX])\]\s/)
  if (taskMatch) {
    const markerLen = taskMatch[0].length
    if (currentLine === cursorLine) return true
    const checked = taskMatch[3]!.toLowerCase() === 'x'
    const widget = checked ? new TaskCheckedWidget() : new TaskUncheckedWidget()
    builder.add(from, from + markerLen, Decoration.replace({ widget }))
    return true
  }

  // Ordered list: "1. "
  const olMatch = lineText.match(/^(\s*)(\d+)\.\s/)
  if (olMatch) {
    const markerLen = olMatch[0].length
    if (currentLine === cursorLine) return true
    const num = parseInt(olMatch[2]!, 10)
    builder.add(from, from + markerLen, Decoration.replace({ widget: new OrderedNumberWidget(num) }))
    return true
  }

  // Unordered list: "- ", "* ", "+ "
  const ulMatch = lineText.match(/^(\s*)([-*+])\s/)
  if (ulMatch) {
    const markerLen = ulMatch[0].length
    if (currentLine === cursorLine) return true
    builder.add(from, from + markerLen, Decoration.replace({ widget: new BulletWidget() }))
    return true
  }

  return false
}

function decorateBlockquote(
  lineText: string,
  from: number,
  cursorLine: number,
  currentLine: number,
  builder: RangeSetBuilder<Decoration>,
): boolean {
  const m = lineText.match(/^(\s*)>\s?/)
  if (!m) return false
  const markerLen = m[0].length

  if (currentLine === cursorLine) return true

  // Hide "> " prefix, show left-border gutter widget
  builder.add(from, from + markerLen, Decoration.replace({ widget: new BlockquoteGutterWidget() }))
  // Style the content
  builder.add(
    from + markerLen,
    from + lineText.length,
    Decoration.mark({
      attributes: { style: 'color:var(--text-secondary);font-style:italic' },
    }),
  )
  return true
}

// ── Inline decorations (bold, italic, code) ──

function decorateInline(
  lineText: string,
  lineFrom: number,
  builder: RangeSetBuilder<Decoration>,
): void {
  // Bold: **text** — hide opening/closing **, bold the content
  for (const m of lineText.matchAll(/\*\*(.+?)\*\*/g)) {
    const start = lineFrom + m.index!
    const end = start + m[0].length
    builder.add(start, start + 2, Decoration.replace({}))
    builder.add(end - 2, end, Decoration.replace({}))
    builder.add(start + 2, end - 2, Decoration.mark({ attributes: { style: 'font-weight:700' } }))
  }

  // Italic: *text* (not ** — lookbehind ensures single *)
  for (const m of lineText.matchAll(/(?<!\*)\*(?!\*)(.+?)(?<!\*)\*(?!\*)/g)) {
    const start = lineFrom + m.index!
    const end = start + m[0].length
    builder.add(start, start + 1, Decoration.replace({}))
    builder.add(end - 1, end, Decoration.replace({}))
    builder.add(start + 1, end - 1, Decoration.mark({ attributes: { style: 'font-style:italic' } }))
  }

  // Inline code: `code`
  for (const m of lineText.matchAll(/`([^`]+)`/g)) {
    const start = lineFrom + m.index!
    const end = start + m[0].length
    builder.add(start, start + 1, Decoration.replace({}))
    builder.add(end - 1, end, Decoration.replace({}))
    builder.add(start + 1, end - 1, Decoration.mark({
      attributes: {
        style: 'background:var(--bg-code);font-family:ui-monospace,monospace;font-size:0.875em;padding:0.1em 0.3em;border-radius:3px',
      },
    }))
  }
}

// ── ViewPlugin ──

export function livePreview() {
  return ViewPlugin.fromClass(
    class {
      decorations: RangeSet<Decoration>

      constructor(view: any) {
        this.decorations = this.build(view)
      }

      update(update: ViewUpdate) {
        // Only rebuild on doc changes or selection changes
        if (update.docChanged || update.selectionSet || update.viewportChanged) {
          this.decorations = this.build(update.view)
        }
      }

      build(view: any): RangeSet<Decoration> {
        const builder = new RangeSetBuilder<Decoration>()
        const doc = view.state.doc
        const cursor = view.state.selection.main.head
        const cursorLine = doc.lineAt(cursor).number

        // Precompute code-block ranges in a single O(n) pass
        const codeBlockRanges = computeCodeBlockRanges(doc)

        let lineNum = 1
        for (const line of iterLines(doc)) {
          const from = line.from
          const lineText = line.text

          if (isLineInCodeBlock(lineNum, codeBlockRanges)) {
            lineNum++
            continue
          }

          if (decorateHeading(lineText, from, cursorLine, lineNum, builder)) { lineNum++; continue }
          if (decorateList(lineText, from, cursorLine, lineNum, builder)) { lineNum++; continue }
          if (decorateBlockquote(lineText, from, cursorLine, lineNum, builder)) { lineNum++; continue }

          if (lineNum !== cursorLine) {
            decorateInline(lineText, from, builder)
          }
          lineNum++
        }

        return builder.finish()
      }
    },
    { decorations: (v: any) => v.decorations },
  )
}

// ── Code block helpers (O(n) precompute) ──

type CodeBlockRange = [startLine: number, endLine: number]

function computeCodeBlockRanges(doc: Text): CodeBlockRange[] {
  const ranges: CodeBlockRange[] = []
  let inFence = false
  let fenceStart = 0
  let lineNum = 1

  for (const line of iterLines(doc)) {
    if (/^\s*```/.test(line.text)) {
      if (inFence) {
        ranges.push([fenceStart, lineNum])
        inFence = false
      } else {
        fenceStart = lineNum
        inFence = true
      }
    }
    lineNum++
  }

  if (inFence) {
    ranges.push([fenceStart, lineNum - 1])
  }

  return ranges
}

function isLineInCodeBlock(lineNum: number, ranges: CodeBlockRange[]): boolean {
  // Binary search: find the last range whose start <= lineNum
  let lo = 0
  let hi = ranges.length - 1
  let idx = -1
  while (lo <= hi) {
    const mid = (lo + hi) >>> 1
    if (ranges[mid]![0] <= lineNum) {
      idx = mid
      lo = mid + 1
    } else {
      hi = mid - 1
    }
  }
  if (idx === -1) return false
  return lineNum <= ranges[idx]![1]
}

function* iterLines(doc: Text): Generator<{ from: number; text: string }> {
  const len = doc.length
  let pos = 0
  while (pos < len) {
    const line = doc.lineAt(pos)
    yield { from: line.from, text: line.text }
    // Advance past line.to (exclusive end of content).
    // If a newline follows, skip it too.
    pos = line.to + 1
    if (pos < len && doc.sliceString(pos, pos + 1) === '\n') {
      pos++
    }
  }
}
