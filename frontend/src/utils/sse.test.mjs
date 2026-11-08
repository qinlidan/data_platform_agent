import { test } from 'node:test'
import assert from 'node:assert/strict'
import { createSseParser } from './sse.js'

const collect = (chunks) => {
  const out = []
  const p = createSseParser((event, data) => out.push([event, data]))
  chunks.forEach(c => p.push(c))
  p.flush()
  return out
}

test('事件名与数据在相邻两行：能把 event 和 data 配成一对', () => {
  const out = collect(['event:text\ndata:{"content":"你好"}\n\n'])
  assert.deepEqual(out, [['text', { content: '你好' }]])
})

test('带空格的 "event: x" / "data: {...}" 同样支持', () => {
  const out = collect(['event: citations\ndata: {"citations":[{"index":1}]}\n\n'])
  assert.deepEqual(out, [['citations', { citations: [{ index: 1 }] }]])
})

test('一个网络块里多个事件', () => {
  const out = collect(['event:text\ndata:{"content":"a"}\n\nevent:done\ndata:{}\n\n'])
  assert.deepEqual(out.map(o => o[0]), ['text', 'done'])
})

test('一个事件被拆在两个网络块里（含拆在 JSON 中间、拆在汉字中间对应的字符串边界）', () => {
  const out = collect(['event:cit', 'ations\ndata:{"citations":[{"ind', 'ex":1}]}\n\n'])
  assert.deepEqual(out, [['citations', { citations: [{ index: 1 }] }]])
})

test('没有 event 行的数据默认事件名为 message', () => {
  assert.deepEqual(collect(['data:{"a":1}\n\n']), [['message', { a: 1 }]])
})

test('非法 JSON 的数据被跳过，不影响后续事件', () => {
  const out = collect(['event:text\ndata:{坏\n\nevent:done\ndata:{}\n\n'])
  assert.deepEqual(out.map(o => o[0]), ['done'])
})

test('CRLF 换行也能解析', () => {
  assert.deepEqual(collect(['event:text\r\ndata:{"content":"x"}\r\n\r\n']), [['text', { content: 'x' }]])
})

test('末尾没有空行的最后一个事件，flush 时仍然送出', () => {
  assert.deepEqual(collect(['event:done\ndata:{}']), [['done', {}]])
})

test('以冒号开头的注释行（心跳）被忽略', () => {
  assert.deepEqual(collect([': keepalive\n\nevent:done\ndata:{}\n\n']), [['done', {}]])
})
