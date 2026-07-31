import { wrapIn } from '@milkdown/kit/prose/commands'
import { InputRule } from '@milkdown/kit/prose/inputrules'
import { Selection } from '@milkdown/kit/prose/state'
import type { NodeViewConstructor } from '@milkdown/kit/prose/view'
import { $command, $inputRule, $node, $remark, $view } from '@milkdown/kit/utils'
import { getCalloutConfig, isCalloutType } from '@/utils/callouts'

/**
 * Callout 扩展（`> [!NOTE] 标题` → 带颜色/图标的卡片块）。
 *
 * Milkdown 标准三段式（见 docs/editor-redesign-plan.md §5.4）：
 * 1. remark 侧：解析时把首段匹配 `[!TYPE]` 的 blockquote 改写为 callout 节点；
 *    序列化时还原为 `> [!TYPE] 标题` 引用块（remark-stringify 会把 `[` 转义为
 *    `\[`，渲染管线 useMarkdown.ts 两种写法都能识别）。
 * 2. schema 侧：callout 节点（attrs: type/title，content: block+）。
 * 3. 视图侧：NodeView 渲染卡片外壳，头部（图标+标题输入框）不可编辑，
 *    正文区域仍为普通 ProseMirror 内容。
 *
 * 交互：斜杠菜单「标注 Callout」分组插入；在引用块首行输入 `[!TYPE] ` 自动转换。
 */

/* ── 1. remark 侧：blockquote → callout 的 AST 变换 ───────────────── */

/** 最小化的 mdast 节点形状（避免引入 mdast 类型依赖） */
interface MdNode {
  type: string
  value?: string
  children?: MdNode[]
  /** 变换后附加的 callout 元数据 */
  calloutType?: string
  calloutTitle?: string
}

const CALLOUT_START_RE = /^\[!([A-Za-z]+)\](?=[ \t\n]|$)/

/**
 * 若 blockquote 首段以 `[!TYPE]` 开头则原地改写为 callout 节点。
 * 标题 = `[!TYPE]` 后第一行文本；同段剩余软换行内容与后续块归入正文。
 */
function tryTransformBlockquote(node: MdNode): void {
  const first = node.children?.[0]
  if (!first || first.type !== 'paragraph') return
  const firstText = first.children?.[0]
  if (!firstText || firstText.type !== 'text' || !firstText.value) return

  const match = firstText.value.match(CALLOUT_START_RE)
  if (!match) return
  const type = match[1]!.toLowerCase()
  if (!isCalloutType(type)) return

  const rest = firstText.value.slice(match[0].length).replace(/^[ \t]/, '')
  const newline = rest.indexOf('\n')
  const title = (newline === -1 ? rest : rest.slice(0, newline)).trim()
  const bodyText = newline === -1 ? '' : rest.slice(newline + 1)

  // 从首段移除标记行；空文本节点一并清理。
  // 注意：Milkdown 会把软换行预处理成 break 节点，标题行结束处的 break
  // 会残留在正文开头（渲染为前导硬换行），必须一并移除。
  firstText.value = bodyText
  while (first.children!.length > 0) {
    const head = first.children![0]!
    if (head.type === 'break' || (head.type === 'text' && !head.value)) first.children!.shift()
    else break
  }
  if (first.children!.length === 0) node.children!.shift()
  // callout 的 content 是 block+，空内容补一个空段落兜底
  if (node.children!.length === 0) node.children!.push({ type: 'paragraph', children: [] })

  node.type = 'callout'
  node.calloutType = type
  node.calloutTitle = title
}

function walkMdast(node: MdNode): void {
  for (const child of node.children ?? []) {
    if (child.type === 'blockquote') tryTransformBlockquote(child)
    walkMdast(child)
  }
}

const remarkCallout = $remark('remarkCallout', () => () => (tree) => {
  walkMdast(tree as MdNode)
})

/* ── 2. schema 侧：callout ProseMirror 节点 ───────────────────────── */

export const calloutNode = $node('callout', () => ({
  group: 'block',
  content: 'block+',
  defining: true,
  attrs: {
    type: { default: 'note' },
    title: { default: '' },
  },
  parseDOM: [
    {
      // 粘贴站点渲染后的 callout HTML（如从文章页复制）时还原为 callout 节点
      tag: 'div.callout',
      contentElement: '.callout-body',
      getAttrs: (dom) => {
        const typeClass = Array.from(dom.classList).find(
          (c) => c.startsWith('callout-') && c !== 'callout-header' && c !== 'callout-body',
        )
        return {
          type: typeClass?.slice('callout-'.length) ?? 'note',
          title: dom.querySelector('.callout-title-text')?.textContent ?? '',
        }
      },
    },
  ],
  // NodeView 接管实际渲染；此结构也用于文档→DOM 序列化（如复制），
  // 必须与 parseDOM 的 contentElement 对应，否则解析回来会找不到内容容器
  toDOM: (node) => [
    'div',
    { class: `callout callout-${node.attrs['type'] as string}` },
    ['div', { class: 'callout-body' }, 0],
  ],
  parseMarkdown: {
    match: (node) => node.type === 'callout',
    runner: (state, node, type) => {
      const mdNode = node as MdNode
      state
        .openNode(type, { type: mdNode.calloutType ?? 'note', title: mdNode.calloutTitle ?? '' })
        .next(node.children)
        .closeNode()
    },
  },
  toMarkdown: {
    match: (node) => node.type.name === 'callout',
    runner: (state, node) => {
      const marker = `[!${String(node.attrs['type']).toUpperCase()}]`
      const title = node.attrs['title'] ? ` ${String(node.attrs['title'])}` : ''
      // 正文只有空段落时跳过，否则会被序列化成 `> <br />`
      const onlyEmptyParagraph =
        node.childCount === 1 &&
        node.firstChild?.type.name === 'paragraph' &&
        node.firstChild.content.size === 0
      state.openNode('blockquote').addNode('paragraph', [{ type: 'text', value: marker + title }])
      if (!onlyEmptyParagraph) state.next(node.content)
      state.closeNode()
    },
  },
}))

