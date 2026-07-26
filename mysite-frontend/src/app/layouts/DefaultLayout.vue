<script setup lang="ts">
import { defineAsyncComponent } from 'vue'
import { RouterView } from 'vue-router'
import AppHeader from '@/components/common/AppHeader.vue'
import AppFooter from '@/components/common/AppFooter.vue'
import BackToTop from '@/components/common/BackToTop.vue'
import { gsap } from 'gsap'

const ChatWidget = defineAsyncComponent(
  () => import('@/components/chat/ChatWidget.vue'),
)

// GSAP page transition hooks
function onBeforeEnter(el: Element) {
  gsap.set(el as HTMLElement, { autoAlpha: 0, y: 12 })
}

function onEnter(el: Element, done: () => void) {
  gsap.to(el as HTMLElement, {
    autoAlpha: 1,
    y: 0,
    duration: 0.35,
    ease: 'power2.out',
    onComplete: done,
  })
}

function onBeforeLeave(el: Element) {
  gsap.set(el as HTMLElement, { position: 'absolute', width: '100%' })
}

function onLeave(el: Element, done: () => void) {
  gsap.to(el as HTMLElement, {
    autoAlpha: 0,
    y: -6,
    duration: 0.2,
    ease: 'power2.in',
    onComplete: done,
  })
}
</script>

<template>
  <div class="min-h-screen flex flex-col">
    <AppHeader />
    <main class="flex-1 w-full max-w-[1080px] mx-auto px-4 sm:px-6 py-8 sm:py-12">
      <RouterView v-slot="{ Component }">
        <Transition
          mode="out-in"
          @before-enter="onBeforeEnter"
          @enter="onEnter"
          @before-leave="onBeforeLeave"
          @leave="onLeave"
        >
          <component :is="Component" />
        </Transition>
      </RouterView>
    </main>
    <AppFooter />
    <BackToTop />
    <ChatWidget />
  </div>
</template>
