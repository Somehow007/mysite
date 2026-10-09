<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useHead } from '@unhead/vue'
import { Loader2, Save, ArrowLeft, FileText, ChevronRight, AlertCircle, X, Plus, Search, TrendingUp, Library, Check, ChevronDown, Lock, Globe, Keyboard, Upload, FileUp, ImageOff } from 'lucide-vue-next'
import { createArticle, updateArticle, getArticleById, importArticle } from '@/api/article'
import type { ArticleImportImage } from '@/api/article'
import { getCategories } from '@/api/category'
import { getTags, createTag } from '@/api/tag'
import { getCollections, addArticleToCollection, removeArticleFromCollection } from '@/api/collection'
import { uploadImage, MAX_IMAGE_FILE_SIZE } from '@/api/image'
import { useUserStore } from '@/stores/user'
import { useToast } from '@/composables/useToast'
import MarkdownWysiwygEditor from '@/components/editor/MarkdownWysiwygEditor.vue'
import type { Category, Tag, Collection } from '@/types'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const toast = useToast()

const isEdit = computed(() => !!route.params.id)
const pageTitle = computed(() => isEdit.value ? '编辑文章' : '写文章')

useHead(() => ({
  title: `${pageTitle.value} - MySite`,
}))

const title = ref('')
const content = ref('')
const summary = ref('')
const coverImage = ref('')
const categoryId = ref('')
const selectedTagIds = ref<string[]>([])

const categories = ref<Category[]>([])
const tags = ref<Tag[]>([])
const collections = ref<Collection[]>([])
const selectedCollectionId = ref<string>('')
// 记录编辑时的原始合集ID，用于判断是否需要切换合集
const originalCollectionId = ref<string>('')
const visibility = ref<number>(0)
const loading = ref(false)
const saving = ref(false)
const error = ref('')

const showMetaPanel = ref(false)
const showSummaryHint = ref(false)
const coverUploading = ref(false)
let summaryHintTimer: ReturnType<typeof setTimeout> | null = null

// ── 文档导入（txt / md）──
const importInputRef = ref<HTMLInputElement | null>(null)
const repairInputRef = ref<HTMLInputElement | null>(null)
const importing = ref(false)
const repairing = ref(false)
const importNotice = ref<{ failed: ArticleImportImage[]; uploaded: number; kept: number } | null>(null)

function triggerImportPicker() {
  if (importing.value) return
  importInputRef.value?.click()
}

const DOC_EXT = /\.(txt|md|markdown)$/i
const IMG_EXT = /\.(png|jpe?g|gif|webp|svg|bmp|avif)$/i

function fileBaseName(path: string): string {
  return path.split(/[\\/]/).pop() || path
}

/**
 * 把「图片待补传」占位（或残留的 Obsidian / Markdown 引用）替换为已上传图片。
 */
function replaceImagePlaceholder(markdown: string, originalRef: string, newUrl: string): string {
  const esc = originalRef.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  // 与 ArticleDocumentParser.buildPlaceholder 生成格式一一对应
  const callout = new RegExp(
    '\\n{0,2}> \\[!warning\\] 图片待补传\\n' +
      '> 原引用：`' + esc + '`\\n' +
      '> 未能自动上传。[^\\n]*\\n?',
    'g',
  )
  const asImage = `\n\n![](${newUrl})\n\n`
  let out = markdown.replace(callout, asImage)
  if (out !== markdown) return out

  const obs = new RegExp('!\\[\\[' + esc + '(?:\\|[^\\]]*)?\\]\\]', 'g')
  out = markdown.replace(obs, `![](${newUrl})`)
  if (out !== markdown) return out

  const mdImg = new RegExp('!\\[[^\\]]*\\]\\(' + esc + '\\)', 'g')
  return markdown.replace(mdImg, `![](${newUrl})`)
}

