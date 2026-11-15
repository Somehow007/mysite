// composables/useChatLauncher.ts
// 聊天面板启动器 —— 模块级单例状态。
//
// ChatWidget 的开合状态从组件内 ref 提升到这里：页面上的提问入口
// （首页 HomeAskPanel、文章页 AskArticleCard、选区 popover 等）通过
// launcher.open() 打开面板并携带一次性参数，无需感知 ChatWidget 实例。
//
// pending 语义：open(opts) 写入 pending 后置 isOpen=true；ChatWidget 在
// isOpen 变 true（或 pending 在面板已开时变化）时消费一次并清空。

import { ref } from 'vue'

export interface ChatLaunchOptions {
  /** 预填到输入框的问题（send=false 时生效） */
  question?: string
  /** 输入框上方的引用条内容（可删除），发送时拼成「> 引用\n\n问题」 */
  quote?: string
  /** 打开后立即发送 question（需同时给 question） */
  send?: boolean
  /** 本次打开时应用的 KB 检索范围；缺省不动用户当前选择 */
  kbIds?: string[]
}

const isOpen = ref(false)
const pending = ref<ChatLaunchOptions | null>(null)
/** 文章页当前阅读文章标题，由 BlogPost 注入；移动端 sheet 标题展示用 */
const readingTitle = ref<string | null>(null)

export function useChatLauncher() {
  function open(opts?: ChatLaunchOptions) {
    pending.value = opts ?? null
    isOpen.value = true
  }

  function close() {
    isOpen.value = false
  }

  /** ChatWidget 消费一次性启动参数；无参数时返回 null */
  function consumePending(): ChatLaunchOptions | null {
    const opts = pending.value
    pending.value = null
    return opts
  }

  /** BlogPost 同步当前阅读文章标题（null = 不在文章页/无标题） */
  function setReadingTitle(title: string | null) {
    readingTitle.value = title
  }

  return { isOpen, pending, readingTitle, open, close, consumePending, setReadingTitle }
}
