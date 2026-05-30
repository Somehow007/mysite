<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useHead } from '@unhead/vue'
import { useRouter } from 'vue-router'
import { FileText, Plus, Trash2, Edit, Eye } from 'lucide-vue-next'
import { getArticles, deleteArticle } from '@/api/article'
import { useUserStore } from '@/stores/user'
import { usePermission } from '@/composables/usePermission'
import { useToast } from '@/composables/useToast'
import type { ArticleListItem, Pagination } from '@/types'

useHead(() => ({
  title: '文章管理 - MySite',
}))

const router = useRouter()
const userStore = useUserStore()
const { isDeveloper, canModifyArticle } = usePermission()
const toast = useToast()

const articles = ref<ArticleListItem[]>([])
const pagination = ref<Pagination | null>(null)
const loading = ref(false)
const deleting = ref<string | null>(null)

async function fetchArticles(page = 1) {
  loading.value = true
  try {
    const res = await getArticles({ page, size: 20, published: null })
    if (isDeveloper.value) {
      articles.value = res.list
    } else {
      articles.value = res.list.filter(
        (a: ArticleListItem) => a.authorId === userStore.user?.id
      )
    }
    pagination.value = res.pagination
  } catch {
    articles.value = []
  } finally {
    loading.value = false
  }
}

async function handleDelete(id: string) {
  if (!confirm('确定要删除这篇文章吗？')) return
  deleting.value = id
  try {
    await deleteArticle(id)
    toast.success('文章已删除')
    await fetchArticles(pagination.value?.page || 1)
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : '删除失败'
    toast.error(msg)
  } finally {
    deleting.value = null
  }
}

function canModify(article: ArticleListItem): boolean {
  return canModifyArticle(article.authorId)
}

onMounted(() => {
  fetchArticles()
})
</script>

<template>
  <div>
    <div class="flex items-center justify-between mb-8">
      <h1 class="text-2xl font-semibold text-text-primary">
        文章管理
      </h1>
      <button
        @click="router.push('/dashboard/posts/new')"
        class="btn-primary"
      >
        <Plus :size="14" />
        写文章
      </button>
    </div>

    <div v-if="loading" class="py-16 text-center text-text-muted">
      加载中...
    </div>

    <div v-else-if="articles.length === 0" class="py-16 text-center">
      <FileText :size="48" class="mx-auto mb-4 text-text-muted" />
      <p class="text-text-muted mb-4">还没有文章，开始写第一篇吧</p>
      <button
        @click="router.push('/dashboard/posts/new')"
        class="btn-primary"
      >
        <Plus :size="14" />
        写文章
      </button>
    </div>

    <div v-else class="space-y-2">
      <div
        v-for="article in articles"
        :key="article.id"
        class="flex items-center justify-between p-4 rounded-xl glass glass-sm glass-hover hover:border-accent/30 transition-all duration-200"
      >
        <div class="flex-1 min-w-0">
          <h3 class="text-sm font-medium text-text-primary truncate">
            {{ article.title }}
          </h3>
          <div class="flex items-center gap-3 mt-1 text-xs text-text-muted">
            <span v-if="article.published === 0" class="px-1.5 py-0.5 rounded bg-amber-50 text-amber-600">
              草稿
            </span>
            <span v-if="article.categoryName" class="px-1.5 py-0.5 rounded bg-accent-subtle text-accent">
              {{ article.categoryName }}
            </span>
            <span class="inline-flex items-center gap-1">
              <Eye :size="10" />
              {{ article.viewCount }}
            </span>
            <span>{{ article.updateTime ? new Date(article.updateTime).toLocaleDateString('zh-CN') : '' }}</span>
          </div>
        </div>

        <div class="flex items-center gap-1 ml-4">
          <button
            @click="router.push(`/post/${article.id}`)"
            class="p-2 rounded-lg text-text-muted hover:bg-accent-subtle hover:text-accent transition-all duration-200"
            title="查看"
          >
            <Eye :size="14" />
          </button>
          <button
            v-if="canModify(article)"
            @click="router.push(`/dashboard/posts/${article.id}/edit`)"
            class="p-2 rounded-lg text-text-muted hover:bg-accent-subtle hover:text-accent transition-all duration-200"
            title="编辑"
          >
            <Edit :size="14" />
          </button>
          <button
            v-if="canModify(article)"
            @click="handleDelete(article.id)"
            :disabled="deleting === article.id"
            class="p-2 rounded-lg text-text-muted hover:bg-red-50 hover:text-red-500 transition-all duration-200 disabled:opacity-50"
            title="删除"
          >
            <Trash2 :size="14" />
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
