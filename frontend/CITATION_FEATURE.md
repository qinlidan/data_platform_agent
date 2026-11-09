# 引用溯源功能说明

## 修复内容

### 1. 问题修复
- ✅ 修复了展开原文看不到内容的问题：移除了 `v-if` 条件，改用 CSS 控制显示/隐藏
- ✅ 添加了 `.citation-item.open .citation-body { display: block; }` 样式规则
- ✅ 将问答框宽度从 380px 调整为 570px（约1.5倍）

### 2. 文件修改

#### `frontend/src/views/JobList.vue`
- 宽度调整：`.ai-assistant-panel` 从 380px → 570px
- 引用展开：原文容器默认隐藏（`display: none`），点击后添加 `.open` 类显示
- 移除了模板中的 `v-if="msg.citeOpen === c.index"`，改为纯 CSS 控制

#### `frontend/src/utils/citations.js`
- `renderCitationMarkers()`: 将 [1]、[2] 等标记转换为带提示的上标
- `shortSectionPath()`: 缩短章节路径显示
- `escapeHtml()`: HTML 转义

#### `frontend/test-citations.html`
- 静态测试页面，可直接在浏览器打开查看效果
- 实现了与真实组件一致的展开/收起逻辑

## 功能流程

1. **后端推送引用数据**
   ```javascript
   eventSource.addEventListener('citations', (e) => {
     const data = JSON.parse(e.data)
     // data.citations = [{ index: 1, text: "...", sectionPath: "..." }]
     assistantMessage.citations = data.citations
   })
   ```

2. **前端渲染引用标记**
   ```javascript
   // 将 [1] 转换为 <sup class="cite">[1]</sup>
   formatMessageWithCitations(msg)
   ```

3. **用户点击展开原文**
   ```javascript
   toggleCite(msg, c.index) {
     // 切换 msg.citeOpen 的值
     msg.citeOpen = msg.citeOpen === c.index ? null : c.index
   }
   ```

4. **CSS 控制显示/隐藏**
   ```css
   .citation-body { display: none; }
   .citation-item.open .citation-body { display: block; }
   ```

## 样式说明

### 引用标记
- 蓝色上标（`#409eff`）
- 鼠标悬停显示来源路径提示
- 字体加粗，轻量间距

### 引用列表
- 灰色标题"参考来源"
- 每个引用项带边框，悬停时边框变蓝
- 点击头部区域展开/收起原文
- 原文区域浅灰背景，预格式化文本

### 宽度调整
- 原宽度：380px
- 新宽度：570px（1.5倍）
- 更宽的空间展示更长的对话内容

## 测试方法

1. **测试引用展开功能**
   - 在浏览器打开 `D:/alert4/frontend/test-citations.html`
   - 点击任意引用项的"展开原文"按钮
   - 应该看到原文内容展开，按钮文字变为"收起"
   - 再次点击应该收起原文

2. **集成测试**
   - 启动前后端服务
   - 在作业列表页面询问知识库相关问题
   - 观察 AI 回答中是否有蓝色上标 [1]、[2] 等
   - 点击下方"参考来源"中的引用项
   - 验证原文能够正确展开和收起

## 数据格式

### 后端推送的 citations 格式
```json
{
  "citations": [
    {
      "index": 1,
      "text": "原文内容...",
      "sectionPath": "文档名 > 章节名 > 子章节名"
    },
    {
      "index": 2,
      "text": "另一段原文...",
      "sectionPath": "另一个文档 > 章节"
    }
  ]
}
```

### 前端消息对象结构
```javascript
{
  role: 'assistant',
  content: '回答内容中包含 [1] 和 [2] 标记',
  citations: [
    { index: 1, text: '...', sectionPath: '...' },
    { index: 2, text: '...', sectionPath: '...' }
  ],
  citeOpen: null  // 当前展开的引用索引，null 表示都未展开
}
```

## 构建验证

```bash
cd D:/alert4/frontend
npm run build
```

构建成功，所有修改已生效。
