import { test } from 'node:test'
import assert from 'node:assert/strict'
import { existsSync, readFileSync } from 'node:fs'
import { createSseParser } from './sse.js'
import { renderCitationMarkers, normalizeCitations } from './citations.js'

// 真实抓包：后端 SSE 原始响应。文件由端到端联调生成，不存在时跳过（不影响常规单测）。
const CAPTURE = 'D:/alert4/target/sse_capture.txt'

// 与 AiAssistant.vue 里 formatMessage 保持一致的简化 markdown 处理
const formatMessage = (content) =>
  content
    .replace(/\n/g, '<br>')
    .replace(/```(.*?)```/gs, '<pre><code>$1</code></pre>')
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
    .replace(/__(.*?)__/g, '<strong>$1</strong>')

const replayInChunks = (raw, size) => {
  const msg = { content: '', citations: [] }
  const parser = createSseParser((event, data) => {
    if (event === 'text' && data.content) msg.content += data.content
    else if (event === 'citations') msg.citations = normalizeCitations(data.citations)
  })
  // 按字节切块再用 stream:true 解码，模拟汉字被拆在两个网络块之间
  const bytes = Buffer.from(raw, 'utf8')
  const decoder = new TextDecoder()
  for (let i = 0; i < bytes.length; i += size) {
    parser.push(decoder.decode(bytes.subarray(i, i + size), { stream: true }))
  }
  parser.flush()
  return msg
}

test('真实抓包：无论怎么切块，解析出的正文与引用都一致', { skip: !existsSync(CAPTURE) }, () => {
  const raw = readFileSync(CAPTURE, 'utf8')
  const whole = replayInChunks(raw, raw.length * 4)
  assert.ok(whole.content.length > 200, '应解析出回答正文')
  assert.ok(whole.citations.length >= 1, '应解析出引用')

  for (const size of [1, 2, 3, 7, 64, 1000]) {
    const m = replayInChunks(raw, size)
    assert.equal(m.content, whole.content, `块大小 ${size} 时正文不一致`)
    assert.deepEqual(m.citations, whole.citations, `块大小 ${size} 时引用不一致`)
  }
  assert.ok(!whole.content.includes('�'), '不应出现被拆坏的汉字')
})

test('真实抓包：回答里的每个 [n] 角标都有对应来源并被渲染', { skip: !existsSync(CAPTURE) }, () => {
  const raw = readFileSync(CAPTURE, 'utf8')
  const m = replayInChunks(raw, 64)
  const used = [...new Set((m.content.match(/\[(\d{1,2})\]/g) || []))]
  const html = renderCitationMarkers(formatMessage(m.content), m.citations)

  for (const u of used) {
    const n = u.slice(1, -1)
    assert.ok(html.includes(`data-cite="${n}"`), `角标 ${u} 没有被渲染成可点击元素`)
  }
  const rendered = (html.match(/class="cite"/g) || []).length
  const occurrences = (m.content.match(/\[\d{1,2}\]/g) || []).length
  assert.equal(rendered, occurrences, '渲染的角标数应等于正文里 [n] 出现的次数')
  console.log(`真实回答: ${m.content.length} 字, 引用 ${m.citations.length} 条, 角标 ${rendered} 处`)
})
