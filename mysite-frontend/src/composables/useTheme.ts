import { ref, watch } from 'vue'
import { usePreferredDark, useStorage } from '@vueuse/core'

export type ThemeId =
  | 'classic-light'
  | 'classic-dark'
  | 'aurora'
  | 'rose-garden'
  | 'ocean-breeze'
  | 'warm-sunset'
  | 'liquid-glass'
  | 'liquid-glass-dark'

export interface ThemeMeta {
  id: ThemeId
  name: string
  group: string
  icon: string
  hasDarkVariant: boolean
  darkVariant?: ThemeId
  lightVariant?: ThemeId
  preview: { bg: string; accent: string }
}

export const THEME_REGISTRY: ThemeMeta[] = [
  {
    id: 'classic-light',
    name: '经典·白',
    group: 'classic',
    icon: 'sun',
    hasDarkVariant: true,
    darkVariant: 'classic-dark',
    preview: { bg: '#F8F9FA', accent: '#4F46E5' },
  },
  {
    id: 'classic-dark',
    name: '经典·黑',
    group: 'classic',
    icon: 'moon',
    hasDarkVariant: true,
    lightVariant: 'classic-light',
    preview: { bg: '#0B0F19', accent: '#818CF8' },
  },
  {
    id: 'aurora',
    name: '极光紫夜',
    group: 'aurora',
    icon: 'sparkles',
    hasDarkVariant: false,
    preview: { bg: '#080D0E', accent: '#A655F6' },
  },
  {
    id: 'rose-garden',
    name: '玫瑰花园',
    group: 'rose',
    icon: 'flower-2',
    hasDarkVariant: false,
    preview: { bg: '#FFF5F7', accent: '#FD7397' },
  },
  {
    id: 'ocean-breeze',
    name: '海风青韵',
    group: 'ocean',
    icon: 'waves',
    hasDarkVariant: false,
    preview: { bg: '#F0FFFE', accent: '#01B8C2' },
  },
  {
    id: 'warm-sunset',
    name: '暖阳珊瑚',
    group: 'sunset',
    icon: 'sunset',
    hasDarkVariant: false,
    preview: { bg: '#FFF8F0', accent: '#E88A5A' },
  },
  {
    id: 'liquid-glass',
    name: 'Liquid Glass',
    group: 'glass',
    icon: 'glass-water',
    hasDarkVariant: true,
    darkVariant: 'liquid-glass-dark',
    preview: { bg: '#F2F2F7', accent: '#007AFF' },
  },
  {
    id: 'liquid-glass-dark',
    name: 'Liquid Glass·暗',
    group: 'glass',
    icon: 'glass-water',
    hasDarkVariant: true,
    lightVariant: 'liquid-glass',
    preview: { bg: '#000000', accent: '#0A84FF' },
  },
]

const currentTheme = useStorage<ThemeId>('mysite_theme_v2', 'classic-light')
const preferredDark = usePreferredDark()
const isDark = ref(false)

function isThemeDark(id: ThemeId): boolean {
  return id.includes('dark') || id === 'aurora'
}

function applyTheme(id: ThemeId) {
  if (typeof document === 'undefined') return
  document.documentElement.setAttribute('data-theme', id)
  isDark.value = isThemeDark(id)
  document.documentElement.classList.toggle('dark', isDark.value)
  document.documentElement.style.colorScheme = isDark.value ? 'dark' : 'light'
}

function transitionTheme(id: ThemeId) {
  if (typeof document === 'undefined') return
  document.documentElement.classList.add('theme-transitioning')
  applyTheme(id)
  setTimeout(() => {
    document.documentElement.classList.remove('theme-transitioning')
  }, 400)
}

watch([currentTheme, preferredDark], () => {
  applyTheme(currentTheme.value)
}, { immediate: true })

export function useTheme() {
  function setTheme(id: ThemeId, transition = true) {
    if (transition) {
      transitionTheme(id)
    } else {
      applyTheme(id)
    }
    currentTheme.value = id
  }

  function toggleDarkMode() {
    const meta = THEME_REGISTRY.find(t => t.id === currentTheme.value)
    if (meta?.darkVariant) {
      setTheme(meta.darkVariant)
    } else if (meta?.lightVariant) {
      setTheme(meta.lightVariant)
    }
  }

  function getThemeMeta(id: ThemeId): ThemeMeta | undefined {
    return THEME_REGISTRY.find(t => t.id === id)
  }

  return {
    currentTheme,
    isDark,
    themeRegistry: THEME_REGISTRY,
    setTheme,
    toggleDarkMode,
    getThemeMeta,
  }
}
