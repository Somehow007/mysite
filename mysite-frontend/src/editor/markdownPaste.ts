import { editorViewCtx } from '@milkdown/kit/core'
import type { Ctx } from '@milkdown/kit/ctx'
import { markdownToSlice } from '@milkdown/kit/utils'

/**
 * 「代码视图复制」粘贴修正。
 *
 * 问题：从 IDE（VS Code / IntelliJ 等）复制文本时，剪贴板在 text/plain 之外
 * 还带一份 text/html——内容是 <pre> 或等宽字体样式包裹的源码。Milkdown 默认
 * 优先用 text/html 解析，导致整篇 Markdown 被塞进一个代码块。
 *
 * 策略：在容器上以捕获阶段监听 paste（先于 ProseMirror 自身的处理器），
 * 若剪贴板 HTML 具备代码视图特征且其文本与 text/plain 一致（即 HTML 没有
 * 携带任何真实格式信息），则拦截并改用 Markdown 解析插入；否则放行默认行为。
 */

/** 判断 text/html 是否为「代码视图复制」产物 */
function isCodeViewerHtml(html: string, plainText: string): boolean {
  const doc = new DOMParser().parseFromString(html, 'text/html')
  const htmlText = doc.body.textContent ?? ''
  // HTML 纯文本化后与 text/plain 不一致 → 携带了真实富文本内容，放行。
  // 注意按空白归一化比较：VS Code 用 <div> 分行，textContent 不含换行。
  const strip = (s: string) => s.replace(/\s+/g, '')
  if (strip(htmlText) !== strip(plainText)) return false

  // 代码视图特征一：<pre> 包裹（IntelliJ / 各类 raw 代码页）
  if (doc.querySelector('pre')) return true

  // 代码视图特征二：等宽字体或保留空白样式（VS Code 语法高亮复制）
  return Array.from(doc.body.querySelectorAll('[style]')).some((el) => {
    const style = (el.getAttribute('style') ?? '').toLowerCase()
    return (
      style.includes('monospace') ||
      style.includes('consolas') ||
      style.includes('menlo') ||
      style.includes('monaco') ||
      style.includes('courier') ||
      style.includes('white-space: pre')
    )
  })
}

/**
 * 在编辑器容器上挂载粘贴守卫，返回卸载函数。
 * 必须在 Crepe create() 之后调用（需要可用的 ctx / view）。
 */
export function attachMarkdownPasteGuard(root: HTMLElement, ctx: Ctx): () => void {
  const onPaste = (event: ClipboardEvent) => {
    const cd = event.clipboardData
    if (!cd) return
    const html = cd.getData('text/html')
    const text = cd.getData('text/plain')
    if (!html || !text) return // 纯文本粘贴 Milkdown 本就会按 Markdown 解析
    if (!isCodeViewerHtml(html, text)) return

    event.preventDefault()
    event.stopPropagation()

    const view = ctx.get(editorViewCtx)
    const slice = markdownToSlice(text)(ctx)
    view.dispatch(view.state.tr.replaceSelection(slice).scrollIntoView())
  }

  root.addEventListener('paste', onPaste, true)
  return () => root.removeEventListener('paste', onPaste, true)
}
