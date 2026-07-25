<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, RouterLink } from 'vue-router'
import { useHead } from '@unhead/vue'
import {
  User, Calendar, MapPin, Link, FileText, BookOpen,
  Heart, Loader2, ExternalLink, Eye, Edit3,
} from 'lucide-vue-next'
import { Pagination } from '@/components/ui'
import { useUserStore } from '@/stores/user'
import { getUserProfile, updateUser } from '@/api/user'
import { getFavoriteArticles } from '@/api/article'
import { getPaginated } from '@/api/client'
import { usePermission } from '@/composables/usePermission'
import { useToast } from '@/composables/useToast'
import { formatDate } from '@/utils/date'
import type { UserProfile } from '@/types'
import type { ArticleListItem } from '@/types'

const route = useRoute()
const userStore = useUserStore()
const { isAdmin } = usePermission()

const username = computed(() => route.params.username as string)
const loading = ref(true)
const error = ref('')

const profile = ref<UserProfile | null>(null)
type ProfileTab = 'articles' | 'collections' | 'favorites' | 'about'
const activeTab = ref<ProfileTab>('articles')

// 文章列表
const articles = ref<ArticleListItem[]>([])
const loadingArticles = ref(false)
const articlePage = ref(1)
const articleTotal = ref(0)

const isOwner = computed(() =>
  userStore.isLoggedIn && userStore.user?.username === username.value
)

// Owner or admin can view private tabs (favorites)
const canViewPrivate = computed(() => isOwner.value || isAdmin.value)

// Favorites list
const favorites = ref<ArticleListItem[]>([])
const loadingFavorites = ref(false)
const favoritePage = ref(1)
const favoriteTotal = ref(0)

const ROLE_LABEL: Record<string, string> = {
  'ADMIN': '管理员',
  'CREATOR': '创作者',
  'USER': '用户',
  'DEVELOPER': '开发者',
}

const ROLE_BADGE: Record<string, string> = {
  'ADMIN': 'bg-warning-subtle text-warning',
  'CREATOR': 'bg-accent-subtle text-accent',
  'USER': 'bg-info-subtle text-info',
  'DEVELOPER': 'bg-warning-subtle text-warning',
}

useHead(() => ({
  title: profile.value
    ? `${profile.value.realName || profile.value.username} - MySite`
    : `${username.value} - MySite`,
}))

async function fetchProfile() {
  loading.value = true
  error.value = ''
  try {
    profile.value = await getUserProfile(username.value)
    await fetchArticles(1)
  } catch (err: unknown) {
    const msg = (err as Error)?.message || ''
    if (msg.includes('用户') || msg.includes('USER_QUERY')) {
      error.value = '用户不存在'
    } else {
      error.value = msg || '加载失败'
    }
  } finally {
    loading.value = false
  }
}

async function fetchArticles(page: number = 1) {
  loadingArticles.value = true
  try {
    const result = await getPaginated<ArticleListItem>('/v1/articles', {
      current: page,
      size: 10,
      searchType: 'author',
      keyword: username.value,
      sortField: 'createTime',
      sortOrder: 'desc',
      published: 1,
    })
    articles.value = result.list
    articleTotal.value = result.pagination.total
    articlePage.value = page
  } catch {
    articles.value = []
  } finally {
    loadingArticles.value = false
  }
}

async function fetchFavorites(page: number = 1) {
  if (!canViewPrivate.value) return
  loadingFavorites.value = true
  try {
    const result = await getFavoriteArticles({
      page,
      size: 10,
      // Admin viewing another user's favorites: pass the userId
      userId: !isOwner.value && isAdmin.value && profile.value?.id
        ? String(profile.value.id)
        : undefined,
    })
    favorites.value = result.list
    favoriteTotal.value = result.pagination.total
    favoritePage.value = page
  } catch {
    favorites.value = []
  } finally {
    loadingFavorites.value = false
  }
}

// Bio editing
const editingBio = ref(false)
const bioText = ref('')
const savingBio = ref(false)
const { success, error: toastError } = useToast()

function startEditBio() {
  bioText.value = profile.value?.bio || ''
  editingBio.value = true
}

function cancelEditBio() {
  editingBio.value = false
  bioText.value = ''
}

