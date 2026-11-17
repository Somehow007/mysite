<script setup lang="ts">
import { computed } from 'vue'
import { Sun, Moon, Check, SunMoon } from 'lucide-vue-next'
import { useTheme, THEME_REGISTRY, type ThemeId } from '@/composables/useTheme'

const emit = defineEmits<{ close: [] }>()

const { currentTheme, setTheme, followSystem, toggleDarkMode, getThemeMeta } = useTheme()

const uniqueThemes = THEME_REGISTRY.filter(t => !t.id.includes('-dark') || t.group === 'glass')

// 当前主题有明暗 variant 才显示直切行——无 variant 主题下 toggleDarkMode 是 no-op
const canToggleDark = computed(() => {
  const meta = getThemeMeta(currentTheme.value)
  return !!(meta?.darkVariant || meta?.lightVariant)
})

function isThemeActive(theme: (typeof THEME_REGISTRY)[number]): boolean {
  if (currentTheme.value === theme.id) return true
  if (currentTheme.value === theme.darkVariant) {
    return !uniqueThemes.some(t => t.id === theme.darkVariant)
  }
  return false
}

function handleSelect(id: ThemeId) {
  setTheme(id)
  emit('close')
}

function handleFollowSystem() {
  followSystem()
  emit('close')
}

function handleToggleDark() {
  toggleDarkMode()
}
</script>

<template>
  <div>
    <div class="px-2 py-1">
      <!-- 跟随系统：清掉手动选择存储，currentTheme 重算回系统解析（paper 对） -->
      <button
        @click="handleFollowSystem"
        role="menuitem"
        class="flex items-center gap-2.5 w-full px-2 py-2 text-sm text-text-secondary hover:bg-accent-subtle hover:text-accent transition-colors duration-150 rounded-md"
      >
        <SunMoon :size="15" class="text-accent" />
        <span>跟随系统</span>
      </button>
      <button
        v-if="canToggleDark"
        @click="handleToggleDark"
        role="menuitem"
        class="flex items-center gap-2.5 w-full px-2 py-2 text-sm text-text-secondary hover:bg-accent-subtle hover:text-accent transition-colors duration-150 rounded-md"
      >
        <Sun v-if="!currentTheme.includes('dark') && currentTheme !== 'aurora'" :size="15" class="text-accent" />
        <Moon v-else :size="15" class="text-accent" />
        <span>{{ currentTheme.includes('dark') || currentTheme === 'aurora' ? '切换亮色' : '切换暗色' }}</span>
      </button>
    </div>

    <div class="h-px bg-border mx-2 my-1" />

    <div class="grid grid-cols-2 gap-1.5 px-2 py-1">
      <button
        v-for="theme in uniqueThemes"
        :key="theme.id"
        @click="handleSelect(theme.id)"
        role="menuitem"
        :aria-current="isThemeActive(theme) ? 'true' : undefined"
        class="group flex flex-col items-center gap-1.5 p-2.5 rounded-lg border transition-all duration-200 hover:scale-[1.02]"
        :class="[
          isThemeActive(theme)
            ? 'border-accent bg-accent-subtle'
            : 'border-border hover:border-accent/40'
        ]"
      >
        <div
          class="w-10 h-10 rounded-lg border border-border-subtle flex items-center justify-center"
          :style="{ backgroundColor: theme.preview.bg }"
        >
          <div
            class="w-5 h-5 rounded-md"
            :style="{ backgroundColor: theme.preview.accent }"
          />
        </div>
        <span class="text-xs text-text-secondary group-hover:text-accent transition-colors duration-150 truncate w-full text-center">
          {{ theme.name }}
        </span>
        <Check
          v-if="isThemeActive(theme)"
          :size="12"
          class="text-accent"
        />
      </button>
    </div>
  </div>
</template>
