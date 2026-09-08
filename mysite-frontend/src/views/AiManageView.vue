<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import type { Component } from 'vue'
import { useHead } from '@unhead/vue'
import {
  Bot, Coins, Cpu, List, PieChart, Activity, CheckCircle2,
  BarChart3, Users,
} from 'lucide-vue-next'
import {
  getAiProviders, getAiUsageLogs, getAiUsageSummary,
  type LlmProviderView, type LlmUsageLog, type LlmUsageSummary,
} from '@/api/aiAdmin'
import { formatDateTime } from '@/utils/date'
import {
  Badge, DataTable, EmptyState, PageHeader, SearchFilterBar, StatsCard,
} from '@/components/ui'
import AiProviderCard from '@/components/dashboard/AiProviderCard.vue'
import type { Column } from '@/components/ui/DataTable.vue'

useHead(() => ({ title: 'AI 管理 - MySite' }))

type Tab = 'logs' | 'dashboard' | 'providers'
const tab = ref<Tab>('logs')

const TABS: { id: Tab; label: string; icon: Component }[] = [
  { id: 'logs', label: '调用记录', icon: List },
  { id: 'dashboard', label: '消费看板', icon: BarChart3 },
  { id: 'providers', label: '模型与 API', icon: Cpu },
]

const CALL_TYPE_LABEL: Record<string, string> = {
  CHAT: '对话生成',
  REWRITE: '查询改写',
  CLASSIFY: '意图分类',
  EMBED: '向量化',
  RERANK: '精排',
}

const CALL_TYPE_BADGE: Record<string, 'accent' | 'info' | 'warning' | 'success' | 'muted'> = {
  CHAT: 'accent',
  REWRITE: 'info',
  CLASSIFY: 'warning',
  EMBED: 'success',
  RERANK: 'muted',
}

const CALL_TYPE_COLOR: Record<string, string> = {
  CHAT: 'var(--accent)',
  REWRITE: 'var(--info)',
  CLASSIFY: 'var(--warning)',
  EMBED: 'var(--success)',
  RERANK: 'var(--text-muted)',
}

const MODEL_BAR_COLORS = ['var(--accent)', 'var(--info)', 'var(--success)', 'var(--warning)', 'var(--text-muted)']

function actorOf(row: LlmUsageLog) {
  if (row.username) return row.username
  if (row.visitorId) return `访客 ${row.visitorId.slice(0, 8)}`
  return '匿名'
}

function formatCost(n: number | null | undefined) {
  if (n == null || Number.isNaN(Number(n))) return '—'
  const v = Number(n)
  if (v === 0) return '¥0'
  if (v < 0.0001) return `¥${v.toFixed(6)}`
  if (v < 0.01) return `¥${v.toFixed(4)}`
  return `¥${v.toFixed(4)}`
}

function formatTokens(n: number | null | undefined) {
  return (n ?? 0).toLocaleString()
}

function isoRange(preset: 'today' | '7d' | '30d' | 'month') {
  const to = new Date()
  const from = new Date()
  if (preset === 'today') {
    from.setHours(0, 0, 0, 0)
  } else if (preset === '7d') {
    from.setTime(to.getTime() - 7 * 86400000)
  } else if (preset === '30d') {
    from.setTime(to.getTime() - 30 * 86400000)
  } else {
    from.setDate(1)
    from.setHours(0, 0, 0, 0)
  }
  return { from: from.toISOString(), to: to.toISOString() }
}

// ── Logs ──
const keyword = ref('')
const callType = ref('')
const successFilter = ref('')
const logs = ref<LlmUsageLog[]>([])
const logsTotal = ref(0)
const logsPage = ref(1)
const logsLoading = ref(false)
const expandedId = ref<string | null>(null)
const relatedLogs = ref<LlmUsageLog[]>([])
const relatedLoading = ref(false)

async function fetchLogs(page = 1) {
  logsLoading.value = true
  try {
    const res = await getAiUsageLogs({
      page,
      size: 20,
      keyword: keyword.value.trim() || undefined,
      callType: callType.value || undefined,
      success: successFilter.value === '' ? undefined : successFilter.value === 'true',
    })
    logs.value = res.list
    logsTotal.value = res.pagination.total
    logsPage.value = res.pagination.page
  } catch {
    logs.value = []
    logsTotal.value = 0
  } finally {
    logsLoading.value = false
  }
}

function handleLogReset() {
  keyword.value = ''
  callType.value = ''
  successFilter.value = ''
  expandedId.value = null
  fetchLogs(1)
}

async function toggleExpand(id: string) {
  if (expandedId.value === id) {
    expandedId.value = null
    relatedLogs.value = []
    return
  }
  expandedId.value = id
  const row = logs.value.find(r => r.id === id)
  if (!row?.traceId) {
    relatedLogs.value = []
    return
  }
  relatedLoading.value = true
  try {
    const res = await getAiUsageLogs({ page: 1, size: 50, traceId: row.traceId })
    relatedLogs.value = res.list
  } catch {
    relatedLogs.value = []
  } finally {
    relatedLoading.value = false
  }
}

