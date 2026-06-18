/**
 * Obsidian-style Enter key continuation for Markdown editing.
 *
 * When the user presses Enter, this module determines whether the next line
 * should auto-continue the current line's syntax structure (lists, blockquotes,
 * callouts, etc.) and produces the new text content + cursor position.
 *
 * Pure function — no DOM or framework dependencies.
 */

// ── Types ──

interface LineMarker {
  type: 'ul' | 'ol' | 'task' | 'quote'
  bullet?: string   // '-', '*', '+'
  number?: number   // for ordered lists
}

export interface ParsedLine {
  markers: LineMarker[]
  /** Everything after the prefix markers (may be empty) */
  content: string
  /** The full matched prefix including leading whitespace, e.g. "> - " */
  rawPrefix: string
}

export interface EnterResult {
  text: string
  cursorPos: number
}

// ── Public API ──

/**
 * Process an Enter key press in a Markdown editor.
 *
 * @param fullText  The entire current textarea value
 * @param cursorPos The cursor position (selectionStart)
 * @returns null if no continuation is needed (let native Enter handle it),
 *          or { text, cursorPos } with the new full text and cursor position.
 */
export function processEnterKey(fullText: string, cursorPos: number): EnterResult | null {
  // ── Extract current line ──
  const textBeforeCursor = fullText.substring(0, cursorPos)
  const textAfterCursor = fullText.substring(cursorPos)
  const lineStart = textBeforeCursor.lastIndexOf('\n') + 1
  const nextNewline = fullText.indexOf('\n', cursorPos)
  const lineEnd = nextNewline === -1 ? fullText.length : nextNewline
  const fullLineContent = fullText.substring(lineStart, lineEnd)

  // ── Check block context (code / math blocks) ──
  if (isInsideBlock(fullText, lineStart)) {
    return null // Let native Enter pass through — don't auto-close blocks
  }

  // ── Parse the current line's prefix markers ──
  const parsed = parseLinePrefix(fullLineContent)
  if (!parsed) return null // No markers — plain paragraph, native Enter

  // ── Build continuation prefix ──
  const continuation = buildContinuation(parsed)

  // ── Check exit condition: two consecutive empty items ──
  const content = parsed.content.trim()
  if (content === '') {
    const prevLine = getPreviousLine(fullText, lineStart)
    if (prevLine !== null) {
      const prevParsed = parseLinePrefix(prevLine)
      if (prevParsed && prevParsed.rawPrefix === parsed.rawPrefix && prevParsed.content.trim() === '') {
        // Exit: remove the current line's prefix, leaving just a blank line
        const prefixEnd = lineStart + parsed.rawPrefix.length
        const newText = fullText.substring(0, lineStart) + fullText.substring(prefixEnd)
        const newCursorPos = lineStart // cursor at start of the now-empty line
        return { text: newText, cursorPos: newCursorPos }
      }
    }
  }

  // ── Avoid double-space when continuation ends with ' ' and afterCursor starts with ' ' ──
  let effectiveContinuation = continuation
  if (continuation.endsWith(' ') && (textAfterCursor.startsWith(' ') || textAfterCursor.startsWith('\t'))) {
    effectiveContinuation = continuation.slice(0, -1)
  }

  // ── Build result ──
  const insertText = '\n' + effectiveContinuation
  const newText = textBeforeCursor + insertText + textAfterCursor
  const newCursorPos = cursorPos + insertText.length

  return { text: newText, cursorPos: newCursorPos }
}

// ── Block context detection ──

/**
 * Check whether the cursor is inside a fenced code block or display math block.
 * Scans all lines up to the given position and counts unmatched openers.
 */
