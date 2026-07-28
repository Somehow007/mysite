<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useHead } from '@unhead/vue'
import { Shield, ShieldCheck, Trash2, Eye, FileText, X, Loader2, ChevronDown, Users, CheckCircle2 } from 'lucide-vue-next'
import { getAdminUsers, updateUserRole, updateUserStatus, deleteUser, getOperationLogs } from '@/api/admin'
import type { AdminUser, UserOperationLog, AdminUserPage, OperationLogPage } from '@/api/admin'
import { formatDate } from '@/utils/date'
import { useToast } from '@/composables/useToast'
import { Pagination, StatsCard, PageHeader, SearchFilterBar, DataTable, Badge, Dropdown } from '@/components/ui'
import { useConfirm } from '@/composables/useConfirm'
import type { Column } from '@/components/ui/DataTable.vue'

const { error: toastError, success } = useToast()
const { confirm } = useConfirm()

useHead(() => ({
  title: '用户管理 - MySite',
}))

const users = ref<AdminUser[]>([])
const userPage = ref<AdminUserPage | null>(null)
const userLoading = ref(false)
const keyword = ref('')
const sortField = ref('createTime')
const sortOrder = ref('desc')

const logs = ref<UserOperationLog[]>([])
const logPage = ref<OperationLogPage | null>(null)
const logLoading = ref(false)
const showLogs = ref(false)
const logTargetUserId = ref<string | undefined>(undefined)

const updatingUserId = ref<string | null>(null)

async function fetchUsers(current = 1) {
  userLoading.value = true
  try {
    const params: { current: number; size: number; keyword?: string; sortField?: string; sortOrder?: string } = {
      current,
      size: 10,
      sortField: sortField.value,
      sortOrder: sortOrder.value,
    }
    if (keyword.value.trim()) params.keyword = keyword.value.trim()
    const res = await getAdminUsers(params)
    users.value = res.records || []
    userPage.value = res
  } catch {
    users.value = []
  } finally {
    userLoading.value = false
  }
}

async function fetchLogs(current = 1) {
  logLoading.value = true
  try {
    const params: { current: number; size: number; targetUserId?: string } = { current, size: 15 }
    if (logTargetUserId.value) params.targetUserId = logTargetUserId.value
    const res = await getOperationLogs(params)
    logs.value = res.records || []
    logPage.value = res
  } catch {
    logs.value = []
  } finally {
    logLoading.value = false
  }
}

function handleSearch() {
  fetchUsers(1)
}

function toggleSortOrder() {
  sortOrder.value = sortOrder.value === 'desc' ? 'asc' : 'desc'
  fetchUsers(1)
}

const ROLE_OPTIONS = [
  { value: 'ADMIN', label: '系统管理员' },
  { value: 'CREATOR', label: '创作者' },
  { value: 'USER', label: '普通用户' },
]

const ROLE_LABEL: Record<string, string> = {
  'ADMIN': '系统管理员',
  'CREATOR': '创作者',
  'USER': '普通用户',
}

async function handleRoleChange(user: AdminUser, newRole: string) {
  if (newRole === user.role) return
  const label = ROLE_LABEL[newRole] || newRole
  const ok = await confirm({
    title: '修改用户角色',
    message: `确定将用户「${user.realName || user.username}」的角色修改为「${label}」吗？`,
  })
  if (!ok) return

  updatingUserId.value = user.id
  try {
    await updateUserRole(user.id, newRole)
    success('角色修改成功')
    await fetchUsers(userPage.value?.current || 1)
  } catch (e: unknown) {
    toastError(e instanceof Error ? e.message : '操作失败')
  } finally {
    updatingUserId.value = null
  }
}

async function handleStatusToggle(user: AdminUser) {
  const newStatus = user.status === 1 ? 0 : 1
  const label = newStatus === 1 ? '启用' : '禁用'
  const ok = await confirm({
    title: `${label}用户`,
    message: `确定${label}用户「${user.realName || user.username}」吗？${newStatus === 0 ? '禁用后该用户将无法登录。' : ''}`,
  })
  if (!ok) return

  updatingUserId.value = user.id
  try {
    await updateUserStatus(user.id, newStatus)
    success('状态修改成功')
    await fetchUsers(userPage.value?.current || 1)
  } catch (e: unknown) {
    toastError(e instanceof Error ? e.message : '操作失败')
  } finally {
    updatingUserId.value = null
  }
}

