<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { useHead } from '@unhead/vue'
import { Globe, User, Link2, FileText, MessageSquare, Loader2, BadgeCheck } from 'lucide-vue-next'
import { PageHeader } from '@/components/ui'
import { useSiteStore } from '@/stores/site'
import { updateSiteConfig, type SiteConfigUpdate } from '@/api/site'
import { useToast } from '@/composables/useToast'
import type { SiteConfig } from '@/types'

useHead(() => ({
  title: '站点设置',
}))

const siteStore = useSiteStore()
const { success, error: toastError } = useToast()

const form = reactive({
  title: '',
  subtitle: '',
  description: '',
  author: '',
  authorTagline: '',
  authorBio: '',
  techStack: '',
  avatar: '',
  url: '',
  github: '',
  rss: '',
  aboutContent: '',
  commentEnabled: true,
})

const original = ref({ ...form })
const pageLoading = ref(true)
const saving = ref(false)
const saveError = ref('')
const saveSuccess = ref('')

const isDirty = computed(() =>
  (Object.keys(form) as (keyof typeof form)[]).some(k => form[k] !== original.value[k]),
)

function fillForm(site: SiteConfig) {
  form.title = site.title || ''
  form.subtitle = site.subtitle || ''
  form.description = site.description || ''
  form.author = site.author || ''
  form.authorTagline = site.authorTagline || ''
  form.authorBio = site.authorBio || ''
  form.techStack = site.techStack || ''
  form.avatar = site.avatar || ''
  form.url = site.url || ''
  form.github = site.github || ''
  form.rss = site.rss || ''
  form.aboutContent = site.aboutContent || ''
  form.commentEnabled = site.commentEnabled !== false
  original.value = { ...form }
}

onMounted(async () => {
  try {
    await siteStore.fetchSite()
    fillForm(siteStore.site)
  } finally {
    pageLoading.value = false
  }
})

