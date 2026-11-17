<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { RouterView } from 'vue-router'
import { Menu } from 'lucide-vue-next'
import DashboardSidebar from '@/components/dashboard/DashboardSidebar.vue'
import Drawer from '@/components/ui/Drawer.vue'
import { gsap } from 'gsap'
import { useHead } from '@unhead/vue'

// 后台所有页面不收录
useHead({
  meta: [{ name: 'robots', content: 'noindex' }],
})

const drawerOpen = ref(false)

// 纸本后台作用域：挂 <html> 而非根 div，因为 Modal/Drawer/Dropdown Teleport 到 body
onMounted(() => document.documentElement.classList.add('dash-mode'))
onBeforeUnmount(() => document.documentElement.classList.remove('dash-mode'))

// GSAP page transition hooks
function onBeforeEnter(el: Element) {
  gsap.set(el as HTMLElement, { autoAlpha: 0, y: 8 })
}

function onEnter(el: Element, done: () => void) {
  gsap.to(el as HTMLElement, {
    autoAlpha: 1,
    y: 0,
    duration: 0.25,
    ease: 'power2.out',
    onComplete: done,
  })
}

function onLeave(el: Element, done: () => void) {
  gsap.to(el as HTMLElement, {
    autoAlpha: 0,
    y: -8,
    duration: 0.15,
    ease: 'power2.in',
    onComplete: done,
  })
}
</script>

<template>
  <div class="min-h-screen flex bg-bg-primary">
    <!-- Desktop Sidebar -->
    <DashboardSidebar />

    <!-- Mobile Drawer -->
    <Drawer v-model:open="drawerOpen" placement="left">
      <div class="md:hidden">
        <DashboardSidebar />
      </div>
    </Drawer>

    <!-- Main Content -->
    <main class="flex-1 p-4 sm:p-6 md:p-8 min-w-0 flex flex-col overflow-x-hidden">
      <!-- Mobile Header Bar -->
      <div class="md:hidden flex items-center gap-3 mb-4">
        <button
          @click="drawerOpen = true"
          class="p-2 rounded-lg border border-border bg-bg-secondary text-text-secondary hover:text-text-primary transition-colors"
        >
          <Menu :size="20" />
        </button>
        <h1 class="text-lg font-semibold text-text-primary">控制台</h1>
      </div>

      <RouterView v-slot="{ Component }">
        <Transition
          @before-enter="onBeforeEnter"
          @enter="onEnter"
          @leave="onLeave"
        >
          <component :is="Component" />
        </Transition>
      </RouterView>
    </main>
  </div>
</template>
