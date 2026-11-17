<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { CalendarDays, Pencil, Plus, Tags, Trash2 } from 'lucide-vue-next'
import {
  createAiPrice, getAiHolidays, getAiPrices, replaceAiHolidays,
  retireAiPrice, reviseAiPrice,
  type LlmHoliday, type LlmModelPrice, type LlmModelPriceUpsert,
} from '@/api/aiAdmin'
import { useToast } from '@/composables/useToast'
import { formatDateTime } from '@/utils/date'
import { Badge, ConfirmDialog, EmptyState, Modal } from '@/components/ui'

const { success, error: toastError } = useToast()

// ── 价卡 ──
const prices = ref<LlmModelPrice[]>([])
const pricesLoading = ref(false)
const showHistory = ref(false)

const visiblePrices = computed(() =>
  showHistory.value ? prices.value : prices.value.filter(p => p.current),
)

const historyCount = computed(() => prices.value.length - prices.value.filter(p => p.current).length)

async function fetchPrices() {
  pricesLoading.value = true
  try {
    prices.value = await getAiPrices()
  } catch {
    prices.value = []
  } finally {
    pricesLoading.value = false
  }
}

function fmtPrice(v?: number | null) {
  if (v == null) return '—'
  return Number(v).toLocaleString('zh-CN', { maximumFractionDigits: 4 })
}

function priceSummary(p: LlmModelPrice) {
  const parts = [`入 ${fmtPrice(p.inputPrice)}`]
  if (p.cacheReadPrice != null) parts.push(`读 ${fmtPrice(p.cacheReadPrice)}`)
  if (p.cacheWritePrice != null) parts.push(`写 ${fmtPrice(p.cacheWritePrice)}`)
  parts.push(`出 ${fmtPrice(p.outputPrice)}`)
  return parts.join(' · ')
}

function peakLabel(p: LlmModelPrice) {
  if (!p.peakWindows) return '—'
  const off = p.offPeakFactor != null && Number(p.offPeakFactor) !== 1
    ? `谷×${Number(p.offPeakFactor)}`
    : ''
  return `峰谷${off ? `（${off}）` : ''}`
}

function validityLabel(p: LlmModelPrice) {
  const from = p.effectiveFrom ? formatDateTime(p.effectiveFrom).slice(0, 10) : '—'
  const to = p.effectiveTo ? formatDateTime(p.effectiveTo).slice(0, 10) : '至今'
  return `${from} ~ ${to}`
}

// ── 编辑弹窗 ──
interface PriceForm {
  provider: string
  model: string
  displayName: string
  currency: string
  inputPrice: string
  cacheReadPrice: string
  cacheReadFactor: string
  cacheWritePrice: string
  cacheWriteFactor: string
  outputPrice: string
  offPeakFactor: string
  excludeHolidays: boolean
  peakWindows: string
  tiers: string
  effectiveFrom: string
  note: string
}

const emptyForm = (): PriceForm => ({
  provider: '', model: '', displayName: '', currency: 'CNY',
  inputPrice: '', cacheReadPrice: '', cacheReadFactor: '',
  cacheWritePrice: '', cacheWriteFactor: '', outputPrice: '',
  offPeakFactor: '', excludeHolidays: false,
  peakWindows: '', tiers: '', effectiveFrom: '', note: '',
})

const formOpen = ref(false)
const formMode = ref<'create' | 'edit'>('create')
const formId = ref<string | null>(null)
const form = ref<PriceForm>(emptyForm())
const formSaving = ref(false)

function openCreate() {
  formMode.value = 'create'
  formId.value = null
  form.value = emptyForm()
  formOpen.value = true
}

function openEdit(p: LlmModelPrice) {
  formMode.value = 'edit'
  formId.value = p.id
  form.value = {
    provider: p.provider,
    model: p.model,
    displayName: p.displayName || '',
    currency: p.currency || 'CNY',
    inputPrice: p.inputPrice != null ? String(p.inputPrice) : '',
    cacheReadPrice: p.cacheReadPrice != null ? String(p.cacheReadPrice) : '',
    cacheReadFactor: p.cacheReadFactor != null ? String(p.cacheReadFactor) : '',
    cacheWritePrice: p.cacheWritePrice != null ? String(p.cacheWritePrice) : '',
    cacheWriteFactor: p.cacheWriteFactor != null ? String(p.cacheWriteFactor) : '',
    outputPrice: p.outputPrice != null ? String(p.outputPrice) : '',
    offPeakFactor: p.offPeakFactor != null ? String(p.offPeakFactor) : '',
    excludeHolidays: !!p.excludeHolidays,
    peakWindows: p.peakWindows || '',
    tiers: p.tiers || '',
    // 修订语义=新版本从此刻起生效，预填旧版本生效时间会被后端「必须晚于旧版」拒绝
    effectiveFrom: '',
    note: p.note || '',
  }
  formOpen.value = true
}

