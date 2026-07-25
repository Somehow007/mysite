<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import {
  FileText, PenSquare, FolderTree, Users, Settings, LogOut, ChevronLeft,
  Image as ImageIcon, Tags, MessageSquare, BookOpen, Database,
  LayoutDashboard, Home,
} from 'lucide-vue-next'
import { useUserStore } from '@/stores/user'
import { usePermission } from '@/composables/usePermission'

const route = useRoute()
const userStore = useUserStore()
const { isAdmin, isCreator } = usePermission()

const isCollapsed = ref(false)

// Grouped navigation
const navGroups = computed(() => {
  const contentItems = [
    { label: '文章管理', path: '/dashboard', icon: FileText, requireAdmin: false, requireCreator: true },
    { label: '写文章', path: '/dashboard/posts/new', icon: PenSquare, requireAdmin: false, requireCreator: true },
    { label: '合集管理', path: '/dashboard/collections', icon: BookOpen, requireAdmin: false, requireCreator: false },
  ]

  const communityItems = [
    { label: '用户管理', path: '/dashboard/users', icon: Users, requireAdmin: true, requireCreator: false },
    { label: '评论管理', path: '/dashboard/comments', icon: MessageSquare, requireAdmin: true, requireCreator: false },
  ]

  const systemItems = [
    { label: '分类管理', path: '/dashboard/categories', icon: FolderTree, requireAdmin: true, requireCreator: false },
    { label: '标签管理', path: '/dashboard/tags', icon: Tags, requireAdmin: true, requireCreator: false },
    { label: '图片管理', path: '/dashboard/images', icon: ImageIcon, requireAdmin: true, requireCreator: false },
    { label: '知识库', path: '/dashboard/knowledge', icon: Database, requireAdmin: true, requireCreator: false },
  ]

  const allGroups = [
    { title: '内容', items: contentItems },
    { title: '社区', items: communityItems },
    { title: '系统', items: systemItems },
  ]

  return allGroups.map(group => ({
    ...group,
    items: group.items.filter(item => {
      if (item.requireAdmin && !isAdmin.value) return false
      if (item.requireCreator && !(isAdmin.value || isCreator.value)) return false
      return true
    }),
  })).filter(group => group.items.length > 0)
})

async function handleLogout() {
  await userStore.logout()
  window.location.href = '/'
}

// Auto-collapse when editing a post, expand when leaving
watch(
  () => route.name,
  (name) => {
    if (name === 'post-new' || name === 'post-edit') {
      isCollapsed.value = true
    } else {
      isCollapsed.value = false
    }
  },
  { immediate: true },
)

function toggleSidebar() {
  isCollapsed.value = !isCollapsed.value
}

const sidebarWidth = computed(() => isCollapsed.value ? 'w-16' : 'w-56')
const profileUrl = computed(() =>
  userStore.user?.username ? `/user/${userStore.user.username}` : '/dashboard/settings'
)
</script>

