<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { MessageSquare, Send, Loader2, AlertCircle, RefreshCw, LogIn } from 'lucide-vue-next'
import { getArticleComments, createComment, toggleCommentLike, deleteComment } from '@/api/comment'
import { useUserStore } from '@/stores/user'
import { useToast } from '@/composables/useToast'
import { getGravatarUrl } from '@/utils/gravatar'
import { useRouter } from 'vue-router'
import CommentItem from '@/components/comment/CommentItem.vue'
import type { Comment } from '@/types'

const props = defineProps<{
  articleId: string
}>()

const userStore = useUserStore()
const toast = useToast()
const router = useRouter()

const comments = ref<Comment[]>([])
const loading = ref(false)
const error = ref(false)
const submitting = ref(false)
const newContent = ref('')

const isLoggedIn = computed(() => userStore.isLoggedIn)
const currentUser = computed(() => userStore.user)

const commentCount = computed(() => {
  let count = 0
  function countReplies(list: Comment[]) {
    for (const c of list) {
      count++
      if (c.replies?.length) countReplies(c.replies)
    }
  }
  countReplies(comments.value)
  return count
})

async function fetchComments() {
  loading.value = true
  error.value = false
  try {
    comments.value = await getArticleComments(props.articleId)
  } catch {
    error.value = true
  } finally {
    loading.value = false
  }
}

async function handleSubmit() {
  if (!isLoggedIn.value) {
    toast.error('请先登录后再评论')
    router.push('/login')
    return
  }
  if (!newContent.value.trim()) {
    toast.error('请输入评论内容')
    return
  }

  submitting.value = true
  try {
    await createComment({
      articleId: props.articleId,
      content: newContent.value.trim(),
    })
    toast.success('评论成功')
    newContent.value = ''
    await fetchComments()
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : '评论失败'
    toast.error(msg)
  } finally {
    submitting.value = false
  }
}

async function handleReply(payload: { parentId: string; content: string }) {
  try {
    await createComment({
      articleId: props.articleId,
      parentId: payload.parentId,
      content: payload.content,
    })
    toast.success('回复成功')
    await fetchComments()
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : '回复失败'
    toast.error(msg)
  }
}

async function handleLike(comment: Comment) {
  try {
    const result = await toggleCommentLike(comment.id)
    comment.isLiked = result.liked
    comment.likeCount = result.likeCount
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : '操作失败'
    toast.error(msg)
  }
}

async function handleDelete(comment: Comment) {
  if (!confirm('确定要删除这条评论吗？')) return
  try {
    await deleteComment(comment.id)
    toast.success('删除成功')
    await fetchComments()
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : '删除失败'
    toast.error(msg)
  }
}

function canDelete(comment: Comment): boolean {
  if (userStore.isAdmin) return true
  if (isLoggedIn.value && comment.userId && currentUser.value?.id === comment.userId) return true
  return false
}

watch(() => props.articleId, () => {
  fetchComments()
})

onMounted(() => {
  fetchComments()
})
</script>

<template>
  <div class="mt-12 pt-8 border-t border-border">
    <div class="flex items-center gap-2 mb-6">
      <MessageSquare :size="20" class="text-accent" />
      <h3 class="text-lg font-semibold text-text-primary">评论</h3>
      <span class="text-sm text-text-muted">({{ commentCount }})</span>
    </div>

    <!-- 顶级评论输入区 - 已登录 -->
    <div v-if="isLoggedIn" class="mb-8 p-4 rounded-lg bg-surface-secondary border border-border">
      <div class="flex items-center gap-3 mb-3">
        <img
          :src="currentUser?.avatar || getGravatarUrl(currentUser?.email)"
          :alt="currentUser?.username"
          class="w-8 h-8 rounded-full bg-surface-secondary"
        />
        <span class="text-sm font-medium text-text-primary">{{ currentUser?.username }}</span>
      </div>

      <textarea
        v-model="newContent"
        rows="3"
        placeholder="写下你的评论..."
        class="w-full px-3 py-2 text-sm rounded-md border border-border bg-surface-primary text-text-primary placeholder:text-text-muted focus:outline-none focus:ring-2 focus:ring-accent/50 focus:border-accent transition-colors resize-none"
      />

      <div class="flex justify-end mt-3">
        <button
          @click="handleSubmit"
          :disabled="submitting || !newContent.trim()"
          class="inline-flex items-center gap-1.5 px-4 py-2 text-sm font-medium rounded-md bg-accent text-white hover:bg-accent-hover disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
        >
          <Loader2 v-if="submitting" :size="14" class="animate-spin" />
          <Send v-else :size="14" />
          {{ submitting ? '提交中...' : '发表评论' }}
        </button>
      </div>
    </div>

    <!-- 评论输入区 - 未登录 -->
    <div v-else class="mb-8 p-4 rounded-lg bg-surface-secondary border border-border text-center">
      <p class="text-sm text-text-muted mb-3">登录后即可参与评论</p>
      <button
        @click="router.push('/login')"
        class="inline-flex items-center gap-1.5 px-4 py-2 text-sm font-medium rounded-md bg-accent text-white hover:bg-accent-hover transition-colors"
      >
        <LogIn :size="14" />
        登录
      </button>
    </div>

    <!-- 加载状态 -->
    <div v-if="loading" class="flex items-center justify-center py-8 gap-2">
      <div class="w-4 h-4 border-2 border-accent border-t-transparent rounded-full animate-spin" />
      <span class="text-sm text-text-muted">评论加载中...</span>
    </div>

    <!-- 错误状态 -->
    <div v-else-if="error" class="text-center py-8">
      <div class="flex items-center justify-center gap-2 text-text-muted mb-2">
        <AlertCircle :size="16" />
        <span class="text-sm">评论加载失败</span>
      </div>
      <button @click="fetchComments" class="inline-flex items-center gap-1 text-xs text-accent hover:opacity-70 transition-opacity">
        <RefreshCw :size="12" />
        点击重试
      </button>
    </div>

    <!-- 评论列表 -->
    <div v-else-if="comments.length > 0" class="space-y-1">
      <CommentItem
        v-for="comment in comments"
        :key="comment.id"
        :comment="comment"
        :depth="0"
        :is-logged-in="isLoggedIn"
        :current-user="currentUser"
        :can-delete="canDelete(comment)"
        @reply="handleReply"
        @like="handleLike"
        @delete="handleDelete"
      />
    </div>

    <!-- 空状态 -->
    <div v-else class="text-center py-8 text-text-muted text-sm">
      暂无评论，来说两句吧~
    </div>
  </div>
</template>
