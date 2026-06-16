<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { FileText, PenSquare, FolderTree, Users, Settings, LogOut, ChevronLeft, ChevronRight, Image as ImageIcon, Tags, MessageSquare, BookOpen } from 'lucide-vue-next'
import { useUserStore } from '@/stores/user'
import { usePermission } from '@/composables/usePermission'

const route = useRoute()
const userStore = useUserStore()
const { isDeveloper } = usePermission()

const isCollapsed = ref(false)

const allNavItems = [
  { label: '文章管理', path: '/dashboard', icon: FileText, requireDeveloper: false },
  { label: '写文章', path: '/dashboard/posts/new', icon: PenSquare, requireDeveloper: false },
  { label: '合集管理', path: '/dashboard/collections', icon: BookOpen, requireDeveloper: false },
  { label: '分类管理', path: '/dashboard/categories', icon: FolderTree, requireDeveloper: true },
  { label: '标签管理', path: '/dashboard/tags', icon: Tags, requireDeveloper: true },
  { label: '图片管理', path: '/dashboard/images', icon: ImageIcon, requireDeveloper: true },
  { label: '用户管理', path: '/dashboard/users', icon: Users, requireDeveloper: true },
  { label: '评论管理', path: '/dashboard/comments', icon: MessageSquare, requireDeveloper: true },
  { label: '设置', path: '/dashboard/settings', icon: Settings, requireDeveloper: false },
]

const navItems = computed(() =>
  allNavItems.filter(item => !item.requireDeveloper || isDeveloper.value)
)

async function handleLogout() {
  await userStore.logout()
  window.location.href = '/'
}

// 编写文章时默认收起侧边栏，给编辑器更多空间
watch(
  () => route.name,
  (name) => {
    if (name === 'post-new' || name === 'post-edit') {
      isCollapsed.value = true
    }
  },
  { immediate: true },
)

function toggleSidebar() {
  isCollapsed.value = !isCollapsed.value
}

const sidebarWidth = computed(() => isCollapsed.value ? 'w-16' : 'w-56')
</script>

<template>
  <aside
    :class="sidebarWidth"
    class="min-h-screen border-r border-border glass glass-sm transition-all duration-300 var(--ease-out) relative flex flex-col overflow-visible"
    :aria-expanded="!isCollapsed"
  >
    <div class="p-5 overflow-hidden">
      <RouterLink
        to="/"
        class="text-lg font-semibold text-text-primary whitespace-nowrap hover:text-accent transition-colors duration-200"
        :class="{ 'opacity-0': isCollapsed }"
      >
        {{ isCollapsed ? 'M' : 'MySite' }}
      </RouterLink>
    </div>

    <nav class="mt-2 flex flex-col gap-0.5 px-2 overflow-hidden">
      <RouterLink
        v-for="item in navItems"
        :key="item.path"
        :to="item.path"
        class="flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm transition-all duration-200 whitespace-nowrap"
        :class="[
          route.path === item.path
            ? 'bg-accent-subtle text-accent font-medium'
            : 'text-text-secondary hover:bg-bg-code hover:text-accent'
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
        class="flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm text-text-secondary hover:bg-red-50 hover:text-red-500 transition-all duration-200 w-full whitespace-nowrap"
        :title="isCollapsed ? '退出登录' : ''"
      >
        <LogOut :size="16" class="flex-shrink-0" />
        <span :class="{ 'hidden': isCollapsed }">退出登录</span>
      </button>
    </div>

    <button
      @click="toggleSidebar"
      class="absolute -right-3 top-6 z-10 w-6 h-6 rounded-full bg-bg-secondary border border-border flex items-center justify-center hover:bg-accent-subtle hover:border-accent hover:text-accent transition-all duration-200 text-text-muted"
      :aria-label="isCollapsed ? '展开侧边栏' : '收起侧边栏'"
      :title="isCollapsed ? '展开侧边栏' : '收起侧边栏'"
    >
      <ChevronRight v-if="isCollapsed" :size="12" />
      <ChevronLeft v-else :size="12" />
    </button>
  </aside>
</template>

<style scoped>
aside {
  contain: layout style !important;
}
</style>
