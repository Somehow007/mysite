<script setup lang="ts">
import { ref, computed } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { FileText, PenSquare, FolderTree, Users, Settings, LogOut, ChevronLeft, ChevronRight } from 'lucide-vue-next'
import { useUserStore } from '@/stores/user'
import { usePermission } from '@/composables/usePermission'

const route = useRoute()
const userStore = useUserStore()
const { isDeveloper } = usePermission()

const isCollapsed = ref(false)

const allNavItems = [
  { label: '文章管理', path: '/dashboard', icon: FileText, requireDeveloper: false },
  { label: '写文章', path: '/dashboard/posts/new', icon: PenSquare, requireDeveloper: false },
  { label: '分类管理', path: '/dashboard/categories', icon: FolderTree, requireDeveloper: true },
  { label: '用户管理', path: '/dashboard/users', icon: Users, requireDeveloper: true },
  { label: '设置', path: '/dashboard/settings', icon: Settings, requireDeveloper: false },
]

const navItems = computed(() =>
  allNavItems.filter(item => !item.requireDeveloper || isDeveloper.value)
)

async function handleLogout() {
  await userStore.logout()
  window.location.href = '/'
}

function toggleSidebar() {
  isCollapsed.value = !isCollapsed.value
}

const sidebarWidth = computed(() => isCollapsed.value ? 'w-16' : 'w-52')
</script>

<template>
  <aside
    :class="sidebarWidth"
    class="min-h-screen border-r border-[var(--color-border)] dark:border-[var(--color-dark-border)] bg-[var(--color-bg-card)] dark:bg-[var(--color-dark-bg-card)] transition-all duration-300 ease-in-out relative flex flex-col"
    :aria-expanded="!isCollapsed"
  >
    <button
      @click="toggleSidebar"
      class="absolute -right-3 top-6 z-10 w-6 h-6 rounded-full bg-[var(--color-bg-card)] dark:bg-[var(--color-dark-bg-card)] border border-[var(--color-border)] dark:border-[var(--color-dark-border)] flex items-center justify-center hover:bg-[var(--color-bg-code)] dark:hover:bg-[var(--color-dark-bg-code)] transition-colors"
      :aria-label="isCollapsed ? '展开侧边栏' : '收起侧边栏'"
      :title="isCollapsed ? '展开侧边栏' : '收起侧边栏'"
    >
      <ChevronRight v-if="isCollapsed" :size="14" />
      <ChevronLeft v-else :size="14" />
    </button>

    <div class="p-5 overflow-hidden">
      <RouterLink
        to="/"
        class="text-lg font-semibold text-[var(--color-text-heading)] dark:text-[var(--color-dark-text-heading)] whitespace-nowrap"
        :class="{ 'opacity-0': isCollapsed }"
      >
        {{ isCollapsed ? 'M' : 'MySite' }}
      </RouterLink>
    </div>

    <nav class="mt-2 flex flex-col gap-1 px-2 overflow-hidden">
      <RouterLink
        v-for="item in navItems"
        :key="item.path"
        :to="item.path"
        class="flex items-center gap-3 px-3 py-2 rounded-lg text-sm transition-colors whitespace-nowrap"
        :class="[
          route.path === item.path
            ? 'bg-[var(--color-bg-code)] dark:bg-[var(--color-dark-bg-code)] text-[var(--color-text-heading)] dark:text-[var(--color-dark-text-heading)] font-medium'
            : 'text-[var(--color-text-body)] dark:text-[var(--color-dark-text-body)] hover:bg-[var(--color-bg-code)] dark:hover:bg-[var(--color-dark-bg-code)]'
        ]"
        :title="isCollapsed ? item.label : ''"
      >
        <component :is="item.icon" :size="16" class="flex-shrink-0" />
        <span :class="{ 'hidden': isCollapsed }">{{ item.label }}</span>
      </RouterLink>
    </nav>

    <div class="mt-auto pt-4 px-2 overflow-hidden">
      <button
        @click="handleLogout"
        class="flex items-center gap-3 px-3 py-2 rounded-lg text-sm text-[var(--color-text-body)] dark:text-[var(--color-dark-text-body)] hover:bg-[var(--color-bg-code)] dark:hover:bg-[var(--color-dark-bg-code)] transition-colors w-full whitespace-nowrap"
        :title="isCollapsed ? '退出登录' : ''"
      >
        <LogOut :size="16" class="flex-shrink-0" />
        <span :class="{ 'hidden': isCollapsed }">退出登录</span>
      </button>
    </div>
  </aside>
</template>
