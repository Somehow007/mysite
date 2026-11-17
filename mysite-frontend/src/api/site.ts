import { get, put } from './client'
import type { SiteConfig } from '@/types'

export interface SiteConfigUpdate {
  title?: string
  subtitle?: string
  description?: string
  author?: string
  authorBio?: string
  authorTagline?: string
  aboutContent?: string
  techStack?: string
  avatar?: string
  url?: string
  github?: string
  rss?: string
  commentEnabled?: boolean
}

export function getSiteConfig(): Promise<SiteConfig> {
  return get<SiteConfig>('/v1/site/config')
}

export function updateSiteConfig(data: SiteConfigUpdate): Promise<SiteConfig> {
  return put<SiteConfig>('/v1/site/config', data)
}
