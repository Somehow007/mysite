/**
 * Markdown auto-conversion on Space/Tab.
 *
 * When the user types a shorthand pattern at the start of a line and presses
 * Space or Tab, the pattern is replaced with standard Markdown syntax.
 *
 * Conversions:
 *   "+<Space>"        → "- "   (plus → unordered list with dash)
 *   "*<Space>"        → "* "   (normalize spacing)
 *   "-<Space>"        → "- "   (normalize spacing)
 *   "N.<Space>"       → "N. "  (normalize ordered list)
 *   "- []<Space>"     → "- [ ] " (task list)
 *   "- [x]<Space>"    → "- [x] " (task list checked)
 *   "><Space>"        → "> "   (normalize blockquote)
 *   "#<Space>"–"######<Space>" → heading (normalize)
 */

import { keymap } from '@codemirror/view'
import { EditorView } from '@codemirror/view'
import type { EditorState, Transaction } from '@codemirror/state'

// ── Conversion rules ──
// Each rule: [pattern, replacement]
// Pattern is matched against the text from line-start to cursor.
// Replacement replaces the pattern text (plus the space/tab being typed).

interface Rule {
  /** Regex to match the text before cursor (from line start to cursor) */
  match: RegExp
  /** Replacement string. Can be a function receiving the matched string. */
  replace: string | ((match: string) => string)
}

const rules: Rule[] = [
  // + → - (plus sign is not standard markdown)
  { match: /^\+$/, replace: '- ' },
  // Normalize unordered list markers
  { match: /^[-*]$/, replace: (m) => m + ' ' },
  // Normalize ordered list: "1." → "1. "
  { match: /^\d+\.$/, replace: (m) => m + ' ' },
  // Task list: "- []" → "- [ ] "
  { match: /^- ?\[\s*\]$/, replace: '- [ ] ' },
  // Task list checked: "- [x]" → "- [x] "
  { match: /^- ?\[[xX]\]$/, replace: (m) => {
    const checked = m.includes('x') ? 'x' : 'X'
    return `- [${checked}] `
  }},
  // Blockquote: ">" → "> "
  { match: /^>$/, replace: '> ' },
  // Headings: "#" → "######" → normalize
  { match: /^#{1,6}$/, replace: (m) => m + ' ' },
]

function tryAutoConvert(view: EditorView): boolean {
  const { state } = view
  const { selection } = state
  const cursor = selection.main.head

  // Get text from line start to cursor
  const line = state.doc.lineAt(cursor)
  const textBeforeCursor = state.doc.sliceString(line.from, cursor)

  for (const rule of rules) {
    if (rule.match.test(textBeforeCursor)) {
      const replacement = typeof rule.replace === 'function'
        ? rule.replace(textBeforeCursor)
        : rule.replace

      view.dispatch({
        changes: { from: line.from, to: cursor, insert: replacement },
        selection: { anchor: line.from + replacement.length },
      })
      return true
    }
  }

  return false
}

/** Keymap that intercepts Space and Tab for auto-conversion. */
export const autoConvertKeymap = keymap.of([
  {
    key: 'Space',
    run: (view) => tryAutoConvert(view),
  },
  {
    key: 'Tab',
    run: (view) => tryAutoConvert(view),
  },
])
