export interface PhoneValidationResult {
  valid: boolean
  message: string
}

const VALID_PREFIXES = [
  '130', '131', '132', '133', '134', '135', '136', '137', '138', '139',
  '145', '146', '147', '148', '149',
  '150', '151', '152', '153', '155', '156', '157', '158', '159',
  '162', '165', '166', '167',
  '170', '171', '172', '173', '174', '175', '176', '177', '178',
  '180', '181', '182', '183', '184', '185', '186', '187', '188', '189',
  '190', '191', '193', '195', '196', '197', '198', '199',
]

export function validatePhone(phone: string): PhoneValidationResult {
  if (!phone || !phone.trim()) {
    return { valid: false, message: '请输入手机号' }
  }

  const trimmed = phone.trim()

  if (!/^\d+$/.test(trimmed)) {
    return { valid: false, message: '手机号只能包含数字，请移除非数字字符' }
  }

  if (trimmed.length < 11) {
    return { valid: false, message: `手机号长度不足，当前 ${trimmed.length} 位，需 11 位` }
  }

  if (trimmed.length > 11) {
    return { valid: false, message: `手机号长度超出，当前 ${trimmed.length} 位，需 11 位` }
  }

  const prefix = trimmed.substring(0, 3)
  if (!VALID_PREFIXES.includes(prefix)) {
    return { valid: false, message: `号段 ${prefix} 不是有效的手机号段，请检查输入` }
  }

  return { valid: true, message: '' }
}