function numOrNull(s: string) {
  const t = s.trim()
  if (!t) return null
  const n = Number(t)
  return Number.isFinite(n) ? n : null
}

async function savePrice() {
  const f = form.value
  if (!f.provider.trim() || !f.model.trim()) {
    toastError('provider 和 model 必填')
    return
  }
  if (f.inputPrice.trim() === '' || f.outputPrice.trim() === '') {
    toastError('输入价 / 输出价必填（每 1M token）')
    return
  }
  const body: LlmModelPriceUpsert = {
    provider: f.provider.trim(),
    model: f.model.trim(),
    displayName: f.displayName.trim() || null,
    currency: f.currency,
    inputPrice: numOrNull(f.inputPrice),
    cacheReadPrice: numOrNull(f.cacheReadPrice),
    cacheReadFactor: numOrNull(f.cacheReadFactor),
    cacheWritePrice: numOrNull(f.cacheWritePrice),
    cacheWriteFactor: numOrNull(f.cacheWriteFactor),
    outputPrice: numOrNull(f.outputPrice),
    offPeakFactor: numOrNull(f.offPeakFactor),
    excludeHolidays: f.excludeHolidays,
    peakWindows: f.peakWindows.trim() || null,
    tiers: f.tiers.trim() || null,
    // 本地墙钟时间（yyyy-MM-ddTHH:mm），与后端 LocalDateTime 语义一致，勿转 UTC
    effectiveFrom: f.effectiveFrom || null,
    note: f.note.trim() || null,
  }
  formSaving.value = true
  try {
    if (formMode.value === 'create') {
      prices.value = await createAiPrice(body)
      success('价卡已创建')
    } else if (formId.value) {
      prices.value = await reviseAiPrice(formId.value, body)
      success('已生成新版本，旧版本自动闭合')
    }
    formOpen.value = false
  } catch (e) {
    toastError(e instanceof Error ? e.message : '保存失败')
  } finally {
    formSaving.value = false
  }
}

const retireTarget = ref<LlmModelPrice | null>(null)
const retiring = ref(false)

async function confirmRetire() {
  if (!retireTarget.value) return
  retiring.value = true
  try {
    prices.value = await retireAiPrice(retireTarget.value.id)
    success('已停用该版本')
    retireTarget.value = null
  } catch (e) {
    toastError(e instanceof Error ? e.message : '停用失败')
  } finally {
    retiring.value = false
  }
}

// ── 节假日 ──
const holidays = ref<LlmHoliday[]>([])
const holidaysLoading = ref(false)
const holidayDraft = ref<LlmHoliday[]>([])
const holidayDirty = ref(false)
const newHolidayDate = ref('')
const newHolidayName = ref('')
const holidaysSaving = ref(false)

async function fetchHolidays() {
  holidaysLoading.value = true
  try {
    holidays.value = await getAiHolidays()
    holidayDraft.value = holidays.value.map(h => ({ ...h }))
    holidayDirty.value = false
  } catch {
    holidays.value = []
    holidayDraft.value = []
  } finally {
    holidaysLoading.value = false
  }
}

function addHoliday() {
  if (!newHolidayDate.value) return
  holidayDraft.value.push({ holidayDate: newHolidayDate.value, name: newHolidayName.value.trim() || null })
  holidayDraft.value.sort((a, b) => a.holidayDate.localeCompare(b.holidayDate))
  newHolidayDate.value = ''
  newHolidayName.value = ''
  holidayDirty.value = true
}

function removeHoliday(idx: number) {
  holidayDraft.value.splice(idx, 1)
  holidayDirty.value = true
}

function resetHolidays() {
  holidayDraft.value = holidays.value.map(h => ({ ...h }))
  holidayDirty.value = false
}

