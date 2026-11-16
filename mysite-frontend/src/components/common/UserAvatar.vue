<script setup lang="ts">
import { computed, ref, watch } from 'vue'

const props = withDefaults(defineProps<{
  src?: string | null
  name: string
  size?: number
}>(), {
  size: 32,
})

const failed = ref(false)
watch(() => props.src, () => {
  failed.value = false
})

const initial = computed(() => props.name.trim().charAt(0).toUpperCase() || '?')
</script>

<template>
  <span
    class="inline-flex items-center justify-center rounded-full overflow-hidden shrink-0 select-none bg-accent-subtle text-accent font-semibold"
    :style="{
      width: `${size}px`,
      height: `${size}px`,
      fontSize: `${Math.round(size * 0.4)}px`,
    }"
    role="img"
    :aria-label="name"
  >
    <img
      v-if="src && !failed"
      :src="src"
      :alt="name"
      class="w-full h-full object-cover"
      @error="failed = true"
    />
    <span v-else aria-hidden="true">{{ initial }}</span>
  </span>
</template>
