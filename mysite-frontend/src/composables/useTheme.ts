/**
 * 主题切换组合式函数
 */
import { ref, watch, onMounted } from 'vue'
import { getThemeMode, setThemeMode, applyTheme, type ThemeMode } from '@/utils/theme'

export function useTheme() {
  const currentTheme = ref<ThemeMode>(getThemeMode())

  // 初始化主题
  onMounted(() => {
    applyTheme(currentTheme.value)
  })

  // 切换主题
  const changeTheme = (mode: ThemeMode) => {
    currentTheme.value = mode
    setThemeMode(mode)
  }

  // 循环切换主题：system -> light -> dark -> system
  const toggleTheme = () => {
    const modes: ThemeMode[] = ['system', 'light', 'dark']
    const currentIndex = modes.indexOf(currentTheme.value)
    const nextIndex = (currentIndex + 1) % modes.length
    const nextMode = modes[nextIndex]
    if (nextMode) {
      changeTheme(nextMode)
    }
  }

  // 监听系统主题变化
  if (typeof window !== 'undefined') {
    const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)')
    const handleChange = () => {
      if (currentTheme.value === 'system') {
        applyTheme('system')
      }
    }
    mediaQuery.addEventListener('change', handleChange)
  }

  return {
    currentTheme,
    changeTheme,
    toggleTheme,
  }
}

