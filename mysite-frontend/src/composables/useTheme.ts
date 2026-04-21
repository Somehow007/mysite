import { ref, watch } from 'vue'
import { usePreferredDark, useStorage } from '@vueuse/core'

type ThemeMode = 'light' | 'dark' | 'system'

const mode = useStorage<ThemeMode>('mysite_theme', 'system')
const preferredDark = usePreferredDark()
const isDark = ref(false)

function updateIsDark() {
  if (mode.value === 'system') {
    isDark.value = preferredDark.value
  } else {
    isDark.value = mode.value === 'dark'
  }
  if (typeof document !== 'undefined') {
    document.documentElement.classList.toggle('dark', isDark.value)
  }
}

watch([mode, preferredDark], updateIsDark, { immediate: true })

export function useTheme() {
  function setTheme(newMode: ThemeMode) {
    mode.value = newMode
  }

  function toggleTheme() {
    const modes: ThemeMode[] = ['light', 'dark', 'system']
    const currentIndex = modes.indexOf(mode.value)
    mode.value = modes[(currentIndex + 1) % modes.length]
  }

  return {
    mode,
    isDark,
    setTheme,
    toggleTheme,
  }
}
