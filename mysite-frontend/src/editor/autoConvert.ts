/**
 * Markdown auto-conversion on Space.
 *
 * When the user types a shorthand pattern at the start of a line and presses
 * Space, the pattern is replaced with standard Markdown syntax.
 *
 * Conversions:
 *   "+<Space>"  → "- "   (plus → unordered list with dash)
 *   "*<Space>"  → "* "   (normalize spacing)
 *   "-<Space>"  → "- "   (normalize spacing)
 *   "N.<Space>" → "N. "  (normalize ordered list)
 *   "- []<Space>"  → "- [ ] " (task list)
 *   "- [x]<Space>" → "- [x] " (task list checked)
 *   "><Space>"  → "> "   (normalize blockquote)
 *   "#<Space>"–"######<Space>" → heading (normalize)
 *
 * NOTE: Only Space triggers auto-conversion. Tab is left for indentWithTab.
 */

import { keymap, EditorView } from '@codemirror/view'

interface Rule {
  match: RegExp
  replace: string | ((match: string) => string)
}

const rules: Rule[] = [
  { match: /^\+$/, replace: '- ' },
  { match: /^[-*]$/, replace: (m) => m + ' ' },
  { match: /^\d+\.$/, replace: (m) => m + ' ' },
  { match: /^- ?\[\s*\]$/, replace: '- [ ] ' },
  { match: /^- ?\[[xX]\]$/, replace: (m) => {
    const checked = m.includes('x') ? 'x' : 'X'
    return `- [${checked}] `
  }},
  { match: /^>$/, replace: '> ' },
  { match: /^#{1,6}$/, replace: (m) => m + ' ' },
]

function tryAutoConvert(view: EditorView): boolean {
  const { state } = view
  const cursor = state.selection.main.head
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

/** Keymap — only intercepts Space. Tab belongs to indentWithTab. */
export const autoConvertKeymap = keymap.of([
  {
    key: 'Space',
    run: (view) => tryAutoConvert(view),
  },
])
