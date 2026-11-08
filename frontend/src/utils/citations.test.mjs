import { test } from 'node:test'
import assert from 'node:assert/strict'
import { escapeHtml, renderCitationMarkers, shortSectionPath, normalizeCitations } from './citations.js'

const refs = [
  { index: 1, chunkId: 3376, text: '个别 task 很慢', sectionPath: '文档二 / 2. 数据倾斜 / 个别 task' },
  { index: 2, chunkId: 3373, text: '第二条', sectionPath: '文档一 / 1.2 shuffle' }
]

test('escapeHtml 转义所有 HTML 特殊字符', () => {
  assert.equal(escapeHtml('<img src=x onerror="a()"> & \'q\''),
    '&lt;img src=x onerror=&quot;a()&quot;&gt; &amp; &#39;q&#39;')
  assert.equal(escapeHtml(null), '')
  assert.equal(escapeHtml(undefined), '')
})

test('[n] 被替换成带 data-cite 的角标，且只替换有对应引用的编号', () => {
  const html = renderCitationMarkers('倾斜 [1] 与 shuffle [2]，不存在的 [9] 保持原样', refs)
  assert.match(html, /<sup class="cite" data-cite="1"[^>]*>\[1\]<\/sup>/)
  assert.match(html, /<sup class="cite" data-cite="2"[^>]*>\[2\]<\/sup>/)
  assert.ok(html.includes('[9]'))
  assert.ok(!html.includes('data-cite="9"'))
})

test('没有引用或引用为空时原样返回', () => {
  assert.equal(renderCitationMarkers('文本 [1]', []), '文本 [1]')
  assert.equal(renderCitationMarkers('文本 [1]', null), '文本 [1]')
  assert.equal(renderCitationMarkers('', refs), '')
})

test('角标 title 里的来源路径必须转义，防止知识库内容里的标签注入页面', () => {
  const evil = [{ index: 1, chunkId: 1, text: 't', sectionPath: '"><img src=x onerror=alert(1)>' }]
  const html = renderCitationMarkers('见 [1]', evil)
  assert.ok(!html.includes('<img'), html)
  assert.ok(html.includes('&lt;img'), html)
  // 属性值没有被提前闭合
  assert.equal((html.match(/title="/g) || []).length, 1)
})

test('连续引用 [1][2] 各自独立替换', () => {
  const html = renderCitationMarkers('结论[1][2]。', refs)
  assert.equal((html.match(/class="cite"/g) || []).length, 2)
})

test('不会改写代码块里的 [1]（行内 code 标签内保持原样）', () => {
  const html = renderCitationMarkers('数组 <code>a[1]</code> 与引用 [1]', refs)
  assert.ok(html.includes('<code>a[1]</code>'), html)
  assert.equal((html.match(/class="cite"/g) || []).length, 1)
})

test('shortSectionPath 去掉文档标题只留章节，过长时截断', () => {
  assert.equal(shortSectionPath('文档二：Spark/Hive 调优 - 作业变慢篇 / 2. 数据倾斜（单点长尾） / 作业能够跑完'),
    '2. 数据倾斜（单点长尾）')
  assert.equal(shortSectionPath('只有一段'), '只有一段')
  assert.equal(shortSectionPath(''), '')
  assert.equal(shortSectionPath(null), '')
  assert.ok(shortSectionPath('文档 / ' + 'x'.repeat(200)).length <= 61)
})

test('normalizeCitations 过滤坏数据并按 index 排序', () => {
  const out = normalizeCitations([
    { index: 2, chunkId: 2, text: 'b', sectionPath: 'p2' },
    null,
    { index: 'x' },
    { index: 1, chunkId: 1, text: 'a', sectionPath: 'p1' }
  ])
  assert.deepEqual(out.map(c => c.index), [1, 2])
  assert.deepEqual(normalizeCitations(undefined), [])
  assert.deepEqual(normalizeCitations('oops'), [])
})
