/**
 * Markdown Live Preview —— 基于 @lezer/markdown 官方语法树驱动
 *
 * 重写自正则解析方案，核心改变：
 *   1. 放弃自写正则 + computeBlocks，改用 syntaxTree(state).iterate()
 *   2. 只隐藏标记 token（HeaderMark/EmphasisMark 等），永不覆盖整行
 *   3. 所有 widget 实现 ignoreEvent/eq/updateDOM
 *   4. 提供 atomicRanges facet，光标自动跳过 replace 区间
 *
 * 参考：
 *   - https://codemirror.net/examples/decoration/ （官方装饰范式）
 *   - Obsidian Live Preview（同款语法树驱动方案）
 *
 * 安全不变量（从历次崩溃中总结）：
 *   - 绝不对整行使用 Decoration.replace（只隐藏标记 token）
 *   - Decoration.line 与 Decoration.replace 可安全共存（replace 不覆盖整行）
 *   - 所有 widget 实现 ignoreEvent() 返回 false 让事件穿透
 */

import {
  ViewPlugin,
  ViewUpdate,
  Decoration,
  WidgetType,
  EditorView,
  type DecorationSet,
} from '@codemirror/view'
import { syntaxTree } from '@codemirror/language'
import type { EditorState, Range } from '@codemirror/state'
import katex from 'katex'

// ═══════════════════════════════════════════════════════════════
// Callout 业务配置（保留自旧实现）
// ═══════════════════════════════════════════════════════════════
const CALLOUT_COLORS: Record<string, string> = {
  NOTE: '#448aff', INFO: '#448aff', TODO: '#448aff',
  TIP: '#00c853', SUCCESS: '#00c853', CHECK: '#00c853', DONE: '#00c853',
  WARNING: '#ff9100', CAUTION: '#ff9100', QUESTION: '#ff9100', ATTENTION: '#ff9100',
  ERROR: '#ff1744', DANGER: '#ff1744', FAILURE: '#ff1744', BUG: '#ff1744',
  EXAMPLE: '#7c4dff', QUOTE: '#9e9e9e', CITE: '#9e9e9e', ABSTRACT: '#9e9e9e', SUMMARY: '#9e9e9e', TLDR: '#9e9e9e',
}

const CALLOUT_ICONS: Record<string, string> = {
  NOTE: '📝', INFO: 'ℹ️', TODO: '☑️',
  TIP: '💡', SUCCESS: '✅', CHECK: '✔️', DONE: '🏁',
  WARNING: '⚠️', CAUTION: '⚠️', QUESTION: '❓', ATTENTION: '👀',
  ERROR: '❌', DANGER: '⚡', FAILURE: '🚫', BUG: '🐛',
  EXAMPLE: '📋', QUOTE: '💬', CITE: '📖', ABSTRACT: '📄', SUMMARY: '📊', TLDR: '⚡',
}

// ═══════════════════════════════════════════════════════════════
// Widgets —— 全部实现 ignoreEvent/eq/updateDOM
// ═══════════════════════════════════════════════════════════════
//
// CURSOR/CLICK SAFETY (CM6 官方推荐):
//   - ignoreEvent() 返回 false：让鼠标/键盘事件穿透到 CM6 编辑器
//   - eq()：判断 widget 等价性，避免不必要的 DOM 重建
//   - updateDOM()：原位更新 DOM，避免重建丢失状态（如 KaTeX 字体）

class BulletWidget extends WidgetType {
  eq(): boolean { return true }
  ignoreEvent(): boolean { return false }
  toDOM() {
    const s = document.createElement('span')
    s.className = 'cm-lp-bullet'
    s.textContent = '•'
    s.style.cssText = 'margin-right:0.5em;color:var(--text-muted)'
    return s
  }
}

class TaskCheckboxWidget extends WidgetType {
  constructor(private checked: boolean) { super() }
  eq(other: WidgetType): boolean {
    return other instanceof TaskCheckboxWidget && this.checked === other.checked
  }
  ignoreEvent(): boolean { return false }
  toDOM() {
    const s = document.createElement('span')
    s.className = 'cm-lp-task' + (this.checked ? ' checked' : '')
    s.innerHTML = `<input type="checkbox" ${this.checked ? 'checked ' : ''}disabled tabindex="-1">`
    s.style.cssText = 'margin-right:0.5em'
    return s
  }
}

