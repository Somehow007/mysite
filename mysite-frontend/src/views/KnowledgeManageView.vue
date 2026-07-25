<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { useHead } from '@unhead/vue'
import {
  Plus, Trash2, Edit, Database, BookOpen, AlertCircle, CheckCircle2,
  Loader2, Search, X, FileText, FileUp, Layers, Cpu, ChevronDown,
  BrainCircuit, RotateCw, RefreshCw, Settings, Check, Eye, Clock, Filter,
} from 'lucide-vue-next'
import {
  getKnowledgeBases, createKnowledgeBase, updateKnowledgeBase, deleteKnowledgeBase,
  toggleKnowledgeBase,
  getKnowledgeDocuments, getAvailableArticles, addArticlesToKb,
  deleteKnowledgeDocument, reprocessDocument, getDocumentChunks,
  type KnowledgeChunk,
} from '@/api/rag'
import { useToast } from '@/composables/useToast'
import { useConfirm } from '@/composables/useConfirm'
import { Pagination, PageHeader, DataTable, Badge, StatusDot, Modal } from '@/components/ui'
import type { KnowledgeBase, KnowledgeDocument } from '@/types'
import type { AvailableArticle } from '@/api/rag'
import type { Column } from '@/components/ui/DataTable.vue'

const PAGE = 20

useHead(() => ({ title: '知识库管理 - MySite' }))

const toast = useToast()
const { confirm } = useConfirm()

// ── 知识库列表 ──
const kbs = ref<KnowledgeBase[]>([])
const loadingKbs = ref(false)
const selectedKbId = ref<string | null>(null)
const activeTab = ref<'docs' | 'articles' | 'settings'>('docs')

const selectedKb = computed(() =>
  kbs.value.find(k => k.id === selectedKbId.value) ?? null,
)

async function fetchKbs() {
  loadingKbs.value = true
  try { kbs.value = await getKnowledgeBases() } catch { /* ignore */ } finally { loadingKbs.value = false }
}

function selectKb(kbId: string) {
  if (selectedKbId.value === kbId) {
    selectedKbId.value = null
    return
  }
  selectedKbId.value = kbId
  docSearch.value = ''
  docStatusFilter.value = 'ALL'
  docPage.value = 1
  availSearch.value = ''
  availPage.value = 0
  checkedIds.value = new Set()
  fetchDocs()
  fetchAvailable()
}

// ── 新建/编辑知识库弹窗 ──
const showEditor = ref(false)
const saving = ref(false)
const editingKb = ref<KnowledgeBase | null>(null)
const editorForm = ref({
  name: '', description: '',
  embeddingModel: 'text-embedding-v4', embeddingDimension: 1024,
  chunkSize: 800, chunkOverlap: 100, chunkingMode: 'MARKDOWN_HEADING',
})
const showAdvancedConfig = ref(false)

function openCreate() {
  editingKb.value = null
  editorForm.value = { name: '', description: '', embeddingModel: 'text-embedding-v4', embeddingDimension: 1024, chunkSize: 800, chunkOverlap: 100, chunkingMode: 'MARKDOWN_HEADING' }
  showAdvancedConfig.value = false
  showEditor.value = true
}
function openEdit(kb: KnowledgeBase) {
  editingKb.value = kb
  editorForm.value = {
    name: kb.name, description: kb.description || '',
    embeddingModel: kb.embeddingModel, embeddingDimension: kb.embeddingDimension,
    chunkSize: kb.chunkSize, chunkOverlap: kb.chunkOverlap, chunkingMode: kb.chunkingMode || 'MARKDOWN_HEADING',
  }
  showAdvancedConfig.value = false
  showEditor.value = true
}
async function saveKb() {
  if (!editorForm.value.name.trim()) { toast.error('名称不能为空'); return }
  saving.value = true
  try {
    if (editingKb.value) {
      await updateKnowledgeBase(editingKb.value.id, editorForm.value)
      toast.success('知识库已更新')
    } else {
      await createKnowledgeBase(editorForm.value)
      toast.success('知识库已创建')
    }
    showEditor.value = false
    await fetchKbs()
  } catch (e: unknown) { toast.error((e as Error).message || '保存失败') } finally { saving.value = false }
}

// ── 删除知识库 ──
const showDelKb = ref(false)
const delTarget = ref<KnowledgeBase | null>(null)
const deletingKb = ref(false)
function confirmDelKb(kb: KnowledgeBase) { delTarget.value = kb; showDelKb.value = true }
async function doDelKb() {
  if (!delTarget.value) return
  deletingKb.value = true
  try {
    await deleteKnowledgeBase(delTarget.value.id)
    if (selectedKbId.value === delTarget.value.id) { selectedKbId.value = null }
    showDelKb.value = false
    await fetchKbs()
    toast.success('知识库已删除')
  } catch (e: unknown) { toast.error((e as Error).message || '删除失败') } finally { deletingKb.value = false }
}

// ── 文档列表（服务端分页） ──
const docs = ref<KnowledgeDocument[]>([])
const loadingDocs = ref(false)
const docSearch = ref('')
const docStatusFilter = ref<'ALL' | 'READY' | 'CHUNKING' | 'PENDING' | 'FAILED'>('ALL')
const docPage = ref(1)
const docTotal = ref(0)
const docPageSize = 20
const expandedDocId = ref<string | null>(null)
let docSearchTimer: ReturnType<typeof setTimeout> | null = null

const docStats = computed(() => ({
  total: docTotal.value,
  ready: docs.value.filter(d => d.status === 'READY').length,
  processing: docs.value.filter(d => d.status === 'PENDING' || d.status === 'CHUNKING').length,
  failed: docs.value.filter(d => d.status === 'FAILED').length,
}))

