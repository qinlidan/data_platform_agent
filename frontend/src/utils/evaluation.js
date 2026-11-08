// 检索评估：常量与纯函数（不依赖 Vue / 后端，可直接在 node 中运行验证）

// 策略 key 固定四个，顺序即页面展示顺序
export const STRATEGIES = [
  { key: 'BM25', label: '纯 BM25' },
  { key: 'VECTOR', label: '纯向量' },
  { key: 'HYBRID', label: '混合(RRF)' },
  { key: 'HYBRID_RERANK', label: '混合+精排' }
]

export const CATEGORIES = [
  { key: 'colloquial', label: '口语化' },
  { key: 'platform', label: '平台问题' },
  { key: 'error_keyword', label: '专业报错关键字' }
]

export const strategyLabel = (key) => STRATEGIES.find(s => s.key === key)?.label || key
export const categoryLabel = (key) => CATEGORIES.find(c => c.key === key)?.label || key || '—'

// 样本少于该值时提示"不宜当结论"
export const LOW_SAMPLE_THRESHOLD = 10

const EPS = 1e-9

export const isNum = (v) => typeof v === 'number' && Number.isFinite(v)

/**
 * 指标格式化：null / 非数字 -> "—"（绝不显示成 0，避免被误读为"检索完全失败"）
 * 统一保留两位小数。
 */
export function formatMetric(v) {
  return isNum(v) ? v.toFixed(2) : '—'
}

/**
 * 取某策略统计里的指标值；样本数为 0 或指标为 null 时返回 null（不参与最优比较）。
 */
export function metricOf(stat, field) {
  if (!stat || !(stat.sampleCount > 0)) return null
  return isNum(stat[field]) ? stat[field] : null
}

/**
 * 最优值判定：返回与 values 等长的布尔数组。
 * - null / 非数字不参与比较
 * - 并列最优全部为 true（浮点误差 1e-9 内视为相等）
 * - 全部无效 -> 全 false
 * - 最优值为 0（所有有效值都是 0）时不标最优：把"全军覆没"标成最优会误导
 */
export function bestFlags(values) {
  const valid = values.filter(isNum)
  if (valid.length === 0) return values.map(() => false)
  const max = Math.max(...valid)
  if (max <= EPS) return values.map(() => false)
  return values.map(v => isNum(v) && max - v <= EPS)
}

/**
 * 一个类别（或 ALL）下的有效样本数：取各策略 sampleCount 的最大值。
 */
export function categorySampleCount(stats) {
  if (!stats) return 0
  return STRATEGIES.reduce((m, s) => Math.max(m, stats[s.key]?.sampleCount || 0), 0)
}

/**
 * 某道题是否存在"成功运行但未命中"的策略（失败的策略不算未命中）。
 */
export function hasMiss(evalCase) {
  const results = evalCase?.results || {}
  return STRATEGIES.some(s => {
    const r = results[s.key]
    return r && r.success && (r.firstHitRank == null)
  })
}