class CalloutHeaderWidget extends WidgetType {
  constructor(private cType: string, private color: string, private icon: string) { super() }
  eq(other: WidgetType): boolean {
    return other instanceof CalloutHeaderWidget &&
      this.cType === other.cType && this.color === other.color && this.icon === other.icon
  }
  ignoreEvent(): boolean { return false }
  toDOM() {
    const s = document.createElement('span')
    s.className = 'cm-lp-callout-hdr'
    s.style.cssText = 'display:inline-flex;align-items:center;gap:0.5rem;font-family:var(--font-sans,sans-serif)'
    const iconSpan = document.createElement('span')
    iconSpan.style.cssText = `font-size:1rem;line-height:1;flex-shrink:0;color:${this.color}`
    iconSpan.textContent = this.icon
    const titleSpan = document.createElement('span')
    titleSpan.className = 'cm-lp-callout-title'
    titleSpan.style.cssText = 'font-weight:600;font-size:0.9375rem;color:var(--text-primary)'
    titleSpan.textContent = this.cType
    s.appendChild(iconSpan)
    s.appendChild(titleSpan)
    return s
  }
}

class KaTeXInlineWidget extends WidgetType {
  constructor(private latex: string) { super() }
  eq(other: WidgetType): boolean {
    return other instanceof KaTeXInlineWidget && this.latex === other.latex
  }
  ignoreEvent(): boolean { return false }
  // 原位更新 DOM，避免重建导致 KaTeX 重复渲染丢失字体加载状态
  updateDOM(dom: HTMLElement): boolean {
    try {
      katex.render(this.latex, dom, { throwOnError: false, displayMode: false, strict: false })
      return true
    } catch {
      return false
    }
  }
  toDOM() {
    const s = document.createElement('span')
    s.className = 'cm-lp-katex-inline'
    s.style.cssText = 'display:inline;vertical-align:middle;font-size:1.1em;line-height:1.8'
    try {
      katex.render(this.latex, s, { throwOnError: false, displayMode: false, strict: false })
    } catch {
      s.textContent = `$${this.latex}$`
    }
    return s
  }
}

class KaTeXDisplayWidget extends WidgetType {
  constructor(private latex: string) { super() }
  eq(other: WidgetType): boolean {
    return other instanceof KaTeXDisplayWidget && this.latex === other.latex
  }
  ignoreEvent(): boolean { return false }
  get estimatedHeight() { return 40 }
  updateDOM(dom: HTMLElement): boolean {
    try {
      katex.render(this.latex, dom, { throwOnError: false, displayMode: true, strict: false })
      return true
    } catch {
      return false
    }
  }
  toDOM() {
    const d = document.createElement('div')
    d.className = 'cm-lp-katex-display'
    d.style.cssText = 'text-align:center;padding:0.5em 0;overflow-x:auto;overflow-y:visible;margin:1.5em 0 2.2em'
    try {
      katex.render(this.latex, d, { throwOnError: false, displayMode: true, strict: false })
    } catch {
      d.textContent = `$$${this.latex}$$`
    }
    return d
  }
}

// ═══════════════════════════════════════════════════════════════
// 辅助函数
// ═══════════════════════════════════════════════════════════════

/**
 * 判断选区是否与给定范围相交。
 *
 * 光标在标记 token 上时不隐藏该 token，保证用户能编辑原始标记。
 * 这是 Obsidian Live Preview 的核心交互模式。
 */
function selectionTouches(state: EditorState, from: number, to: number): boolean {
  for (const r of state.selection.ranges) {
    if (r.from <= to && r.to >= from) return true
  }
  return false
}

/**
 * 解析 callout 类型标记 "> [!TYPE]"。
 * 返回 [类型, 标记结束位置] 或 null。
 */
function parseCalloutMarker(text: string, from: number): [string, number] | null {
  const m = text.match(/^>\s*\[!(\w+)\]\s*/)
  if (!m) return null
  return [m[1]!.toUpperCase(), from + m[0].length]
}

