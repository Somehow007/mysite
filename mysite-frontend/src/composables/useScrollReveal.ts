import { onMounted, onUnmounted } from 'vue'
import { gsap } from 'gsap'
import { ScrollTrigger } from 'gsap/ScrollTrigger'

/**
 * Composable that creates entrance reveal animations triggered on scroll.
 * Elements with the `data-reveal` attribute will animate in when they
 * enter the viewport.
 *
 * Usage in template:
 *   <div data-reveal>Content to reveal on scroll</div>
 *   <div data-reveal="stagger">Staggered child items</div>
 *
 * Respects prefers-reduced-motion: animations are skipped for users
 * who prefer reduced motion.
 */
export function useScrollReveal(scope?: string | HTMLElement) {
  const defaults = {
    autoAlpha: 0,
    y: 30,
    duration: 0.6,
    ease: 'power2.out',
  }

  let triggers: ScrollTrigger[] = []

  onMounted(() => {
    const container = typeof scope === 'string'
      ? document.querySelector(scope)
      : scope || document

    if (!container) return

    // Standard reveal with ScrollTrigger.batch()
    const batchTriggers = ScrollTrigger.batch('[data-reveal]:not([data-reveal="stagger"])', {
      onEnter: (elements) => {
        gsap.fromTo(elements,
          { autoAlpha: 0, y: 30 },
          {
            autoAlpha: 1,
            y: 0,
            duration: 0.6,
            ease: 'power2.out',
            stagger: 0.08,
            overwrite: true,
          },
        )
      },
      onLeaveBack: (elements) => {
        gsap.set(elements, { autoAlpha: 0, y: 30, overwrite: true })
      },
      start: 'top 85%',
      once: true,
    })
    triggers.push(...batchTriggers)

    // Staggered children: parent gets data-reveal="stagger"
    const staggerTriggers = ScrollTrigger.batch('[data-reveal="stagger"] > *', {
      onEnter: (elements) => {
        gsap.fromTo(elements,
          { autoAlpha: 0, y: 24 },
          {
            autoAlpha: 1,
            y: 0,
            duration: 0.5,
            ease: 'power2.out',
            stagger: { each: 0.06, from: 'start' },
            overwrite: true,
          },
        )
      },
      start: 'top 88%',
      once: true,
    })
    triggers.push(...staggerTriggers)
  })

  onUnmounted(() => {
    triggers.forEach(t => t.kill())
  })

  return { triggers }
}
