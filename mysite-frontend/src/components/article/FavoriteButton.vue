<script setup lang="ts">
import { computed, ref } from 'vue'
import { Heart, Loader2 } from 'lucide-vue-next'
import { useFavorite } from '@/composables/useFavorite'
import { useUserStore } from '@/stores/user'

const props = defineProps<{
  articleId: string
  initialFavorited?: boolean
  favoriteCount?: number
  size?: 'sm' | 'md' | 'lg'
  showCount?: boolean
}>()

const emit = defineEmits<{
  'toggle': [favorited: boolean]
  'login-required': []
}>()

const userStore = useUserStore()
const { isFavorited, setFavoriteStatus, toggleFavorite, isPending } = useFavorite()

if (props.initialFavorited !== undefined) {
  setFavoriteStatus(props.articleId, props.initialFavorited)
}

const favorited = computed(() => isFavorited(props.articleId))
const pending = computed(() => isPending(props.articleId))

const animating = ref(false)

const sizeClass = computed(() => {
  switch (props.size) {
    case 'sm': return 'w-7 h-7'
    case 'lg': return 'w-11 h-11'
    default: return 'w-9 h-9'
  }
})

const iconSize = computed(() => {
  switch (props.size) {
    case 'sm': return 14
    case 'lg': return 22
    default: return 18
  }
})

async function handleToggle() {
  if (!userStore.isLoggedIn) {
    emit('login-required')
    return
  }

  animating.value = true
  const result = await toggleFavorite(props.articleId)

  if (result.success) {
    emit('toggle', result.favorited)
  }

  setTimeout(() => {
    animating.value = false
  }, 400)
}
</script>

<template>
  <button
    @click="handleToggle"
    class="inline-flex items-center gap-1.5 rounded-full transition-all duration-200 focus:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-accent)] dark:focus-visible:ring-[var(--color-dark-accent)]"
    :class="[
      sizeClass,
      favorited
        ? 'text-red-500 dark:text-red-400 hover:text-red-600 dark:hover:text-red-300'
        : 'text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)] hover:text-red-500 dark:hover:text-red-400',
      pending ? 'cursor-wait' : 'cursor-pointer'
    ]"
    :disabled="pending"
    :aria-label="favorited ? '取消收藏' : '收藏文章'"
  >
    <span class="relative" :class="{ 'animate-favorite-bounce': animating }">
      <Loader2 v-if="pending" :size="iconSize" class="animate-spin" />
      <Heart
        v-else
        :size="iconSize"
        :fill="favorited ? 'currentColor' : 'none'"
        :stroke-width="favorited ? 0 : 2"
        class="transition-all duration-200"
      />
    </span>
    <span
      v-if="showCount && favoriteCount !== undefined"
      class="text-xs font-medium tabular-nums"
    >
      {{ favoriteCount }}
    </span>
  </button>
</template>
