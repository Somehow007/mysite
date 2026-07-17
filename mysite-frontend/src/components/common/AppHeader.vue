<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { Menu, X, LogOut, PenSquare, LayoutDashboard, Settings, Heart, Camera, Loader2 } from 'lucide-vue-next'
import ThemeToggle from './ThemeToggle.vue'
import SearchDialog from './SearchDialog.vue'
import { useSiteStore } from '@/stores/site'
import { useUserStore } from '@/stores/user'
import { uploadAvatar } from '@/api/user'

const siteStore = useSiteStore()
const userStore = useUserStore()
const route = useRoute()

const mobileMenuOpen = ref(false)
const scrolled = ref(false)
const userMenuOpen = ref(false)
const avatarUploading = ref(false)
const menuStyle = ref<Record<string, string>>({})
const triggerRef = ref<HTMLElement | null>(null)
let menuCloseTimer: ReturnType<typeof setTimeout> | null = null

// simple-game（数独、扫雷）部署在服务器的 80 端口，可通过 VITE_GAME_URL 覆盖
const gameUrl = import.meta.env.VITE_GAME_URL || 'http://124.222.65.169'

function handleScroll() {
  scrolled.value = window.scrollY > 10
}

onMounted(() => {
  window.addEventListener('scroll', handleScroll, { passive: true })
  document.addEventListener('click', handleOutsideClick)
})

onUnmounted(() => {
  window.removeEventListener('scroll', handleScroll)
  document.removeEventListener('click', handleOutsideClick)
  if (menuCloseTimer) clearTimeout(menuCloseTimer)
})

function toggleMobileMenu() {
  mobileMenuOpen.value = !mobileMenuOpen.value
}

function closeMobileMenu() {
  mobileMenuOpen.value = false
}

function updateMenuPosition() {
  if (!triggerRef.value) return
  const rect = triggerRef.value.getBoundingClientRect()
  menuStyle.value = {
    position: 'fixed',
    top: `${rect.bottom + 8}px`,
    right: `${window.innerWidth - rect.right}px`,
    zIndex: '9999',
  }
}

async function toggleUserMenu() {
  userMenuOpen.value = !userMenuOpen.value
  if (userMenuOpen.value) {
    await nextTick()
    updateMenuPosition()
  }
}

function closeUserMenu() {
  userMenuOpen.value = false
}

function handleMenuEnter() {
  if (menuCloseTimer) {
    clearTimeout(menuCloseTimer)
    menuCloseTimer = null
  }
}

function handleMenuLeave() {
  menuCloseTimer = setTimeout(() => {
    userMenuOpen.value = false
  }, 150)
}

function handleOutsideClick(e: MouseEvent) {
  const target = e.target as HTMLElement
  if (userMenuOpen.value && !target.closest('[data-user-menu]') && !target.closest('[data-user-trigger]')) {
    userMenuOpen.value = false
  }
}

async function handleLogout() {
  await userStore.logout()
  userMenuOpen.value = false
  window.location.href = '/'
}

async function handleAvatarUpload(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  if (!file.type.startsWith('image/')) return
  avatarUploading.value = true
  try {
    await uploadAvatar(file)
  } catch {
  } finally {
    avatarUploading.value = false
    input.value = ''
  }
}
</script>