const logColumns: Column<LlmUsageLog>[] = [
  { key: 'createTime', label: '时间', width: '158px' },
  { key: 'actor', label: '谁', width: '112px' },
  { key: 'callType', label: '类型', width: '96px' },
  { key: 'model', label: '模型' },
  { key: 'tokens', label: 'Token', width: '118px', align: 'right' },
  { key: 'cost', label: '费用', width: '88px', align: 'right' },
  { key: 'latencyMs', label: '耗时', width: '72px', align: 'right', hideMobile: true },
  { key: 'success', label: '状态', width: '76px' },
]

// ── Dashboard ──
const dashPreset = ref<'today' | '7d' | '30d' | 'month'>('7d')
const summary = ref<LlmUsageSummary | null>(null)
const summaryLoading = ref(false)

async function fetchSummary() {
  summaryLoading.value = true
  try {
    summary.value = await getAiUsageSummary(isoRange(dashPreset.value))
  } catch {
    summary.value = null
  } finally {
    summaryLoading.value = false
  }
}

function setPreset(p: 'today' | '7d' | '30d' | 'month') {
  clearHover()
  dashPreset.value = p
  fetchSummary()
}

const maxDailyCost = computed(() => {
  const series = summary.value?.series ?? []
  return Math.max(...series.map(s => Number(s.cost) || 0), 0.000001)
})

const hoverPoint = ref<LlmUsageSummary['series'][number] | null>(null)
const hoverSeg = ref<string | null>(null)
const hoverModelKey = ref<string | null>(null)
const hoverUserKey = ref<string | null>(null)

interface DashTip {
  x: number
  y: number
  title: string
  subtitle?: string
  accent?: string
  rows: { label: string; value: string }[]
}
const dashTip = ref<DashTip | null>(null)

function placeTip(e: MouseEvent, data: Omit<DashTip, 'x' | 'y'>) {
  const w = 200
  const h = 36 + data.rows.length * 22 + (data.subtitle ? 16 : 0)
  let x = e.clientX + 14
  let y = e.clientY + 16
  if (x + w > window.innerWidth - 10) x = e.clientX - w - 12
  if (y + h > window.innerHeight - 10) y = e.clientY - h - 12
  dashTip.value = { ...data, x, y }
}

function hideTip() {
  dashTip.value = null
}

function clearHover() {
  hoverPoint.value = null
  hoverSeg.value = null
  hoverModelKey.value = null
  hoverUserKey.value = null
  hideTip()
}

function formatFullDay(day: string) {
  const d = new Date(`${day}T00:00:00`)
  if (Number.isNaN(d.getTime())) return day
  const week = '日一二三四五六'[d.getDay()]
  return `${d.getFullYear()}年${d.getMonth() + 1}月${d.getDate()}日 周${week}`
}

function avgCostPerCall(cost: number, calls: number) {
  if (!calls) return '—'
  return formatCost(Number(cost) / calls)
}

function barTipData(point: LlmUsageSummary['series'][number]): Omit<DashTip, 'x' | 'y'> {
  const series = summary.value?.series ?? []
  return {
    title: formatFullDay(point.day),
    accent: 'var(--accent)',
    rows: [
      { label: '费用', value: formatCost(point.cost) },
      { label: '调用', value: `${formatTokens(point.calls)} 次` },
      { label: 'Token', value: formatTokens(point.tokens) },
      { label: '占区间费用', value: `${sliceShare(point.cost, series).toFixed(1)}%` },
      { label: '相对峰值', value: `${Math.round((Number(point.cost) / maxDailyCost.value) * 100)}%` },
    ],
  }
}

function modelTipData(s: LlmUsageSummary['byModel'][number], idx: number): Omit<DashTip, 'x' | 'y'> {
  const list = summary.value?.byModel ?? []
  return {
    title: s.name,
    subtitle: s.provider || undefined,
    accent: MODEL_BAR_COLORS[idx % MODEL_BAR_COLORS.length],
    rows: [
      { label: '费用', value: formatCost(s.cost) },
      { label: '占比', value: `${sliceShare(s.cost, list).toFixed(1)}%` },
      { label: '调用', value: `${formatTokens(s.calls)} 次` },
      { label: 'Token', value: formatTokens(s.tokens) },
      { label: '次均费用', value: avgCostPerCall(s.cost, s.calls) },
    ],
  }
}

function typeTipData(s: {
  name: string
  cost: number
  tokens: number
  calls: number
  pct?: number
  color?: string
}): Omit<DashTip, 'x' | 'y'> {
  const pct = s.pct != null ? s.pct * 100 : sliceShare(s.cost, summary.value?.byCallType ?? [])
  return {
    title: CALL_TYPE_LABEL[s.name] || s.name,
    accent: s.color || CALL_TYPE_COLOR[s.name] || 'var(--accent)',
    rows: [
      { label: '费用', value: formatCost(s.cost) },
      { label: '占比', value: `${pct.toFixed(1)}%` },
      { label: '调用', value: `${formatTokens(s.calls)} 次` },
      { label: 'Token', value: formatTokens(s.tokens) },
      { label: '次均费用', value: avgCostPerCall(s.cost, s.calls) },
    ],
  }
}

