<script setup lang="ts">
import { useRouter } from 'vue-router'
import { ArrowLeft } from 'lucide-vue-next'

defineProps<{
  title: string
  subtitle?: string
  /** 传入路由路径时在标题左侧渲染返回按钮（router.push，不用 router.back()） */
  back?: string
}>()

const router = useRouter()
</script>

<template>
  <div class="ui-page-header flex items-center justify-between gap-4">
    <div class="flex items-center gap-3 min-w-0">
      <button
        v-if="back"
        type="button"
        aria-label="返回"
        class="p-2 -ml-2 rounded-lg text-text-muted hover:bg-accent-subtle hover:text-accent transition-all duration-200 group shrink-0"
        @click="router.push(back)"
      >
        <ArrowLeft :size="18" class="group-hover:-translate-x-0.5 transition-transform duration-200" />
      </button>
      <div class="min-w-0">
        <h1 class="ui-page-title text-xl md:text-2xl font-bold text-text-primary">{{ title }}</h1>
        <p v-if="subtitle" class="text-sm text-text-secondary mt-1">{{ subtitle }}</p>
      </div>
    </div>
    <div v-if="$slots.actions" class="flex items-center gap-2 shrink-0">
      <slot name="actions" />
    </div>
  </div>
</template>
