// composables/useVisualViewport.ts
// 可视视口（visualViewport）高度追踪 —— 全站"键盘感知高度"基建。
//
// 背景：移动端软键盘弹起时，iOS Safari 的布局视口（layout viewport，100vh/h-full
// 的依据）不会收缩，导致基于它定高的 fixed 弹层（聊天 sheet、底部对话框）被键盘
// 整个盖住。window.visualViewport.height 始终等于真实可见区域高度，键盘弹出/
// 收起动画期间会连续触发 resize，是移动端唯一可靠的高度来源。
//
// 本 composable 做两件事：
//   1. 暴露响应式 vvHeight（px），供 JS 逻辑消费（如键盘弹起时联动滚动）；
//   2. 把高度实时写入全局 CSS 变量 --vvh，供任意组件在样式里直接引用：
//        height: min(calc(100dvh - 24px), calc(var(--vvh, 100dvh) - 8px))
//      键盘收起时 --vvh ≈ 可视高度，弹层被 dvh 项封顶；键盘弹起时 --vvh 收缩，
//      弹层随之压缩到键盘上方。
//
// 降级：不支持 visualViewport 的浏览器回退 window.innerHeight + resize 监听。

import { ref, onScopeDispose } from 'vue'

export function useVisualViewport() {
  const vv = window.visualViewport
  const vvHeight = ref(vv ? vv.height : window.innerHeight)

  function update() {
    const h = vv ? vv.height : window.innerHeight
    vvHeight.value = h
    document.documentElement.style.setProperty('--vvh', `${Math.round(h)}px`)
  }

  // 首次挂载立即写入，保证 --vvh 在任何 resize 触发前就存在
  update()

  if (vv) {
    vv.addEventListener('resize', update)
    vv.addEventListener('scroll', update)
  } else {
    window.addEventListener('resize', update)
  }

  onScopeDispose(() => {
    if (vv) {
      vv.removeEventListener('resize', update)
      vv.removeEventListener('scroll', update)
    } else {
      window.removeEventListener('resize', update)
    }
  })

  return { vvHeight }
}
