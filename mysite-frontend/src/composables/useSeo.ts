import { toValue, type MaybeRefOrGetter } from 'vue'
import { useHead } from '@unhead/vue'
import { useSiteStore } from '@/stores/site'
import { absoluteUrl, isoDateTime, siteOrigin, siteUrl, truncateText } from '@/utils/seo'

export interface SeoOptions {
  /** 页面标题（不含站名，App.vue titleTemplate 统一拼「标题 - 站名」；缺省=纯站名） */
  title?: MaybeRefOrGetter<string | undefined>
  /** meta description；缺省/为空时回落站点 description */
  description?: MaybeRefOrGetter<string | undefined>
  /** og:image；相对路径按站点域名补全 */
  image?: MaybeRefOrGetter<string | null | undefined>
  /** og:type，默认 website */
  ogType?: MaybeRefOrGetter<'website' | 'article' | undefined>
  /** name=keywords */
  keywords?: MaybeRefOrGetter<string | undefined>
  /** robots noindex（登录/搜索/收藏/后台等不该被收录的页面） */
  noindex?: MaybeRefOrGetter<boolean | undefined>
  /** 文章发布时间/修改时间（OG article:* 与 JSON-LD 共用，输出 ISO 8601） */
  publishedTime?: MaybeRefOrGetter<string | null | undefined>
  modifiedTime?: MaybeRefOrGetter<string | null | undefined>
  /** article:section / article:author（仅 ogType=article 时输出） */
  section?: MaybeRefOrGetter<string | undefined>
  author?: MaybeRefOrGetter<string | undefined>
  /** article:tag（字符串数组，顿号拼接） */
  tags?: MaybeRefOrGetter<string[] | undefined>
  /** JSON-LD 结构化数据（WebSite / BlogPosting 等），对象形式传入自动序列化 */
  jsonLd?: MaybeRefOrGetter<Record<string, unknown> | null | undefined>
}

/**
 * 页面级 SEO 入口：统一产出 title / description / og:* / article:* / robots / JSON-LD。
 * canonical 与 og:url 由 App.vue 按当前路由全局维护，这里不重复。
 */
export function useSeo(options: SeoOptions = {}) {
  const siteStore = useSiteStore()

  useHead(() => {
    const site = siteStore.site
    const origin = siteOrigin(site.url)
    const title = toValue(options.title)
    const description = truncateText(toValue(options.description)) || truncateText(site.description)
    const image = absoluteUrl(toValue(options.image), origin)
    const ogType = toValue(options.ogType) ?? 'website'
    const keywords = toValue(options.keywords)
    const noindex = toValue(options.noindex)
    const jsonLd = toValue(options.jsonLd)

    const meta: { name?: string; property?: string; content: string }[] = []
    if (description) meta.push({ name: 'description', content: description })
    if (keywords) meta.push({ name: 'keywords', content: keywords })
    if (noindex) meta.push({ name: 'robots', content: 'noindex' })

    const ogTitle = title || site.title
    if (ogTitle) meta.push({ property: 'og:title', content: ogTitle })
    if (description) meta.push({ property: 'og:description', content: description })
    meta.push({ property: 'og:type', content: ogType })
    if (image) meta.push({ property: 'og:image', content: image })

    if (ogType === 'article') {
      const published = isoDateTime(toValue(options.publishedTime))
      const modified = isoDateTime(toValue(options.modifiedTime))
      const author = toValue(options.author)
      const section = toValue(options.section)
      const articleTags = toValue(options.tags)
      if (published) meta.push({ property: 'article:published_time', content: published })
      if (modified) meta.push({ property: 'article:modified_time', content: modified })
      if (author) meta.push({ property: 'article:author', content: author })
      if (section) meta.push({ property: 'article:section', content: section })
      if (articleTags?.length) meta.push({ property: 'article:tag', content: articleTags.join('、') })
    }

    return {
      title,
      meta,
      script: jsonLd
        ? [{ type: 'application/ld+json', key: 'seo-jsonld', innerHTML: JSON.stringify(jsonLd).replace(/</g, '\\u003c') }]
        : [],
    }
  })
}

/** 站点根地址（SITE_URL 优先，空则当前 origin）；供 JSON-LD 等需要拼 URL 的场景使用 */
export function useSiteOrigin() {
  const siteStore = useSiteStore()
  return () => siteOrigin(siteStore.site.url)
}

export { siteUrl, absoluteUrl, isoDateTime, truncateText }
export type { MaybeRefOrGetter }
