<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { Cpu, KeyRound, Loader2, PlugZap, Save } from 'lucide-vue-next'
import { pingAiProvider, updateAiProvider, type LlmProviderView } from '@/api/aiAdmin'
import { Badge } from '@/components/ui'
import { useToast } from '@/composables/useToast'

const props = defineProps<{
  provider: LlmProviderView
}>()

const emit = defineEmits<{
  updated: [list: LlmProviderView[]]
}>()

const { success, error: toastError } = useToast()

const CHAT_MODEL_HINTS: Record<string, string[]> = {
  deepseek: ['deepseek-v4-flash', 'deepseek-chat', 'deepseek-reasoner'],
  bailian: ['qwen3-max', 'qwen-plus', 'qwen-turbo', 'qwen-max'],
  siliconflow: ['glm-4.7', 'Qwen/Qwen3-8B'],
  aihubmix: ['gpt-5.4', 'gpt-4o'],
  ollama: ['qwen3:8b', 'qwen2.5:7b'],
}

const form = reactive({
  enabled: false,
  priority: 1,
  chatModel: '',
  baseUrl: '',
  apiKey: '',
})

watch(
  () => props.provider,
  (p) => {
    form.enabled = p.enabled
    form.priority = p.priority
    form.chatModel = p.chatModel || ''
    form.baseUrl = p.baseUrl || ''
    form.apiKey = ''
  },
  { immediate: true },
)

const dirty = computed(() => {
  const p = props.provider
  return (
    form.enabled !== p.enabled
    || form.priority !== p.priority
    || form.chatModel !== (p.chatModel || '')
    || form.baseUrl !== (p.baseUrl || '')
    || form.apiKey.trim() !== ''
  )
})

const saving = ref(false)
const pinging = ref(false)
const pingResult = ref<{ ok: boolean; text: string } | null>(null)

const hints = computed(() => CHAT_MODEL_HINTS[props.provider.name] ?? [])
const listId = computed(() => `chat-model-${props.provider.name}`)

async function save() {
  saving.value = true
  pingResult.value = null
  try {
    const list = await updateAiProvider(props.provider.name, {
      enabled: form.enabled,
      priority: form.priority,
      chatModel: form.chatModel.trim(),
      baseUrl: form.baseUrl.trim(),
      apiKey: form.apiKey.trim() || undefined,
    })
    form.apiKey = ''
    emit('updated', list)
    success(`${props.provider.name} 已保存并立即生效`)
  } catch (e) {
    toastError(e instanceof Error ? e.message : '保存失败')
  } finally {
    saving.value = false
  }
}

async function ping() {
  pinging.value = true
  pingResult.value = null
  try {
    const r = await pingAiProvider(props.provider.name)
    pingResult.value = {
      ok: r.ok,
      text: r.ok
        ? `连通 ${r.latencyMs}ms${r.preview ? ` · ${r.preview}` : ''}`
        : (r.message || '测试失败'),
    }
    if (r.ok) success(`${props.provider.name} 连通`)
    else toastError(r.message || '测试失败')
  } catch (e) {
    const msg = e instanceof Error ? e.message : '测试失败'
    pingResult.value = { ok: false, text: msg }
    toastError(msg)
  } finally {
    pinging.value = false
  }
}
</script>

<template>
  <div class="card-solid p-5 flex flex-col gap-4">
    <div class="flex items-start justify-between gap-3">
      <div class="flex items-center gap-2 min-w-0">
        <div class="w-8 h-8 rounded-lg bg-accent-subtle text-accent flex items-center justify-center shrink-0">
          <Cpu :size="16" />
        </div>
        <div class="min-w-0">
          <h3 class="font-medium text-text-primary truncate">{{ provider.name }}</h3>
          <p class="text-[11px] text-text-muted">
            {{ provider.envApiKeyName || provider.envChatModelName || '未绑定环境变量' }}
            <span v-if="provider.persisted"> · 已覆盖</span>
          </p>
        </div>
      </div>
      <label class="flex items-center gap-2 shrink-0 cursor-pointer select-none">
        <input v-model="form.enabled" type="checkbox" class="accent-[var(--accent)]" />
        <Badge :variant="form.enabled ? 'success' : 'muted'" dot>
          {{ form.enabled ? '启用' : '关闭' }}
        </Badge>
      </label>
    </div>

    <div class="grid gap-3">
      <label class="block">
        <span class="text-xs text-text-muted">Chat 模型</span>
        <input
          v-model="form.chatModel"
          class="input-base mt-1 font-mono text-xs"
          :list="listId"
          autocomplete="off"
        />
        <datalist :id="listId">
          <option v-for="m in hints" :key="m" :value="m" />
        </datalist>
      </label>
      <div class="grid grid-cols-2 gap-3">
        <label class="block">
          <span class="text-xs text-text-muted">优先级</span>
          <input v-model.number="form.priority" type="number" min="1" max="99" class="input-base mt-1" />
        </label>
        <label class="block">
          <span class="text-xs text-text-muted flex items-center gap-1">
            <KeyRound :size="12" /> API Key
          </span>
          <input
            v-model="form.apiKey"
            type="password"
            class="input-base mt-1 font-mono text-xs"
            :placeholder="provider.apiKeyMasked || (provider.requiresApiKey ? '未配置' : '无需 Key')"
            :disabled="!provider.requiresApiKey"
            autocomplete="new-password"
          />
        </label>
      </div>
      <label class="block">
        <span class="text-xs text-text-muted">Base URL</span>
        <input v-model="form.baseUrl" class="input-base mt-1 font-mono text-[11px]" />
      </label>
    </div>

    <dl v-if="provider.embeddingModel || provider.rerankModel" class="space-y-1.5 text-xs border-t border-border pt-3">
      <div v-if="provider.embeddingModel" class="flex items-baseline justify-between gap-3">
        <dt class="text-text-muted shrink-0">Embedding</dt>
        <dd class="font-mono text-text-secondary truncate">{{ provider.embeddingModel }}</dd>
      </div>
      <div v-if="provider.rerankModel" class="flex items-baseline justify-between gap-3">
        <dt class="text-text-muted shrink-0">Rerank</dt>
        <dd class="font-mono text-text-secondary truncate">{{ provider.rerankModel }}</dd>
      </div>
      <p class="text-[11px] text-text-muted">向量 / 精排模型与库维度绑定，不在此修改。</p>
    </dl>

    <p v-if="pingResult" class="text-[11px]" :class="pingResult.ok ? 'text-[var(--success)]' : 'text-[var(--danger)]'">
      {{ pingResult.text }}
    </p>

    <div class="flex flex-wrap items-center gap-2 mt-auto">
      <button type="button" class="btn-primary text-sm px-3 py-1.5" :disabled="saving || !dirty" @click="save">
        <Loader2 v-if="saving" :size="14" class="animate-spin" />
        <Save v-else :size="14" />
        保存
      </button>
      <button
        type="button"
        class="btn-secondary text-sm px-3 py-1.5"
        :disabled="pinging || !provider.runtimeBound"
        @click="ping"
      >
        <Loader2 v-if="pinging" :size="14" class="animate-spin" />
        <PlugZap v-else :size="14" />
        测试连通
      </button>
      <span v-if="!provider.runtimeBound" class="text-[11px] text-[var(--warning)]">未接入运行时</span>
    </div>
  </div>
</template>
