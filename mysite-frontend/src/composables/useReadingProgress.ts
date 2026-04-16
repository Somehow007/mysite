/**
 * 阅读进度条组合式函数
 */
import { ref, onMounted, onUnmounted } from 'vue'

export function useReadingProgress() {
  const progress = ref(0)
  const isComplete = ref(false)

  let progressBar: HTMLElement | null = null
  let progressContainer: HTMLElement | null = null
  let postContent: HTMLElement | null = null
  let optimizedCalculateProgress: (() => void) | null = null

  const calculateProgress = () => {
    if (!postContent || !progressBar || !progressContainer) return

    const postBottom = postContent.getBoundingClientRect().top + postContent.offsetHeight
    const viewportHeight = window.innerHeight
    const scrollTop = document.documentElement.scrollTop || document.body.scrollTop

    // 计算进度
    const progressValue =
      100 -
      ((postBottom - (scrollTop + viewportHeight) + viewportHeight / 3) /
        (postBottom - viewportHeight + viewportHeight / 3)) *
        100

    progress.value = Math.min(progressValue, 100)

    // 更新进度条宽度
    if (progressBar) {
      progressBar.style.width = `${Math.min(progress.value, 100)}%`
    }

    // 添加或移除 'complete' 类
    if (progress.value > 100) {
      progressContainer.classList.add('complete')
      isComplete.value = true
    } else {
      progressContainer.classList.remove('complete')
      isComplete.value = false
    }
  }

  // 节流函数
  const throttle = (callback: () => void, delay: number) => {
    let lastCall = 0
    return () => {
      const now = Date.now()
      if (now - lastCall >= delay) {
        lastCall = now
        callback()
      }
    }
  }

  const init = () => {
    progressContainer = document.querySelector('.progress-container')
    progressBar = document.querySelector('.progress-bar')
    postContent = document.querySelector('.post-content')

    if (postContent && progressBar && progressContainer) {
      optimizedCalculateProgress = throttle(calculateProgress, 50)
      calculateProgress()
      window.addEventListener('scroll', optimizedCalculateProgress)
      window.addEventListener('resize', optimizedCalculateProgress)
      window.addEventListener('orientationchange', optimizedCalculateProgress)
    }
  }

  onMounted(() => {
    // 延迟初始化，确保DOM已渲染
    setTimeout(init, 100)
  })

  onUnmounted(() => {
    if (optimizedCalculateProgress) {
      window.removeEventListener('scroll', optimizedCalculateProgress)
      window.removeEventListener('resize', optimizedCalculateProgress)
      window.removeEventListener('orientationchange', optimizedCalculateProgress)
    }
  })

  return {
    progress,
    isComplete,
  }
}

