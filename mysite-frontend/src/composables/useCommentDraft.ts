// composables/useCommentDraft.ts
// 选区「引用」到评论框的通道（UI 重构 T10）：
//
// - draft：模块级单例。已登录用户在文章页选中文字点「引用」时写入，
//   CommentSection watch 消费一次（articleId 匹配才生效）。
// - sessionStorage 暂存：匿名用户点「引用」先跳登录，回跳文章页后
//   CommentSection 取出预填（读取即清，一次性语义，同 usePendingAsk）。

import { ref } from 'vue'

export interface CommentDraft {
  quote: string
  articleId: string
}

const draft = ref<CommentDraft | null>(null)

const PENDING_COMMENT_KEY = 'mysite_pending_comment'

interface PendingCommentPayload {
  quote: string
  articleId: string
}

export function setCommentDraft(d: CommentDraft | null) {
  draft.value = d
}

export function useCommentDraft() {
  return { draft, setCommentDraft }
}

export function stashCommentQuote(articleId: string, quote: string) {
  sessionStorage.setItem(
    PENDING_COMMENT_KEY,
    JSON.stringify({ quote, articleId } satisfies PendingCommentPayload),
  )
}

/** 取出并清除暂存引用（无论是否命中都清除，保证一次性）；articleId 不匹配返回 null */
export function takeCommentQuote(articleId: string): string | null {
  const raw = sessionStorage.getItem(PENDING_COMMENT_KEY)
  if (!raw) return null
  sessionStorage.removeItem(PENDING_COMMENT_KEY)
  try {
    const pending = JSON.parse(raw) as PendingCommentPayload
    const q = pending.quote?.trim()
    if (!q || pending.articleId !== articleId) return null
    return q
  } catch {
    return null
  }
}
