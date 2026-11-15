// composables/usePendingAsk.ts
// 未登录提问暂存：匿名用户在首页/文章页提交问题时先写 sessionStorage，
// 登录回跳后由来源页取出并交给 ChatWidget launcher 自动发送。
// payload.path 记录来源路由，跨页残留的暂存不会被错误消费（一次性语义）。

import { getItem } from '@/utils/storage'
import { useUserStore } from '@/stores/user'

const PENDING_ASK_KEY = 'mysite_pending_ask'

interface PendingAskPayload {
  question: string
  path?: string
}

export function stashPendingAsk(question: string, path: string) {
  sessionStorage.setItem(
    PENDING_ASK_KEY,
    JSON.stringify({ question, path } satisfies PendingAskPayload),
  )
}

/**
 * 取出并清除暂存问题（无论是否命中都清除，保证一次性）。
 * path 传入当前路由 fullPath；暂存带 path 且不匹配时返回 null。
 */
export function takePendingAsk(path?: string): string | null {
  const raw = sessionStorage.getItem(PENDING_ASK_KEY)
  if (!raw) return null
  sessionStorage.removeItem(PENDING_ASK_KEY)
  try {
    const pending = JSON.parse(raw) as PendingAskPayload
    const q = pending.question?.trim()
    if (!q) return null
    if (pending.path && path && pending.path !== path) return null
    return q
  } catch {
    return null
  }
}

/** 是否具备可发消息的登录态（userStore 可能还在初始化，token 兜底） */
export function hasAuthSession(): boolean {
  const userStore = useUserStore()
  return userStore.isLoggedIn || !!getItem<string>('access_token')
}
