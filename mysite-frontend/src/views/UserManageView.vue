<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useHead } from '@unhead/vue'
import { Search, Shield, ShieldCheck, UserCog, Trash2, Eye, FileText, ChevronLeft, ChevronRight, X, Loader2 } from 'lucide-vue-next'
import { getAdminUsers, updateUserRole, updateUserStatus, deleteUser, getOperationLogs } from '@/api/admin'
import type { AdminUser, UserOperationLog, AdminUserPage, OperationLogPage } from '@/api/admin'
import { formatDate } from '@/utils/date'

useHead(() => ({
  title: '用户管理 - MySite',
}))

const users = ref<AdminUser[]>([])
const userPage = ref<AdminUserPage | null>(null)
const userLoading = ref(false)
const keyword = ref('')

const logs = ref<UserOperationLog[]>([])
const logPage = ref<OperationLogPage | null>(null)
const logLoading = ref(false)
const showLogs = ref(false)
const logTargetUserId = ref<number | undefined>(undefined)

const updatingUserId = ref<string | null>(null)
const confirmDialog = ref<{ show: boolean; title: string; message: string; action: () => void }>({
  show: false, title: '', message: '', action: () => {}
})

async function fetchUsers(current = 1) {
  userLoading.value = true
  try {
    const params: { current: number; size: number; keyword?: string } = { current, size: 10 }
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
    const params: { current: number; size: number; targetUserId?: number } = { current, size: 15 }
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

function handleRoleChange(user: AdminUser) {
  const newRole = user.role === 'DEVELOPER' ? 'USER' : 'DEVELOPER'
  const label = newRole === 'DEVELOPER' ? '开发者' : '普通用户'
  confirmDialog.value = {
    show: true,
    title: '修改用户角色',
    message: `确定将用户「${user.realName || user.username}」的角色修改为「${label}」吗？`,
    action: () => doRoleChange(user, newRole)
  }
}

async function doRoleChange(user: AdminUser, newRole: string) {
  updatingUserId.value = user.id
  confirmDialog.value.show = false
  try {
    await updateUserRole(user.id, newRole)
    await fetchUsers(userPage.value?.current || 1)
  } catch (e: unknown) {
    alert(e instanceof Error ? e.message : '操作失败')
  } finally {
    updatingUserId.value = null
  }
}

function handleStatusToggle(user: AdminUser) {
  const newStatus = user.status === 1 ? 0 : 1
  const label = newStatus === 1 ? '启用' : '禁用'
  confirmDialog.value = {
    show: true,
    title: `${label}用户`,
    message: `确定${label}用户「${user.realName || user.username}」吗？${newStatus === 0 ? '禁用后该用户将无法登录。' : ''}`,
    action: () => doStatusChange(user, newStatus)
  }
}

async function doStatusChange(user: AdminUser, newStatus: number) {
  updatingUserId.value = user.id
  confirmDialog.value.show = false
  try {
    await updateUserStatus(user.id, newStatus)
    await fetchUsers(userPage.value?.current || 1)
  } catch (e: unknown) {
    alert(e instanceof Error ? e.message : '操作失败')
  } finally {
    updatingUserId.value = null
  }
}

function handleDelete(user: AdminUser) {
  confirmDialog.value = {
    show: true,
    title: '删除用户',
    message: `确定删除用户「${user.realName || user.username}」吗？此操作不可撤销。`,
    action: () => doDelete(user)
  }
}

async function doDelete(user: AdminUser) {
  updatingUserId.value = user.id
  confirmDialog.value.show = false
  try {
    await deleteUser(user.id)
    await fetchUsers(userPage.value?.current || 1)
  } catch (e: unknown) {
    alert(e instanceof Error ? e.message : '操作失败')
  } finally {
    updatingUserId.value = null
  }
}

function viewUserLogs(userId: number) {
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

function getRoleBadgeClass(role: string) {
  return role === 'DEVELOPER'
    ? 'bg-amber-100 dark:bg-amber-900/30 text-amber-700 dark:text-amber-300'
    : 'bg-blue-100 dark:bg-blue-900/30 text-blue-700 dark:text-blue-300'
}

function getStatusBadgeClass(status: number) {
  return status === 1
    ? 'bg-green-100 dark:bg-green-900/30 text-green-700 dark:text-green-300'
    : 'bg-red-100 dark:bg-red-900/30 text-red-700 dark:text-red-300'
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
</script>

<template>
  <div>
    <div class="flex items-center justify-between mb-6">
      <h1 class="text-2xl font-bold text-[var(--color-text-heading)] dark:text-[var(--color-dark-text-heading)]">
        用户管理
      </h1>
      <button
        @click="viewAllLogs"
        class="inline-flex items-center gap-1.5 px-3 py-2 rounded-lg text-sm font-medium bg-[var(--color-bg-code)] dark:bg-[var(--color-dark-bg-code)] text-[var(--color-text-body)] dark:text-[var(--color-dark-text-body)] hover:bg-[var(--color-border)] dark:hover:bg-[var(--color-dark-border)] transition-colors"
      >
        <FileText :size="14" />
        操作日志
      </button>
    </div>

    <div class="mb-4 flex gap-2">
      <div class="relative flex-1 max-w-md">
        <Search :size="16" class="absolute left-3 top-1/2 -translate-y-1/2 text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)]" />
        <input
          v-model="keyword"
          type="text"
          placeholder="搜索用户名、姓名、邮箱或手机号..."
          class="w-full pl-9 pr-3 py-2 rounded-lg border border-[var(--color-border)] dark:border-[var(--color-dark-border)] bg-[var(--color-bg-card)] dark:bg-[var(--color-dark-bg-card)] text-[var(--color-text-heading)] dark:text-[var(--color-dark-text-heading)] placeholder:text-[var(--color-text-muted)] dark:placeholder:text-[var(--color-dark-text-muted)] focus:outline-none focus:ring-2 focus:ring-[var(--color-accent)] dark:focus:ring-[var(--color-dark-accent)] focus:border-transparent text-sm"
          @keyup.enter="handleSearch"
        />
      </div>
      <button
        @click="handleSearch"
        class="px-4 py-2 rounded-lg text-sm font-medium bg-[var(--color-accent)] dark:bg-[var(--color-dark-accent)] text-[var(--color-bg-card)] dark:text-[var(--color-dark-bg-card)] hover:opacity-90 transition-opacity"
      >
        搜索
      </button>
    </div>

    <div v-if="userLoading" class="flex justify-center py-12">
      <Loader2 :size="24" class="animate-spin text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)]" />
    </div>

    <div v-else-if="users.length === 0" class="text-center py-12 text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)]">
      暂无用户数据
    </div>

    <div v-else class="overflow-x-auto rounded-lg border border-[var(--color-border)] dark:border-[var(--color-dark-border)]">
      <table class="w-full text-sm">
        <thead>
          <tr class="bg-[var(--color-bg-code)] dark:bg-[var(--color-dark-bg-code)]">
            <th class="text-left px-4 py-3 font-medium text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)]">用户</th>
            <th class="text-left px-4 py-3 font-medium text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)]">邮箱</th>
            <th class="text-left px-4 py-3 font-medium text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)]">手机号</th>
            <th class="text-center px-4 py-3 font-medium text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)]">角色</th>
            <th class="text-center px-4 py-3 font-medium text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)]">状态</th>
            <th class="text-left px-4 py-3 font-medium text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)]">注册时间</th>
            <th class="text-center px-4 py-3 font-medium text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)]">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="user in users"
            :key="user.id"
            class="border-t border-[var(--color-border)] dark:border-[var(--color-dark-border)] hover:bg-[var(--color-bg-code)] dark:hover:bg-[var(--color-dark-bg-code)] transition-colors"
          >
            <td class="px-4 py-3">
              <div class="font-medium text-[var(--color-text-heading)] dark:text-[var(--color-dark-text-heading)]">{{ user.username }}</div>
              <div class="text-xs text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)]">{{ user.realName }}</div>
            </td>
            <td class="px-4 py-3 text-[var(--color-text-body)] dark:text-[var(--color-dark-text-body)]">{{ user.email || '-' }}</td>
            <td class="px-4 py-3 text-[var(--color-text-body)] dark:text-[var(--color-dark-text-body)]">{{ user.phoneNumber || '-' }}</td>
            <td class="px-4 py-3 text-center">
              <span class="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium" :class="getRoleBadgeClass(user.role)">
                <ShieldCheck v-if="user.role === 'DEVELOPER'" :size="10" />
                <Shield v-else :size="10" />
                {{ user.role === 'DEVELOPER' ? '开发者' : '普通用户' }}
              </span>
            </td>
            <td class="px-4 py-3 text-center">
              <span class="inline-flex px-2 py-0.5 rounded-full text-xs font-medium" :class="getStatusBadgeClass(user.status)">
                {{ user.status === 1 ? '启用' : '禁用' }}
              </span>
            </td>
            <td class="px-4 py-3 text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)] text-xs">
              {{ formatDate(user.createTime) }}
            </td>
            <td class="px-4 py-3">
              <div class="flex items-center justify-center gap-1">
                <button
                  @click="handleRoleChange(user)"
                  :disabled="updatingUserId === user.id"
                  class="p-1.5 rounded text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)] hover:bg-[var(--color-bg-code)] dark:hover:bg-[var(--color-dark-bg-code)] transition-colors disabled:opacity-50"
                  title="切换角色"
                >
                  <UserCog :size="14" />
                </button>
                <button
                  @click="handleStatusToggle(user)"
                  :disabled="updatingUserId === user.id"
                  class="p-1.5 rounded text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)] hover:bg-[var(--color-bg-code)] dark:hover:bg-[var(--color-dark-bg-code)] transition-colors disabled:opacity-50"
                  :title="user.status === 1 ? '禁用用户' : '启用用户'"
                >
                  <Shield :size="14" />
                </button>
                <button
                  @click="viewUserLogs(Number(user.id))"
                  class="p-1.5 rounded text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)] hover:bg-[var(--color-bg-code)] dark:hover:bg-[var(--color-dark-bg-code)] transition-colors"
                  title="查看操作日志"
                >
                  <Eye :size="14" />
                </button>
                <button
                  @click="handleDelete(user)"
                  :disabled="updatingUserId === user.id"
                  class="p-1.5 rounded text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)] hover:bg-red-50 dark:hover:bg-red-900/20 hover:text-red-500 transition-colors disabled:opacity-50"
                  title="删除用户"
                >
                  <Trash2 :size="14" />
                </button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-if="userPage && userPage.pages > 1" class="flex items-center justify-center gap-2 mt-4">
      <button
        @click="fetchUsers((userPage?.current || 1) - 1)"
        :disabled="!userPage?.current || userPage.current <= 1"
        class="p-2 rounded-lg text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)] hover:bg-[var(--color-bg-code)] dark:hover:bg-[var(--color-dark-bg-code)] transition-colors disabled:opacity-50"
      >
        <ChevronLeft :size="16" />
      </button>
      <span class="text-sm text-[var(--color-text-body)] dark:text-[var(--color-dark-text-body)]">
        {{ userPage.current }} / {{ userPage.pages }}
      </span>
      <button
        @click="fetchUsers((userPage?.current || 1) + 1)"
        :disabled="!userPage?.current || userPage.current >= userPage.pages"
        class="p-2 rounded-lg text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)] hover:bg-[var(--color-bg-code)] dark:hover:bg-[var(--color-dark-bg-code)] transition-colors disabled:opacity-50"
      >
        <ChevronRight :size="16" />
      </button>
    </div>

    <Teleport to="body">
      <div v-if="confirmDialog.show" class="fixed inset-0 z-50 flex items-center justify-center bg-black/40" @click.self="confirmDialog.show = false">
        <div class="bg-[var(--color-bg-card)] dark:bg-[var(--color-dark-bg-card)] rounded-xl shadow-xl p-6 w-full max-w-md mx-4">
          <h3 class="text-lg font-semibold text-[var(--color-text-heading)] dark:text-[var(--color-dark-text-heading)] mb-2">
            {{ confirmDialog.title }}
          </h3>
          <p class="text-sm text-[var(--color-text-body)] dark:text-[var(--color-dark-text-body)] mb-6">
            {{ confirmDialog.message }}
          </p>
          <div class="flex justify-end gap-3">
            <button
              @click="confirmDialog.show = false"
              class="px-4 py-2 rounded-lg text-sm font-medium bg-[var(--color-bg-code)] dark:bg-[var(--color-dark-bg-code)] text-[var(--color-text-body)] dark:text-[var(--color-dark-text-body)] hover:bg-[var(--color-border)] dark:hover:bg-[var(--color-dark-border)] transition-colors"
            >
              取消
            </button>
            <button
              @click="confirmDialog.action()"
              class="px-4 py-2 rounded-lg text-sm font-medium bg-red-500 text-white hover:bg-red-600 transition-colors"
            >
              确认
            </button>
          </div>
        </div>
      </div>
    </Teleport>

    <Teleport to="body">
      <div v-if="showLogs" class="fixed inset-0 z-50 flex items-center justify-center bg-black/40" @click.self="closeLogs">
        <div class="bg-[var(--color-bg-card)] dark:bg-[var(--color-dark-bg-card)] rounded-xl shadow-xl p-6 w-full max-w-2xl mx-4 max-h-[80vh] overflow-y-auto">
          <div class="flex items-center justify-between mb-4">
            <h3 class="text-lg font-semibold text-[var(--color-text-heading)] dark:text-[var(--color-dark-text-heading)]">
              操作日志
            </h3>
            <button @click="closeLogs" class="p-1 rounded hover:bg-[var(--color-bg-code)] dark:hover:bg-[var(--color-dark-bg-code)] transition-colors">
              <X :size="18" class="text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)]" />
            </button>
          </div>

          <div v-if="logLoading" class="flex justify-center py-8">
            <Loader2 :size="20" class="animate-spin text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)]" />
          </div>

          <div v-else-if="logs.length === 0" class="text-center py-8 text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)]">
            暂无操作日志
          </div>

          <div v-else class="space-y-3">
            <div
              v-for="log in logs"
              :key="log.id"
              class="flex items-start gap-3 p-3 rounded-lg bg-[var(--color-bg-code)] dark:bg-[var(--color-dark-bg-code)]"
            >
              <div class="flex-1 min-w-0">
                <div class="flex items-center gap-2 mb-1">
                  <span class="text-xs font-medium px-1.5 py-0.5 rounded bg-[var(--color-bg-card)] dark:bg-[var(--color-dark-bg-card)] text-[var(--color-text-heading)] dark:text-[var(--color-dark-text-heading)]">
                    {{ getOperationTypeLabel(log.operationType) }}
                  </span>
                  <span class="text-xs text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)]">
                    {{ formatDate(log.createTime) }}
                  </span>
                </div>
                <p class="text-sm text-[var(--color-text-body)] dark:text-[var(--color-dark-text-body)]">
                  {{ log.detail }}
                </p>
                <p class="text-xs text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)] mt-1">
                  操作者: {{ log.operatorName }} → 目标: {{ log.targetUserName }}
                </p>
              </div>
            </div>
          </div>

          <div v-if="logPage && logPage.pages > 1" class="flex items-center justify-center gap-2 mt-4">
            <button
              @click="fetchLogs((logPage?.current || 1) - 1)"
              :disabled="!logPage?.current || logPage.current <= 1"
              class="p-1.5 rounded text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)] hover:bg-[var(--color-bg-code)] dark:hover:bg-[var(--color-dark-bg-code)] transition-colors disabled:opacity-50"
            >
              <ChevronLeft :size="14" />
            </button>
            <span class="text-xs text-[var(--color-text-body)] dark:text-[var(--color-dark-text-body)]">
              {{ logPage.current }} / {{ logPage.pages }}
            </span>
            <button
              @click="fetchLogs((logPage?.current || 1) + 1)"
              :disabled="!logPage?.current || logPage.current >= logPage.pages"
              class="p-1.5 rounded text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)] hover:bg-[var(--color-bg-code)] dark:hover:bg-[var(--color-dark-bg-code)] transition-colors disabled:opacity-50"
            >
              <ChevronRight :size="14" />
            </button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>