/* ── 3. 视图侧：卡片 NodeView ─────────────────────────────────────── */

const calloutView = $view(calloutNode, (): NodeViewConstructor => {
  return (initialNode, view, getPos) => {
    const dom = document.createElement('div')

    const header = document.createElement('div')
    header.className = 'callout-header'
    header.contentEditable = 'false'

    const icon = document.createElement('span')
    icon.className = 'callout-icon'

    const titleInput = document.createElement('input')
    titleInput.className = 'callout-title-input'
    titleInput.placeholder = '标注标题（可选）'
    titleInput.spellcheck = false

    header.append(icon, titleInput)

    const body = document.createElement('div')
    body.className = 'callout-body'

    dom.append(header, body)

    const applyAttrs = (type: string, title: string) => {
      const config = getCalloutConfig(type)
      dom.className = `callout callout-${type}`
      dom.style.setProperty('--callout-color', config.color)
      icon.textContent = config.icon
      // 正在输入标题时不要回写，避免打断 IME 组合输入
      if (document.activeElement !== titleInput) titleInput.value = title
    }
    applyAttrs(initialNode.attrs['type'] as string, initialNode.attrs['title'] as string)

    titleInput.addEventListener('input', () => {
      const pos = typeof getPos === 'function' ? getPos() : undefined
      if (pos == null) return
      view.dispatch(view.state.tr.setNodeAttribute(pos, 'title', titleInput.value))
    })

    // 标题框内回车 → 光标移入正文开头
    titleInput.addEventListener('keydown', (event) => {
      if (event.key !== 'Enter') return
      event.preventDefault()
      const pos = typeof getPos === 'function' ? getPos() : undefined
      if (pos == null) return
      const $pos = view.state.doc.resolve(pos + 1)
      view.dispatch(view.state.tr.setSelection(Selection.near($pos, 1)).scrollIntoView())
      view.focus()
    })

    return {
      dom,
      contentDOM: body,
      update: (node) => {
        if (node.type.name !== 'callout') return false
        applyAttrs(node.attrs['type'] as string, node.attrs['title'] as string)
        return true
      },
      // 头部（标题输入框）的 DOM 变化与事件不交给 ProseMirror 处理
      ignoreMutation: (mutation) => !body.contains(mutation.target),
      stopEvent: (event) => event.target === titleInput,
    }
  }
})

/* ── 交互：input rule + 斜杠菜单命令 ──────────────────────────────── */

/** 在引用块首段输入 `[!TYPE] `（结尾空格触发）→ 转换为 callout */
const calloutInputRule = $inputRule(
  (ctx) =>
    new InputRule(/^\[!([a-z]+)\] $/i, (state, match, start, end) => {
      const type = match[1]!.toLowerCase()
      if (!isCalloutType(type)) return null

      const $start = state.doc.resolve(start)
      let depth = -1
      for (let d = $start.depth; d > 0; d--) {
        if ($start.node(d).type.name === 'blockquote') {
          depth = d
          break
        }
      }
      // 仅在引用块的首个段落内触发
      if (depth < 0 || $start.index(depth) !== 0) return null

      const blockquotePos = $start.before(depth)
      const tr = state.tr.delete(start, end)
      tr.setNodeMarkup(tr.mapping.map(blockquotePos, -1), calloutNode.type(ctx), {
        type,
        title: '',
      })
      return tr
    }),
)

/** 斜杠菜单「标注 Callout」入口：把当前块包裹进 callout */
export const wrapInCalloutCommand = $command(
  'WrapInCallout',
  (ctx) =>
    (payload?: { type: string }) =>
      wrapIn(calloutNode.type(ctx), { type: payload?.type ?? 'note', title: '' }),
)

// $remark 返回的是元组形插件，需要 flat 展开（与官方 preset 做法一致）
export const calloutPlugins = [
  remarkCallout,
  calloutNode,
  calloutView,
  calloutInputRule,
  wrapInCalloutCommand,
].flat()
