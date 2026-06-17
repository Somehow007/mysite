import { ref } from 'vue'
import {
  getCollections,
  getCollectionById,
  createCollection,
  updateCollection,
  deleteCollection,
  addArticleToCollection,
  removeArticleFromCollection,
  batchAddArticles,
  updateArticleSort,
  getArticleNavigation,
} from '@/api/collection'
import type { Collection, CollectionDetail, ArticleNavInfo, CreateCollectionRequest, UpdateCollectionRequest } from '@/types'

/**
 * 合集相关逻辑的可复用 composable
 */
export function useCollection() {
  const loading = ref(false)
  const error = ref('')

  async function fetchCollections(params?: {
    page?: number
    size?: number
    keyword?: string
    authorId?: string
  }) {
    loading.value = true
    error.value = ''
    try {
      return await getCollections(params)
    } catch (e) {
      error.value = e instanceof Error ? e.message : '获取合集列表失败'
      throw e
    } finally {
      loading.value = false
    }
  }

  async function fetchCollectionDetail(id: string, current = 1, size = 10) {
    loading.value = true
    error.value = ''
    try {
      return await getCollectionById(id, current, size)
    } catch (e) {
      error.value = e instanceof Error ? e.message : '获取合集详情失败'
      throw e
    } finally {
      loading.value = false
    }
  }

  async function create(data: CreateCollectionRequest) {
    error.value = ''
    try {
      return await createCollection(data)
    } catch (e) {
      error.value = e instanceof Error ? e.message : '创建合集失败'
      throw e
    }
  }

  async function update(id: string, data: UpdateCollectionRequest) {
    error.value = ''
    try {
      await updateCollection(id, data)
    } catch (e) {
      error.value = e instanceof Error ? e.message : '更新合集失败'
      throw e
    }
  }

  async function remove(id: string) {
    error.value = ''
    try {
      await deleteCollection(id)
    } catch (e) {
      error.value = e instanceof Error ? e.message : '删除合集失败'
      throw e
    }
  }

  async function addArticle(collectionId: string, articleId: string) {
    error.value = ''
    try {
      await addArticleToCollection(collectionId, articleId)
    } catch (e) {
      error.value = e instanceof Error ? e.message : '添加文章到合集失败'
      throw e
    }
  }

  async function removeArticle(collectionId: string, articleId: string) {
    error.value = ''
    try {
      await removeArticleFromCollection(collectionId, articleId)
    } catch (e) {
      error.value = e instanceof Error ? e.message : '从合集移除文章失败'
      throw e
    }
  }

  async function batchAdd(collectionId: string, articleIds: string[]) {
    error.value = ''
    try {
      await batchAddArticles(collectionId, articleIds)
    } catch (e) {
      error.value = e instanceof Error ? e.message : '批量添加文章失败'
      throw e
    }
  }

  async function sort(collectionId: string, articleIds: string[]) {
    error.value = ''
    try {
      await updateArticleSort(collectionId, articleIds)
    } catch (e) {
      error.value = e instanceof Error ? e.message : '更新排序失败'
      throw e
    }
  }

  async function fetchNavigation(articleId: string): Promise<ArticleNavInfo | null> {
    error.value = ''
    try {
      return await getArticleNavigation(articleId)
    } catch {
      return null
    }
  }

  return {
    loading,
    error,
    fetchCollections,
    fetchCollectionDetail,
    create,
    update,
    remove,
    addArticle,
    removeArticle,
    batchAdd,
    sort,
    fetchNavigation,
  }
}
