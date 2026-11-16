<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import {
  FileText, PenSquare, FolderTree, Users, Settings, LogOut, ChevronLeft,
  Image as ImageIcon, Tags, MessageSquare, BookOpen, Database, Bot,
  LayoutDashboard, Home, NotebookPen,
} from 'lucide-vue-next'
import { useUserStore } from '@/stores/user'
import { useSiteStore } from '@/stores/site'
import { usePermission } from '@/composables/usePermission'
import UserAvatar from '@/components/common/UserAvatar.vue'

const route = useRoute()
const userStore = useUserStore()
const siteStore = useSiteStore()
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
    { label: 'AI 管理', path: '/dashboard/ai', icon: Bot, requireAdmin: true, requireCreator: false },
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

// 站名 logo：与 AppHeader 一致，「·」分隔符着 accent 色
const logoSegments = computed(() => siteStore.site.title.split('·'))
const logoInitial = computed(() => logoSegments.value[0]?.trim().charAt(0).toUpperCase() || '')
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
        <!-- 折叠态：墨线方框站名字首字（印章式） -->
        <div
          v-if="isCollapsed"
          class="w-8 h-8 border border-border-strong font-serif font-black text-text-primary flex items-center justify-center shrink-0 text-sm"
        >
          <template v-if="logoInitial">{{ logoInitial }}</template>
          <LayoutDashboard v-else :size="15" />
        </div>
        <!-- 展开态：衬线站名 + 控制台副标 -->
        <div v-else class="min-w-0">
          <p class="font-serif text-[17px] font-black tracking-tight text-text-primary leading-tight truncate">
            <template v-if="siteStore.site.title">
              <template v-for="(seg, i) in logoSegments" :key="i">
                <span v-if="i > 0" class="text-accent">·</span>{{ seg }}
              </template>
            </template>
            <template v-else>控制台</template>
          </p>
          <p
            v-if="siteStore.site.title"
            class="text-[11px] text-text-muted tracking-[0.06em] leading-tight mt-0.5"
          >
            控制台
          </p>
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
        <a
          v-if="isAdmin"
          href="/journal/"
          :title="isCollapsed ? '手帐' : ''"
          class="flex items-center gap-2.5 rounded-lg text-[13px] text-text-muted hover:text-text-primary hover:bg-bg-code transition-all duration-150"
          :class="isCollapsed ? 'justify-center px-0 py-2.5' : 'px-3 py-2'"
        >
          <NotebookPen :size="16" class="shrink-0" />
          <span v-if="!isCollapsed">手帐</span>
        </a>
      </div>

      <!-- Footer: user info -->
      <RouterLink
        :to="profileUrl"
        :title="isCollapsed ? userStore.displayName : ''"
        class="border-t border-border hover:bg-bg-code transition-colors flex items-center gap-2.5"
        :class="isCollapsed ? 'justify-center px-0 py-3' : 'p-3'"
      >
        <UserAvatar
          :src="userStore.user?.avatar"
          :name="userStore.displayName || 'U'"
          :size="32"
        />
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
