<template>
  <div class="create-post">
    <div class="inner">
      <header class="post-header">
        <h1 class="post-title">{{ isEditMode ? '编辑文章' : '写文章' }}</h1>
      </header>

      <main class="content" role="main">
        <form @submit.prevent="handleSubmit" class="post-form">
          <div class="form-group">
            <label for="title" class="form-label">标题 *</label>
            <input
              id="title"
              v-model="formData.title"
              type="text"
              class="form-input"
              placeholder="输入文章标题"
              required
            />
          </div>

          <div class="form-group">
            <label for="summary" class="form-label">摘要</label>
            <textarea
              id="summary"
              v-model="formData.summary"
              class="form-textarea"
              rows="3"
              placeholder="输入文章摘要（可选）"
            ></textarea>
          </div>

          <div class="form-group">
            <label for="content" class="form-label">内容 *</label>
            <textarea
              id="content"
              v-model="formData.content"
              class="form-textarea form-textarea-large"
              rows="20"
              placeholder="输入文章内容（支持 Markdown）"
              required
            ></textarea>
            <p class="form-hint">支持 Markdown 格式</p>
          </div>

          <div class="form-actions">
            <button type="button" class="btn btn-secondary" @click="handleCancel">
              取消
            </button>
            <button type="submit" class="btn btn-primary" :disabled="saving || !isFormValid">
              {{ saving ? '发布中...' : isEditMode ? '更新文章' : '发布文章' }}
            </button>
          </div>
        </form>
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { createArticle, updateArticle, getArticleById } from '@/api/article'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const isEditMode = computed(() => {
  return route.name === 'EditPost' || (route.name === 'CreatePost' && !!route.params.id)
})

const formData = ref({
  title: '',
  content: '',
  summary: ''
})

const saving = ref(false)

const isFormValid = computed(() => {
  return formData.value.title.trim().length > 0 && formData.value.content.trim().length > 0
})

const loadPost = async (id: string) => {
  try {
    const response = await getArticleById(id)
    if (response && response.code === '0' && response.data) {
      formData.value = {
        title: response.data.title || '',
        content: response.data.content || '',
        summary: response.data.summary || ''
      }
    } else {
      router.push('/')
    }
  } catch (error) {
    console.error('加载文章失败:', error)
    router.push('/')
  }
}

const handleSubmit = async () => {
  if (!isFormValid.value) return

  saving.value = true
  try {
    if (isEditMode.value) {
      const id = route.params.id as string
      if (!id) {
        alert('无法获取文章标识')
        return
      }
      const response = await updateArticle({
        id: Number(id),
        title: formData.value.title,
        content: formData.value.content,
        summary: formData.value.summary
      })
      if (response && response.code === '0') {
        router.push(`/post/${id}`)
      } else {
        alert(response?.message || '更新失败')
      }
    } else {
      if (!userStore.user?.id) {
        alert('请先登录')
        router.push('/login')
        return
      }
      const response = await createArticle({
        title: formData.value.title,
        content: formData.value.content,
        summary: formData.value.summary
      })
      if (response && response.code === '0') {
        router.push('/')
      } else {
        alert(response?.message || '创建失败')
      }
    }
  } catch (error) {
    console.error('保存文章失败:', error)
    alert('保存文章失败，请重试')
  } finally {
    saving.value = false
  }
}

const handleCancel = () => {
  if (confirm('确定要离开吗？未保存的更改将丢失。')) {
    if (isEditMode.value) {
      const id = route.params.id as string
      if (id) {
        router.push(`/post/${id}`)
      } else {
        router.push('/')
      }
    } else {
      router.push('/')
    }
  }
}

onMounted(() => {
  if (isEditMode.value) {
    const id = route.params.id as string
    if (id) {
      loadPost(id)
    }
  }
})
</script>

<style scoped>
.create-post {
  min-height: 100vh;
  padding: 4rem 0;
}

.post-header {
  margin-bottom: 4rem;
}

.post-title {
  font-size: 4rem;
  line-height: 1.25em;
  font-weight: 700;
  color: var(--color-content-lead);
  margin: 0;
}

.post-form {
  max-width: 800px;
  margin: 0 auto;
}

.form-group {
  margin-bottom: 3rem;
}

.form-label {
  display: block;
  font-size: 1.6em;
  font-weight: 500;
  color: var(--color-content-main);
  margin-bottom: 1rem;
}

.form-input,
.form-textarea {
  width: 100%;
  padding: 1rem;
  font-size: 1.6em;
  line-height: 1.75em;
  color: var(--color-content-main);
  background: var(--color-background-main);
  border: 1px solid var(--color-background-contrast);
  border-radius: 4px;
  box-sizing: border-box;
  transition: all ease-out 0.1s;
}

.form-input:focus,
.form-textarea:focus {
  outline: none;
  border-color: var(--ghost-accent-color);
  box-shadow: inset 0 0 0 1px var(--ghost-accent-color);
}

.form-textarea {
  font-family: inherit;
  resize: vertical;
}

.form-textarea-large {
  font-family: monospace;
  font-size: 1.4em;
  line-height: 1.75em;
}

.form-hint {
  margin-top: 0.5rem;
  font-size: 1.4em;
  color: var(--color-content-secondary);
}

.form-actions {
  display: flex;
  gap: 1rem;
  justify-content: flex-end;
  margin-top: 4rem;
  padding-top: 3rem;
  border-top: 1px solid var(--color-background-contrast);
}

.btn {
  padding: 1rem 2rem;
  font-size: 1.6em;
  font-weight: 500;
  border: none;
  border-radius: 4rem;
  cursor: pointer;
  transition: all ease-out 0.1s;
  text-decoration: none;
  display: inline-block;
}

.btn-primary {
  background: var(--ghost-accent-color);
  color: #fff;
}

.btn-primary:hover:not(:disabled) {
  opacity: 0.92;
}

.btn-primary:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn-secondary {
  background: var(--color-background-secondary);
  color: var(--color-content-main);
  border: 1px solid var(--color-background-contrast);
}

.btn-secondary:hover:not(:disabled) {
  border-color: var(--ghost-accent-color);
  color: var(--ghost-accent-color);
}

@media only screen and (max-width: 640px) {
  .create-post {
    padding: 2rem 0;
  }

  .post-title {
    font-size: 3rem;
  }

  .form-actions {
    flex-direction: column;
  }

  .btn {
    width: 100%;
  }
}
</style>