function userTipData(u: LlmUsageSummary['byUser'][number]): Omit<DashTip, 'x' | 'y'> {
  const list = summary.value?.byUser ?? []
  return {
    title: u.actor,
    accent: 'var(--accent)',
    rows: [
      { label: '费用', value: formatCost(u.cost) },
      { label: '占用户费用', value: `${sliceShare(u.cost, list).toFixed(1)}%` },
      { label: '调用', value: `${formatTokens(u.calls)} 次` },
      { label: 'Token', value: formatTokens(u.tokens) },
      { label: '次均费用', value: avgCostPerCall(u.cost, u.calls) },
    ],
  }
}

function logTipData(item: LlmUsageLog): Omit<DashTip, 'x' | 'y'> {
  const rows = [
    { label: '类型', value: CALL_TYPE_LABEL[item.callType] || item.callType },
    { label: '输入 Token', value: formatTokens(item.promptTokens) },
    { label: '输出 Token', value: formatTokens(item.completionTokens) },
    { label: '费用', value: formatCost(item.cost) },
    { label: '耗时', value: item.latencyMs != null ? `${item.latencyMs}ms` : '—' },
    { label: '计量', value: item.tokenSource === 'ESTIMATED' ? '估算' : 'API 实计' },
  ]
  if (item.errorMessage) {
    rows.push({ label: '错误', value: item.errorMessage.slice(0, 80) })
  }
  return {
    title: actorOf(item),
    subtitle: `${item.model} · ${item.provider}`,
    accent: item.success ? 'var(--success)' : 'var(--danger)',
    rows,
  }
}

function peakDay() {
  const series = summary.value?.series ?? []
  if (!series.length) return null
  return series.reduce((a, b) => (Number(a.cost) >= Number(b.cost) ? a : b))
}

function costCardTip(): Omit<DashTip, 'x' | 'y'> {
  const s = summary.value
  const peak = peakDay()
  return {
    title: '费用',
    accent: 'var(--accent)',
    rows: [
      { label: '合计', value: formatCost(s?.totalCost ?? 0) },
      { label: '日均', value: formatCost(avgOf(s?.totalCost ?? 0)) },
      { label: '峰值日', value: peak ? `${String(peak.day).slice(5)} ${formatCost(peak.cost)}` : '—' },
    ],
  }
}

function tokenCardTip(): Omit<DashTip, 'x' | 'y'> {
  const s = summary.value
  return {
    title: 'Token',
    accent: 'var(--info)',
    rows: [
      { label: '合计', value: formatTokens(s?.totalTokens ?? 0) },
      { label: '日均', value: formatTokens(Math.round(avgOf(s?.totalTokens ?? 0))) },
      { label: '次均', value: s?.totalCalls ? formatTokens(Math.round((s.totalTokens || 0) / s.totalCalls)) : '—' },
    ],
  }
}

function callCardTip(): Omit<DashTip, 'x' | 'y'> {
  const s = summary.value
  return {
    title: '调用次数',
    accent: 'var(--success)',
    rows: [
      { label: '合计', value: `${formatTokens(s?.totalCalls ?? 0)} 次` },
      { label: '日均', value: `${formatTokens(Math.round(avgOf(s?.totalCalls ?? 0)))} 次` },
      { label: '次均费用', value: avgCostPerCall(s?.totalCost ?? 0, s?.totalCalls ?? 0) },
    ],
  }
}

function successCardTip(): Omit<DashTip, 'x' | 'y'> {
  const s = summary.value
  const total = s?.totalCalls ?? 0
  const failed = Math.round(total * (1 - (s?.successRate ?? 0) / 100))
  return {
    title: '成功率',
    accent: successTone.value === 'success' ? 'var(--success)' : successTone.value === 'warning' ? 'var(--warning)' : 'var(--danger)',
    rows: [
      { label: '成功率', value: `${(s?.successRate ?? 0).toFixed(1)}%` },
      { label: '成功', value: `${formatTokens(total - failed)} 次` },
      { label: '失败', value: `${formatTokens(failed)} 次` },
    ],
  }
}

const seriesBarMaxWidth = computed(() => {
  const n = summary.value?.series.length ?? 0
  if (n <= 8) return '52px'
  if (n <= 14) return '36px'
  return '100%'
})

const chartCompact = computed(() => (summary.value?.series.length ?? 0) <= 14)

const successTone = computed<'success' | 'warning' | 'danger'>(() => {
  const rate = summary.value?.successRate ?? 0
  if (rate >= 95) return 'success'
  if (rate >= 80) return 'warning'
  return 'danger'
})

const daysInRange = computed(() => {
  if (dashPreset.value === 'today') return 1
  if (dashPreset.value === '7d') return 7
  if (dashPreset.value === '30d') return 30
  return Math.max(new Date().getDate(), 1)
})

