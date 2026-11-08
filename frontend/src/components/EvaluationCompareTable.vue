<template>
  <el-card shadow="never" class="compare-card">
    <template #header>
      <div class="card-title">
        <span class="title-text">{{ title }}</span>
        <el-tag size="small" type="info">样本数 {{ sampleTotal }}</el-tag>
      </div>
    </template>

    <p v-if="sampleTotal === 0" class="hint-text">该类别暂无有效样本，无法比较。</p>
    <p v-else-if="sampleTotal < LOW_SAMPLE_THRESHOLD" class="hint-text">
      样本较少，分数差几个百分点不宜当结论
    </p>

    <el-table :data="rows" border size="small" style="width: 100%">
      <el-table-column prop="label" label="检索策略" min-width="110" />
      <el-table-column :label="`Recall@${recallK}`" min-width="160" align="center">
        <template #default="{ row }">
          <span :class="['metric', { best: row.recallBest }]">
            <template v-if="row.recallBest"><span aria-hidden="true">★ </span><span class="sr-only">最优 </span></template>{{ row.recallText }}
          </span>
          <el-tag v-if="row.failedCount > 0" size="small" type="danger" class="failed-tag">
            {{ row.failedCount }} 个失败已排除
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="MRR" min-width="160" align="center">
        <template #default="{ row }">
          <span :class="['metric', { best: row.mrrBest }]">
            <template v-if="row.mrrBest"><span aria-hidden="true">★ </span><span class="sr-only">最优 </span></template>{{ row.mrrText }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="样本数" width="100" align="center">
        <template #default="{ row }">{{ row.sampleCount > 0 ? row.sampleCount : '—' }}</template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script setup>
import { computed } from 'vue'
import {
  STRATEGIES,
  LOW_SAMPLE_THRESHOLD,
  bestFlags,
  categorySampleCount,
  formatMetric,
  metricOf
} from '@/utils/evaluation'

const props = defineProps({
  title: { type: String, required: true },
  // { [strategy]: {sampleCount, failedCount, recallAtK|null, mrr|null} }
  stats: { type: Object, default: () => ({}) },
  recallK: { type: Number, default: 5 }
})

const sampleTotal = computed(() => categorySampleCount(props.stats))

const rows = computed(() => {
  const recalls = STRATEGIES.map(s => metricOf(props.stats?.[s.key], 'recallAtK'))
  const mrrs = STRATEGIES.map(s => metricOf(props.stats?.[s.key], 'mrr'))
  const recallBest = bestFlags(recalls)
  const mrrBest = bestFlags(mrrs)
  return STRATEGIES.map((s, i) => {
    const st = props.stats?.[s.key] || {}
    return {
      key: s.key,
      label: s.label,
      sampleCount: st.sampleCount || 0,
      failedCount: st.failedCount || 0,
      recallText: formatMetric(recalls[i]),
      mrrText: formatMetric(mrrs[i]),
      recallBest: recallBest[i],
      mrrBest: mrrBest[i]
    }
  })
})
</script>

<style scoped>
.compare-card {
  height: 100%;
}

.card-title {
  display: flex;
  align-items: center;
  gap: 10px;
}

.title-text {
  font-weight: 600;
  color: #303133;
}

.hint-text {
  margin: 0 0 10px;
  font-size: 13px;
  color: #909399;
}

.metric {
  color: #606266;
}

.metric.best {
  font-weight: 700;
  color: #409eff;
}

.failed-tag {
  margin-left: 6px;
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
}
</style>
