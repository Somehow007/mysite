<script setup lang="ts">
import { ref, watch, onMounted, onUnmounted, nextTick, computed, markRaw } from 'vue'
import { useDebounceFn } from '@vueuse/core'
import { useMarkdown } from '@/composables/useMarkdown'
import { uploadImage, uploadImageByUrl, MAX_IMAGE_FILE_SIZE } from '@/api/image'
import { useToast } from '@/composables/useToast'
import { Image as ImageIcon, HelpCircle, X, Bold, Italic, Link, Code, List, Quote, Heading, LinkIcon, Loader2, Sigma, Lightbulb } from 'lucide-vue-next'

interface HistoryEntry {
  content: string
  selectionStart: number
  selectionEnd: number
}

class UndoManager {
  private undoStack: HistoryEntry[] = []
  private redoStack: HistoryEntry[] = []
  private maxSize = 100

  push(entry: HistoryEntry) {
    if (this.undoStack.length > 0 && this.undoStack[this.undoStack.length - 1]!.content === entry.content) return
    this.undoStack.push({ ...entry })
    this.redoStack = []
    if (this.undoStack.length > this.maxSize) this.undoStack.shift()
  }

  undo(current: HistoryEntry): HistoryEntry | null {
    if (this.undoStack.length <= 1) return null
    this.redoStack.push({ ...current })
    return this.undoStack.pop()!
  }

  redo(current: HistoryEntry): HistoryEntry | null {
    if (this.redoStack.length === 0) return null
    const entry = this.redoStack.pop()!
    this.undoStack.push({ ...current })
    return entry
  }

  canUndo() { return this.undoStack.length > 1 }
  canRedo() { return this.redoStack.length > 0 }

  reset(entry: HistoryEntry) {
    this.undoStack = [{ ...entry }]
    this.redoStack = []
  }
}

const props = defineProps<{
  modelValue: string
  placeholder?: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
  'save': []
}>()

const textareaRef = ref<HTMLTextAreaElement | null>(null)
const previewRef = ref<HTMLDivElement | null>(null)

const content = ref(props.modelValue)
const showPreview = ref(true)
const showShortcuts = ref(false)
const showAutocomplete = ref(false)
const autocompleteItems = ref<string[]>([])
const autocompletePosition = ref({ top: 0, left: 0 })
const selectedIndex = ref(0)

const showUrlDialog = ref(false)
const imageUrl = ref('')
const urlUploading = ref(false)

// Guard to prevent undo/redo double-firing when both keydown and
// beforeinput handlers catch the same Cmd+Z / Cmd+Shift+Z action.
let undoRedoHandledByKeydown = false

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

const isRestoring = ref(false)
const cursorLine = ref(1)
const cursorCol = ref(1)
const wordCount = ref(0)
const charCount = ref(0)
const lineCount = ref(0)

const undoManager = markRaw(new UndoManager())

const { renderedHtml, render, rendering } = useMarkdown()
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
  { key: `${modKey.value}+A`, description: '全选', syntax: '选中全部内容' },
  { key: `${modKey.value}+X`, description: '剪切', syntax: '剪切选中内容' },
  { key: `${modKey.value}+C`, description: '复制', syntax: '复制选中内容' },
  { key: `${modKey.value}+V`, description: '粘贴', syntax: '粘贴剪贴板内容' },
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

watch(
  () => props.modelValue,
  (newVal) => {
    if (newVal !== content.value) {
      content.value = newVal
      updateCounts(newVal)
    }
  },
)

watch(content, (newVal) => {
  if (!isRestoring.value) {
    emit('update:modelValue', newVal)
    debouncedPushUndo()
  }
  updateCounts(newVal)
  debouncedUpdatePreview()
})

watch(showPreview, (newVal) => {
  if (newVal) updatePreview()
})

const debouncedUpdatePreview = useDebounceFn(updatePreview, 300)

const debouncedPushUndo = useDebounceFn(() => {
  if (!textareaRef.value || isRestoring.value) return
  undoManager.push({
    content: content.value,
    selectionStart: textareaRef.value.selectionStart,
    selectionEnd: textareaRef.value.selectionEnd,
  })
}, 400)