async function handleSave() {
  saveError.value = ''
  saveSuccess.value = ''
  if (!form.title.trim()) {
    saveError.value = '站点标题不能为空'
    return
  }
  saving.value = true
  try {
    const payload: SiteConfigUpdate = {
      title: form.title,
      subtitle: form.subtitle,
      description: form.description,
      author: form.author,
      authorTagline: form.authorTagline,
      authorBio: form.authorBio,
      techStack: form.techStack,
      avatar: form.avatar,
      url: form.url,
      github: form.github,
      rss: form.rss,
      aboutContent: form.aboutContent,
      commentEnabled: form.commentEnabled,
    }
    const updated = await updateSiteConfig(payload)
    siteStore.site = updated
    fillForm(updated)
    saveSuccess.value = '站点配置已保存并即时生效'
    success('站点配置已更新')
    setTimeout(() => { saveSuccess.value = '' }, 3000)
  } catch (e) {
    const msg = e instanceof Error ? e.message : '保存失败，请重试'
    saveError.value = msg
    toastError(msg)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="flex flex-col gap-5 min-w-0 max-w-4xl">
    <PageHeader title="站点设置" subtitle="站点名称、作者信息与关于页内容，保存后前台即时生效" />

    <div v-if="pageLoading" class="flex justify-center py-16">
      <Loader2 :size="24" class="animate-spin text-text-muted" />
    </div>

    <form v-else class="flex flex-col gap-5" @submit.prevent="handleSave">
      <!-- 基本信息 -->
      <section class="card-solid p-6">
        <h2 class="text-lg font-medium text-text-primary mb-6 flex items-center gap-2">
          <Globe :size="20" class="text-accent" />
          基本信息
        </h2>
        <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div>
            <label class="block text-sm font-medium text-text-secondary mb-1.5">站点标题 *</label>
            <input v-model="form.title" type="text" class="input-base" placeholder="Somehow's Blog" />
            <p class="mt-1 text-xs text-text-muted">显示在页头、浏览器标题后缀、og:site_name</p>
          </div>
          <div>
            <label class="block text-sm font-medium text-text-secondary mb-1.5">副标题</label>
            <input v-model="form.subtitle" type="text" class="input-base" placeholder="可选" />
          </div>
          <div class="md:col-span-2">
            <label class="block text-sm font-medium text-text-secondary mb-1.5">站点简介</label>
            <input v-model="form.description" type="text" class="input-base" placeholder="记录学习与思考的个人博客" />
            <p class="mt-1 text-xs text-text-muted">用于 meta description / og:description / 关于页副标题，建议 160 字以内</p>
          </div>
        </div>
      </section>

      <!-- 作者信息 -->
      <section class="card-solid p-6">
        <h2 class="text-lg font-medium text-text-primary mb-6 flex items-center gap-2">
          <User :size="20" class="text-accent" />
          作者信息
        </h2>
        <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div>
            <label class="block text-sm font-medium text-text-secondary mb-1.5">作者署名</label>
            <input v-model="form.author" type="text" class="input-base" placeholder="somehow" />
          </div>
          <div>
            <label class="block text-sm font-medium text-text-secondary mb-1.5">一句话签名</label>
            <input v-model="form.authorTagline" type="text" class="input-base" placeholder="代码与生活，都值得记录。" />
          </div>
          <div class="md:col-span-2">
            <label class="block text-sm font-medium text-text-secondary mb-1.5">作者简介</label>
            <textarea v-model="form.authorBio" rows="2" class="input-base resize-none" placeholder="Java / Vue / RAG 爱好者，写代码，也记录生活。" />
          </div>
          <div class="md:col-span-2">
            <label class="block text-sm font-medium text-text-secondary mb-1.5">技术栈</label>
            <input v-model="form.techStack" type="text" class="input-base" placeholder="Java · Spring Boot · Vue 3 · TypeScript" />
            <p class="mt-1 text-xs text-text-muted">关于页作者卡展示，如「Java · Spring Boot · Vue 3」</p>
          </div>
          <div class="md:col-span-2">
            <label class="block text-sm font-medium text-text-secondary mb-1.5">头像 URL</label>
            <input v-model="form.avatar" type="text" class="input-base" placeholder="https://... 或 /uploads/images/..." />
            <p class="mt-1 text-xs text-text-muted">留空则显示署名首字母占位</p>
          </div>
        </div>
      </section>

      <!-- 站点链接 -->
      <section class="card-solid p-6">
        <h2 class="text-lg font-medium text-text-primary mb-6 flex items-center gap-2">
          <Link2 :size="20" class="text-accent" />
          站点链接
        </h2>
        <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div>
            <label class="block text-sm font-medium text-text-secondary mb-1.5">站点地址</label>
            <input v-model="form.url" type="text" class="input-base" placeholder="https://somehow007.top" />
            <p class="mt-1 text-xs text-text-muted">canonical / og:url / JSON-LD 使用；留空则取当前域名</p>
          </div>
          <div>
            <label class="block text-sm font-medium text-text-secondary mb-1.5">GitHub</label>
            <input v-model="form.github" type="text" class="input-base" placeholder="https://github.com/Somehow007" />
          </div>
          <div class="md:col-span-2">
            <label class="block text-sm font-medium text-text-secondary mb-1.5">RSS 地址</label>
            <input v-model="form.rss" type="text" class="input-base" placeholder="可选" />
          </div>
        </div>
      </section>

      <!-- 关于页长文 -->
      <section class="card-solid p-6">
        <h2 class="text-lg font-medium text-text-primary mb-2 flex items-center gap-2">
          <FileText :size="20" class="text-accent" />
          关于页长文
        </h2>
        <p class="text-xs text-text-muted mb-4">Markdown 格式，渲染在关于页作者卡下方；支持多行（env 配置只能放单行，多行请走这里）</p>
        <textarea
          v-model="form.aboutContent"
          rows="8"
          class="input-base font-mono text-[13px] resize-y"
          placeholder="你好，我是 somehow……"
        />
      </section>

      <!-- 评论开关 -->
      <section class="card-solid p-6">
        <h2 class="text-lg font-medium text-text-primary mb-4 flex items-center gap-2">
          <MessageSquare :size="20" class="text-accent" />
          评论
        </h2>
        <label class="flex items-center gap-2.5 cursor-pointer">
          <input v-model="form.commentEnabled" type="checkbox" class="w-4 h-4 rounded border-border accent-accent" />
          <span class="text-sm text-text-secondary">开启文章评论（关闭后文章页不再展示评论区）</span>
        </label>
      </section>

      <div v-if="saveSuccess" class="text-sm text-success px-3 py-2 rounded-lg bg-success-subtle flex items-center gap-2">
        <BadgeCheck :size="16" />
        {{ saveSuccess }}
      </div>
      <div v-if="saveError" class="text-sm text-danger px-3 py-2 rounded-lg bg-danger-subtle">
        {{ saveError }}
      </div>

      <div class="flex items-center gap-3">
        <button type="submit" :disabled="saving || !isDirty" class="btn-primary">
          <Loader2 v-if="saving" :size="14" class="animate-spin" />
          {{ saving ? '保存中...' : '保存配置' }}
        </button>
        <span v-if="!isDirty && !saving" class="text-xs text-text-muted">没有未保存的更改</span>
      </div>
    </form>
  </div>
</template>
