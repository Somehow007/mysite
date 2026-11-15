<script setup lang="ts">
import { computed } from 'vue'
import { useSiteStore } from '@/stores/site'
import { formatChineseDate } from '@/utils/date'

const props = defineProps<{
  /** 文章总数（分页 total），>0 时显示在日期行 */
  total?: number
}>()

const siteStore = useSiteStore()

const today = formatChineseDate(new Date(), true)

// 与 AppHeader logo 同规则：站名里的「·」分隔符着 accent 色
const titleSegments = computed(() => siteStore.site.title.split('·'))
const subtitle = computed(() => siteStore.site.subtitle || siteStore.site.description)
</script>

<template>
  <section class="border-b-[3px] border-b-border-strong border-double pb-6 pt-8 text-center lg:pb-8 lg:pt-12">
    <p class="text-xs uppercase tracking-[0.14em] text-text-muted">
      {{ today }}<template v-if="props.total && props.total > 0"> · 第 {{ props.total }} 篇</template>
    </p>
    <h1 class="mt-2.5 font-serif text-[44px] font-black leading-[1.05] tracking-tight text-text-primary lg:text-[76px]">
      <template v-for="(seg, i) in titleSegments" :key="i">
        <span v-if="i > 0" class="text-accent">·</span>{{ seg }}
      </template>
    </h1>
    <p
      v-if="subtitle"
      class="mx-auto mt-3 max-w-xl font-serif text-[15px] leading-relaxed text-text-secondary lg:text-[17px]"
    >
      {{ subtitle }}
    </p>
  </section>
</template>
