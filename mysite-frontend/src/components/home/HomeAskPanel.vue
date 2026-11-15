<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useSiteStore } from '@/stores/site'
import { useUserStore } from '@/stores/user'
import { useChatLauncher } from '@/composables/useChatLauncher'
import { getKnowledgeBases } from '@/api/rag'
import { getItem } from '@/utils/storage'

// 与后端 rag.rate-limit.* 及 ChatInput 文案一致的硬编码口径（限流配置无下发通道）
const FALLBACK_EXAMPLES = [
  'LLM 熔断怎么做的',
  'pgvector 和 ES 怎么选',
  '为什么前端 ID 用 string',
  '最近在学什么',
]

const MAX_QUESTION_LENGTH = 500
const PENDING_ASK_KEY = 'mysite_pending_ask'

const router = useRouter()
const siteStore = useSiteStore()
const userStore = useUserStore()
const launcher = useChatLauncher()

const question = ref('')
const inputRef = ref<HTMLInputElement | null>(null)
// 知识库状态：文章同步进所有启用 KB，「全部文章」= 全部 enabled KB 的 id
const kbState = ref<'loading' | 'ready' | 'empty'>('loading')
const enabledKbIds = ref<string[]>([])

const aiAvailable = computed(() => kbState.value === 'ready')
const kbEmpty = computed(() => kbState.value === 'empty')
const controlsDisabled = computed(() => kbState.value !== 'ready')

const examples = computed(() => {
  const fromSite = siteStore.site.askExamples?.filter(e => e?.trim())
  return fromSite?.length ? fromSite : FALLBACK_EXAMPLES
})

const demo = computed(() => {
  const d = siteStore.site.askDemo
  return d?.answer?.trim() ? d : null
})

const demoLabel = computed(() =>
  demo.value?.question ? `示例 · ${demo.value.question}` : '示例'
)

// 回答文本里的 [n] 渲染为上标角标（对齐概念稿 .answer sup）
const answerParts = computed(() =>
  (demo.value?.answer ?? '')
    .split(/(\[\d+\])/)
    .filter(Boolean)
    .map(seg => {
      const m = /^\[(\d+)\]$/.exec(seg)
      return m
        ? { isRef: true, n: m[1], text: '' }
        : { isRef: false, n: '', text: seg }
    })
)

const quotaNote = computed(() => {
  if (!userStore.isLoggedIn) return '登录后使用 · 每小时 10 次'
  const role = userStore.role
  if (role === 'ADMIN') return '' // ADMIN 不显示额度
  if (role === 'CREATOR') return '每小时 20 次'
  return '每小时 10 次'
})

async function loadKbs() {
  kbState.value = 'loading'
  try {
    const kbs = await getKnowledgeBases()
    enabledKbIds.value = kbs.filter(k => k.enabled).map(k => k.id)
    kbState.value = enabledKbIds.value.length ? 'ready' : 'empty'
  } catch {
    enabledKbIds.value = []
    kbState.value = 'empty'
  }
}

function submit(raw?: string) {
  const q = (raw ?? question.value).trim()
  if (!q || q.length > MAX_QUESTION_LENGTH || !aiAvailable.value) return
  if (!userStore.isLoggedIn) {
    // 未登录：暂存问题，回跳首页后由 resumePendingAsk 自动打开面板并发送
    sessionStorage.setItem(PENDING_ASK_KEY, JSON.stringify({ question: q }))
    router.push({ name: 'login', query: { redirect: '/' } })
    return
  }
  launcher.open({ question: q, send: true, kbIds: enabledKbIds.value })
  question.value = ''
}

function fillExample(text: string) {
  question.value = text
  inputRef.value?.focus()
}

function handleEnter(e: KeyboardEvent) {
  if (e.isComposing) return // 中文输入法选词中的 Enter 不提交
  e.preventDefault()
  submit()
}

// 登录回跳恢复：stash 只在「未登录点提问」时写入；token 在即可放行
// （userStore.user 可能还在 init 拉取中，SSE 请求自行带 token）
function resumePendingAsk() {
  const raw = sessionStorage.getItem(PENDING_ASK_KEY)
  if (!raw) return
  sessionStorage.removeItem(PENDING_ASK_KEY)
  try {
    const pending = JSON.parse(raw) as { question?: string }
    const q = pending.question?.trim()
    const hasSession = userStore.isLoggedIn || !!getItem<string>('access_token')
    if (q && hasSession && aiAvailable.value) {
      launcher.open({ question: q, send: true, kbIds: enabledKbIds.value })
    }
  } catch {
    // 坏数据忽略
  }
}

