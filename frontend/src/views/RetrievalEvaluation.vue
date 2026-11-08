<template>
  <div class="evaluation-container">
    <!-- 顶部操作区 -->
    <el-card shadow="never">
      <div class="toolbar">
        <el-button type="primary" :loading="running" :disabled="running" @click="runEvaluation">
          <el-icon v-if="!running"><VideoPlay /></el-icon>
          {{ running ? '评估运行中…' : '运行评估' }}
        </el-button>
        <span class="last-run">
          上次运行时间：{{ lastRunTime || '本页尚未运行' }}
        </span>
      </div>
      <p class="note-text">
        运行评估会调用重排接口，按用例数计费；耗时可能从几十秒到几分钟，运行期间请勿重复点击或离开本页。
      </p>
    </el-card>

    <el-alert
      v-if="runError && !running"
      class="block-gap"
      type="error"
      show-icon
      :closable="false"
      title="评估运行失败"
      :description="runError"
    />

    <el-alert
      v-if="result && result.skippedCases > 0"
      class="block-gap"
      type="warning"
      show-icon
      :closable="false"
      :title="`有 ${result.skippedCases} 道题没有标注标准答案，已跳过，未计入任何平均值`"
    />

    <!-- 运行中 -->
    <el-card v-if="running" class="block-gap" shadow="never">
      <div v-loading="true" element-loading-text="评估运行中，请勿重复点击…" class="running-box" />
    </el-card>

    <!-- 空状态：从未运行 -->
    <el-card v-else-if="!result" class="block-gap" shadow="never">
      <el-empty description="还没有评估结果">
        <template #default>
          <p class="empty-guide">
            点击上方“运行评估”，系统会用评估集分别测试 纯 BM25、纯向量、混合(RRF)、混合+精排
            四种检索策略，并按 Recall@{{ recallK }} 与 MRR 对比。
          </p>
        </template>
      </el-empty>
    </el-card>

    <template v-else>
      <!-- 汇总对比 -->
      <div class="section-title">汇总对比</div>
      <p class="note-text">
        Recall@{{ recallK }}：标准答案在前 {{ recallK }} 条结果中被找回的比例；MRR：第一个正确结果排名倒数的平均值。
        两项均为越高越好，每列最优值以 ★ 加粗标出；运行失败的用例已从该策略的平均值中排除。
      </p>
      <el-row :gutter="16">
        <el-col v-for="t in summaryTables" :key="t.key" :xs="24" :lg="12" class="summary-col">
          <EvaluationCompareTable :title="t.title" :stats="t.stats" :recall-k="recallK" />
        </el-col>
      </el-row>

      <!-- 逐题明细 -->
      <div class="section-title">逐题明细</div>
      <el-card shadow="never">
        <div class="filter-bar">
          <el-select
            v-model="categoryFilter"
            placeholder="全部类别"
            clearable
            style="width: 200px"
            aria-label="按类别筛选"
          >
            <el-option v-for="c in CATEGORIES" :key="c.key" :label="c.label" :value="c.key" />
          </el-select>
          <el-switch v-model="onlyMiss" active-text="只看有策略未命中的题" />
          <span class="note-text">共 {{ filteredCases.length }} / {{ allCases.length }} 题</span>
        </div>

        <el-table :data="filteredCases" row-key="_key" border stripe style="width: 100%">
          <el-table-column type="expand">
            <template #default="{ row }">
              <div class="expand-box">
                <el-table :data="strategyRows(row)" border size="small" style="width: 100%">
                  <el-table-column prop="label" label="检索策略" width="110" />
                  <el-table-column label="是否成功" width="90" align="center">
                    <template #default="{ row: s }">
                      <el-tag :type="s.success ? 'success' : 'danger'" size="small">
                        {{ s.success ? '成功' : '失败' }}
                      </el-tag>
                    </template>
                  </el-table-column>
                  <el-table-column :label="`Recall@${recallK}`" width="100" align="center">
                    <template #default="{ row: s }">{{ s.success ? formatMetric(s.recallAtK) : '—' }}</template>
                  </el-table-column>
                  <el-table-column label="MRR" width="90" align="center">
                    <template #default="{ row: s }">{{ s.success ? formatMetric(s.mrr) : '—' }}</template>
                  </el-table-column>
                  <el-table-column label="第一次命中名次" width="130" align="center">
                    <template #default="{ row: s }">
                      <template v-if="!s.success">—</template>
                      <template v-else-if="s.firstHitRank == null">
                        <span class="miss-text">未命中</span>
                      </template>
                      <template v-else>第 {{ s.firstHitRank }} 名</template>
                    </template>
                  </el-table-column>
                  <el-table-column label="召回的 chunkId（带 ✓ 的为标准答案）" min-width="260">
                    <template #default="{ row: s }">
                      <div v-if="!s.success" class="error-text">
                        失败：{{ s.error || '未知错误' }}
                      </div>
                      <span v-else-if="s.retrievedChunkIds.length === 0" class="muted">无召回结果</span>
                      <template v-else>
                        <span
                          v-for="(id, i) in s.retrievedChunkIds"
                          :key="`${id}-${i}`"
                          :class="['chunk-id', { hit: isHit(row, id) }]"
                        >{{ isHit(row, id) ? '✓ ' : '' }}{{ id }}</span>
                      </template>
                    </template>
                  </el-table-column>
                </el-table>
                <div class="truth-line">
                  标准答案 chunkId：
                  <span v-if="row.groundTruth && row.groundTruth.length">{{ row.groundTruth.join('、') }}</span>
                  <span v-else class="muted">—</span>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="query" label="题目" min-width="280" show-overflow-tooltip />
          <el-table-column label="类别" width="150">
            <template #default="{ row }">
              <el-tag size="small">{{ categoryLabel(row.category) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="标准答案 chunk 数" width="150" align="center">
            <template #default="{ row }">{{ row.groundTruth ? row.groundTruth.length : 0 }}</template>
          </el-table-column>
          <el-table-column label="未命中" width="100" align="center">
            <template #default="{ row }">
              <el-tag v-if="hasMiss(row)" type="warning" size="small">有未命中</el-tag>
              <span v-else class="muted">—</span>
            </template>
          </el-table-column>
          <template #empty>
            <span>没有符合筛选条件的题目</span>
          </template>
        </el-table>
      </el-card>

      <!-- 失败汇总 -->
      <el-card v-if="failures.length > 0" class="block-gap" shadow="never">
        <template #header>
          <span class="title-text">运行失败记录（{{ failures.length }}）</span>
        </template>
        <el-table :data="failures" border size="small" style="width: 100%">
          <el-table-column prop="query" label="题目" min-width="240" show-overflow-tooltip />
          <el-table-column label="检索策略" width="120">
            <template #default="{ row }">{{ strategyLabel(row.strategy) }}</template>
          </el-table-column>
          <el-table-column prop="message" label="错误信息" min-width="260" show-overflow-tooltip />
        </el-table>
      </el-card>
    </template>

    <!-- 标注辅助 -->
    <el-collapse v-model="helperOpen" class="block-gap helper-collapse">
      <el-collapse-item title="标注辅助：按关键词查候选 chunk" name="helper">
        <el-alert
          type="info"
          show-icon
          :closable="false"
          title="标准答案必须由人判断，不能拿检索系统自己的结果当答案。这里只用来辅助查找 chunkId。"
        />
        <el-form :inline="true" class="helper-form" @submit.prevent="searchCandidates">
          <el-form-item label="关键词">
            <el-input
              v-model="candKeyword"
              placeholder="输入关键词，如报错信息片段"
              clearable
              style="width: 320px"
              @keyup.enter="searchCandidates"
            />
          </el-form-item>
          <el-form-item label="返回数量">
            <el-input-number v-model="candLimit" :min="1" :max="100" style="width: 120px" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="candLoading" @click="searchCandidates">
              <el-icon><Search /></el-icon>
              查询候选
            </el-button>
          </el-form-item>
        </el-form>

        <el-table
          v-if="candidates.length > 0"
          :data="candidates"
          v-loading="candLoading"
          border
          stripe
          style="width: 100%"
        >
          <el-table-column prop="chunkId" label="chunkId" width="110" />
          <el-table-column prop="docType" label="文档类型" width="110" />
          <el-table-column prop="sectionPath" label="章节路径" min-width="200" show-overflow-tooltip />
          <el-table-column label="内容（前 120 字）" min-width="320">
            <template #default="{ row }">{{ preview(row.text) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="110" align="center">
            <template #default="{ row }">
              <el-button size="small" link type="primary" @click="copyId(row.chunkId)">
                <el-icon><DocumentCopy /></el-icon>
                复制 id
              </el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-else-if="candSearched && !candLoading" description="没有找到匹配的候选 chunk" />
      </el-collapse-item>
    </el-collapse>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { VideoPlay, Search, DocumentCopy } from '@element-plus/icons-vue'
import { evaluationApi } from '@/api'
import EvaluationCompareTable from '@/components/EvaluationCompareTable.vue'
import {
  STRATEGIES,
  CATEGORIES,
  categoryLabel,
  strategyLabel,
  formatMetric,
  hasMiss
} from '@/utils/evaluation'

// ---------- 运行评估 ----------
const result = ref(null)
const running = ref(false)
const runError = ref('')
const lastRunTime = ref('')

const recallK = computed(() => result.value?.recallK || 5)

const runEvaluation = async () => {
  if (running.value) return
  running.value = true
  runError.value = ''
  try {
    // 不传 cases，使用服务端评估集；request 超时在 api 内放大到 300s
    const data = await evaluationApi.run()
    result.value = data
    lastRunTime.value = new Date().toLocaleString('zh-CN', { hour12: false })
  } catch (e) {
    // 错误提示已由 request 拦截器弹出；此处保留页面内错误，保留上一次的结果不动
    runError.value = e?.message || '请求失败'
  } finally {
    running.value = false
  }
}

// ---------- 汇总 ----------
const summaryTables = computed(() => {
  const summary = result.value?.summary || {}
  return [
    ...CATEGORIES.map(c => ({ key: c.key, title: c.label, stats: summary[c.key] || {} })),
    { key: 'ALL', title: '全部', stats: summary.ALL || {} }
  ]
})

// ---------- 逐题明细 ----------
const categoryFilter = ref('')
const onlyMiss = ref(false)

const allCases = computed(() =>
  (result.value?.cases || []).map((c, i) => ({ ...c, _key: `${i}-${c.query}` }))
)

const filteredCases = computed(() =>
  allCases.value.filter(c => {
    if (categoryFilter.value && c.category !== categoryFilter.value) return false
    if (onlyMiss.value && !hasMiss(c)) return false
    return true
  })
)

const failures = computed(() => result.value?.failures || [])

const strategyRows = (evalCase) =>
  STRATEGIES.map(s => {
    const r = evalCase.results?.[s.key]
    return {
      key: s.key,
      label: s.label,
      success: !!r && r.success,
      // 契约里没有该策略的结果时，当作失败展示，不伪造数据
      error: r ? r.error : '后端未返回该策略的结果',
      recallAtK: r?.recallAtK ?? null,
      mrr: r?.mrr ?? null,
      firstHitRank: r?.firstHitRank ?? null,
      retrievedChunkIds: r?.retrievedChunkIds || []
    }
  })

const isHit = (evalCase, id) => (evalCase.groundTruth || []).includes(id)

// ---------- 标注辅助 ----------
const helperOpen = ref([])
const candKeyword = ref('')
const candLimit = ref(20)
const candidates = ref([])
const candLoading = ref(false)
const candSearched = ref(false)

const preview = (text) => {
  if (!text) return '—'
  return text.length > 120 ? `${text.slice(0, 120)}…` : text
}

const searchCandidates = async () => {
  const keyword = candKeyword.value.trim()
  if (!keyword) {
    ElMessage.warning('请输入关键词')
    return
  }
  candLoading.value = true
  try {
    candidates.value = (await evaluationApi.getCandidates(keyword, candLimit.value)) || []
    candSearched.value = true
  } catch (e) {
    // 错误提示已由拦截器弹出；清空旧结果，避免误把上次结果当本次结果
    candidates.value = []
    candSearched.value = false
  } finally {
    candLoading.value = false
  }
}

const copyId = async (id) => {
  const text = String(id)
  try {
    if (navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(text)
    } else {
      const ta = document.createElement('textarea')
      ta.value = text
      ta.setAttribute('readonly', '')
      ta.style.position = 'fixed'
      ta.style.opacity = '0'
      document.body.appendChild(ta)
      ta.select()
      const ok = document.execCommand('copy')
      document.body.removeChild(ta)
      if (!ok) throw new Error('copy failed')
    }
    ElMessage.success(`已复制 chunkId ${text}`)
  } catch (e) {
    ElMessage.error('复制失败，请手动选中复制')
  }
}
</script>

<style scoped>
.evaluation-container {
  padding: 20px;
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 16px;
}

.last-run {
  font-size: 14px;
  color: #606266;
}

.note-text {
  margin: 8px 0 0;
  font-size: 13px;
  color: #909399;
  line-height: 1.6;
}

.block-gap {
  margin-top: 16px;
}

.section-title {
  margin: 24px 0 8px;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.summary-col {
  margin-top: 12px;
}

.running-box {
  height: 160px;
}

.empty-guide {
  max-width: 520px;
  margin: 0;
  font-size: 13px;
  color: #909399;
  line-height: 1.6;
}

.filter-bar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 16px;
  margin-bottom: 12px;
}

.filter-bar .note-text {
  margin: 0;
}

.expand-box {
  padding: 12px 20px;
}

.truth-line {
  margin-top: 8px;
  font-size: 13px;
  color: #606266;
}

.chunk-id {
  display: inline-block;
  margin: 0 6px 4px 0;
  padding: 0 6px;
  font-size: 13px;
  color: #606266;
  border: 1px solid transparent;
  border-radius: 3px;
}

.chunk-id.hit {
  font-weight: 700;
  color: #67c23a;
  background-color: #f0f9eb;
  border-color: #b3e19d;
}

.miss-text {
  color: #e6a23c;
}

.error-text {
  color: #f56c6c;
}

.muted {
  color: #909399;
}

.title-text {
  font-weight: 600;
  color: #303133;
}

.helper-collapse {
  background-color: #fff;
  padding: 0 20px;
  border-radius: 4px;
  border: 1px solid #e4e7ed;
}

.helper-form {
  margin-top: 16px;
}
</style>
