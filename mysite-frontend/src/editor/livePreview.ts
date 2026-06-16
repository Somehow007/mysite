/**
 * CodeMirror 6 Live Preview / WYSIWYG extension.
 *
 * Hides Markdown syntax markers on lines that don't have the cursor,
 * replacing them with styled visual representations.
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
  EditorView,
} from '@codemirror/view'
import { RangeSet, RangeSetBuilder } from '@codemirror/state'

// ── Widgets ──

/** Renders a bullet point (•) for unordered lists. */
class BulletWidget extends WidgetType {
  toDOM(): HTMLElement {
    const span = document.createElement('span')
    span.className = 'cm-live-bullet'
    span.textContent = '•'
    span.style.cssText = 'margin-right:0.5em;color:var(--text-muted)'
    return span
  }
}

/** Renders an ordered-list number. */
class OrderedNumberWidget extends WidgetType {
  constructor(private readonly num: number) { super() }
  toDOM(): HTMLElement {
    const span = document.createElement('span')
    span.className = 'cm-live-ol-num'
    span.textContent = `${this.num}.`
    span.style.cssText = 'margin-right:0.5em;color:var(--text-muted);font-variant-numeric:tabular-nums'
    return span
  }
}

/** Renders an unchecked task checkbox. */
class TaskUncheckedWidget extends WidgetType {
  toDOM(): HTMLElement {
    const span = document.createElement('span')
    span.className = 'cm-live-task'
    span.innerHTML = '<input type="checkbox" disabled tabindex="-1">'
    span.style.cssText = 'margin-right:0.5em'
    return span
  }
}

/** Renders a checked task checkbox. */
class TaskCheckedWidget extends WidgetType {
  toDOM(): HTMLElement {
    const span = document.createElement('span')
    span.className = 'cm-live-task checked'
    span.innerHTML = '<input type="checkbox" checked disabled tabindex="-1">'
    span.style.cssText = 'margin-right:0.5em'
    return span
  }
}

/** Renders a blockquote left-border gutter. */
class BlockquoteGutterWidget extends WidgetType {
  toDOM(): HTMLElement {
    const div = document.createElement('div')
    div.className = 'cm-live-bq-gutter'
    div.style.cssText = 'display:inline-block;width:3px;background:var(--accent, #448aff);border-radius:2px;margin-right:0.75em;vertical-align:top;min-height:1.2em'
    return div
  }
}

// ── Helper: build heading decorations ──

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

  if (currentLine === cursorLine) return true // show raw source

  // Replace "#" markers with nothing (hide them)
  builder.add(from, from + markerLen, Decoration.replace({}))
  // Style the heading text
  const fontSize = 2.2 - level * 0.15 // h1 ~2em, h6 ~1.3em
  builder.add(
    from + markerLen,
    from + lineText.length,
    Decoration.mark({
      attributes: {
        style: `font-size:${fontSize}em;font-weight:700;color:var(--text-primary);line-height:1.3`,
      },
    }),
  )
  return true
}

// ── Helper: build list decorations ──

function decorateList(
  lineText: string,
  from: number,
  cursorLine: number,
  currentLine: number,
  builder: RangeSetBuilder<Decoration>,
  view: EditorView,
): boolean {
  const leadingWs = lineText.match(/^(\s*)/)?.[1] ?? ''

  // Task list: "- [ ]" or "- [x]" etc.
  const taskMatch = lineText.match(/^(\s*)([-*+])\s\[([ xX])\]\s/)
  if (taskMatch) {
    const markerLen = taskMatch[0].length  // e.g. "- [ ] " = 6
    if (currentLine === cursorLine) return true
    // Hide the marker
    builder.add(from, from + markerLen, Decoration.replace({}))
    // Show checkbox widget at the start
    const checked = taskMatch[3]!.toLowerCase() === 'x'
    const widget = checked ? new TaskCheckedWidget() : new TaskUncheckedWidget()
    builder.add(from, from, Decoration.widget({ widget, side: 1 }))
    return true
  }

  // Ordered list: "1. " etc.
  const olMatch = lineText.match(/^(\s*)(\d+)\.\s/)
  if (olMatch) {
    const markerLen = olMatch[0].length
    if (currentLine === cursorLine) return true
    const num = parseInt(olMatch[2]!, 10)
    builder.add(from, from + markerLen, Decoration.replace({}))
    builder.add(from, from, Decoration.widget({ widget: new OrderedNumberWidget(num), side: 1 }))
    return true
  }

  // Unordered list: "- ", "* ", "+ "
  const ulMatch = lineText.match(/^(\s*)([-*+])\s/)
  if (ulMatch) {
    const markerLen = ulMatch[0].length
    if (currentLine === cursorLine) return true
    builder.add(from, from + markerLen, Decoration.replace({}))
    builder.add(from, from, Decoration.widget({ widget: new BulletWidget(), side: 1 }))
    return true
  }

  return false
}