<template>
  <header
    class="sticky top-0 z-50 transition-all duration-300"
    :class="[
      scrolled
        ? 'glass glass-sm border-b border-border'
        : 'bg-transparent'
    ]"
  >
    <div class="max-w-[1080px] mx-auto px-4 sm:px-6 h-16 flex items-center justify-between">
      <RouterLink
        to="/"
        class="text-lg font-semibold text-text-primary hover:text-accent transition-colors duration-200"
        @click="closeMobileMenu"
      >
        {{ siteStore.site.title }}
      </RouterLink>

      <nav class="hidden md:flex items-center gap-1">
        <RouterLink
          v-for="item in siteStore.site.navigation"
          :key="item.path"
          :to="item.path"
          class="px-3 py-1.5 rounded-md text-sm text-text-secondary hover:text-accent hover:bg-accent-subtle transition-all duration-200"
          :class="{ 'font-medium text-accent bg-accent-subtle': route.path === item.path }"
        >
          {{ item.label }}
        </RouterLink>
        <a
          :href="gameUrl"
          target="_blank"
          rel="noopener noreferrer"
          class="px-3 py-1.5 rounded-md text-sm text-text-secondary hover:text-accent hover:bg-accent-subtle transition-all duration-200"
        >
          游戏
        </a>

        <div class="flex items-center gap-1 ml-3 pl-3 border-l border-border">
          <SearchDialog />
          <ThemeToggle />

          <template v-if="!userStore.isLoggedIn">
            <RouterLink
              to="/login"
              class="ml-1 px-3 py-1.5 rounded-md text-sm font-medium text-accent hover:bg-accent-subtle transition-all duration-200"
            >
              登录
            </RouterLink>
          </template>

          <template v-else>
            <div class="relative" @mouseenter="handleMenuEnter" @mouseleave="handleMenuLeave">
              <button
                ref="triggerRef"
                data-user-trigger
                @click="toggleUserMenu"
                class="flex items-center gap-2 px-2 py-1 rounded-md text-sm text-text-secondary hover:bg-bg-code transition-all duration-200"
              >
                <div class="w-7 h-7 rounded-full bg-accent text-text-inverse flex items-center justify-center text-xs font-medium overflow-hidden">
                  <img v-if="userStore.user?.avatar" :src="userStore.user.avatar" :alt="userStore.displayName" class="w-full h-full object-cover" />
                  <span v-else>{{ userStore.displayName?.charAt(0)?.toUpperCase() || 'U' }}</span>
                </div>
                <span class="hidden lg:inline">{{ userStore.displayName }}</span>
              </button>
            </div>
          </template>
        </div>
      </nav>

      <div class="flex md:hidden items-center gap-2">
        <SearchDialog />
        <ThemeToggle />
        <button
          class="p-2 rounded-lg text-text-secondary hover:bg-bg-code transition-colors duration-200"
          @click="toggleMobileMenu"
          :aria-label="mobileMenuOpen ? '关闭菜单' : '打开菜单'"
        >
          <X v-if="mobileMenuOpen" :size="20" />
          <Menu v-else :size="20" />
        </button>
      </div>
    </div>

    <transition name="slide-up">
      <div
        v-if="mobileMenuOpen"
        class="md:hidden border-t border-border glass glass-sm px-6 py-4"
      >
        <nav class="flex flex-col gap-1">
          <RouterLink
            v-for="item in siteStore.site.navigation"
            :key="item.path"
            :to="item.path"
            class="text-sm py-2.5 px-3 rounded-lg text-text-secondary hover:bg-accent-subtle hover:text-accent transition-all duration-200"
            :class="{ 'font-medium text-accent bg-accent-subtle': route.path === item.path }"
            @click="closeMobileMenu"
          >
            {{ item.label }}
          </RouterLink>
          <a
            :href="gameUrl"
            target="_blank"
            rel="noopener noreferrer"
            class="text-sm py-2.5 px-3 rounded-lg text-text-secondary hover:bg-accent-subtle hover:text-accent transition-all duration-200"
            @click="closeMobileMenu"
          >
            游戏
          </a>

          <template v-if="!userStore.isLoggedIn">
            <RouterLink
              to="/login"
              class="text-sm py-2.5 px-3 rounded-lg text-accent font-medium hover:bg-accent-subtle transition-all duration-200"
              @click="closeMobileMenu"
            >
              登录
            </RouterLink>
          </template>
          <template v-else>
            <div class="h-px bg-border my-2" />
            <RouterLink
              to="/dashboard"
              class="text-sm py-2.5 px-3 rounded-lg text-text-secondary hover:bg-accent-subtle hover:text-accent transition-all duration-200"
              @click="closeMobileMenu"
            >
              仪表盘
            </RouterLink>
            <RouterLink
              to="/favorites"
              class="text-sm py-2.5 px-3 rounded-lg text-text-secondary hover:bg-accent-subtle hover:text-accent transition-all duration-200"
              @click="closeMobileMenu"
            >
              我的收藏
            </RouterLink>
            <RouterLink
              to="/dashboard/posts/new"
              class="text-sm py-2.5 px-3 rounded-lg text-text-secondary hover:bg-accent-subtle hover:text-accent transition-all duration-200"
              @click="closeMobileMenu"
            >
              写文章
            </RouterLink>
            <RouterLink
              to="/dashboard/settings"
              class="text-sm py-2.5 px-3 rounded-lg text-text-secondary hover:bg-accent-subtle hover:text-accent transition-all duration-200"
              @click="closeMobileMenu"
            >
              个人设置
            </RouterLink>
            <button
              @click="handleLogout"
              class="text-sm py-2.5 px-3 rounded-lg text-left text-red-500 hover:bg-red-50 transition-colors duration-200"
            >
              退出登录
            </button>
          </template>
        </nav>
      </div>
    </transition>
  </header>

  <Teleport to="body">
    <transition
      enter-active-class="transition duration-200 var(--ease-out)"
      enter-from-class="opacity-0 scale-95 -translate-y-1"
      enter-to-class="opacity-100 scale-100 translate-y-0"
      leave-active-class="transition duration-150 ease-in"
      leave-from-class="opacity-100 scale-100"
      leave-to-class="opacity-0 scale-95"
    >
      <div
        v-if="userMenuOpen"
        data-user-menu
        :style="menuStyle"
        class="w-52 glass glass-sm rounded-xl py-1.5"
        @mouseenter="handleMenuEnter"
        @mouseleave="handleMenuLeave"
      >
        <div class="px-3 py-2 border-b border-border mb-1 flex items-center gap-2.5">
          <div class="relative group">
            <div class="w-9 h-9 rounded-full bg-accent text-text-inverse flex items-center justify-center text-sm font-medium overflow-hidden">
              <img v-if="userStore.user?.avatar" :src="userStore.user.avatar" :alt="userStore.displayName" class="w-full h-full object-cover" />
              <span v-else>{{ userStore.displayName?.charAt(0)?.toUpperCase() || 'U' }}</span>
            </div>
            <label class="absolute inset-0 rounded-full bg-black/40 flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity cursor-pointer">
              <Loader2 v-if="avatarUploading" :size="14" class="animate-spin text-white" />
              <Camera v-else :size="14" class="text-white" />
              <input type="file" accept="image/*" class="hidden" @change="handleAvatarUpload" :disabled="avatarUploading" />
            </label>
          </div>
          <p class="text-sm font-medium text-text-primary">{{ userStore.displayName }}</p>
        </div>
        <RouterLink
          to="/dashboard"
          class="flex items-center gap-2.5 px-3 py-2 text-sm text-text-secondary hover:bg-accent-subtle hover:text-accent transition-colors duration-150 mx-1 rounded-md"
          @click="closeUserMenu"
        >
          <LayoutDashboard :size="15" />
          仪表盘
        </RouterLink>
        <RouterLink
          to="/favorites"
          class="flex items-center gap-2.5 px-3 py-2 text-sm text-text-secondary hover:bg-accent-subtle hover:text-accent transition-colors duration-150 mx-1 rounded-md"
          @click="closeUserMenu"
        >
          <Heart :size="15" />
          我的收藏
        </RouterLink>
        <RouterLink
          to="/dashboard/posts/new"
          class="flex items-center gap-2.5 px-3 py-2 text-sm text-text-secondary hover:bg-accent-subtle hover:text-accent transition-colors duration-150 mx-1 rounded-md"
          @click="closeUserMenu"
        >
          <PenSquare :size="15" />
          写文章
        </RouterLink>
        <RouterLink
          to="/dashboard/settings"
          class="flex items-center gap-2.5 px-3 py-2 text-sm text-text-secondary hover:bg-accent-subtle hover:text-accent transition-colors duration-150 mx-1 rounded-md"
          @click="closeUserMenu"
        >
          <Settings :size="15" />
          个人设置
        </RouterLink>
        <hr class="my-1.5 border-border" />
        <button
          @click="handleLogout"
          class="flex items-center gap-2.5 px-3 py-2 text-sm text-red-500 hover:bg-red-50 transition-colors duration-150 w-full text-left mx-1 rounded-md"
        >
          <LogOut :size="15" />
          退出登录
        </button>
      </div>
    </transition>
  </Teleport>
</template>