// ═══════════════════════════════════════════════════════════════
// 装饰构建 —— 基于语法树遍历
// ═══════════════════════════════════════════════════════════════

/**
 * 构建 markdown live preview 装饰。
 *
 * 核心策略（只隐藏标记 token，永不覆盖整行）：
 *   - HeaderMark (#) -> replace 隐藏
 *   - EmphasisMark (星号/下划线/波浪号) -> replace 隐藏
 *   - CodeMark (反引号) -> replace 隐藏
 *   - LinkMark (方括号/圆括号) -> replace 隐藏
 *   - QuoteMark (大于号) -> replace 隐藏
 *   - ListMark (减号/星号/数字点) -> replace 隐藏 + widget bullet
 *   - ATXHeading1-6 -> line 样式
 *   - Blockquote -> line 样式
 *   - FencedCode -> line 样式
 *   - TaskMarker -> replace + widget checkbox
 *   - 数学公式 $...$ / $$...$$ -> replace + widget KaTeX
 *
 * 光标安全：所有 replace 只覆盖标记 token（几个字符），永不覆盖整行，
 * 从根源消除"整行 replace + line decoration 共存"的崩溃隐患。
 */
function buildDecorations(view: EditorView): DecorationSet {
  const decos: Range<Decoration>[] = []
  const { state } = view

  for (const { from, to } of view.visibleRanges) {
    syntaxTree(state).iterate({
      from,
      to,
      enter: (node) => {
        const name = node.name
        const nodeFrom = node.from
        const nodeTo = node.to

        // ── 隐藏标记 token（光标不在其上时）──
        if (isMarkToken(name)) {
          if (!selectionTouches(state, nodeFrom, nodeTo)) {
            decos.push(Decoration.replace({}).range(nodeFrom, nodeTo))
          }
          return
        }

        // ── 标题行样式 ──
        const headingMatch = name.match(/^ATXHeading(\d)$/) || name.match(/^SetextHeading(\d)$/)
        if (headingMatch) {
          const level = headingMatch[1]!
          decos.push(Decoration.line({
            attributes: { style: headingLineStyle(level) },
          }).range(nodeFrom))
          return
        }

        // ── 引用块行样式 ──
        if (name === 'Blockquote') {
          // 给引用块内的每行加样式
          const lineFrom = state.doc.lineAt(nodeFrom).from
          decos.push(Decoration.line({
            attributes: { style: 'border-left:3px solid var(--accent);padding-left:1rem;color:var(--text-muted);font-style:italic' },
          }).range(lineFrom))
          return
        }

        // ── 代码块行样式 ──
        if (name === 'FencedCode' || name === 'CodeBlock') {
          const startLine = state.doc.lineAt(nodeFrom)
          const endLine = state.doc.lineAt(nodeTo)
          for (let ln = startLine.number; ln <= endLine.number; ln++) {
            const line = state.doc.line(ln)
            decos.push(Decoration.line({
              attributes: { style: codeBlockLineStyle(ln === startLine.number, ln === endLine.number) },
            }).range(line.from))
          }
          return
        }

        // ── 列表项 bullet widget ──
        if (name === 'ListItem') {
          // 检查是否为有序列表
          const listMarkMatch = state.doc.sliceString(nodeFrom, Math.min(nodeFrom + 10, nodeTo)).match(/^(\d+\.|[-*+])\s/)
          if (listMarkMatch) {
            const isOrdered = /\d/.test(listMarkMatch[1]!)
            if (isOrdered) {
              const num = parseInt(listMarkMatch[1]!)
              decos.push(Decoration.widget({
                widget: new OrderedNumberWidget(num),
                side: -1,
              }).range(nodeFrom))
            } else {
              decos.push(Decoration.widget({
                widget: new BulletWidget(),
                side: -1,
              }).range(nodeFrom))
            }
          }
          return
        }

        // ── Task marker（GFM 任务列表）──
        if (name === 'TaskMarker') {
          const text = state.doc.sliceString(nodeFrom, nodeTo)
          const checked = /\[x\]/i.test(text)
          if (!selectionTouches(state, nodeFrom, nodeTo)) {
            decos.push(Decoration.replace({
              widget: new TaskCheckboxWidget(checked),
            }).range(nodeFrom, nodeTo))
          }
          return
        }

        // ── 数学公式（自定义节点，由 mathExtension 提供）──
        if (name === 'InlineMath' || name === 'BlockMath') {
          if (!selectionTouches(state, nodeFrom, nodeTo)) {
            const latex = state.doc.sliceString(nodeFrom, nodeTo)
            if (name === 'InlineMath') {
              decos.push(Decoration.replace({
                widget: new KaTeXInlineWidget(latex),
              }).range(nodeFrom, nodeTo))
            } else {
              decos.push(Decoration.replace({
                widget: new KaTeXDisplayWidget(latex),
                block: true,
              }).range(nodeFrom, nodeTo))
            }
          }
          return
        }
      },
    })
  }

  // 处理 callout（Obsidian 风格 [!TYPE] 标注）—— 需要扫描行
  // 因为 callout 不是标准 markdown，语法树不识别
  buildCalloutDecorations(view, decos)

  return Decoration.set(decos, true)
}

