<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useHead } from '@unhead/vue'
import { MessageSquare, Trash2, CheckCircle, XCircle, Clock, Search, SortAsc, SortDesc } from 'lucide-vue-next'
import { getAdminComments, updateCommentStatus, deleteComment } from '@/api/comment'
import { useToast } from '@/composables/useToast'
import type { CommentAdmin, Pagination } from '@/types'

useHead(() => ({
  title: '评论管理 - MySite',
}))

const toast = useToast()

const comments = ref<CommentAdmin[]>([])
const pagination = ref<Pagination | null>(null)
const loading = ref(false)
const keyword = ref('')
const statusFilter = ref<number | undefined>(undefined)
const sortField = ref('createTime')
const sortOrder = ref('desc')

const statusMap: Record<number, { label: string; class: string; icon: typeof CheckCircle }> = {
  0: { label: '待审核', class: 'bg-amber-50 text-amber-600', icon: Clock },
  1: { label: '已通过', class: 'bg-green-50 text-green-600', icon: CheckCircle },
  2: { label: '已拒绝', class: 'bg-red-50 text-red-600', icon: XCircle },
}

async function fetchComments(page = 1) {
  loading.value = true
  try {
    const res = await getAdminComments({
      page,
      size: 20,
      keyword: keyword.value || undefined,
      status: statusFilter.value,
      sortField: sortField.value,
      sortOrder: sortOrder.value,
    })
    comments.value = res.list
    pagination.value = res.pagination
  } catch {
    comments.value = []
  } finally {
    loading.value = false
  }
}

async function handleStatusChange(id: string, status: number) {
  try {
    await updateCommentStatus(id, status)
    toast.success('状态已更新')
    await fetchComments(pagination.value?.page || 1)
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : '操作失败'
    toast.error(msg)
  }
}

async function handleDelete(id: string) {
  if (!confirm('确定要删除这条评论吗？')) return
  try {
    await deleteComment(id)
    toast.success('评论已删除')
    await fetchComments(pagination.value?.page || 1)
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : '删除失败'
    toast.error(msg)
  }
}

function handleSearch() {
  fetchComments(1)
}

function toggleSortOrder() {
  sortOrder.value = sortOrder.value === 'desc' ? 'asc' : 'desc'
  fetchComments(1)
}

onMounted(() => {
  fetchComments()
})
</script>

