/**
 * 日期格式化工具函数
 */

/**
 * 格式化日期为 "DD MMM YYYY" 格式
 * @example formatDate(new Date('2023-01-15')) => "15 Jan 2023"
 */
export function formatDate(date: Date | string | null | undefined): string {
  if (date == null) return ''
  const d = typeof date === 'string' ? new Date(date) : date
  const months = [
    'Jan',
    'Feb',
    'Mar',
    'Apr',
    'May',
    'Jun',
    'Jul',
    'Aug',
    'Sep',
    'Oct',
    'Nov',
    'Dec',
  ]

  const day = d.getDate()
  const month = months[d.getMonth()]
  const year = d.getFullYear()

  return `${day} ${month} ${year}`
}

/**
 * 格式化日期为 "DD-MM-YYYY" 格式（用于 datetime 属性）
 */
export function formatDateISO(date: Date | string | null | undefined): string {
  if (date == null) return ''
  const d = typeof date === 'string' ? new Date(date) : date
  const day = String(d.getDate()).padStart(2, '0')
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const year = d.getFullYear()
  return `${day}-${month}-${year}`
}

/**
 * 计算阅读时间（基于字数）
 */
export function calculateReadingTime(wordCount: number, wordsPerMinute = 200): string {
  const minutes = Math.ceil(wordCount / wordsPerMinute)
  if (minutes === 1) {
    return '1 min read'
  }
  return `${minutes} min read`
}

