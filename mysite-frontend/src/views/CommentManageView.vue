<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useHead } from '@unhead/vue'
import { MessageSquare, Trash2, CheckCircle, Ban } from 'lucide-vue-next'
import { getAdminComments, updateCommentStatus, deleteComment } from '@/api/comment'
import { useToast } from '@/composables/useToast'
import { useConfirm } from '@/composables/useConfirm'
import { PageHeader, SearchFilterBar, Badge, Pagination, EmptyState } from '@/components/ui'
import type { CommentAdmin, Pagination as PaginationType } from '@/types'

useHead(() => ({
  title: '评论管理 - MySite',
}))

const toast = useToast()
const { confirm } = useConfirm()

const comments = ref<CommentAdmin[]>([])
const pagination = ref<PaginationType | null>(null)
const loading = ref(false)
const keyword = ref('')
const statusFilter = ref<number | undefined>(undefined)
const sortField = ref('createTime')
const sortOrder = ref('desc')

const statusFilterOptions = [
  { label: '全部状态', value: '' },
  { label: '待审核', value: '0' },
  { label: '已通过', value: '1' },
  { label: '已拒绝', value: '2' },
]

const sortOptions = [
  { label: '按创建时间', value: 'createTime' },
  { label: '按点赞数', value: 'likeCount' },
  { label: '按回复数', value: 'replyCount' },
]

const statusMap: Record<number, { label: string; variant: 'success' | 'warning' | 'danger' }> = {
  0: { label: '待审核', variant: 'warning' },
  1: { label: '已通过', variant: 'success' },
  2: { label: '已拒绝', variant: 'danger' },
}

const statusFilterValue = computed({
  get: () => (statusFilter.value === undefined ? '' : String(statusFilter.value)),
  set: (v: string) => {
    statusFilter.value = v === '' ? undefined : Number(v)
  },
})

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
  const ok = await confirm({ message: '确定要删除这条评论吗？', danger: true, confirmText: '删除' })
  if (!ok) return
  try {
    await deleteComment(id)
    toast.success('评论已删除')
    await fetchComments(pagination.value?.page || 1)
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : '删除失败'
    toast.error(msg)
  }
}

function handleFilterChange(value: string) {
  statusFilterValue.value = value
  fetchComments(1)
}

function handleSortFieldChange(value: string) {
  sortField.value = value
  fetchComments(1)
}

function handleSortOrderChange(value: string) {
  sortOrder.value = value
  fetchComments(1)
}

function handleReset() {
  keyword.value = ''
  statusFilter.value = undefined
  sortField.value = 'createTime'
  sortOrder.value = 'desc'
  fetchComments(1)
}

onMounted(() => {
  fetchComments()
})
</script>

<template>
  <div>
    <PageHeader title="评论管理" subtitle="审核与管理全站评论" />

    <!-- 筛选栏 -->
    <SearchFilterBar
      v-model="keyword"
      placeholder="搜索评论内容..."
      :filter-options="statusFilterOptions"
      :filter-value="statusFilterValue"
      :sort-options="sortOptions"
      :sort-field="sortField"
      :sort-order="sortOrder"
      class="mb-4"
      @update:filter-value="handleFilterChange"
      @update:sort-field="handleSortFieldChange"
      @update:sort-order="handleSortOrderChange"
      @search="fetchComments(1)"
      @reset="handleReset"
    />

    <!-- 加载状态 -->
    <div v-if="loading" class="py-16 text-center text-text-muted">
      加载中...
    </div>

    <!-- 空状态 -->
    <EmptyState
      v-else-if="comments.length === 0"
      :icon="MessageSquare"
      title="暂无评论"
      description="文章评论将会显示在这里"
    />

    <!-- 评论列表 -->
    <div v-else class="card-solid overflow-hidden">
      <div class="divide-y divide-border">
        <div
          v-for="comment in comments"
          :key="comment.id"
          class="p-4 transition-colors hover:bg-bg-code/50"
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
                <Badge :variant="statusMap[comment.status]?.variant ?? 'muted'" dot>
                  {{ statusMap[comment.status]?.label }}
                </Badge>
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
                class="p-2 rounded-lg text-text-muted hover:bg-success-subtle hover:text-success transition-colors"
                title="通过"
              >
                <CheckCircle :size="14" />
              </button>
              <button
                v-if="comment.status !== 2"
                @click="handleStatusChange(comment.id, 2)"
                class="p-2 rounded-lg text-text-muted hover:bg-danger-subtle hover:text-danger transition-colors"
                title="拒绝"
              >
                <Ban :size="14" />
              </button>
              <button
                @click="handleDelete(comment.id)"
                class="p-2 rounded-lg text-text-muted hover:bg-danger-subtle hover:text-danger transition-colors"
                title="删除"
              >
                <Trash2 :size="14" />
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- 分页 -->
      <Pagination
        v-if="pagination"
        in-card
        :current="pagination.page"
        :total="pagination.total"
        :page-size="20"
        @update:current="fetchComments($event)"
      />
    </div>
  </div>
</template>