async function handleDelete(user: AdminUser) {
  const ok = await confirm({
    title: '删除用户',
    message: `确定删除用户「${user.realName || user.username}」吗？此操作不可撤销，该用户的所有文章将被保留。`,
    danger: true,
    confirmText: '删除',
    confirmInput: user.username,
  })
  if (!ok) return

  updatingUserId.value = user.id
  try {
    await deleteUser(user.id)
    success('用户已删除')
    await fetchUsers(userPage.value?.current || 1)
  } catch (e: unknown) {
    toastError(e instanceof Error ? e.message : '操作失败')
  } finally {
    updatingUserId.value = null
  }
}

function viewUserLogs(userId: string) {
  logTargetUserId.value = userId
  showLogs.value = true
  fetchLogs(1)
}

function viewAllLogs() {
  logTargetUserId.value = undefined
  showLogs.value = true
  fetchLogs(1)
}

function closeLogs() {
  showLogs.value = false
  logTargetUserId.value = undefined
}

function getOperationTypeLabel(type: string) {
  const map: Record<string, string> = {
    ROLE_CHANGE: '角色变更',
    STATUS_CHANGE: '状态变更',
    DELETE: '删除用户'
  }
  return map[type] || type
}

onMounted(() => {
  fetchUsers()
})

// DataTable columns config
const userColumns: Column<AdminUser>[] = [
  { key: 'user', label: '用户' },
  { key: 'email', label: '邮箱', width: '200px' },
  { key: 'role', label: '角色', align: 'center', width: '150px' },
  { key: 'status', label: '状态', align: 'center', width: '96px' },
  { key: 'createTime', label: '注册时间', width: '144px' },
  { key: 'actions', label: '操作', align: 'center', width: '156px' },
]

const userSortOptions = [
  { label: '注册时间', value: 'createTime' },
  { label: '用户名', value: 'username' },
  { label: '最后登录', value: 'lastLoginTime' },
]

const roleFilterOptions = [
  { label: '全部角色', value: '' },
  { label: '系统管理员', value: 'ADMIN' },
  { label: '创作者', value: 'CREATOR' },
  { label: '普通用户', value: 'USER' },
]

const statusFilterOptions = [
  { label: '全部状态', value: '' },
  { label: '正常', value: '1' },
  { label: '禁用', value: '0' },
]

// Batch selection
const selectedUserIds = ref<Set<string>>(new Set())

function toggleUserSelect(id: string) {
  const next = new Set(selectedUserIds.value)
  if (next.has(id)) next.delete(id); else next.add(id)
  selectedUserIds.value = next
}

function toggleSelectAllUsers() {
  if (selectedUserIds.value.size === users.value.length) {
    selectedUserIds.value = new Set()
  } else {
    selectedUserIds.value = new Set(users.value.map(u => u.id))
  }
}

function clearUserSelection() {
  selectedUserIds.value = new Set()
}

async function handleBatchDisable() {
  if (selectedUserIds.value.size === 0) return
  const ok = await confirm({
    title: '批量禁用',
    message: `确定禁用选中的 ${selectedUserIds.value.size} 名用户吗？`,
  })
  if (!ok) return
  for (const id of selectedUserIds.value) {
    try { await updateUserStatus(id, 0) } catch { /* continue */ }
  }
  success('批量操作完成')
  clearUserSelection()
  await fetchUsers(userPage.value?.current || 1)
}

// Role filter state
const roleFilter = ref('')
const statusFilter = ref('')

// Reset handler
function handleUserReset() {
  keyword.value = ''
  roleFilter.value = ''
  statusFilter.value = ''
  sortField.value = 'createTime'
  sortOrder.value = 'desc'
  fetchUsers(1)
}
</script>

