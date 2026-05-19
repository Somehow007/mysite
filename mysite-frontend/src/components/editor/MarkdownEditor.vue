<script setup lang="ts">
import { ref, watch, onMounted, onUnmounted, nextTick, computed } from 'vue'
import { useMarkdown } from '@/composables/useMarkdown'
import { uploadImage, uploadImageByUrl } from '@/api/image'
import { useToast } from '@/composables/useToast'
import { Image as ImageIcon, HelpCircle, X, Bold, Italic, Link, Code, List, Quote, Heading, LinkIcon, Loader2 } from 'lucide-vue-next'

const props = defineProps<{
  modelValue: string
  placeholder?: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
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

const uploadingFiles = ref<Map<string, { name: string; progress: number }>>(new Map())

const { renderedHtml, render, applyHighlighting, rendering } = useMarkdown()
const toast = useToast()

const isMac = computed(() => {
  if (typeof navigator === 'undefined') return false
  return /Mac|iPod|iPhone|iPad/.test(navigator.platform)
})

const modKey = computed(() => isMac.value ? 'Cmd' : 'Ctrl')

const isUploading = computed(() => uploadingFiles.value.size > 0 || urlUploading.value)

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
  { key: `${modKey.value}+Shift+I`, description: '插入图片', syntax: '![描述](图片URL)' },
  { key: 'Tab', description: '插入缩进', syntax: '    ' },
  { key: 'Shift+Tab', description: '删除缩进', syntax: '删除行首缩进' },
])

watch(
  () => props.modelValue,
  (newVal) => {
    content.value = newVal
    updatePreview()
  },
)

watch(content, (newVal) => {
  emit('update:modelValue', newVal)
  updatePreview()
})

onMounted(() => {
  if (textareaRef.value) {
    textareaRef.value.addEventListener('keydown', handleKeyDown)
    textareaRef.value.addEventListener('input', handleInput)
    textareaRef.value.addEventListener('paste', handlePaste)
    textareaRef.value.addEventListener('drop', handleDrop)
    textareaRef.value.addEventListener('dragover', handleDragOver)
  }
  updatePreview()
})

onUnmounted(() => {
  if (textareaRef.value) {
    textareaRef.value.removeEventListener('keydown', handleKeyDown)
    textareaRef.value.removeEventListener('input', handleInput)
    textareaRef.value.removeEventListener('paste', handlePaste)
    textareaRef.value.removeEventListener('drop', handleDrop)
    textareaRef.value.removeEventListener('dragover', handleDragOver)
  }
})