async function onImportFile(e: Event) {
  const input = e.target as HTMLInputElement
  const selected = Array.from(input.files || [])
  // 允许重复选同一文件
  input.value = ''
  if (selected.length === 0) return

  // 多选：文档 1 个 + 图片附件若干（Obsidian ![[图]] 按文件名匹配）
  const docs = selected.filter(f => DOC_EXT.test(f.name))
  const images = selected.filter(f => IMG_EXT.test(f.name))
  const ignored = selected.filter(f => !DOC_EXT.test(f.name) && !IMG_EXT.test(f.name))

  if (docs.length === 0) {
    toast.error('请选择 TXT / Markdown 文档（可同时选中图片附件）')
    return
  }
  if (docs.length > 1) {
    toast.error('一次只能导入一个文档，请只选一个 txt/md')
    return
  }
  if (ignored.length > 0) {
    toast.error(`已忽略 ${ignored.length} 个不支持的文件（仅识别图片附件）`)
  }

  const file = docs[0]!
  if (file.size > 2 * 1024 * 1024) {
    toast.error('文档文件过大（最大 2MB）')
    return
  }

  // 编辑区已有内容时确认覆盖，避免误操作丢稿
  const hasBody = content.value.trim().length > 0 || title.value.trim().length > 0
  if (hasBody && !window.confirm('导入将覆盖当前标题和正文，确定继续？')) {
    return
  }

  importing.value = true
  importNotice.value = null
  error.value = ''
  try {
    const result = await importArticle(file, images)
    content.value = result.content
    if (!title.value.trim() && result.title) {
      title.value = result.title
    }

    const imgResults = result.images || []
    const failed = imgResults.filter(i => i.status === 'FAILED')
    const uploaded = imgResults.filter(i => i.status === 'UPLOADED').length
    const kept = imgResults.filter(i => i.status === 'KEPT').length

    if (failed.length > 0) {
      importNotice.value = { failed, uploaded, kept }
      toast.error(`导入完成，${failed.length} 张图片未自动上传——点上方「选择图片补传」即可`)
    } else if (uploaded > 0) {
      toast.success(`导入成功，已自动上传 ${uploaded} 张图片`)
    } else {
      toast.success('导入成功，内容已填入编辑器')
    }
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : '导入失败'
    error.value = msg
    toast.error(msg)
  } finally {
    importing.value = false
  }
}

/**
 * 二次补传：单独选图片文件（可来自任意目录，如 Obsidian attachments/），
 * 按文件名匹配失败项，上传后替换正文占位。
 */
function triggerRepairPicker() {
  if (repairing.value || !importNotice.value) return
  repairInputRef.value?.click()
}

async function onRepairFiles(e: Event) {
  const input = e.target as HTMLInputElement
  const files = Array.from(input.files || []).filter(f => IMG_EXT.test(f.name))
  input.value = ''
  if (files.length === 0 || !importNotice.value) return

  repairing.value = true
  const failed = importNotice.value.failed
  const byBase = new Map<string, ArticleImportImage>()
  for (const img of failed) {
    byBase.set(fileBaseName(img.originalRef).toLowerCase(), img)
  }

  let patched = content.value
  const doneKeys = new Set<string>()
  const errorByKey = new Map<string, string>()
  let ok = 0

  for (const file of files) {
    const key = file.name.toLowerCase()
    const target = byBase.get(key)
    if (!target || doneKeys.has(key)) continue
    if (file.size > MAX_IMAGE_FILE_SIZE) {
      errorByKey.set(key, `图片超过 ${MAX_IMAGE_FILE_SIZE / (1024 * 1024)}MB`)
      continue
    }
    try {
      const uploaded = await uploadImage(file)
      patched = replaceImagePlaceholder(patched, target.originalRef, uploaded.url)
      doneKeys.add(key)
      ok++
    } catch (err) {
      errorByKey.set(key, err instanceof Error ? err.message : '上传失败')
    }
  }

  content.value = patched

  const remaining: ArticleImportImage[] = []
  for (const img of failed) {
    const key = fileBaseName(img.originalRef).toLowerCase()
    if (doneKeys.has(key)) continue
    const reason = errorByKey.get(key)
    remaining.push(reason ? { ...img, reason } : img)
  }

  if (remaining.length === 0) {
    importNotice.value = null
    toast.success(`补传完成，${ok} 张图片已替换到正文`)
  } else {
    importNotice.value = {
      failed: remaining,
      uploaded: importNotice.value.uploaded + ok,
      kept: importNotice.value.kept,
    }
    if (ok > 0) {
      toast.error(`已补传 ${ok} 张，仍有 ${remaining.length} 张待处理`)
    } else {
      toast.error('所选图片未能匹配到待补传项（请确认文件名一致）')
    }
  }
}

function dismissImportNotice() {
  importNotice.value = null
}

