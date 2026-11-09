<template>
  <div class="calibration-container">
    <!-- 顶部操作区 -->
    <el-card shadow="never">
      <div class="toolbar">
        <el-button type="primary" :loading="calibrating" :disabled="calibrating" @click="runCalibration">
          <el-icon v-if="!calibrating"><Histogram /></el-icon>
          {{ calibrating ? '标定运行中…' : '执行阈值标定' }}
        </el-button>
        <span class="last-run" v-if="lastRunTime">
          上次标定时间：{{ lastRunTime }}
        </span>
      </div>
      <p class="note-text">
        基于正例（可答）和反例（应拒答）评估集，自动扫描 0.05～0.95 的阈值范围，计算最优置信度阈值以平衡可答通过率和应拒拒绝率。
        标定过程需要对每个样本调用检索和重排接口，预计耗时 1-3 分钟，请耐心等待。
      </p>
    </el-card>

    <!-- 错误提示 -->
    <el-alert
      v-if="error && !calibrating"
      class="block-gap"
      type="error"
      show-icon
      :closable="false"
      title="标定失败"
      :description="error"
    />

    <!-- 运行中 -->
    <el-card v-if="calibrating" class="block-gap" shadow="never">
      <div v-loading="true" element-loading-text="正在标定阈值，需要调用检索和重排接口处理所有样本，预计耗时 1-3 分钟，请稍候…" class="running-box" />
    </el-card>

    <!-- 标定结果 -->
    <template v-else-if="report">
      <!-- 核心指标卡片 -->
      <div class="metrics-grid block-gap">
        <el-card shadow="hover" class="metric-card">
          <div class="metric-label">推荐阈值</div>
          <div class="metric-value primary">{{ report.recommendedThreshold.toFixed(2) }}</div>
          <div class="metric-desc">Youden J = {{ report.youdenIndex.toFixed(3) }}</div>
        </el-card>
        <el-card shadow="hover" class="metric-card">
          <div class="metric-label">可答通过率</div>
          <div class="metric-value success">{{ (report.accuracy * 100).toFixed(1) }}%</div>
          <div class="metric-desc">通过闸门的问题中可答占比</div>
        </el-card>
        <el-card shadow="hover" class="metric-card">
          <div class="metric-label">应拒拒绝率</div>
          <div class="metric-value warning">{{ (report.rejectionRate * 100).toFixed(1) }}%</div>
          <div class="metric-desc">应拒答的问题中被拒比例</div>
        </el-card>
        <el-card shadow="hover" class="metric-card">
          <div class="metric-label">召回率</div>
          <div class="metric-value info">{{ (report.recall * 100).toFixed(1) }}%</div>
          <div class="metric-desc">可答问题中通过闸门的比例</div>
        </el-card>
      </div>

      <!-- 阈值扫描曲线 -->
      <el-card shadow="never" class="block-gap">
        <template #header>
          <div class="card-header">
            <span>阈值扫描曲线</span>
            <span class="header-note">扫描范围：0.05～0.95，步长 0.01</span>
          </div>
        </template>
        <div ref="chartContainer" style="width: 100%; height: 400px;"></div>
        <el-alert type="info" :closable="false" class="chart-guide">
          <template #title>
            <strong>读图指引：</strong>横轴为阈值 t，每条线代表随 t 变化的性能指标。选定 t 后，只有置信度 ≥ t 的问题通过闸门生成回答。
          </template>
          <ul style="margin: 8px 0 0 20px; line-height: 1.8;">
            <li><span class="legend-dot" style="background: #28a745;"></span><strong>可答通过率</strong>：通过闸门的样本中，真正可答占比</li>
            <li><span class="legend-dot" style="background: #dc3545;"></span><strong>应拒拒绝率</strong>：应拒样本中实际被拒比例</li>
            <li><span class="legend-dot" style="background: #007bff;"></span><strong>综合准确率</strong>：(可答通过数 + 应拒拒绝数) / 总样本数</li>
            <li><span class="legend-dot" style="background: #6c757d;"></span><strong>召回率</strong>：可答通过数 / 可答总数（保留了多少真正可答的问题）</li>
          </ul>
        </el-alert>
      </el-card>

      <!-- 置信度分布统计 -->
      <el-card shadow="never" class="block-gap">
        <template #header>
          <span>置信度值域分布（按样本类型）</span>
        </template>
        <el-table :data="distributionTable" border stripe>
          <el-table-column prop="type" label="类型" width="150">
            <template #default="{ row }">
              <div class="type-label" :style="{ borderLeftColor: row.color }">
                {{ row.type }}
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="count" label="数量" width="80" align="center" />
          <el-table-column prop="min" label="最低" width="100" align="center" />
          <el-table-column prop="p25" label="P25" width="100" align="center" />
          <el-table-column prop="median" label="中位数" width="100" align="center" />
          <el-table-column prop="p75" label="P75" width="100" align="center" />
          <el-table-column prop="max" label="最高" width="100" align="center" />
        </el-table>
      </el-card>

      <!-- 多策略推荐 -->
      <el-card shadow="never" class="block-gap">
        <template #header>
          <span>多策略阈值推荐</span>
        </template>
        <div class="recommendations">
          <el-card
            v-for="(item, key) in recommendationsList"
            :key="key"
            shadow="hover"
            class="rec-card"
            :class="{ current: item.isCurrent }"
          >
            <div class="rec-title">
              {{ item.label }}
              <el-tag v-if="item.isCurrent" type="success" size="small">当前推荐</el-tag>
            </div>
            <div class="rec-value">{{ item.threshold.toFixed(2) }}</div>
            <div class="rec-desc">{{ item.desc }}</div>
          </el-card>
        </div>
        <el-alert type="info" :closable="false" style="margin-top: 16px;">
          <p style="margin: 0; line-height: 1.6;">
            <strong>配置更新：</strong>修改配置文件 <code>application.yml</code> 中的 <code>knowledge.confidence.threshold</code> 值，重启服务后生效。
            建议选择 Youden 指数最大的阈值，在可答通过率和应拒拒绝率之间取得最佳平衡。
          </p>
        </el-alert>
      </el-card>
    </template>

    <!-- 空状态 -->
    <el-card v-else class="block-gap" shadow="never">
      <el-empty description="还没有标定结果">
        <template #default>
          <p class="empty-guide">
            点击上方"执行阈值标定"，系统会自动从评估集加载正例和反例样本，扫描 91 个阈值点（0.05～0.95），
            计算每个阈值下的可答通过率、应拒拒绝率、综合准确率和召回率，最终推荐 Youden 指数最大的阈值。
          </p>
        </template>
      </el-empty>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { Histogram } from '@element-plus/icons-vue'