/**
 * 判断节点是否为标记 token（需要隐藏的符号）。
 */
function isMarkToken(name: string): boolean {
  return (
    name === 'HeaderMark' ||
    name === 'QuoteMark' ||
    name === 'ListMark' ||
    name === 'EmphasisMark' ||
    name === 'CodeMark' ||
    name === 'LinkMark' ||
    name === 'StrikethroughMark'
  )
}

function headingLineStyle(level: string): string {
  const sizes: Record<string, string> = {
    '1': '2rem',
    '2': '1.75rem',
    '3': '1.5rem',
    '4': '1.25rem',
    '5': '1.1rem',
    '6': '1rem',
  }
  const size = sizes[level] ?? '1rem'
  return `font-size:${size};font-weight:700;line-height:1.3;margin:1.5em 0 0.5em`
}

function codeBlockLineStyle(isFirst: boolean, isLast: boolean): string {
  const parts = [
    'background-color:var(--code-bg)',
    'border-left:1px solid var(--code-border)',
    'border-right:1px solid var(--code-border)',
    'padding-left:1.5rem',
    'padding-right:1.5rem',
    'font-family:var(--font-mono,ui-monospace,monospace)',
    'font-size:0.875rem',
    'line-height:1.7',
  ]
  if (isFirst) parts.push('border-top:1px solid var(--code-border)', 'border-top-left-radius:8px', 'border-top-right-radius:8px', 'padding-top:1.25rem')
  if (isLast) parts.push('border-bottom:1px solid var(--code-border)', 'border-bottom-left-radius:8px', 'border-bottom-right-radius:8px', 'padding-bottom:1.25rem')
  return parts.join(';')
}

/**
 * 构建 callout 装饰（Obsidian 风格 [!TYPE] 标注）。
 *
 * Callout 不是标准 markdown，语法树将其解析为 Blockquote。
 * 这里扫描可见行，识别 "> [!TYPE]" 模式并添加 widget。
 */
function buildCalloutDecorations(view: EditorView, decos: Range<Decoration>[]) {
  const { state } = view
  for (const { from, to } of view.visibleRanges) {
    const startLine = state.doc.lineAt(from)
    const endLine = state.doc.lineAt(to)
    for (let ln = startLine.number; ln <= endLine.number; ln++) {
      const line = state.doc.line(ln)
      const text = line.text
      const cm = parseCalloutMarker(text, line.from)
      if (cm) {
        const [cType, markerEnd] = cm
        const color = CALLOUT_COLORS[cType] ?? '#448aff'
        const icon = CALLOUT_ICONS[cType] ?? '📝'
        // 光标不在标记行时，替换标记为 widget
        if (!selectionTouches(state, line.from, markerEnd)) {
          decos.push(Decoration.replace({
            widget: new CalloutHeaderWidget(cType, color, icon),
          }).range(line.from, markerEnd))
          // callout 行样式
          decos.push(Decoration.line({
            attributes: { style: `border-left:3px solid ${color};padding-left:1rem;background:rgba(68,138,255,0.05)` },
          }).range(line.from))
        }
      }
    }
  }
}

