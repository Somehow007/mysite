<script setup lang="ts">
import { MessageSquarePlus, MessageCircle } from 'lucide-vue-next'
import type { ConversationSummary } from '@/api/rag'

defineProps<{
  conversations: ConversationSummary[]
  currentId: string | null
  loading: boolean
}>()

const emit = defineEmits<{
  select: [id: string]
  new: []
}>()

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
</script>

<template>
  <!-- flex-1 min-h-0：在抽屉 flex 列中取"剩余高度"而非 h-full（100% 父高）——
       h-full 会与抽屉头部叠加溢出，底部条目被裁切且滚不到（历史显示不全的根因） -->
  <div class="flex flex-col flex-1 min-h-0">
    <!-- New conversation button -->
    <div class="px-3 pt-3 pb-2 shrink-0">
      <button
        class="chat-history-new w-full flex items-center gap-2 px-3 py-2 rounded-lg
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

      <button
        v-for="conv in conversations"
        :key="conv.id"
        class="chat-history-item w-full text-left px-3 py-2.5 rounded-lg mb-0.5
               transition-colors relative"
        :class="conv.id === currentId
          ? 'bg-accent-subtle text-accent'
          : 'hover:bg-bg-secondary text-text-secondary hover:text-text-primary'"
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
    </div>
  </div>
</template>
