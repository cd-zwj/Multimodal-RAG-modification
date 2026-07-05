<template>
  <aside
    v-if="citations.length > 0 || retrievalDebug"
    class="hidden xl:flex flex-col w-80 bg-surface-container-lowest border-l border-outline-variant flex-shrink-0 h-full overflow-hidden shadow-sm min-h-0"
  >
    <div class="p-md border-b border-outline-variant flex items-center justify-between bg-surface-bright">
      <h3 class="font-headline-sm text-[18px] leading-[24px] font-semibold text-on-surface flex items-center gap-sm">
        <span class="material-symbols-outlined text-primary" style="font-variation-settings: 'FILL' 1;">source</span>
        来源与诊断
      </h3>
      <button
        @click="$emit('clear')"
        class="text-on-surface-variant hover:bg-surface-container-high p-1 rounded-md transition-colors cursor-pointer"
      >
        <span class="material-symbols-outlined text-[20px]">close</span>
      </button>
    </div>
    <div class="flex-1 min-h-0 overflow-y-auto p-md flex flex-col gap-md bg-background/50">
      <div v-if="retrievalDebug" class="bg-surface-container-lowest border border-outline-variant rounded-lg p-md shadow-sm">
        <div class="font-label-md text-label-md text-outline uppercase tracking-wider mb-sm">RAG 命中解释</div>
        <div class="space-y-xs font-body-sm text-body-sm text-on-surface-variant">
          <div><span class="text-outline">原始问题：</span>{{ retrievalDebug.originalQuery }}</div>
          <div v-if="retrievalDebug.rewrittenQuery && retrievalDebug.rewrittenQuery !== retrievalDebug.originalQuery">
            <span class="text-outline">改写后：</span>{{ retrievalDebug.rewrittenQuery }}
          </div>
          <div class="flex flex-wrap gap-xs">
            <span class="px-2 py-0.5 rounded bg-surface-container text-primary">模式 {{ retrievalDebug.retrievalMode || '-' }}</span>
            <span class="px-2 py-0.5 rounded bg-surface-container text-primary">候选 {{ retrievalDebug.candidateCount ?? 0 }}</span>
            <span class="px-2 py-0.5 rounded bg-surface-container text-primary">最终 {{ retrievalDebug.finalCount ?? 0 }}</span>
            <span class="px-2 py-0.5 rounded bg-surface-container text-primary">{{ retrievalDebug.durationMs ?? 0 }}ms</span>
          </div>
          <div v-if="retrievalDebug.topScore !== null && retrievalDebug.topScore !== undefined">
            <span class="text-outline">最高分：</span>{{ Number(retrievalDebug.topScore).toFixed(4) }}
          </div>
          <div v-if="retrievalDebug.noHitReason" class="text-secondary">
            {{ retrievalDebug.noHitReason }}
          </div>
          <div v-if="retrievalDebug.subQueries?.length" class="space-y-xs">
            <div class="text-outline">子查询</div>
            <div v-for="query in retrievalDebug.subQueries" :key="query" class="rounded bg-surface-container px-2 py-1 break-words">
              {{ query }}
            </div>
          </div>
        </div>
      </div>

      <div
        v-for="(cite, i) in citations"
        :key="i"
        @click="$emit('select', cite, i)"
        class="bg-surface-container-lowest border rounded-lg p-md shadow-sm hover:border-primary transition-all duration-200 cursor-pointer"
        :class="selectedIndex === i ? 'border-primary' : 'border-outline-variant'"
      >
        <div class="flex items-start justify-between mb-sm gap-xs">
          <div class="flex items-center gap-xs text-primary font-label-md text-label-md min-w-0">
            <span class="material-symbols-outlined text-[16px] flex-shrink-0">description</span>
            <span class="truncate font-semibold">{{ cite.sourceName }}</span>
          </div>
          <span class="bg-surface-container text-primary px-2 py-0.5 rounded-full font-label-md text-label-md flex-shrink-0">
            {{ cite.label }}
          </span>
        </div>
        <p
          v-if="formatCitationPath(cite)"
          class="font-label-md text-label-md text-outline mb-sm break-words"
        >
          {{ formatCitationPath(cite) }}
        </p>
        <p class="font-body-sm text-body-sm text-on-surface-variant line-clamp-4 leading-relaxed">
          {{ cite.text }}
        </p>
        <div class="mt-sm pt-sm border-t border-surface-variant flex items-center justify-between text-outline">
          <span class="font-label-md text-label-md flex items-center gap-xs">
            <span class="material-symbols-outlined text-[14px]">check_circle</span>
            匹配度: {{ cite.score }}%
          </span>
          <span class="font-label-md text-label-md text-primary hover:underline">定位片段</span>
        </div>
      </div>
    </div>
  </aside>
</template>

<script setup>
defineProps({
  citations: {
    type: Array,
    default: () => []
  },
  retrievalDebug: {
    type: Object,
    default: null
  },
  selectedIndex: {
    type: Number,
    default: -1
  }
})

defineEmits(['clear', 'select'])

const formatCitationPath = (cite) => {
  const segments = []
  const docName = cite.docTitle || cite.sourceName
  if (docName) segments.push(docName)
  if (cite.sectionTitle) segments.push(cite.sectionTitle)
  if (cite.chunkIndex) segments.push(`分段 ${cite.chunkIndex}`)
  return segments.join(' > ')
}
</script>