onMounted(async () => {
  await loadKbs()
  resumePendingAsk()
})
</script>

<template>
  <section class="pb-2 pt-11">
    <div class="mb-4 flex items-baseline justify-between gap-4">
      <h2 class="font-serif text-[22px] font-extrabold leading-tight text-text-primary lg:text-[28px]">
        不想翻目录？<span class="text-accent">直接问。</span>
      </h2>
      <span class="hidden shrink-0 text-xs text-text-muted lg:block">
        回答只基于站内文章，并标出原文出处
      </span>
    </div>

    <!-- 无可用知识库：整块降级为一行提示 -->
    <p
      v-if="kbEmpty"
      class="border border-dashed border-border px-4 py-3 text-sm text-text-muted"
    >
      AI 助手暂未开放
    </p>

    <div
      v-else
      class="paper-card paper-card--strong rounded-xl border border-border bg-bg-elevated shadow-hard"
    >
      <!-- 输入行：移动端 input 独占一行，scope+send 折到第二行（scope mr-auto 推 send 靠右） -->
      <div class="flex flex-wrap items-center gap-x-3 gap-y-2 py-2.5 pl-[18px] pr-2.5">
        <input
          ref="inputRef"
          v-model="question"
          type="text"
          maxlength="500"
          :disabled="controlsDisabled"
          placeholder="例如：作者是怎么处理 LLM 熔断的？"
          class="min-w-0 grow basis-full border-0 bg-transparent py-2 font-serif text-base text-text-primary outline-none placeholder:text-text-muted disabled:opacity-60 lg:basis-0 lg:text-[17px]"
          @keydown.enter="handleEnter"
        />
        <span class="mr-auto border border-accent px-[9px] py-[3px] text-xs whitespace-nowrap text-accent lg:mr-0">
          全部文章 ▾
        </span>
        <button
          type="button"
          :disabled="controlsDisabled || !question.trim()"
          class="shrink-0 bg-text-primary px-[18px] py-[11px] text-sm font-semibold whitespace-nowrap text-bg-primary paper-flat transition-opacity hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-40"
          @click="submit()"
        >
          提问 ↵
        </button>
      </div>

      <!-- 示例问题 chips -->
      <div v-if="examples.length" class="flex flex-wrap gap-2 px-[18px] pb-3.5">
        <button
          v-for="ex in examples"
          :key="ex"
          type="button"
          class="border border-dashed border-border px-[11px] py-1 text-[13px] text-text-secondary transition-colors hover:border-solid hover:border-accent hover:text-accent"
          @click="fillExample(ex)"
        >
          {{ ex }}
        </button>
      </div>

      <!-- 示例问答 + 出处卡：site.ask-demo 配置后才渲染 -->
      <div
        v-if="demo"
        class="grid gap-x-6 gap-y-4 border-t border-dashed border-border px-[18px] pb-[18px] pt-4 lg:grid-cols-2"
      >
        <div class="min-w-0">
          <div class="mb-1.5 text-xs text-text-muted">
            {{ demoLabel }}
          </div>
          <div class="font-serif text-[15.5px] leading-[1.85] text-text-primary">
            <template v-for="(part, i) in answerParts" :key="i">
              <sup
                v-if="part.isRef"
                class="font-sans text-[11px] font-bold text-accent"
              >[{{ part.n }}]</sup>
              <template v-else>{{ part.text }}</template>
            </template>
          </div>
        </div>
        <div v-if="demo.sources?.length" class="grid content-start gap-2.5 lg:grid-cols-2">
          <template v-for="(src, i) in demo.sources" :key="i">
            <RouterLink
              v-if="src.articleId"
              :to="`/post/${src.articleId}`"
              class="block border border-border bg-bg-primary px-3 py-2.5 text-[12.5px] text-text-muted transition-colors hover:border-accent"
            >
              <span class="block font-serif text-sm font-semibold text-text-primary">[{{ i + 1 }}] {{ src.title }}</span>
              <span v-if="src.section">{{ src.section }}</span>
            </RouterLink>
            <div
              v-else
              class="border border-border bg-bg-primary px-3 py-2.5 text-[12.5px] text-text-muted"
            >
              <span class="block font-serif text-sm font-semibold text-text-primary">[{{ i + 1 }}] {{ src.title }}</span>
              <span v-if="src.section">{{ src.section }}</span>
            </div>
          </template>
        </div>
      </div>
    </div>

    <p v-if="quotaNote" class="mt-3.5 text-xs text-text-muted">
      {{ quotaNote }}
    </p>
  </section>
</template>