onMounted(() => {
  if (textareaRef.value) {
    textareaRef.value.addEventListener('beforeinput', handleBeforeInput)
    textareaRef.value.addEventListener('keydown', handleKeyDown)
    textareaRef.value.addEventListener('input', handleInput)
    textareaRef.value.addEventListener('paste', handlePaste)
    textareaRef.value.addEventListener('drop', handleDrop)
    textareaRef.value.addEventListener('dragover', handleDragOver)
    textareaRef.value.addEventListener('scroll', handleEditorScroll, { passive: true })
    textareaRef.value.addEventListener('click', updateCursorPosition)
    textareaRef.value.addEventListener('keyup', updateCursorPosition)
  }
  updateCounts(content.value)
  updateCursorPosition()
  undoManager.reset({
    content: content.value,
    selectionStart: 0,
    selectionEnd: 0,
  })
  updatePreview()
})

onUnmounted(() => {
  if (textareaRef.value) {
    textareaRef.value.removeEventListener('beforeinput', handleBeforeInput)
    textareaRef.value.removeEventListener('keydown', handleKeyDown)
    textareaRef.value.removeEventListener('input', handleInput)
    textareaRef.value.removeEventListener('paste', handlePaste)
    textareaRef.value.removeEventListener('drop', handleDrop)
    textareaRef.value.removeEventListener('dragover', handleDragOver)
    textareaRef.value.removeEventListener('scroll', handleEditorScroll)
    textareaRef.value.removeEventListener('click', updateCursorPosition)
    textareaRef.value.removeEventListener('keyup', updateCursorPosition)
  }
})

async function updatePreview() {
  if (showPreview.value && content.value) {
    await render(content.value)
  } else if (!content.value) {
    await render('')
  }
}

function updateCounts(text: string) {
  charCount.value = text.length
  lineCount.value = text.split('\n').length
  const words = text.trim().split(/\s+/).filter(Boolean)
  wordCount.value = words.length
}

function updateCursorPosition() {
  if (!textareaRef.value) return
  const pos = textareaRef.value.selectionStart
  const text = content.value.substring(0, pos)
  const lines = text.split('\n')
  cursorLine.value = lines.length
  cursorCol.value = (lines[lines.length - 1] ?? '').length + 1
}

let scrollRafId = 0

function handleEditorScroll() {
  if (scrollRafId) return
  scrollRafId = requestAnimationFrame(() => {
    scrollRafId = 0
    if (!textareaRef.value || !showPreview.value || !previewRef.value) return
    const ratio = textareaRef.value.scrollTop / (textareaRef.value.scrollHeight - textareaRef.value.clientHeight || 1)
    previewRef.value.scrollTop = ratio * (previewRef.value.scrollHeight - previewRef.value.clientHeight)
  })
}

function pushUndoNow() {
  if (!textareaRef.value || isRestoring.value) return
  undoManager.push({
    content: content.value,
    selectionStart: textareaRef.value.selectionStart,
    selectionEnd: textareaRef.value.selectionEnd,
  })
}

function performUndo() {
  if (!textareaRef.value || !undoManager.canUndo()) return
  showAutocomplete.value = false
  const entry = undoManager.undo({
    content: content.value,
    selectionStart: textareaRef.value.selectionStart,
    selectionEnd: textareaRef.value.selectionEnd,
  })
  if (entry) {
    isRestoring.value = true
    content.value = entry.content
    emit('update:modelValue', entry.content)
    nextTick(() => {
      if (textareaRef.value) {
        textareaRef.value.setSelectionRange(entry.selectionStart, entry.selectionEnd)
        textareaRef.value.focus()
      }
      isRestoring.value = false
      updateCursorPosition()
    })
  }
}

function performRedo() {
  if (!textareaRef.value || !undoManager.canRedo()) return
  showAutocomplete.value = false
  const entry = undoManager.redo({
    content: content.value,
    selectionStart: textareaRef.value.selectionStart,
    selectionEnd: textareaRef.value.selectionEnd,
  })
  if (entry) {
    isRestoring.value = true
    content.value = entry.content
    emit('update:modelValue', entry.content)
    nextTick(() => {
      if (textareaRef.value) {
        textareaRef.value.setSelectionRange(entry.selectionStart, entry.selectionEnd)
        textareaRef.value.focus()
      }
      isRestoring.value = false
      updateCursorPosition()
    })
  }
}

