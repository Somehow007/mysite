<script setup lang="ts">
import { ref, watch, onMounted, onUnmounted, computed } from 'vue'

import { uploadImage, uploadImageByUrl, MAX_IMAGE_FILE_SIZE } from '@/api/image'
import { useToast } from '@/composables/useToast'
import {
  Image as ImageIcon, HelpCircle, X, Bold, Italic, Link, Code,
  List, Quote, Heading, LinkIcon, Loader2, Sigma, Lightbulb,
} from 'lucide-vue-next'

// ── CodeMirror 6 ──
import { EditorView, keymap, drawSelection, dropCursor, highlightActiveLine, highlightSpecialChars } from '@codemirror/view'
import { history, historyKeymap, defaultKeymap, indentWithTab, redo, undo } from '@codemirror/commands'
import { markdown, markdownLanguage } from '@codemirror/lang-markdown'
import { syntaxHighlighting, defaultHighlightStyle, bracketMatching, indentOnInput } from '@codemirror/language'

// ── Custom editor extensions ──
import { livePreview, mathExtension } from '@/editor/livePreview'
import { autoConvertKeymap } from '@/editor/autoConvert'
import { enterContinuationKeymap } from '@/editor/enterContinuation'

const props = defineProps<{
  modelValue: string
  placeholder?: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
  'save': []
}>()

// ── Editor state ──
const cmContainer = ref<HTMLDivElement | null>(null)
const editorView = ref<EditorView | null>(null)

const showShortcuts = ref(false)
const showAutocomplete = ref(false)
const autocompleteItems = ref<string[]>([])
const autocompletePosition = ref({ top: 0, left: 0 })
const selectedIndex = ref(0)

const showUrlDialog = ref(false)
const imageUrl = ref('')
const urlUploading = ref(false)

// ── Callout state ──
const showCalloutDialog = ref(false)
const calloutSearchQuery = ref('')

interface CalloutType {
  type: string
  label: string
  icon: string
}

interface CalloutGroup {
  label: string
  color: string
  types: CalloutType[]
}

const calloutGroups: CalloutGroup[] = [
  {
    label: '信息',
    color: '#448aff',
    types: [
      { type: 'NOTE', label: '备注', icon: '📝' },
      { type: 'INFO', label: '信息', icon: 'ℹ️' },
      { type: 'TODO', label: '待办', icon: '☑️' },
    ],
  },
  {
    label: '成功/提示',
    color: '#00c853',
    types: [
      { type: 'TIP', label: '提示', icon: '💡' },
      { type: 'SUCCESS', label: '成功', icon: '✅' },
      { type: 'CHECK', label: '检查', icon: '✔️' },
      { type: 'DONE', label: '完成', icon: '🏁' },
    ],
  },
  {
    label: '警告/注意',
    color: '#ff9100',
    types: [
      { type: 'WARNING', label: '警告', icon: '⚠️' },
      { type: 'CAUTION', label: '注意', icon: '⚠️' },
      { type: 'QUESTION', label: '问题', icon: '❓' },
      { type: 'ATTENTION', label: '关注', icon: '👀' },
    ],
  },
  {
    label: '错误/危险',
    color: '#ff1744',
    types: [
      { type: 'ERROR', label: '错误', icon: '❌' },
      { type: 'DANGER', label: '危险', icon: '⚡' },
      { type: 'FAILURE', label: '失败', icon: '🚫' },
      { type: 'BUG', label: '缺陷', icon: '🐛' },
    ],
  },
  {
    label: '示例/引用',
    color: '#7c4dff',
    types: [
      { type: 'EXAMPLE', label: '示例', icon: '📋' },
      { type: 'QUOTE', label: '引用', icon: '💬' },
      { type: 'ABSTRACT', label: '摘要', icon: '📄' },
      { type: 'SUMMARY', label: '总结', icon: '📊' },
    ],
  },
]

const filteredCalloutGroups = computed(() => {
  if (!calloutSearchQuery.value.trim()) return calloutGroups
  const q = calloutSearchQuery.value.trim().toLowerCase()
  return calloutGroups
    .map(g => ({
      ...g,
      types: g.types.filter(t => t.type.toLowerCase().includes(q) || t.label.includes(q)),
    }))
    .filter(g => g.types.length > 0)
})

const allCalloutTypes = computed(() => calloutGroups.flatMap(g => g.types))

const uploadingFiles = ref<Map<string, { name: string; progress: number }>>(new Map())

const cursorLine = ref(1)
const cursorCol = ref(1)
const wordCount = ref(0)
const charCount = ref(0)
const lineCount = ref(0)

const toast = useToast()

const isMac = computed(() => {
  if (typeof navigator === 'undefined') return false
  return /Mac|iPod|iPhone|iPad/.test(navigator.platform)
})

const modKey = computed(() => isMac.value ? 'Cmd' : 'Ctrl')

const isUploading = computed(() => uploadingFiles.value.size > 0 || urlUploading.value)

