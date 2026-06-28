<script setup lang="ts">
import { ref } from 'vue'
import ArticleContent from '@/components/article/ArticleContent.vue'
import { Pencil, Eye, Columns2 } from 'lucide-vue-next'

/**
 * 轻量级「粘贴 + 实时渲染」编辑器。
 *
 * 临时替代基于 CodeMirror 6 的 MarkdownEditor.vue（该编辑器当前处于不可用状态）。
 * 仅保留最基本的能力：把写好的内容粘贴进左侧文本框，右侧实时渲染预览，
 * 从而保证最起码的写作体验。文章其余信息（标题/摘要/分类/标签等）在
 * PostEditorView 的元数据面板中补充。
 */
const props = defineProps<{
  modelValue: string
  placeholder?: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
  'save': []
}>()

type ViewMode = 'edit' | 'split' | 'preview'
const viewMode = ref<ViewMode>('split')

const modeButtons: { mode: ViewMode; label: string; icon: typeof Pencil }[] = [
  { mode: 'edit', label: '编辑', icon: Pencil },
  { mode: 'split', label: '分屏', icon: Columns2 },
  { mode: 'preview', label: '预览', icon: Eye },
]

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
</script>

<template>
  <div class="simple-editor h-full flex flex-col">
    <!-- 顶栏：视图切换 + 提示 -->
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
      <div class="flex-1 min-w-4" />
      <p class="text-xs text-text-muted truncate hidden md:block">
        将写好的内容粘贴到左侧，右侧实时预览
      </p>
    </div>

    <!-- 编辑 + 预览 -->
    <div class="editor-body flex-1 min-h-0 flex">
      <textarea
        v-show="viewMode !== 'preview'"
        :value="modelValue"
        :placeholder="placeholder || '将写好的 Markdown 内容粘贴到这里...'"
        @input="onInput"
        @keydown="onKeydown"
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