import * as echarts from 'echarts'

const calibrating = ref(false)
const error = ref(null)
const report = ref(null)
const lastRunTime = ref(null)
const chartContainer = ref(null)
let chartInstance = null

// 置信度分布表格数据
const distributionTable = computed(() => {
  if (!report.value?.distribution) return []

  const categories = [
    { key: 'answerable', label: '可答(A/B/C桶)', color: '#28a745' },
    { key: 'colloquial', label: '口语化', color: '#ffc107' },
    { key: 'errorKeyword', label: '关键词错误', color: '#ff9800' },
    { key: 'platform', label: '平台问题', color: '#dc3545' }
  ]

  return categories.map(cat => {
    const data = report.value.distribution[cat.key]
    if (!data || data.count === 0) {
      return {
        type: cat.label,
        color: cat.color,
        count: 0,
        min: '-',
        p25: '-',
        median: '-',
        p75: '-',
        max: '-'
      }
    }

    return {
      type: cat.label,
      color: cat.color,
      count: data.count,
      min: data.min.toFixed(3),
      p25: data.p25.toFixed(3),
      median: data.median.toFixed(3),
      p75: data.p75.toFixed(3),
      max: data.max.toFixed(3)
    }
  })
})

// 多策略推荐列表
const recommendationsList = computed(() => {
  if (!report.value?.recommendations) return []

  const strategies = [
    { key: 'youden_max', label: 'Youden 指数最大', desc: '平衡可答通过率和应拒拒绝率' },
    { key: 'high_recall', label: '高召回率优先 (准确率≥0.8)', desc: '优先保留更多可答问题' },
    { key: 'high_accuracy', label: '高准确率优先 (召回率≥0.7)', desc: '优先降低误答率' }
  ]

  return strategies
    .filter(strat => report.value.recommendations[strat.key] !== undefined)
    .map(strat => ({
      ...strat,
      threshold: report.value.recommendations[strat.key],
      isCurrent: Math.abs(report.value.recommendations[strat.key] - report.value.recommendedThreshold) < 0.005
    }))
})