async function fetchDocs(page?: number) {
  if (!selectedKbId.value) return
  loadingDocs.value = true
  try {
    const p = page ?? docPage.value
    const params: Record<string, unknown> = {
      current: p,
      size: docPageSize,
      keyword: docSearch.value.trim() || undefined,
      status: docStatusFilter.value !== 'ALL' ? docStatusFilter.value : undefined,
    }
    const result = await getKnowledgeDocuments(selectedKbId.value, params)
    docs.value = result.list
    docTotal.value = result.pagination.total
    docPage.value = result.pagination.page
  } catch { docs.value = []; docTotal.value = 0 } finally { loadingDocs.value = false }
}

function onDocSearchInput() {
  if (docSearchTimer) clearTimeout(docSearchTimer)
  docSearchTimer = setTimeout(() => fetchDocs(1), 300)
}
// ── 分块预览 ──
const chunks = ref<KnowledgeChunk[]>([])
const loadingChunks = ref(false)
const chunkPage = ref(1)
const chunkTotal = ref(0)
const chunkPageSize = 30

async function fetchChunks(docId: string, page: number = 1) {
  if (!selectedKbId.value) return
  loadingChunks.value = true
  try {
    const result = await getDocumentChunks(selectedKbId.value, docId, {
      current: page,
      size: chunkPageSize,
    })
    chunks.value = result.list
    chunkTotal.value = result.pagination.total
    chunkPage.value = page
  } catch { chunks.value = [] } finally { loadingChunks.value = false }
}

function toggleDocExpand(docId: string) {
  if (expandedDocId.value === docId) {
    expandedDocId.value = null
  } else {
    expandedDocId.value = docId
    chunkPage.value = 1
    fetchChunks(docId, 1)
  }
}
async function delDoc(doc: KnowledgeDocument) {
  const ok = await confirm({ message: `确定移除文档「${doc.title}」？`, danger: true, confirmText: '移除' })
  if (!selectedKbId.value || !ok) return
  try {
    await deleteKnowledgeDocument(selectedKbId.value, doc.id)
    docs.value = docs.value.filter(d => d.id !== doc.id)
    if (expandedDocId.value === doc.id) expandedDocId.value = null
    toast.success('文档已移除')
  } catch (e: unknown) { toast.error((e as Error).message || '操作失败') }
}
async function reprocess(doc: KnowledgeDocument) {
  if (!selectedKbId.value) return
  try {
    await reprocessDocument(selectedKbId.value, doc.id)
    toast.success('已重新提交处理')
    await fetchDocs()
    // Start polling if there are processing docs
    if (hasActiveDocs()) startPolling()
  } catch (e: unknown) { toast.error((e as Error).message || '操作失败') }
}
	function setDocFilter(f: typeof docStatusFilter.value) {
	  docStatusFilter.value = f
	  fetchDocs(1)
	}

// ── 可选文章 ──
const available = ref<AvailableArticle[]>([])
const loadingAvail = ref(false)
const availSearch = ref('')
const availPage = ref(0)
const checkedIds = ref<Set<string>>(new Set())
const addingArticles = ref(false)

const filteredAvail = computed(() => {
  const q = availSearch.value.trim().toLowerCase()
  const source = q ? available.value.filter(a => a.title.toLowerCase().includes(q)) : available.value
  return source
})
const pagedAvail = computed(() => filteredAvail.value.slice(0, (availPage.value + 1) * PAGE))
const hasMoreAvail = computed(() => pagedAvail.value.length < filteredAvail.value.length)

async function fetchAvailable() {
  if (!selectedKbId.value) return
  loadingAvail.value = true
  try { available.value = await getAvailableArticles(selectedKbId.value) } catch { available.value = [] } finally { loadingAvail.value = false }
  checkedIds.value = new Set()
}
function toggleCheck(articleId: string) {
  const next = new Set(checkedIds.value)
  if (next.has(articleId)) next.delete(articleId); else next.add(articleId)
  checkedIds.value = next
}
function toggleAll() {
  if (checkedIds.value.size === pagedAvail.value.length && pagedAvail.value.length > 0) {
    checkedIds.value = new Set()
  } else {
    checkedIds.value = new Set(pagedAvail.value.map(a => a.id))
  }
}
async function addSelected() {
  if (!selectedKbId.value || checkedIds.value.size === 0) return
  addingArticles.value = true
  try {
    const result = await addArticlesToKb(selectedKbId.value, [...checkedIds.value])
    toast.success(`已添加 ${result.added} 篇文章，后台处理中`)
    checkedIds.value = new Set()
    availSearch.value = ''
    await fetchDocs()
    await fetchAvailable()
    if (hasActiveDocs()) startPolling()
  } catch (e: unknown) { toast.error((e as Error).message || '添加失败') } finally { addingArticles.value = false }
}
function loadMoreAvail() { availPage.value++ }

async function toggleKb(kb: KnowledgeBase) {
  try {
    const updated = await toggleKnowledgeBase(kb.id)
    const idx = kbs.value.findIndex(k => k.id === kb.id)
    if (idx !== -1) kbs.value[idx] = updated
    toast.success(updated.enabled ? `「${kb.name}」已启用` : `「${kb.name}」已禁用`)
  } catch (e: unknown) { toast.error((e as Error).message || '操作失败') }
}

