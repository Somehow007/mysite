/**
 * Obsidian-style Enter key continuation for CodeMirror 6.
 *
 * Reuses the pure function `processEnterKey` from `@/utils/markdownContinuation`
 * and applies the result via a CM6 Transaction.
 *
 * Computes the minimal change delta between old and new text to avoid
 * wiping CM6's history state (which a full-doc replace would do).
 */

import { keymap, EditorView } from '@codemirror/view'
import { processEnterKey } from '@/utils/markdownContinuation'

function handleEnter(view: EditorView): boolean {
  const { state } = view
  const cursor = state.selection.main.head
  const fullText = state.doc.toString()

  const result = processEnterKey(fullText, cursor)
  if (!result) return false // No continuation — let CM6 handle Enter natively

  // Compute minimal change: find where old and new text diverge
  const oldLen = fullText.length
  const newLen = result.text.length
  const minLen = Math.min(oldLen, newLen)
  let commonPrefixLen = 0
  while (commonPrefixLen < minLen && fullText[commonPrefixLen] === result.text[commonPrefixLen]) {
    commonPrefixLen++
  }

  view.dispatch({
    changes: { from: commonPrefixLen, to: oldLen, insert: result.text.substring(commonPrefixLen) },
    selection: { anchor: result.cursorPos },
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
