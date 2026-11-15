export function formatViewCount(count?: number | null): string {
  if (!count || count <= 0) return '0'
  if (count >= 10000) return (count / 10000).toFixed(1) + '万'
  if (count >= 1000) return (count / 1000).toFixed(1) + 'k'
  return String(count)
}

/** 阿拉伯数字 → 罗马数字（合集侧栏序号用，支持到 39） */
export function toRomanNumeral(n: number): string {
  if (!Number.isFinite(n) || n <= 0) return ''
  const table: [number, string][] = [
    [10, 'X'], [9, 'IX'], [5, 'V'], [4, 'IV'], [1, 'I'],
  ]
  let rest = Math.floor(n)
  let out = ''
  for (const [value, numeral] of table) {
    while (rest >= value) {
      out += numeral
      rest -= value
    }
  }
  return out
}
