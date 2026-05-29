import { onMounted, onBeforeUnmount } from 'vue'

const LOADING_CLASS = 'img-loading'
const LOADED_CLASS = 'img-loaded'
const ERROR_CLASS = 'img-error'
const WRAPPER_CLASS = 'img-wrapper'

function handleImageLoad(img: HTMLImageElement) {
  img.classList.remove(LOADING_CLASS)
  img.classList.add(LOADED_CLASS)
}

function handleImageError(img: HTMLImageElement) {
  img.classList.remove(LOADING_CLASS)
  img.classList.add(ERROR_CLASS)
  img.alt = '图片加载失败'
  const wrapper = img.closest(`.${WRAPPER_CLASS}`) as HTMLElement | null
  if (wrapper) {
    wrapper.setAttribute('data-error', 'true')
  }
}

function processImage(img: HTMLImageElement) {
  if (img.dataset.optimized) return
  img.dataset.optimized = 'true'

  img.classList.add(LOADING_CLASS)

  if (img.complete) {
    if (img.naturalWidth > 0) {
      handleImageLoad(img)
    } else {
      handleImageError(img)
    }
    return
  }

  img.addEventListener('load', () => handleImageLoad(img), { once: true })
  img.addEventListener('error', () => handleImageError(img), { once: true })
}

function processContainer(container: HTMLElement) {
  const images = container.querySelectorAll('img')
  images.forEach(processImage)

  const wrappers = container.querySelectorAll(`.${WRAPPER_CLASS}`)
  wrappers.forEach((wrapper) => {
    if (!(wrapper as HTMLElement).dataset.bound) {
      ;(wrapper as HTMLElement).dataset.bound = 'true'
    }
  })
}

export function useImageOptimizer(containerRef: ReturnType<typeof import('vue').ref<HTMLElement | null>>) {
  let observer: MutationObserver | null = null

  onMounted(() => {
    if (containerRef.value) {
      processContainer(containerRef.value)
    }

    observer = new MutationObserver((mutations) => {
      for (const mutation of mutations) {
        for (const node of mutation.addedNodes) {
          if (node instanceof HTMLElement) {
            if (node.tagName === 'IMG') {
              processImage(node as HTMLImageElement)
            } else {
              const images = node.querySelectorAll('img')
              images.forEach(processImage)
            }
          }
        }
      }
    })

    if (containerRef.value) {
      observer.observe(containerRef.value, {
        childList: true,
        subtree: true,
      })
    }
  })

  onBeforeUnmount(() => {
    observer?.disconnect()
  })

  return {
    processContainer,
  }
}