const uploadingProgress = computed(() => {
  if (uploadingFiles.value.size === 0) return 0
  let total = 0
  let count = 0
  uploadingFiles.value.forEach((file) => {
    total += file.progress
    count++
  })
  return count > 0 ? Math.round(total / count) : 0
})

const shortcuts = computed(() => [
  { key: `${modKey.value}+Z`, description: '撤销', syntax: '撤销上一步操作' },
  { key: `${modKey.value}+Shift+Z`, description: '重做', syntax: '重做已撤销操作' },
  { key: `${modKey.value}+Y`, description: '重做（备选）', syntax: '重做已撤销操作' },
  { key: `${modKey.value}+S`, description: '保存', syntax: '保存文章' },
  { key: `${modKey.value}+B`, description: '加粗', syntax: '**文字**' },
  { key: `${modKey.value}+I`, description: '斜体', syntax: '*文字*' },
  { key: `${modKey.value}+U`, description: '下划线', syntax: '<u>文字</u>' },
  { key: `${modKey.value}+K`, description: '插入链接', syntax: '[文字](URL)' },
  { key: `${modKey.value}+Shift+K`, description: '插入代码块', syntax: '```lang\\n代码\\n```' },
  { key: `${modKey.value}+\``, description: '行内代码', syntax: '`代码`' },
  { key: `${modKey.value}+Shift+H`, description: '标题', syntax: '# 标题' },
  { key: `${modKey.value}+Shift+L`, description: '无序列表', syntax: '- 列表项' },
  { key: `${modKey.value}+Shift+O`, description: '有序列表', syntax: '1. 列表项' },
  { key: `${modKey.value}+Shift+Q`, description: '引用', syntax: '> 引用内容' },
  { key: `${modKey.value}+Shift+.`, description: '插入标注', syntax: '> [!NOTE] 标题' },
  { key: `${modKey.value}+Shift+I`, description: '插入图片', syntax: '![描述](图片URL)' },
  { key: 'Tab', description: '插入缩进', syntax: '    ' },
  { key: 'Shift+Tab', description: '删除缩进', syntax: '删除行首缩进' },
])

// ── Sync: external modelValue → CM6 ──
// Track the last value emitted by us so we can short-circuit the echo that
// comes back when the parent re-sets the same value. This avoids an O(n)
// doc.toString() comparison on every keystroke.
let lastEmittedValue = ''
watch(
  () => props.modelValue,
  (newVal) => {
    if (newVal === lastEmittedValue) return // ignore echo from our own emit
    const view = editorView.value
    if (!view) return
    const current = view.state.doc.toString()
    if (newVal !== current) {
      view.dispatch({
        changes: { from: 0, to: current.length, insert: newVal },
      })
    }
  },
)

// ── Cursor/selection tracking ──
function updateCursorStats(view: EditorView) {
  const pos = view.state.selection.main.head
  const doc = view.state.doc
  const line = doc.lineAt(pos)
  cursorLine.value = line.number
  cursorCol.value = pos - line.from + 1

  // Use doc.length / doc.lines (O(1)) instead of doc.toString() (O(n))
  charCount.value = doc.length
  lineCount.value = doc.lines
  // Word count is handled by updateWordCount (only on docChanged)
}

// Separate word-count update (heavier, debounced so rapid typing doesn't
// trigger an O(n) doc.toString() + regex scan on every keystroke).
let wordCountTimer: ReturnType<typeof setTimeout> | null = null
function updateWordCount(view: EditorView) {
  if (wordCountTimer) clearTimeout(wordCountTimer)
  wordCountTimer = setTimeout(() => {
    if (!editorView.value) return
    const text = view.state.doc.toString()
    const words = text.trim().split(/\s+/).filter(Boolean)
    wordCount.value = words.length
  }, 300)
}

