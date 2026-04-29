<script setup lang="ts">
import { Sun, Moon, Monitor } from 'lucide-vue-next'
import { useTheme } from '@/composables/useTheme'

const { mode, toggleTheme } = useTheme()
</script>

<template>
  <button
    @click="toggleTheme"
    class="p-2 rounded-lg text-[var(--color-text-muted)] dark:text-[var(--color-dark-text-muted)] hover:text-[var(--color-accent)] dark:hover:text-[var(--color-dark-accent)] hover:bg-[var(--color-accent-light)] dark:hover:bg-[var(--color-dark-accent-light)] transition-all duration-200"
    :aria-label="`当前主题: ${mode === 'system' ? '跟随系统' : mode === 'dark' ? '暗色' : '亮色'}，点击切换`"
  >
    <Sun v-if="mode === 'light'" :size="18" />
    <Moon v-else-if="mode === 'dark'" :size="18" />
    <Monitor v-else :size="18" />
  </button>
</template>
