<script setup lang="ts">
import { useConfirm } from '@/composables/useConfirm'
import ConfirmDialog from './ui/ConfirmDialog.vue'

const { dialogState, close } = useConfirm()
</script>

<template>
  <ConfirmDialog
    v-if="dialogState"
    :open="true"
    :title="dialogState.title"
    :message="dialogState.message"
    :confirm-text="dialogState.confirmText"
    :cancel-text="dialogState.cancelText"
    :danger="dialogState.danger"
    :confirm-input="dialogState.confirmInput"
    @confirm="close(true)"
    @cancel="close(false)"
  />
</template>
