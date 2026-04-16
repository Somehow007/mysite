/**
 * 响应式视频工具函数
 * 将iframe等嵌入内容转换为响应式
 */

/**
 * 使iframe等嵌入内容响应式
 */
export function reframe(elements: NodeListOf<Element> | Element[]) {
  const items = Array.from(elements)

  items.forEach((item) => {
    const wrapper = document.createElement('div')
    wrapper.className = 'js-reframe'
    wrapper.style.paddingTop = '56.25%'
    wrapper.style.position = 'relative'
    wrapper.style.width = '100%'

    item.parentNode?.insertBefore(wrapper, item)
    wrapper.appendChild(item)

    if (item instanceof HTMLIFrameElement) {
      item.style.position = 'absolute'
      item.style.height = '100%'
      item.style.left = '0'
      item.style.top = '0'
      item.style.width = '100%'
    }
  })
}

/**
 * 初始化响应式视频
 */
export function initResponsiveVideos() {
  if (typeof document === 'undefined') return

  const sources = [
    '.post-content iframe[src*="youtube.com"]',
    '.post-content iframe[src*="youtube-nocookie.com"]',
    '.post-content iframe[src*="player.vimeo.com"]',
    '.post-content iframe[src*="kickstarter.com"][src*="video.html"]',
    '.post-content object',
    '.post-content embed',
  ]

  const elements = document.querySelectorAll(sources.join(','))
  reframe(elements)
}

