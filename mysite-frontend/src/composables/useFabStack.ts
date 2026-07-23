// composables/useFabStack.ts
// 浮动操作按钮（FAB）动态堆叠管理器。
//
// 问题：站点有多个 fixed 定位的 FAB（ChatWidget、BackToTop、MobileTocDrawer），
// 硬编码 bottom 值在不同屏幕尺寸下互相重叠。尤其 Tablet 区间（640-1023px）
// 同时存在 ChatWidget 和 MobileTocDrawer 时重叠严重。
//
// 方案（Material Design 3 FAB stacking pattern）：
//   所有 FAB 注册到本 composable，按声明顺序从屏幕底部向上堆叠。
//   每个 FAB 的 bottom 偏移 = baseMargin + Σ(下方可见 FAB 的高度 + gap)。
//   任何 FAB 显示/隐藏时，所有上方的 FAB 自动重新计算位置。

import { ref, computed, onMounted, onBeforeUnmount, type Ref } from 'vue'

// ── 配置 ──────────────────────────────────────────────────────

/** FAB 之间的间距 (px) */
const GAP = 16

/** 距离屏幕底部的基准间距 (px)，对齐 Tailwind bottom-8 = 2rem */
const BASE_MARGIN = 32

// ── FAB 定义（index 越小越靠近屏幕底部）───────────────────────

interface FabSlot {
  id: string
  height: number // px，含 padding
}

const SLOTS: FabSlot[] = [
  { id: 'back-to-top',  height: 48 },  // p-3 + icon 18px = 24 + 18 + 24 ≈ 48
  { id: 'chat-widget',  height: 56 },  // w-14 h-14
  { id: 'mobile-toc',   height: 48 },  // w-12 h-12
]

// ── 全局可见性状态（按 fabId 索引）───────────────────────────

const visibility = ref<Record<string, boolean>>({})

// ── 公开 API ──────────────────────────────────────────────────

export function useFabStack(fabId: string) {
  const slotIndex = SLOTS.findIndex(s => s.id === fabId)
  if (slotIndex < 0) {
    console.warn(`[useFabStack] unknown fabId: ${fabId}`)
  }

  /**
   * 当前 FAB 的 bottom 偏移（px），需通过 :style 绑定到元素。
   * 自动响应下方 FAB 的显示/隐藏。
   */
  const bottomPx = computed(() => {
    let offset = BASE_MARGIN
    for (let i = 0; i < slotIndex; i++) {
      const slot = SLOTS[i]
      if (!slot) continue
      if (visibility.value[slot.id] !== false) {
        offset += slot.height + GAP
      }
    }
    return offset
  })

  /**
   * 设置当前 FAB 的可见性。
   * - true / undefined（默认）→ 可见，占位
   * - false → 不可见，下方 FAB 上移
   */
  function setVisible(v: boolean) {
    visibility.value[fabId] = v
  }

  // 注册/注销
  onMounted(() => {
    // 默认可见
    if (!(fabId in visibility.value)) {
      visibility.value[fabId] = true
    }
  })

  onBeforeUnmount(() => {
    delete visibility.value[fabId]
    // 触发响应式更新
    visibility.value = { ...visibility.value }
  })

  return {
    /** 当前 FAB 的 bottom CSS 值（如 "80px"），直接用于 :style="{ bottom: bottomPx }" */
    bottomStyle: computed(() => `${bottomPx.value}px`),
    /** 标记 FAB 可见/不可见 */
    setVisible,
  }
}
