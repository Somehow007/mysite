/**
 * Obsidian-style Enter key continuation for CodeMirror 6.
 *
 * Reuses the pure function `processEnterKey` from `@/utils/markdownContinuation`
 * and applies the result via a CM6 Transaction.
 */

import { keymap, EditorView } from '@codemirror/view'
import { processEnterKey } from '@/utils/markdownContinuation'

function handleEnter(view: EditorView): boolean {
  const { state } = view
  const cursor = state.selection.main.head
  const fullText = state.doc.toString()

  const result = processEnterKey(fullText, cursor)
  if (!result) return false // No continuation — let CM6 default Enter handle it

  view.dispatch({
    changes: { from: 0, to: fullText.length, insert: result.text },
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
