<script setup lang="ts">
import { ref, computed, nextTick } from 'vue'
import ArticleContent from '@/components/article/ArticleContent.vue'
import { Pencil, Eye, Columns2, Image as ImageIcon, Loader2 } from 'lucide-vue-next'
import { uploadImage, MAX_IMAGE_FILE_SIZE } from '@/api/image'
import { useToast } from '@/composables/useToast'

/**
 * 轻量级「粘贴 + 实时渲染」编辑器。
 *
 * 临时替代基于 CodeMirror 6 的 MarkdownEditor.vue（该编辑器当前处于不可用状态）。
 * 保留基本写作能力：左侧文本框编辑，右侧实时渲染预览。
 *
 * 图片上传能力（与 MarkdownEditor 对齐）：
 * 1. 工具栏按钮选择本地图片（支持多选）
 * 2. 编辑框内直接粘贴图片（截图 / 复制的图片）
 * 3. 拖拽图片文件到编辑区
 * 上传成功后在光标处自动插入 Markdown 图片语法。
 */
const props = defineProps<{
  modelValue: string
  placeholder?: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
  'save': []
}>()

const toast = useToast()

type ViewMode = 'edit' | 'split' | 'preview'
const viewMode = ref<ViewMode>('split')

const modeButtons: { mode: ViewMode; label: string; icon: typeof Pencil }[] = [
  { mode: 'edit', label: '编辑', icon: Pencil },
  { mode: 'split', label: '分屏', icon: Columns2 },
  { mode: 'preview', label: '预览', icon: Eye },
]

const textareaRef = ref<HTMLTextAreaElement | null>(null)

// ── 图片上传状态 ──
const uploadingFiles = ref<Map<string, { name: string; progress: number }>>(new Map())
const isUploading = computed(() => uploadingFiles.value.size > 0)
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

// 拖拽进入编辑区的高亮状态
const isDraggingOver = ref(false)

function onInput(e: Event) {
  emit('update:modelValue', (e.target as HTMLTextAreaElement).value)
}

// Mod/Ctrl + S 保存文章
function onKeydown(e: KeyboardEvent) {
  if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 's') {
    e.preventDefault()
    emit('save')
  }
}

// 在文本框内按 Tab 插入缩进（Markdown 列表/代码常用），避免焦点跳出
function onTab(e: KeyboardEvent) {
  if (e.key !== 'Tab') return
  e.preventDefault()
  const ta = e.target as HTMLTextAreaElement
  const { selectionStart: start, selectionEnd: end, value } = ta
  const insert = '  '
  ta.value = value.slice(0, start) + insert + value.slice(end)
  const pos = start + insert.length
  ta.setSelectionRange(pos, pos)
  emit('update:modelValue', ta.value)
}

// ── 光标处插入文本 ──
function insertAtCursor(text: string) {
  const ta = textareaRef.value
  const current = props.modelValue
  if (!ta || ta.selectionStart == null) {
    // 编辑器不可用或处于预览模式：追加到末尾
    emit('update:modelValue', (current ? current + '\n' : '') + text)
    return
  }
  const start = ta.selectionStart
  const end = ta.selectionEnd
  const next = current.slice(0, start) + text + current.slice(end)
  emit('update:modelValue', next)
  nextTick(() => {
    ta.focus()
    const pos = start + text.length
    ta.setSelectionRange(pos, pos)
  })
}

// ── 图片上传 ──

/** 工具栏按钮：弹出文件选择器（支持多选） */
function handleUploadClick() {
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = 'image/jpeg,image/png,image/gif,image/webp,image/svg+xml'
  input.multiple = true
  input.onchange = () => {
    const files = input.files
    if (files) {
      for (let i = 0; i < files.length; i++) {
        const file = files[i]
        if (file) void doUploadFile(file)
      }
    }
  }
  input.click()
}

