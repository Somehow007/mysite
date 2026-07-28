<script setup lang="ts">
import { ref, nextTick } from 'vue'
import { MessageSquarePlus, MessageCircle, Trash2, Pencil, X, Check } from 'lucide-vue-next'
import { useConfirm } from '@/composables/useConfirm'
import { useToast } from '@/composables/useToast'
import { renameConversation, deleteConversation } from '@/api/rag'
import type { ConversationSummary } from '@/api/rag'

defineProps<{
  conversations: ConversationSummary[]
  currentId: string | null
  loading: boolean
}>()

const emit = defineEmits<{
  select: [id: string]
  new: []
  deleted: [id: string]
  renamed: [id: string, title: string]
}>()

const { confirm } = useConfirm()
const { success, error: toastError } = useToast()

const editingId = ref<string | null>(null)
const editTitle = ref('')
const editInputRef = ref<HTMLInputElement | null>(null)
const deletingId = ref<string | null>(null)
const renamingId = ref<string | null>(null)

function formatTime(iso: string): string {
  if (!iso) return ''
  const d = new Date(iso)
  const now = new Date()
  const diff = now.getTime() - d.getTime()
  if (diff < 60_000) return '刚刚'
  if (diff < 3600_000) return `${Math.floor(diff / 60_000)} 分钟前`
  if (diff < 86400_000) return `${Math.floor(diff / 3600_000)} 小时前`
  if (diff < 604800_000) return `${Math.floor(diff / 86400_000)} 天前`
  return `${d.getMonth() + 1}/${d.getDate()}`
}

function startEdit(conv: ConversationSummary, e: MouseEvent) {
  e.stopPropagation()
  editingId.value = conv.id
  editTitle.value = conv.title || ''
  nextTick(() => {
    editInputRef.value?.focus()
    editInputRef.value?.select()
  })
}

function cancelEdit() {
  editingId.value = null
  editTitle.value = ''
}

async function saveEdit(convId: string, e?: Event) {
  e?.stopPropagation()
  const title = editTitle.value.trim()
  if (!title || title === '') {
    cancelEdit()
    return
  }
  renamingId.value = convId
  try {
    await renameConversation(convId, title)
    emit('renamed', convId, title)
    success('已重命名')
  } catch (e: unknown) {
    toastError(e instanceof Error ? e.message : '重命名失败')
  } finally {
    renamingId.value = null
    editingId.value = null
  }
}

async function handleDelete(conv: ConversationSummary, e: MouseEvent) {
  e.stopPropagation()
  const ok = await confirm({
    message: `确定删除对话「${conv.title || '(无标题)'}」吗？`,
    danger: true,
    confirmText: '删除',
  })
  if (!ok) return

  deletingId.value = conv.id
  try {
    await deleteConversation(conv.id)
    emit('deleted', conv.id)
    success('已删除')
  } catch (e: unknown) {
    toastError(e instanceof Error ? e.message : '删除失败')
  } finally {
    deletingId.value = null
  }
}

function handleKeydown(e: KeyboardEvent, convId: string) {
  if (e.key === 'Enter') {
    e.preventDefault()
    saveEdit(convId)
  } else if (e.key === 'Escape') {
    cancelEdit()
  }
}
</script>

<template>
  <!-- flex-1 min-h-0：在抽屉 flex 列中取"剩余高度"而非 h-full（100% 父高）——
       h-full 会与抽屉头部叠加溢出，底部条目被裁切且滚不到（历史显示不全的根因） -->
  <div class="flex flex-col flex-1 min-h-0">
    <!-- New conversation button -->
    <div class="px-3 pt-3 pb-2 shrink-0">
      <button
        class="w-full flex items-center gap-2 px-3 py-2 rounded-lg
               border border-dashed border-border text-text-secondary
               hover:border-accent hover:text-accent hover:bg-accent-subtle
               transition-colors text-sm font-medium"
        @click="emit('new')"
      >
        <MessageSquarePlus :size="16" />
        新建对话
      </button>
    </div>

    <!-- Conversation list -->
    <div class="flex-1 overflow-y-auto scrollbar-thin px-2">
      <p
        v-if="conversations.length === 0 && !loading"
        class="text-xs text-text-muted text-center py-6"
      >
        暂无历史对话
      </p>

      <div
        v-if="loading"
        class="flex items-center justify-center py-6"
      >
        <span class="w-4 h-4 border-2 border-accent/30 border-t-accent rounded-full animate-spin" />
      </div>

      <div
        v-for="conv in conversations"
        :key="conv.id"
        class="relative group"
      >
        <!-- Normal display -->
        <div
          v-if="editingId !== conv.id"
          class="w-full text-left"
        >
          <button
            class="w-full text-left px-3 py-2.5 rounded-lg mb-0.5
                   transition-colors relative"
            :class="conv.id === currentId
              ? 'bg-accent-subtle text-accent'
              : 'hover:bg-bg-secondary text-text-secondary hover:text-text-primary'"
            :disabled="deletingId === conv.id"
            @click="emit('select', conv.id)"
          >
            <!-- 激活态左侧竖条 -->
            <span
              v-if="conv.id === currentId"
              class="absolute left-0 top-2 bottom-2 w-[3px] rounded-r-sm bg-accent"
            />
            <div class="flex items-start gap-2">
              <MessageCircle :size="14" class="mt-0.5 shrink-0 opacity-60" />
              <div class="min-w-0 flex-1">
                <p class="text-xs leading-relaxed truncate">
                  {{ conv.title || '(无标题)' }}
                </p>
                <p class="text-[10px] text-text-muted mt-0.5">
                  {{ conv.messageCount ?? 0 }} 条消息 · {{ formatTime(conv.updateTime) }}
                </p>
              </div>
            </div>
          </button>

          <!-- Hover actions (delete + rename) -->
          <div class="absolute right-2 top-1/2 -translate-y-1/2 hidden group-hover:flex items-center gap-0.5">
            <button
              class="p-1 rounded text-text-muted hover:text-text-primary hover:bg-bg-secondary transition-colors"
              title="重命名"
              @click="startEdit(conv, $event)"
            >
              <Pencil :size="12" />
            </button>
            <button
              class="p-1 rounded text-text-muted hover:text-danger hover:bg-danger-subtle transition-colors"
              title="删除"
              :disabled="deletingId === conv.id"
              @click="handleDelete(conv, $event)"
            >
              <span v-if="deletingId === conv.id" class="w-3 h-3 border-2 border-danger/30 border-t-danger rounded-full animate-spin block" />
              <Trash2 v-else :size="12" />
            </button>
          </div>
        </div>

        <!-- Inline edit -->
        <div v-else class="px-3 py-2" @click.stop>
          <div class="flex items-center gap-1">
            <input
              ref="editInputRef"
              v-model="editTitle"
              type="text"
              class="flex-1 px-2 py-1 text-xs rounded border border-accent bg-bg-elevated text-text-primary outline-none"
              :disabled="renamingId === conv.id"
              @keydown="handleKeydown($event, conv.id)"
              @click.stop
            />
            <button
              class="p-1 rounded text-accent hover:bg-accent/10 transition-colors"
              :disabled="renamingId === conv.id"
              @click="saveEdit(conv.id, $event)"
            >
              <span v-if="renamingId === conv.id" class="w-3 h-3 border-2 border-accent/30 border-t-accent rounded-full animate-spin block" />
              <Check v-else :size="14" />
            </button>
            <button
              class="p-1 rounded text-text-muted hover:text-text-primary transition-colors"
              :disabled="renamingId === conv.id"
              @click="cancelEdit"
            >
              <X :size="14" />
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
