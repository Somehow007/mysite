/**
 * 主题工具函数
 * 用于管理主题切换和持久化
 */

export type ThemeMode = 'light' | 'dark' | 'system'

const THEME_STORAGE_KEY = 'attila_theme'

/**
 * 获取当前主题模式
 */
export function getThemeMode(): ThemeMode {
  if (typeof window === 'undefined') return 'system'
  const stored = localStorage.getItem(THEME_STORAGE_KEY) as ThemeMode | null
  return stored || 'system'
}

/**
 * 设置主题模式
 */
export function setThemeMode(mode: ThemeMode): void {
  if (typeof window === 'undefined') return
  localStorage.setItem(THEME_STORAGE_KEY, mode)
  applyTheme(mode)
}

/**
 * 应用主题到文档
 */
export function applyTheme(mode: ThemeMode): void {
  if (typeof window === 'undefined') return

  const html = document.documentElement

  // 移除所有主题类
  html.classList.remove('theme-light', 'theme-dark')

  if (mode === 'system') {
    // 系统主题：移除类，让CSS媒体查询生效
    html.classList.remove('theme-light', 'theme-dark')
  } else if (mode === 'dark') {
    html.classList.add('theme-dark')
  } else {
    html.classList.add('theme-light')
  }
}

/**
 * 初始化主题（在应用启动时调用）
 */
export function initTheme(): void {
  if (typeof window === 'undefined') return

  const mode = getThemeMode()
  applyTheme(mode)
}

/**
 * 检测系统主题偏好
 */
export function getSystemTheme(): 'light' | 'dark' {
  if (typeof window === 'undefined') return 'light'
  return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
}

