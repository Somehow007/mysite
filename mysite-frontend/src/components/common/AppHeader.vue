<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { Menu, X, LogOut, PenSquare, LayoutDashboard, Settings, Heart, Camera, Loader2, User, Search, Sun, Moon, Palette } from 'lucide-vue-next'
import ThemeToggle from './ThemeToggle.vue'
import ThemePicker from './ThemePicker.vue'
import SearchDialog from './SearchDialog.vue'
import { useSiteStore } from '@/stores/site'
import { useUserStore } from '@/stores/user'
import { useTheme } from '@/composables/useTheme'
import { useSearch } from '@/composables/useSearch'
import { uploadAvatar } from '@/api/user'
import { Dropdown } from '@/components/ui'

const siteStore = useSiteStore()
const userStore = useUserStore()
const route = useRoute()
const { currentTheme, isDark, toggleDarkMode, getThemeMeta } = useTheme()
const { open: openSearch } = useSearch()

const mobileMenuOpen = ref(false)
const mobileThemeOpen = ref(false)
const avatarUploading = ref(false)

// 当前主题有明暗 variant 时显示日/月直切快捷钮；调色板入口（ThemeToggle）始终可见
const canToggleDark = computed(() => {
  const meta = getThemeMeta(currentTheme.value)
  return !!(meta?.darkVariant || meta?.lightVariant)
})

const isMac = /Mac|iPhone|iPad|iPod/.test(navigator.userAgent)
const kbdHint = isMac ? '⌘K' : 'Ctrl K'

// logo：站名里的「·」分隔符着 accent 色（如「某某·手记」）
const logoSegments = computed(() => siteStore.site.title.split('·'))

// 调色板入口常驻顶栏：variant 主题（classic/paper）下直切钮只做快捷通道，完整选择走面板

function isNavActive(path: string) {
  if (path === '/') return route.name === 'home' || route.name === 'home-page'
  return route.path === path || route.path.startsWith(`${path}/`)
}

function toggleMobileMenu() {
  mobileMenuOpen.value = !mobileMenuOpen.value
}

function closeMobileMenu() {
  mobileMenuOpen.value = false
}

function openSearchFromMenu() {
  closeMobileMenu()
  openSearch()
}

watch(() => route.fullPath, closeMobileMenu)

function handleEscape(e: KeyboardEvent) {
  if (e.key === 'Escape' && mobileMenuOpen.value) closeMobileMenu()
}

onMounted(() => {
  window.addEventListener('keydown', handleEscape)
})

onUnmounted(() => {
  window.removeEventListener('keydown', handleEscape)
})