async function doUploadFile(file: File) {
  if (!file.type.startsWith('image/')) {
    toast.error(`"${file.name || '文件'}" 不是图片，仅支持 JPEG / PNG / GIF / WebP / SVG`)
    return
  }
  if (file.size > MAX_IMAGE_FILE_SIZE) {
    const maxSizeMB = MAX_IMAGE_FILE_SIZE / (1024 * 1024)
    toast.error(`图片 "${file.name || ''}" 超过大小限制（最大 ${maxSizeMB}MB）`)
    return
  }

  const fileId = `${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
  uploadingFiles.value.set(fileId, { name: file.name || '图片', progress: 0 })

  try {
    const result = await uploadImage(file, (progressEvent) => {
      uploadingFiles.value.set(fileId, { name: file.name || '图片', progress: progressEvent.progress })
    })
    insertAtCursor(`![${result.originalName || '图片'}](${result.url})\n`)
  } catch (e) {
    console.error('图片上传失败:', e)
    const errorMessage = e instanceof Error ? e.message : '未知错误'
    if (errorMessage.includes('413') || errorMessage.includes('过大') || errorMessage.toLowerCase().includes('too large')) {
      toast.error('图片文件过大，请压缩后重试（最大 5MB）')
    } else {
      toast.error('图片上传失败：' + errorMessage)
    }
  } finally {
    uploadingFiles.value.delete(fileId)
  }
}

// ── 粘贴图片上传 ──
function onPaste(e: ClipboardEvent) {
  const items = e.clipboardData?.items
  if (!items) return

  const imageFiles: File[] = []
  for (let i = 0; i < items.length; i++) {
    const item = items[i]
    if (item && item.kind === 'file' && item.type.startsWith('image/')) {
      const file = item.getAsFile()
      if (file) imageFiles.push(file)
    }
  }
  if (imageFiles.length === 0) return // 普通文本粘贴走默认行为

  e.preventDefault()
  imageFiles.forEach(file => void doUploadFile(file))
}

// ── 拖拽图片上传 ──
function onDragOver(e: DragEvent) {
  if (e.dataTransfer?.types.includes('Files')) {
    e.preventDefault()
    isDraggingOver.value = true
  }
}

function onDragLeave(e: DragEvent) {
  // 移动到子元素不算离开编辑区，避免高亮闪烁
  const current = e.currentTarget as Node | null
  if (!current || !e.relatedTarget || !current.contains(e.relatedTarget as Node)) {
    isDraggingOver.value = false
  }
}

function onDrop(e: DragEvent) {
  isDraggingOver.value = false
  const files = e.dataTransfer?.files
  if (!files || files.length === 0) return

  const imageFiles = Array.from(files).filter(f => f.type.startsWith('image/'))
  if (imageFiles.length === 0) return

  e.preventDefault()
  imageFiles.forEach(f => void doUploadFile(f))
}
</script>

<template>
  <div class="simple-editor h-full flex flex-col">
    <!-- 顶栏：视图切换 + 图片上传 + 提示 -->
    <div class="editor-toolbar glass glass-sm flex items-center gap-1 p-1.5 border-b border-border">
      <div class="flex items-center gap-0.5">
        <button
          v-for="btn in modeButtons"
          :key="btn.mode"
          @click="viewMode = btn.mode"
          class="toolbar-btn"
          :class="{ 'is-active': viewMode === btn.mode }"
          :title="btn.label"
        >
          <component :is="btn.icon" :size="15" />
          <span class="hidden sm:inline ml-1 text-xs">{{ btn.label }}</span>
        </button>
      </div>

      <div class="w-px h-4 bg-border mx-0.5 flex-shrink-0" />

      <button
        @click="handleUploadClick"
        class="toolbar-btn"
        title="上传图片（支持粘贴 / 拖拽，最大 5MB）"
      >
        <ImageIcon :size="15" />
        <span class="hidden sm:inline ml-1 text-xs">图片</span>
      </button>

      <div class="flex-1 min-w-4" />

      <div v-if="isUploading" class="flex items-center gap-1.5 text-xs text-accent flex-shrink-0">
        <Loader2 :size="12" class="animate-spin" />
        <span>上传中 {{ uploadingProgress }}%</span>
      </div>
      <p v-else class="text-xs text-text-muted truncate hidden md:block">
        支持粘贴 / 拖拽图片上传，右侧实时预览
      </p>
    </div>

    <!-- 编辑 + 预览（整个区域都是拖拽上传的投放目标） -->
    <div
      class="editor-body relative flex-1 min-h-0 flex"
      @dragover="onDragOver"
      @dragleave="onDragLeave"
      @drop="onDrop"
    >
      <textarea
        v-show="viewMode !== 'preview'"
        ref="textareaRef"
        :value="modelValue"
        :placeholder="placeholder || '将写好的 Markdown 内容粘贴到这里...（图片可直接粘贴或拖入）'"
        @input="onInput"
        @keydown="onKeydown"
        @paste="onPaste"
        spellcheck="false"
        class="editor-textarea flex-1 min-h-0 resize-none border-0 outline-none p-4 bg-bg-secondary text-sm font-mono text-text-primary placeholder:text-text-muted"
        :class="{ 'border-r border-border': viewMode === 'split' }"
      />

      <div
        v-show="viewMode !== 'edit'"
        class="editor-preview flex-1 min-h-0 overflow-y-auto p-6 bg-bg-secondary"
      >
        <div v-if="!modelValue.trim()" class="h-full flex items-center justify-center text-text-muted text-sm">
          预览区：粘贴内容后这里会实时渲染
        </div>
        <ArticleContent v-else :content="modelValue" />
      </div>

      <!-- 拖拽图片进入编辑区时的提示遮罩 -->
      <div
        v-if="isDraggingOver"
        class="drop-overlay absolute inset-2 z-10 flex items-center justify-center rounded-lg border-2 border-dashed border-accent bg-accent-subtle/80 text-accent text-sm font-medium pointer-events-none"
      >
        松开鼠标以上传图片
      </div>
    </div>
  </div>
</template>

<style scoped>
.simple-editor {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.editor-textarea {
  line-height: 1.7;
  tab-size: 2;
  overscroll-behavior: contain;
}

.editor-preview {
  scrollbar-gutter: stable;
}

.toolbar-btn {
  display: flex;
  align-items: center;
  padding: 4px 8px;
  border-radius: 4px;
  color: var(--text-secondary);
  transition: background-color 0.15s, color 0.15s;
  flex-shrink: 0;
}

.toolbar-btn:hover {
  background-color: var(--bg-code);
  color: var(--text-primary);
}

.toolbar-btn.is-active {
  background-color: var(--accent-subtle);
  color: var(--accent);
}
</style>
