import { get, getPaginated, post, put } from './client'
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
  embeddingDimension?: number
  rerankModel?: string
  configured: boolean
  envApiKeyName?: string | null
  envChatModelName?: string | null
  envFile?: string | null
  runtimeBound: boolean
  persisted: boolean
  requiresApiKey: boolean
}

export interface LlmProviderUpdate {
  enabled?: boolean
  priority?: number
  baseUrl?: string
  chatModel?: string
  embeddingModel?: string
  embeddingDimension?: number
  rerankModel?: string
  apiKey?: string
}

export interface LlmProviderPing {
  ok: boolean
  provider: string
  model?: string | null
  preview?: string | null
  latencyMs: number
  message?: string | null
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

export function updateAiProvider(name: string, body: LlmProviderUpdate): Promise<LlmProviderView[]> {
  return put<LlmProviderView[]>(`/v1/admin/ai/providers/${encodeURIComponent(name)}`, body)
}

export function pingAiProvider(name: string): Promise<LlmProviderPing> {
  return post<LlmProviderPing>(`/v1/admin/ai/providers/${encodeURIComponent(name)}/ping`, undefined, { timeout: 25000 })
}
