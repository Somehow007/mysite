/**
 * Obsidian-style Enter key continuation for CodeMirror 6.
 *
 * When the user presses Enter, determines whether the next line should
 * auto-continue the current line's syntax structure (lists, blockquotes,
 * callouts) and dispatches the minimal change directly via CM6 Transaction.
 *
 * Uses CM6's line-based API to avoid O(n) doc.toString() calls.
 */

import { keymap, EditorView } from '@codemirror/view'
import { Text } from '@codemirror/state'
import { parseLinePrefix, buildContinuation, type ParsedLine } from '@/utils/markdownContinuation'

/** Check whether the cursor is inside a fenced code block or display math block. */
function isInsideBlock(doc: Text, currentLineNum: number): boolean {
  let inCode = false
  let inMath = false

  for (let i = 1; i < currentLineNum; i++) {
    const trimmed = doc.line(i).text.trim()
    if (/^(```|~~~)/.test(trimmed)) {
      inCode = !inCode
      continue
    }
    if (!inCode && trimmed === '$$') {
      inMath = !inMath
    }
  }

  return inCode || inMath
}

function handleEnter(view: EditorView): boolean {
  const { state } = view
  const doc = state.doc
  const cursor = state.selection.main.head
  const line = doc.lineAt(cursor)
  const lineText = line.text
  const lineStart = line.from
  const lineNum = line.number

  // ── Block context check ──
  if (isInsideBlock(doc, lineNum)) return false

  // ── Parse prefix markers ──
  const parsed = parseLinePrefix(lineText)
  if (!parsed) return false

  // ── Exit condition: two consecutive empty items ──
  if (parsed.content.trim() === '' && lineNum > 1) {
    const prevLine = doc.line(lineNum - 1)
    const prevParsed = parseLinePrefix(prevLine.text)
    if (prevParsed && prevParsed.rawPrefix === parsed.rawPrefix && prevParsed.content.trim() === '') {
      const prefixEnd = lineStart + parsed.rawPrefix.length
      view.dispatch({
        changes: { from: lineStart, to: prefixEnd, insert: '' },
        selection: { anchor: lineStart },
      })
      return true
    }
  }

  // ── Build continuation ──
  const continuation = buildContinuation(parsed)

  // Avoid double-space
  const textAfterCursor = doc.sliceString(cursor, line.to)
  let effective = continuation
  if (continuation.endsWith(' ') && (textAfterCursor.startsWith(' ') || textAfterCursor.startsWith('\t'))) {
    effective = continuation.slice(0, -1)
  }

  const insertText = '\n' + effective
  view.dispatch({
    changes: { from: cursor, to: cursor, insert: insertText },
    selection: { anchor: cursor + insertText.length },
  })
  return true
}

/** CM6 keymap that intercepts unmodified Enter for Obsidian-style continuation. */
export const enterContinuationKeymap = keymap.of([
  {
    key: 'Enter',
    run: (view) => handleEnter(view),
  },
])