// ── Helper: build blockquote decoration ──

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

  // Hide "> " prefix
  builder.add(from, from + markerLen, Decoration.replace({}))
  // Add blockquote left-border widget
  builder.add(from, from, Decoration.widget({ widget: new BlockquoteGutterWidget(), side: 1 }))
  // Style the content with muted color
  builder.add(
    from + markerLen,
    from + lineText.length,
    Decoration.mark({
      attributes: { style: 'color:var(--text-secondary);font-style:italic' },
    }),
  )
  return true
}

// ── Helper: build inline decorations (bold, italic, code) ──

function decorateInline(
  lineText: string,
  lineFrom: number,
  builder: RangeSetBuilder<Decoration>,
): void {
  // Bold: **text** or __text__
  for (const m of lineText.matchAll(/\*\*(.+?)\*\*/g)) {
    const start = lineFrom + m.index!
    const end = start + m[0].length
    // Hide opening ** (2 chars)
    builder.add(start, start + 2, Decoration.replace({}))
    // Hide closing ** (2 chars)
    builder.add(end - 2, end, Decoration.replace({}))
    // Bold the inner text
    builder.add(
      start + 2,
      end - 2,
      Decoration.mark({ attributes: { style: 'font-weight:700' } }),
    )
  }

  // Italic: *text* (but not **)
  for (const m of lineText.matchAll(/(?<!\*)\*(?!\*)(.+?)(?<!\*)\*(?!\*)/g)) {
    const start = lineFrom + m.index!
    const end = start + m[0].length
    builder.add(start, start + 1, Decoration.replace({}))
    builder.add(end - 1, end, Decoration.replace({}))
    builder.add(
      start + 1,
      end - 1,
      Decoration.mark({ attributes: { style: 'font-style:italic' } }),
    )
  }

  // Inline code: `code`
  for (const m of lineText.matchAll(/`(.+?)`/g)) {
    const start = lineFrom + m.index!
    const end = start + m[0].length
    builder.add(start, start + 1, Decoration.replace({}))
    builder.add(end - 1, end, Decoration.replace({}))
    builder.add(
      start + 1,
      end - 1,
      Decoration.mark({
        attributes: {
          style: 'background:var(--bg-code);font-family:ui-monospace,monospace;font-size:0.875em;padding:0.1em 0.3em;border-radius:3px',
        },
      }),
    )
  }
}

// ── The ViewPlugin ──

export function livePreview() {
  return ViewPlugin.fromClass(
    class {
      decorations: RangeSet<Decoration>

      constructor(view: EditorView) {
        this.decorations = this.build(view)
      }

      update(update: ViewUpdate) {
        if (update.docChanged || update.selectionSet || update.viewportChanged) {
          this.decorations = this.build(update.view)
        }
      }

      build(view: EditorView): RangeSet<Decoration> {
        const builder = new RangeSetBuilder<Decoration>()
        const doc = view.state.doc
        const cursor = view.state.selection.main.head
        const cursorLine = doc.lineAt(cursor).number

        for (let i = 1; i <= doc.lines; i++) {
          const line = doc.line(i)
          const lineText = line.text
          const from = line.from

          // Check code blocks — always show raw source inside fences
          // We do a quick check: if the line is inside a code block, skip decoration
          // Detecting code blocks: scan backwards for unmatched ```
          if (isInsideCodeBlock(doc.toString(), from)) {
            continue // don't decorate code block content
          }

          // Apply decorations in order (first match wins for block-level)
          if (decorateHeading(lineText, from, cursorLine, i, builder)) continue
          if (decorateList(lineText, from, cursorLine, i, builder, view)) continue
          if (decorateBlockquote(lineText, from, cursorLine, i, builder)) continue

          // Inline decorations on non-cursor lines
          if (i !== cursorLine) {
            decorateInline(lineText, from, builder)
          }
        }

        return builder.finish()
      }
    },
    { decorations: (v) => v.decorations },
  )
}

// ── Code block detection ──

function isInsideCodeBlock(docText: string, pos: number): boolean {
  const textBefore = docText.substring(0, pos)
  const lines = textBefore.split('\n')
  let inFence = false
  for (const line of lines) {
    if (/^\s*```/.test(line)) {
      inFence = !inFence
    }
  }
  return inFence
}
