<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { ArrowUp } from 'lucide-vue-next'

const visible = ref(false)

function handleScroll() {
  visible.value = window.scrollY > 300
}

function scrollToTop() {
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

onMounted(() => {
  window.addEventListener('scroll', handleScroll, { passive: true })
})

onUnmounted(() => {
  window.removeEventListener('scroll', handleScroll)
})
</script>

<template>
  <transition name="fade">
    <button
      v-if="visible"
      @click="scrollToTop"
      class="fixed bottom-8 right-8 z-40 p-3 rounded-full bg-[var(--color-accent)] dark:bg-[var(--color-dark-accent)] text-white dark:text-[var(--color-dark-bg-primary)] shadow-lg hover:shadow-xl hover:-translate-y-0.5 transition-all duration-200"
      aria-label="返回顶部"
    >
      <ArrowUp :size="18" />
    </button>
  </transition>
</template>
