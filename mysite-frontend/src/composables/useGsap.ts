import { onMounted, onUnmounted, ref, unref, type Ref } from 'vue'
import { gsap } from 'gsap'

/**
 * GSAP composable for Vue 3 — manages gsap.context() lifecycle.
 *
 * Usage:
 * ```ts
 * const { container, ctx } = useGsap()
 * onMounted(() => {
 *   if (!container.value) return
 *   ctx.add(() => {
 *     gsap.from('.item', { autoAlpha: 0, y: 20, stagger: 0.1 })
 *   })
 * })
 * ```
 *
 * All animations and ScrollTriggers created inside ctx.add() are automatically
 * scoped to the container element and reverted on component unmount.
 */
export function useGsap() {
  const container = ref<HTMLElement | null>(null)
  let ctx: gsap.Context | null = null

  onMounted(() => {
    if (!container.value) return
    ctx = gsap.context(() => {}, container.value)
  })

  onUnmounted(() => {
    ctx?.revert()
  })

  /**
   * Add animations to the GSAP context. Call inside onMounted
   * so the container ref is already in the DOM.
   */
  function add(fn: () => void) {
    ctx?.add(fn)
  }

  return { container, ctx: { add } }
}

/**
 * Creates responsive GSAP animations that auto-revert when breakpoints
 * or conditions change. Wraps gsap.matchMedia() with Vue lifecycle.
 *
 * Usage:
 * ```ts
 * const { container } = useGsapMatchMedia(
 *   {
 *     isDesktop: '(min-width: 800px)',
 *     reduceMotion: '(prefers-reduced-motion: reduce)',
 *   },
 *   (context) => {
 *     const { isDesktop, reduceMotion } = context.conditions!
 *     gsap.from('.item', {
 *       autoAlpha: 0,
 *       y: isDesktop ? 80 : 40,
 *       duration: reduceMotion ? 0 : 0.6,
 *       stagger: reduceMotion ? 0 : 0.1,
 *     })
 *   },
 * )
 * ```
 */
export function useGsapMatchMedia<T extends Record<string, string>>(
  conditions: T,
  handler: (context: gsap.Context & { conditions?: Record<keyof T, boolean> }) => void,
  scope?: Ref<HTMLElement | null> | HTMLElement | null,
) {
  const mm = gsap.matchMedia()

  onMounted(() => {
    const scopeEl = scope ? unref(scope) : undefined
    mm.add(conditions, handler as any, scopeEl ?? undefined)
  })

  onUnmounted(() => {
    mm.revert()
  })

  return { mm }
}
