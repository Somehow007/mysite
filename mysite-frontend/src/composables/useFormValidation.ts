import { ref, computed } from 'vue'
import { validateEmail } from '@/utils/emailValidator'
import { validatePhone } from '@/utils/phoneValidator'

export interface FieldValidation {
  valid: boolean
  message: string
}

export function useFormValidation() {
  const errors = ref<Record<string, string>>({})
  const touched = ref<Record<string, boolean>>({})

  function setFieldError(field: string, message: string) {
    errors.value[field] = message
  }

  function clearFieldError(field: string) {
    delete errors.value[field]
  }

  function setFieldTouched(field: string) {
    touched.value[field] = true
  }

  const hasErrors = computed(() => Object.keys(errors.value).length > 0)

  function validateRequired(field: string, value: string, label: string): boolean {
    if (!value.trim()) {
      setFieldError(field, `请输入${label}`)
      return false
    }
    clearFieldError(field)
    return true
  }

  function validateEmailField(field: string, value: string): boolean {
    if (!value.trim()) {
      setFieldError(field, '请输入邮箱地址')
      return false
    }
    const result = validateEmail(value)
    if (!result.valid) {
      setFieldError(field, result.message)
      return false
    }
    clearFieldError(field)
    return true
  }

  function validatePhoneField(field: string, value: string): boolean {
    if (!value.trim()) {
      setFieldError(field, '请输入手机号')
      return false
    }
    const result = validatePhone(value)
    if (!result.valid) {
      setFieldError(field, result.message)
      return false
    }
    clearFieldError(field)
    return true
  }

  function validateMinLength(field: string, value: string, min: number, label: string): boolean {
    if (value.length < min) {
      setFieldError(field, `${label}至少 ${min} 位`)
      return false
    }
    clearFieldError(field)
    return true
  }

  function validateMatch(field: string, value1: string, value2: string, message: string): boolean {
    if (value1 !== value2) {
      setFieldError(field, message)
      return false
    }
    clearFieldError(field)
    return true
  }

  function onFieldBlur(field: string) {
    setFieldTouched(field)
  }

  function getFieldError(field: string): string {
    return touched.value[field] ? (errors.value[field] || '') : ''
  }

  function isFieldInvalid(field: string): boolean {
    return !!touched.value[field] && !!errors.value[field]
  }

  function clearAll() {
    errors.value = {}
    touched.value = {}
  }

  return {
    errors,
    touched,
    hasErrors,
    setFieldError,
    clearFieldError,
    setFieldTouched,
    validateRequired,
    validateEmailField,
    validatePhoneField,
    validateMinLength,
    validateMatch,
    onFieldBlur,
    getFieldError,
    isFieldInvalid,
    clearAll,
  }
}