const donutSegments = computed(() => {
  const slices = summary.value?.byCallType ?? []
  const total = slices.reduce((s, x) => s + (Number(x.cost) || 0), 0) || 1
  let acc = 0
  return slices.map(s => {
    const pct = (Number(s.cost) || 0) / total
    const start = acc
    acc += pct
    return {
      ...s,
      pct,
      start,
      color: CALL_TYPE_COLOR[s.name] || 'var(--text-muted)',
    }
  })
})

function barHeightPct(cost: number) {
  return Math.max(6, (Number(cost) / maxDailyCost.value) * 100)
}

function sliceMax(slices: { cost: number }[]) {
  return Math.max(...slices.map(s => Number(s.cost) || 0), 0.000001)
}

function sliceShare(cost: number, slices: { cost: number }[]) {
  const total = slices.reduce((s, x) => s + (Number(x.cost) || 0), 0)
  if (!total) return 0
  return (Number(cost) / total) * 100
}

function dayTick(day: string, index: number, total: number) {
  const mmdd = String(day).slice(5)
  if (total <= 10) return mmdd
  const step = total <= 16 ? 2 : Math.ceil(total / 8)
  if (index === 0 || index === total - 1 || index % step === 0) return mmdd
  return ''
}

function avgOf(total: number) {
  return total / daysInRange.value
}

const hoverSegData = computed(() => donutSegments.value.find(s => s.name === hoverSeg.value) ?? null)

// ── Providers ──
const providers = ref<LlmProviderView[]>([])
const providersLoading = ref(false)

async function fetchProviders() {
  providersLoading.value = true
  try {
    providers.value = await getAiProviders()
  } catch {
    providers.value = []
  } finally {
    providersLoading.value = false
  }
}

function switchTab(next: Tab) {
  clearHover()
  tab.value = next
  if (next === 'logs' && logs.value.length === 0) fetchLogs(1)
  if (next === 'dashboard' && !summary.value) fetchSummary()
  if (next === 'providers') fetchProviders()
}

function onProvidersUpdated(list: LlmProviderView[]) {
  providers.value = list
}

const providersEnvFile = computed(() => providers.value[0]?.envFile || '')

onMounted(() => {
  fetchLogs(1)
  fetchSummary()
})

onBeforeUnmount(() => {
  clearHover()
})
</script>

