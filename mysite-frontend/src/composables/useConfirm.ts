import { ref } from 'vue'

export interface ConfirmOptions {
  title?: string
  message: string
  confirmText?: string
  cancelText?: string
  danger?: boolean
  /** If set, user must type this exact string to confirm */
  confirmInput?: string
}

interface ConfirmState extends ConfirmOptions {
  resolve: (value: boolean) => void
}

const state = ref<ConfirmState | null>(null)

export function useConfirm() {
  function confirm(options: ConfirmOptions | string): Promise<boolean> {
    const opts: ConfirmOptions = typeof options === 'string'
      ? { message: options }
      : options

    return new Promise((resolve) => {
      state.value = {
        ...opts,
        resolve,
      }
    })
  }

  function close(value: boolean) {
    if (state.value) {
      state.value.resolve(value)
      state.value = null
    }
  }

  return {
    confirm,
    dialogState: state,
    close,
  }
}
