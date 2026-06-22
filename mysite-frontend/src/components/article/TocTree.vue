<script setup lang="ts">
import { computed } from 'vue'
import { ChevronRight } from 'lucide-vue-next'
import type { TocNode } from '@/composables/useToc'
import CollapseTransition from '@/components/common/CollapseTransition.vue'

const props = defineProps<{
  nodes: TocNode[]
  activeId: string
  minLevel: number
  isGroupExpanded: (id: string) => boolean
  staggerDelay: boolean
  topLevelIndex?: number
  textMutedClass?: string
}>()

const inactiveClass = computed(() => props.textMutedClass ?? 'text-text-muted')

const emit = defineEmits<{
  navigate: [id: string]
  toggle: [id: string]
}>()
</script>

<template>
  <template v-for="(node, idx) in nodes" :key="node.id">
    <li :style="{ paddingLeft: `${(node.level - minLevel) * 12}px` }">
      <button
        :data-active="activeId === node.id"
        @click="emit('navigate', node.id)"
        class="group/toc flex items-center w-full text-left py-1.5 pl-3 transition-all duration-200 border-l-2 -ml-px text-[13px] leading-snug"
        :class="[
          activeId === node.id
            ? 'border-accent text-accent font-medium bg-accent-subtle/50'
            : `border-transparent ${inactiveClass} hover:text-text-secondary hover:border-border`,
        ]"
        :aria-current="activeId === node.id ? 'true' : undefined"
      >
        <span
          v-if="node.hasChildren"
          @click.stop="emit('toggle', node.id)"
          class="toc-toggle shrink-0"
          :class="isGroupExpanded(node.id) ? 'rotate-90' : ''"
          role="button"
          :aria-label="isGroupExpanded(node.id) ? '收起' : '展开'"
        >
          <ChevronRight :size="12" />
        </span>
        {{ node.text }}
      </button>
      <CollapseTransition
        v-if="node.hasChildren"
        :show="isGroupExpanded(node.id)"
        :delay="staggerDelay ? (topLevelIndex ?? idx) * 40 : undefined"
      >
        <ul>
          <TocTree
            :nodes="node.children"
            :active-id="activeId"
            :min-level="minLevel"
            :is-group-expanded="isGroupExpanded"
            :stagger-delay="staggerDelay"
            :top-level-index="topLevelIndex ?? idx"
            :text-muted-class="textMutedClass"
            @navigate="emit('navigate', $event)"
            @toggle="emit('toggle', $event)"
          />
        </ul>
      </CollapseTransition>
    </li>
  </template>
</template>

<style scoped>
.toc-toggle {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 20px;
  min-height: 20px;
  margin: -4px 2px -4px -4px;
  border-radius: 4px;
  color: currentColor;
  opacity: 0.5;
  cursor: pointer;
  transition:
    opacity 150ms ease,
    transform 200ms ease,
    background-color 150ms ease;
}

.toc-toggle:hover {
  opacity: 1;
  background-color: var(--border, #e5e7eb);
}
</style>