<template>
  <div>
    <PageHeader title="AI 管理" subtitle="LLM 调用记录、用量与供应商配置" />

    <div class="flex items-center gap-1 border-b border-border mb-5 overflow-x-auto">
      <button
        v-for="t in TABS"
        :key="t.id"
        class="flex items-center gap-2 px-4 py-2.5 text-sm font-medium whitespace-nowrap transition-colors border-b-2 -mb-px"
        :class="tab === t.id ? 'border-accent text-accent' : 'border-transparent text-text-muted hover:text-text-secondary'"
        @click="switchTab(t.id)"
      >
        <component :is="t.icon" :size="14" />
        {{ t.label }}
      </button>
    </div>

    <!-- ════ 调用记录 ════ -->
    <template v-if="tab === 'logs'">
      <SearchFilterBar
        v-model="keyword"
        placeholder="用户名 / visitorId"
        :show-sort="false"
        :show-filter="true"
        :filter-options="[
          { label: '全部类型', value: '' },
          { label: '对话生成', value: 'CHAT' },
          { label: '查询改写', value: 'REWRITE' },
          { label: '意图分类', value: 'CLASSIFY' },
          { label: '向量化', value: 'EMBED' },
          { label: '精排', value: 'RERANK' },
        ]"
        :filter-value="callType"
        class="mb-4"
        @update:filter-value="callType = $event"
        @search="fetchLogs(1)"
        @reset="handleLogReset"
      >
        <select
          v-model="successFilter"
          class="px-3 py-2 text-sm rounded-lg border border-border bg-bg-secondary text-text-primary focus:outline-none focus:ring-2 focus:ring-accent/50 focus:border-accent"
        >
          <option value="">全部状态</option>
          <option value="true">成功</option>
          <option value="false">失败</option>
        </select>
      </SearchFilterBar>

      <DataTable
        :columns="logColumns"
        :data="logs"
        :loading="logsLoading"
        :total="logsTotal"
        :current-page="logsPage"
        :page-size="20"
        expandable
        :expanded-id="expandedId"
        empty-title="还没有调用记录"
        empty-description="用户提问或文章入库后，计费调用会显示在这里"
        :empty-icon="List"
        @update:current-page="fetchLogs"
        @toggle-expand="toggleExpand"
      >
        <template #cell-createTime="{ item }">
          <span class="text-xs tabular-nums text-text-secondary">{{ formatDateTime(item.createTime) }}</span>
        </template>
        <template #cell-actor="{ item }">
          <div
            class="min-w-0"
            @mousemove="(e) => placeTip(e, logTipData(item))"
            @mouseleave="hideTip"
          >
            <p class="truncate text-sm text-text-primary">{{ actorOf(item) }}</p>
            <p v-if="item.userRole" class="text-[11px] text-text-muted">{{ item.userRole }}</p>
          </div>
        </template>
        <template #cell-callType="{ item }">
          <Badge size="sm" :variant="CALL_TYPE_BADGE[item.callType] || 'muted'">
            {{ CALL_TYPE_LABEL[item.callType] || item.callType }}
          </Badge>
        </template>
        <template #cell-model="{ item }">
          <div class="min-w-0">
            <p class="truncate text-sm text-text-primary" :title="item.model">{{ item.model }}</p>
            <p class="text-[11px] text-text-muted">{{ item.provider }}</p>
          </div>
        </template>
        <template #cell-tokens="{ item }">
          <span
            class="text-xs tabular-nums"
            @mousemove="(e) => placeTip(e, logTipData(item))"
            @mouseleave="hideTip"
          >
            {{ formatTokens(item.promptTokens) }} / {{ formatTokens(item.completionTokens) }}
            <span v-if="item.tokenSource === 'ESTIMATED'" class="text-text-muted">估</span>
          </span>
        </template>
        <template #cell-cost="{ item }">
          <span
            class="text-xs tabular-nums"
            @mousemove="(e) => placeTip(e, logTipData(item))"
            @mouseleave="hideTip"
          >{{ formatCost(item.cost) }}</span>
        </template>
        <template #cell-latencyMs="{ item }">
          <span class="text-xs tabular-nums text-text-secondary">{{ item.latencyMs != null ? `${item.latencyMs}ms` : '—' }}</span>
        </template>
        <template #cell-success="{ item }">
          <Badge :variant="item.success ? 'success' : 'danger'" dot>
            {{ item.success ? '成功' : '失败' }}
          </Badge>
        </template>
        <template #mobile-card="{ item }">
          <div class="flex items-start justify-between gap-2">
            <div class="min-w-0">
              <p class="text-sm font-medium text-text-primary truncate">{{ actorOf(item) }}</p>
              <p class="text-xs text-text-muted tabular-nums">{{ formatDateTime(item.createTime) }}</p>
            </div>
            <Badge :variant="item.success ? 'success' : 'danger'" dot>
              {{ item.success ? '成功' : '失败' }}
            </Badge>
          </div>
          <div class="mt-2 flex flex-wrap items-center gap-2 text-xs text-text-secondary">
            <Badge size="sm" :variant="CALL_TYPE_BADGE[item.callType] || 'muted'">
              {{ CALL_TYPE_LABEL[item.callType] || item.callType }}
            </Badge>
            <span class="truncate">{{ item.model }}</span>
            <span class="tabular-nums ml-auto">{{ formatCost(item.cost) }}</span>
          </div>
        </template>
        <template #expand="{ item }">
          <div class="px-4 py-3 text-sm bg-bg-code/40">
            <p class="text-xs text-text-muted mb-2 font-mono">trace {{ item.traceId }}</p>
            <p v-if="relatedLoading" class="text-text-muted">加载关联调用…</p>
            <ul v-else class="space-y-1.5">
              <li
                v-for="rel in relatedLogs"
                :key="rel.id"
                class="flex flex-wrap items-center gap-2 text-xs"
              >
                <Badge size="sm" :variant="CALL_TYPE_BADGE[rel.callType] || 'muted'">
                  {{ CALL_TYPE_LABEL[rel.callType] || rel.callType }}
                </Badge>
                <span class="truncate">{{ rel.model }}</span>
                <span class="tabular-nums text-text-secondary">{{ formatTokens(rel.totalTokens) }} tok</span>
                <span class="tabular-nums">{{ formatCost(rel.cost) }}</span>
                <span class="text-text-muted">{{ rel.latencyMs }}ms</span>
              </li>
            </ul>
          </div>
        </template>
      </DataTable>
    </template>

    <!-- ════ 消费看板 ════ -->
    <template v-else-if="tab === 'dashboard'">
      <div class="flex flex-wrap items-center justify-between gap-3 mb-4">
        <p class="text-sm text-text-secondary">按所选时间区间汇总计费调用</p>
        <div class="inline-flex rounded-lg border border-border p-0.5 bg-bg-elevated">
          <button
            v-for="p in ([
              ['today', '今天'],
              ['7d', '近 7 天'],
              ['30d', '近 30 天'],
              ['month', '本月'],
            ] as const)"
            :key="p[0]"
            class="px-3 py-1.5 text-xs rounded-md transition-colors"
            :class="dashPreset === p[0] ? 'bg-accent-subtle text-accent font-medium' : 'text-text-secondary hover:text-text-primary'"
            @click="setPreset(p[0])"
          >
            {{ p[1] }}
          </button>
        </div>
      </div>

      <div class="grid grid-cols-2 lg:grid-cols-4 gap-3 mb-4">
        <div @mousemove="(e) => placeTip(e, costCardTip())" @mouseleave="hideTip">
          <StatsCard :icon="Coins" label="费用" :value="formatCost(summary?.totalCost ?? 0)" icon-color="accent">
            <template #trend>
              <span class="text-text-muted">日均 {{ formatCost(avgOf(summary?.totalCost ?? 0)) }}</span>
            </template>
          </StatsCard>
        </div>
        <div @mousemove="(e) => placeTip(e, tokenCardTip())" @mouseleave="hideTip">
          <StatsCard :icon="Activity" label="Token" :value="formatTokens(summary?.totalTokens ?? 0)" icon-color="info">
            <template #trend>
              <span class="text-text-muted">日均 {{ formatTokens(Math.round(avgOf(summary?.totalTokens ?? 0))) }}</span>
            </template>
          </StatsCard>
        </div>
        <div @mousemove="(e) => placeTip(e, callCardTip())" @mouseleave="hideTip">
          <StatsCard :icon="Bot" label="调用次数" :value="formatTokens(summary?.totalCalls ?? 0)" icon-color="success">
            <template #trend>
              <span class="text-text-muted">日均 {{ formatTokens(Math.round(avgOf(summary?.totalCalls ?? 0))) }} 次</span>
            </template>
          </StatsCard>
        </div>
        <div @mousemove="(e) => placeTip(e, successCardTip())" @mouseleave="hideTip">
          <StatsCard
            :icon="CheckCircle2"
            label="成功率"
            :value="`${(summary?.successRate ?? 0).toFixed(1)}%`"
            :icon-color="successTone"
            :color="successTone"
          >
            <template #trend>
              <span class="text-text-muted">
                失败 {{ Math.round((summary?.totalCalls ?? 0) * (1 - (summary?.successRate ?? 0) / 100)) }} 次
              </span>
            </template>
          </StatsCard>
        </div>
      </div>

      <EmptyState
        v-if="!summaryLoading && (!summary || summary.totalCalls === 0)"
        :icon="PieChart"
        title="区间内没有用量"
        description="产生聊天或文档入库后即可看到费用趋势与拆分"
      />

      <div v-else-if="summaryLoading && !summary" class="card-solid h-56 animate-pulse bg-bg-code/50" />

      <template v-else-if="summary">
        <div class="card-solid p-5 mb-4">
          <div class="flex items-start justify-between gap-3 mb-4">
            <div>
              <h3 class="text-sm font-medium text-text-primary">按日费用</h3>
              <p class="text-xs text-text-muted mt-0.5">峰值 {{ formatCost(maxDailyCost) }} / 天</p>
            </div>
            <p class="text-xs tabular-nums text-text-secondary text-right min-h-[2.5rem]">
              <template v-if="hoverPoint">
                <span class="block text-text-primary font-medium">{{ formatFullDay(hoverPoint.day) }}</span>
                {{ formatCost(hoverPoint.cost) }} · {{ hoverPoint.calls }} 次 · {{ formatTokens(hoverPoint.tokens) }} tok
              </template>
              <template v-else>
                <span class="text-text-muted">悬停柱体查看明细</span>
              </template>
            </p>
          </div>

          <div v-if="summary.series.length === 0" class="text-sm text-text-muted py-10 text-center">暂无趋势数据</div>
          <div v-else class="relative h-52" :class="chartCompact ? 'max-w-2xl' : ''">
            <div class="hidden sm:flex absolute left-0 top-0 bottom-7 w-12 flex-col justify-between text-[10px] text-text-muted tabular-nums text-right pr-2 leading-none">
              <span>{{ formatCost(maxDailyCost) }}</span>
              <span>{{ formatCost(maxDailyCost / 2) }}</span>
              <span>¥0</span>
            </div>
            <div class="absolute inset-x-0 sm:left-12 top-0 bottom-7 flex flex-col justify-between pointer-events-none">
              <div class="border-t border-border/80" />
              <div class="border-t border-dashed border-border/60" />
              <div class="border-t border-border" />
            </div>
            <div
              class="absolute inset-x-0 sm:left-12 top-0 bottom-7 flex items-end justify-start gap-2"
              @mouseleave="hoverPoint = null; hideTip()"
            >
              <div
                v-for="point in summary.series"
                :key="point.day"
                class="relative h-full flex items-end justify-center min-w-0 cursor-pointer rounded-t-md"
                :class="hoverPoint?.day === point.day ? 'bg-accent/10' : ''"
                :style="{ flex: '1 1 0', maxWidth: seriesBarMaxWidth }"
                @mouseenter="hoverPoint = point"
                @mousemove="(e) => placeTip(e, barTipData(point))"
              >
                <div
                  class="w-[72%] rounded-t-md origin-bottom transition-all duration-150"
                  :class="hoverPoint && hoverPoint.day !== point.day ? 'bg-accent/40' : hoverPoint?.day === point.day ? 'bg-accent scale-x-105' : 'bg-accent/70'"
                  :style="{ height: barHeightPct(point.cost) + '%' }"
                />
              </div>
            </div>
            <div class="absolute inset-x-0 sm:left-12 bottom-0 h-7 flex justify-start gap-2">
              <span
                v-for="(point, i) in summary.series"
                :key="point.day"
                class="text-[10px] text-text-muted text-center truncate leading-7"
                :style="{ flex: '1 1 0', maxWidth: seriesBarMaxWidth }"
              >{{ dayTick(point.day, i, summary.series.length) }}</span>
            </div>
          </div>
        </div>

        <div class="grid lg:grid-cols-2 gap-4 mb-4 items-stretch">
          <div class="card-solid p-5">
            <h3 class="text-sm font-medium text-text-primary mb-4">按模型</h3>
            <div v-if="summary.byModel.length === 0" class="text-sm text-text-muted">暂无</div>
            <ul v-else class="space-y-4">
              <li
                v-for="(s, idx) in summary.byModel"
                :key="s.name + (s.provider || '')"
                class="rounded-lg -mx-2 px-2 py-1.5 transition-colors cursor-default"
                :class="hoverModelKey === s.name + (s.provider || '') ? 'bg-bg-code/70' : 'hover:bg-bg-code/40'"
                @mousemove="(e) => { hoverModelKey = s.name + (s.provider || ''); placeTip(e, modelTipData(s, idx)) }"
                @mouseleave="hoverModelKey = null; hideTip()"
              >
                <div class="flex items-baseline justify-between gap-3 text-xs mb-1">
                  <div class="min-w-0 flex items-center gap-2">
                    <span
                      class="w-2 h-2 rounded-full shrink-0"
                      :style="{ background: MODEL_BAR_COLORS[idx % MODEL_BAR_COLORS.length] }"
                    />
                    <span class="truncate text-text-primary">{{ s.name }}</span>
                    <span class="text-text-muted shrink-0">{{ s.provider }}</span>
                  </div>
                  <span class="tabular-nums shrink-0 text-text-primary">{{ formatCost(s.cost) }}</span>
                </div>
                <div class="h-2 rounded-full bg-bg-code overflow-hidden">
                  <div
                    class="h-full rounded-full transition-all duration-150"
                    :style="{
                      width: `${(Number(s.cost) / sliceMax(summary.byModel)) * 100}%`,
                      background: MODEL_BAR_COLORS[idx % MODEL_BAR_COLORS.length],
                      opacity: hoverModelKey && hoverModelKey !== s.name + (s.provider || '') ? 0.35 : 1,
                    }"
                  />
                </div>
                <p class="mt-1 text-[11px] text-text-muted tabular-nums">
                  {{ sliceShare(s.cost, summary.byModel).toFixed(0) }}% · {{ formatTokens(s.calls) }} 次 · {{ formatTokens(s.tokens) }} tok
                </p>
              </li>
            </ul>
          </div>

          <div class="card-solid p-5 flex flex-col">
            <h3 class="text-sm font-medium text-text-primary mb-4">按调用类型</h3>
            <div v-if="summary.byCallType.length === 0" class="text-sm text-text-muted">暂无</div>
            <div v-else class="flex-1 flex flex-col sm:flex-row items-center gap-6">
              <div class="relative w-36 h-36 shrink-0">
                <svg viewBox="0 0 36 36" class="w-full h-full -rotate-90">
                  <circle cx="18" cy="18" r="15.915" fill="none" stroke="var(--bg-code)" stroke-width="3.8" />
                  <circle
                    v-for="seg in donutSegments"
                    :key="seg.name"
                    cx="18"
                    cy="18"
                    r="15.915"
                    fill="none"
                    :stroke="seg.color"
                    class="cursor-pointer transition-[stroke-width,opacity] duration-150"
                    :stroke-width="hoverSeg === seg.name ? 5 : 3.8"
                    :stroke-dasharray="`${seg.pct * 100} ${100 - seg.pct * 100}`"
                    :stroke-dashoffset="`${-seg.start * 100}`"
                    stroke-linecap="butt"
                    pointer-events="stroke"
                    :style="{ opacity: hoverSeg && hoverSeg !== seg.name ? 0.28 : 1 }"
                    @mousemove="(e) => { hoverSeg = seg.name; placeTip(e, typeTipData(seg)) }"
                    @mouseleave="hoverSeg = null; hideTip()"
                  />
                </svg>
                <div class="absolute inset-0 flex flex-col items-center justify-center pointer-events-none px-3 text-center">
                  <template v-if="hoverSegData">
                    <span class="text-sm font-bold tabular-nums leading-none">{{ formatCost(hoverSegData.cost) }}</span>
                    <span class="text-[11px] text-text-muted mt-1 truncate w-full">{{ CALL_TYPE_LABEL[hoverSegData.name] || hoverSegData.name }}</span>
                  </template>
                  <template v-else>
                    <span class="text-lg font-bold tabular-nums leading-none">{{ formatTokens(summary.totalCalls) }}</span>
                    <span class="text-[11px] text-text-muted mt-1">次调用</span>
                  </template>
                </div>
              </div>
              <ul class="flex-1 w-full space-y-1 min-w-0">
                <li
                  v-for="s in donutSegments"
                  :key="s.name"
                  class="flex items-center gap-2.5 text-xs rounded-md px-1.5 py-1.5 -mx-1.5 cursor-default transition-colors"
                  :class="hoverSeg === s.name ? 'bg-bg-code/70' : 'hover:bg-bg-code/40'"
                  :style="{ opacity: hoverSeg && hoverSeg !== s.name ? 0.45 : 1 }"
                  @mousemove="(e) => { hoverSeg = s.name; placeTip(e, typeTipData(s)) }"
                  @mouseleave="hoverSeg = null; hideTip()"
                >
                  <span class="w-2 h-2 rounded-full shrink-0" :style="{ background: s.color }" />
                  <span class="flex-1 truncate">{{ CALL_TYPE_LABEL[s.name] || s.name }}</span>
                  <span class="tabular-nums text-text-muted shrink-0">{{ formatTokens(s.calls) }} 次</span>
                  <span class="tabular-nums shrink-0 w-[4.5rem] text-right">{{ formatCost(s.cost) }}</span>
                </li>
              </ul>
            </div>
          </div>
        </div>

        <div class="card-solid p-5 overflow-hidden">
          <div class="flex items-center gap-2 mb-3">
            <Users :size="14" class="text-text-muted" />
            <h3 class="text-sm font-medium text-text-primary">Top 用户</h3>
          </div>
          <div v-if="summary.byUser.length" class="overflow-x-auto">
            <table class="w-full text-sm table-fixed">
              <thead>
                <tr class="text-left text-xs text-text-muted border-b border-border">
                  <th class="py-2 pr-3 font-medium w-10">#</th>
                  <th class="py-2 pr-3 font-medium">谁</th>
                  <th class="py-2 pr-3 font-medium text-right w-20">次数</th>
                  <th class="py-2 pr-3 font-medium text-right w-28 hidden sm:table-cell">Token</th>
                  <th class="py-2 font-medium text-right w-36">费用</th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="(u, i) in summary.byUser"
                  :key="u.actor + (u.userId || u.visitorId || '')"
                  class="border-t border-border/70 transition-colors cursor-default"
                  :class="hoverUserKey === u.actor + (u.userId || u.visitorId || '') ? 'bg-bg-code/60' : 'hover:bg-bg-code/35'"
                  @mousemove="(e) => { hoverUserKey = u.actor + (u.userId || u.visitorId || ''); placeTip(e, userTipData(u)) }"
                  @mouseleave="hoverUserKey = null; hideTip()"
                >
                  <td class="py-3 pr-3 text-text-muted tabular-nums">{{ i + 1 }}</td>
                  <td class="py-3 pr-3 truncate">{{ u.actor }}</td>
                  <td class="py-3 pr-3 tabular-nums text-right text-text-secondary">{{ formatTokens(u.calls) }}</td>
                  <td class="py-3 pr-3 tabular-nums text-right text-text-secondary hidden sm:table-cell">{{ formatTokens(u.tokens) }}</td>
                  <td class="py-3">
                    <div class="flex items-center justify-end gap-2">
                      <div class="hidden sm:block h-1.5 w-16 rounded-full bg-bg-code overflow-hidden">
                        <div
                          class="h-full bg-accent rounded-full"
                          :style="{ width: `${sliceShare(u.cost, summary.byUser)}%` }"
                        />
                      </div>
                      <span class="tabular-nums shrink-0">{{ formatCost(u.cost) }}</span>
                    </div>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <p v-else class="text-sm text-text-muted">暂无</p>
        </div>
      </template>
    </template>

    <!-- ════ 模型与 API ════ -->
    <template v-else>
      <p class="text-sm text-text-secondary mb-4">
        改动立即对后续对话生效。API Key 与 Chat 模型会回写
        <code class="text-xs bg-bg-code px-1 py-0.5 rounded">{{ providersEnvFile || '.env' }}</code>
        （本地为项目根目录，生产为云服务器 <code class="text-xs bg-bg-code px-1 py-0.5 rounded">/opt/mysite/.env</code>）。
      </p>
      <div v-if="providersLoading" class="text-sm text-text-muted">加载供应商…</div>
      <div v-else-if="providers.length === 0" class="text-sm text-text-muted">暂无供应商配置</div>
      <div v-else class="grid md:grid-cols-2 gap-4">
        <AiProviderCard
          v-for="p in providers"
          :key="p.name"
          :provider="p"
          @updated="onProvidersUpdated"
        />
      </div>
    </template>

    <Teleport to="body">
      <div
        v-if="dashTip"
        class="fixed z-[80] pointer-events-none min-w-[176px] max-w-[240px] rounded-xl border border-border bg-bg-elevated/95 px-3 py-2.5 shadow-lg backdrop-blur-sm"
        :style="{ left: dashTip.x + 'px', top: dashTip.y + 'px' }"
      >
          <div class="flex items-center gap-2 mb-2">
            <span
              class="w-2 h-2 rounded-full shrink-0"
              :style="{ background: dashTip.accent || 'var(--accent)' }"
            />
            <p class="text-xs font-medium text-text-primary truncate">{{ dashTip.title }}</p>
          </div>
          <p v-if="dashTip.subtitle" class="text-[11px] text-text-muted -mt-1.5 mb-2 truncate">{{ dashTip.subtitle }}</p>
          <dl class="space-y-1">
            <div
              v-for="row in dashTip.rows"
              :key="row.label"
              class="flex items-baseline justify-between gap-6 text-[11px]"
            >
              <dt class="text-text-muted shrink-0">{{ row.label }}</dt>
              <dd class="tabular-nums text-text-primary">{{ row.value }}</dd>
            </div>
          </dl>
        </div>
    </Teleport>
  </div>
</template>
