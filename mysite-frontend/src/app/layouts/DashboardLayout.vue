<script setup lang="ts">
import { ref } from 'vue'
import { RouterView } from 'vue-router'
import { Menu } from 'lucide-vue-next'
import DashboardSidebar from '@/components/dashboard/DashboardSidebar.vue'
import Drawer from '@/components/ui/Drawer.vue'

const drawerOpen = ref(false)
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
        <component :is="Component" />
      </RouterView>
    </main>
  </div>
</template>

<style scoped>
.page-enter-active,
.page-leave-active {
  transition: opacity 150ms var(--ease-smooth), transform 150ms var(--ease-smooth);
}
.page-enter-from {
  opacity: 0;
  transform: translateY(8px);
}
.page-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}
</style>
