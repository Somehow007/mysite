<template>
  <svg
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
  >
    <path
      fill-rule="evenodd"
      clip-rule="evenodd"
      d="M4 13V11H17.5858L11.5858 5L13 3.58578L21.4142 12L13 20.4142L11.5858 19L17.5858 13H4Z"
      :fill="color"
    />
  </svg>
</template>

<script setup lang="ts">
withDefaults(
  defineProps<{
    size?: string | number
    color?: string
  }>(),
  {
    size: 24,
    color: 'currentColor',
  }
)
</script>

