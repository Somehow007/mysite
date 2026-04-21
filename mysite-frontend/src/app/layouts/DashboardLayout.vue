<script setup lang="ts">
import { RouterView } from 'vue-router'
import DashboardSidebar from '@/components/dashboard/DashboardSidebar.vue'
</script>

<template>
  <div class="min-h-screen flex">
    <DashboardSidebar />
    <main class="flex-1 p-8">
      <RouterView v-slot="{ Component }">
        <transition name="page" mode="out-in">
          <component :is="Component" />
        </transition>
      </RouterView>
    </main>
  </div>
</template>