// 有序列表数字 widget（需在 buildDecorations 中使用）
class OrderedNumberWidget extends WidgetType {
  constructor(private n: number) { super() }
  eq(other: WidgetType): boolean {
    return other instanceof OrderedNumberWidget && this.n === other.n
  }
  ignoreEvent(): boolean { return false }
  toDOM() {
    const s = document.createElement('span')
    s.className = 'cm-lp-ol'
    s.textContent = `${this.n}.`
    s.style.cssText = 'margin-right:0.5em;color:var(--text-muted);font-variant-numeric:tabular-nums'
    return s
  }
}

// ═══════════════════════════════════════════════════════════════
// ViewPlugin
// ═══════════════════════════════════════════════════════════════
//
// 增量更新策略（参考官方 Decoration 示例）：
//   - docChanged: 重建
//   - viewportChanged: 重建
//   - selectionSet: 重建（光标位置影响标记隐藏）
//   - 语法树变化: 重建
//
// atomicRanges: 将所有 replace 装饰注册为原子区间，光标移动时
// 自动跳过隐藏文本区域（CM6 官方推荐，解决"上键跳顶"根因）。

export function livePreview() {
  return ViewPlugin.fromClass(
    class {
      decorations: DecorationSet = Decoration.none

      constructor(view: EditorView) {
        this.decorations = buildDecorations(view)
      }

      update(update: ViewUpdate) {
        if (
          update.docChanged ||
          update.viewportChanged ||
          update.selectionSet ||
          syntaxTree(update.startState) !== syntaxTree(update.state)
        ) {
          this.decorations = buildDecorations(update.view)
        }
      }
    },
    {
      decorations: (v) => v.decorations,
      // ★ 核心修复：将所有 replace 装饰注册为 atomic ranges
      // CM6 官方推荐方案（参考 codemirror.net/examples/decoration/）
      // 光标移动时自动跳过 replace 区间，不会进入隐藏文本区域
      provide: (plugin) =>
        EditorView.atomicRanges.of((view) => {
          return view.plugin(plugin)?.decorations ?? Decoration.none
        }),
    },
  )
}

// ═══════════════════════════════════════════════════════════════
// 数学公式 Lezer 扩展
// ═══════════════════════════════════════════════════════════════
//
// @lezer/markdown 不内置数学公式支持，需要自定义扩展。
// 定义 InlineMath（$...$）和 BlockMath（$$...$$）节点。
//
// 参考 @lezer/markdown 的 Strikethrough 扩展实现。

import type { MarkdownConfig } from '@lezer/markdown'

export const mathExtension: MarkdownConfig = {
  defineNodes: [
    'InlineMath',
    'BlockMath',
    'MathMark',
  ],
  parseInline: [
    {
      name: 'InlineMath',
      parse(cx, next, pos) {
        // $...$ 行内数学公式（next 是 $ 的 charCode = 36）
        if (next !== 36 /* '$' */) return -1
        // 检查是否为 $$ 块级公式
        if (cx.char(pos + 1) === 36) return -1
        // 查找闭合 $
        let end = pos + 1
        while (end < cx.end) {
          if (cx.char(end) === 36 && cx.char(end + 1) !== 36) break
          end++
        }
        if (end >= cx.end) return -1
        // 创建 InlineMath 节点（包含 $ 符号）
        return cx.addElement(cx.elt('InlineMath', pos, end + 1))
      },
      after: 'Emphasis',
    },
    {
      name: 'BlockMath',
      parse(cx, next, pos) {
        // $$...$$ 块级数学公式
        if (next !== 36 /* '$' */ || cx.char(pos + 1) !== 36) return -1
        // 查找闭合 $$
        let end = pos + 2
        while (end < cx.end - 1) {
          if (cx.char(end) === 36 && cx.char(end + 1) === 36) break
          end++
        }
        if (end >= cx.end - 1) return -1
        // 创建 BlockMath 节点（包含 $$ 符号）
        return cx.addElement(cx.elt('BlockMath', pos, end + 2))
      },
      after: 'InlineMath',
    },
  ],
}