// Intercept native undo/redo at the beforeinput level — this catches
// undo/redo from ALL sources: keyboard shortcuts, Edit menu, touch gestures.
// More reliable than keydown.preventDefault() alone, especially on macOS.
// The undoRedoHandledByKeydown guard prevents double-firing when keydown
// already handled the same Cmd+Z / Cmd+Shift+Z action.
function handleBeforeInput(e: InputEvent) {
  if (e.inputType === 'historyUndo') {
    e.preventDefault()
    if (!undoRedoHandledByKeydown) {
      performUndo()
    }
    undoRedoHandledByKeydown = false
    return
  }
  if (e.inputType === 'historyRedo') {
    e.preventDefault()
    if (!undoRedoHandledByKeydown) {
      performRedo()
    }
    undoRedoHandledByKeydown = false
    return
  }
}

function handleKeyDown(e: KeyboardEvent) {
  if (showAutocomplete.value) {
    if (e.key === 'ArrowDown') {
      e.preventDefault()
      selectedIndex.value = Math.min(selectedIndex.value + 1, autocompleteItems.value.length - 1)
      return
    }
    if (e.key === 'ArrowUp') {
      e.preventDefault()
      selectedIndex.value = Math.max(selectedIndex.value - 1, 0)
      return
    }
    if (e.key === 'Enter' || e.key === 'Tab') {
      e.preventDefault()
      applyAutocomplete()
      return
    }
    if (e.key === 'Escape') {
      showAutocomplete.value = false
      return
    }
  }

  if (e.ctrlKey || e.metaKey) {
    // Undo: Ctrl/Cmd+Z  (use e.code for keyboard-layout independence)
    if (!e.shiftKey && (e.key.toLowerCase() === 'z' || e.code === 'KeyZ')) {
      e.preventDefault()
      undoRedoHandledByKeydown = true
      performUndo()
      return
    }
    // Redo: Ctrl/Cmd+Shift+Z (macOS standard) or Ctrl+Y (Windows standard)
    if (e.key.toLowerCase() === 'y' || e.code === 'KeyY') {
      e.preventDefault()
      undoRedoHandledByKeydown = true
      performRedo()
      return
    }
    if (e.shiftKey && (e.key.toLowerCase() === 'z' || e.code === 'KeyZ')) {
      e.preventDefault()
      undoRedoHandledByKeydown = true
      performRedo()
      return
    }
    if (e.key.toLowerCase() === 's') {
      e.preventDefault()
      emit('save')
      return
    }

    switch (e.key.toLowerCase()) {
      case 'b':
        e.preventDefault()
        insertMarkdown('**', '**', '粗体文本')
        break
      case 'i':
        if (e.shiftKey) {
          e.preventDefault()
          handleImageUpload()
        } else {
          e.preventDefault()
          insertMarkdown('*', '*', '斜体文本')
        }
        break
      case 'u':
        e.preventDefault()
        insertMarkdown('<u>', '</u>', '下划线文本')
        break
      case 'k':
        if (e.shiftKey) {
          e.preventDefault()
          insertCodeBlock()
        } else {
          e.preventDefault()
          insertLink()
        }
        break
      case '`':
        e.preventDefault()
        insertMarkdown('`', '`', '代码')
        break
      case 'h':
        if (e.shiftKey) {
          e.preventDefault()
          insertHeading()
        }
        break
      case 'l':
        if (e.shiftKey) {
          e.preventDefault()
          insertList('-')
        }
        break
      case 'o':
        if (e.shiftKey) {
          e.preventDefault()
          insertList('1.')
        }
        break
      case 'q':
        if (e.shiftKey) {
          e.preventDefault()
          insertQuote()
        }
        break
      case '.':
        if (e.shiftKey) {
          e.preventDefault()
          showCalloutDialog.value = true
        }
        break
    }
  }

  if (e.key === 'Tab' && !showAutocomplete.value) {
    e.preventDefault()
    if (e.shiftKey) {
      removeIndent()
    } else {
      insertText('    ')
    }
  }
}

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
  pushUndoNow()

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

