<script setup lang="ts">
// 移动端文章底部工具栏（T9）—— 概念稿 .dock：
// 目录（已读 %）/ ✦ 问这篇（accent 底）/ 收藏（数量）。
// <lg 显示（与 ≥lg 的右栏互补）；样式在 styles/post.css。
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { Heart, Loader2 } from 'lucide-vue-next'
import type { Article } from '@/types'
import type { TocItem } from '@/composables/useMarkdown'
import { useFavorite } from '@/composables/useFavorite'
import { useToast } from '@/composables/useToast'
import { useChatLauncher } from '@/composables/useChatLauncher'
import { useUserStore } from '@/stores/user'
import MobileTocDrawer from './MobileTocDrawer.vue'

const props = defineProps<{
  items: TocItem[]
  /** 页面级已读进度（0-100） */
  readPct: number
  article: Article
}>()

const emit = defineEmits<{
  'toggle': [favorited: boolean]
  'login-required': []
}>()

const userStore = useUserStore()
const toast = useToast()
const launcher = useChatLauncher()
const { isFavorited, setFavoriteStatus, getFavoriteCount, setFavoriteCount, toggleFavorite, isPending } = useFavorite()

const tocOpen = ref(false)

// ---- 收藏（与 FavoriteButton 同路径：useFavorite + 缓存计数）----
const favorited = computed(() => isFavorited(props.article.id))
const favPending = computed(() => isPending(props.article.id))
const displayCount = ref(props.article.favoriteCount ?? 0)

// article 对象在页内切文章时会整体替换：重新同步收藏态与计数
watch(() => props.article, (a) => {
  if (a.isFavorited !== undefined) setFavoriteStatus(a.id, a.isFavorited)
  const cached = getFavoriteCount(a.id)
  displayCount.value = cached ?? a.favoriteCount ?? 0
}, { immediate: true })

// 刊头/文末按钮经 emit('toggle') 让父级原地 +1/-1 article.favoriteCount：
// 对象引用不变，须监听字段本身才能让 dock 计数与刊头同步
watch(() => props.article.favoriteCount, (v) => {
  if (v !== undefined) displayCount.value = v
})

async function handleFavorite() {
  if (!userStore.isLoggedIn) {
    emit('login-required')
    return
  }
  const result = await toggleFavorite(props.article.id)
  if (result.success) {
    emit('toggle', result.favorited)
    if (result.favoriteCount !== undefined) {
      displayCount.value = result.favoriteCount
      setFavoriteCount(props.article.id, result.favoriteCount)
    }
    toast.success(result.favorited ? '收藏成功' : '已取消收藏')
  } else if (result.message) {
    toast.error(result.message)
  }
}

// ---- 问这篇：打开聊天 sheet（articleId 由 ChatWidget 按路由自动注入）----
function handleAsk() {
  launcher.open()
}

// ---- body 留白类：把页脚/文末推出 dock 覆盖区（post.css 消费，<lg 生效）----
onMounted(() => {
  document.body.classList.add('has-post-dock')
})

onUnmounted(() => {
  document.body.classList.remove('has-post-dock')
})
</script>

<template>
  <!-- 底部工具栏：<lg 显示（样式 post.css .post-dock） -->
  <div class="post-dock" role="toolbar" aria-label="文章工具栏">
    <button
      type="button"
      class="post-dock-btn"
      :disabled="items.length === 0"
      aria-label="打开文章目录"
      @click="tocOpen = true"
    >
      <span class="post-dock-label">目录</span>
      <small class="post-dock-sub">已读 {{ readPct }}%</small>
    </button>
    <button
      type="button"
      class="post-dock-btn post-dock-ask"
      aria-label="问这篇文章"
      @click="handleAsk"
    >
      <span class="post-dock-label">✦ 问这篇</span>
      <small class="post-dock-sub">AI 助手</small>
    </button>
    <button
      type="button"
      class="post-dock-btn"
      :class="{ 'is-faved': favorited }"
      :disabled="favPending"
      :aria-label="favorited ? '取消收藏' : '收藏文章'"
      @click="handleFavorite"
    >
      <span class="post-dock-label post-dock-fav">
        <Loader2 v-if="favPending" :size="15" class="animate-spin" />
        <Heart
          v-else
          :size="15"
          :fill="favorited ? 'currentColor' : 'none'"
          :stroke-width="favorited ? 0 : 2"
        />
      </span>
      <small class="post-dock-sub">{{ displayCount }}</small>
    </button>
  </div>

  <!-- 目录底部 sheet（受控） -->
  <MobileTocDrawer v-model:open="tocOpen" :items="items" :read-pct="readPct" />
</template>
