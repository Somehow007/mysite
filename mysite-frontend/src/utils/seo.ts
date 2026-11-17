/**
 * SEO 辅助纯函数：og:image / canonical / article:* 时间都要求绝对地址与 ISO 8601，
 * 而后端下发的 coverImage、createTime 形态不一，这里统一归一。
 */

/** 相对路径补全为绝对地址；base 为空或 url 为空返回 undefined（调用方据此省略标签） */
export function absoluteUrl(url: string | null | undefined, base: string): string | undefined {
  if (!url) return undefined
  if (/^https?:\/\//i.test(url)) return url
  // 协议相对地址 //cdn.com/x：补 https（og:image 等场景要求绝对地址）
  if (url.startsWith('//')) return `https:${url}`
  if (!base) return undefined
  return `${base}${url.startsWith('/') ? '' : '/'}${url}`
}

/** 归一化为 ISO 8601（OG article:* 与 JSON-LD date 字段要求）；无法解析返回 undefined */
export function isoDateTime(value: string | null | undefined): string | undefined {
  if (!value) return undefined
  const time = new Date(value).getTime()
  return Number.isNaN(time) ? undefined : new Date(time).toISOString()
}

/** meta description 截断（搜索结果展示约 160 字符为宜）；空值返回 undefined 走兜底 */
export function truncateText(text: string | null | undefined, max = 160): string | undefined {
  const trimmed = text?.replace(/\s+/g, ' ').trim()
  if (!trimmed) return undefined
  return trimmed.length <= max ? trimmed : `${trimmed.slice(0, max - 1)}…`
}

/** 站点根地址：优先 site.url（SITE_URL 环境变量），未配置回落当前 origin（dev/预览兜底） */
export function siteOrigin(siteUrl: string | null | undefined): string {
  if (siteUrl) return siteUrl.replace(/\/+$/, '')
  return typeof window !== 'undefined' ? window.location.origin : ''
}

/** 站内路径拼绝对地址（canonical / og:url / JSON-LD url 共用）；path 须以 / 开头 */
export function siteUrl(path: string, origin: string): string | undefined {
  if (!origin || !path.startsWith('/')) return undefined
  return `${origin}${path}`
}