function removeIndent() {
  if (!textareaRef.value) return
  pushUndoNow()

  const start = textareaRef.value.selectionStart
  const value = textareaRef.value.value

  const lastNewLine = value.lastIndexOf('\n', start - 1)
  const lineStart = lastNewLine + 1
  const lineContent = value.substring(lineStart, start)

  if (lineContent.startsWith('    ')) {
    const newText = value.substring(0, lineStart) + lineContent.substring(4) + value.substring(start)
    content.value = newText
    nextTick(() => {
      if (!textareaRef.value) return
      textareaRef.value.setSelectionRange(start - 4, start - 4)
      textareaRef.value.focus()
    })
  } else if (lineContent.startsWith('\t')) {
    const newText = value.substring(0, lineStart) + lineContent.substring(1) + value.substring(start)
    content.value = newText
    nextTick(() => {
      if (!textareaRef.value) return
      textareaRef.value.setSelectionRange(start - 1, start - 1)
      textareaRef.value.focus()
    })
  }
}

function handleInput(e: Event) {
  const target = e.target as HTMLTextAreaElement
  const value = target.value
  const cursorPos = target.selectionStart

  const lastNewLine = value.lastIndexOf('\n', cursorPos - 1)
  const currentLine = value.substring(lastNewLine + 1, cursorPos)

  // Callout autocomplete: trigger when typing "> [" or "> [!PARTIAL"
  const calloutMatch = currentLine.match(/^> \[!(\w*)$/i)
  if (calloutMatch) {
    const partial = (calloutMatch[1] ?? '').toLowerCase()
    const items = allCalloutTypes.value
      .filter(ct => ct.type.toLowerCase().startsWith(partial))
      .slice(0, 8)
      .map(ct => `> [!${ct.type}] ${ct.icon} ${ct.label}`)
    if (items.length > 0) {
      showAutocompletePanel(items)
    } else {
      showAutocomplete.value = false
    }
  } else if (currentLine.match(/^#{1,6}$/)) {
    showAutocompletePanel(['# 一级标题', '## 二级标题', '### 三级标题', '#### 四级标题', '##### 五级标题', '###### 六级标题'])
  } else if (currentLine.match(/^[-*+]$/)) {
    showAutocompletePanel(['- 列表项', '* 列表项', '+ 列表项'])
  } else if (currentLine.match(/^\d+\.$/)) {
    showAutocompletePanel(['1. 有序列表'])
  } else if (currentLine.match(/^>$/)) {
    showAutocompletePanel(['> 引用内容', ...allCalloutTypes.value.slice(0, 4).map(ct => `> [!${ct.type}] ${ct.icon} ${ct.label}`)])
  } else if (currentLine.match(/^`{1,2}$/)) {
    showAutocompletePanel(['`行内代码`', '```lang 代码块 ```'])
  } else if (currentLine.match(/^\[.*\]$/)) {
    showAutocompletePanel(['[链接文字](URL)', '![图片描述](图片URL)'])
  } else {
    showAutocomplete.value = false
  }
}

function showAutocompletePanel(items: string[]) {
  if (!textareaRef.value) return

  autocompleteItems.value = items
  selectedIndex.value = 0
  showAutocomplete.value = true

  const textarea = textareaRef.value
  const rect = textarea.getBoundingClientRect()
  const style = getComputedStyle(textarea)
  const lineHeight = parseFloat(style.lineHeight) || 20
  const paddingTop = parseFloat(style.paddingTop) || 0
  const paddingLeft = parseFloat(style.paddingLeft) || 0

  const textBeforeCursor = textarea.value.substring(0, textarea.selectionStart)
  const lines = textBeforeCursor.split('\n')
  const currentLineIndex = lines.length - 1
  const currentCol = (lines[lines.length - 1] ?? '').length

  const charWidth = parseFloat(style.fontSize) * 0.6

  const top = rect.top + paddingTop + (currentLineIndex + 1) * lineHeight - textarea.scrollTop
  const left = rect.left + paddingLeft + currentCol * charWidth - textarea.scrollLeft

  autocompletePosition.value = {
    top: Math.max(top, rect.top + paddingTop),
    left: Math.min(Math.max(left, rect.left + paddingLeft), rect.right - 220),
  }
}

function applyAutocomplete() {
  if (!textareaRef.value || selectedIndex.value >= autocompleteItems.value.length) return
  pushUndoNow()

  const selected = autocompleteItems.value[selectedIndex.value]
  const cursorPos = textareaRef.value.selectionStart
  const value = textareaRef.value.value

  const lastNewLine = value.lastIndexOf('\n', cursorPos - 1)
  const beforeLine = value.substring(0, lastNewLine + 1)
  const afterCursor = value.substring(cursorPos)

  const newText = beforeLine + selected + '\n' + afterCursor
  content.value = newText

  const newPos = beforeLine.length + (selected?.length || 0) + 1
  nextTick(() => {
    if (!textareaRef.value) return
    textareaRef.value.setSelectionRange(newPos, newPos)
    textareaRef.value.focus()
  })

  showAutocomplete.value = false
}

function insertMarkdown(before: string, after: string, placeholder: string) {
  if (!textareaRef.value) return
  pushUndoNow()

  const start = textareaRef.value.selectionStart
  const end = textareaRef.value.selectionEnd
  const value = textareaRef.value.value
  const selectedText = value.substring(start, end) || placeholder

  const newText = value.substring(0, start) + before + selectedText + after + value.substring(end)
  content.value = newText

  nextTick(() => {
    if (!textareaRef.value) return
    const newCursorPos = start + before.length + selectedText.length
    textareaRef.value.setSelectionRange(start + before.length, newCursorPos)
    textareaRef.value.focus()
  })
}

function insertText(text: string) {
  if (!textareaRef.value) return
  pushUndoNow()

  const start = textareaRef.value.selectionStart
  const end = textareaRef.value.selectionEnd
  const value = textareaRef.value.value

  const newText = value.substring(0, start) + text + value.substring(end)
  content.value = newText

  nextTick(() => {
    if (!textareaRef.value) return
    textareaRef.value.setSelectionRange(start + text.length, start + text.length)
    textareaRef.value.focus()
  })
}

function insertLink() {
  if (!textareaRef.value) return
  pushUndoNow()

  const start = textareaRef.value.selectionStart
  const end = textareaRef.value.selectionEnd
  const value = textareaRef.value.value
  const selectedText = value.substring(start, end) || '链接文字'

  const linkMarkdown = `[${selectedText}](URL)`
  const newText = value.substring(0, start) + linkMarkdown + value.substring(end)
  content.value = newText

  nextTick(() => {
    if (!textareaRef.value) return
    const urlStartPos = start + selectedText.length + 3
    textareaRef.value.setSelectionRange(urlStartPos, urlStartPos + 3)
    textareaRef.value.focus()
  })
}

function insertImageMarkdown(url: string, alt?: string) {
  if (!textareaRef.value) return
  pushUndoNow()

  const start = textareaRef.value.selectionStart
  const value = textareaRef.value.value
  const imageMarkdown = `![${alt || '图片'}](${url})`

  const newText = value.substring(0, start) + imageMarkdown + value.substring(start)
  content.value = newText

  nextTick(() => {
    if (!textareaRef.value) return
    const newPos = start + imageMarkdown.length
    textareaRef.value.setSelectionRange(newPos, newPos)
    textareaRef.value.focus()
  })
}

function insertCodeBlock() {
  if (!textareaRef.value) return
  pushUndoNow()

  const start = textareaRef.value.selectionStart
  const end = textareaRef.value.selectionEnd
  const value = textareaRef.value.value
  const selectedText = value.substring(start, end)

  const langPlaceholder = 'lang'
  const codeContent = selectedText || ''
  const beforeCursor = value.substring(0, start)
  const afterCursor = value.substring(end)

  const needsLeadingNewline = beforeCursor.length > 0 && !beforeCursor.endsWith('\n')
  const leadingNewline = needsLeadingNewline ? '\n' : ''

  const codeBlock = `${leadingNewline}\`\`\`${langPlaceholder}\n${codeContent}\n\`\`\`\n`
  content.value = beforeCursor + codeBlock + afterCursor

  nextTick(() => {
    if (!textareaRef.value) return
    const langOffset = start + leadingNewline.length + 3
    textareaRef.value.setSelectionRange(langOffset, langOffset + langPlaceholder.length)
    textareaRef.value.focus()
  })
}

function insertHeading() {
  if (!textareaRef.value) return
  pushUndoNow()

  const start = textareaRef.value.selectionStart
  const value = textareaRef.value.value

  const lastNewLine = value.lastIndexOf('\n', start - 1)
  const lineStart = lastNewLine + 1
  const currentLineText = value.substring(lineStart, start)

  const needsNewline = currentLineText.trim().length > 0
  const prefix = needsNewline ? '\n# ' : '# '
  const insertPos = needsNewline ? start : lineStart

  const newText = value.substring(0, insertPos) + prefix + value.substring(insertPos)
  content.value = newText

  nextTick(() => {
    if (!textareaRef.value) return
    textareaRef.value.setSelectionRange(insertPos + prefix.length, insertPos + prefix.length)
    textareaRef.value.focus()
  })
}

function insertList(prefix: string) {
  if (!textareaRef.value) return
  pushUndoNow()

  const start = textareaRef.value.selectionStart
  const value = textareaRef.value.value

  const lastNewLine = value.lastIndexOf('\n', start - 1)
  const lineStart = lastNewLine + 1
  const currentLineText = value.substring(lineStart, start)

  const needsNewline = currentLineText.trim().length > 0
  const listMarkdown = prefix + ' '
  const insertPrefix = needsNewline ? '\n' + listMarkdown : listMarkdown
  const insertPos = needsNewline ? start : lineStart

  const newText = value.substring(0, insertPos) + insertPrefix + value.substring(insertPos)
  content.value = newText

  nextTick(() => {
    if (!textareaRef.value) return
    textareaRef.value.setSelectionRange(insertPos + insertPrefix.length, insertPos + insertPrefix.length)
    textareaRef.value.focus()
  })
}

function insertQuote() {
  if (!textareaRef.value) return
  pushUndoNow()

  const start = textareaRef.value.selectionStart
  const value = textareaRef.value.value

  const lastNewLine = value.lastIndexOf('\n', start - 1)
  const lineStart = lastNewLine + 1
  const currentLineText = value.substring(lineStart, start)

  const needsNewline = currentLineText.trim().length > 0
  const quoteMarkdown = '> '
  const insertPrefix = needsNewline ? '\n' + quoteMarkdown : quoteMarkdown
  const insertPos = needsNewline ? start : lineStart

  const newText = value.substring(0, insertPos) + insertPrefix + value.substring(insertPos)
  content.value = newText

  nextTick(() => {
    if (!textareaRef.value) return
    textareaRef.value.setSelectionRange(insertPos + insertPrefix.length, insertPos + insertPrefix.length)
    textareaRef.value.focus()
  })
}

function insertMath() {
  if (!textareaRef.value) return
  pushUndoNow()

  const start = textareaRef.value.selectionStart
  const end = textareaRef.value.selectionEnd
  const value = textareaRef.value.value
  const selectedText = value.substring(start, end)

  if (selectedText.trim()) {
    // Wrap selected text in $...$ (inline math)
    const newText = value.substring(0, start) + '$' + selectedText + '$' + value.substring(end)
    content.value = newText
    nextTick(() => {
      if (!textareaRef.value) return
      textareaRef.value.setSelectionRange(start + 1, start + 1 + selectedText.length)
      textareaRef.value.focus()
    })
  } else {
    // Insert empty inline math template $ $
    const template = '$ $'
    const newText = value.substring(0, start) + template + value.substring(end)
    content.value = newText
    nextTick(() => {
      if (!textareaRef.value) return
      const cursorPos = start + 2 // Between the two $
      textareaRef.value.setSelectionRange(cursorPos, cursorPos)
      textareaRef.value.focus()
    })
  }
}

function insertCallout(type: string) {
  if (!textareaRef.value) return
  pushUndoNow()

  const start = textareaRef.value.selectionStart
  const end = textareaRef.value.selectionEnd
  const value = textareaRef.value.value
  const selectedText = value.substring(start, end)
  const beforeCursor = value.substring(0, start)
  const afterCursor = value.substring(end)

  const needsLeadingNewline = beforeCursor.length > 0 && !beforeCursor.endsWith('\n')
  const leadingNewline = needsLeadingNewline ? '\n' : ''

  if (selectedText.trim()) {
    // Wrap selected lines as callout content — each non-empty line gets "> " prefix
    const lines = selectedText.split('\n')
    const wrappedLines = lines.map(line => {
      // Preserve empty lines (but they still need the ">" marker for callout continuation)
      if (line.length === 0) return '>'
      // If already starts with "> ", just keep it as-is
      if (line.startsWith('> ')) return line
      if (line === '>') return line
      return '> ' + line
    }).join('\n')

    const calloutText = `${leadingNewline}> [!${type}]\n${wrappedLines}\n`
    content.value = beforeCursor + calloutText + afterCursor

    nextTick(() => {
      if (!textareaRef.value) return
      // Position cursor after "[!TYPE]" — ready to type a title
      const cursorPos = start + leadingNewline.length + 4 + type.length + 1
      textareaRef.value.setSelectionRange(cursorPos, cursorPos)
      textareaRef.value.focus()
    })
  } else {
    // Insert empty callout template
    const titlePlaceholder = '标题'
    const template = `${leadingNewline}> [!${type}] ${titlePlaceholder}\n> 内容\n`
    content.value = beforeCursor + template + afterCursor

    nextTick(() => {
      if (!textareaRef.value) return
      // Select the title placeholder so user can immediately type a title
      const titleStart = start + leadingNewline.length + 4 + type.length + 2
      textareaRef.value.setSelectionRange(titleStart, titleStart + titlePlaceholder.length)
      textareaRef.value.focus()
    })
  }

  showCalloutDialog.value = false
  calloutSearchQuery.value = ''
}

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
        if (file) {
          doUploadFile(file)
        }
      }
    }
  }
  input.click()
}

