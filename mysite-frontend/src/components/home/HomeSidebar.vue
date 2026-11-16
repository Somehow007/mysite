<script setup lang="ts">
import { computed } from 'vue'
import { Lock } from 'lucide-vue-next'
import { useSiteStore } from '@/stores/site'
import { toRomanNumeral, collectionProgress, formatCollectionProgress } from '@/utils/format'
import type { Collection } from '@/types'

defineProps<{
  collections: Collection[]
}>()

const siteStore = useSiteStore()

const authorInitial = computed(() => siteStore.site.author?.charAt(0) || '·')
</script>

<template>
  <aside class="mt-12 min-w-0 lg:mt-0">
    <section v-if="collections.length">
      <div class="flex items-baseline justify-between gap-3 border-b-2 border-b-border-strong pb-2">
        <h3 class="text-[12px] font-semibold uppercase tracking-[0.16em] text-text-secondary">
          连载合集
        </h3>
        <RouterLink
          to="/collections"
          class="shrink-0 text-[13px] text-text-muted transition-colors hover:text-accent"
        >
          全部 →
        </RouterLink>
      </div>
      <div>
        <RouterLink
          v-for="(collection, i) in collections"
          :key="collection.id"
          :to="`/collection/${collection.id}`"
          class="group flex gap-3.5 border-b border-border py-4"
        >
          <span class="w-9 shrink-0 font-serif text-[30px] leading-none text-accent">
            {{ toRomanNumeral(i + 1) }}
          </span>
          <span class="min-w-0 flex-1">
            <span class="flex items-center gap-1.5 font-serif text-[17px] font-semibold leading-snug text-text-primary transition-colors duration-200 group-hover:text-accent">
              <span class="truncate">{{ collection.title }}</span>
              <Lock
                v-if="collection.visibility === 1"
                :size="11"
                class="shrink-0 text-warning"
              />
            </span>
            <span class="mt-1 block text-xs text-text-muted">
              {{ formatCollectionProgress(collection) }}
            </span>
            <span
              v-if="collectionProgress(collection).percent != null"
              class="mt-1.5 block h-[3px] w-full bg-border"
              aria-hidden="true"
            >
              <span
                class="block h-full bg-accent transition-all duration-300"
                :style="{ width: collectionProgress(collection).percent + '%' }"
              />
            </span>
          </span>
        </RouterLink>
      </div>
    </section>

    <div
      class="paper-card rounded-xl border border-border bg-bg-elevated p-5"
      :class="collections.length ? 'mt-7' : ''"
    >
      <div class="flex items-center gap-3">
        <img
          v-if="siteStore.site.avatar"
          :src="siteStore.site.avatar"
          :alt="siteStore.site.author"
          class="h-11 w-11 shrink-0 rounded-full object-cover"
        />
        <div
          v-else
          class="flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-gradient-to-br from-accent-subtle to-accent font-serif text-lg font-bold text-accent"
          aria-hidden="true"
        >
          {{ authorInitial }}
        </div>
        <div class="min-w-0">
          <p class="truncate font-serif text-base font-bold text-text-primary">
            {{ siteStore.site.author }}
          </p>
          <p v-if="siteStore.site.authorTagline" class="truncate text-xs text-text-muted">
            {{ siteStore.site.authorTagline }}
          </p>
        </div>
      </div>
      <p
        v-if="siteStore.site.authorBio"
        class="mt-3 line-clamp-4 text-[13px] leading-relaxed text-text-secondary"
      >
        {{ siteStore.site.authorBio }}
      </p>
      <div class="mt-3 flex flex-wrap gap-x-4 gap-y-1 text-xs">
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
        <RouterLink
          to="/about"
          class="text-accent transition-opacity hover:opacity-80"
        >
          关于我 →
        </RouterLink>
      </div>
    </div>
  </aside>
</template>
