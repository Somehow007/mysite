/**
 * 代码高亮工具函数
 */
import hljs from 'highlight.js'
import 'highlight.js/styles/default.css'

/**
 * 初始化代码高亮
 */
export function initCodeHighlight() {
  if (typeof document === 'undefined') return

  // 选择所有代码块
  document.querySelectorAll('pre code').forEach((codeBlock) => {
    // 代码高亮
    hljs.highlightElement(codeBlock as HTMLElement)

    // 跳过纯文本块
    if (!codeBlock.classList.contains('language-text')) {
      // 计算行数
      const lines = (codeBlock.textContent || '').split(/\n(?!$)/g).length
      const lineCount = lines > 1 ? lines + 1 : lines

      // 生成行号
      const numbers = Array.from({ length: lineCount - 1 }, (_, i) => {
        return `<span class="line" aria-hidden="true">${i + 1}</span>`
      }).join('')

      // 创建并添加行号容器
      const linesContainer = document.createElement('div')
      linesContainer.className = 'lines'
      linesContainer.innerHTML = numbers
      codeBlock.parentNode?.appendChild(linesContainer)
    }
  })
}