// 编辑器使用说明弹层（快捷键与 Milkdown Crepe 内置 keymap 保持一致）
const showEditorHelp = ref(false)
const isMac = /mac/i.test(navigator.platform)
const K = {
  mod: isMac ? '⌘' : 'Ctrl',
  alt: isMac ? '⌥' : 'Alt',
  shift: isMac ? '⇧' : 'Shift',
}
const editorHelpGroups = [
  {
    label: '通用',
    items: [
      [`${K.mod} S`, '保存文章'],
      [`${K.mod} Z`, '撤销'],
      [`${K.mod} ${K.shift} Z`, '重做'],
    ],
  },
  {
    label: '行内格式',
    items: [
      [`${K.mod} B`, '加粗'],
      [`${K.mod} I`, '斜体'],
      [`${K.mod} E`, '行内代码'],
      [`${K.mod} ${K.alt} X`, '删除线'],
    ],
  },
  {
    label: '块级结构',
    items: [
      [`${K.mod} ${K.alt} 1~6`, '标题 H1–H6'],
      [`${K.mod} ${K.alt} 0`, '正文'],
      [`${K.mod} ${K.shift} B`, '引用块'],
      [`${K.mod} ${K.alt} 7 / 8`, '有序 / 无序列表'],
      [`${K.mod} ${K.alt} C`, '代码块'],
    ],
  },
] as const
const editorHelpTips = [
  '输入 / 唤起斜杠菜单：标注 Callout、图片、表格、公式等',
  '选中文字弹出浮动格式工具栏',
  '直接粘贴截图或拖入图片，自动上传插入',
  '支持导入 TXT / Markdown；可一并选中图片附件（匹配 Obsidian ![[图]]）',
  '从 IDE 复制的 Markdown 粘贴后自动渲染为排版',
  '引用块首行输入 [!note] + 空格，生成标注卡片',
] as const

const tagSearchQuery = ref('')
const newTagName = ref('')
const newTagSlug = ref('')
const creatingTag = ref(false)

// 合集下拉框
const collectionDropdownOpen = ref(false)
const collectionDropdownRef = ref<HTMLElement | null>(null)

const selectedCollectionTitle = computed(() => {
  if (!selectedCollectionId.value) return ''
  return collections.value.find(c => c.id === selectedCollectionId.value)?.title || ''
})

function toggleCollectionDropdown() {
  collectionDropdownOpen.value = !collectionDropdownOpen.value
}

function selectCollection(id: string) {
  selectedCollectionId.value = id
  collectionDropdownOpen.value = false
}

function closeCollectionDropdown(e: MouseEvent) {
  if (collectionDropdownRef.value && !collectionDropdownRef.value.contains(e.target as Node)) {
    collectionDropdownOpen.value = false
  }
}

const filteredTags = computed(() => {
  if (!tagSearchQuery.value.trim()) return tags.value
  const q = tagSearchQuery.value.trim().toLowerCase()
  return tags.value.filter(
    t => t.name.toLowerCase().includes(q) || t.slug.toLowerCase().includes(q)
  )
})

const hotTags = computed(() =>
  [...tags.value]
    .filter(t => (t.articleCount ?? 0) > 0)
    .sort((a, b) => (b.articleCount ?? 0) - (a.articleCount ?? 0))
    .slice(0, 5)
)

