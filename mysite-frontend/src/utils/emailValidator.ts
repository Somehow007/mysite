export interface EmailValidationResult {
  valid: boolean
  message: string
}

export function validateEmail(email: string): EmailValidationResult {
  if (!email || !email.trim()) {
    return { valid: false, message: '请输入邮箱地址' }
  }

  const trimmed = email.trim()

  const atCount = (trimmed.match(/@/g) || []).length
  if (atCount === 0) {
    return { valid: false, message: '邮箱地址缺少 @ 符号' }
  }
  if (atCount > 1) {
    return { valid: false, message: '邮箱地址只能包含一个 @ 符号' }
  }

  const atIndex = trimmed.indexOf('@')
  const localPart = trimmed.substring(0, atIndex)
  const domainPart = trimmed.substring(atIndex + 1)

  if (!localPart) {
    return { valid: false, message: '@ 符号前不能为空' }
  }

  if (localPart.length > 64) {
    return { valid: false, message: '邮箱本地部分长度不能超过 64 个字符' }
  }

  if (localPart.startsWith('.')) {
    return { valid: false, message: '邮箱本地部分不能以点号开头' }
  }

  if (localPart.endsWith('.')) {
    return { valid: false, message: '邮箱本地部分不能以点号结尾' }
  }

  if (localPart.includes('..')) {
    return { valid: false, message: '邮箱本地部分不能包含连续的点号' }
  }

  const localPartRegex = /^[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+$/
  if (!localPartRegex.test(localPart)) {
    return { valid: false, message: '邮箱本地部分包含不允许的字符' }
  }

  if (!domainPart) {
    return { valid: false, message: '请输入有效的邮箱域名' }
  }

  if (domainPart.length > 255) {
    return { valid: false, message: '域名部分长度不能超过 255 个字符' }
  }

  if (domainPart.startsWith('-') || domainPart.endsWith('-')) {
    return { valid: false, message: '域名部分不能以连字符开头或结尾' }
  }

  if (domainPart.startsWith('.') || domainPart.endsWith('.')) {
    return { valid: false, message: '域名部分不能以点号开头或结尾' }
  }

  if (domainPart.includes('..')) {
    return { valid: false, message: '域名部分不能包含连续的点号' }
  }

  const domainLabels = domainPart.split('.')
  if (domainLabels.length < 2) {
    return { valid: false, message: '域名格式不正确，请包含完整的域名（如 example.com）' }
  }

  for (const label of domainLabels) {
    if (!label) {
      return { valid: false, message: '域名部分格式不正确' }
    }
    if (label.startsWith('-') || label.endsWith('-')) {
      return { valid: false, message: '域名标签不能以连字符开头或结尾' }
    }
    const labelRegex = /^[a-zA-Z0-9-]+$/
    if (!labelRegex.test(label)) {
      return { valid: false, message: '域名部分包含不允许的字符' }
    }
  }

  const tld = domainLabels[domainLabels.length - 1]
  if (!tld || tld.length < 2) {
    return { valid: false, message: '顶级域名至少需要 2 个字符' }
  }

  if (!/^[a-zA-Z]+$/.test(tld)) {
    return { valid: false, message: '顶级域名只能包含字母' }
  }

  return { valid: true, message: '' }
}
