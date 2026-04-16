<template>
  <div class="viewport" :class="{ 'menu-active': isMenuOpen }">
    <div class="nav-header">
      <nav class="nav-wrapper" aria-label="Main">
        <span class="logo" v-if="site.logo">
          <router-link to="/" title="首页">
            <img :src="site.logo" alt="Logo" />
          </router-link>
        </span>
        <ul v-if="navigation.length > 0">
          <li
            v-for="item in navigation"
            :key="item.slug"
            :class="['nav-' + item.slug, { active: isActiveRoute(item.url) }]"
          >
            <router-link :to="item.url">
              <span>{{ item.label }}</span>
            </router-link>
          </li>
        </ul>
        <a
          class="nav-search"
          title="搜索"
          aria-label="搜索"
          @click="openSearch"
          @keydown.enter="openSearch"
        >
          <i class="icon icon-search">
            <IconSearch />
          </i>
        </a>
        <span class="nav-members" v-if="site.members_enabled">
          <template v-if="!userStore.isLoggedIn">
            <template v-if="!site.members_invite_only">
              <router-link class="nav-button-secondary" to="/login">登录</router-link>
              <router-link
                v-if="!hideSubscribeButton"
                class="nav-button-primary"
                to="/register"
              >
                订阅
              </router-link>
            </template>
            <template v-else>
              <router-link class="nav-button-primary" to="/login">登录</router-link>
            </template>
          </template>
          <template v-else>
            <div class="nav-user-info">
              <router-link to="/account" class="nav-user-avatar" :title="userStore.displayName">
                <img 
                  v-if="userStore.user?.avatar && !avatarLoadFailed" 
                  :src="userStore.user.avatar" 
                  :alt="userStore.displayName"
                  @error="handleAvatarError"
                />
                <span v-else class="nav-user-name">{{ userStore.displayName.charAt(0).toUpperCase() }}</span>
              </router-link>
            </div>
            <router-link class="nav-button-primary" to="/post/create">写文章</router-link>
            <a class="nav-button-secondary nav-logout" @click="handleLogout">登出</a>
          </template>
        </span>
      </nav>

      <div class="nav-wrapper-control">
        <div class="inner">
          <a
            class="nav-menu"
            role="button"
            @click="toggleMenu"
            @keydown.enter="toggleMenu"
          >
            <i class="icon icon-menu">
              <IconMenu />
            </i>
            菜单
          </a>
          <a
            class="nav-search"
            title="搜索"
            aria-label="搜索"
            role="button"
            @click="openSearch"
            @keydown.enter="openSearch"
          >
            <i class="icon icon-search">
              <IconSearch />
            </i>
          </a>
        </div>
      </div>
    </div>
    <div
      class="nav-close"
      role="button"
      aria-label="关闭"
      @click="closeMenu"
      @keydown.enter="closeMenu"
    ></div>

    <section class="page-wrapper">
      <slot></slot>

      <div class="nav-footer">
        <nav class="nav-wrapper" aria-label="Footer">
          <span class="nav-copy">{{ site.title }} &copy; {{ currentYear }}</span>
          <span class="nav-center">
            <span class="nav-mode">
              <a
                class="js-theme"
                href="#"
                @click.prevent="handleThemeClick"
                @keydown.enter.prevent="handleThemeClick"
                :data-system="'System'"
                :data-dark="'Dark'"
                :data-light="'Light'"
              >
                <span class="theme-icon"></span>
                <span class="theme-text">{{ themeText }}</span>
              </a>
            </span>
            <ul v-if="secondaryNavigation.length > 0">
              <li
                v-for="item in secondaryNavigation"
                :key="item.slug"
                :class="{ active: isActiveRoute(item.url) }"
              >
                <router-link :to="item.url">
                  <span>{{ item.label }}</span>
                </router-link>
              </li>
            </ul>
          </span>
          <span class="nav-publish">
            使用 <a href="https://vuejs.org">Vue</a> 和
            <a href="https://github.com/zutrinken/attila">Attila</a> 发布
          </span>
        </nav>
      </div>
    </section>

    <SearchModal :is-open="isSearchOpen" @close="closeSearch" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useTheme } from '@/composables/useTheme'
import { initTheme } from '@/utils/theme'
import type { Site, NavigationItem } from '@/types/blog'
import IconSearch from './icons/IconSearch.vue'
import IconMenu from './icons/IconMenu.vue'
import SearchModal from './SearchModal.vue'
import { useUserStore } from '@/stores/user'

const props = withDefaults(
  defineProps<{
    site?: Partial<Site>
    navigation?: NavigationItem[]
    secondaryNavigation?: NavigationItem[]
    member?: any
    hideSubscribeButton?: boolean
  }>(),
  {
    site: () => ({
      title: '我的博客',
      description: '',
      logo: '',
      cover_image: '',
      url: '/',
      locale: 'zh',
      members_enabled: true,
      members_invite_only: false,
    }),
    navigation: () => [],
    secondaryNavigation: () => [],
    member: null,
    hideSubscribeButton: false,
  }
)

const route = useRoute()
const router = useRouter()
const { currentTheme, toggleTheme } = useTheme()
const userStore = useUserStore()
const isMenuOpen = ref(false)
const isSearchOpen = ref(false)
const avatarLoadFailed = ref(false)

const currentYear = new Date().getFullYear()

const handleAvatarError = () => {
  avatarLoadFailed.value = true
}

const handleLogout = async () => {
  await userStore.logout()
  router.push('/')
}

const themeText = computed(() => {
  const themeMap: Record<string, string> = {
    system: 'System',
    light: 'Light',
    dark: 'Dark',
  }
  return themeMap[currentTheme.value] || 'System'
})

const isActiveRoute = (url: string): boolean => {
  return route.path === url || route.path.startsWith(url + '/')
}

const toggleMenu = () => {
  isMenuOpen.value = !isMenuOpen.value
  if (isMenuOpen.value) {
    document.body.classList.add('menu-active')
    document.documentElement.classList.add('menu-active')
  } else {
    closeMenu()
  }
}

const closeMenu = () => {
  isMenuOpen.value = false
  document.body.classList.remove('menu-active')
  document.documentElement.classList.remove('menu-active')
}

const openSearch = () => {
  isSearchOpen.value = true
  document.body.style.overflow = 'hidden'
}

const closeSearch = () => {
  isSearchOpen.value = false
  document.body.style.overflow = ''
}

const handleThemeClick = () => {
  toggleTheme()
}

onMounted(async () => {
  initTheme()
  await userStore.fetchCurrentUser()
})
</script>

<style scoped>
.nav-user-info {
  display: flex;
  align-items: center;
  margin-right: 0.5rem;
}

.nav-user-avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 3.2rem;
  height: 3.2rem;
  border-radius: 50%;
  overflow: hidden;
  background: var(--ghost-accent-color);
  color: #fff;
  font-weight: 600;
  font-size: 1.4rem;
  text-decoration: none;
  transition: all 0.2s ease;
}

.nav-user-avatar:hover {
  opacity: 0.9;
  transform: scale(1.05);
}

.nav-user-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.nav-user-name {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
}

.nav-logout {
  cursor: pointer;
}
</style>
