<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useMediaQuery } from '@vueuse/core'
import { ArrowUp } from 'lucide-vue-next'
import { useFabStack } from '@/composables/useFabStack'

const { bottomStyle, setVisible } = useFabStack('back-to-top')

const route = useRoute()
// 文章页 <lg 由 PostDock 接管底部区域（T9），BackToTop 让位
const isNarrow = useMediaQuery('(max-width: 1023px)')
const dockActive = computed(() => isNarrow.value && route.name === 'post')

const scrolled = ref(false)
const visible = computed(() => scrolled.value && !dockActive.value)
watch(visible, setVisible, { immediate: true })

function handleScroll() {
  scrolled.value = window.scrollY > 300
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
      :style="{ bottom: bottomStyle }"
      class="fixed right-8 z-40 p-3 rounded-full bg-accent text-white shadow-lg hover:shadow-xl hover:-translate-y-0.5 transition-all duration-200"
      aria-label="返回顶部"
    >
      <ArrowUp :size="18" />
    </button>
  </transition>
</template>
