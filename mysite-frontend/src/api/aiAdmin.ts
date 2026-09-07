import { get, getPaginated } from './client'
import type { PaginatedResponse } from '@/types'

export type LlmCallType = 'CHAT' | 'REWRITE' | 'CLASSIFY' | 'EMBED' | 'RERANK'

export interface LlmUsageLog {
  id: string
  traceId: string
  callType: LlmCallType | string
  provider: string
  model: string
  userId?: string | null
  username?: string | null
  visitorId?: string | null
  userRole?: string | null
  conversationId?: string | null
  documentId?: string | null
  promptTokens: number
  completionTokens: number
  totalTokens: number
  tokenSource: 'API' | 'ESTIMATED' | string
  cost: number
  currency: string
  latencyMs?: number | null
  success: boolean
  errorMessage?: string | null
  createTime: string
}

export interface LlmUsageSummary {
  totalCost: number
  totalTokens: number
  totalCalls: number
  successRate: number
  currency: string
  series: { day: string; cost: number; tokens: number; calls: number }[]
  byModel: { name: string; provider?: string; cost: number; tokens: number; calls: number }[]
  byCallType: { name: string; cost: number; tokens: number; calls: number }[]
  byUser: { actor: string; userId?: string | null; visitorId?: string | null; cost: number; tokens: number; calls: number }[]
}

export interface LlmProviderView {
  name: string
  enabled: boolean
  priority: number
  baseUrl?: string
  apiKeyMasked?: string
  chatModel?: string
  embeddingModel?: string
  rerankModel?: string
  configured: boolean
}

export interface UsageQuery {
  page?: number
  size?: number
  from?: string
  to?: string
  callType?: string
  provider?: string
  model?: string
  keyword?: string
  success?: boolean | ''
  traceId?: string
}

export function getAiUsageLogs(params: UsageQuery = {}): Promise<PaginatedResponse<LlmUsageLog>> {
  return getPaginated<LlmUsageLog>('/v1/admin/ai/logs', {
    current: params.page || 1,
    size: params.size || 20,
    from: params.from,
    to: params.to,
    callType: params.callType || undefined,
    provider: params.provider || undefined,
    model: params.model || undefined,
    keyword: params.keyword || undefined,
    success: params.success === '' || params.success === undefined ? undefined : params.success,
    traceId: params.traceId || undefined,
  })
}

export function getAiUsageSummary(params?: { from?: string; to?: string }): Promise<LlmUsageSummary> {
  return get<LlmUsageSummary>('/v1/admin/ai/summary', params)
}

export function getAiProviders(): Promise<LlmProviderView[]> {
  return get<LlmProviderView[]>('/v1/admin/ai/providers')
}