onMounted(async () => {
  document.addEventListener('click', closeCollectionDropdown)
  loading.value = true
  try {
    const [catsRes, tagsRes, collectionsRes] = await Promise.all([
      getCategories(),
      getTags(),
      getCollections({ page: 1, size: 100 }).catch(() => ({ list: [] as Collection[], pagination: { page: 1, size: 100, total: 0, totalPages: 0 } })),
    ])
    categories.value = catsRes
    tags.value = tagsRes
    collections.value = collectionsRes.list

    // 从合集编辑页跳转来新建文章时，自动选中目标合集
    const collectionQuery = route.query.collection as string
    if (collectionQuery && !isEdit.value) {
      selectedCollectionId.value = collectionQuery
      showMetaPanel.value = true
    }

    if (isEdit.value && route.params.id) {
      const article = await getArticleById(route.params.id as string)
      title.value = article.title
      content.value = article.content || ''
      summary.value = article.summary || ''
      coverImage.value = article.coverImage || ''
      categoryId.value = article.categoryId || ''
      if (article.tags) {
        selectedTagIds.value = article.tags.map(t => t.id)
      }
      if (article.collectionId) {
        selectedCollectionId.value = article.collectionId
        originalCollectionId.value = article.collectionId
      }
      if (article.visibility !== undefined) {
        visibility.value = article.visibility
      }
      if (summary.value || categoryId.value || coverImage.value || selectedTagIds.value.length > 0 || selectedCollectionId.value) {
        showMetaPanel.value = true
      }
    }
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
})

onUnmounted(() => {
  if (summaryHintTimer) clearTimeout(summaryHintTimer)
  document.removeEventListener('click', closeCollectionDropdown)
})

async function handleSave(isPublish: boolean) {
  // 防止并发保存（双击、Ctrl+S 在保存未完成时再次触发等）
  if (saving.value) return

  if (!title.value.trim()) {
    error.value = '请输入文章标题'
    return
  }
  if (!content.value.trim()) {
    error.value = '请输入文章内容'
    return
  }

  if (isPublish && !summary.value.trim()) {
    showMetaPanel.value = true
    showSummaryHint.value = true
    error.value = '发布文章前，建议填写文章摘要'
    if (summaryHintTimer) clearTimeout(summaryHintTimer)
    summaryHintTimer = setTimeout(() => {
      showSummaryHint.value = false
    }, 3000)
    return
  }

  // 在异步操作开始前捕获编辑模式，避免 reactive 状态在 async 过程中变化导致误判
  const editing = isEdit.value && !!route.params.id
  const articleId = editing ? (route.params.id as string) : undefined

  saving.value = true
  error.value = ''

  try {
    const userId = userStore.user?.id
    if (!userId) {
      error.value = '请先登录'
      return
    }

    if (editing && articleId) {
      await updateArticle({
        id: articleId,
        title: title.value.trim(),
        content: content.value,
        summary: summary.value.trim() || undefined,
        coverImage: coverImage.value.trim(),
        categoryId: categoryId.value || undefined,
        tagIds: selectedTagIds.value.length > 0 ? selectedTagIds.value : undefined,
        published: isPublish ? 1 : 0,
        visibility: visibility.value,
      })
      // 处理合集关联变更
      const newCollectionId = selectedCollectionId.value
      const oldCollectionId = originalCollectionId.value

      if (newCollectionId !== oldCollectionId) {
        // 合集发生了变化
        // 先从旧合集移除
        if (oldCollectionId) {
          try {
            await removeArticleFromCollection(oldCollectionId, articleId)
          } catch (e) {
            const msg = e instanceof Error ? e.message : '未知错误'
            toast.error(`文章已保存，但从旧合集移除失败: ${msg}`)
          }
        }
        // 添加到新合集
        if (newCollectionId) {
          try {
            await addArticleToCollection(newCollectionId, articleId)
          } catch (e) {
            const msg = e instanceof Error ? e.message : '加入合集失败'
            toast.error(`文章已保存，但加入合集失败: ${msg}`)
          }
        }
      }
    } else {
      await createArticle({
        title: title.value.trim(),
        content: content.value,
        authorId: userId,
        summary: summary.value.trim() || undefined,
        coverImage: coverImage.value.trim(),
        categoryId: categoryId.value || undefined,
        tagIds: selectedTagIds.value.length > 0 ? selectedTagIds.value : undefined,
        published: isPublish ? 1 : 0,
        visibility: visibility.value,
        collectionId: selectedCollectionId.value || undefined,
      })
    }

    router.push('/dashboard')
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : '保存失败'
  } finally {
    saving.value = false
  }
}

function toggleTag(tagId: string) {
  const idx = selectedTagIds.value.indexOf(tagId)
  if (idx >= 0) {
    selectedTagIds.value.splice(idx, 1)
  } else {
    selectedTagIds.value.push(tagId)
  }
}

async function handleCreateTag() {
  if (!newTagName.value.trim()) {
    toast.error('请输入标签名称')
    return
  }
  const slug = newTagSlug.value.trim() || newTagName.value.trim()
    .toLowerCase()
    .replace(/\s+/g, '-')
    .replace(/[^\w\u4e00-\u9fa5-]/g, '')

  if (!slug) {
    toast.error('请输入标签别名')
    return
  }

  creatingTag.value = true
  try {
    const created = await createTag({
      name: newTagName.value.trim(),
      slug,
    })
    tags.value.push(created)
    selectedTagIds.value.push(created.id)
    newTagName.value = ''
    newTagSlug.value = ''
    toast.success('标签已创建')
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : '创建标签失败'
    toast.error(msg)
  } finally {
    creatingTag.value = false
  }
}

function toggleMetaPanel() {
  showMetaPanel.value = !showMetaPanel.value
}

async function handleCoverUpload(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  if (!file.type.startsWith('image/')) {
    toast.error('请选择图片文件')
    return
  }
  
  if (file.size > MAX_IMAGE_FILE_SIZE) {
    const maxSizeMB = MAX_IMAGE_FILE_SIZE / (1024 * 1024)
    const fileSizeMB = (file.size / (1024 * 1024)).toFixed(2)
    toast.error(`图片文件过大（当前: ${fileSizeMB}MB，限制: ${maxSizeMB}MB）`)
    return
  }
  
  coverUploading.value = true
  try {
    const result = await uploadImage(file, (progressEvent) => {
      if (progressEvent.total) {
        const progress = Math.round((progressEvent.loaded * 100) / progressEvent.total)
        console.log(`上传进度: ${progress}%`)
      }
    })
    coverImage.value = result.url
  } catch (err) {
    const errorMessage = err instanceof Error ? err.message : '封面上传失败'
    if (errorMessage.includes('413') || errorMessage.includes('过大')) {
      const maxSizeMB = MAX_IMAGE_FILE_SIZE / (1024 * 1024)
      const fileSizeMB = (file.size / (1024 * 1024)).toFixed(2)
      error.value = `图片文件过大（当前: ${fileSizeMB}MB，限制: ${maxSizeMB}MB）`
    } else {
      error.value = errorMessage
    }
  } finally {
    coverUploading.value = false
    input.value = ''
  }
}

function removeCover() {
  coverImage.value = ''
}
</script>

<template>
  <div class="max-w-[1400px] mx-auto flex-1 flex flex-col min-h-0 w-full">
    <div class="flex items-center justify-between mb-6">
      <div class="flex items-center gap-4">
        <button
          @click="router.back()"
          class="p-2 rounded-lg text-text-muted hover:bg-accent-subtle hover:text-accent transition-all duration-200 group"
        >
          <ArrowLeft :size="18" class="group-hover:-translate-x-0.5 transition-transform duration-200" />
        </button>
        <h1 class="text-2xl font-semibold text-text-primary">
          {{ pageTitle }}
        </h1>
      </div>

      <div class="flex items-center gap-3">
        <input
          ref="importInputRef"
          type="file"
          multiple
          accept=".txt,.md,.markdown,text/plain,text/markdown,image/*,.png,.jpg,.jpeg,.gif,.webp,.svg,.bmp,.avif"
          class="hidden"
          @change="onImportFile"
        />
        <input
          ref="repairInputRef"
          type="file"
          multiple
          accept="image/*,.png,.jpg,.jpeg,.gif,.webp,.svg,.bmp,.avif"
          class="hidden"
          @change="onRepairFiles"
        />
        <button
          type="button"
          class="btn-secondary"
          :disabled="importing"
          title="导入 TXT / Markdown 文档（可同时选中图片附件，按文件名匹配 ![[图片]]）"
          @click="triggerImportPicker"
        >
          <Loader2 v-if="importing" :size="14" class="animate-spin" />
          <Upload v-else :size="14" />
          <span class="hidden sm:inline">{{ importing ? '导入中…' : '导入文档' }}</span>
        </button>
        <button
          @click="toggleMetaPanel"
          class="btn-secondary"
          :class="{ 'bg-accent-subtle text-accent border-accent/30': showMetaPanel }"
          :aria-expanded="showMetaPanel"
          :aria-label="showMetaPanel ? '收起文章信息面板' : '展开文章信息面板'"
        >
          <FileText :size="14" />
          <span class="hidden sm:inline">{{ showMetaPanel ? '收起' : '文章信息' }}</span>
          <ChevronRight
            :size="14"
            class="transition-transform duration-200"
            :class="{ 'rotate-90': showMetaPanel }"
          />
        </button>
        <button
          @click="handleSave(false)"
          :disabled="saving"
          class="btn-secondary disabled:opacity-50"
        >
          <Loader2 v-if="saving" :size="14" class="animate-spin" />
          <Save v-else :size="14" />
          存草稿
        </button>
        <button
          @click="handleSave(true)"
          :disabled="saving"
          class="btn-primary disabled:opacity-50"
        >
          <Loader2 v-if="saving" :size="14" class="animate-spin" />
          发布
        </button>
      </div>
    </div>

    <div v-if="error" class="mb-6 p-3 rounded-lg bg-danger-subtle text-danger text-sm flex items-center gap-2">
      <AlertCircle :size="16" />
      {{ error }}
    </div>

    <!-- 导入后图片补传提示（不阻断编辑） -->
    <div
      v-if="importNotice"
      class="mb-4 rounded-xl border border-accent/30 bg-accent-subtle/60 p-4"
      role="status"
    >
      <div class="flex items-start gap-3">
        <ImageOff :size="18" class="text-accent flex-shrink-0 mt-0.5" />
        <div class="flex-1 min-w-0">
          <div class="flex items-start justify-between gap-3">
            <div>
              <p class="text-sm font-medium text-text-primary">
                导入完成，{{ importNotice.failed.length }} 张图片未能自动上传
              </p>
              <p class="text-xs text-text-secondary mt-1">
                远程图会自动转存；本地 / Obsidian 图需文件名对上附件。正文已用「图片待补传」标出位置。
                <template v-if="importNotice.uploaded || importNotice.kept">
                  已自动上传 {{ importNotice.uploaded }} 张、保留本站图片 {{ importNotice.kept }} 张。
                </template>
              </p>
            </div>
            <button
              type="button"
              class="p-1 rounded text-text-muted hover:text-text-primary hover:bg-bg-secondary transition-colors"
              aria-label="关闭补传提示"
              @click="dismissImportNotice"
            >
              <X :size="14" />
            </button>
          </div>

          <div class="mt-3 flex flex-wrap items-center gap-2">
            <button
              type="button"
              class="btn-primary"
              :disabled="repairing"
              @click="triggerRepairPicker"
            >
              <Loader2 v-if="repairing" :size="14" class="animate-spin" />
              <FileUp v-else :size="14" />
              选择图片补传
            </button>
            <span class="text-xs text-text-muted">
              可多选（如 Obsidian attachments/ 里的图），按文件名自动替换占位
            </span>
          </div>

          <ul class="mt-3 space-y-1.5">
            <li
              v-for="(img, idx) in importNotice.failed"
              :key="idx"
              class="flex items-start gap-2 text-xs text-text-secondary"
            >
              <FileUp :size="12" class="mt-0.5 flex-shrink-0 text-danger/70" />
              <div class="min-w-0">
                <span class="font-mono break-all text-text-primary/80">{{ img.originalRef }}</span>
                <span v-if="img.reason" class="text-text-muted"> — {{ img.reason }}</span>
              </div>
            </li>
          </ul>
        </div>
      </div>
    </div>

    <div v-if="loading" class="py-16 text-center text-text-muted">
      加载中...
    </div>

    <div v-else class="flex gap-6 flex-1 min-h-0">
      <div class="flex-1 min-w-0 flex flex-col space-y-4 min-h-0">
        <input
          v-model="title"
          type="text"
          placeholder="文章标题..."
          class="w-full px-0 py-3 text-2xl font-semibold bg-transparent border-none outline-none text-text-primary placeholder:text-text-muted"
        />

        <div class="relative flex justify-end">
          <button
            type="button"
            class="flex items-center gap-1.5 text-xs text-text-muted hover:text-accent transition-colors"
            :class="{ 'text-accent': showEditorHelp }"
            @click="showEditorHelp = !showEditorHelp"
          >
            <Keyboard :size="14" />
            <span>使用说明 · 快捷键</span>
          </button>

          <!-- 点击外部关闭 -->
          <div v-if="showEditorHelp" class="fixed inset-0 z-40" @click="showEditorHelp = false"></div>

          <div
            v-if="showEditorHelp"
            class="absolute right-0 top-full mt-2 z-50 w-84 rounded-xl border border-border bg-bg-elevated card-shadow p-4"
          >
            <h3 class="text-sm font-semibold text-text-primary mb-2.5">编辑器使用说明</h3>
            <ul class="space-y-1.5 text-xs text-text-secondary mb-4">
              <li v-for="tip in editorHelpTips" :key="tip" class="flex gap-1.5">
                <span class="text-accent flex-shrink-0">•</span>
                <span>{{ tip }}</span>
              </li>
            </ul>
            <div v-for="group in editorHelpGroups" :key="group.label" class="mb-3 last:mb-0">
              <h4 class="text-xs font-medium text-text-muted mb-1.5">{{ group.label }}</h4>
              <div class="grid grid-cols-2 gap-x-3 gap-y-1.5">
                <div v-for="[keys, desc] in group.items" :key="keys" class="flex items-center justify-between gap-2">
                  <span class="text-xs text-text-secondary">{{ desc }}</span>
                  <kbd class="px-1.5 py-0.5 rounded border border-border bg-bg-code text-text-secondary font-mono text-[11px] whitespace-nowrap">
                    {{ keys }}
                  </kbd>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div class="h-[calc(100vh-280px)] min-h-[500px] rounded-xl border border-border overflow-hidden bg-bg-secondary card-shadow">
          <MarkdownWysiwygEditor
            v-model="content"
            placeholder="开始创作，输入 / 唤起更多功能..."
            @save="handleSave(false)"
          />
        </div>
      </div>

      <transition name="slide">
        <div
          v-show="showMetaPanel"
          class="w-80 flex-shrink-0 space-y-6 overflow-y-auto max-h-[calc(100vh-200px)]"
          role="region"
          aria-label="文章元数据"
        >
          <div>
            <label class="block text-sm font-medium text-text-primary mb-1.5">
              摘要
              <span v-if="showSummaryHint" class="text-danger ml-1">*</span>
            </label>
            <textarea
              v-model="summary"
              placeholder="文章摘要（建议填写）..."
              rows="3"
              class="input-base resize-y"
              :class="{ 'ring-2 ring-danger': showSummaryHint }"
            />
          </div>

          <div>
            <label class="block text-sm font-medium text-text-primary mb-1.5">
              分类
            </label>
            <select v-model="categoryId" class="input-base">
              <option value="">未分类</option>
              <option v-for="cat in categories" :key="cat.id" :value="cat.id">
                {{ cat.name }}
              </option>
            </select>
          </div>

          <div>
            <label class="block text-sm font-medium text-text-primary mb-1.5">
              标签
            </label>

            <div v-if="selectedTagIds.length > 0" class="flex flex-wrap gap-1.5 mb-3">
              <span
                v-for="tagId in selectedTagIds"
                :key="tagId"
                class="inline-flex items-center gap-1 text-xs px-2 py-1 rounded-full border border-accent bg-accent text-text-inverse"
              >
                #{{ tags.find(t => t.id === tagId)?.name || tagId }}
                <button @click="toggleTag(tagId)" class="hover:opacity-70 transition-opacity">
                  <X :size="10" />
                </button>
              </span>
            </div>

            <div v-if="hotTags.length > 0" class="mb-3">
              <div class="flex items-center gap-1.5 mb-1.5">
                <TrendingUp :size="12" class="text-accent" />
                <span class="text-xs text-text-muted">热门标签</span>
              </div>
              <div class="flex flex-wrap gap-1.5">
                <button
                  v-for="tag in hotTags"
                  :key="'hot-' + tag.id"
                  @click="toggleTag(tag.id)"
                  class="text-xs px-2 py-1 rounded-full border transition-all duration-200"
                  :class="selectedTagIds.includes(tag.id)
                    ? 'border-accent bg-accent text-text-inverse'
                    : 'border-accent/30 bg-accent-subtle text-accent hover:border-accent hover:bg-accent hover:text-text-inverse'"
                >
                  #{{ tag.name }}
                </button>
              </div>
            </div>

            <div class="relative mb-2">
              <Search :size="14" class="absolute left-2.5 top-1/2 -translate-y-1/2 text-text-muted" />
              <input
                v-model="tagSearchQuery"
                type="text"
                placeholder="搜索标签..."
                class="input-base pl-8 text-xs"
              />
            </div>

            <div class="flex flex-wrap gap-1.5 mb-3 max-h-32 overflow-y-auto">
              <button
                v-for="tag in filteredTags"
                :key="tag.id"
                @click="toggleTag(tag.id)"
                class="text-xs px-2.5 py-1 rounded-full border transition-all duration-200"
                :class="selectedTagIds.includes(tag.id)
                  ? 'border-accent bg-accent text-text-inverse'
                  : 'border-border text-text-muted hover:border-accent hover:text-accent'"
              >
                #{{ tag.name }}
              </button>
              <span v-if="filteredTags.length === 0" class="text-xs text-text-muted">
                {{ tagSearchQuery ? '没有匹配的标签' : '暂无标签' }}
              </span>
            </div>

            <div class="border-t border-border pt-3">
              <p class="text-xs text-text-muted mb-2">创建新标签</p>
              <div class="flex gap-2">
                <input
                  v-model="newTagName"
                  type="text"
                  placeholder="标签名称"
                  class="input-base text-xs flex-1"
                  @keydown.enter="handleCreateTag"
                />
                <input
                  v-model="newTagSlug"
                  type="text"
                  placeholder="别名(可选)"
                  class="input-base text-xs w-24"
                  @keydown.enter="handleCreateTag"
                />
                <button
                  @click="handleCreateTag"
                  :disabled="creatingTag || !newTagName.trim()"
                  class="p-2 rounded-lg border border-border text-text-muted hover:bg-accent-subtle hover:text-accent hover:border-accent transition-all duration-200 disabled:opacity-50 disabled:cursor-not-allowed"
                  title="创建标签"
                >
                  <Loader2 v-if="creatingTag" :size="14" class="animate-spin" />
                  <Plus v-else :size="14" />
                </button>
              </div>
            </div>
          </div>

          <div>
            <label class="block text-sm font-medium text-text-primary mb-1.5">
              合集
            </label>
            <div ref="collectionDropdownRef" class="relative">
              <button
                type="button"
                @click="toggleCollectionDropdown"
                class="input-base flex items-center justify-between cursor-pointer text-left"
                :class="{ 'ring-2 ring-accent/20 border-accent': collectionDropdownOpen }"
              >
                <span class="flex items-center gap-1.5 min-w-0">
                  <Library :size="14" class="text-accent shrink-0" />
                  <span v-if="selectedCollectionTitle" class="text-text-primary truncate">{{ selectedCollectionTitle }}</span>
                  <span v-else class="text-text-muted">未选择合集</span>
                </span>
                <ChevronDown
                  :size="14"
                  class="text-text-muted shrink-0 transition-transform duration-200"
                  :class="{ 'rotate-180': collectionDropdownOpen }"
                />
              </button>

              <transition
                enter-active-class="transition duration-150 ease-out"
                enter-from-class="opacity-0 -translate-y-1"
                enter-to-class="opacity-100 translate-y-0"
                leave-active-class="transition duration-100 ease-in"
                leave-from-class="opacity-100 translate-y-0"
                leave-to-class="opacity-0 -translate-y-1"
              >
                <div
                  v-if="collectionDropdownOpen"
                  class="absolute z-30 left-0 right-0 mt-1 rounded-lg border border-border bg-bg-elevated shadow-lg overflow-hidden max-h-60 overflow-y-auto scrollbar-thin"
                >
                  <button
                    type="button"
                    @click="selectCollection('')"
                    class="w-full flex items-center justify-between px-3 py-2 text-sm text-text-muted hover:bg-accent-subtle transition-colors duration-150"
                    :class="{ 'bg-accent-subtle text-accent': !selectedCollectionId }"
                  >
                    <span>未选择合集</span>
                    <Check v-if="!selectedCollectionId" :size="14" />
                  </button>
                  <div v-if="collections.length > 0" class="border-t border-border-subtle">
                    <button
                      v-for="col in collections"
                      :key="col.id"
                      type="button"
                      @click="selectCollection(col.id)"
                      class="w-full flex items-center justify-between gap-2 px-3 py-2 text-sm hover:bg-accent-subtle transition-colors duration-150"
                      :class="selectedCollectionId === col.id ? 'text-accent bg-accent-subtle' : 'text-text-primary'"
                    >
                      <span class="flex items-center gap-1.5 min-w-0">
                        <Library :size="13" class="shrink-0 opacity-60" />
                        <span class="truncate">{{ col.title }}</span>
                      </span>
                      <span class="flex items-center gap-1.5 shrink-0">
                        <span class="text-xs text-text-muted">{{ col.articleCount ?? 0 }} 篇</span>
                        <Check v-if="selectedCollectionId === col.id" :size="14" />
                      </span>
                    </button>
                  </div>
                  <div v-else class="px-3 py-3 text-xs text-text-muted text-center border-t border-border-subtle">
                    暂无合集，请先创建合集
                  </div>
                </div>
              </transition>
            </div>
          </div>

          <div>
            <label class="block text-sm font-medium text-text-primary mb-1.5">
              可见性
            </label>
            <div class="flex gap-2">
              <button
                type="button"
                @click="visibility = 0"
                class="flex-1 flex items-center justify-center gap-1.5 px-3 py-2 rounded-lg text-sm border transition-all duration-200"
                :class="visibility === 0
                  ? 'border-accent bg-accent text-text-inverse'
                  : 'border-border text-text-muted hover:border-accent hover:text-accent'"
              >
                <Globe :size="14" />
                公开
              </button>
              <button
                type="button"
                @click="visibility = 1"
                class="flex-1 flex items-center justify-center gap-1.5 px-3 py-2 rounded-lg text-sm border transition-all duration-200"
                :class="visibility === 1
                  ? 'border-accent bg-accent text-text-inverse'
                  : 'border-border text-text-muted hover:border-accent hover:text-accent'"
              >
                <Lock :size="14" />
                仅自己可见
              </button>
            </div>
          </div>

          <div>
            <label class="block text-sm font-medium text-text-primary mb-1.5">
              封面图片
            </label>
            <div v-if="coverImage" class="relative group mb-2">
              <img :src="coverImage" alt="封面预览" class="w-full aspect-[2/1] object-cover rounded-lg border border-border" />
              <button @click="removeCover" class="absolute top-2 right-2 p-1 rounded-full bg-black/60 text-white opacity-0 group-hover:opacity-100 transition-opacity" title="移除封面">
                <X :size="14" />
              </button>
            </div>
            <div class="flex gap-2 mb-2">
              <label class="btn-secondary cursor-pointer text-xs flex items-center gap-1.5">
                <Loader2 v-if="coverUploading" :size="12" class="animate-spin" />
                <span v-else>📁</span>
                {{ coverUploading ? '上传中...' : '本地上传' }}
                <input type="file" accept="image/*" class="hidden" @change="handleCoverUpload" :disabled="coverUploading" />
              </label>
            </div>
            <input
              v-model="coverImage"
              type="text"
              placeholder="或输入图片URL"
              class="input-base"
            />
          </div>

          <div class="p-4 rounded-lg bg-bg-code">
            <p class="text-xs text-text-muted leading-relaxed mb-2">
              图片上传：支持<strong>直接粘贴</strong>（截图/复制的图片）、<strong>拖拽图片</strong>到编辑区，或点击工具栏「图片」按钮选择文件（≤5MB），上传后自动插入图片语法。
            </p>
            <p class="text-xs text-text-muted leading-relaxed">
              支持 Markdown 语法、代码高亮、数学公式与标注；可在编辑 / 分屏 / 预览之间切换。
            </p>
          </div>
        </div>
      </transition>
    </div>
  </div>
</template>

<style scoped>
.slide-enter-active,
.slide-leave-active {
  transition: all 0.3s var(--ease-out);
}

.slide-enter-from,
.slide-leave-to {
  opacity: 0;
  transform: translateX(20px);
}
</style>
