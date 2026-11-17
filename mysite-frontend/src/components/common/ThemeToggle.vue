<script setup lang="ts">
import { Palette } from 'lucide-vue-next'
import { useTheme } from '@/composables/useTheme'
import { Dropdown } from '@/components/ui'
import ThemePicker from './ThemePicker.vue'

const { currentTheme, getThemeMeta } = useTheme()
</script>

<template>
  <Dropdown trigger="click" placement="bottom-end" :spacing="6">
    <template #trigger>
      <button
        class="icon-btn"
        :aria-label="`当前主题: ${getThemeMeta(currentTheme)?.name}，点击切换`"
        aria-haspopup="true"
      >
        <Palette :size="17" />
      </button>
    </template>
    <template #default="{ close }">
      <div role="menu" class="w-72 glass glass-sm rounded-xl py-2">
        <div class="px-3 py-2 border-b border-border mb-1">
          <p class="text-xs font-medium text-text-muted">选择主题</p>
        </div>
        <ThemePicker @close="close" />
      </div>
    </template>
  </Dropdown>
</template>