<template>
  <div>
    <PageHeader title="用户管理" :subtitle="userPage ? `共 ${userPage.total} 位注册用户` : ''">
      <template #actions>
        <button @click="viewAllLogs" class="btn-secondary">
          <FileText :size="14" />
          操作日志
        </button>
      </template>
    </PageHeader>

    <!-- Stats -->
    <div class="grid grid-cols-2 lg:grid-cols-4 gap-3 mb-4">
      <StatsCard :icon="Users" label="用户总数" :value="userPage?.total ?? 0" icon-color="accent" />
      <StatsCard :icon="ShieldCheck" label="管理员" :value="users.filter(u => u.role === 'ADMIN').length" icon-color="warning" />
      <StatsCard :icon="FileText" label="创作者" :value="users.filter(u => u.role === 'CREATOR').length" icon-color="info" />
      <StatsCard :icon="Shield" label="禁用" :value="users.filter(u => u.status === 0).length" icon-color="danger" />
    </div>

    <SearchFilterBar
      v-model="keyword"
      placeholder="搜索用户名、姓名、邮箱或手机号..."
      :sort-options="userSortOptions"
      :sort-field="sortField"
      :sort-order="sortOrder"
      :filter-options="roleFilterOptions"
      :filter-value="roleFilter"
      class="mb-4"
      @update:sort-field="sortField = $event; fetchUsers(1)"
      @update:sort-order="sortOrder = $event; fetchUsers(1)"
      @update:filter-value="roleFilter = $event; fetchUsers(1)"
      @search="handleSearch"
      @reset="handleUserReset"
    />

    <!-- Batch bar -->
    <Transition
      enter-active-class="transition-all duration-200 ease-out"
      enter-from-class="opacity-0 -translate-y-1"
      leave-active-class="transition-all duration-200 ease-in"
      leave-to-class="opacity-0 -translate-y-1"
    >
      <div v-if="selectedUserIds.size > 0" class="flex justify-center mb-4">
        <div class="inline-flex items-center gap-3 px-4 py-2 rounded-full border border-accent/30 bg-bg-elevated shadow-md">
          <span class="text-[13px] font-semibold text-accent">已选 {{ selectedUserIds.size }} 项</span>
          <button @click="handleBatchDisable" class="inline-flex items-center gap-1.5 px-3 py-1.5 text-[12.5px] rounded-full bg-warning-subtle text-warning hover:bg-warning/20 transition-colors">
            <Shield :size="12" />批量禁用
          </button>
          <button @click="clearUserSelection" class="px-2 py-1 text-[12.5px] text-text-muted hover:text-text-primary transition-colors">取消</button>
        </div>
      </div>
    </Transition>

    <DataTable
      :columns="userColumns"
      :data="users"
      :loading="userLoading"
      selectable
      :selected-ids="selectedUserIds"
      id-key="id"
      :total="userPage?.total"
      :current-page="userPage?.current"
      :page-size="10"
      @toggle-select="toggleUserSelect($event)"
      @toggle-select-all="toggleSelectAllUsers()"
      @update:current-page="fetchUsers($event)"
    >
      <template #cell-user="{ item }">
        <div class="flex items-center gap-2.5">
          <div class="w-7 h-7 rounded-full shrink-0 flex items-center justify-center text-[11px] font-semibold text-white" :style="{ background: 'linear-gradient(135deg, var(--accent), #7C3AED)' }">
            {{ (item.realName || item.username).charAt(0) }}
          </div>
          <!-- table-fixed 下弹性列：长用户名/昵称自行截断 + 悬浮全称 -->
          <div class="min-w-0 flex-1">
            <div class="text-[13.5px] font-medium text-text-primary truncate" :title="'@' + item.username">@{{ item.username }}</div>
            <div class="text-xs text-text-muted truncate" :title="item.realName">{{ item.realName }}</div>
          </div>
        </div>
      </template>
      <template #cell-email="{ item }">
        <span class="text-[13px] text-text-secondary truncate block">{{ item.email || '-' }}</span>
      </template>
      <template #cell-role="{ item }">
        <Dropdown trigger="click" placement="bottom-start" :spacing="4">
          <template #trigger>
            <button
              :disabled="updatingUserId === item.id"
              class="inline-flex items-center gap-1 disabled:opacity-50 cursor-pointer"
            >
              <Badge :variant="item.role === 'ADMIN' ? 'warning' : item.role === 'CREATOR' ? 'accent' : 'info'" dot>
                {{ ROLE_LABEL[item.role] || item.role }}
              </Badge>
              <ChevronDown :size="10" class="text-text-muted" />
            </button>
          </template>
          <template #default="{ close: closeRole }">
            <div class="w-40 py-1 rounded-xl border border-border/60 bg-bg-elevated shadow-[0_12px_40px_-12px_rgba(0,0,0,0.18)]">
              <button
                v-for="opt in ROLE_OPTIONS"
                :key="opt.value"
                @click="handleRoleChange(item, opt.value); closeRole()"
                :disabled="opt.value === item.role"
                class="w-full text-left px-3.5 py-2.5 text-[13px] transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
                :class="opt.value === item.role
                  ? 'text-accent font-medium bg-accent-subtle/50'
                  : 'text-text-secondary hover:bg-bg-code'"
              >
                <span class="flex items-center justify-between">
                  {{ opt.label }}
                  <CheckCircle2 v-if="opt.value === item.role" :size="13" class="text-accent shrink-0" />
                </span>
              </button>
            </div>
          </template>
        </Dropdown>
      </template>
      <template #cell-status="{ item }">
        <Badge :variant="item.status === 1 ? 'success' : 'danger'" :dot="true" :dot-pulse="false">
          {{ item.status === 1 ? '正常' : '禁用' }}
        </Badge>
      </template>
      <template #cell-createTime="{ item }">
        <span class="text-xs text-text-muted tabular-nums whitespace-nowrap">{{ formatDate(item.createTime) }}</span>
      </template>
      <template #cell-actions="{ item }">
        <div class="flex items-center justify-center gap-1">
          <button
            @click="handleStatusToggle(item)"
            :disabled="updatingUserId === item.id"
            class="p-1.5 rounded-lg text-text-muted hover:text-warning hover:bg-warning-subtle transition-colors disabled:opacity-40"
            :title="item.status === 1 ? '禁用用户' : '启用用户'"
          >
            <Shield :size="15" />
          </button>
          <button
            @click="viewUserLogs(item.id)"
            class="p-1.5 rounded-lg text-text-muted hover:text-accent hover:bg-accent-subtle transition-colors"
            title="查看操作日志"
          >
            <Eye :size="15" />
          </button>
          <button
            @click="handleDelete(item)"
            :disabled="updatingUserId === item.id"
            class="p-1.5 rounded-lg text-text-muted hover:text-danger hover:bg-danger-subtle transition-colors disabled:opacity-40"
            title="删除用户"
          >
            <Trash2 :size="15" />
          </button>
        </div>
      </template>
    </DataTable>

    <Teleport to="body">
      <div v-if="showLogs" class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm" @click.self="closeLogs">
        <div class="card-solid p-6 w-full max-w-2xl mx-4 max-h-[80vh] overflow-y-auto animate-scale-in">
          <div class="flex items-center justify-between mb-4">
            <h3 class="text-lg font-semibold text-text-primary">
              操作日志
            </h3>
            <button @click="closeLogs" class="p-1 rounded hover:bg-bg-code transition-colors">
              <X :size="18" class="text-text-muted" />
            </button>
          </div>

          <div v-if="logLoading" class="flex justify-center py-8">
            <Loader2 :size="20" class="animate-spin text-text-muted" />
          </div>

          <div v-else-if="logs.length === 0" class="text-center py-8 text-text-muted">
            暂无操作日志
          </div>

          <div v-else class="space-y-3">
            <div
              v-for="log in logs"
              :key="log.id"
              class="flex items-start gap-3 p-3 rounded-lg bg-bg-code"
            >
              <div class="flex-1 min-w-0">
                <div class="flex items-center gap-2 mb-1">
                  <span class="text-xs font-medium px-1.5 py-0.5 rounded bg-bg-secondary text-text-primary">
                    {{ getOperationTypeLabel(log.operationType) }}
                  </span>
                  <span class="text-xs text-text-muted">
                    {{ formatDate(log.createTime) }}
                  </span>
                </div>
                <p class="text-sm text-text-secondary">
                  {{ log.detail }}
                </p>
                <p class="text-xs text-text-muted mt-1">
                  操作者: {{ log.operatorName }} → 目标: {{ log.targetUserName }}
                </p>
              </div>
            </div>
          </div>

          <Pagination
            v-if="logPage && logPage.pages > 1"
            :current="logPage.current"
            :total="logPage.total"
            :page-size="15"
            class="mt-4"
            @update:current="fetchLogs($event)"
          />
        </div>
      </div>
    </Teleport>
  </div>
</template>
