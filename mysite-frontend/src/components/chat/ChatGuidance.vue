<script setup lang="ts">
/**
 * ChatGuidance — 歧义引导组件（Phase 6）。
 *
 * 当意图分类器置信度低时，AI 不盲目检索，而是生成候选方向让用户选择。
 * 用户点击某个选项后，前端带上 chosenIntentId 重新请求，后端跳过分类直接按指定意图走管道。
 */
import { HelpCircle, ChevronRight } from 'lucide-vue-next'
import type { GuidanceOption } from '@/types'

defineProps<{
  message: string
  options: GuidanceOption[]
}>()

const emit = defineEmits<{
  select: [intentId: string]
}>()
</script>

<template>
  <div class="mt-3 border-t border-amber-500/20 pt-3">
    <div class="flex items-start gap-2 mb-2.5">
      <HelpCircle :size="14" class="text-amber-500 mt-0.5 shrink-0" />
      <p class="text-sm text-text-secondary leading-relaxed">{{ message }}</p>
    </div>

    <div class="flex flex-wrap gap-2">
      <button
        v-for="opt in options"
        :key="opt.intentId"
        class="flex items-center gap-1.5 px-3 py-2 rounded-lg
               bg-accent-subtle text-accent text-sm
               hover:bg-accent hover:text-white
               transition-colors duration-150
               border border-accent/20 hover:border-accent"
        @click="emit('select', opt.intentId)"
      >
        <span>{{ opt.label }}</span>
        <ChevronRight :size="14" />
      </button>
    </div>
  </div>
</template>
