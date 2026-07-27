<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { Menu, X, LogOut, PenSquare, LayoutDashboard, Settings, Heart, Camera, Loader2, User } from 'lucide-vue-next'
import ThemeToggle from './ThemeToggle.vue'
import SearchDialog from './SearchDialog.vue'
import { useSiteStore } from '@/stores/site'
import { useUserStore } from '@/stores/user'
import { uploadAvatar } from '@/api/user'
import { Dropdown } from '@/components/ui'

const siteStore = useSiteStore()
const userStore = useUserStore()
const route = useRoute()

const mobileMenuOpen = ref(false)
const scrolled = ref(false)
const avatarUploading = ref(false)

function handleScroll() {
  scrolled.value = window.scrollY > 10
}

onMounted(() => {
  window.addEventListener('scroll', handleScroll, { passive: true })
})

onUnmounted(() => {
  window.removeEventListener('scroll', handleScroll)
})

function toggleMobileMenu() {
  mobileMenuOpen.value = !mobileMenuOpen.value
}

function closeMobileMenu() {
  mobileMenuOpen.value = false
}

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
  <header
    class="sticky top-0 z-50 transition-all duration-300"
    :class="scrolled
      ? 'bg-bg-elevated/80 backdrop-blur-lg border-b border-border shadow-sm'
      : 'bg-transparent'"
  >
    <div class="max-w-[1080px] mx-auto px-4 sm:px-6 h-16 flex items-center justify-between">
      <RouterLink
        to="/"
        class="flex items-center gap-2.5 text-text-primary hover:opacity-80 transition-opacity"
        @click="closeMobileMenu"
      >
        <div class="w-[30px] h-[30px] rounded-lg flex items-center justify-center shrink-0" :style="{ background: 'linear-gradient(135deg, var(--accent), #8B5CF6)' }">
          <span class="text-white text-[13px] font-bold">M</span>
        </div>
        <span class="text-lg font-semibold">{{ siteStore.site.title }}</span>
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

        <!-- 学习手帐（花期）：子路径独立应用，仅管理员可见；用原生 a 避免被 vue-router 拦截 -->
        <a
          v-if="userStore.isAdmin"
          href="/journal/"
          class="px-3 py-1.5 rounded-md text-sm text-text-secondary hover:text-accent hover:bg-accent-subtle transition-all duration-200"
        >
          手帐
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
            <Dropdown trigger="click" placement="bottom-end" :spacing="6">
              <template #trigger>
                <button
                  class="flex items-center gap-2 px-2 py-1 rounded-md text-sm text-text-secondary hover:bg-bg-code transition-all duration-200"
                >
                  <div class="w-7 h-7 rounded-full bg-accent text-text-inverse flex items-center justify-center text-xs font-medium overflow-hidden">
                    <img v-if="userStore.user?.avatar" :src="userStore.user.avatar" :alt="userStore.displayName" class="w-full h-full object-cover" />
                    <span v-else>{{ userStore.displayName?.charAt(0)?.toUpperCase() || 'U' }}</span>
                  </div>
                  <span class="hidden lg:inline">{{ userStore.displayName }}</span>
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
        class="md:hidden border-t border-border bg-bg-elevated/95 backdrop-blur-lg px-6 py-4"
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
            v-if="userStore.isAdmin"
            href="/journal/"
            class="text-sm py-2.5 px-3 rounded-lg text-text-secondary hover:bg-accent-subtle hover:text-accent transition-all duration-200"
            @click="closeMobileMenu"
          >
            手帐
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
              class="text-sm py-2.5 px-3 rounded-lg text-left text-danger hover:bg-danger-subtle transition-colors duration-200"
            >
              退出登录
            </button>
          </template>
        </nav>
      </div>
    </transition>
  </header>

</template>
