<script setup lang="ts">
import { Palette, Sun, Moon, Check } from 'lucide-vue-next'
import { useTheme, THEME_REGISTRY, type ThemeId } from '@/composables/useTheme'
import { Dropdown } from '@/components/ui'

const { currentTheme, setTheme, toggleDarkMode, getThemeMeta } = useTheme()

const uniqueThemes = THEME_REGISTRY.filter(t => !t.id.includes('-dark') || t.group === 'glass')

function isThemeActive(theme: (typeof THEME_REGISTRY)[number]): boolean {
  if (currentTheme.value === theme.id) return true
  if (currentTheme.value === theme.darkVariant) {
    return !uniqueThemes.some(t => t.id === theme.darkVariant)
  }
  return false
}

function handleSelect(id: ThemeId, close: () => void) {
  setTheme(id)
  close()
}

function handleToggleDark() {
  toggleDarkMode()
}
</script>

<template>
  <Dropdown trigger="click" placement="bottom-end" :spacing="6">
    <template #trigger>
      <button
        class="p-2 rounded-lg text-text-muted hover:text-accent hover:bg-accent-subtle focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent/50 transition-all duration-200"
        :aria-label="`当前主题: ${getThemeMeta(currentTheme)?.name}，点击切换`"
        aria-haspopup="true"
      >
        <Palette :size="18" />
      </button>
    </template>
    <template #default="{ close }">
      <div role="menu" class="w-72 glass glass-sm rounded-xl py-2">
        <div class="px-3 py-2 border-b border-border mb-1">
          <p class="text-xs font-medium text-text-muted">选择主题</p>
        </div>

        <div class="px-2 py-1">
          <button
            @click="handleToggleDark()"
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
            @click="handleSelect(theme.id, close)"
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
  </Dropdown>
</template>
