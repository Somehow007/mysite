export function formatDate(dateStr: string): string {
  const date = new Date(dateStr)
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

const CHINESE_CHARS_PER_MINUTE = 300
const ENGLISH_WORDS_PER_MINUTE = 225
const CODE_CHARS_PER_MINUTE = 200
const IMAGE_TIME_MINUTES = 0.2

const CODE_BLOCK_REGEX = /```[\s\S]*?```/g
const CODE_BLOCK_MARKER_REGEX = /^```[^\n]*\n|\n?```$/g
const IMAGE_REGEX = /!\[[^\]]*\]\([^)]+\)|<img[^>]+>/g
const CHINESE_REGEX = /[\u4e00-\u9fa5]/g
const ENGLISH_REGEX = /[a-zA-Z]+/g

export function calculateReadingTime(content: string): number {
  if (!content) return 1

  const codeBlocks: string[] = []
  let match: RegExpExecArray | null
  while ((match = CODE_BLOCK_REGEX.exec(content)) !== null) {
    codeBlocks.push(match[0].replace(CODE_BLOCK_MARKER_REGEX, ''))
  }
  const textWithoutCode = content.replace(CODE_BLOCK_REGEX, '')

  const images = textWithoutCode.match(IMAGE_REGEX)
  const imageCount = images ? images.length : 0
  const textWithoutImages = textWithoutCode.replace(IMAGE_REGEX, '')

  const chineseChars = (textWithoutImages.match(CHINESE_REGEX) || []).length
  const englishWords = (textWithoutImages.match(ENGLISH_REGEX) || []).length
  const codeCharCount = codeBlocks.join('').replace(/\s+/g, '').length

  const textMinutes = chineseChars / CHINESE_CHARS_PER_MINUTE + englishWords / ENGLISH_WORDS_PER_MINUTE
  const codeMinutes = codeCharCount / CODE_CHARS_PER_MINUTE
  const imageMinutes = imageCount * IMAGE_TIME_MINUTES

  const totalMinutes = textMinutes + codeMinutes + imageMinutes
  return Math.max(1, Math.ceil(totalMinutes))
}
