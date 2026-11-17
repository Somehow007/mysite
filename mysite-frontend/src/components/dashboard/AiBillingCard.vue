<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Landmark, Loader2, RefreshCw } from 'lucide-vue-next'
import { getAiBilling, type LlmBilling, type LlmBillingBalance, type LlmBillingProvider } from '@/api/aiAdmin'
import { formatDateTime } from '@/utils/date'

const billing = ref<LlmBilling | null>(null)
const loading = ref(false)
const error = ref('')

async function fetch() {
  loading.value = true
  error.value = ''
  try {
    billing.value = await getAiBilling()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '查询失败'
  } finally {
    loading.value = false
  }
}

onMounted(fetch)

function balancesOf(pb: LlmBillingProvider): LlmBillingBalance[] {
  const b = pb.data?.balances
  return Array.isArray(b) ? (b as LlmBillingBalance[]) : []
}

function num(v: unknown): number | null {
  if (v == null || v === '') return null
  const n = Number(v)
  return Number.isFinite(n) ? n : null
}

function fmtMoney(v: unknown, currency: string) {
  const n = num(v)
  if (n == null) return '—'
  const sym = currency === 'USD' ? '$' : currency === 'CNY' ? '¥' : `${currency} `
  return `${sym}${n.toFixed(n < 0.01 ? 4 : 2)}`
}

function fmtInt(v: unknown) {
  const n = num(v)
  return n == null ? '—' : Math.round(n).toLocaleString()
}

const PROVIDER_LABEL: Record<string, string> = {
  deepseek: 'DeepSeek',
  bailian: '百炼 (Bailian)',
}
</script>

<template>
  <div class="card-solid p-5">
    <div class="flex items-center justify-between gap-3 mb-4">
      <div class="flex items-center gap-2">
        <Landmark :size="15" class="text-text-muted" />
        <div>
          <h3 class="text-sm font-medium text-text-primary">官方账单对账</h3>
          <p class="text-xs text-text-muted mt-0.5">
            官方余额/配额口径<span v-if="billing?.probedAt"> · 拉取于 {{ formatDateTime(billing.probedAt) }}</span>
          </p>
        </div>
      </div>
      <button
        type="button"
        class="btn-secondary text-xs px-3 py-1.5 flex items-center gap-1.5"
        :disabled="loading"
        @click="fetch"
      >
        <Loader2 v-if="loading" :size="13" class="animate-spin" />
        <RefreshCw v-else :size="13" />
        {{ loading ? '查询中…' : '刷新' }}
      </button>
    </div>

    <p v-if="error" class="text-sm text-[var(--danger)]">{{ error }}</p>
    <p v-else-if="loading && !billing" class="text-sm text-text-muted">正在拉取官方账单…</p>
    <p v-else-if="billing && billing.providers.length === 0" class="text-sm text-text-muted">
      没有可对账的供应商（仅支持 DeepSeek 余额、百炼配额）
    </p>

    <div v-else-if="billing" class="space-y-4">
      <div
        v-for="pb in billing.providers"
        :key="pb.provider"
        class="rounded-lg border border-border/70 p-4"
      >
        <div class="flex items-center justify-between gap-3 mb-2">
          <div class="flex items-center gap-2 min-w-0">
            <span class="text-sm font-medium text-text-primary truncate">
              {{ PROVIDER_LABEL[pb.provider] || pb.provider }}
            </span>
            <span
              class="text-[11px] px-1.5 py-0.5 rounded"
              :class="pb.ok ? 'bg-[var(--success)]/10 text-[var(--success)]' : 'bg-[var(--danger)]/10 text-[var(--danger)]'"
            >
              {{ pb.ok ? '正常' : '失败' }}
            </span>
          </div>
          <span class="text-[11px] text-text-muted tabular-nums shrink-0">
            {{ pb.latencyMs != null ? `${pb.latencyMs}ms` : '' }}
          </span>
        </div>
        <p v-if="!pb.ok" class="text-xs text-[var(--danger)]">{{ pb.message || '查询失败' }}</p>

        <!-- 余额类（DeepSeek） -->
        <template v-else-if="pb.data?.kind === 'balance'">
          <div class="space-y-1.5">
            <div
              v-for="b in balancesOf(pb)"
              :key="b.currency"
              class="flex items-baseline justify-between text-sm"
            >
              <span class="text-text-secondary">{{ b.currency }} 余额</span>
              <span class="tabular-nums text-text-primary font-medium">
                {{ fmtMoney(b.totalBalance, b.currency) }}
                <span class="text-[11px] text-text-muted font-normal ml-1">
                  充值 {{ fmtMoney(b.toppedUpBalance, b.currency) }} · 赠金 {{ fmtMoney(b.grantedBalance, b.currency) }}
                </span>
              </span>
            </div>
          </div>
          <div
            v-if="pb.delta?.periodSpend && Object.keys(pb.delta.periodSpend).length"
            class="mt-2 pt-2 border-t border-border/60 text-xs text-text-muted"
          >
            <span v-for="(spend, cur) in pb.delta.periodSpend" :key="cur" class="mr-4 tabular-nums">
              距上次快照实花 {{ fmtMoney(spend, cur) }}（{{ cur }}）
            </span>
            <span>自 {{ formatDateTime(pb.delta.fromTime) }}</span>
          </div>
          <p v-else class="mt-2 pt-2 border-t border-border/60 text-xs text-text-muted">
            首次快照，累计实花需两次拉取后可见
          </p>
        </template>

        <!-- 配额类（百炼） -->
        <template v-else-if="pb.data?.kind === 'quota'">
          <div class="grid grid-cols-2 sm:grid-cols-4 gap-3 text-sm">
            <div>
              <p class="text-[11px] text-text-muted">今日花费</p>
              <p class="tabular-nums text-text-primary font-medium">{{ fmtMoney(pb.data.dailySpend, 'CNY') }}</p>
            </div>
            <div>
              <p class="text-[11px] text-text-muted">本月花费</p>
              <p class="tabular-nums text-text-primary font-medium">{{ fmtMoney(pb.data.monthlySpend, 'CNY') }}</p>
            </div>
            <div>
              <p class="text-[11px] text-text-muted">Token 用量</p>
              <p class="tabular-nums text-text-primary font-medium">{{ fmtInt(pb.data.tokensUsed) }}</p>
            </div>
            <div>
              <p class="text-[11px] text-text-muted">请求数</p>
              <p class="tabular-nums text-text-primary font-medium">{{ fmtInt(pb.data.requestsUsed) }}</p>
            </div>
          </div>
          <p class="mt-2 text-[11px] text-text-muted">
            官方口径可能与账单页存在口径差，以阿里云账单为准
          </p>
        </template>

        <p v-else class="text-xs text-text-muted">官方返回格式未识别，原始数据已存快照</p>
      </div>
    </div>
  </div>
</template>
