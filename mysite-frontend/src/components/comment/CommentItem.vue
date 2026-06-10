<script setup lang="ts">
import { ref } from 'vue'
import { ThumbsUp, Reply, Trash2, Send, Loader2, X } from 'lucide-vue-next'
import { getGravatarUrl } from '@/utils/gravatar'
import type { Comment, User } from '@/types'

const props = defineProps<{
  comment: Comment
  depth: number
  canDelete: boolean
  isLoggedIn: boolean
  currentUser: User | null | undefined
}>()

const emit = defineEmits<{
  'reply': [payload: { parentId: string; content: string }]
  'like': [comment: Comment]
  'delete': [comment: Comment]
}>()

const showReplyForm = ref(false)
const replyContent = ref('')
const replySubmitting = ref(false)

function getAvatarUrl(comment: Comment): string {
  if (comment.avatar) return comment.avatar
  return getGravatarUrl(comment.email)
}

function formatTime(dateStr: string): string {
  const date = new Date(dateStr)
  const now = new Date()
  const diff = now.getTime() - date.getTime()
  const minutes = Math.floor(diff / 60000)
  const hours = Math.floor(diff / 3600000)
  const days = Math.floor(diff / 86400000)

  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes} 分钟前`
  if (hours < 24) return `${hours} 小时前`
  if (days < 30) return `${days} 天前`
  return date.toLocaleDateString('zh-CN', { year: 'numeric', month: 'long', day: 'numeric' })
}

function openReplyForm() {
  showReplyForm.value = true
  replyContent.value = `@${props.comment.nickname} `
}

function closeReplyForm() {
  showReplyForm.value = false
  replyContent.value = ''
}

async function submitReply() {
  if (!replyContent.value.trim()) return
  replySubmitting.value = true
  try {
    emit('reply', { parentId: props.comment.id, content: replyContent.value.trim() })
    closeReplyForm()
  } finally {
    replySubmitting.value = false
  }
}
</script>

<template>
  <div :class="depth > 0 ? 'ml-8 pl-4 border-l-2 border-border' : ''">
    <div class="flex gap-3 py-4">
      <!-- 头像 -->
      <img
        :src="getAvatarUrl(comment)"
        :alt="comment.nickname"
        class="w-9 h-9 rounded-full flex-shrink-0 bg-surface-secondary"
        loading="lazy"
      />

      <!-- 评论内容 -->
      <div class="flex-1 min-w-0">
        <div class="flex items-center gap-2 mb-1">
          <span class="text-sm font-medium text-text-primary">{{ comment.nickname }}</span>
          <span class="text-xs text-text-muted">{{ formatTime(comment.createTime) }}</span>
        </div>

        <div class="text-sm text-text-primary leading-relaxed whitespace-pre-wrap break-words">
          {{ comment.content }}
        </div>

        <!-- 操作按钮 -->
        <div class="flex items-center gap-4 mt-2">
          <button
            @click="emit('like', comment)"
            class="inline-flex items-center gap-1 text-xs transition-colors"
            :class="comment.isLiked ? 'text-accent' : 'text-text-muted hover:text-accent'"
          >
            <ThumbsUp :size="13" />
            <span v-if="comment.likeCount > 0">{{ comment.likeCount }}</span>
          </button>

          <button
            v-if="isLoggedIn"
            @click="openReplyForm"
            class="inline-flex items-center gap-1 text-xs text-text-muted hover:text-accent transition-colors"
          >
            <Reply :size="13" />
            <span>回复</span>
          </button>

          <button
            v-if="canDelete"
            @click="emit('delete', comment)"
            class="inline-flex items-center gap-1 text-xs text-text-muted hover:text-red-500 transition-colors"
          >
            <Trash2 :size="13" />
          </button>
        </div>

        <!-- 内联回复表单 -->
        <div v-if="showReplyForm" class="mt-3 p-3 rounded-lg bg-surface-secondary border border-border">
          <div class="flex items-center justify-between mb-2">
            <div class="flex items-center gap-2">
              <img
                :src="currentUser?.avatar || getGravatarUrl(currentUser?.email)"
                :alt="currentUser?.username"
                class="w-6 h-6 rounded-full bg-surface-secondary"
              />
              <span class="text-xs font-medium text-text-primary">{{ currentUser?.username }}</span>
              <span class="text-xs text-text-muted">回复 @{{ comment.nickname }}</span>
            </div>
            <button @click="closeReplyForm" class="text-text-muted hover:text-text-primary transition-colors">
              <X :size="14" />
            </button>
          </div>
          <textarea
            v-model="replyContent"
            rows="2"
            placeholder="写下你的回复..."
            class="w-full px-3 py-2 text-sm rounded-md border border-border bg-surface-primary text-text-primary placeholder:text-text-muted focus:outline-none focus:ring-2 focus:ring-accent/50 focus:border-accent transition-colors resize-none"
            @keydown.ctrl.enter="submitReply"
            @keydown.meta.enter="submitReply"
          />
          <div class="flex justify-end mt-2">
            <button
              @click="submitReply"
              :disabled="replySubmitting || !replyContent.trim()"
              class="inline-flex items-center gap-1 px-3 py-1.5 text-xs font-medium rounded-md bg-accent text-white hover:bg-accent-hover disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
            >
              <Loader2 v-if="replySubmitting" :size="12" class="animate-spin" />
              <Send v-else :size="12" />
              {{ replySubmitting ? '提交中...' : '回复' }}
            </button>
          </div>
        </div>

        <!-- 嵌套回复 -->
        <div v-if="comment.replies?.length" class="mt-1">
          <CommentItem
            v-for="reply in comment.replies"
            :key="reply.id"
            :comment="reply"
            :depth="depth + 1"
            :can-delete="canDelete"
            :is-logged-in="isLoggedIn"
            :current-user="currentUser"
            @reply="emit('reply', $event)"
            @like="emit('like', $event)"
            @delete="emit('delete', $event)"
          />
        </div>
      </div>
    </div>
  </div>
</template>