async function saveBio() {
  if (!profile.value) return
  savingBio.value = true
  try {
    await updateUser({ bio: bioText.value })
    profile.value.bio = bioText.value
    success('个人介绍已更新')
    editingBio.value = false
  } catch (e: unknown) {
    toastError((e as Error).message || '保存失败')
  } finally {
    savingBio.value = false
  }
}

watch(username, () => {
  profile.value = null
  articles.value = []
  favorites.value = []
  fetchProfile()
})

// Auto-load favorites when switching to the favorites tab
watch(activeTab, (tab) => {
  if (tab === 'favorites' && canViewPrivate.value && favorites.value.length === 0) {
    fetchFavorites(1)
  }
})

onMounted(() => {
  fetchProfile()
})
</script>

<template>
  <div class="max-w-[1080px] mx-auto px-4 py-8">
    <!-- Loading -->
    <div v-if="loading" class="flex items-center justify-center py-20">
      <Loader2 :size="24" class="animate-spin text-text-muted" />
    </div>

    <!-- Error -->
    <div v-else-if="error" class="text-center py-20">
      <p class="text-lg text-text-primary mb-2">{{ error }}</p>
      <RouterLink to="/" class="text-sm text-accent hover:underline">返回首页</RouterLink>
    </div>

    <!-- Full profile -->
    <div v-else-if="profile">
      <!-- Profile card -->
      <div class="card-solid rounded-[20px] p-8 md:p-10 mb-6 relative overflow-hidden">
        <!-- 顶部渐变条纹 -->
        <div class="absolute top-0 left-0 right-0 h-1 bg-gradient-to-r from-indigo-500 via-purple-500 to-pink-500" />
        <div class="flex flex-col sm:flex-row items-center sm:items-start gap-6">
          <!-- Avatar -->
          <div class="w-24 h-24 rounded-full bg-gradient-to-br from-indigo-500 to-purple-600 text-text-inverse flex items-center justify-center text-3xl font-bold shrink-0 overflow-hidden shadow-[0_0_0_4px_var(--bg-elevated),0_0_0_6px_var(--accent-subtle)]">
            <img v-if="profile.avatar" :src="profile.avatar" :alt="profile.realName" class="w-full h-full object-cover" />
            <span v-else>{{ (profile.realName || profile.username).charAt(0).toUpperCase() }}</span>
          </div>

          <!-- Info -->
          <div class="flex-1 text-center sm:text-left">
            <div class="flex flex-col sm:flex-row sm:items-center gap-2 mb-1">
              <h1 class="text-2xl font-bold text-text-primary">{{ profile.realName || profile.username }}</h1>
              <span
                class="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium"
                :class="ROLE_BADGE[profile.role] || 'bg-info-subtle text-info'"
              >
                {{ ROLE_LABEL[profile.role] || profile.role }}
              </span>
            </div>
            <span class="inline-flex items-center font-mono text-xs text-text-muted bg-bg-secondary rounded-full px-2.5 py-0.5 mb-3">@{{ profile.username }}</span>

            <p v-if="profile.bio" class="text-text-secondary text-sm leading-relaxed mb-3 max-w-md">
              {{ profile.bio }}
            </p>

            <div class="flex flex-wrap justify-center sm:justify-start gap-4 text-xs text-text-muted">
              <span v-if="profile.createTime" class="inline-flex items-center gap-1">
                <Calendar :size="12" />
                {{ formatDate(profile.createTime) }} 加入
              </span>
              <span v-if="profile.location" class="inline-flex items-center gap-1">
                <MapPin :size="12" />
                {{ profile.location }}
              </span>
              <a
                v-if="profile.website"
                :href="profile.website"
                target="_blank"
                rel="noopener"
                class="inline-flex items-center gap-1 text-accent hover:underline"
              >
                <Link :size="12" />
                {{ profile.website.replace(/^https?:\/\//, '') }}
                <ExternalLink :size="10" />
              </a>
            </div>
          </div>
        </div>

        <!-- Stats -->
        <div class="grid grid-cols-2 sm:grid-cols-4 divide-x divide-border mt-7 pt-6 border-t border-border">
          <div class="text-center">
            <div class="text-[22px] font-bold text-text-primary tabular-nums tracking-tight">{{ profile.articleCount }}</div>
            <div class="text-[13px] text-text-muted mt-0.5">文章</div>
          </div>
          <div class="text-center">
            <div class="text-[22px] font-bold text-text-primary tabular-nums tracking-tight">{{ profile.collectionCount }}</div>
            <div class="text-[13px] text-text-muted mt-0.5">合集</div>
          </div>
          <div class="text-center">
            <div class="text-[22px] font-bold text-text-primary tabular-nums tracking-tight">{{ profile.likeCount }}</div>
            <div class="text-[13px] text-text-muted mt-0.5">获赞</div>
          </div>
          <div class="text-center">
            <div class="text-[22px] font-bold text-text-primary tabular-nums tracking-tight">{{ profile.favoriteCount }}</div>
            <div class="text-[13px] text-text-muted mt-0.5">被收藏</div>
          </div>
        </div>
      </div>

      <!-- Tabs -->
      <div class="border-b border-border mb-6">
        <nav class="flex gap-0 -mb-px">
          <button
            v-for="tab in [
              { key: 'articles', label: '文章', icon: FileText },
              { key: 'collections', label: '合集', icon: BookOpen },
              { key: 'favorites', label: '收藏', icon: Heart, private: true },
              { key: 'about', label: '关于', icon: User },
            ]"
            v-show="!tab.private || canViewPrivate"
            :key="tab.key"
            class="flex items-center gap-1.5 px-4 py-3 text-[15px] font-medium transition-colors border-b-2"
            :class="activeTab === tab.key
              ? 'border-accent text-accent'
              : 'border-transparent text-text-muted hover:text-text-secondary'"
            @click="activeTab = tab.key as ProfileTab"
          >
            <component :is="tab.icon" :size="14" />
            {{ tab.label }}
          </button>
        </nav>
      </div>

      <!-- Tab content -->
      <div v-if="activeTab === 'articles'">
        <div v-if="loadingArticles" class="flex justify-center py-8">
          <Loader2 :size="20" class="animate-spin text-text-muted" />
        </div>
        <div v-else-if="articles.length === 0" class="text-center py-8">
          <FileText :size="32" class="mx-auto mb-3 text-text-muted/40" />
          <p class="text-text-muted text-sm">暂无文章</p>
        </div>
        <div v-else>
          <!-- 分隔线式文章列表 -->
          <RouterLink
            v-for="a in articles"
            :key="a.id"
            :to="`/post/${a.id}`"
            class="group flex items-stretch gap-7 py-7 border-b border-border last:border-b-0"
          >
            <div class="flex-1 min-w-0">
              <h3 class="text-[19px] font-semibold leading-snug tracking-tight text-text-primary line-clamp-2 group-hover:text-accent transition-colors">{{ a.title }}</h3>
              <p v-if="a.summary" class="mt-2 text-sm leading-relaxed text-text-secondary line-clamp-2">{{ a.summary }}</p>
              <div class="mt-3.5 flex items-center gap-4 text-[13px] text-text-muted">
                <span v-if="a.categoryName" class="inline-flex items-center rounded-md bg-accent-subtle px-2 py-0.5 text-xs font-medium text-accent">{{ a.categoryName }}</span>
                <span class="inline-flex items-center gap-1"><Eye :size="14" /> {{ a.viewCount }}</span>
                <span class="inline-flex items-center gap-1"><Heart :size="14" /> {{ a.favoriteCount }}</span>
                <span>{{ a.createTime ? new Date(a.createTime).toLocaleDateString('zh-CN') : '' }}</span>
              </div>
            </div>
            <!-- 封面缩略图：有封面用封面图，否则渐变占位 -->
            <div class="hidden sm:flex w-[200px] h-[100px] shrink-0 rounded-lg overflow-hidden shadow-sm">
              <img v-if="a.coverImage" :src="a.coverImage" :alt="a.title" class="w-full h-full object-cover" />
              <div v-else class="w-full h-full bg-gradient-to-br from-indigo-500 via-purple-500 to-pink-500 flex items-center justify-center px-4 text-center text-[13px] font-bold leading-snug text-white/90 line-clamp-2">
                {{ a.title }}
              </div>
            </div>
          </RouterLink>
          <!-- Pagination -->
          <Pagination
            :current="articlePage"
            :total="articleTotal"
            :page-size="10"
            @update:current="fetchArticles"
          />
        </div>
      </div>
      <div v-else-if="activeTab === 'collections'" class="space-y-4">
        <p class="text-text-muted text-center py-8">合集列表将在此显示</p>
      </div>
      <div v-else-if="activeTab === 'favorites' && canViewPrivate">
        <!-- Load on first open -->
        <template v-if="favorites.length === 0 && !loadingFavorites && favoriteTotal === 0">
          <!-- Triggered: call fetch -->
        </template>

        <div v-if="loadingFavorites" class="flex justify-center py-8">
          <Loader2 :size="20" class="animate-spin text-text-muted" />
        </div>
        <div v-else-if="favorites.length === 0" class="text-center py-8">
          <Heart :size="32" class="mx-auto mb-3 text-text-muted/40" />
          <p class="text-text-muted text-sm">暂无收藏</p>
        </div>
        <div v-else>
          <RouterLink
            v-for="a in favorites"
            :key="a.id"
            :to="`/post/${a.id}`"
            class="group flex items-stretch gap-7 py-7 border-b border-border last:border-b-0"
          >
            <div class="flex-1 min-w-0">
              <h3 class="text-[19px] font-semibold leading-snug tracking-tight text-text-primary line-clamp-2 group-hover:text-accent transition-colors">{{ a.title }}</h3>
              <p v-if="a.summary" class="mt-2 text-sm leading-relaxed text-text-secondary line-clamp-2">{{ a.summary }}</p>
              <div class="mt-3.5 flex items-center gap-4 text-[13px] text-text-muted">
                <span v-if="a.categoryName" class="inline-flex items-center rounded-md bg-accent-subtle px-2 py-0.5 text-xs font-medium text-accent">{{ a.categoryName }}</span>
                <span class="inline-flex items-center gap-1"><Eye :size="14" /> {{ a.viewCount }}</span>
                <span class="inline-flex items-center gap-1"><Heart :size="14" /> {{ a.favoriteCount }}</span>
                <span>{{ a.createTime ? new Date(a.createTime).toLocaleDateString('zh-CN') : '' }}</span>
              </div>
            </div>
            <div class="hidden sm:flex w-[200px] h-[100px] shrink-0 rounded-lg overflow-hidden shadow-sm">
              <img v-if="a.coverImage" :src="a.coverImage" :alt="a.title" class="w-full h-full object-cover" />
              <div v-else class="w-full h-full bg-gradient-to-br from-indigo-500 via-purple-500 to-pink-500 flex items-center justify-center px-4 text-center text-[13px] font-bold leading-snug text-white/90 line-clamp-2">
                {{ a.title }}
              </div>
            </div>
          </RouterLink>
          <Pagination
            :current="favoritePage"
            :total="favoriteTotal"
            :page-size="10"
            @update:current="fetchFavorites"
          />
        </div>
      </div>
      <div v-else-if="activeTab === 'about'" class="space-y-4">
        <div class="card-solid p-6">
          <!-- View mode -->
          <div v-if="!editingBio" class="relative">
            <p v-if="profile.bio" class="text-text-secondary leading-relaxed whitespace-pre-wrap pr-8">{{ profile.bio }}</p>
            <p v-else class="text-text-muted">这位用户还没有填写个人介绍。</p>
            <button
              v-if="isOwner"
              @click="startEditBio"
              class="absolute top-0 right-0 p-1.5 rounded-lg text-text-muted hover:text-accent hover:bg-accent-subtle transition-colors"
              title="编辑个人介绍"
            >
              <Edit3 :size="14" />
            </button>
          </div>
          <!-- Edit mode -->
          <div v-else>
            <textarea
              v-model="bioText"
              rows="4"
              maxlength="500"
              class="w-full px-3 py-2.5 rounded-lg border border-border bg-bg-primary text-sm text-text-primary placeholder:text-text-muted/60 focus:outline-none focus:ring-2 focus:ring-accent/20 focus:border-accent/40 transition-shadow resize-none"
              placeholder="介绍一下自己..."
            />
            <div class="flex items-center justify-between mt-2">
              <span class="text-xs text-text-muted">{{ bioText.length }}/500</span>
              <div class="flex items-center gap-2">
                <button @click="cancelEditBio" class="btn-secondary text-xs">取消</button>
                <button
                  @click="saveBio"
                  :disabled="savingBio"
                  class="btn-primary text-xs flex items-center gap-1.5"
                >
                  <Loader2 v-if="savingBio" :size="12" class="animate-spin" />
                  保存
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
