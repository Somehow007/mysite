export function formatDate(dateStr: string): string {
  const date = new Date(dateStr)
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

export function calculateReadingTime(content: string): number {
  const wordsPerMinute = 300
  const chineseCharsPerMinute = 500
  const chineseChars = (content.match(/[\u4e00-\u9fa5]/g) || []).length
  const englishWords = (content.match(/[a-zA-Z]+/g) || []).length
  const minutes = chineseChars / chineseCharsPerMinute + englishWords / wordsPerMinute
  return Math.max(1, Math.ceil(minutes))
}