async function updatePreview() {
  if (showPreview.value && content.value) {
    await render(content.value)
    await nextTick()
    if (previewRef.value) {
      await applyHighlighting(previewRef.value)
    }
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

  if (currentLine.match(/^#{1,6}$/)) {
    showAutocompletePanel(['# 一级标题', '## 二级标题', '### 三级标题', '#### 四级标题', '##### 五级标题', '###### 六级标题'])
  } else if (currentLine.match(/^[-*+]$/)) {
    showAutocompletePanel(['- 列表项', '* 列表项', '+ 列表项'])
  } else if (currentLine.match(/^\d+\.$/)) {
    showAutocompletePanel(['1. 有序列表'])
  } else if (currentLine.match(/^>$/)) {
    showAutocompletePanel(['> 引用内容'])
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

  const rect = textareaRef.value.getBoundingClientRect()
  const lineHeight = parseInt(getComputedStyle(textareaRef.value).lineHeight)
  const lines = textareaRef.value.value.substring(0, textareaRef.value.selectionStart).split('\n')
  const currentLineIndex = lines.length - 1

  autocompletePosition.value = {
    top: rect.top + (currentLineIndex + 1) * lineHeight,
    left: rect.left + 20,
  }
}

function applyAutocomplete() {
  if (!textareaRef.value || selectedIndex.value >= autocompleteItems.value.length) return

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
  const fileId = `${Date.now()}_${Math.random().toString(36).substring(2, 8)}`
  uploadingFiles.value.set(fileId, { name: file.name, progress: 0 })

  try {
    const result = await uploadImage(file)
    insertImageMarkdown(result.url, result.originalName)
  } catch (e) {
    console.error('图片上传失败:', e)
    toast.error('图片上传失败：' + (e instanceof Error ? e.message : '未知错误'))
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

function getHighlightHtml(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/```(\w*)\n([\s\S]*?)```/g, '<span class="md-code-block">```$1\n$2```</span>')
    .replace(/^(#{1,6})\s+(.*)$/gm, '<span class="md-heading">$1 $2</span>')
    .replace(/\*\*([^*]+)\*\*/g, '<span class="md-bold">**$1**</span>')
    .replace(/\*([^*]+)\*/g, '<span class="md-italic">*$1*</span>')
    .replace(/`([^`]+)`/g, '<span class="md-code">`$1`</span>')
    .replace(/\[([^\]]+)\]\(([^)]+)\)/g, '<span class="md-link">[$1]($2)</span>')
    .replace(/!\[([^\]]*)\]\(([^)]+)\)/g, '<span class="md-image">![$1]($2)</span>')
    .replace(/^[-*+]\s+/gm, '<span class="md-list">$&</span>')
    .replace(/^\d+\.\s+/gm, '<span class="md-list">$&</span>')
    .replace(/^>\s+/gm, '<span class="md-quote">$&</span>')
    .replace(/\n/g, '<br>')
}
</script>

<template>
  <div class="markdown-editor h-full flex flex-col">
    <div class="editor-toolbar flex items-center gap-2 p-2 border-b border-[var(--color-border)] dark:border-[var(--color-dark-border)] bg-[var(--color-bg-card)] dark:bg-[var(--color-dark-bg-card)]">
      <button
        @click="insertMarkdown('**', '**', '粗体文本')"
        class="p-1.5 rounded hover:bg-[var(--color-bg-code)] dark:hover:bg-[var(--color-dark-bg-code)] transition-colors"
        :title="`加粗 (${modKey}+B)`"
      >
        <Bold :size="16" />
      </button>
      <button
        @click="insertMarkdown('*', '*', '斜体文本')"
        class="p-1.5 rounded hover:bg-[var(--color-bg-code)] dark:hover:bg-[var(--color-dark-bg-code)] transition-colors"
        :title="`斜体 (${modKey}+I)`"
      >
        <Italic :size="16" />
      </button>
      <button
        @click="insertLink"
        class="p-1.5 rounded hover:bg-[var(--color-bg-code)] dark:hover:bg-[var(--color-dark-bg-code)] transition-colors"
        :title="`插入链接 (${modKey}+K)`"
      >
        <Link :size="16" />
      </button>
      <button
        @click="insertCodeBlock"
        class="p-1.5 rounded hover:bg-[var(--color-bg-code)] dark:hover:bg-[var(--color-dark-bg-code)] transition-colors"
        :title="`代码块 (${modKey}+Shift+K)`"
      >
        <Code :size="16" />
      </button>
      <button
        @click="insertHeading"
        class="p-1.5 rounded hover:bg-[var(--color-bg-code)] dark:hover:bg-[var(--color-dark-bg-code)] transition-colors"
        :title="`标题 (${modKey}+Shift+H)`"
      >
        <Heading :size="16" />
      </button>
      <button
        @click="insertList('-')"
        class="p-1.5 rounded hover:bg-[var(--color-bg-code)] dark:hover:bg-[var(--color-dark-bg-code)] transition-colors"
        :title="`无序列表 (${modKey}+Shift+L)`"
      >
        <List :size="16" />
      </button>
      <button
        @click="insertQuote"
        class="p-1.5 rounded hover:bg-[var(--color-bg-code)] dark:hover:bg-[var(--color-dark-bg-code)] transition-colors"
        :title="`引用 (${modKey}+Shift+Q)`"
      >
        <Quote :size="16" />
      </button>
      <div class="w-px h-5 bg-[var(--color-border)] dark:bg-[var(--color-dark-border)] mx-1" />
      <button
        @click="handleImageUpload"
        class="p-1.5 rounded hover:bg-[var(--color-bg-code)] dark:hover:bg-[var(--color-dark-bg-code)] transition-colors"
        :title="`上传图片 (${modKey}+Shift+I)`"
      >
        <ImageIcon :size="16" />
      </button>
      <button
        @click="openUrlDialog"
        class="p-1.5 rounded hover:bg-[var(--color-bg-code)] dark:hover:bg-[var(--color-dark-bg-code)] transition-colors"
        title="通过URL插入图片"
      >
        <LinkIcon :size="16" />
      </button>
      <div class="flex-1" />
      <div v-if="isUploading" class="flex items-center gap-1.5 text-xs text-[var(--color-accent)] dark:text-[var(--color-dark-accent)]">
        <Loader2 :size="12" class="animate-spin" />
        <span>上传中...</span>
      </div>
      <button
        @click="showPreview = !showPreview"
        class="px-3 py-1.5 text-sm rounded border border-[var(--color-border)] dark:border-[var(--color-dark-border)] hover:bg-[var(--color-bg-code)] dark:hover:bg-[var(--color-dark-bg-code)] transition-colors"
        :class="{ 'bg-[var(--color-bg-code)] dark:bg-[var(--color-dark-bg-code)]': showPreview }"
      >
        {{ showPreview ? '隐藏预览' : '显示预览' }}
      </button>
      <button
        @click="showShortcuts = true"
        class="p-1.5 rounded hover:bg-[var(--color-bg-code)] dark:hover:bg-[var(--color-dark-bg-code)] transition-colors"
        title="快捷键帮助"
      >
        <HelpCircle :size="16" />
      </button>
    </div>

    <div class="editor-content flex-1 flex min-h-0">
      <div class="editor-pane flex-1 overflow-auto" :class="{ 'border-r border-[var(--color-border)] dark:border-[var(--color-dark-border)]': showPreview }">
        <div class="editor-wrapper">
          <div
            class="editor-highlight p-3 font-mono text-sm leading-relaxed whitespace-pre-wrap pointer-events-none"
            v-html="getHighlightHtml(content)"
          />
          <textarea
            ref="textareaRef"
            v-model="content"
            :placeholder="placeholder"
            class="editor-textarea p-3 font-mono text-sm leading-relaxed resize-none outline-none"
            spellcheck="false"
          />
        </div>
      </div>

      <div v-if="showPreview" class="preview-pane flex-1 overflow-auto bg-[var(--color-bg-card)] dark:bg-[var(--color-dark-bg-card)]">
        <div v-if="rendering" class="p-6 animate-pulse space-y-4">
          <div class="h-8 w-3/4 bg-[var(--color-bg-code)] dark:bg-[var(--color-dark-bg-code)] rounded" />
          <div class="h-4 w-full bg-[var(--color-bg-code)] dark:bg-[var(--color-dark-bg-code)] rounded" />
          <div class="h-4 w-5/6 bg-[var(--color-bg-code)] dark:bg-[var(--color-dark-bg-code)] rounded" />
        </div>
        <div
          v-else
          ref="previewRef"
          class="prose prose-sm max-w-none p-6"
          v-html="renderedHtml"
        />
      </div>
    </div>

    <div
      v-if="showAutocomplete && autocompleteItems.length > 0"
      class="autocomplete-panel fixed bg-[var(--color-bg-card)] dark:bg-[var(--color-dark-bg-card)] border border-[var(--color-border)] dark:border-[var(--color-dark-border)] rounded-lg shadow-lg overflow-hidden z-50"
      :style="{ top: `${autocompletePosition.top}px`, left: `${autocompletePosition.left}px` }"
    >
      <div
        v-for="(item, index) in autocompleteItems"
        :key="index"
        class="px-3 py-2 text-sm cursor-pointer hover:bg-[var(--color-bg-code)] dark:hover:bg-[var(--color-dark-bg-code)]"
        :class="{ 'bg-[var(--color-bg-code)] dark:bg-[var(--color-dark-bg-code)]': index === selectedIndex }"
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
        class="bg-[var(--color-bg-card)] dark:bg-[var(--color-dark-bg-card)] rounded-lg shadow-xl w-full max-w-md mx-4"
        @click.stop
      >
        <div class="flex items-center justify-between p-4 border-b border-[var(--color-border)] dark:border-[var(--color-dark-border)]">
          <h3 class="text-lg font-semibold">通过URL插入图片</h3>
          <button
            @click="showUrlDialog = false"
            class="p-1 rounded hover:bg-[var(--color-bg-code)] dark:hover:bg-[var(--color-dark-bg-code)] transition-colors"
          >
            <X :size="18" />
          </button>
        </div>
        <div class="p-4 space-y-4">
          <div>
            <label class="block text-sm font-medium text-[var(--color-text-heading)] dark:text-[var(--color-dark-text-heading)] mb-1.5">
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
          <p class="text-xs text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)]">
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

    <div
      v-if="showShortcuts"
      class="fixed inset-0 bg-black/50 flex items-center justify-center z-50"
      @click="showShortcuts = false"
    >
      <div
        class="bg-[var(--color-bg-card)] dark:bg-[var(--color-dark-bg-card)] rounded-lg shadow-xl max-w-2xl w-full mx-4 max-h-[80vh] overflow-auto"
        @click.stop
      >
        <div class="flex items-center justify-between p-4 border-b border-[var(--color-border)] dark:border-[var(--color-dark-border)]">
          <h3 class="text-lg font-semibold">Markdown 快捷键</h3>
          <button
            @click="showShortcuts = false"
            class="p-1 rounded hover:bg-[var(--color-bg-code)] dark:hover:bg-[var(--color-dark-bg-code)] transition-colors"
          >
            <X :size="18" />
          </button>
        </div>
        <div class="p-4">
          <div class="grid gap-2">
            <div
              v-for="shortcut in shortcuts"
              :key="shortcut.key"
              class="flex items-center justify-between p-2 rounded bg-[var(--color-bg-code)] dark:bg-[var(--color-dark-bg-code)]"
            >
              <div class="flex items-center gap-3">
                <kbd class="px-2 py-1 text-xs font-mono bg-[var(--color-bg-card)] dark:bg-[var(--color-dark-bg-card)] border border-[var(--color-border)] dark:border-[var(--color-dark-border)] rounded">
                  {{ shortcut.key }}
                </kbd>
                <span class="text-sm">{{ shortcut.description }}</span>
              </div>
              <code class="text-xs text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)]">
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
  background: var(--color-bg-card, #fff);
  overflow: auto;
}

.dark .editor-pane {
  background: var(--color-dark-bg-card, #1a1a1a);
}

.editor-wrapper {
  display: grid;
  grid-template-areas: 'editor';
  min-height: 100%;
}

.editor-highlight {
  grid-area: editor;
  overflow: visible;
}

.editor-textarea {
  grid-area: editor;
  width: 100%;
  min-height: 100%;
  box-sizing: border-box;
  background: transparent;
  color: transparent;
  caret-color: var(--color-text-body);
}

.dark .editor-textarea {
  caret-color: var(--color-dark-text-body);
}

:deep(.md-heading) {
  color: #0550ae;
  font-weight: 600;
}

.dark :deep(.md-heading) {
  color: #79c0ff;
}

:deep(.md-bold) {
  color: #953800;
  font-weight: 700;
}

.dark :deep(.md-bold) {
  color: #ffa657;
}

:deep(.md-italic) {
  color: #6639ba;
  font-style: italic;
}

.dark :deep(.md-italic) {
  color: #d2a8ff;
}

:deep(.md-code) {
  background-color: rgba(175, 184, 193, 0.2);
  color: #24292f;
  padding: 0.2em 0.4em;
  border-radius: 3px;
  font-family: ui-monospace, SFMono-Regular, SF Mono, Menlo, Consolas, monospace;
}

.dark :deep(.md-code) {
  background-color: rgba(110, 118, 129, 0.4);
  color: #c9d1d9;
}

:deep(.md-code-block) {
  color: #0550ae;
  background-color: rgba(175, 184, 193, 0.2);
  padding: 0.2em;
  border-radius: 3px;
  display: block;
}

.dark :deep(.md-code-block) {
  color: #79c0ff;
  background-color: rgba(110, 118, 129, 0.4);
}

:deep(.md-link) {
  color: #0969da;
  text-decoration: underline;
}

.dark :deep(.md-link) {
  color: #58a6ff;
}

:deep(.md-image) {
  color: #1a7f37;
  font-weight: 500;
}

.dark :deep(.md-image) {
  color: #7ee787;
}

:deep(.md-list) {
  color: #8250df;
  font-weight: 500;
}

.dark :deep(.md-list) {
  color: #d2a8ff;
}

:deep(.md-quote) {
  color: #57606a;
  font-style: italic;
}

.dark :deep(.md-quote) {
  color: #8b949e;
}

.autocomplete-panel {
  min-width: 200px;
  max-width: 400px;
}

.prose {
  color: var(--color-text-body, #24292f);
}

.dark .prose {
  color: var(--color-dark-text-body, #c9d1d9);
}
</style>
