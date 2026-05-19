<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { Menu, X, LogOut, PenSquare, LayoutDashboard, Settings, FolderTree, Users, Heart, ImageIcon } from 'lucide-vue-next'
import ThemeToggle from './ThemeToggle.vue'
import SearchDialog from './SearchDialog.vue'
import { useSiteStore } from '@/stores/site'
import { useUserStore } from '@/stores/user'
import { usePermission } from '@/composables/usePermission'

const siteStore = useSiteStore()
const userStore = useUserStore()
const { isDeveloper } = usePermission()
const route = useRoute()

const mobileMenuOpen = ref(false)
const scrolled = ref(false)
const userMenuOpen = ref(false)
let menuCloseTimer: ReturnType<typeof setTimeout> | null = null

function handleScroll() {
  scrolled.value = window.scrollY > 10
}

onMounted(() => {
  window.addEventListener('scroll', handleScroll, { passive: true })
})

onUnmounted(() => {
  window.removeEventListener('scroll', handleScroll)
  if (menuCloseTimer) clearTimeout(menuCloseTimer)
})

function toggleMobileMenu() {
  mobileMenuOpen.value = !mobileMenuOpen.value
}

function closeMobileMenu() {
  mobileMenuOpen.value = false
}

function toggleUserMenu() {
  userMenuOpen.value = !userMenuOpen.value
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

async function handleLogout() {
  await userStore.logout()
  userMenuOpen.value = false
  window.location.href = '/'
}
</script>

<template>
  <header
    class="sticky top-0 z-50 transition-all duration-300"
    :class="[
      scrolled
        ? 'bg-[var(--color-bg-card)]/85 dark:bg-[var(--color-dark-bg-card)]/85 backdrop-blur-xl border-b border-[var(--color-border)] dark:border-[var(--color-dark-border)] shadow-sm'
        : 'bg-transparent'
    ]"
  >
    <div class="max-w-[1080px] mx-auto px-4 sm:px-6 h-16 flex items-center justify-between">
      <RouterLink
        to="/"
        class="text-lg font-semibold text-[var(--color-text-heading)] dark:text-[var(--color-dark-text-heading)] hover:text-[var(--color-accent)] dark:hover:text-[var(--color-dark-accent)] transition-colors duration-200"
        @click="closeMobileMenu"
      >
        {{ siteStore.site.title }}
      </RouterLink>

      <nav class="hidden md:flex items-center gap-1">
        <RouterLink
          v-for="item in siteStore.site.navigation"
          :key="item.path"
          :to="item.path"
          class="px-3 py-1.5 rounded-md text-sm text-[var(--color-text-body)] dark:text-[var(--color-dark-text-body)] hover:text-[var(--color-accent)] dark:hover:text-[var(--color-dark-accent)] hover:bg-[var(--color-accent-light)] dark:hover:bg-[var(--color-dark-accent-light)] transition-all duration-200"
          :class="{ 'font-medium text-[var(--color-accent)] dark:text-[var(--color-dark-accent)] bg-[var(--color-accent-light)] dark:bg-[var(--color-dark-accent-light)]': route.path === item.path }"
        >
          {{ item.label }}
        </RouterLink>

        <div class="flex items-center gap-1 ml-3 pl-3 border-l border-[var(--color-border)] dark:border-[var(--color-dark-border)]">
          <SearchDialog />
          <ThemeToggle />

          <template v-if="!userStore.isLoggedIn">
            <RouterLink
              to="/login"
              class="ml-1 px-3 py-1.5 rounded-md text-sm font-medium text-[var(--color-accent)] dark:text-[var(--color-dark-accent)] hover:bg-[var(--color-accent-light)] dark:hover:bg-[var(--color-dark-accent-light)] transition-all duration-200"
            >
              登录
            </RouterLink>
          </template>

          <template v-else>
            <div class="relative" @mouseenter="handleMenuEnter" @mouseleave="handleMenuLeave">
              <button
                @click="toggleUserMenu"
                class="flex items-center gap-2 px-2 py-1 rounded-md text-sm text-[var(--color-text-body)] dark:text-[var(--color-dark-text-body)] hover:bg-[var(--color-bg-code)] dark:hover:bg-[var(--color-dark-bg-code)] transition-all duration-200"
              >
                <div class="w-7 h-7 rounded-full bg-[var(--color-accent)] dark:bg-[var(--color-dark-accent)] text-white dark:text-[var(--color-dark-bg-primary)] flex items-center justify-center text-xs font-medium">
                  {{ userStore.displayName?.charAt(0)?.toUpperCase() || 'U' }}
                </div>
                <span class="hidden lg:inline">{{ userStore.displayName }}</span>
              </button>

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
                  class="absolute right-0 mt-2 w-52 bg-[var(--color-bg-card)] dark:bg-[var(--color-dark-bg-card)] border border-[var(--color-border)] dark:border-[var(--color-dark-border)] rounded-xl shadow-lg py-1.5 z-50 card-shadow-hover"
                >
                  <div class="px-3 py-2 border-b border-[var(--color-border)] dark:border-[var(--color-dark-border)] mb-1">
                    <p class="text-sm font-medium text-[var(--color-text-heading)] dark:text-[var(--color-dark-text-heading)]">{{ userStore.displayName }}</p>
                  </div>
                  <RouterLink
                    to="/dashboard"
                    class="flex items-center gap-2.5 px-3 py-2 text-sm text-[var(--color-text-body)] dark:text-[var(--color-dark-text-body)] hover:bg-[var(--color-accent-light)] dark:hover:bg-[var(--color-dark-accent-light)] hover:text-[var(--color-accent)] dark:hover:text-[var(--color-dark-accent)] transition-colors duration-150 mx-1 rounded-md"
                    @click="closeUserMenu"
                  >
                    <LayoutDashboard :size="15" />
                    仪表盘
                  </RouterLink>
                  <RouterLink
                    to="/favorites"
                    class="flex items-center gap-2.5 px-3 py-2 text-sm text-[var(--color-text-body)] dark:text-[var(--color-dark-text-body)] hover:bg-[var(--color-accent-light)] dark:hover:bg-[var(--color-dark-accent-light)] hover:text-[var(--color-accent)] dark:hover:text-[var(--color-dark-accent)] transition-colors duration-150 mx-1 rounded-md"
                    @click="closeUserMenu"
                  >
                    <Heart :size="15" />
                    我的收藏
                  </RouterLink>
                  <RouterLink
                    to="/dashboard/posts/new"
                    class="flex items-center gap-2.5 px-3 py-2 text-sm text-[var(--color-text-body)] dark:text-[var(--color-dark-text-body)] hover:bg-[var(--color-accent-light)] dark:hover:bg-[var(--color-dark-accent-light)] hover:text-[var(--color-accent)] dark:hover:text-[var(--color-dark-accent)] transition-colors duration-150 mx-1 rounded-md"
                    @click="closeUserMenu"
                  >
                    <PenSquare :size="15" />
                    写文章
                  </RouterLink>
                  <RouterLink
                    v-if="isDeveloper"
                    to="/dashboard/images"
                    class="flex items-center gap-2.5 px-3 py-2 text-sm text-[var(--color-text-body)] dark:text-[var(--color-dark-text-body)] hover:bg-[var(--color-accent-light)] dark:hover:bg-[var(--color-dark-accent-light)] hover:text-[var(--color-accent)] dark:hover:text-[var(--color-dark-accent)] transition-colors duration-150 mx-1 rounded-md"
                    @click="closeUserMenu"
                  >
                    <ImageIcon :size="15" />
                    图片管理
                  </RouterLink>
                  <RouterLink
                    v-if="isDeveloper"
                    to="/dashboard/categories"
                    class="flex items-center gap-2.5 px-3 py-2 text-sm text-[var(--color-text-body)] dark:text-[var(--color-dark-text-body)] hover:bg-[var(--color-accent-light)] dark:hover:bg-[var(--color-dark-accent-light)] hover:text-[var(--color-accent)] dark:hover:text-[var(--color-dark-accent)] transition-colors duration-150 mx-1 rounded-md"
                    @click="closeUserMenu"
                  >
                    <FolderTree :size="15" />
                    分类管理
                  </RouterLink>
                  <RouterLink
                    v-if="isDeveloper"
                    to="/dashboard/users"
                    class="flex items-center gap-2.5 px-3 py-2 text-sm text-[var(--color-text-body)] dark:text-[var(--color-dark-text-body)] hover:bg-[var(--color-accent-light)] dark:hover:bg-[var(--color-dark-accent-light)] hover:text-[var(--color-accent)] dark:hover:text-[var(--color-dark-accent)] transition-colors duration-150 mx-1 rounded-md"
                    @click="closeUserMenu"
                  >
                    <Users :size="15" />
                    用户管理
                  </RouterLink>
                  <RouterLink
                    to="/dashboard/settings"
                    class="flex items-center gap-2.5 px-3 py-2 text-sm text-[var(--color-text-body)] dark:text-[var(--color-dark-text-body)] hover:bg-[var(--color-accent-light)] dark:hover:bg-[var(--color-dark-accent-light)] hover:text-[var(--color-accent)] dark:hover:text-[var(--color-dark-accent)] transition-colors duration-150 mx-1 rounded-md"
                    @click="closeUserMenu"
                  >
                    <Settings :size="15" />
                    个人设置
                  </RouterLink>
                  <hr class="my-1.5 border-[var(--color-border)] dark:border-[var(--color-dark-border)]" />
                  <button
                    @click="handleLogout"
                    class="flex items-center gap-2.5 px-3 py-2 text-sm text-red-500 dark:text-red-400 hover:bg-red-50 dark:hover:bg-red-900/20 transition-colors duration-150 w-full text-left mx-1 rounded-md"
                  >
                    <LogOut :size="15" />
                    退出登录
                  </button>
                </div>
              </transition>
            </div>
          </template>
        </div>
      </nav>

      <div class="flex md:hidden items-center gap-2">
        <SearchDialog />
        <ThemeToggle />
        <button
          class="p-2 rounded-lg text-[var(--color-text-body)] dark:text-[var(--color-dark-text-body)] hover:bg-[var(--color-bg-code)] dark:hover:bg-[var(--color-dark-bg-code)] transition-colors duration-200"
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
        class="md:hidden border-t border-[var(--color-border)] dark:border-[var(--color-dark-border)] bg-[var(--color-bg-card)]/95 dark:bg-[var(--color-dark-bg-card)]/95 backdrop-blur-xl px-6 py-4"
      >
        <nav class="flex flex-col gap-1">
          <RouterLink
            v-for="item in siteStore.site.navigation"
            :key="item.path"
            :to="item.path"
            class="text-sm py-2.5 px-3 rounded-lg text-[var(--color-text-body)] dark:text-[var(--color-dark-text-body)] hover:bg-[var(--color-accent-light)] dark:hover:bg-[var(--color-dark-accent-light)] hover:text-[var(--color-accent)] dark:hover:text-[var(--color-dark-accent)] transition-all duration-200"
            :class="{ 'font-medium text-[var(--color-accent)] dark:text-[var(--color-dark-accent)] bg-[var(--color-accent-light)] dark:bg-[var(--color-dark-accent-light)]': route.path === item.path }"
            @click="closeMobileMenu"
          >
            {{ item.label }}
          </RouterLink>

          <template v-if="!userStore.isLoggedIn">
            <RouterLink
              to="/login"
              class="text-sm py-2.5 px-3 rounded-lg text-[var(--color-accent)] dark:text-[var(--color-dark-accent)] font-medium hover:bg-[var(--color-accent-light)] dark:hover:bg-[var(--color-dark-accent-light)] transition-all duration-200"
              @click="closeMobileMenu"
            >
              登录
            </RouterLink>
          </template>
          <template v-else>
            <div class="h-px bg-[var(--color-border)] dark:bg-[var(--color-dark-border)] my-2" />
            <RouterLink
              to="/dashboard"
              class="text-sm py-2.5 px-3 rounded-lg text-[var(--color-text-body)] dark:text-[var(--color-dark-text-body)] hover:bg-[var(--color-accent-light)] dark:hover:bg-[var(--color-dark-accent-light)] hover:text-[var(--color-accent)] dark:hover:text-[var(--color-dark-accent)] transition-all duration-200"
              @click="closeMobileMenu"
            >
              仪表盘
            </RouterLink>
            <RouterLink
              to="/favorites"
              class="text-sm py-2.5 px-3 rounded-lg text-[var(--color-text-body)] dark:text-[var(--color-dark-text-body)] hover:bg-[var(--color-accent-light)] dark:hover:bg-[var(--color-dark-accent-light)] hover:text-[var(--color-accent)] dark:hover:text-[var(--color-dark-accent)] transition-all duration-200"
              @click="closeMobileMenu"
            >
              我的收藏
            </RouterLink>
            <RouterLink
              to="/dashboard/posts/new"
              class="text-sm py-2.5 px-3 rounded-lg text-[var(--color-text-body)] dark:text-[var(--color-dark-text-body)] hover:bg-[var(--color-accent-light)] dark:hover:bg-[var(--color-dark-accent-light)] hover:text-[var(--color-accent)] dark:hover:text-[var(--color-dark-accent)] transition-all duration-200"
              @click="closeMobileMenu"
            >
              写文章
            </RouterLink>
            <RouterLink
              v-if="isDeveloper"
              to="/dashboard/images"
              class="text-sm py-2.5 px-3 rounded-lg text-[var(--color-text-body)] dark:text-[var(--color-dark-text-body)] hover:bg-[var(--color-accent-light)] dark:hover:bg-[var(--color-dark-accent-light)] hover:text-[var(--color-accent)] dark:hover:text-[var(--color-dark-accent)] transition-all duration-200"
              @click="closeMobileMenu"
            >
              图片管理
            </RouterLink>
            <RouterLink
              v-if="isDeveloper"
              to="/dashboard/categories"
              class="text-sm py-2.5 px-3 rounded-lg text-[var(--color-text-body)] dark:text-[var(--color-dark-text-body)] hover:bg-[var(--color-accent-light)] dark:hover:bg-[var(--color-dark-accent-light)] hover:text-[var(--color-accent)] dark:hover:text-[var(--color-dark-accent)] transition-all duration-200"
              @click="closeMobileMenu"
            >
              分类管理
            </RouterLink>
            <RouterLink
              v-if="isDeveloper"
              to="/dashboard/users"
              class="text-sm py-2.5 px-3 rounded-lg text-[var(--color-text-body)] dark:text-[var(--color-dark-text-body)] hover:bg-[var(--color-accent-light)] dark:hover:bg-[var(--color-dark-accent-light)] hover:text-[var(--color-accent)] dark:hover:text-[var(--color-dark-accent)] transition-all duration-200"
              @click="closeMobileMenu"
            >
              用户管理
            </RouterLink>
            <RouterLink
              to="/dashboard/settings"
              class="text-sm py-2.5 px-3 rounded-lg text-[var(--color-text-body)] dark:text-[var(--color-dark-text-body)] hover:bg-[var(--color-accent-light)] dark:hover:bg-[var(--color-dark-accent-light)] hover:text-[var(--color-accent)] dark:hover:text-[var(--color-dark-accent)] transition-all duration-200"
              @click="closeMobileMenu"
            >
              个人设置
            </RouterLink>
            <button
              @click="handleLogout"
              class="text-sm py-2.5 px-3 rounded-lg text-left text-red-500 dark:text-red-400 hover:bg-red-50 dark:hover:bg-red-900/20 transition-colors duration-200"
            >
              退出登录
            </button>
          </template>
        </nav>
      </div>
    </transition>
  </header>
</template>
