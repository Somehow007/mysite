<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useChatLauncher } from '@/composables/useChatLauncher'
import { stashPendingAsk } from '@/composables/usePendingAsk'

const MAX_QUESTION_LENGTH = 500
const EXAMPLES = ['总结成 3 点', '这篇的核心结论是什么', '适合什么读者看']

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const launcher = useChatLauncher()

const question = ref('')
const inputRef = ref<HTMLInputElement | null>(null)

function submit(raw?: string) {
  const q = (raw ?? question.value).trim()
  if (!q || q.length > MAX_QUESTION_LENGTH) return
  if (!userStore.isLoggedIn) {
    // 未登录：暂存问题，登录回跳本页后由 BlogPost 自动打开面板并发送
    stashPendingAsk(q, route.fullPath)
    router.push({ name: 'login', query: { redirect: route.fullPath } })
    return
  }
  // 文章上下文由 ChatWidget 按 /post/:id 路由自动注入
  launcher.open({ question: q, send: true })
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
</script>

<template>
  <div class="ask-mini">
    <div class="ask-mini-title">✦ 问这篇文章</div>
    <p class="ask-mini-desc">读到一半有疑问？AI 会结合本文和站内其他文章回答。</p>
    <input
      ref="inputRef"
      v-model="question"
      type="text"
      maxlength="500"
      placeholder="例如：这篇的核心结论是什么？"
      class="ask-mini-input"
      @keydown.enter="handleEnter"
    />
    <div class="ask-mini-chips">
      <button
        v-for="ex in EXAMPLES"
        :key="ex"
        type="button"
        class="ask-mini-chip"
        @click="fillExample(ex)"
      >
        {{ ex }}
      </button>
    </div>
  </div>
</template>