async function doUploadFile(file: File) {
  // 文件大小预检
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
</script>

<template>
  <div class="markdown-editor h-full flex flex-col">
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
        @click="showPreview = !showPreview"
        class="toolbar-btn text-xs px-2 gap-1"
        :class="{ 'bg-bg-code': showPreview }"
      >
        预览
      </button>
      <button
        @click="showShortcuts = true"
        class="toolbar-btn"
        title="快捷键帮助"
      >
        <HelpCircle :size="15" />
      </button>
    </div>

    <div class="editor-content flex-1 flex min-h-0">
      <div class="editor-pane flex-1" :class="{ 'border-r border-border': showPreview }">
        <textarea
          ref="textareaRef"
          v-model="content"
          :placeholder="placeholder"
          class="editor-textarea"
          spellcheck="false"
          autocomplete="off"
          autocorrect="off"
          autocapitalize="off"
        />
      </div>

      <div v-if="showPreview" class="preview-pane flex-1 overflow-auto bg-bg-secondary">
        <div v-if="rendering" class="p-6 animate-pulse space-y-4">
          <div class="h-8 w-3/4 bg-bg-code rounded" />
          <div class="h-4 w-full bg-bg-code rounded" />
          <div class="h-4 w-5/6 bg-bg-code rounded" />
        </div>
        <div
          v-else
          ref="previewRef"
          class="prose prose-sm max-w-none p-6"
          v-html="renderedHtml"
        />
      </div>
    </div>

    <div class="editor-status-bar flex items-center gap-4 px-3 py-1 text-xs text-text-muted border-t border-border bg-bg-secondary/50 select-none flex-shrink-0">
      <span>{{ wordCount }} 字</span>
      <span>{{ charCount }} 字符</span>
      <span>{{ lineCount }} 行</span>
      <span class="ml-auto">行 {{ cursorLine }}, 列 {{ cursorCol }}</span>
    </div>

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

.editor-pane {
  position: relative;
  background: var(--bg-secondary);
  overflow: hidden;
}

.editor-textarea {
  display: block;
  width: 100%;
  height: 100%;
  padding: 0.75rem;
  border: none;
  outline: none;
  resize: none;
  background: transparent;
  color: var(--text-primary);
  font-family: ui-monospace, SFMono-Regular, SF Mono, Menlo, Consolas, 'Liberation Mono', monospace;
  font-size: 0.875rem;
  line-height: 1.7;
  tab-size: 4;
  -moz-tab-size: 4;
  box-sizing: border-box;
}

.editor-textarea::placeholder {
  color: var(--text-muted);
  opacity: 0.6;
}

.editor-textarea::selection {
  background-color: rgba(59, 130, 246, 0.25);
}

.editor-textarea::-moz-selection {
  background-color: rgba(59, 130, 246, 0.25);
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

.prose {
  color: var(--text-secondary);
}

@media (max-width: 768px) {
  .editor-status-bar {
    gap: 0.5rem;
    font-size: 0.65rem;
    padding: 0.125rem 0.5rem;
  }
}
</style>
