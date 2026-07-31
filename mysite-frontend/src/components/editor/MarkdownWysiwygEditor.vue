<script setup lang="ts">
import { ref, watch, onMounted, onUnmounted } from 'vue'
import type { EditorHandle } from '@/editor/createEditor'

/**
 * 所见即所得 Markdown 编辑器（Milkdown Crepe）。
 *
 * 对外契约与原编辑器保持一致：`v-model` 双向绑定 Markdown 源文本，
 * `Cmd/Ctrl+S` 触发 `save` 事件。Milkdown 相关实现收敛在 `@/editor/` 内。
 *
 * 健壮性设计（docs/editor-redesign-plan.md §4.3 / §5.2）：
 * - 回声短路：组件自身 emit 的值经 props 回来时直接忽略，避免
 *   「编辑 → 回写 → 重建文档 → 光标跳动」循环；
 * - 序列化防抖：编辑期间 300ms 防抖 emit，save / 卸载时立即 flush；
 * - 初始化失败降级为纯文本 textarea，保证「编辑器坏了也能改文章」。
 */
const props = defineProps<{
  modelValue: string
  placeholder?: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
  'save': []
}>()

const containerRef = ref<HTMLDivElement | null>(null)
const loading = ref(true)
const fallbackMode = ref(false)

let handle: EditorHandle | null = null

// ── 回声短路：记录最近一次由本组件 emit 的值 ──
let lastEmittedValue = ''

// ── 序列化防抖 ──
const EMIT_DEBOUNCE_MS = 300
let pendingValue: string | null = null
let debounceTimer: ReturnType<typeof setTimeout> | null = null

function flushEmit() {
  if (debounceTimer) {
    clearTimeout(debounceTimer)
    debounceTimer = null
  }
  if (pendingValue === null) return
  lastEmittedValue = pendingValue
  emit('update:modelValue', pendingValue)
  pendingValue = null
}

function onEditorChange(markdown: string) {
  pendingValue = markdown
  if (debounceTimer) clearTimeout(debounceTimer)
  debounceTimer = setTimeout(flushEmit, EMIT_DEBOUNCE_MS)
}

// ── 外部 modelValue → 编辑器 ──
watch(
  () => props.modelValue,
  (newVal) => {
    if (newVal === lastEmittedValue) return // 忽略自身 emit 的回声
    if (!handle) return
    // 外部赋值（如打开文章回填）：整体重建文档
    handle.setMarkdown(newVal)
    // setMarkdown 会触发 onEditorChange，提前记账避免把同值再 emit 回去
    lastEmittedValue = newVal
    pendingValue = null
  },
)

// ── Cmd/Ctrl + S 保存 ──
function onKeydown(e: KeyboardEvent) {
  if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 's') {
    e.preventDefault()
    flushEmit() // 保存前确保父级拿到最新内容
    emit('save')
  }
}

onMounted(async () => {
  const container = containerRef.value
  if (!container) return

  container.addEventListener('keydown', onKeydown, true)

  try {
    // 动态引入：Milkdown 体积较大，仅在进入编辑页时加载
    const { createEditor } = await import('@/editor/createEditor')
    handle = await createEditor({
      root: container,
      defaultValue: props.modelValue,
      placeholder: props.placeholder,
      onChange: onEditorChange,
    })
    lastEmittedValue = props.modelValue
  } catch (e) {
    console.error('[MarkdownWysiwygEditor] init failed, falling back to textarea:', e)
    fallbackMode.value = true
  } finally {
    loading.value = false
  }
})

onUnmounted(() => {
  flushEmit()
  if (containerRef.value) {
    containerRef.value.removeEventListener('keydown', onKeydown, true)
  }
  if (handle) {
    void handle.destroy()
    handle = null
  }
  lastEmittedValue = ''
})

function onFallbackInput(e: Event) {
  lastEmittedValue = (e.target as HTMLTextAreaElement).value
  emit('update:modelValue', lastEmittedValue)
}
</script>

<template>
  <div class="wysiwyg-editor h-full flex flex-col">
    <div
      v-show="!fallbackMode"
      ref="containerRef"
      class="crepe-container flex-1 min-h-0 overflow-y-auto bg-bg-secondary"
      :class="{ 'is-loading': loading }"
    />
    <textarea
      v-if="fallbackMode"
      :value="modelValue"
      :placeholder="placeholder || '编辑器初始化失败，已降级为纯文本模式。内容仍可正常编辑和保存。'"
      spellcheck="false"
      class="fallback-textarea flex-1 min-h-0 resize-none border-0 outline-none p-4 bg-bg-secondary text-sm font-mono text-text-primary placeholder:text-text-muted"
      @input="onFallbackInput"
    />
  </div>
</template>

<style>
@import '@/components/editor/editorTheme.css';
</style>

<style scoped>
.wysiwyg-editor {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.crepe-container {
  overscroll-behavior: contain;
  scrollbar-gutter: stable;
}

.crepe-container.is-loading {
  opacity: 0.5;
}

.fallback-textarea {
  line-height: 1.7;
  tab-size: 2;
  overscroll-behavior: contain;
}
</style>