// ── 工具函数 ──
type DocStatus = KnowledgeDocument['status']
function statusBadge(s: DocStatus): { icon: typeof CheckCircle2; cls: string; label: string } {
  switch (s) {
    case 'READY': return { icon: CheckCircle2, cls: 'text-success', label: '就绪' }
    case 'CHUNKING': return { icon: Loader2, cls: 'text-warning', label: '分块中' }
    case 'PENDING': return { icon: Clock, cls: 'text-warning', label: '排队中' }
    case 'FAILED': return { icon: AlertCircle, cls: 'text-danger', label: '失败' }
    default: return { icon: FileText, cls: 'text-text-muted', label: s }
  }
}
function sourceIcon(t: KnowledgeDocument['sourceType']) {
  return t === 'ARTICLE' ? BookOpen : FileText
}
function sourceLabel(t: KnowledgeDocument['sourceType']) {
  return t === 'ARTICLE' ? '文章' : '上传'
}
function fmtDate(t: string) {
  if (!t) return '-'
  const d = new Date(t)
  const now = new Date()
  const diffMs = now.getTime() - d.getTime()
  const diffMin = Math.floor(diffMs / 60000)
  if (diffMin < 1) return '刚刚'
  if (diffMin < 60) return `${diffMin} 分钟前`
  const diffHr = Math.floor(diffMin / 60)
  if (diffHr < 24) return `${diffHr} 小时前`
  return d.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })
}
function statusDotCls(s: DocStatus): string {
  switch (s) {
    case 'READY': return 'bg-success'
    case 'CHUNKING': return 'bg-warning animate-pulse'
    case 'PENDING': return 'bg-warning'
    case 'FAILED': return 'bg-danger'
    default: return 'bg-text-muted'
  }
}

// ── 文档状态轮询 ──
const polling = ref(false)
let pollTimer: ReturnType<typeof setTimeout> | null = null
let pollInterval = 5000 // 5s 起步
let consecutiveFailures = 0
const POLL_MAX = 30000 // 30s 封顶
const POLL_START = 5000
const POLL_MAX_FAILURES = 5

function hasActiveDocs(): boolean {
  return docs.value.some(d => d.status === 'PENDING' || d.status === 'CHUNKING')
}

function startPolling() {
  if (polling.value) return
  if (!hasActiveDocs()) return
  polling.value = true
  pollInterval = POLL_START
  consecutiveFailures = 0
  schedulePoll()
}

function schedulePoll() {
  if (!polling.value) return
  pollTimer = setTimeout(async () => {
    if (!selectedKbId.value || !polling.value) return
    try {
      const result = await getKnowledgeDocuments(selectedKbId.value, {
        current: docPage.value,
        size: docPageSize,
        keyword: docSearch.value.trim() || undefined,
        status: docStatusFilter.value !== 'ALL' ? docStatusFilter.value : undefined,
      })
      docs.value = result.list
      consecutiveFailures = 0
      // Gradually reduce to base interval
      pollInterval = POLL_START
      if (!hasActiveDocs()) {
        stopPolling()
        return
      }
    } catch {
      consecutiveFailures++
      if (consecutiveFailures >= POLL_MAX_FAILURES) {
        stopPolling()
        toast.error('文档状态刷新失败，请手动刷新')
        return
      }
      // Exponential backoff
      pollInterval = Math.min(pollInterval * 2, POLL_MAX)
    }
    schedulePoll()
  }, pollInterval)
}

function stopPolling() {
  polling.value = false
  if (pollTimer) { clearTimeout(pollTimer); pollTimer = null }
}

function handleVisibilityChange() {
  if (document.hidden) {
    stopPolling()
  } else if (selectedKbId.value && hasActiveDocs()) {
    startPolling()
  }
}

// When KB selected, check if polling should start
watch(selectedKbId, (newId) => {
  stopPolling()
  if (newId) {
    // Start after first fetch
    setTimeout(() => {
      if (hasActiveDocs()) startPolling()
    }, 2000)
  }
})

// Watch for docs changes after fetch
watch(docs, (newDocs) => {
  if (selectedKbId.value && newDocs && hasActiveDocs() && !polling.value) {
    startPolling()
  }
})

// DataTable columns for document list
const docColumns: Column<KnowledgeDocument>[] = [
  { key: 'title', label: '文档标题', width: 'auto' },
  { key: 'source', label: '来源', width: '80px' },
  { key: 'chunkCount', label: '分块', width: '64px', align: 'center' },
  { key: 'fileSize', label: '大小', width: '68px', align: 'center' },
  { key: 'status', label: '状态', width: '104px' },
  { key: 'updateTime', label: '更新时间', width: '104px', hideMobile: true },
  { key: 'actions', label: '操作', width: '88px', align: 'center' },
]

onMounted(() => {
  fetchKbs()
  document.addEventListener('visibilitychange', handleVisibilityChange)
})

onBeforeUnmount(() => {
  stopPolling()
  document.removeEventListener('visibilitychange', handleVisibilityChange)
})

// 切换 KB 时重置文档搜索/筛选
watch(selectedKbId, () => {
  docSearch.value = ''
  docStatusFilter.value = 'ALL'
  docPage.value = 1
  expandedDocId.value = null
})
</script>