<template>
  <div>
    <div class="flex items-center justify-between mb-8">
      <h1 class="text-2xl font-semibold text-text-primary">
        评论管理
      </h1>
    </div>

    <!-- 筛选栏 -->
    <div class="flex items-center gap-3 mb-6 flex-wrap">
      <div class="relative flex-1 min-w-[200px]">
        <Search :size="14" class="absolute left-3 top-1/2 -translate-y-1/2 text-text-muted" />
        <input
          v-model="keyword"
          type="text"
          placeholder="搜索评论内容..."
          class="w-full pl-9 pr-3 py-2 text-sm rounded-lg border border-border bg-surface-primary text-text-primary placeholder:text-text-muted focus:outline-none focus:ring-2 focus:ring-accent/50 focus:border-accent transition-colors"
          @keyup.enter="handleSearch"
        />
      </div>
      <select
        v-model="statusFilter"
        class="px-3 py-2 text-sm rounded-lg border border-border bg-surface-primary text-text-primary focus:outline-none focus:ring-2 focus:ring-accent/50 focus:border-accent transition-colors"
        @change="fetchComments(1)"
      >
        <option :value="undefined">全部状态</option>
        <option :value="0">待审核</option>
        <option :value="1">已通过</option>
        <option :value="2">已拒绝</option>
      </select>
      <select
        v-model="sortField"
        class="px-3 py-2 text-sm rounded-lg border border-border bg-surface-primary text-text-primary focus:outline-none focus:ring-2 focus:ring-accent/50 focus:border-accent transition-colors"
        @change="fetchComments(1)"
      >
        <option value="createTime">按创建时间</option>
        <option value="likeCount">按点赞数</option>
        <option value="replyCount">按回复数</option>
      </select>
      <button
        @click="toggleSortOrder"
        class="flex items-center gap-1 px-3 py-2 text-sm rounded-lg border border-border bg-surface-primary text-text-primary hover:bg-surface-secondary transition-colors"
        title="切换排序方向"
      >
        <SortAsc v-if="sortOrder === 'asc'" :size="14" />
        <SortDesc v-else :size="14" />
        {{ sortOrder === 'asc' ? '升序' : '降序' }}
      </button>
      <button @click="handleSearch" class="btn-primary text-sm">
        搜索
      </button>
    </div>

    <!-- 加载状态 -->
    <div v-if="loading" class="py-16 text-center text-text-muted">
      加载中...
    </div>

    <!-- 空状态 -->
    <div v-else-if="comments.length === 0" class="py-16 text-center">
      <MessageSquare :size="48" class="mx-auto mb-4 text-text-muted" />
      <p class="text-text-muted">暂无评论</p>
    </div>

    <!-- 评论列表 -->
    <div v-else class="space-y-3">
      <div
        v-for="comment in comments"
        :key="comment.id"
        class="p-4 rounded-xl glass glass-sm hover:border-accent/30 transition-all duration-200"
      >
        <div class="flex items-start justify-between gap-4">
          <div class="flex-1 min-w-0">
            <div class="flex items-center gap-2 mb-2">
              <img
                v-if="comment.avatar"
                :src="comment.avatar"
                :alt="comment.nickname"
                class="w-6 h-6 rounded-full"
              />
              <span class="text-sm font-medium text-text-primary">{{ comment.nickname }}</span>
              <span
                class="px-1.5 py-0.5 rounded text-xs"
                :class="statusMap[comment.status]?.class"
              >
                {{ statusMap[comment.status]?.label }}
              </span>
              <span class="text-xs text-text-muted">
                {{ comment.createTime ? new Date(comment.createTime).toLocaleDateString('zh-CN') : '' }}
              </span>
            </div>
            <p class="text-sm text-text-primary break-words">{{ comment.content }}</p>
            <div class="flex items-center gap-3 mt-2 text-xs text-text-muted">
              <span v-if="comment.ipAddress">IP: {{ comment.ipAddress }}</span>
              <span>点赞: {{ comment.likeCount }}</span>
              <span>回复: {{ comment.replyCount }}</span>
            </div>
          </div>

          <div class="flex items-center gap-1 shrink-0">
            <button
              v-if="comment.status !== 1"
              @click="handleStatusChange(comment.id, 1)"
              class="p-2 rounded-lg text-text-muted hover:bg-green-50 hover:text-green-600 transition-all duration-200"
              title="通过"
            >
              <CheckCircle :size="14" />
            </button>
            <button
              v-if="comment.status !== 2"
              @click="handleStatusChange(comment.id, 2)"
              class="p-2 rounded-lg text-text-muted hover:bg-red-50 hover:text-red-500 transition-all duration-200"
              title="拒绝"
            >
              <XCircle :size="14" />
            </button>
            <button
              @click="handleDelete(comment.id)"
              class="p-2 rounded-lg text-text-muted hover:bg-red-50 hover:text-red-500 transition-all duration-200"
              title="删除"
            >
              <Trash2 :size="14" />
            </button>
          </div>
        </div>
      </div>

      <!-- 分页 -->
      <div v-if="pagination && pagination.totalPages > 1" class="flex items-center justify-center gap-2 mt-6">
        <button
          v-for="page in pagination.totalPages"
          :key="page"
          @click="fetchComments(page)"
          class="w-8 h-8 text-sm rounded-lg transition-colors"
          :class="page === pagination.page ? 'bg-accent text-white' : 'text-text-muted hover:bg-surface-secondary'"
        >
          {{ page }}
        </button>
      </div>
    </div>
  </div>
</template>
