<script setup lang="ts">
import { ref, onMounted } from 'vue'

const props = withDefaults(defineProps<{
  src: string
  alt?: string
  fetchPriority?: 'high' | 'low' | 'auto'
  lazy?: boolean
}>(), {
  alt: '',
  fetchPriority: 'auto',
  lazy: true,
})

const loaded = ref(false)
const error = ref(false)

onMounted(() => {
  if (!props.src) {
    error.value = true
  }
})

function onLoad() {
  loaded.value = true
}

function onError() {
  error.value = true
}
</script>

<template>
  <div class="optimized-img" :class="{ 'is-loaded': loaded, 'is-error': error }">
    <div v-if="!loaded && !error" class="optimized-img__placeholder">
      <div class="optimized-img__shimmer" />
    </div>
    <img
      v-if="src && !error"
      :src="src"
      :alt="alt"
      :loading="lazy ? 'lazy' : 'eager'"
      :fetchpriority="fetchPriority"
      decoding="async"
      class="optimized-img__img"
      @load="onLoad"
      @error="onError"
    />
    <div v-if="error" class="optimized-img__error">
      <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect width="18" height="18" x="3" y="3" rx="2" ry="2"/><circle cx="9" cy="9" r="2"/><path d="m21 15-3.086-3.086a2 2 0 0 0-2.828 0L6 21"/></svg>
      <span>图片加载失败</span>
    </div>
  </div>
</template>

<style scoped>
.optimized-img {
  position: relative;
  overflow: hidden;
  border-radius: inherit;
  background-color: var(--bg-code);
}

.optimized-img__placeholder {
  position: absolute;
  inset: 0;
  z-index: 1;
}

.optimized-img__shimmer {
  width: 100%;
  height: 100%;
  background: linear-gradient(
    90deg,
    var(--bg-code) 25%,
    var(--border-subtle) 50%,
    var(--bg-code) 75%
  );
  background-size: 200% 100%;
  animation: shimmer 1.5s ease-in-out infinite;
}

@keyframes shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

.optimized-img__img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
  opacity: 0;
  transition: opacity 400ms ease-out;
}

.optimized-img.is-loaded .optimized-img__img {
  opacity: 1;
}

.optimized-img.is-loaded .optimized-img__placeholder {
  display: none;
}

.optimized-img__error {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  width: 100%;
  height: 100%;
  min-height: 120px;
  color: var(--text-muted);
  font-size: 0.75rem;
}

.optimized-img__error svg {
  opacity: 0.4;
}
</style>