<template>
  <div class="w-full max-w-6xl mx-auto p-6 space-y-6 min-w-0">
    <!-- ═══ 页头 ═══ -->
    <PageHeader title="知识库管理" subtitle="管理向量知识库，将博客文章同步为 AI 可检索的文档索引">
      <template #actions>
        <button class="btn-primary flex items-center gap-2 flex-shrink-0" @click="openCreate">
          <Plus :size="14" />
          新建知识库
        </button>
      </template>
    </PageHeader>

    <!-- ═══ 知识库卡片网格 ═══ -->
    <div v-if="loadingKbs" class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
      <div v-for="i in 3" :key="i" class="rounded-xl border border-border bg-bg-primary p-5 animate-pulse">
        <div class="h-5 bg-bg-secondary rounded w-2/3 mb-3" />
        <div class="h-3 bg-bg-secondary rounded w-full mb-2" />
        <div class="h-3 bg-bg-secondary rounded w-1/2" />
      </div>
    </div>

    <div v-else-if="kbs.length === 0" class="text-center py-20">
      <div class="inline-flex items-center justify-center w-16 h-16 rounded-2xl bg-accent-subtle mb-4">
        <Database :size="28" class="text-accent opacity-70" />
      </div>
      <h3 class="text-base font-medium text-text-primary mb-1">还没有知识库</h3>
      <p class="text-sm text-text-muted mb-5">创建知识库后，可将博客文章同步为向量索引，为 AI 助手提供知识来源</p>
      <button class="btn btn-primary flex items-center gap-2 mx-auto" @click="openCreate">
        <Plus :size="16" />
        创建第一个知识库
      </button>
    </div>

    <div v-else class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
      <button
        v-for="kb in kbs" :key="kb.id"
        class="group relative text-left rounded-xl border-[1.5px] p-[18px] transition-all duration-150 cursor-pointer shadow-sm"
        :class="selectedKbId === kb.id
          ? 'border-accent bg-bg-elevated shadow-[0_0_0_3px_rgba(79,70,229,0.14)]'
          : 'border-border bg-bg-elevated hover:border-accent hover:-translate-y-0.5 hover:shadow-md'"
        @click="selectKb(kb.id)"
      >
        <!-- Selected checkmark -->
        <div
          v-if="selectedKbId === kb.id"
          class="absolute -top-2 -left-2 w-[22px] h-[22px] rounded-full bg-accent text-white flex items-center justify-center shadow-md"
        >
          <CheckCircle2 :size="12" />
        </div>

        <div class="flex items-center gap-2.5 mb-2">
          <div class="w-9 h-9 rounded-[10px] bg-accent-subtle text-accent flex items-center justify-center shrink-0">
            <Database :size="17" />
          </div>
          <div class="min-w-0 flex-1">
            <h3 class="font-semibold text-[15px] text-text-primary truncate">{{ kb.name }}</h3>
            <p class="text-[11px] text-text-muted">{{ kb.docCount ?? 0 }} 篇文档 · {{ kb.embeddingModel }}</p>
          </div>
        </div>
        <p v-if="kb.description" class="text-[12.5px] text-text-secondary truncate mb-3.5">
          {{ kb.description }}
        </p>
        <p v-else class="text-[12.5px] text-text-muted truncate mb-3.5">暂无描述</p>
        <div class="flex items-center gap-2 flex-wrap">
          <span class="inline-flex items-center gap-1 px-2 py-0.5 rounded text-[11px] font-medium bg-accent-subtle text-accent">
            <Layers :size="10" /> {{ kb.chunkSize }}
          </span>
          <span class="inline-flex items-center gap-1 px-2 py-0.5 rounded text-[11px] font-medium bg-bg-code text-text-muted">
            <Cpu :size="10" /> {{ kb.chunkingMode === 'MARKDOWN_HEADING' ? '标题感知' : '固定大小' }}
          </span>
          <!-- 启用/禁用开关 -->
          <button
            class="ml-auto flex items-center gap-1.5 px-2 py-0.5 rounded text-[11px] font-medium transition-colors"
            :class="kb.enabled !== false ? 'bg-success-subtle text-success' : 'bg-bg-code text-text-muted'"
            :title="kb.enabled !== false ? '已启用，点击禁用' : '已禁用，点击启用'"
            @click.stop="toggleKb(kb)"
          >
            <span class="w-1.5 h-1.5 rounded-full" :class="kb.enabled !== false ? 'bg-success' : 'bg-text-muted'" />
            {{ kb.enabled !== false ? '启用' : '禁用' }}
          </button>
        </div>
      </button>
    </div>

    <!-- ═══ KB 未选中占位 ═══ -->
    <div v-if="kbs.length > 0 && !selectedKb" class="text-center py-12 text-text-muted">
      <Database :size="32" class="mx-auto mb-2 opacity-25" />
      <p class="text-sm">选择一个知识库以查看和管理文档</p>
    </div>

    <!-- ═══ 知识库详情面板 ═══ -->
    <template v-if="selectedKb">
      <div class="w-full min-w-0 card-solid overflow-hidden !rounded-2xl">
        <!-- 统计条：竖向单元格 -->
        <div class="grid grid-cols-4 border-b border-border">
          <div class="px-5 py-4 text-center border-r border-border last:border-r-0">
            <div class="text-[12.5px] text-text-muted mb-1">文档总数</div>
            <div class="text-[26px] font-bold text-text-primary tabular-nums leading-none">{{ docStats.total }}</div>
          </div>
          <div class="px-5 py-4 text-center border-r border-border last:border-r-0">
            <div class="text-[12.5px] text-text-muted mb-1">已就绪</div>
            <div class="text-[26px] font-bold text-success tabular-nums leading-none">{{ docStats.ready }}</div>
          </div>
          <div class="px-5 py-4 text-center border-r border-border last:border-r-0">
            <div class="text-[12.5px] text-text-muted mb-1">处理中</div>
            <div class="text-[26px] font-bold text-warning tabular-nums leading-none flex items-center justify-center gap-1.5">
              {{ docStats.processing }}
              <Loader2 v-if="docStats.processing > 0" :size="16" class="animate-spin text-warning/70" />
            </div>
            <div v-if="polling" class="text-[10px] text-text-muted mt-1 flex items-center justify-center gap-1">
              <RefreshCw :size="8" class="animate-spin" />每 5 秒自动刷新
            </div>
          </div>
          <div class="px-5 py-4 text-center">
            <div class="text-[12.5px] text-text-muted mb-1">失败</div>
            <div class="text-[26px] font-bold tabular-nums leading-none" :class="docStats.failed > 0 ? 'text-danger' : 'text-text-primary'">{{ docStats.failed }}</div>
          </div>
        </div>

        <!-- Tab 导航 + 操作 -->
        <div class="flex items-center justify-between border-b border-border px-5">
          <div class="flex items-center">
            <button
              class="flex items-center gap-2 px-4 py-3 text-sm font-medium transition-colors border-b-2 -mb-px"
              :class="activeTab === 'docs' ? 'border-accent text-accent' : 'border-transparent text-text-muted hover:text-text-secondary'"
              @click="activeTab = 'docs'"
            ><FileText :size="14" />已同步文档<span class="text-xs opacity-70 tabular-nums">({{ docs.length }})</span></button>
            <button
              class="flex items-center gap-2 px-4 py-3 text-sm font-medium transition-colors border-b-2 -mb-px"
              :class="activeTab === 'articles' ? 'border-accent text-accent' : 'border-transparent text-text-muted hover:text-text-secondary'"
              @click="activeTab = 'articles'"
            ><BookOpen :size="14" />添加文章<span class="text-xs opacity-70 tabular-nums">({{ available.length }})</span></button>
            <button
              class="flex items-center gap-2 px-4 py-3 text-sm font-medium transition-colors border-b-2 -mb-px"
              :class="activeTab === 'settings' ? 'border-accent text-accent' : 'border-transparent text-text-muted hover:text-text-secondary'"
              @click="activeTab = 'settings'"
            ><Settings :size="14" />分块设置</button>
          </div>
          <div class="flex items-center gap-2">
            <button class="btn-secondary text-xs flex items-center gap-1.5" @click="openEdit(selectedKb)">
              <Edit :size="13" />编辑
            </button>
            <button class="btn-secondary text-xs flex items-center gap-1.5 !text-danger" @click="confirmDelKb(selectedKb)">
              <Trash2 :size="13" />删除
            </button>
          </div>
        </div>

        <!-- Tab 内容 -->
        <!-- ═══ 文档列表 Tab ═══ -->
        <div v-if="activeTab === 'docs'" class="p-5 min-w-0">
          <!-- 搜索与筛选 -->
          <div class="flex items-center gap-3 mb-4">
            <div class="relative w-[260px] flex-shrink-0">
              <Search :size="14" class="absolute left-3 top-1/2 -translate-y-1/2 text-text-muted" />
              <input
                v-model="docSearch" type="text" placeholder="搜索文档标题..." @input="onDocSearchInput"
                class="w-full pl-9 pr-3 py-2 rounded-lg border border-border bg-bg-primary text-sm text-text-primary placeholder:text-text-muted/60 focus:outline-none focus:ring-2 focus:ring-accent/20 focus:border-accent/40 transition-shadow"
              />
            </div>
            <div class="flex items-center gap-1.5">
              <button
                v-for="f in (['ALL','READY','CHUNKING','PENDING','FAILED'] as const)"
                :key="f"
                class="px-3 py-1.5 rounded-full text-xs font-medium border transition-colors"
                :class="docStatusFilter === f
                  ? 'border-accent bg-accent-subtle text-accent'
                  : 'border-border text-text-muted hover:text-text-secondary hover:border-text-muted'"
                @click="setDocFilter(f)"
              >
                {{ f === 'ALL' ? '全部' : f === 'CHUNKING' ? '处理中' : f === 'PENDING' ? '排队' : f === 'READY' ? '就绪' : '失败' }}
              </button>
            </div>
            <div class="flex-1" />
            <button class="btn-secondary text-xs flex items-center gap-1.5" @click="fetchDocs()">
              <RefreshCw :size="13" :class="{ 'animate-spin': loadingDocs }" />同步
            </button>
          </div>

          <!-- 加载中 -->
          <div v-if="loadingDocs" class="space-y-2">
            <div v-for="i in 5" :key="i" class="h-12 rounded-lg bg-bg-secondary/50 animate-pulse" />
          </div>

          <!-- 空 -->
          <div v-else-if="docs.length === 0" class="text-center py-16">
            <FileText :size="36" class="mx-auto mb-3 text-text-muted/30" />
            <p class="text-sm text-text-muted mb-3">此知识库还没有同步文档</p>
            <button class="btn btn-secondary text-xs flex items-center gap-1.5 mx-auto" @click="activeTab = 'articles'">
              <BookOpen :size="13" />去添加文章
            </button>
          </div>

          <!-- 文档 DataTable -->
          <DataTable
            v-else
            :columns="docColumns"
            :data="docs"
            expandable
            :expanded-id="expandedDocId"
            :total="docTotal"
            :current-page="docPage"
            :page-size="docPageSize"
            @toggle-expand="toggleDocExpand($event)"
            @update:current-page="fetchDocs($event)"
          >
            <template #cell-title="{ item }">
              <div class="flex items-center gap-2.5">
                <div class="w-[30px] h-[30px] rounded-lg flex items-center justify-center shrink-0"
                  :class="item.sourceType === 'ARTICLE' ? 'bg-accent-subtle text-accent' : 'bg-bg-code text-text-muted'">
                  <component :is="sourceIcon(item.sourceType)" :size="15" />
                </div>
                <span class="text-sm text-text-primary font-medium truncate">{{ item.title }}</span>
              </div>
            </template>
            <template #cell-source="{ item }">
              <Badge :variant="item.sourceType === 'ARTICLE' ? 'accent' : 'muted'" size="sm">
                {{ sourceLabel(item.sourceType) }}
              </Badge>
            </template>
            <template #cell-chunkCount="{ value }">
              <span class="text-sm text-text-muted tabular-nums">{{ value ?? 0 }}</span>
            </template>
            <template #cell-fileSize="{ item }">
              <span class="text-xs text-text-muted">{{ item.charCount?.toLocaleString() ?? '-' }}</span>
            </template>
            <template #cell-status="{ item }">
              <Badge
                :variant="item.status === 'READY' ? 'success' : item.status === 'FAILED' ? 'danger' : 'warning'"
                dot
                :dot-pulse="item.status === 'CHUNKING'"
              >
                {{ statusBadge(item.status).label }}
              </Badge>
            </template>
            <template #cell-updateTime="{ item }">
              <span class="text-xs text-text-muted whitespace-nowrap">{{ fmtDate(item.createTime) }}</span>
            </template>
            <template #cell-actions="{ item }">
              <div class="flex items-center justify-center gap-0.5">
                <button
                  v-if="item.status === 'FAILED'"
                  class="p-1.5 rounded text-text-muted hover:text-accent hover:bg-accent-subtle transition-colors"
                  title="重新处理" @click="reprocess(item)"
                ><RotateCw :size="13" /></button>
                <button
                  class="p-1.5 rounded text-text-muted hover:text-danger hover:bg-danger-subtle transition-colors"
                  title="删除" @click="delDoc(item)"
                ><X :size="13" /></button>
                <ChevronDown :size="14" class="text-text-muted transition-transform duration-200"
                  :class="{ 'rotate-180': expandedDocId === item.id }" />
              </div>
            </template>
            <!-- 展开行：文档详情 + 分块预览 -->
            <template #expand="{ item }">
              <div class="px-4 py-4 space-y-3 bg-bg-code/30">
                <div class="grid grid-cols-2 sm:grid-cols-4 gap-x-4 gap-y-1.5 text-xs">
                  <div><span class="text-text-muted">来源</span><span class="text-text-primary ml-2 font-medium">{{ sourceLabel(item.sourceType) }}</span></div>
                  <div><span class="text-text-muted">分块数</span><span class="text-text-primary ml-2 font-medium tabular-nums">{{ item.chunkCount }}</span></div>
                  <div><span class="text-text-muted">字符数</span><span class="text-text-primary ml-2 font-medium tabular-nums">{{ item.charCount?.toLocaleString() ?? '-' }}</span></div>
                  <div><span class="text-text-muted">创建时间</span><span class="text-text-primary ml-2 font-medium">{{ fmtDate(item.createTime) }}</span></div>
                </div>
                <div v-if="item.status === 'FAILED' && item.failReason"
                  class="p-3 rounded-lg bg-danger-subtle border border-danger/20 text-xs text-danger flex items-start gap-2">
                  <AlertCircle :size="13" class="flex-shrink-0 mt-px" /><span>{{ item.failReason }}</span>
                </div>
                <!-- 分块预览 -->
                <div v-if="chunks.length > 0" class="pt-3 border-t border-dashed border-border">
                  <div class="flex items-center gap-3 mb-3 text-xs text-text-muted flex-wrap">
                    <span>分块模式：<strong class="text-text-primary">{{ selectedKb.chunkingMode === 'MARKDOWN_HEADING' ? '标题感知' : '固定大小' }}</strong></span>
                    <span>chunkSize：<strong class="text-text-primary">{{ selectedKb.chunkSize }}</strong></span>
                    <span>overlap：<strong class="text-text-primary">{{ selectedKb.chunkOverlap }}</strong></span>
                    <span class="tabular-nums">共 {{ chunkTotal }} 个分块</span>
                  </div>
                  <div class="grid grid-cols-1 lg:grid-cols-2 gap-3 max-h-80 overflow-y-auto">
                    <div v-if="loadingChunks" class="col-span-full flex justify-center py-4">
                      <Loader2 :size="14" class="animate-spin text-text-muted" />
                    </div>
                    <div v-for="chunk in chunks" :key="chunk.id" class="rounded-lg border border-border bg-bg-code p-3">
                      <div class="flex items-center gap-2 mb-1.5">
                        <span class="inline-flex items-center justify-center w-5 h-5 rounded-full bg-accent-subtle text-accent text-[10px] font-bold tabular-nums">#{{ chunk.chunkIndex }}</span>
                        <span class="text-[10px] text-text-muted">{{ chunk.charCount?.toLocaleString() ?? 0 }} 字符</span>
                      </div>
                      <p class="text-xs text-text-secondary leading-relaxed whitespace-pre-wrap break-words font-mono">{{ chunk.content.slice(0, 200) }}{{ chunk.content.length > 200 ? '...' : '' }}</p>
                    </div>
                  </div>
                  <div v-if="chunkTotal > chunkPageSize" class="mt-2 flex justify-center">
                    <Pagination :current="chunkPage" :total="chunkTotal" :page-size="chunkPageSize" @update:current="fetchChunks(expandedDocId!, $event)" />
                  </div>
                </div>
              </div>
            </template>
          </DataTable>
        </div>

        <!-- ═══ 添加文章 Tab ═══ -->
        <div v-if="activeTab === 'articles'" class="p-5">
          <!-- 搜索 + 操作 -->
          <div class="flex flex-col sm:flex-row gap-3 mb-4">
            <div class="relative flex-1">
              <Search :size="14" class="absolute left-3 top-1/2 -translate-y-1/2 text-text-muted" />
              <input
                v-model="availSearch" type="text" placeholder="搜索文章标题..."
                class="w-full pl-9 pr-3 py-2 rounded-lg border border-border bg-bg-primary text-sm text-text-primary placeholder:text-text-muted/60 focus:outline-none focus:ring-2 focus:ring-accent/20 focus:border-accent/40 transition-shadow"
              />
            </div>
            <button
              class="btn btn-primary text-xs flex items-center gap-1.5 flex-shrink-0"
              :disabled="checkedIds.size === 0 || addingArticles"
              @click="addSelected"
            >
              <Loader2 v-if="addingArticles" :size="13" class="animate-spin" />
              <Plus v-else :size="13" />
              {{ addingArticles ? '添加中...' : `添加选中 (${checkedIds.size})` }}
            </button>
          </div>

          <!-- 加载中 -->
          <div v-if="loadingAvail" class="space-y-2">
            <div v-for="i in 5" :key="i" class="h-10 rounded-lg bg-bg-secondary/50 animate-pulse" />
          </div>

          <!-- 空 -->
          <div v-else-if="available.length === 0" class="text-center py-12">
            <CheckCircle2 :size="36" class="mx-auto mb-3 text-success/40" />
            <p class="text-sm text-text-muted">所有文章已同步到此知识库</p>
          </div>

          <!-- 无匹配 -->
          <div v-else-if="pagedAvail.length === 0" class="text-center py-12 text-sm text-text-muted">
            没有匹配"{{ availSearch }}"的文章
          </div>

          <!-- 文章列表 -->
          <div v-else>
            <div class="rounded-lg border border-border/60 overflow-hidden">
              <div
                v-for="(a, idx) in pagedAvail" :key="a.id"
                class="flex items-center gap-3 px-4 py-2.5 cursor-pointer transition-colors hover:bg-bg-secondary/30"
                :class="[
                  idx < pagedAvail.length - 1 ? 'border-b border-border/30' : '',
                  checkedIds.has(a.id) ? 'bg-accent-subtle/20' : '',
                ]"
                @click="toggleCheck(a.id)"
              >
                <div
                  class="w-4 h-4 rounded border-2 flex-shrink-0 flex items-center justify-center transition-colors"
                  :class="checkedIds.has(a.id)
                    ? 'bg-accent border-accent'
                    : 'border-border/60 hover:border-accent/40'"
                >
                  <CheckCircle2 v-if="checkedIds.has(a.id)" :size="12" class="text-white" />
                </div>
                <BookOpen :size="14" class="text-text-muted flex-shrink-0" />
                <span class="flex-1 text-sm text-text-primary truncate">{{ a.title }}</span>
                <span class="flex-shrink-0 text-xs text-text-muted">{{ fmtDate(a.createTime) }}</span>
              </div>
            </div>
            <!-- 全选 + 分页 -->
            <div class="flex items-center justify-between mt-3">
              <label class="flex items-center gap-1.5 text-xs text-text-muted cursor-pointer select-none">
                <input
                  type="checkbox"
                  :checked="checkedIds.size === pagedAvail.length && pagedAvail.length > 0"
                  @change="toggleAll"
                  class="w-3.5 h-3.5 rounded accent-accent"
                />
                全选可见
              </label>
              <span class="text-xs text-text-muted tabular-nums">已选 {{ checkedIds.size }} / {{ available.length }} 篇</span>
            </div>
            <div v-if="hasMoreAvail" class="text-center mt-3">
              <button class="text-sm text-accent hover:underline font-medium" @click="loadMoreAvail">
                加载更多 ({{ filteredAvail.length - pagedAvail.length }})
              </button>
            </div>
          </div>
        </div>

        <!-- ═══ 分块设置 Tab ═══ -->
        <div v-if="activeTab === 'settings' && selectedKb" class="p-5 space-y-5">
          <div class="rounded-lg border border-border bg-bg-code p-4">
            <h3 class="text-sm font-medium text-text-primary mb-1 flex items-center gap-2">
              <Cpu :size="14" class="text-accent" />
              分块策略
            </h3>
            <p class="text-xs text-text-muted mb-4">
              调整这些参数会影响检索精度和语义连贯性。修改后新入库的文档将使用新设置。
            </p>
            <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div>
                <label class="block text-xs font-medium text-text-secondary mb-1">
                  分块大小（chunkSize）
                </label>
                <div class="flex items-center gap-2">
                  <input
                    type="number"
                    :value="selectedKb.chunkSize"
                    readonly
                    class="w-20 px-2 py-1.5 text-sm rounded border border-border bg-bg-secondary text-text-primary text-center"
                  />
                  <span class="text-xs text-text-muted">字符</span>
                </div>
                <p class="text-[11px] text-text-muted mt-1">
                  每个切片的最大字符数，推荐 512–800。过大会降低检索精度，过小会切断语义。
                </p>
              </div>
              <div>
                <label class="block text-xs font-medium text-text-secondary mb-1">
                  重叠大小（chunkOverlap）
                </label>
                <div class="flex items-center gap-2">
                  <input
                    type="number"
                    :value="selectedKb.chunkOverlap"
                    readonly
                    class="w-20 px-2 py-1.5 text-sm rounded border border-border bg-bg-secondary text-text-primary text-center"
                  />
                  <span class="text-xs text-text-muted">字符</span>
                </div>
                <p class="text-[11px] text-text-muted mt-1">
                  相邻切片之间的重叠字符数，推荐 80–150。防止信息在切片边界处丢失。
                </p>
              </div>
            </div>
          </div>
          <div class="rounded-lg border border-border bg-bg-code p-4">
            <h3 class="text-sm font-medium text-text-primary mb-1 flex items-center gap-2">
              <Layers :size="14" class="text-accent" />
              分块模式
            </h3>
            <div class="flex items-center gap-2 mt-2">
              <span
                class="px-2.5 py-1 rounded-full text-xs font-medium"
                :class="selectedKb.chunkingMode === 'MARKDOWN_HEADING' ? 'bg-accent-subtle text-accent' : 'bg-bg-secondary text-text-muted'"
              >
                {{ selectedKb.chunkingMode === 'MARKDOWN_HEADING' ? 'Markdown 标题感知' : '固定大小' }}
              </span>
            </div>
            <p class="text-xs text-text-muted mt-2">
              Markdown 标题感知模式会根据标题层级结构保持语义连贯性；固定大小模式仅按字符数切分。
            </p>
          </div>
          <div class="rounded-lg border border-border bg-bg-code p-4">
            <h3 class="text-sm font-medium text-text-primary mb-1 flex items-center gap-2">
              <BrainCircuit :size="14" class="text-accent" />
              Embedding 模型
            </h3>
            <p class="text-xs text-text-muted mb-3">向量化模型影响语义理解的深度。</p>
            <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div>
                <label class="block text-xs font-medium text-text-secondary mb-1">模型名称</label>
                <input
                  type="text"
                  :value="selectedKb.embeddingModel"
                  readonly
                  class="w-full px-2 py-1.5 text-sm rounded border border-border bg-bg-secondary text-text-primary"
                />
              </div>
              <div>
                <label class="block text-xs font-medium text-text-secondary mb-1">向量维度</label>
                <input
                  type="number"
                  :value="selectedKb.embeddingDimension"
                  readonly
                  class="w-20 px-2 py-1.5 text-sm rounded border border-border bg-bg-secondary text-text-primary text-center"
                />
              </div>
            </div>
          </div>
          <p class="text-xs text-text-muted">
            提示：修改以上设置请通过右上角「编辑」按钮进入知识库编辑界面。
          </p>
        </div>
      </div>
    </template>

    <!-- ═══ 新建/编辑知识库弹窗 ═══ -->
    <Modal
      :open="showEditor"
      :title="editingKb ? '编辑知识库' : '新建知识库'"
      max-width="max-w-lg"
      @update:open="showEditor = $event"
    >
      <div class="space-y-4">
        <div class="space-y-3">
          <h3 class="text-xs font-semibold text-text-muted uppercase tracking-wider flex items-center gap-2">
            <Database :size="12" />基本信息
          </h3>
          <div>
            <label class="block text-sm font-medium text-text-primary mb-1.5">名称 <span class="text-danger">*</span></label>
            <input v-model="editorForm.name" class="input-base" placeholder="例如：博客文章知识库" @keyup.enter="saveKb" />
          </div>
          <div>
            <label class="block text-sm font-medium text-text-primary mb-1.5">描述</label>
            <textarea v-model="editorForm.description" rows="2" class="input-base resize-none" placeholder="可选：描述知识库的用途和内容范围" />
          </div>
        </div>
        <div class="border-t border-border pt-4">
          <button class="flex items-center gap-1.5 text-xs font-semibold text-text-muted uppercase tracking-wider hover:text-text-secondary transition-colors w-full" @click="showAdvancedConfig = !showAdvancedConfig">
            <BrainCircuit :size="12" />高级配置
            <ChevronDown :size="12" class="transition-transform duration-200" :class="{ 'rotate-180': showAdvancedConfig }" />
          </button>
          <div v-if="showAdvancedConfig" class="grid grid-cols-2 gap-3 mt-3">
            <div>
              <label class="block text-sm font-medium text-text-primary mb-1.5">嵌入模型</label>
              <input v-model="editorForm.embeddingModel" class="input-base" />
            </div>
            <div>
              <label class="block text-sm font-medium text-text-primary mb-1.5">向量维度</label>
              <input v-model.number="editorForm.embeddingDimension" type="number" class="input-base" />
            </div>
            <div>
              <label class="block text-sm font-medium text-text-primary mb-1.5">分块大小</label>
              <input v-model.number="editorForm.chunkSize" type="number" class="input-base" />
            </div>
            <div>
              <label class="block text-sm font-medium text-text-primary mb-1.5">分块重叠</label>
              <input v-model.number="editorForm.chunkOverlap" type="number" class="input-base" />
            </div>
            <div class="col-span-2">
              <label class="block text-sm font-medium text-text-primary mb-1.5">分块模式</label>
              <select v-model="editorForm.chunkingMode" class="input-base">
                <option value="MARKDOWN_HEADING">Markdown 标题感知（推荐）</option>
                <option value="FIXED_SIZE">固定大小</option>
              </select>
              <p class="text-[11px] text-text-muted mt-1">Markdown 标题感知模式会根据标题层级结构保持语义连贯性；固定大小模式仅按字符数切分。</p>
            </div>
          </div>
        </div>
      </div>
      <template #footer>
        <button class="btn-secondary text-sm" @click="showEditor = false">取消</button>
        <button class="btn-primary text-sm flex items-center gap-2" :disabled="saving" @click="saveKb">
          <Loader2 v-if="saving" :size="14" class="animate-spin" />
          {{ saving ? '保存中...' : (editingKb ? '保存修改' : '创建知识库') }}
        </button>
      </template>
    </Modal>

    <!-- ═══ 删除确认弹窗 ═══ -->
    <Modal
      :open="showDelKb"
      title="删除知识库"
      max-width="max-w-sm"
      @update:open="showDelKb = $event"
    >
      <div class="text-center">
        <div class="flex items-center justify-center w-11 h-11 rounded-full bg-danger-subtle mx-auto mb-4">
          <AlertCircle :size="22" class="text-danger" />
        </div>
        <p class="text-sm text-text-muted mb-5">
          确定要删除「<span class="font-medium text-text-primary">{{ delTarget?.name }}</span>」吗？<br />
          所有关联的文档、分块和向量数据将被<b class="text-danger">永久移除</b>，此操作不可撤销。
        </p>
      </div>
      <template #footer>
        <button class="btn-secondary text-sm flex-1" @click="showDelKb = false">取消</button>
        <button class="btn-danger text-sm flex-1 flex items-center justify-center gap-2" :disabled="deletingKb" @click="doDelKb">
          <Loader2 v-if="deletingKb" :size="14" class="animate-spin" />
          {{ deletingKb ? '删除中...' : '确认删除' }}
        </button>
      </template>
    </Modal>
  </div>
</template>
