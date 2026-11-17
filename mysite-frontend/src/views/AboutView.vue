<script setup lang="ts">
import { computed } from 'vue'
import { useSeo } from '@/composables/useSeo'
import { useSiteStore } from '@/stores/site'
import ArticleContent from '@/components/article/ArticleContent.vue'

const siteStore = useSiteStore()

useSeo({
  title: '关于',
  description: () => {
    const site = siteStore.site
    const parts = [site.description, site.authorBio].filter(Boolean)
    return parts.length ? parts.join(' — ') : `关于${site.title || '本站'}`
  },
  image: () => siteStore.site.avatar,
})

const authorInitial = computed(() => siteStore.site.author?.charAt(0) || '·')
// 长文走 site.aboutContent（markdown）；未配置时整块不渲染，作者卡已含短简介
const aboutMarkdown = computed(() => siteStore.site.aboutContent?.trim() ?? '')
const techStack = computed(() => siteStore.site.techStack?.trim() ?? '')
const hasCardFooter = computed(() =>
  !!(siteStore.site.github || siteStore.site.rss || techStack.value),
)
</script>

<template>
  <div class="mx-auto w-full max-w-[720px]">
    <section class="border-b-[3px] border-b-border-strong border-double pb-6 pt-2 text-center">
      <p class="text-xs uppercase tracking-[0.14em] text-text-muted">
        About
      </p>
      <h1 class="mt-2 font-serif text-[40px] font-black leading-[1.1] tracking-tight text-text-primary lg:text-[56px]">
        关于
      </h1>
      <p
        v-if="siteStore.site.description"
        class="mx-auto mt-3 max-w-xl font-serif text-[15px] leading-relaxed text-text-secondary lg:text-[17px]"
      >
        {{ siteStore.site.description }}
      </p>
    </section>

    <section
      class="paper-card paper-card--strong paper-hard-shadow mt-8 rounded-xl border border-border bg-bg-elevated p-6 sm:p-8"
    >
      <div class="flex flex-col gap-5 sm:flex-row sm:items-center">
        <img
          v-if="siteStore.site.avatar"
          :src="siteStore.site.avatar"
          :alt="siteStore.site.author"
          class="h-16 w-16 shrink-0 rounded-full object-cover"
        />
        <div
          v-else
          class="flex h-16 w-16 shrink-0 items-center justify-center rounded-full bg-gradient-to-br from-accent-subtle to-accent font-serif text-2xl font-bold text-accent"
          aria-hidden="true"
        >
          {{ authorInitial }}
        </div>
        <div class="min-w-0 flex-1">
          <h2 class="font-serif text-2xl font-bold text-text-primary">
            {{ siteStore.site.author }}
          </h2>
          <p
            v-if="siteStore.site.authorTagline"
            class="mt-1 text-sm text-text-muted"
          >
            {{ siteStore.site.authorTagline }}
          </p>
          <p
            v-if="siteStore.site.authorBio"
            class="mt-3 text-sm leading-relaxed text-text-secondary"
          >
            {{ siteStore.site.authorBio }}
          </p>
        </div>
      </div>

      <div
        v-if="hasCardFooter"
        class="mt-6 flex flex-wrap items-center gap-x-5 gap-y-2 border-t border-border pt-4 text-[13px]"
      >
        <a
          v-if="siteStore.site.github"
          :href="siteStore.site.github"
          target="_blank"
          rel="noopener noreferrer"
          class="text-accent transition-opacity hover:opacity-80"
        >
          GitHub
        </a>
        <a
          v-if="siteStore.site.rss"
          :href="siteStore.site.rss"
          class="text-accent transition-opacity hover:opacity-80"
        >
          RSS
        </a>
        <span
          v-if="techStack"
          class="text-text-muted"
        >
          技术栈 · {{ techStack }}
        </span>
      </div>
    </section>

    <div v-if="aboutMarkdown" class="mt-10">
      <ArticleContent :content="aboutMarkdown" />
    </div>
  </div>
</template>
