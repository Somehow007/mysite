import { del, get, getPaginated, post, put } from './client'
import apiClient from './client'
import type { ApiResponse, PaginatedResponse } from '@/types'

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
  cacheReadTokens?: number | null
  cacheWriteTokens?: number | null
  priceId?: string | null
  peak?: boolean | null
  tokenSource: 'API' | 'ESTIMATED' | string
  cost: number
  currency: string
  latencyMs?: number | null
  success: boolean
  errorMessage?: string | null
  createTime: string
}

export interface LlmUsageSummary {
  totalCalls: number
  successRate: number
  byCurrency: {
    currency: string
    totalCost: number
    totalTokens: number
    totalCalls: number
    successCalls: number
  }[]
  series: { day: string; currency: string; cost: number; tokens: number; calls: number }[]
  byModel: { name: string; provider?: string; currency: string; cost: number; tokens: number; calls: number }[]
  byCallType: { name: string; currency: string; cost: number; tokens: number; calls: number }[]
  byUser: { actor: string; userId?: string | null; visitorId?: string | null; currency: string; cost: number; tokens: number; calls: number }[]
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

// ── 价卡 ──

export interface LlmModelPrice {
  id: string
  provider: string
  model: string
  displayName?: string | null
  currency: string
  unit: string
  inputPrice?: number | null
  cacheReadPrice?: number | null
  cacheReadFactor?: number | null
  cacheWritePrice?: number | null
  cacheWriteFactor?: number | null
  outputPrice?: number | null
  peakWindows?: string | null
  offPeakFactor?: number | null
  excludeHolidays?: boolean | null
  tiers?: string | null
  effectiveFrom?: string | null
  effectiveTo?: string | null
  source?: string | null
  note?: string | null
  current?: boolean | null
}

export interface LlmModelPriceUpsert {
  provider?: string
  model?: string
  displayName?: string | null
  currency?: string
  inputPrice?: number | null
  cacheReadPrice?: number | null
  cacheReadFactor?: number | null
  cacheWritePrice?: number | null
  cacheWriteFactor?: number | null
  outputPrice?: number | null
  peakWindows?: string | null
  offPeakFactor?: number | null
  excludeHolidays?: boolean | null
  tiers?: string | null
  effectiveFrom?: string | null
  note?: string | null
}

// ── 节假日 ──

export interface LlmHoliday {
  holidayDate: string
  name?: string | null
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

// ── 价卡 / 节假日 ──

export function getAiPrices(): Promise<LlmModelPrice[]> {
  return get<LlmModelPrice[]>('/v1/admin/ai/prices')
}

export function createAiPrice(body: LlmModelPriceUpsert): Promise<LlmModelPrice[]> {
  return post<LlmModelPrice[]>('/v1/admin/ai/prices', body)
}

export function reviseAiPrice(id: string, body: LlmModelPriceUpsert): Promise<LlmModelPrice[]> {
  return put<LlmModelPrice[]>(`/v1/admin/ai/prices/${encodeURIComponent(id)}`, body)
}

export function retireAiPrice(id: string): Promise<LlmModelPrice[]> {
  return del<LlmModelPrice[]>(`/v1/admin/ai/prices/${encodeURIComponent(id)}`)
}

export function getAiHolidays(): Promise<LlmHoliday[]> {
  return get<LlmHoliday[]>('/v1/admin/ai/holidays')
}

export function replaceAiHolidays(items: LlmHoliday[]): Promise<LlmHoliday[]> {
  return put<LlmHoliday[]>('/v1/admin/ai/holidays', { items })
}

// ── 官方账单对账 ──

export interface LlmBillingBalance {
  currency: string
  totalBalance?: string | null
  grantedBalance?: string | null
  toppedUpBalance?: string | null
}

export interface LlmBillingDelta {
  fromSnapshotId: string
  fromTime: string
  periodSpend?: Record<string, string>
  fromTotal?: Record<string, string>
  toTotal?: Record<string, string>
}

export interface LlmBillingProvider {
  provider: string
  ok: boolean
  latencyMs?: number | null
  message?: string | null
  data?: Record<string, unknown> | null
  delta?: LlmBillingDelta | null
}

export interface LlmBilling {
  probedAt: string
  providers: LlmBillingProvider[]
}

export async function getAiBilling(): Promise<LlmBilling> {
  // 探针逐 provider 串行调官方接口（单家最长 ~10s），给足超时
  const response = await apiClient.get<ApiResponse<LlmBilling>>('/v1/admin/ai/billing', { timeout: 45000 })
  return response.data.data as LlmBilling
}
