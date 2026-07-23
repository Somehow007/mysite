<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useHead } from '@unhead/vue'
import {
  Plus, Trash2, Edit, Database, BookOpen, AlertCircle, CheckCircle2,
  Loader2, Search, X, FileText, Layers, Clock, Cpu, ChevronDown,
  BrainCircuit, Filter, RotateCw,
} from 'lucide-vue-next'
import {
  getKnowledgeBases, createKnowledgeBase, updateKnowledgeBase, deleteKnowledgeBase,
  getKnowledgeDocuments, getAvailableArticles, addArticlesToKb,
  deleteKnowledgeDocument, reprocessDocument,
} from '@/api/rag'
import { useToast } from '@/composables/useToast'
import type { KnowledgeBase, KnowledgeDocument } from '@/types'
import type { AvailableArticle } from '@/api/rag'

useHead(() => ({ title: '知识库管理 - MySite' }))

const toast = useToast()

// ── 知识库列表 ──
const kbs = ref<KnowledgeBase[]>([])
const loadingKbs = ref(false)
const selectedKbId = ref<string | null>(null)
const activeTab = ref<'docs' | 'articles'>('docs')

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
  docPage.value = 0
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
  chunkSize: 800, chunkOverlap: 100,
})
const showAdvancedConfig = ref(false)

function openCreate() {
  editingKb.value = null
  editorForm.value = { name: '', description: '', embeddingModel: 'text-embedding-v4', embeddingDimension: 1024, chunkSize: 800, chunkOverlap: 100 }
  showAdvancedConfig.value = false
  showEditor.value = true
}
function openEdit(kb: KnowledgeBase) {
  editingKb.value = kb
  editorForm.value = {
    name: kb.name, description: kb.description || '',
    embeddingModel: kb.embeddingModel, embeddingDimension: kb.embeddingDimension,
    chunkSize: kb.chunkSize, chunkOverlap: kb.chunkOverlap,
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

// ── 文档列表 ──
const docs = ref<KnowledgeDocument[]>([])
const loadingDocs = ref(false)
const docSearch = ref('')
const docStatusFilter = ref<'ALL' | 'READY' | 'CHUNKING' | 'PENDING' | 'FAILED'>('ALL')
const docPage = ref(0)
const PAGE = 20
const expandedDocId = ref<string | null>(null)

const docStats = computed(() => ({
  total: docs.value.length,
  ready: docs.value.filter(d => d.status === 'READY').length,
  processing: docs.value.filter(d => d.status === 'PENDING' || d.status === 'CHUNKING').length,
  failed: docs.value.filter(d => d.status === 'FAILED').length,
}))

const filteredDocs = computed(() => {
  let list = docs.value
  if (docSearch.value.trim()) {
    const q = docSearch.value.trim().toLowerCase()
    list = list.filter(d => d.title.toLowerCase().includes(q))
  }
  if (docStatusFilter.value !== 'ALL') {
    list = list.filter(d => d.status === docStatusFilter.value)
  }
  return list
})
const pagedDocs = computed(() => filteredDocs.value.slice(0, (docPage.value + 1) * PAGE))
const hasMoreDocs = computed(() => pagedDocs.value.length < filteredDocs.value.length)

async function fetchDocs() {
  if (!selectedKbId.value) return
  loadingDocs.value = true
  try { docs.value = await getKnowledgeDocuments(selectedKbId.value) } catch { docs.value = [] } finally { loadingDocs.value = false }
}
function toggleDocExpand(docId: string) {
  expandedDocId.value = expandedDocId.value === docId ? null : docId
}
async function delDoc(doc: KnowledgeDocument) {
  if (!selectedKbId.value || !confirm(`确定移除文档「${doc.title}」？`)) return
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
    setTimeout(() => fetchDocs(), 2000)
  } catch (e: unknown) { toast.error((e as Error).message || '操作失败') }
}
function loadMoreDocs() { docPage.value++ }
function setDocFilter(f: typeof docStatusFilter.value) {
  docStatusFilter.value = f
  docPage.value = 0
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
    setTimeout(() => { fetchDocs(); fetchAvailable() }, 1500)
  } catch (e: unknown) { toast.error((e as Error).message || '添加失败') } finally { addingArticles.value = false }
}
function loadMoreAvail() { availPage.value++ }