// ── Autocomplete detection ──
function checkAutocomplete(view: EditorView) {
  const pos = view.state.selection.main.head
  const line = view.state.doc.lineAt(pos)
  const textBeforeCursor = view.state.sliceDoc(line.from, pos)

  // Callout autocomplete
  const calloutMatch = textBeforeCursor.match(/^> \[!(\w*)$/i)
  if (calloutMatch) {
    const partial = (calloutMatch[1] ?? '').toLowerCase()
    const items = allCalloutTypes.value
      .filter(ct => ct.type.toLowerCase().startsWith(partial))
      .slice(0, 8)
      .map(ct => `> [!${ct.type}] ${ct.icon} ${ct.label}`)
    if (items.length > 0) {
      showAutocompletePanel(items, view)
      return
    }
  }

  if (textBeforeCursor.match(/^#{1,6}$/)) {
    showAutocompletePanel(['# 一级标题', '## 二级标题', '### 三级标题', '#### 四级标题', '##### 五级标题', '###### 六级标题'], view)
    return
  }
  if (textBeforeCursor.match(/^[-*+]$/)) {
    showAutocompletePanel(['- 列表项', '* 列表项', '+ 列表项'], view)
    return
  }
  if (textBeforeCursor.match(/^\d+\.$/)) {
    showAutocompletePanel(['1. 有序列表'], view)
    return
  }
  if (textBeforeCursor.match(/^>$/)) {
    const items = ['> 引用内容', ...allCalloutTypes.value.slice(0, 4).map(ct => `> [!${ct.type}] ${ct.icon} ${ct.label}`)]
    showAutocompletePanel(items, view)
    return
  }
  if (textBeforeCursor.match(/^`{1,2}$/)) {
    showAutocompletePanel(['`行内代码`', '```lang 代码块 ```'], view)
    return
  }
  if (textBeforeCursor.match(/^\[.*\]$/)) {
    showAutocompletePanel(['[链接文字](URL)', '![图片描述](图片URL)'], view)
    return
  }

  showAutocomplete.value = false
}

function showAutocompletePanel(items: string[], view: EditorView) {
  autocompleteItems.value = items
  selectedIndex.value = 0
  showAutocomplete.value = true

  const pos = view.state.selection.main.head
  const coords = view.coordsAtPos(pos)
  if (coords) {
    autocompletePosition.value = {
      top: coords.bottom,
      left: coords.left,
    }
  }
}

function applyAutocomplete() {
  const view = editorView.value
  if (!view || selectedIndex.value >= autocompleteItems.value.length) return

  const selected = autocompleteItems.value[selectedIndex.value]
  const pos = view.state.selection.main.head
  const line = view.state.doc.lineAt(pos)

  // Replace current line prefix with the selected item
  view.dispatch({
    changes: { from: line.from, to: line.to, insert: selected },
  })

  showAutocomplete.value = false
  view.focus()
}

// ── Toolbar actions ──

function getSelection(): { from: number; to: number; text: string } | null {
  const view = editorView.value
  if (!view) return null
  const { from, to } = view.state.selection.main
  return { from, to, text: view.state.sliceDoc(from, to) }
}

function replaceSelection(insertion: string, anchorOffset?: number) {
  const view = editorView.value
  if (!view) return
  const { from, to } = view.state.selection.main
  view.focus()
  view.dispatch({
    changes: { from, to, insert: insertion },
    selection: { anchor: anchorOffset != null ? from + anchorOffset : from + insertion.length },
  })
}

function insertMarkdown(before: string, after: string, placeholder: string) {
  const sel = getSelection()
  if (!sel) return
  const selectedText = sel.text || placeholder
  const insertion = before + selectedText + after
  const anchorOffset = before.length + selectedText.length
  replaceSelection(insertion, anchorOffset)
}

function insertLink() {
  const sel = getSelection()
  if (!sel) return
  const selectedText = sel.text || '链接文字'
  const linkMarkdown = `[${selectedText}](URL)`
  replaceSelection(linkMarkdown, sel.from + selectedText.length + 3) // select "URL"
}

function insertImageMarkdown(url: string, alt?: string) {
  const view = editorView.value
  if (!view) return
  const pos = view.state.selection.main.head
  const imageMarkdown = `![${alt || '图片'}](${url})`
  view.focus()
  view.dispatch({
    changes: { from: pos, insert: imageMarkdown },
    selection: { anchor: pos + imageMarkdown.length },
  })
}

function insertCodeBlock() {
  const sel = getSelection()
  if (!sel) return
  const codeContent = sel.text || ''
  const beforeCursor = sel.from > 0 ? '\n' : ''
  const codeBlock = `${beforeCursor}\`\`\`lang\n${codeContent}\n\`\`\`\n`
  replaceSelection(codeBlock, beforeCursor.length + 3) // select "lang"
}

function insertHeading() {
  const view = editorView.value
  if (!view) return
  const pos = view.state.selection.main.head
  const line = view.state.doc.lineAt(pos)
  const hasContent = line.text.trim().length > 0 && pos > line.from

  const prefix = hasContent ? '\n# ' : '# '
  const insertPos = hasContent ? pos : line.from

  view.focus()
  view.dispatch({
    changes: { from: insertPos, insert: prefix },
    selection: { anchor: insertPos + prefix.length },
  })
}

function insertList(prefix: string) {
  const view = editorView.value
  if (!view) return
  const pos = view.state.selection.main.head
  const line = view.state.doc.lineAt(pos)
  const hasContent = line.text.trim().length > 0 && pos > line.from

  const listMarkdown = prefix + ' '
  const insertion = hasContent ? '\n' + listMarkdown : listMarkdown
  const insertPos = hasContent ? pos : line.from

  view.focus()
  view.dispatch({
    changes: { from: insertPos, insert: insertion },
    selection: { anchor: insertPos + insertion.length },
  })
}

function insertQuote() {
  const view = editorView.value
  if (!view) return
  const pos = view.state.selection.main.head
  const line = view.state.doc.lineAt(pos)
  const hasContent = line.text.trim().length > 0 && pos > line.from

  const insertion = hasContent ? '\n> ' : '> '
  const insertPos = hasContent ? pos : line.from

  view.focus()
  view.dispatch({
    changes: { from: insertPos, insert: insertion },
    selection: { anchor: insertPos + insertion.length },
  })
}

function insertMath() {
  const sel = getSelection()
  if (!sel) return
  if (sel.text.trim()) {
    replaceSelection('$' + sel.text + '$', sel.text.length + 1)
  } else {
    replaceSelection('$ $', 2)
  }
}

function insertCallout(type: string) {
  const sel = getSelection()
  if (!sel) return

  const beforeCursor = sel.from > 0 ? '\n' : ''

  if (sel.text.trim()) {
    const lines = sel.text.split('\n')
    const wrappedLines = lines.map(line => {
      if (line.length === 0) return '>'
      if (line.startsWith('> ')) return line
      return '> ' + line
    }).join('\n')
    const calloutText = `${beforeCursor}> [!${type}]\n${wrappedLines}\n`
    replaceSelection(calloutText, beforeCursor.length + 4 + type.length + 1)
  } else {
    const template = `${beforeCursor}> [!${type}] 标题\n> 内容\n`
    replaceSelection(template, beforeCursor.length + 4 + type.length + 2) // select "标题"
  }

  showCalloutDialog.value = false
  calloutSearchQuery.value = ''
}

// ── Custom editor keymap ──

const customKeymap = keymap.of([
  {
    key: 'Mod-s',
    run: () => { emit('save'); return true },
    preventDefault: true,
  },
  {
    key: 'Mod-b',
    run: () => { insertMarkdown('**', '**', '粗体文本'); return true },
    preventDefault: true,
  },
  {
    key: 'Mod-i',
    run: () => { insertMarkdown('*', '*', '斜体文本'); return true },
    preventDefault: true,
  },
  {
    key: 'Mod-u',
    run: () => { insertMarkdown('<u>', '</u>', '下划线文本'); return true },
    preventDefault: true,
  },
  {
    key: 'Mod-k',
    run: () => { insertLink(); return true },
    preventDefault: true,
  },
  {
    key: 'Mod-Shift-k',
    run: () => { insertCodeBlock(); return true },
    preventDefault: true,
  },
  {
    key: 'Mod-`',
    run: () => { insertMarkdown('`', '`', '代码'); return true },
    preventDefault: true,
  },
  {
    key: 'Mod-Shift-h',
    run: () => { insertHeading(); return true },
    preventDefault: true,
  },
  {
    key: 'Mod-Shift-l',
    run: () => { insertList('-'); return true },
    preventDefault: true,
  },
  {
    key: 'Mod-Shift-o',
    run: () => { insertList('1.'); return true },
    preventDefault: true,
  },
  {
    key: 'Mod-Shift-q',
    run: () => { insertQuote(); return true },
    preventDefault: true,
  },
  {
    key: 'Mod-Shift-.',
    run: () => { showCalloutDialog.value = true; return true },
    preventDefault: true,
  },
  {
    key: 'Mod-Shift-i',
    run: () => { handleImageUpload(); return true },
    preventDefault: true,
  },
])

// ── Create CM6 editor ──

onMounted(() => {
  if (!cmContainer.value) return

  try {
    initEditor()
  } catch (e) {
    console.error('[MarkdownEditor] init failed, falling back to textarea:', e)
    initFallback()
  }
})

function initEditor() {
  if (!cmContainer.value) return

  const updateListener = EditorView.updateListener.of((update) => {
    if (update.docChanged) {
      const text = update.state.doc.toString()
      lastEmittedValue = text
      emit('update:modelValue', text)
      checkAutocomplete(update.view)
      updateWordCount(update.view)
    }
    if (update.selectionSet && !update.docChanged) {
      // Close autocomplete on click or non-typing cursor movement.
      // Without this, the panel stays open and handleAutocompleteKeydown
      // keeps intercepting arrow keys, breaking cursor movement.
      showAutocomplete.value = false
    }
    if (update.docChanged || update.selectionSet) {
      updateCursorStats(update.view)
    }
  })

  const view = new EditorView({
    doc: props.modelValue,
    parent: cmContainer.value,
    extensions: [
      // ── Basic editing ──
      drawSelection(),
      dropCursor(),
      highlightActiveLine(),
      highlightSpecialChars(),
      history(),
      indentOnInput(),
      bracketMatching(),

      // ── Keymaps (order matters: later = higher priority) ──
      keymap.of([...defaultKeymap, ...historyKeymap, indentWithTab]),

      // ── Explicit undo/redo with preventDefault to override browser shortcuts ──
      keymap.of([
        { key: 'Mod-z', run: undo, preventDefault: true },
        { key: 'Mod-Shift-z', run: redo, preventDefault: true },
        { key: 'Mod-y', run: redo, preventDefault: true },
      ]),

      customKeymap,
      enterContinuationKeymap,
      autoConvertKeymap,

      // ── Markdown language（含数学公式扩展）──
      markdown({
        base: markdownLanguage,
        codeLanguages: [],
        addKeymap: true,
        extensions: [mathExtension],
      }),

      // ── Syntax highlighting ──
      syntaxHighlighting(defaultHighlightStyle, { fallback: true }),

      // ── Live preview (WYSIWYG decorations) ──
      livePreview(),

      // ── Listeners ──
      updateListener,

      // ── Theme ──
      EditorView.theme({
        '&': {
          height: '100%',
          fontSize: '0.875rem',
          fontFamily: 'ui-monospace, SFMono-Regular, SF Mono, Menlo, Consolas, "Liberation Mono", monospace',
        },
        '.cm-scroller': {
          overflow: 'auto',
          lineHeight: '1.7',
          overscrollBehavior: 'contain',
        },
        '.cm-content': {
          padding: '0.75rem',
          caretColor: 'var(--text-primary)',
        },
        '.cm-activeLine': {
          backgroundColor: 'transparent',
        },
        '.cm-cursor': {
          borderLeftColor: 'var(--text-primary)',
        },
        '.cm-selectionMatch': {
          backgroundColor: 'rgba(59, 130, 246, 0.15)',
        },
        '&.cm-focused .cm-selectionBackground, .cm-selectionBackground': {
          backgroundColor: 'rgba(59, 130, 246, 0.25) !important',
        },
        '.cm-line': {
          padding: '0 0.25rem',
          transition: 'background-color 0.12s ease-out, border-color 0.12s ease-out',
        },
      }),
    ],
  })

  editorView.value = view
  updateCursorStats(view)
  updateWordCount(view)

  // Handle paste/drop for images (store refs so we can remove on unmount)
  const cmDom = view.dom
  cmDom.addEventListener('paste', handlePaste)
  cmDom.addEventListener('drop', handleDrop)
  cmDom.addEventListener('dragover', handleDragOver)
  // Close autocomplete on any mouse click inside the editor. This is a
  // safety net alongside the selectionSet listener — it guarantees the
  // panel closes even if the click doesn't change the cursor position
  // (e.g. clicking on a Decoration.replace widget at the current pos).
  cmDom.addEventListener('mousedown', handleEditorMousedown)
  boundCmDom = cmDom

  // Autocomplete keyboard: listen on the container to intercept keys
  // when the autocomplete panel is visible (CM6 keymaps won't see our panel)
  cmContainer.value.addEventListener('keydown', handleAutocompleteKeydown, true)
}

function handleEditorMousedown() {
  showAutocomplete.value = false
}

// Error-boundary fallback: if CM6 fails to initialise, mount a plain textarea
// so the user can still edit content instead of facing a blank/broken editor.
const fallbackMode = ref(false)
function initFallback() {
  fallbackMode.value = true
}

let boundCmDom: HTMLElement | null = null

onUnmounted(() => {
  // Cancel any pending word-count timer
  if (wordCountTimer) { clearTimeout(wordCountTimer); wordCountTimer = null }
  // Remove DOM event listeners to avoid leaks
  if (boundCmDom) {
    boundCmDom.removeEventListener('paste', handlePaste)
    boundCmDom.removeEventListener('drop', handleDrop)
    boundCmDom.removeEventListener('dragover', handleDragOver)
    boundCmDom.removeEventListener('mousedown', handleEditorMousedown)
    boundCmDom = null
  }
  // Remove autocomplete keydown listener from container
  if (cmContainer.value) {
    cmContainer.value.removeEventListener('keydown', handleAutocompleteKeydown, true)
  }
  editorView.value?.destroy()
  editorView.value = null
  lastEmittedValue = ''
})

// ── Image upload ──

function handleImageUpload() {
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = 'image/jpeg,image/png,image/gif,image/webp,image/svg+xml'
  input.multiple = true
  input.onchange = (e: Event) => {
    const target = e.target as HTMLInputElement
    const files = target.files
    if (files) {
      for (let i = 0; i < files.length; i++) {
        const file = files[i]
        if (file) doUploadFile(file)
      }
    }
  }
  input.click()
}

async function doUploadFile(file: File) {
  if (file.size > MAX_IMAGE_FILE_SIZE) {
    toast.error(`图片 "${file.name}" 超过大小限制（最大 5MB）`)
    return
  }

  const fileId = `${Date.now()}_${Math.random().toString(36).substring(2, 8)}`
  uploadingFiles.value.set(fileId, { name: file.name, progress: 0 })

  try {
    const result = await uploadImage(file, (progressEvent) => {
      uploadingFiles.value.set(fileId, { name: file.name, progress: progressEvent.progress })
    })
    insertImageMarkdown(result.url, result.originalName)
  } catch (e) {
    console.error('图片上传失败:', e)
    const errorMessage = e instanceof Error ? e.message : '未知错误'
    if (errorMessage.includes('413') || errorMessage.includes('大小超出限制') || errorMessage.includes('too large')) {
      toast.error('图片文件过大，请压缩后重试（最大 5MB）')
    } else {
      toast.error('图片上传失败：' + errorMessage)
    }
  } finally {
    uploadingFiles.value.delete(fileId)
  }
}

function openUrlDialog() {
  imageUrl.value = ''
  showUrlDialog.value = true
}

async function handleUrlUpload() {
  if (!imageUrl.value.trim()) return

  urlUploading.value = true
  try {
    const result = await uploadImageByUrl(imageUrl.value.trim())
    insertImageMarkdown(result.url, result.originalName)
    showUrlDialog.value = false
    imageUrl.value = ''
  } catch (e) {
    console.error('URL图片上传失败:', e)
    toast.error('URL图片上传失败：' + (e instanceof Error ? e.message : '未知错误'))
  } finally {
    urlUploading.value = false
  }
}

// ── Drag & drop / paste event handlers ──

function handleDragOver(e: DragEvent) {
  if (e.dataTransfer?.types.includes('Files')) {
    e.preventDefault()
  }
}

function handleDrop(e: DragEvent) {
  const files = e.dataTransfer?.files
  if (files && files.length > 0) {
    e.preventDefault()
    for (let i = 0; i < files.length; i++) {
      const file = files[i]
      if (file && file.type.startsWith('image/')) {
        doUploadFile(file)
      }
    }
  }
}

function handlePaste(e: ClipboardEvent) {
  const items = e.clipboardData?.items
  if (!items) return

  for (let i = 0; i < items.length; i++) {
    const item = items[i]
    if (item && item.type.startsWith('image/')) {
      e.preventDefault()
      const file = item.getAsFile()
      if (file) {
        doUploadFile(file)
      }
      return
    }
  }
}

// ── Autocomplete keyboard ──

function handleAutocompleteKeydown(e: KeyboardEvent) {
  // Only intercept keys when the panel is actually visible with items.
  // Without the autocompleteItems check, the panel could be in a stale
  // "open but empty" state that swallows arrow keys, breaking cursor
  // movement (especially Up arrow jumping to document top).
  if (!showAutocomplete.value || autocompleteItems.value.length === 0) return
  if (e.key === 'ArrowDown') {
    e.preventDefault()
    e.stopPropagation()
    selectedIndex.value = Math.min(selectedIndex.value + 1, autocompleteItems.value.length - 1)
  } else if (e.key === 'ArrowUp') {
    e.preventDefault()
    e.stopPropagation()
    selectedIndex.value = Math.max(selectedIndex.value - 1, 0)
  } else if (e.key === 'Enter' || e.key === 'Tab') {
    e.preventDefault()
    e.stopPropagation()
    applyAutocomplete()
  } else if (e.key === 'Escape') {
    e.preventDefault()
    e.stopPropagation()
    showAutocomplete.value = false
  }
}
</script>

<template>
  <div class="markdown-editor h-full flex flex-col">
    <!-- Toolbar -->
    <div class="editor-toolbar glass glass-sm flex items-center gap-1 p-1.5 border-b border-border overflow-x-auto">
      <button
        @click="insertMarkdown('**', '**', '粗体文本')"
        class="toolbar-btn"
        :title="`加粗 (${modKey}+B)`"
      >
        <Bold :size="15" />
      </button>
      <button
        @click="insertMarkdown('*', '*', '斜体文本')"
        class="toolbar-btn"
        :title="`斜体 (${modKey}+I)`"
      >
        <Italic :size="15" />
      </button>
      <button
        @click="insertLink"
        class="toolbar-btn"
        :title="`插入链接 (${modKey}+K)`"
      >
        <Link :size="15" />
      </button>
      <button
        @click="insertCodeBlock"
        class="toolbar-btn"
        :title="`代码块 (${modKey}+Shift+K)`"
      >
        <Code :size="15" />
      </button>
      <button
        @click="insertHeading"
        class="toolbar-btn"
        :title="`标题 (${modKey}+Shift+H)`"
      >
        <Heading :size="15" />
      </button>
      <button
        @click="insertList('-')"
        class="toolbar-btn"
        :title="`无序列表 (${modKey}+Shift+L)`"
      >
        <List :size="15" />
      </button>
      <button
        @click="insertQuote"
        class="toolbar-btn"
        :title="`引用 (${modKey}+Shift+Q)`"
      >
        <Quote :size="15" />
      </button>
      <div class="w-px h-4 bg-border mx-0.5 flex-shrink-0" />
      <button
        @click="handleImageUpload"
        class="toolbar-btn"
        :title="`上传图片 (${modKey}+Shift+I)`"
      >
        <ImageIcon :size="15" />
      </button>
      <button
        @click="openUrlDialog"
        class="toolbar-btn"
        title="通过URL插入图片"
      >
        <LinkIcon :size="15" />
      </button>
      <div class="w-px h-4 bg-border mx-0.5 flex-shrink-0" />
      <button
        @click="insertMath"
        class="toolbar-btn"
        title="插入行内公式 ($...$)"
      >
        <Sigma :size="15" />
      </button>
      <div class="w-px h-4 bg-border mx-0.5 flex-shrink-0" />
      <button
        @click="showCalloutDialog = true"
        class="toolbar-btn"
        :title="`插入标注 (${modKey}+Shift+.)`"
      >
        <Lightbulb :size="15" />
      </button>
      <div class="flex-1 min-w-4" />
      <div v-if="isUploading" class="flex items-center gap-1.5 text-xs text-accent flex-shrink-0">
        <Loader2 :size="12" class="animate-spin" />
        <span>上传中 {{ uploadingProgress }}%</span>
      </div>
      <button
        @click="showShortcuts = true"
        class="toolbar-btn"
        title="快捷键帮助"
      >
        <HelpCircle :size="15" />
      </button>
    </div>

    <!-- CM6 Editor -->
    <div class="editor-content flex-1 min-h-0 overflow-hidden bg-bg-secondary">
      <div v-if="!fallbackMode" ref="cmContainer" class="cm-editor-container h-full w-full" />
      <textarea
        v-else
        :value="modelValue"
        @input="$emit('update:modelValue', ($event.target as HTMLTextAreaElement).value)"
        class="fallback-textarea h-full w-full resize-none border-0 outline-none p-3 bg-transparent text-sm font-mono"
        :placeholder="placeholder"
      />
    </div>

    <!-- Status bar -->
    <div class="editor-status-bar flex items-center gap-4 px-3 py-1 text-xs text-text-muted border-t border-border bg-bg-secondary/50 select-none flex-shrink-0">
      <span>{{ wordCount }} 字</span>
      <span>{{ charCount }} 字符</span>
      <span>{{ lineCount }} 行</span>
      <span class="ml-auto">行 {{ cursorLine }}, 列 {{ cursorCol }}</span>
    </div>

    <!-- Autocomplete panel -->
    <div
      v-if="showAutocomplete && autocompleteItems.length > 0"
      class="autocomplete-panel fixed glass glass-sm rounded-lg overflow-hidden z-50"
      :style="{ top: `${autocompletePosition.top}px`, left: `${autocompletePosition.left}px` }"
    >
      <div
        v-for="(item, index) in autocompleteItems"
        :key="index"
        class="px-3 py-1.5 text-sm cursor-pointer hover:bg-bg-code transition-colors"
        :class="{ 'bg-bg-code': index === selectedIndex }"
        @click="selectedIndex = index; applyAutocomplete()"
      >
        {{ item }}
      </div>
    </div>

    <!-- URL dialog -->
    <div
      v-if="showUrlDialog"
      class="fixed inset-0 bg-black/50 flex items-center justify-center z-50"
      @click="showUrlDialog = false"
    >
      <div
        class="glass glass-lg rounded-lg w-full max-w-md mx-4"
        @click.stop
      >
        <div class="flex items-center justify-between p-4 border-b border-border">
          <h3 class="text-lg font-semibold">通过URL插入图片</h3>
          <button
            @click="showUrlDialog = false"
            class="p-1 rounded hover:bg-bg-code transition-colors"
          >
            <X :size="18" />
          </button>
        </div>
        <div class="p-4 space-y-4">
          <div>
            <label class="block text-sm font-medium text-text-primary mb-1.5">
              图片URL
            </label>
            <input
              v-model="imageUrl"
              type="url"
              placeholder="https://example.com/image.jpg"
              class="input-base"
              @keydown.enter="handleUrlUpload"
            />
          </div>
          <p class="text-xs text-text-muted">
            图片将从该URL下载并保存到服务器，避免外链失效。
          </p>
          <div class="flex justify-end gap-3">
            <button
              @click="showUrlDialog = false"
              class="btn-secondary"
            >
              取消
            </button>
            <button
              @click="handleUrlUpload"
              :disabled="!imageUrl.trim() || urlUploading"
              class="btn-primary disabled:opacity-50"
            >
              <Loader2 v-if="urlUploading" :size="14" class="animate-spin" />
              插入
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- Callout type picker dialog -->
    <div
      v-if="showCalloutDialog"
      class="fixed inset-0 bg-black/50 flex items-center justify-center z-50"
      @click="showCalloutDialog = false; calloutSearchQuery = ''"
    >
      <div
        class="glass glass-lg rounded-lg w-full max-w-lg mx-4 max-h-[80vh] flex flex-col"
        @click.stop
      >
        <div class="flex items-center justify-between p-4 border-b border-border">
          <h3 class="text-lg font-semibold">插入标注</h3>
          <button
            @click="showCalloutDialog = false; calloutSearchQuery = ''"
            class="p-1 rounded hover:bg-bg-code transition-colors"
          >
            <X :size="18" />
          </button>
        </div>
        <div class="p-3 border-b border-border">
          <input
            v-model="calloutSearchQuery"
            type="text"
            placeholder="搜索标注类型..."
            class="input-base text-sm"
            @keydown.escape="showCalloutDialog = false; calloutSearchQuery = ''"
          />
        </div>
        <div class="p-4 overflow-y-auto flex-1">
          <div
            v-for="group in filteredCalloutGroups"
            :key="group.label"
            class="mb-4 last:mb-0"
          >
            <div class="flex items-center gap-2 mb-2">
              <span
                class="w-3 h-3 rounded-full flex-shrink-0"
                :style="{ backgroundColor: group.color }"
              />
              <span class="text-xs font-medium text-text-muted uppercase tracking-wide">
                {{ group.label }}
              </span>
            </div>
            <div class="grid grid-cols-4 gap-2">
              <button
                v-for="ct in group.types"
                :key="ct.type"
                @click="insertCallout(ct.type)"
                class="flex flex-col items-center gap-1 p-2.5 rounded-lg border border-border hover:border-accent hover:bg-accent-subtle transition-all duration-150 text-center"
                :title="`${ct.label} — > [!${ct.type}]`"
              >
                <span class="text-lg">{{ ct.icon }}</span>
                <span class="text-xs font-medium text-text-secondary leading-tight">{{ ct.label }}</span>
              </button>
            </div>
          </div>
          <div
            v-if="filteredCalloutGroups.length === 0"
            class="text-center py-8 text-text-muted text-sm"
          >
            没有匹配的标注类型
          </div>
        </div>
      </div>
    </div>

    <!-- Shortcuts dialog -->
    <div
      v-if="showShortcuts"
      class="fixed inset-0 bg-black/50 flex items-center justify-center z-50"
      @click="showShortcuts = false"
    >
      <div
        class="glass glass-lg rounded-lg max-w-2xl w-full mx-4 max-h-[80vh] overflow-auto"
        @click.stop
      >
        <div class="flex items-center justify-between p-4 border-b border-border">
          <h3 class="text-lg font-semibold">Markdown 快捷键</h3>
          <button
            @click="showShortcuts = false"
            class="p-1 rounded hover:bg-bg-code transition-colors"
          >
            <X :size="18" />
          </button>
        </div>
        <div class="p-4">
          <div class="grid gap-2">
            <div
              v-for="shortcut in shortcuts"
              :key="shortcut.key"
              class="flex items-center justify-between p-2 rounded bg-bg-code"
            >
              <div class="flex items-center gap-3">
                <kbd class="px-2 py-1 text-xs font-mono bg-bg-secondary border border-border rounded">
                  {{ shortcut.key }}
                </kbd>
                <span class="text-sm">{{ shortcut.description }}</span>
              </div>
              <code class="text-xs text-text-muted">
                {{ shortcut.syntax }}
              </code>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.markdown-editor {
  position: relative;
  display: flex;
  flex-direction: column;
  height: 100%;
}

.editor-content {
  position: relative;
}

.cm-editor-container :deep(.cm-editor) {
  height: 100%;
  outline: none;
}

.cm-editor-container :deep(.cm-scroller) {
  font-family: ui-monospace, SFMono-Regular, SF Mono, Menlo, Consolas, 'Liberation Mono', monospace;
  line-height: 1.7;
  tab-size: 4;
  overscroll-behavior: contain;
}

/* Smooth decoration transitions for Obsidian-like rendering feel.
   Only transition inexpensive properties to avoid layout thrashing. */
.cm-editor-container :deep(.cm-line) {
  transition: background-color 0.12s ease-out, border-color 0.12s ease-out;
}

/* Ensure widgets don't block mouse events — clicks pass through to the
   underlying line element so CM6 can place the cursor correctly. */
.cm-editor-container :deep(.cm-lp-katex-inline),
.cm-editor-container :deep(.cm-lp-katex-display),
.cm-editor-container :deep(.cm-lp-callout-hdr),
.cm-editor-container :deep(.cm-lp-bullet),
.cm-editor-container :deep(.cm-lp-ol),
.cm-editor-container :deep(.cm-lp-task) {
  pointer-events: none;
}

.toolbar-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 4px 6px;
  border-radius: 4px;
  color: var(--text-secondary);
  transition: background-color 0.15s, color 0.15s;
  flex-shrink: 0;
}

.toolbar-btn:hover {
  background-color: var(--bg-code);
  color: var(--text-primary);
}

.editor-status-bar {
  font-variant-numeric: tabular-nums;
}

.autocomplete-panel {
  min-width: 200px;
  max-width: 400px;
}

@media (max-width: 768px) {
  .editor-status-bar {
    gap: 0.5rem;
    font-size: 0.65rem;
    padding: 0.125rem 0.5rem;
  }
}
</style>
