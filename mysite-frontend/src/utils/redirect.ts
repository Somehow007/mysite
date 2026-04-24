import { getItem, setItem, removeItem } from './storage'

const REDIRECT_KEY = 'redirect_url'
const REDIRECT_EXPIRY_KEY = 'redirect_expiry'
const REDIRECT_EXPIRY_TIME = 30 * 60 * 1000

function isValidRedirectUrl(url: string): boolean {
  if (!url || typeof url !== 'string') {
    return false
  }

  if (url.startsWith('//')) {
    return false
  }

  if (/^https?:\/\//i.test(url)) {
    try {
      const currentOrigin = window.location.origin
      const redirectUrl = new URL(url, currentOrigin)
      return redirectUrl.origin === currentOrigin
    } catch {
      return false
    }
  }

  if (url.includes('://')) {
    return false
  }

  if (url.includes('javascript:')) {
    return false
  }

  if (url.includes('data:')) {
    return false
  }

  if (url.includes('vbscript:')) {
    return false
  }

  try {
    const decoded = decodeURIComponent(url)
    if (decoded !== url && !isValidRedirectUrl(decoded)) {
      return false
    }
  } catch {
    return false
  }

  return true
}

export function setRedirectUrl(url: string): void {
  if (!isValidRedirectUrl(url)) {
    return
  }

  setItem(REDIRECT_KEY, url)
  setItem(REDIRECT_EXPIRY_KEY, Date.now() + REDIRECT_EXPIRY_TIME)
}

export function getRedirectUrl(): string | null {
  const expiry = getItem<number>(REDIRECT_EXPIRY_KEY)

  if (expiry && Date.now() > expiry) {
    clearRedirectUrl()
    return null
  }

  const url = getItem<string>(REDIRECT_KEY)

  if (url && !isValidRedirectUrl(url)) {
    clearRedirectUrl()
    return null
  }

  return url
}

export function clearRedirectUrl(): void {
  removeItem(REDIRECT_KEY)
  removeItem(REDIRECT_EXPIRY_KEY)
}

export function getRedirectUrlFromQuery(): string | null {
  const urlParams = new URLSearchParams(window.location.search)
  const redirect = urlParams.get('redirect')

  if (redirect && isValidRedirectUrl(redirect)) {
    return redirect
  }

  return null
}
