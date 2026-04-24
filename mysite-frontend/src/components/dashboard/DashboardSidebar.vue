<script setup lang="ts">
import { RouterLink, useRoute } from 'vue-router'
import { FileText, PenSquare, FolderTree, Settings, LogOut } from 'lucide-vue-next'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const userStore = useUserStore()

const navItems = [
  { label: '文章管理', path: '/dashboard', icon: FileText },
  { label: '写文章', path: '/dashboard/posts/new', icon: PenSquare },
  { label: '分类管理', path: '/dashboard/categories', icon: FolderTree },
  { label: '设置', path: '/dashboard/settings', icon: Settings },
]

async function handleLogout() {
  await userStore.logout()
  window.location.href = '/'
}
</script>

<template>
  <aside class="w-60 min-h-screen border-r border-[var(--color-border)] dark:border-[var(--color-dark-border)] bg-[var(--color-bg-card)] dark:bg-[var(--color-dark-bg-card)] p-6">
    <RouterLink to="/" class="text-lg font-semibold text-[var(--color-text-heading)] dark:text-[var(--color-dark-text-heading)]">
      MySite
    </RouterLink>

    <nav class="mt-8 flex flex-col gap-1">
      <RouterLink
        v-for="item in navItems"
        :key="item.path"
        :to="item.path"
        class="flex items-center gap-3 px-3 py-2 rounded-lg text-sm transition-colors"
        :class="[
          route.path === item.path
            ? 'bg-[var(--color-bg-code)] dark:bg-[var(--color-dark-bg-code)] text-[var(--color-text-heading)] dark:text-[var(--color-dark-text-heading)] font-medium'
            : 'text-[var(--color-text-body)] dark:text-[var(--color-dark-text-body)] hover:bg-[var(--color-bg-code)] dark:hover:bg-[var(--color-dark-bg-code)]'
        ]"
      >
        <component :is="item.icon" :size="16" />
        {{ item.label }}
      </RouterLink>
    </nav>

    <div class="mt-auto pt-8">
      <button
        @click="handleLogout"
        class="flex items-center gap-3 px-3 py-2 rounded-lg text-sm text-[var(--color-text-body)] dark:text-[var(--color-dark-text-body)] hover:bg-[var(--color-bg-code)] dark:hover:bg-[var(--color-dark-bg-code)] transition-colors w-full"
      >
        <LogOut :size="16" />
        退出登录
      </button>
    </div>
  </aside>
</template>
