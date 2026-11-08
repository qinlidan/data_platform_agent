// 最小的 SSE（text/event-stream）解析器，给 fetch 读流的场景用。
// 原因：EventSource 不能带自定义请求头（二次确认要带 X-Confirmation-Token），只能用 fetch 读流；
// 而 SSE 里 "event:" 和 "data:" 是相邻的两行，不能逐行孤立判断，必须按空行分隔的"事件块"来解析。
// 另外一个事件可能被拆在两个网络块之间，所以要自己缓冲。纯函数，无 DOM 依赖。

/**
 * @param {(event: string, data: any) => void} onEvent  每解析出一个事件调用一次；data 已 JSON.parse
 * @returns {{ push: (chunk: string) => void, flush: () => void }}
 */
export const createSseParser = (onEvent) => {
  let buffer = ''

  const dispatch = (block) => {
    let event = 'message'
    const dataLines = []
    for (const rawLine of block.split('\n')) {
      const line = rawLine.replace(/\r$/, '')
      if (!line || line.startsWith(':')) continue          // 空行 / 注释（心跳）
      const colon = line.indexOf(':')
      const field = colon < 0 ? line : line.slice(0, colon)
      let value = colon < 0 ? '' : line.slice(colon + 1)
      if (value.startsWith(' ')) value = value.slice(1)    // 规范：冒号后的一个空格不属于值
      if (field === 'event') event = value
      else if (field === 'data') dataLines.push(value)
    }
    if (dataLines.length === 0) return
    let data
    try {
      data = JSON.parse(dataLines.join('\n'))
    } catch (e) {
      return                                                // 非法 JSON：丢弃这个事件，不影响后续
    }
    onEvent(event, data)
  }

  const drain = (final) => {
    buffer = buffer.replace(/\r\n/g, '\n')
    let idx
    while ((idx = buffer.indexOf('\n\n')) >= 0) {
      dispatch(buffer.slice(0, idx))
      buffer = buffer.slice(idx + 2)
    }
    if (final && buffer.trim()) {
      dispatch(buffer)
      buffer = ''
    }
  }

  return {
    push(chunk) {
      buffer += chunk
      drain(false)
    },
    flush() {
      drain(true)
    }
  }
}
