export function formatViewCount(count?: number | null): string {
  if (!count || count <= 0) return '0'
  if (count >= 10000) return (count / 10000).toFixed(1) + '万'
  if (count >= 1000) return (count / 1000).toFixed(1) + 'k'
  return String(count)
}

/** 合集连载进度：percent 仅在设了计划篇数时有值（0-100 封顶），finished=已完结 */
export function collectionProgress(collection: {
  articleCount?: number | null
  plannedCount?: number | null
  status?: number | null
}): { count: number; planned: number | null; finished: boolean; percent: number | null } {
  const count = collection.articleCount ?? 0
  const planned = collection.plannedCount != null && collection.plannedCount > 0 ? collection.plannedCount : null
  const finished = collection.status === 1
  const percent = planned != null ? Math.min(100, Math.round((count / planned) * 100)) : null
  return { count, planned, finished, percent }
}

/** 「7 / 9 篇」/「3 篇」 */
export function formatCollectionCount(collection: {
  articleCount?: number | null
  plannedCount?: number | null
}): string {
  const p = collectionProgress(collection)
  return p.planned != null ? `${p.count} / ${p.planned} 篇` : `${p.count} 篇`
}

/** 「7 / 9 篇 · 连载中」/「3 篇 · 已完结」 */
export function formatCollectionProgress(collection: {
  articleCount?: number | null
  plannedCount?: number | null
  status?: number | null
}): string {
  const p = collectionProgress(collection)
  const statusText = p.finished ? '已完结' : '连载中'
  return p.planned != null ? `${p.count} / ${p.planned} 篇 · ${statusText}` : `${p.count} 篇 · ${statusText}`
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
