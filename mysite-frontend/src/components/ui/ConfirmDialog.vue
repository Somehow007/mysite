<script setup lang="ts">
import { ref, computed } from 'vue'
import { AlertTriangle } from 'lucide-vue-next'
import Modal from './Modal.vue'

const props = withDefaults(defineProps<{
  open: boolean
  title?: string
  message: string
  confirmText?: string
  cancelText?: string
  danger?: boolean
  /** If set, user must type this exact string to confirm */
  confirmInput?: string
}>(), {
  title: '确认操作',
  confirmText: '确定',
  cancelText: '取消',
  danger: false,
})

const emit = defineEmits<{
  'update:open': [value: boolean]
  confirm: []
  cancel: []
}>()

const inputValue = ref('')

const inputMatch = computed(() => {
  if (!props.confirmInput) return true
  return inputValue.value === props.confirmInput
})

function handleConfirm() {
  if (!inputMatch.value) return
  emit('confirm')
  emit('update:open', false)
  inputValue.value = ''
}

function handleCancel() {
  emit('cancel')
  emit('update:open', false)
  inputValue.value = ''
}
</script>

<template>
  <Modal
    :open="open"
    :title="title"
    :max-width="'max-w-md'"
    :closable="true"
    @close="handleCancel"
  >
    <div class="flex items-start gap-3">
      <div
        v-if="danger"
        class="shrink-0 w-10 h-10 rounded-full bg-danger-subtle flex items-center justify-center"
      >
        <AlertTriangle :size="20" class="text-danger" />
      </div>
      <div class="flex-1">
        <p class="text-sm text-text-secondary leading-relaxed">{{ message }}</p>

        <!-- Input-to-confirm for high-risk ops -->
        <div v-if="confirmInput" class="mt-4">
          <p class="text-xs text-text-muted mb-2">
            请输入 <code class="px-1.5 py-0.5 rounded bg-bg-code text-danger font-mono text-xs">{{ confirmInput }}</code> 以确认：
          </p>
          <input
            v-model="inputValue"
            type="text"
            :placeholder="confirmInput"
            class="input-base"
            @keydown.enter="handleConfirm"
          />
        </div>
      </div>
    </div>

    <template #footer>
      <button class="btn-secondary" @click="handleCancel">{{ cancelText }}</button>
      <button
        :class="danger ? 'btn-danger' : 'btn-primary'"
        :disabled="confirmInput ? !inputMatch : false"
        @click="handleConfirm"
      >
        {{ confirmText }}
      </button>
    </template>
  </Modal>
</template>
