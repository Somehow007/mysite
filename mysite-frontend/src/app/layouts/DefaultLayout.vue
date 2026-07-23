<script setup lang="ts">
import { defineAsyncComponent } from 'vue'
import { RouterView } from 'vue-router'
import AppHeader from '@/components/common/AppHeader.vue'
import AppFooter from '@/components/common/AppFooter.vue'
import BackToTop from '@/components/common/BackToTop.vue'

const ChatWidget = defineAsyncComponent(
  () => import('@/components/chat/ChatWidget.vue'),
)
</script>

<template>
  <div class="min-h-screen flex flex-col">
    <AppHeader />
    <main class="flex-1 w-full max-w-[1080px] mx-auto px-4 sm:px-6 py-8 sm:py-12">
      <RouterView v-slot="{ Component }">
        <transition name="page" mode="out-in">
          <component :is="Component" />
        </transition>
      </RouterView>
    </main>
    <AppFooter />
    <BackToTop />
    <ChatWidget />
  </div>
</template>