// 执行标定
async function runCalibration() {
  calibrating.value = true
  error.value = null
  report.value = null // 建议每次重新标定前清空旧数据，避免干扰

  try {
    // 创建 AbortController 用于超时控制（3 分钟超时）
    const controller = new AbortController()
    const timeoutId = setTimeout(() => controller.abort(), 180000)

    const response = await fetch('/api/confidence/calibrate', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      signal: controller.signal
    })

    clearTimeout(timeoutId)

    if (!response.ok) {
      const errorText = await response.text()
      throw new Error(`HTTP ${response.status}: ${errorText}`)
    }

    // 拿到数据
    report.value = await response.json()
    lastRunTime.value = new Date().toLocaleString('zh-CN')
    ElMessage.success('阈值标定完成')

  } catch (err) {
    if (err.name === 'AbortError') {
      error.value = '请求超时（超过3分钟），请检查后端服务或减少评估样本数量'
    } else {
      error.value = err.message
    }
    ElMessage.error('标定失败：' + error.value)
  } finally {
    // 1. 关闭 loading，触发 Vue 切换 DOM (从 loading 切换到 结果展示区)
    calibrating.value = false

    // 2. 等待 Vue 把图表所在的 div 渲染到页面上
    await nextTick()

    // 3. DOM 有了，数据也有了，开始画图
    if (report.value) {
      renderChart()
    }
  }
}