function isInsideBlock(text: string, upToLineStart: number): boolean {
  const textUpToHere = text.substring(0, upToLineStart)
  const lines = textUpToHere.split('\n')

  let inCode = false
  let inMath = false

  for (const line of lines) {
    const trimmed = line.trim()

    // Code fence: ``` or ~~~ at start of trimmed line
    if (/^(```|~~~)/.test(trimmed)) {
      inCode = !inCode
      continue
    }

    // Display math block: $$ at start of trimmed line (not inside code block)
    if (!inCode && trimmed.startsWith('$$')) {
      inMath = !inMath
    }
  }

  return inCode || inMath
}

// ── Line prefix parsing ──

/**
 * Parse a line's prefix into structured markers.
 *
 * Parsing order (outer → inner):
 *   1. Leading whitespace (indent)
 *   2. Blockquote markers `> ` (can repeat for nesting)
 *   3. List markers (`- `, `* `, `+ `, `1. `, `- [ ] `, `- [x] `)
 *
 * Returns null if no syntax markers are found.
 */
export function parseLinePrefix(line: string): ParsedLine | null {
  let pos = 0
  const markers: LineMarker[] = []

  // Step 1: Consume leading whitespace
  const wsMatch = /^(\s*)/.exec(line)
  const leadingWs = wsMatch?.[1] ?? ''
  pos = leadingWs.length

  // Step 2: Consume blockquote markers (one or more `> `)
  while (pos < line.length && line.substring(pos).startsWith('> ')) {
    markers.push({ type: 'quote' })
    pos += 2
  }

  // Step 3: Consume list marker (at most one)
  const rest = line.substring(pos)

  // Task list: "- [ ] " or "- [x] " or "* [ ] " etc.
  const taskMatch = /^(-|\*|\+) \[([ xX])\] /.exec(rest)
  if (taskMatch) {
    markers.push({ type: 'task', bullet: taskMatch[1] })
    pos += taskMatch[0].length
    return finishParse(line, pos, markers)
  }

  // Ordered list: "1. ", "99. ", etc.
  const olMatch = /^(\d+)\. /.exec(rest)
  if (olMatch) {
    const num = parseInt(olMatch[1]!, 10)
    markers.push({ type: 'ol', number: num, bullet: olMatch[1] })
    pos += olMatch[0].length
    return finishParse(line, pos, markers)
  }

  // Unordered list: "- ", "* ", "+ "
  const ulMatch = /^(-|\*|\+) /.exec(rest)
  if (ulMatch) {
    markers.push({ type: 'ul', bullet: ulMatch[1] })
    pos += ulMatch[0].length
    return finishParse(line, pos, markers)
  }

  // If we only have quote markers (no list), still valid
  if (markers.length > 0) {
    return finishParse(line, pos, markers)
  }

  return null // No markers found
}

function finishParse(line: string, prefixEnd: number, markers: LineMarker[]): ParsedLine {
  const rawPrefix = line.substring(0, prefixEnd)
  const content = line.substring(prefixEnd)
  return { markers, content, rawPrefix }
}

// ── Continuation builder ──

/**
 * Build the prefix string for the next line.
 * Ordered list numbers are incremented, task items are always unchecked.
 */
export function buildContinuation(parsed: ParsedLine): string {
  // Extract leading whitespace from rawPrefix
  const leadingWs = parsed.rawPrefix.match(/^(\s*)/)?.[1] ?? ''
  let result = leadingWs

  for (const marker of parsed.markers) {
    switch (marker.type) {
      case 'quote':
        result += '> '
        break
      case 'ul':
        result += (marker.bullet ?? '-') + ' '
        break
      case 'ol':
        result += ((marker.number ?? 0) + 1).toString() + '. '
        break
      case 'task':
        result += (marker.bullet ?? '-') + ' [ ] '
        break
    }
  }

  return result
}

// ── Helpers ──

/** Get the previous line's text, or null if there is no previous line. */
function getPreviousLine(text: string, currentLineStart: number): string | null {
  if (currentLineStart === 0) return null
  // currentLineStart points to the character after the \n that starts this line
  const prevLineStart = text.lastIndexOf('\n', currentLineStart - 2) + 1
  // The previous line ends at the \n right before currentLineStart
  return text.substring(prevLineStart, currentLineStart - 1)
}