async function handleLogout() {
  await userStore.logout()
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
  <header class="sticky top-0 z-50 border-b border-border bg-bg-primary/88 backdrop-blur-sm">
    <div class="mx-auto flex h-[52px] max-w-[1180px] items-center gap-7 px-4 sm:px-6 lg:h-[58px]">
      <RouterLink
        to="/"
        class="flex shrink-0 items-center"
        @click="closeMobileMenu"
      >
        <span class="font-serif text-[19px] font-black tracking-tight text-text-primary lg:text-[21px]">
          <template v-for="(seg, i) in logoSegments" :key="i">
            <span v-if="i > 0" class="text-accent">·</span>{{ seg }}
          </template>
        </span>
      </RouterLink>

      <nav class="hidden items-center gap-6 text-sm lg:flex" aria-label="主导航">
        <RouterLink
          v-for="item in siteStore.site.navigation"
          :key="item.path"
          :to="item.path"
          class="text-text-secondary transition-colors hover:text-text-primary"
          :class="isNavActive(item.path) && 'font-semibold text-text-primary shadow-[0_2px_0_var(--accent)]'"
        >
          {{ item.label }}
        </RouterLink>

        <!-- 学习手帐：子路径独立应用，仅管理员可见；用原生 a 避免被 vue-router 拦截 -->
        <a
          v-if="userStore.isAdmin"
          href="/journal/"
          class="text-text-secondary transition-colors hover:text-text-primary"
        >
          手帐
        </a>
      </nav>

      <div class="ml-auto flex items-center gap-1.5">
        <button
          type="button"
          class="hidden h-[34px] min-w-[180px] items-center gap-2 rounded-md border border-border px-3 text-[13px] text-text-muted transition-colors hover:border-border-strong hover:text-text-primary lg:flex paper-flat"
          @click="openSearch"
        >
          <Search :size="15" class="shrink-0" />
          <span class="truncate">搜索或提问</span>
          <kbd class="ml-auto border border-border px-1.5 py-px font-mono text-[10px] text-text-muted">{{ kbdHint }}</kbd>
        </button>

        <button
          v-if="canToggleDark"
          type="button"
          class="icon-btn"
          :aria-label="isDark ? '切换到亮色' : '切换到暗色'"
          @click="toggleDarkMode"
        >
          <Sun v-if="isDark" :size="17" />
          <Moon v-else :size="17" />
        </button>
        <ThemeToggle />

        <template v-if="!userStore.isLoggedIn">
          <RouterLink
            to="/login"
            class="hidden items-center rounded-md border border-border-strong px-3.5 py-1.5 text-[13px] font-semibold text-text-primary transition-colors hover:bg-bg-secondary lg:inline-flex paper-flat"
          >
            登录
          </RouterLink>
        </template>

        <div v-else class="hidden lg:block">
          <Dropdown trigger="click" placement="bottom-end" :spacing="6">
            <template #trigger>
              <button
                class="flex items-center gap-2 px-2 py-1 rounded-md text-sm text-text-secondary hover:bg-bg-code transition-all duration-200"
              >
                <div class="w-7 h-7 rounded-full bg-accent text-text-inverse flex items-center justify-center text-xs font-medium overflow-hidden">
                  <img v-if="userStore.user?.avatar" :src="userStore.user.avatar" :alt="userStore.displayName" class="w-full h-full object-cover" />
                  <span v-else>{{ userStore.displayName?.charAt(0)?.toUpperCase() || 'U' }}</span>
                </div>
                <span class="hidden xl:inline">{{ userStore.displayName }}</span>
              </button>
            </template>
            <template #default="{ close }">
              <div class="w-52 bg-bg-elevated/95 backdrop-blur-xl rounded-xl border border-border/60 shadow-[0_16px_48px_-12px_rgba(0,0,0,0.18)] py-1.5">
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
                  @click="close()"
                >
                  <LayoutDashboard :size="15" />
                  控制台
                </RouterLink>
                <RouterLink
                  to="/dashboard/posts/new"
                  class="flex items-center gap-2.5 px-3 py-2 text-sm text-text-secondary hover:bg-accent-subtle hover:text-accent transition-colors duration-150 mx-1 rounded-md"
                  @click="close()"
                >
                  <PenSquare :size="15" />
                  写文章
                </RouterLink>
                <RouterLink
                  v-if="userStore.user"
                  :to="`/user/${userStore.user.username}`"
                  class="flex items-center gap-2.5 px-3 py-2 text-sm text-text-secondary hover:bg-accent-subtle hover:text-accent transition-colors duration-150 mx-1 rounded-md"
                  @click="close()"
                >
                  <User :size="15" />
                  我的主页
                </RouterLink>
                <RouterLink
                  to="/favorites"
                  class="flex items-center gap-2.5 px-3 py-2 text-sm text-text-secondary hover:bg-accent-subtle hover:text-accent transition-colors duration-150 mx-1 rounded-md"
                  @click="close()"
                >
                  <Heart :size="15" />
                  我的收藏
                </RouterLink>
                <RouterLink
                  to="/dashboard/settings"
                  class="flex items-center gap-2.5 px-3 py-2 text-sm text-text-secondary hover:bg-accent-subtle hover:text-accent transition-colors duration-150 mx-1 rounded-md"
                  @click="close()"
                >
                  <Settings :size="15" />
                  个人设置
                </RouterLink>
                <div class="border-t border-border mt-1 pt-1">
                  <button
                    @click="handleLogout()"
                    class="flex items-center gap-2.5 px-3 py-2 text-sm text-danger hover:bg-danger-subtle transition-colors duration-150 mx-1 rounded-md w-full text-left"
                  >
                    <LogOut :size="15" />
                    退出登录
                  </button>
                </div>
              </div>
            </template>
          </Dropdown>
        </div>

        <button
          type="button"
          class="icon-btn lg:hidden"
          @click="toggleMobileMenu"
          :aria-label="mobileMenuOpen ? '关闭菜单' : '打开菜单'"
          :aria-expanded="mobileMenuOpen"
        >
          <X v-if="mobileMenuOpen" :size="19" />
          <Menu v-else :size="19" />
        </button>
      </div>
    </div>

    <transition name="slide-up">
      <div
        v-if="mobileMenuOpen"
        class="border-t border-border bg-bg-primary/95 backdrop-blur-lg lg:hidden"
      >
        <div class="mx-auto max-w-[1180px] px-4 py-3 sm:px-6">
          <button
            type="button"
            class="flex w-full items-center gap-2 rounded-md border border-border px-3 py-2.5 text-sm text-text-muted paper-flat"
            @click="openSearchFromMenu"
          >
            <Search :size="15" class="shrink-0" />
            <span>搜索或提问</span>
            <kbd class="ml-auto border border-border px-1.5 py-px font-mono text-[10px]">{{ kbdHint }}</kbd>
          </button>

          <nav class="mt-2 flex flex-col divide-y divide-border-subtle" aria-label="移动端导航">
            <RouterLink
              v-for="item in siteStore.site.navigation"
              :key="item.path"
              :to="item.path"
              class="flex items-center border-l-2 py-3 pl-3 text-[15px] transition-colors"
              :class="isNavActive(item.path)
                ? 'border-accent font-semibold text-text-primary'
                : 'border-transparent text-text-secondary hover:text-text-primary'"
              @click="closeMobileMenu"
            >
              {{ item.label }}
            </RouterLink>

            <a
              v-if="userStore.isAdmin"
              href="/journal/"
              class="flex items-center border-l-2 border-transparent py-3 pl-3 text-[15px] text-text-secondary transition-colors hover:text-text-primary"
              @click="closeMobileMenu"
            >
              手帐
            </a>

            <button
              type="button"
              class="flex w-full items-center gap-2.5 border-l-2 border-transparent py-3 pl-3 text-left text-[15px] text-text-secondary transition-colors hover:text-text-primary"
              :aria-expanded="mobileThemeOpen"
              @click="mobileThemeOpen = !mobileThemeOpen"
            >
              <Palette :size="16" class="text-text-muted" />
              主题外观
            </button>
            <div v-if="mobileThemeOpen" class="pb-2">
              <ThemePicker />
            </div>

            <template v-if="!userStore.isLoggedIn">
              <RouterLink
                to="/login"
                class="flex items-center gap-2.5 border-l-2 border-transparent py-3 pl-3 text-[15px] font-medium text-accent"
                @click="closeMobileMenu"
              >
                登录
              </RouterLink>
            </template>
            <template v-else>
              <RouterLink
                to="/dashboard"
                class="flex items-center gap-2.5 border-l-2 border-transparent py-3 pl-3 text-[15px] text-text-secondary transition-colors hover:text-text-primary"
                @click="closeMobileMenu"
              >
                <LayoutDashboard :size="16" class="text-text-muted" />
                控制台
              </RouterLink>
              <RouterLink
                to="/dashboard/posts/new"
                class="flex items-center gap-2.5 border-l-2 border-transparent py-3 pl-3 text-[15px] text-text-secondary transition-colors hover:text-text-primary"
                @click="closeMobileMenu"
              >
                <PenSquare :size="16" class="text-text-muted" />
                写文章
              </RouterLink>
              <RouterLink
                v-if="userStore.user"
                :to="`/user/${userStore.user.username}`"
                class="flex items-center gap-2.5 border-l-2 border-transparent py-3 pl-3 text-[15px] text-text-secondary transition-colors hover:text-text-primary"
                @click="closeMobileMenu"
              >
                <User :size="16" class="text-text-muted" />
                我的主页
              </RouterLink>
              <RouterLink
                to="/favorites"
                class="flex items-center gap-2.5 border-l-2 border-transparent py-3 pl-3 text-[15px] text-text-secondary transition-colors hover:text-text-primary"
                @click="closeMobileMenu"
              >
                <Heart :size="16" class="text-text-muted" />
                我的收藏
              </RouterLink>
              <RouterLink
                to="/dashboard/settings"
                class="flex items-center gap-2.5 border-l-2 border-transparent py-3 pl-3 text-[15px] text-text-secondary transition-colors hover:text-text-primary"
                @click="closeMobileMenu"
              >
                <Settings :size="16" class="text-text-muted" />
                个人设置
              </RouterLink>
              <button
                type="button"
                class="flex w-full items-center gap-2.5 border-l-2 border-transparent py-3 pl-3 text-left text-[15px] text-danger"
                @click="handleLogout"
              >
                <LogOut :size="16" />
                退出登录
              </button>
            </template>
          </nav>
        </div>
      </div>
    </transition>

    <SearchDialog />
  </header>
</template>