// 渲染 ECharts 曲线图
function renderChart() {
  console.log('=== renderChart called ===')
  console.log('chartContainer.value:', chartContainer.value)
  console.log('chartContainer client size:', chartContainer.value?.clientWidth, chartContainer.value?.clientHeight)
  console.log('report.value:', report.value)
  console.log('curve length:', report.value?.curve?.length)

  if (!chartContainer.value) {
    console.error('❌ chartContainer is null!')
    return
  }

  if (!report.value?.curve || report.value.curve.length === 0) {
    console.error('❌ curve data is empty!')
    return
  }

  try {
    if (chartInstance) {
      console.log('Disposing old chart instance')
      chartInstance.dispose()
    }

    console.log('Initializing ECharts...')
    chartInstance = echarts.init(chartContainer.value)
    console.log('ECharts instance created:', chartInstance)

    const thresholds = report.value.curve.map(p => p.threshold)
    console.log('Thresholds count:', thresholds.length)
    console.log('First 5 thresholds:', thresholds.slice(0, 5))

    const option = {
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'cross' },
      formatter: (params) => {
        const threshold = params[0].axisValue
        let html = `<strong>阈值：${threshold.toFixed(2)}</strong><br/>`
        params.forEach(item => {
          html += `${item.marker} ${item.seriesName}：${(item.value * 100).toFixed(1)}%<br/>`
        })
        return html
      }
    },
    legend: {
      data: ['可答通过率', '应拒拒绝率', '综合准确率', '召回率'],
      top: 0
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '3%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      data: thresholds,
      name: '阈值 t',
      nameLocation: 'middle',
      nameGap: 25,
      axisLabel: {
        interval: 9
      }
    },
    yAxis: {
      type: 'value',
      name: '比例',
      min: 0,
      max: 1,
      axisLabel: {
        formatter: '{value}'
      }
    },
    series: [
      {
        name: '可答通过率',
        type: 'line',
        data: report.value.curve.map(p => p.answerablePassRate),
        smooth: true,
        lineStyle: { color: '#28a745', width: 2 },
        itemStyle: { color: '#28a745' },
        showSymbol: false
      },
      {
        name: '应拒拒绝率',
        type: 'line',
        data: report.value.curve.map(p => p.refusalRejectRate),
        smooth: true,
        lineStyle: { color: '#dc3545', width: 2 },
        itemStyle: { color: '#dc3545' },
        showSymbol: false
      },
      {
        name: '综合准确率',
        type: 'line',
        data: report.value.curve.map(p => p.combinedAccuracy),
        smooth: true,
        lineStyle: { color: '#007bff', width: 2 },
        itemStyle: { color: '#007bff' },
        showSymbol: false
      },
      {
        name: '召回率',
        type: 'line',
        data: report.value.curve.map(p => p.recallSide),
        smooth: true,
        lineStyle: { color: '#6c757d', width: 2 },
        itemStyle: { color: '#6c757d' },
        showSymbol: false,
        // 推荐阈值垂直线
        markLine: {
          silent: true,
          symbol: 'none',
          lineStyle: {
            type: 'dashed',
            color: '#000',
            width: 2
          },
          data: [{
            xAxis: report.value.recommendedThreshold,
            label: {
              formatter: `推荐: ${report.value.recommendedThreshold.toFixed(2)}`,
              position: 'insideEndTop'
            }
          }]
        }
      }
    ]
  }

  console.log('Setting chart option...')
  console.log('Option series data lengths:', option.series.map(s => s.data.length))

  chartInstance.setOption(option)
  console.log('✅ Chart rendered successfully!')

  } catch (error) {
    console.error('❌ Error rendering chart:', error)
    console.error('Error stack:', error.stack)
  }
}
</script>

<style scoped>
.calibration-container {
  padding: 20px;
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 16px;
}

.last-run {
  font-size: 14px;
  color: #909399;
}

.note-text {
  margin: 12px 0 0 0;
  font-size: 13px;
  color: #909399;
  line-height: 1.6;
}

.block-gap {
  margin-top: 16px;
}

.running-box {
  height: 200px;
}

.empty-guide {
  max-width: 600px;
  margin: 16px auto 0;
  line-height: 1.8;
  color: #606266;
  text-align: left;
}

.metrics-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

.metric-card {
  text-align: center;
}

.metric-label {
  font-size: 14px;
  color: #909399;
  margin-bottom: 8px;
}

.metric-value {
  font-size: 36px;
  font-weight: 600;
  margin-bottom: 4px;
}

.metric-value.primary {
  color: #409eff;
}

.metric-value.success {
  color: #67c23a;
}

.metric-value.warning {
  color: #e6a23c;
}

.metric-value.info {
  color: #909399;
}

.metric-desc {
  font-size: 12px;
  color: #c0c4cc;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-note {
  font-size: 13px;
  color: #909399;
  font-weight: normal;
}

.chart-guide {
  margin-top: 16px;
}

.legend-dot {
  display: inline-block;
  width: 12px;
  height: 12px;
  border-radius: 50%;
  margin-right: 6px;
  vertical-align: middle;
}

.type-label {
  padding-left: 12px;
  border-left: 4px solid;
  font-weight: 500;
}

.recommendations {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}

.rec-card {
  text-align: center;
  cursor: default;
}

.rec-card.current {
  border-color: #67c23a;
}

.rec-title {
  font-size: 14px;
  font-weight: 500;
  color: #303133;
  margin-bottom: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.rec-value {
  font-size: 32px;
  font-weight: 600;
  color: #409eff;
  margin-bottom: 4px;
}

.rec-desc {
  font-size: 12px;
  color: #909399;
  line-height: 1.4;
}

code {
  background: #f5f7fa;
  padding: 2px 6px;
  border-radius: 3px;
  font-family: 'Courier New', monospace;
  color: #e6a23c;
}
</style>
