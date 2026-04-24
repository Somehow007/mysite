<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { Menu, X, LogOut, PenSquare, LayoutDashboard, Settings } from 'lucide-vue-next'
import ThemeToggle from './ThemeToggle.vue'
import SearchDialog from './SearchDialog.vue'
import { useSiteStore } from '@/stores/site'
import { useUserStore } from '@/stores/user'

const siteStore = useSiteStore()
const userStore = useUserStore()
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
        ? 'bg-[var(--color-bg-card)]/80 dark:bg-[var(--color-dark-bg-card)]/80 backdrop-blur-lg border-b border-[var(--color-border)] dark:border-[var(--color-dark-border)]'
        : 'bg-transparent'
    ]"
  >
    <div class="max-w-[1080px] mx-auto px-6 h-16 flex items-center justify-between">
      <RouterLink
        to="/"
        class="text-lg font-semibold text-[var(--color-text-heading)] dark:text-[var(--color-dark-text-heading)] hover:opacity-70 transition-opacity"
        @click="closeMobileMenu"
      >
        {{ siteStore.site.title }}
      </RouterLink>

      <nav class="hidden md:flex items-center gap-6">
        <RouterLink
          v-for="item in siteStore.site.navigation"
          :key="item.path"
          :to="item.path"
          class="text-sm text-[var(--color-text-body)] dark:text-[var(--color-dark-text-body)] hover:text-[var(--color-text-heading)] dark:hover:text-[var(--color-dark-text-heading)] transition-colors"
          :class="{ 'font-medium text-[var(--color-text-heading)] dark:text-[var(--color-dark-text-heading)]': route.path === item.path }"
        >
          {{ item.label }}
        </RouterLink>

        <div class="flex items-center gap-3 ml-2">
          <SearchDialog />
          <ThemeToggle />

          <template v-if="!userStore.isLoggedIn">
            <RouterLink
              to="/login"
              class="text-sm text-[var(--color-text-body)] dark:text-[var(--color-dark-text-body)] hover:text-[var(--color-text-heading)] dark:hover:text-[var(--color-dark-text-heading)] transition-colors"
            >
              登录
            </RouterLink>
          </template>

          <template v-else>
            <div class="relative" @mouseenter="handleMenuEnter" @mouseleave="handleMenuLeave">
              <button
                @click="toggleUserMenu"
                class="flex items-center gap-2 text-sm text-[var(--color-text-body)] dark:text-[var(--color-dark-text-body)] hover:text-[var(--color-text-heading)] dark:hover:text-[var(--color-dark-text-heading)] transition-colors"
              >
                <div class="w-7 h-7 rounded-full bg-[var(--color-accent)] dark:bg-[var(--color-dark-accent)] text-[var(--color-bg-card)] dark:text-[var(--color-dark-bg-card)] flex items-center justify-center text-xs font-medium">
                  {{ userStore.displayName?.charAt(0)?.toUpperCase() || 'U' }}
                </div>
                <span class="hidden lg:inline">{{ userStore.displayName }}</span>
              </button>

              <transition
                enter-active-class="transition duration-150 ease-out"
                enter-from-class="opacity-0 scale-95"
                enter-to-class="opacity-100 scale-100"
                leave-active-class="transition duration-100 ease-in"
                leave-from-class="opacity-100 scale-100"
                leave-to-class="opacity-0 scale-95"
              >
                <div
                  v-if="userMenuOpen"
                  class="absolute right-0 mt-2 w-48 bg-[var(--color-bg-card)] dark:bg-[var(--color-dark-bg-card)] border border-[var(--color-border)] dark:border-[var(--color-dark-border)] rounded-lg shadow-lg py-1 z-50"
                >
                  <RouterLink
                    to="/dashboard"
                    class="flex items-center gap-2 px-4 py-2 text-sm text-[var(--color-text-body)] dark:text-[var(--color-dark-text-body)] hover:bg-[var(--color-bg-code)] dark:hover:bg-[var(--color-dark-bg-code)] transition-colors"
                    @click="closeUserMenu"
                  >
                    <LayoutDashboard :size="14" />
                    仪表盘
                  </RouterLink>
                  <RouterLink
                    to="/dashboard/posts/new"
                    class="flex items-center gap-2 px-4 py-2 text-sm text-[var(--color-text-body)] dark:text-[var(--color-dark-text-body)] hover:bg-[var(--color-bg-code)] dark:hover:bg-[var(--color-dark-bg-code)] transition-colors"
                    @click="closeUserMenu"
                  >
                    <PenSquare :size="14" />
                    写文章
                  </RouterLink>
                  <RouterLink
                    to="/dashboard/settings"
                    class="flex items-center gap-2 px-4 py-2 text-sm text-[var(--color-text-body)] dark:text-[var(--color-dark-text-body)] hover:bg-[var(--color-bg-code)] dark:hover:bg-[var(--color-dark-bg-code)] transition-colors"
                    @click="closeUserMenu"
                  >
                    <Settings :size="14" />
                    个人设置
                  </RouterLink>
                  <hr class="my-1 border-[var(--color-border)] dark:border-[var(--color-dark-border)]" />
                  <button
                    @click="handleLogout"
                    class="flex items-center gap-2 px-4 py-2 text-sm text-[var(--color-text-body)] dark:text-[var(--color-dark-text-body)] hover:bg-[var(--color-bg-code)] dark:hover:bg-[var(--color-dark-bg-code)] transition-colors w-full text-left"
                  >
                    <LogOut :size="14" />
                    退出登录
                  </button>
                </div>
              </transition>
            </div>
          </template>
        </div>
      </nav>

      <div class="flex md:hidden items-center gap-3">
        <SearchDialog />
        <ThemeToggle />
        <button
          class="p-1 text-[var(--color-text-body)] dark:text-[var(--color-dark-text-body)]"
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
        class="md:hidden border-t border-[var(--color-border)] dark:border-[var(--color-dark-border)] bg-[var(--color-bg-card)] dark:bg-[var(--color-dark-bg-card)] px-6 py-4"
      >
        <nav class="flex flex-col gap-3">
          <RouterLink
            v-for="item in siteStore.site.navigation"
            :key="item.path"
            :to="item.path"
            class="text-sm py-2 text-[var(--color-text-body)] dark:text-[var(--color-dark-text-body)] hover:text-[var(--color-text-heading)] dark:hover:text-[var(--color-dark-text-heading)] transition-colors"
            @click="closeMobileMenu"
          >
            {{ item.label }}
          </RouterLink>

          <template v-if="!userStore.isLoggedIn">
            <RouterLink
              to="/login"
              class="text-sm py-2 text-[var(--color-text-body)] dark:text-[var(--color-dark-text-body)]"
              @click="closeMobileMenu"
            >
              登录
            </RouterLink>
          </template>
          <template v-else>
            <RouterLink
              to="/dashboard"
              class="text-sm py-2 text-[var(--color-text-body)] dark:text-[var(--color-dark-text-body)]"
              @click="closeMobileMenu"
            >
              仪表盘
            </RouterLink>
            <RouterLink
              to="/dashboard/posts/new"
              class="text-sm py-2 text-[var(--color-text-body)] dark:text-[var(--color-dark-text-body)]"
              @click="closeMobileMenu"
            >
              写文章
            </RouterLink>
            <RouterLink
              to="/dashboard/settings"
              class="text-sm py-2 text-[var(--color-text-body)] dark:text-[var(--color-dark-text-body)]"
              @click="closeMobileMenu"
            >
              个人设置
            </RouterLink>
            <button
              @click="handleLogout"
              class="text-sm py-2 text-left text-[var(--color-text-body)] dark:text-[var(--color-dark-text-body)]"
            >
              退出登录
            </button>
          </template>
        </nav>
      </div>
    </transition>
  </header>
</template>