async function saveHolidays() {
  holidaysSaving.value = true
  try {
    holidays.value = await replaceAiHolidays(holidayDraft.value)
    holidayDraft.value = holidays.value.map(h => ({ ...h }))
    holidayDirty.value = false
    success('节假日已保存')
  } catch (e) {
    toastError(e instanceof Error ? e.message : '保存失败')
  } finally {
    holidaysSaving.value = false
  }
}

const fieldCls = 'w-full px-3 py-2 text-sm rounded-lg border border-border bg-bg-secondary text-text-primary focus:outline-none focus:ring-2 focus:ring-accent/50 focus:border-accent'
const labelCls = 'block text-xs text-text-muted mb-1'

onMounted(() => {
  fetchPrices()
  fetchHolidays()
})
</script>

<template>
  <div class="flex flex-col gap-5">
    <!-- ════ 价卡 ════ -->
    <div class="card-solid p-5">
      <div class="flex flex-wrap items-center justify-between gap-3 mb-4">
        <div>
          <h3 class="text-sm font-medium text-text-primary">模型价卡</h3>
          <p class="text-xs text-text-muted mt-0.5">
            每 1M token 单价；修订生成新版本，历史记录仍按当时价卡计费
            <span v-if="historyCount > 0">（{{ historyCount }} 个历史版本已隐藏）</span>
          </p>
        </div>
        <div class="flex items-center gap-2">
          <label class="flex items-center gap-1.5 text-xs text-text-secondary cursor-pointer">
            <input v-model="showHistory" type="checkbox" class="accent-[var(--accent)]" />
            显示历史版本
          </label>
          <button
            class="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs rounded-lg bg-accent text-white hover:opacity-90 transition-opacity"
            @click="openCreate"
          >
            <Plus :size="13" /> 新建价卡
          </button>
        </div>
      </div>

      <div v-if="pricesLoading" class="text-sm text-text-muted py-6 text-center">加载价卡…</div>
      <EmptyState
        v-else-if="visiblePrices.length === 0"
        :icon="Tags"
        title="暂无价卡"
        description="首次启动会自动导入官方价目；也可点「新建价卡」手动添加"
      />
      <div v-else class="overflow-x-auto">
        <table class="w-full text-sm">
          <thead>
            <tr class="text-left text-xs text-text-muted border-b border-border">
              <th class="py-2 pr-3 font-medium">模型</th>
              <th class="py-2 pr-3 font-medium">Provider</th>
              <th class="py-2 pr-3 font-medium">币种</th>
              <th class="py-2 pr-3 font-medium">单价（入/读/写/出）</th>
              <th class="py-2 pr-3 font-medium">峰谷</th>
              <th class="py-2 pr-3 font-medium">生效期</th>
              <th class="py-2 pr-3 font-medium">来源</th>
              <th class="py-2 font-medium text-right w-20">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="p in visiblePrices"
              :key="p.id"
              class="border-t border-border/70 align-top"
              :class="p.current ? '' : 'opacity-55'"
            >
              <td class="py-3 pr-3">
                <p class="text-text-primary">{{ p.displayName || p.model }}</p>
                <p class="text-[11px] text-text-muted font-mono">{{ p.model }}</p>
                <Badge v-if="p.tiers" size="sm" variant="outline" class="mt-1">阶梯</Badge>
              </td>
              <td class="py-3 pr-3 text-text-secondary">{{ p.provider }}</td>
              <td class="py-3 pr-3">
                <Badge size="sm" :variant="p.currency === 'CNY' ? 'accent' : 'outline'">{{ p.currency }}</Badge>
              </td>
              <td class="py-3 pr-3 text-xs tabular-nums text-text-secondary whitespace-nowrap">{{ priceSummary(p) }}</td>
              <td class="py-3 pr-3 text-xs text-text-secondary whitespace-nowrap">{{ peakLabel(p) }}</td>
              <td class="py-3 pr-3 text-xs tabular-nums text-text-secondary whitespace-nowrap">{{ validityLabel(p) }}</td>
              <td class="py-3 pr-3 text-xs text-text-muted">{{ p.source || '—' }}</td>
              <td class="py-3 text-right whitespace-nowrap">
                <button
                  class="p-1.5 rounded-md text-text-muted hover:text-accent hover:bg-accent-subtle transition-colors"
                  title="修订（生成新版本）"
                  @click="openEdit(p)"
                >
                  <Pencil :size="14" />
                </button>
                <button
                  v-if="p.current"
                  class="p-1.5 rounded-md text-text-muted hover:text-danger hover:bg-danger/10 transition-colors"
                  title="停用此版本"
                  @click="retireTarget = p"
                >
                  <Trash2 :size="14" />
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- ════ 节假日 ════ -->
    <div class="card-solid p-5">
      <div class="flex flex-wrap items-center justify-between gap-3 mb-1">
        <div>
          <h3 class="text-sm font-medium text-text-primary flex items-center gap-1.5">
            <CalendarDays :size="14" class="text-text-muted" /> 中国法定节假日
          </h3>
          <p class="text-xs text-text-muted mt-0.5">
            DeepSeek 峰谷价在节假日全天按谷时计；调休上班日不要加进来
          </p>
        </div>
        <div v-if="holidayDirty" class="flex items-center gap-2">
          <button class="px-3 py-1.5 text-xs rounded-lg border border-border text-text-secondary hover:text-text-primary" @click="resetHolidays">
            还原
          </button>
          <button
            class="px-3 py-1.5 text-xs rounded-lg bg-accent text-white hover:opacity-90 disabled:opacity-50"
            :disabled="holidaysSaving"
            @click="saveHolidays"
          >
            {{ holidaysSaving ? '保存中…' : '保存' }}
          </button>
        </div>
      </div>

      <div v-if="holidaysLoading" class="text-sm text-text-muted py-4 text-center">加载节假日…</div>
      <template v-else>
        <div class="flex flex-wrap items-end gap-2 mt-3 mb-3">
          <div>
            <label class="block text-[11px] text-text-muted mb-1">日期</label>
            <input v-model="newHolidayDate" type="date" :class="fieldCls" class="w-40" />
          </div>
          <div>
            <label class="block text-[11px] text-text-muted mb-1">名称</label>
            <input v-model="newHolidayName" type="text" placeholder="如：国庆节" :class="fieldCls" class="w-36" @keyup.enter="addHoliday" />
          </div>
          <button
            class="inline-flex items-center gap-1 px-3 py-2 text-xs rounded-lg border border-border text-text-secondary hover:text-accent hover:border-accent transition-colors"
            :disabled="!newHolidayDate"
            @click="addHoliday"
          >
            <Plus :size="13" /> 添加
          </button>
        </div>

        <div v-if="holidayDraft.length === 0" class="text-sm text-text-muted py-4 text-center">
          暂无节假日（峰谷判定将不排除任何日期）
        </div>
        <div v-else class="flex flex-wrap gap-1.5">
          <span
            v-for="(h, i) in holidayDraft"
            :key="h.holidayDate"
            class="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full bg-bg-code text-xs text-text-secondary"
          >
            <span class="tabular-nums">{{ h.holidayDate }}</span>
            <span v-if="h.name" class="text-text-muted">{{ h.name }}</span>
            <button class="text-text-muted hover:text-danger transition-colors" @click="removeHoliday(i)">
              <Trash2 :size="11" />
            </button>
          </span>
        </div>
      </template>
    </div>

    <!-- ════ 价卡编辑弹窗 ════ -->
    <Modal
      :open="formOpen"
      :title="formMode === 'create' ? '新建价卡' : `修订 ${form.model}`"
      :description="formMode === 'edit' ? '保存将生成新版本；旧版本在所选生效时间自动闭合，历史调用记录不受影响' : '价格单位均为每 1M token'"
      max-width="max-w-2xl"
      @update:open="formOpen = $event"
    >
      <div class="grid grid-cols-2 gap-3">
        <div>
          <label :class="labelCls">Provider *</label>
          <input v-model="form.provider" :class="fieldCls" :disabled="formMode === 'edit'" placeholder="deepseek / bailian / ..." />
        </div>
        <div>
          <label :class="labelCls">模型名 *</label>
          <input v-model="form.model" :class="fieldCls" :disabled="formMode === 'edit'" placeholder="deepseek-flash" />
        </div>
        <div>
          <label :class="labelCls">显示名</label>
          <input v-model="form.displayName" :class="fieldCls" placeholder="DeepSeek V4.1 Flash" />
        </div>
        <div>
          <label :class="labelCls">币种 *</label>
          <select v-model="form.currency" :class="fieldCls">
            <option value="CNY">CNY（人民币）</option>
            <option value="USD">USD（美元）</option>
          </select>
        </div>
        <div>
          <label :class="labelCls">输入价（未命中）/1M</label>
          <input v-model="form.inputPrice" type="number" step="any" min="0" :class="fieldCls" placeholder="2" />
        </div>
        <div>
          <label :class="labelCls">输出价 /1M</label>
          <input v-model="form.outputPrice" type="number" step="any" min="0" :class="fieldCls" placeholder="8" />
        </div>
        <div>
          <label :class="labelCls">缓存读价 /1M（空=按系数或同输入价）</label>
          <input v-model="form.cacheReadPrice" type="number" step="any" min="0" :class="fieldCls" placeholder="0.04" />
        </div>
        <div>
          <label :class="labelCls">缓存读系数（显式价为空时 ×输入价）</label>
          <input v-model="form.cacheReadFactor" type="number" step="any" min="0" :class="fieldCls" placeholder="1" />
        </div>
        <div>
          <label :class="labelCls">缓存写价 /1M</label>
          <input v-model="form.cacheWritePrice" type="number" step="any" min="0" :class="fieldCls" placeholder="" />
        </div>
        <div>
          <label :class="labelCls">缓存写系数</label>
          <input v-model="form.cacheWriteFactor" type="number" step="any" min="0" :class="fieldCls" placeholder="0" />
        </div>
        <div>
          <label :class="labelCls">谷时系数（无峰谷填 1 或留空）</label>
          <input v-model="form.offPeakFactor" type="number" step="any" min="0" max="1" :class="fieldCls" placeholder="0.5" />
        </div>
        <div class="flex items-end pb-1">
          <label class="flex items-center gap-2 text-xs text-text-secondary cursor-pointer">
            <input v-model="form.excludeHolidays" type="checkbox" class="accent-[var(--accent)]" />
            节假日按谷时
          </label>
        </div>
        <div>
          <label :class="labelCls">新版本生效时间（本地时间，空=现在）</label>
          <input v-model="form.effectiveFrom" type="datetime-local" :class="fieldCls" />
        </div>
        <div class="col-span-2">
          <label :class="labelCls">峰谷窗口 JSON（UTC，留空=无峰谷）</label>
          <textarea
            v-model="form.peakWindows"
            :class="fieldCls"
            rows="3"
            class="font-mono text-xs"
            placeholder='[{"start":"01:00","end":"04:00","days":[1,2,3,4,5]},{"start":"06:00","end":"10:00","days":[1,2,3,4,5]}]'
          />
        </div>
        <div class="col-span-2">
          <label :class="labelCls">输入长度阶梯 JSON（留空=单一档）</label>
          <textarea
            v-model="form.tiers"
            :class="fieldCls"
            rows="3"
            class="font-mono text-xs"
            placeholder='[{"max_input_tokens":131072,"input_price":0.8,"cache_read_price":0.08,"output_price":4.8},{"max_input_tokens":262144,"input_price":2,"output_price":12}]'
          />
        </div>
        <div class="col-span-2">
          <label :class="labelCls">备注</label>
          <input v-model="form.note" :class="fieldCls" placeholder="价格来源 / 生效说明" />
        </div>
      </div>
      <template #footer>
        <button class="px-4 py-2 text-sm rounded-lg border border-border text-text-secondary hover:text-text-primary" @click="formOpen = false">
          取消
        </button>
        <button
          class="px-4 py-2 text-sm rounded-lg bg-accent text-white hover:opacity-90 disabled:opacity-50"
          :disabled="formSaving"
          @click="savePrice"
        >
          {{ formSaving ? '保存中…' : formMode === 'create' ? '创建' : '保存为新版本' }}
        </button>
      </template>
    </Modal>

    <ConfirmDialog
      :open="!!retireTarget"
      title="停用价卡版本"
      :message="`将 ${retireTarget?.model || ''} 当前版本的生效期截止到现在；历史记录保留，之后该模型按未标价计费。`"
      confirm-text="停用"
      danger
      @update:open="(v) => { if (!v) retireTarget = null }"
      @confirm="confirmRetire"
    />
  </div>
</template>