<template>
  <aside
    :class="sidebarWidth"
    class="hidden md:flex h-screen sticky top-0 border-r border-border bg-bg-elevated transition-[width] duration-300 ease-out relative flex-col shrink-0"
    :aria-expanded="!isCollapsed"
  >
    <!-- ============ Header ============ -->
    <div
      class="border-b border-border shrink-0 transition-all duration-300"
      :class="isCollapsed ? 'px-0 py-5' : 'px-4 py-5'"
    >
      <RouterLink
        to="/dashboard"
        class="flex items-center gap-2.5 text-text-primary hover:opacity-80 transition-opacity"
        :class="isCollapsed ? 'justify-center' : ''"
      >
        <div
          class="w-[30px] h-[30px] rounded-lg flex items-center justify-center shrink-0"
          :style="{ background: 'linear-gradient(135deg, var(--accent), #8B5CF6)' }"
        >
          <LayoutDashboard :size="16" class="text-white" />
        </div>
        <div v-if="!isCollapsed">
          <p class="text-[15px] font-semibold tracking-wide leading-tight">控制台</p>
          <p class="text-[11px] text-text-muted leading-tight">MySite Admin</p>
        </div>
      </RouterLink>
    </div>

    <!-- ============ Navigation ============ -->
    <nav
      class="flex-1 py-3 overflow-y-auto scrollbar-thin space-y-4 transition-all duration-300"
      :class="isCollapsed ? 'px-1' : 'px-2.5'"
    >
      <div v-for="group in navGroups" :key="group.title">
        <!-- Group title: only when expanded -->
        <p
          v-if="!isCollapsed"
          class="text-[11px] text-text-muted px-3 pt-2.5 pb-1.5 uppercase tracking-[0.06em] font-medium"
        >
          {{ group.title }}
        </p>

        <!-- Divider line between groups when collapsed -->
        <div
          v-else
          class="flex items-center justify-center py-2"
        >
          <span class="block w-6 h-px bg-border/60" />
        </div>

        <ul class="space-y-0.5">
          <li v-for="item in group.items" :key="item.path">
            <RouterLink
              :to="item.path"
              :title="isCollapsed ? item.label : ''"
              class="relative flex items-center gap-2.5 rounded-lg text-[13.5px] transition-all duration-150"
              :class="[
                isCollapsed
                  ? 'justify-center px-0 py-2.5'
                  : 'px-3 py-2',
                route.path === item.path
                  ? 'bg-accent-subtle text-accent font-medium'
                  : 'text-text-secondary hover:bg-accent-subtle/50 hover:text-text-primary'
              ]"
            >
              <component :is="item.icon" :size="17" class="shrink-0" />
              <span v-if="!isCollapsed">{{ item.label }}</span>
              <!-- Active indicator bar: only when expanded -->
              <span
                v-if="route.path === item.path && !isCollapsed"
                class="absolute left-0 top-2 bottom-2 w-[3px] rounded-r-sm bg-accent"
              />
              <!-- Active indicator dot: only when collapsed -->
              <span
                v-if="route.path === item.path && isCollapsed"
                class="absolute right-0 top-1/2 -translate-y-1/2 w-1.5 h-1.5 rounded-full bg-accent"
              />
            </RouterLink>
          </li>
        </ul>
      </div>
    </nav>

    <!-- ============ Bottom section ============ -->
    <div class="shrink-0">
      <!-- Back to home -->
      <div class="border-t border-border" :class="isCollapsed ? 'px-1 py-2' : 'px-2.5 py-2'">
        <a
          href="/"
          :title="isCollapsed ? '回到主页' : ''"
          class="flex items-center gap-2.5 rounded-lg text-[13px] text-text-muted hover:text-text-primary hover:bg-bg-code transition-all duration-150"
          :class="isCollapsed ? 'justify-center px-0 py-2.5' : 'px-3 py-2'"
        >
          <Home :size="16" class="shrink-0" />
          <span v-if="!isCollapsed">回到主页</span>
        </a>
      </div>

      <!-- Footer: user info -->
      <RouterLink
        :to="profileUrl"
        :title="isCollapsed ? userStore.displayName : ''"
        class="border-t border-border hover:bg-bg-code transition-colors flex items-center gap-2.5"
        :class="isCollapsed ? 'justify-center px-0 py-3' : 'p-3'"
      >
        <div
          class="w-8 h-8 rounded-full shrink-0 flex items-center justify-center text-xs font-semibold text-white overflow-hidden"
          :style="{ background: userStore.user?.avatar ? undefined : 'linear-gradient(135deg, #F59E0B, #EF4444)' }"
        >
          <img
            v-if="userStore.user?.avatar"
            :src="userStore.user.avatar"
            :alt="userStore.displayName"
            class="w-full h-full object-cover"
          />
          <span v-else>{{ userStore.displayName?.charAt(0)?.toUpperCase() || 'U' }}</span>
        </div>
        <template v-if="!isCollapsed">
          <div class="flex-1 min-w-0">
            <p class="text-[13px] font-medium text-text-primary leading-tight truncate">{{ userStore.displayName }}</p>
            <p class="text-[11px] text-text-muted leading-tight">
              {{ userStore.user?.role === 'ADMIN' ? '系统管理员' : userStore.user?.role === 'CREATOR' ? '创作者' : '用户' }}
            </p>
          </div>
          <button
            @click.prevent.stop="handleLogout"
            class="p-1.5 rounded-lg text-text-muted hover:text-danger hover:bg-danger-subtle transition-colors shrink-0"
            title="退出登录"
          >
            <LogOut :size="15" />
          </button>
        </template>
      </RouterLink>
    </div>

    <!-- ============ Toggle button ============ -->
    <button
      @click="toggleSidebar"
      class="absolute -right-3.5 top-1/2 -translate-y-1/2 w-7 h-7 rounded-full border-2 border-border bg-bg-elevated flex items-center justify-center hover:border-accent hover:text-accent transition-all duration-200 z-[100] shadow-md"
    >
      <ChevronLeft
        :size="14"
        class="text-text-muted transition-transform duration-300"
        :class="{ 'rotate-180': isCollapsed }"
      />
    </button>
  </aside>
</template>