// ── 工具函数 ──
type DocStatus = KnowledgeDocument['status']
function statusBadge(s: DocStatus): { icon: typeof CheckCircle2; cls: string; label: string } {
  switch (s) {
    case 'READY': return { icon: CheckCircle2, cls: 'text-emerald-500', label: '就绪' }
    case 'CHUNKING': return { icon: Loader2, cls: 'text-amber-500', label: '分块中' }
    case 'PENDING': return { icon: Clock, cls: 'text-amber-400', label: '排队中' }
    case 'FAILED': return { icon: AlertCircle, cls: 'text-red-500', label: '失败' }
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
    case 'READY': return 'bg-emerald-400'
    case 'CHUNKING': return 'bg-amber-400 animate-pulse'
    case 'PENDING': return 'bg-amber-300'
    case 'FAILED': return 'bg-red-400'
    default: return 'bg-slate-300'
  }
}

// 切换 KB 时重置文档搜索/筛选
watch(selectedKbId, () => {
  docSearch.value = ''
  docStatusFilter.value = 'ALL'
  docPage.value = 0
  expandedDocId.value = null
})

onMounted(() => { fetchKbs() })
</script>

<template>
  <div class="max-w-6xl mx-auto p-6 space-y-6">
    <!-- ═══ 页头 ═══ -->
    <div class="flex items-start justify-between gap-4">
      <div>
        <h1 class="text-2xl font-bold text-text-primary">知识库管理</h1>
        <p class="text-sm text-text-muted mt-1">管理向量知识库，将博客文章同步为 AI 可检索的文档索引</p>
      </div>
      <button class="btn btn-primary flex items-center gap-2 flex-shrink-0" @click="openCreate">
        <Plus :size="16" />
        新建知识库
      </button>
    </div>

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
        class="group text-left rounded-xl border p-5 transition-all duration-200 cursor-pointer"
        :class="selectedKbId === kb.id
          ? 'border-accent/50 bg-accent-subtle/30 shadow-sm ring-1 ring-accent/20'
          : 'border-border bg-bg-primary hover:border-accent/30 hover:shadow-sm hover:-translate-y-0.5'"
        @click="selectKb(kb.id)"
      >
        <div class="flex items-start justify-between gap-2 mb-3">
          <div class="flex items-center gap-2 min-w-0">
            <div class="w-8 h-8 rounded-lg flex items-center justify-center flex-shrink-0"
              :class="selectedKbId === kb.id ? 'bg-accent/15 text-accent' : 'bg-bg-secondary text-text-muted group-hover:text-accent transition-colors'">
              <Database :size="16" />
            </div>
            <h3 class="font-semibold text-text-primary truncate text-sm">{{ kb.name }}</h3>
          </div>
          <span class="text-xs text-text-muted flex-shrink-0 tabular-nums">{{ kb.docCount ?? 0 }} 篇</span>
        </div>
        <p v-if="kb.description" class="text-xs text-text-muted line-clamp-2 mb-3 leading-relaxed">
          {{ kb.description }}
        </p>
        <div class="flex items-center gap-3 text-xs text-text-muted">
          <span class="flex items-center gap-1">
            <Cpu :size="11" />
            {{ kb.embeddingModel }}
          </span>
          <span class="flex items-center gap-1">
            <Layers :size="11" />
            {{ kb.chunkSize }}
          </span>
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
      <!-- 详情页头 -->
      <div class="rounded-xl border border-border bg-bg-primary overflow-hidden">
        <div class="flex flex-wrap items-center justify-between gap-3 px-5 py-4 border-b border-border/50 bg-bg-secondary/30">
          <div class="flex items-center gap-3 min-w-0">
            <div class="w-9 h-9 rounded-xl bg-accent/10 text-accent flex items-center justify-center flex-shrink-0">
              <Database :size="18" />
            </div>
            <div class="min-w-0">
              <h2 class="font-semibold text-text-primary truncate">{{ selectedKb.name }}</h2>
              <p v-if="selectedKb.description" class="text-xs text-text-muted truncate">{{ selectedKb.description }}</p>
            </div>
          </div>
          <div class="flex items-center gap-2 flex-shrink-0">
            <div class="hidden sm:flex items-center gap-3 text-xs text-text-muted mr-2">
              <span class="flex items-center gap-1"><Cpu :size="11" />{{ selectedKb.embeddingModel }}</span>
              <span class="text-border">·</span>
              <span class="flex items-center gap-1"><Layers :size="11" />{{ selectedKb.chunkSize }}/{{ selectedKb.chunkOverlap }}</span>
            </div>
            <button class="btn btn-secondary text-xs flex items-center gap-1.5" @click="openEdit(selectedKb)">
              <Edit :size="13" />编辑
            </button>
            <button class="btn text-xs flex items-center gap-1.5 text-red-500 hover:bg-red-50" @click="confirmDelKb(selectedKb)">
              <Trash2 :size="13" />删除
            </button>
          </div>
        </div>

        <!-- 统计卡片 -->
        <div class="grid grid-cols-2 lg:grid-cols-4 gap-0 divide-x divide-y divide-border/40 border-b border-border/50">
          <div class="px-5 py-3.5 flex items-center gap-3">
            <FileText :size="18" class="text-text-muted flex-shrink-0" />
            <div>
              <div class="text-lg font-bold text-text-primary tabular-nums">{{ docStats.total }}</div>
              <div class="text-xs text-text-muted">文档总数</div>
            </div>
          </div>
          <div class="px-5 py-3.5 flex items-center gap-3">
            <CheckCircle2 :size="18" class="text-emerald-500 flex-shrink-0" />
            <div>
              <div class="text-lg font-bold text-emerald-600 tabular-nums">{{ docStats.ready }}</div>
              <div class="text-xs text-text-muted">已就绪</div>
            </div>
          </div>
          <div class="px-5 py-3.5 flex items-center gap-3">
            <Loader2 :size="18" class="text-amber-500 flex-shrink-0" :class="{ 'animate-spin': docStats.processing > 0 }" />
            <div>
              <div class="text-lg font-bold text-amber-600 tabular-nums">{{ docStats.processing }}</div>
              <div class="text-xs text-text-muted">处理中</div>
            </div>
          </div>
          <div class="px-5 py-3.5 flex items-center gap-3">
            <AlertCircle :size="18" class="text-red-500 flex-shrink-0" />
            <div>
              <div class="text-lg font-bold tabular-nums" :class="docStats.failed > 0 ? 'text-red-600' : 'text-text-primary'">{{ docStats.failed }}</div>
              <div class="text-xs text-text-muted">失败</div>
            </div>
          </div>
        </div>

        <!-- Tab 导航 -->
        <div class="flex items-center border-b border-border/50">
          <button
            class="flex items-center gap-2 px-5 py-3 text-sm font-medium transition-colors border-b-2 -mb-px"
            :class="activeTab === 'docs'
              ? 'border-accent text-accent'
              : 'border-transparent text-text-muted hover:text-text-secondary'"
            @click="activeTab = 'docs'"
          >
            <FileText :size="14" />
            已同步文档
            <span class="text-xs opacity-70 tabular-nums">({{ docs.length }})</span>
          </button>
          <button
            class="flex items-center gap-2 px-5 py-3 text-sm font-medium transition-colors border-b-2 -mb-px"
            :class="activeTab === 'articles'
              ? 'border-accent text-accent'
              : 'border-transparent text-text-muted hover:text-text-secondary'"
            @click="activeTab = 'articles'"
          >
            <BookOpen :size="14" />
            添加文章
            <span class="text-xs opacity-70 tabular-nums">({{ available.length }})</span>
          </button>
        </div>

        <!-- Tab 内容 -->
        <!-- ═══ 文档列表 Tab ═══ -->
        <div v-if="activeTab === 'docs'" class="p-5">
          <!-- 搜索与筛选 -->
          <div class="flex flex-col sm:flex-row gap-3 mb-4">
            <div class="relative flex-1">
              <Search :size="14" class="absolute left-3 top-1/2 -translate-y-1/2 text-text-muted" />
              <input
                v-model="docSearch" type="text" placeholder="搜索文档标题..."
                class="w-full pl-9 pr-3 py-2 rounded-lg border border-border bg-bg-primary text-sm text-text-primary placeholder:text-text-muted/60 focus:outline-none focus:ring-2 focus:ring-accent/20 focus:border-accent/40 transition-shadow"
              />
            </div>
            <div class="flex items-center gap-1.5 flex-shrink-0">
              <Filter :size="13" class="text-text-muted" />
              <button
                v-for="f in (['ALL','READY','CHUNKING','PENDING','FAILED'] as const)"
                :key="f"
                class="px-2.5 py-1.5 rounded-md text-xs font-medium transition-colors"
                :class="docStatusFilter === f
                  ? 'bg-accent text-white'
                  : 'bg-bg-secondary text-text-muted hover:text-text-secondary hover:bg-bg-code'"
                @click="setDocFilter(f)"
              >
                {{ f === 'ALL' ? '全部' : f === 'CHUNKING' ? '处理中' : f === 'PENDING' ? '排队' : f === 'READY' ? '就绪' : '失败' }}
              </button>
            </div>
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

          <!-- 无匹配 -->
          <div v-else-if="filteredDocs.length === 0" class="text-center py-12 text-sm text-text-muted">
            <Search :size="28" class="mx-auto mb-2 opacity-25" />
            {{ docSearch ? '没有匹配的文档' : '当前筛选条件下没有文档' }}
          </div>

          <!-- 文档列表 -->
          <div v-else class="space-y-1.5">
            <div
              v-for="d in pagedDocs" :key="d.id"
              class="rounded-lg border border-border/60 hover:border-border hover:bg-bg-secondary/30 transition-all"
            >
              <!-- 行 -->
              <div
                class="flex items-center gap-3 px-4 py-3 cursor-pointer select-none"
                :class="expandedDocId === d.id ? '' : ''"
                @click="toggleDocExpand(d.id)"
              >
                <!-- 来源图标 -->
                <component :is="sourceIcon(d.sourceType)" :size="16"
                  class="flex-shrink-0"
                  :class="d.sourceType === 'ARTICLE' ? 'text-accent' : 'text-text-muted'" />
                <!-- 标题 -->
                <span class="flex-1 text-sm text-text-primary truncate font-medium">{{ d.title }}</span>
                <!-- 分块数 -->
                <span class="flex-shrink-0 text-xs text-text-muted w-10 text-right tabular-nums">{{ d.chunkCount }}</span>
                <!-- 状态 -->
                <span class="flex-shrink-0 flex items-center gap-1 text-xs font-medium" :class="statusBadge(d.status).cls">
                  <span class="w-1.5 h-1.5 rounded-full" :class="statusDotCls(d.status)" />
                  {{ statusBadge(d.status).label }}
                </span>
                <!-- 操作 -->
                <div class="flex-shrink-0 flex items-center gap-1 ml-1" @click.stop>
                  <button
                    v-if="d.status === 'FAILED'"
                    class="p-1 rounded text-text-muted hover:text-accent hover:bg-accent-subtle transition-colors"
                    title="重新处理"
                    @click="reprocess(d)"
                  >
                    <RotateCw :size="13" />
                  </button>
                  <button
                    class="p-1 rounded text-text-muted hover:text-red-500 hover:bg-red-50 transition-colors"
                    title="删除"
                    @click="delDoc(d)"
                  >
                    <X :size="13" />
                  </button>
                  <ChevronDown
                    :size="14"
                    class="text-text-muted transition-transform duration-200"
                    :class="{ 'rotate-180': expandedDocId === d.id }" />
                </div>
              </div>
              <!-- 展开详情 -->
              <div
                v-if="expandedDocId === d.id"
                class="px-4 pb-4 pt-0 border-t border-border/30 mx-4 space-y-2"
              >
                <div class="grid grid-cols-2 sm:grid-cols-3 gap-x-4 gap-y-1.5 text-xs">
                  <div>
                    <span class="text-text-muted">来源类型</span>
                    <span class="text-text-primary ml-2 font-medium">{{ sourceLabel(d.sourceType) }}</span>
                  </div>
                  <div>
                    <span class="text-text-muted">分块数量</span>
                    <span class="text-text-primary ml-2 font-medium tabular-nums">{{ d.chunkCount }}</span>
                  </div>
                  <div>
                    <span class="text-text-muted">字符数</span>
                    <span class="text-text-primary ml-2 font-medium tabular-nums">{{ d.charCount?.toLocaleString() ?? '-' }}</span>
                  </div>
                  <div>
                    <span class="text-text-muted">创建时间</span>
                    <span class="text-text-primary ml-2 font-medium">{{ fmtDate(d.createTime) }}</span>
                  </div>
                  <div v-if="d.sourceRef">
                    <span class="text-text-muted">源 ID</span>
                    <span class="text-text-primary ml-2 font-medium font-mono text-[11px]">{{ d.sourceRef }}</span>
                  </div>
                  <div v-if="d.fileType">
                    <span class="text-text-muted">文件类型</span>
                    <span class="text-text-primary ml-2 font-medium">{{ d.fileType }}</span>
                  </div>
                </div>
                <div
                  v-if="d.status === 'FAILED' && d.failReason"
                  class="mt-2 p-3 rounded-lg bg-red-50 border border-red-200 text-xs text-red-700 flex items-start gap-2"
                >
                  <AlertCircle :size="13" class="flex-shrink-0 mt-px" />
                  <span>{{ d.failReason }}</span>
                </div>
              </div>
            </div>
            <!-- 加载更多 -->
            <div v-if="hasMoreDocs" class="text-center pt-3">
              <button class="text-sm text-accent hover:underline font-medium" @click="loadMoreDocs">
                加载更多 ({{ filteredDocs.length - pagedDocs.length }})
              </button>
            </div>
          </div>
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
            <CheckCircle2 :size="36" class="mx-auto mb-3 text-emerald-400/40" />
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
      </div>
    </template>

    <!-- ═══ 新建/编辑知识库弹窗 ═══ -->
    <Teleport to="body">
      <Transition name="fade">
        <div v-if="showEditor" class="fixed inset-0 z-50 flex items-center justify-center bg-black/40" @click.self="showEditor = false">
          <div class="bg-bg-primary rounded-2xl shadow-xl border border-border w-full max-w-lg mx-4 max-h-[90vh] overflow-y-auto" @click.stop>
            <!-- Modal header -->
            <div class="flex items-center justify-between px-6 py-4 border-b border-border/50">
              <h2 class="text-lg font-semibold text-text-primary">
                {{ editingKb ? '编辑知识库' : '新建知识库' }}
              </h2>
              <button class="p-1 rounded-lg text-text-muted hover:text-text-primary hover:bg-bg-secondary transition-colors" @click="showEditor = false">
                <X :size="18" />
              </button>
            </div>
            <!-- Modal body -->
            <div class="px-6 py-4 space-y-4">
              <!-- 基本信息 -->
              <div class="space-y-3">
                <h3 class="text-xs font-semibold text-text-muted uppercase tracking-wider flex items-center gap-2">
                  <Database :size="12" />基本信息
                </h3>
                <div>
                  <label class="block text-sm font-medium text-text-primary mb-1.5">名称 <span class="text-red-400">*</span></label>
                  <input
                    v-model="editorForm.name"
                    class="w-full px-3 py-2.5 rounded-lg border border-border bg-bg-primary text-sm text-text-primary placeholder:text-text-muted/50 focus:outline-none focus:ring-2 focus:ring-accent/20 focus:border-accent/40 transition-shadow"
                    placeholder="例如：博客文章知识库"
                    @keyup.enter="saveKb"
                  />
                </div>
                <div>
                  <label class="block text-sm font-medium text-text-primary mb-1.5">描述</label>
                  <textarea
                    v-model="editorForm.description"
                    rows="2"
                    class="w-full px-3 py-2.5 rounded-lg border border-border bg-bg-primary text-sm text-text-primary placeholder:text-text-muted/50 focus:outline-none focus:ring-2 focus:ring-accent/20 focus:border-accent/40 transition-shadow resize-none"
                    placeholder="可选：描述知识库的用途和内容范围"
                  />
                </div>
              </div>

              <!-- 高级配置 -->
              <div class="border-t border-border/50 pt-4">
                <button
                  class="flex items-center gap-1.5 text-xs font-semibold text-text-muted uppercase tracking-wider hover:text-text-secondary transition-colors w-full"
                  @click="showAdvancedConfig = !showAdvancedConfig"
                >
                  <BrainCircuit :size="12" />高级配置
                  <ChevronDown :size="12" class="transition-transform duration-200" :class="{ 'rotate-180': showAdvancedConfig }" />
                </button>
                <div v-if="showAdvancedConfig" class="grid grid-cols-2 gap-3 mt-3">
                  <div>
                    <label class="block text-sm font-medium text-text-primary mb-1.5">嵌入模型</label>
                    <input
                      v-model="editorForm.embeddingModel"
                      class="w-full px-3 py-2.5 rounded-lg border border-border bg-bg-primary text-sm text-text-primary focus:outline-none focus:ring-2 focus:ring-accent/20 focus:border-accent/40 transition-shadow"
                    />
                  </div>
                  <div>
                    <label class="block text-sm font-medium text-text-primary mb-1.5">向量维度</label>
                    <input
                      v-model.number="editorForm.embeddingDimension"
                      type="number"
                      class="w-full px-3 py-2.5 rounded-lg border border-border bg-bg-primary text-sm text-text-primary focus:outline-none focus:ring-2 focus:ring-accent/20 focus:border-accent/40 transition-shadow"
                    />
                  </div>
                  <div>
                    <label class="block text-sm font-medium text-text-primary mb-1.5">分块大小</label>
                    <input
                      v-model.number="editorForm.chunkSize"
                      type="number"
                      class="w-full px-3 py-2.5 rounded-lg border border-border bg-bg-primary text-sm text-text-primary focus:outline-none focus:ring-2 focus:ring-accent/20 focus:border-accent/40 transition-shadow"
                    />
                  </div>
                  <div>
                    <label class="block text-sm font-medium text-text-primary mb-1.5">分块重叠</label>
                    <input
                      v-model.number="editorForm.chunkOverlap"
                      type="number"
                      class="w-full px-3 py-2.5 rounded-lg border border-border bg-bg-primary text-sm text-text-primary focus:outline-none focus:ring-2 focus:ring-accent/20 focus:border-accent/40 transition-shadow"
                    />
                  </div>
                </div>
              </div>
            </div>
            <!-- Modal footer -->
            <div class="flex justify-end gap-3 px-6 py-4 border-t border-border/50 bg-bg-secondary/20 rounded-b-2xl">
              <button class="btn btn-secondary text-sm" @click="showEditor = false">取消</button>
              <button class="btn btn-primary text-sm flex items-center gap-2" :disabled="saving" @click="saveKb">
                <Loader2 v-if="saving" :size="14" class="animate-spin" />
                {{ saving ? '保存中...' : (editingKb ? '保存修改' : '创建知识库') }}
              </button>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>

    <!-- ═══ 删除确认弹窗 ═══ -->
    <Teleport to="body">
      <Transition name="fade">
        <div v-if="showDelKb" class="fixed inset-0 z-50 flex items-center justify-center bg-black/40" @click.self="showDelKb = false">
          <div class="bg-bg-primary rounded-2xl shadow-xl border border-border w-full max-w-sm mx-4 p-6" @click.stop>
            <div class="flex items-center justify-center w-11 h-11 rounded-full bg-red-100 mx-auto mb-4">
              <AlertCircle :size="22" class="text-red-500" />
            </div>
            <h3 class="text-center font-semibold text-text-primary mb-1">删除知识库</h3>
            <p class="text-center text-sm text-text-muted mb-5">
              确定要删除「<span class="font-medium text-text-primary">{{ delTarget?.name }}</span>」吗？<br />
              所有关联的文档、分块和向量数据将被<b class="text-red-500">永久移除</b>，此操作不可撤销。
            </p>
            <div class="flex gap-3">
              <button class="btn btn-secondary text-sm flex-1" @click="showDelKb = false">取消</button>
              <button
                class="btn bg-red-500 hover:bg-red-600 text-white text-sm flex-1 flex items-center justify-center gap-2"
                :disabled="deletingKb"
                @click="doDelKb"
              >
                <Loader2 v-if="deletingKb" :size="14" class="animate-spin" />
                {{ deletingKb ? '删除中...' : '确认删除' }}
              </button>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>
