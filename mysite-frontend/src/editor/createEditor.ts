import { Crepe } from '@milkdown/crepe'
import { commandsCtx } from '@milkdown/kit/core'
import { clearTextInCurrentBlockCommand } from '@milkdown/kit/preset/commonmark'
import { replaceAll } from '@milkdown/kit/utils'
import { CALLOUT_GROUPS } from '@/utils/callouts'
import { calloutPlugins, wrapInCalloutCommand } from './calloutPlugin'
import { createImageUploader } from './imageUploader'
import { attachMarkdownPasteGuard } from './markdownPaste'

import '@milkdown/crepe/theme/common/style.css'
import '@milkdown/crepe/theme/frame.css'

/**
 * Crepe（Milkdown）所见即所得编辑器封装。
 *
 * 所有 Milkdown 相关代码收敛在 src/editor/ 内，业务侧只面对
 * MarkdownWysiwygEditor.vue 这一个普通 Vue 组件（见
 * docs/editor-redesign-plan.md §4.1）。
 */

export interface EditorHandle {
  /** 当前文档序列化为 Markdown */
  getMarkdown: () => string
  /** 外部赋值：整体替换文档内容（光标重置到文首） */
  setMarkdown: (markdown: string) => void
  /** 释放编辑器实例与全部 DOM 监听 */
  destroy: () => Promise<void>
}

export interface CreateEditorOptions {
  /** 挂载容器 */
  root: HTMLElement
  /** 初始 Markdown 内容 */
  defaultValue: string
  /** 占位文本 */
  placeholder?: string
  /** 文档内容变化回调（已序列化为 Markdown） */
  onChange: (markdown: string) => void
}

export async function createEditor(options: CreateEditorOptions): Promise<EditorHandle> {
  const { root, defaultValue, placeholder, onChange } = options

  const uploader = createImageUploader()

  const crepe = new Crepe({
    root,
    defaultValue,
    featureConfigs: {
      [Crepe.Feature.Placeholder]: {
        text: placeholder ?? '输入 / 唤起更多功能…',
        mode: 'block',
      },
      [Crepe.Feature.ImageBlock]: {
        // 工具栏插入、粘贴、拖拽三种入口统一走站点上传 API
        onUpload: uploader,
        inlineOnUpload: uploader,
        blockOnUpload: uploader,
        // 同源图片不做代理
        proxyDomURL: (url: string) => url,
        // 限制展示高度，避免大图占满整个编辑视口
        maxHeight: 480,
      },
      [Crepe.Feature.Latex]: {
        // 与 useMarkdown.ts 渲染管线的 KaTeX 配置保持一致
        katexOptions: { throwOnError: false, strict: false },
      },
      [Crepe.Feature.BlockEdit]: {
        // 在默认斜杠菜单之后追加「标注 Callout」分组
        buildMenu: (builder) => {
          const group = builder.addGroup('callout', '标注 Callout')
          for (const g of CALLOUT_GROUPS) {
            for (const t of g.types) {
              group.addItem(`callout-${t.type.toLowerCase()}`, {
                label: `${t.label} ${t.type}`,
                icon: t.icon,
                onRun: (ctx) => {
                  const commands = ctx.get(commandsCtx)
                  commands.call(clearTextInCurrentBlockCommand.key)
                  commands.call(wrapInCalloutCommand.key, { type: t.type.toLowerCase() })
                },
              })
            }
          }
        },
      },
    },
  })

  crepe.on((listener) => {
    listener.markdownUpdated((_ctx, markdown, prevMarkdown) => {
      if (markdown !== prevMarkdown) {
        onChange(markdown)
      }
    })
  })

  // 注册 Callout 扩展（必须在 create() 之前）
  crepe.editor.use(calloutPlugins)

  await crepe.create()

  // 「代码视图复制」粘贴修正（IDE 复制的整篇 Markdown 被塞进代码块的问题）
  const detachPasteGuard = attachMarkdownPasteGuard(root, crepe.editor.ctx)

  return {
    getMarkdown: () => crepe.getMarkdown(),
    setMarkdown: (markdown: string) => {
      crepe.editor.action(replaceAll(markdown))
    },
    destroy: async () => {
      detachPasteGuard()
      await crepe.destroy()
    },
  }
}
