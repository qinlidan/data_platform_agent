<template>
  <div class="ai-assistant-container">
    <!-- 左侧会话列表 -->
    <el-card class="conversation-sidebar">
      <template #header>
        <div class="sidebar-header">
          <span>会话历史</span>
          <el-button type="primary" size="small" @click="createNewConversation">
            <el-icon><Plus /></el-icon>
            新会话
          </el-button>
        </div>
      </template>

      <div class="conversation-list">
        <div
          v-for="conv in conversations"
          :key="conv.id"
          :class="['conversation-item', { active: conv.id === currentConversationId }]"
          @click="switchConversation(conv.id)"
        >
          <div class="conv-title">{{ conv.title }}</div>
          <div class="conv-meta">
            <span class="conv-time">{{ formatConvTime(conv.updatedAt) }}</span>
            <el-button
              text
              size="small"
              type="danger"
              @click.stop="deleteConversation(conv.id)"
            >
              <el-icon><Delete /></el-icon>
            </el-button>
          </div>
        </div>

        <el-empty v-if="conversations.length === 0" description="暂无会话" :image-size="80" />
      </div>
    </el-card>

    <!-- 右侧对话区域 -->
    <el-card class="chat-card">
      <template #header>
        <div class="card-header">
          <span><el-icon><ChatDotRound /></el-icon> AI 智能助手</span>
          <div class="header-actions">
            <el-input
              v-if="editingTitle"
              v-model="newTitle"
              size="small"
              style="width: 200px; margin-right: 8px"
              placeholder="输入标题"
              @blur="saveTitle"
              @keyup.enter="saveTitle"
            />
            <el-button v-else text size="small" @click="startEditTitle">
              <el-icon><Edit /></el-icon>
              编辑标题
            </el-button>
            <el-tag type="success">Beta</el-tag>
          </div>
        </div>
      </template>

      <!-- 使用说明 -->
      <el-alert
        v-if="messages.length === 0"
        title="AI 助手使用说明"
        type="info"
        :closable="false"
        class="usage-tips"
      >
        <div class="tips-content">
          <p>💡 你可以用自然语言与 AI 助手对话，快速创建和管理作业：</p>
          <ul>
            <li>创建作业："帮我创建一个从 DB2 的 PRODDB.user_info 迁移到 Hive 的 dw.dim_user 的作业"</li>
            <li>查询作业："查询作业 job_001 的详情"</li>
            <li>更新作业："修改作业 job_001 的目标表为 dw.dim_user_v2"</li>
            <li>查看列表："查看最近的作业列表"</li>
          </ul>
          <p class="warning-text">⚠️ 删除和发布作业需要二次确认</p>
        </div>
      </el-alert>

      <!-- 聊天消息区域 -->
      <div class="chat-messages" ref="messagesContainer">
        <div
          v-for="(msg, index) in messages"
          :key="index"
          :class="['message-item', msg.role]"
        >
          <div class="message-avatar">
            <el-avatar v-if="msg.role === 'user'" :size="36">
              <el-icon><User /></el-icon>
            </el-avatar>
            <el-avatar v-else :size="36" style="background-color: #409eff;">
              <el-icon><ChatDotRound /></el-icon>
            </el-avatar>
          </div>

          <div class="message-content">
            <div class="message-header">
              <span class="message-role">{{ msg.role === 'user' ? '你' : 'AI 助手' }}</span>
              <span class="message-time">{{ msg.time }}</span>
            </div>

            <div class="message-text" v-html="formatMessage(msg.content)"></div>

            <!-- 表格数据展示 -->
            <el-table
              v-if="msg.tableData && msg.tableData.type === 'job_list'"
              :data="msg.tableData.rows"
              size="small"
              style="margin-top: 12px"
              stripe
            >
              <el-table-column prop="jobCode" label="作业编码" width="150" />
              <el-table-column prop="jobName" label="作业名称" width="200" />
              <el-table-column prop="jobType" label="类型" width="120" />
              <el-table-column prop="sourceTable" label="源表" width="200" />
              <el-table-column prop="targetTable" label="目标表" width="200" />
              <el-table-column prop="status" label="状态" width="100">
                <template #default="scope">
                  <el-tag
                    :type="scope.row.status === 'PUBLISHED' ? 'success' : scope.row.status === 'DRAFT' ? 'info' : 'warning'"
                    size="small"
                  >
                    {{ scope.row.status }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="createTime" label="创建时间" width="180" />
            </el-table>

            <!-- 确认按钮 -->
            <div v-if="msg.needConfirmation" class="confirmation-actions">
              <el-alert type="warning" :closable="false" style="margin-bottom: 12px">
                <template #title>
                  <strong>⚠️ 危险操作需要确认</strong>
                </template>
                {{ msg.confirmationMessage || '此操作不可撤销，请确认是否继续？' }}
              </el-alert>
              <el-button type="danger" size="small" @click="handleConfirm(msg)">
                确认删除
              </el-button>
              <el-button size="small" @click="handleCancel(msg)">
                取消
              </el-button>
            </div>
          </div>
        </div>

        <!-- 加载中 -->
        <div v-if="loading" class="message-item assistant">
          <div class="message-avatar">
            <el-avatar :size="36" style="background-color: #409eff;">
              <el-icon><ChatDotRound /></el-icon>
            </el-avatar>
          </div>
          <div class="message-content">
            <el-skeleton :rows="2" animated />
          </div>
        </div>
      </div>

      <!-- 输入框 -->
      <div class="chat-input-area">
        <el-input
          v-model="inputMessage"
          type="textarea"
          :rows="3"
          placeholder="输入你的问题或需求..."
          @keydown.ctrl.enter="sendMessage"
          :disabled="loading"
        />
        <div class="input-actions">
          <div class="quick-actions">
            <el-button text size="small" @click="setQuickMessage('查看作业列表')">
              查看作业列表
            </el-button>
            <el-button text size="small" @click="setQuickMessage('创建一个新作业')">
              创建作业
            </el-button>
          </div>
          <div class="send-actions">
            <el-button @click="clearMessages" :disabled="loading || messages.length === 0">
              清空
            </el-button>
            <el-button type="primary" @click="sendMessage" :loading="loading" :disabled="!inputMessage.trim()">
              发送 (Ctrl+Enter)
            </el-button>
          </div>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ChatDotRound, User, Plus, Delete, Edit } from '@element-plus/icons-vue'

const userId = ref('anonymous')  // 实际项目中从登录状态获取
const conversations = ref([])
const currentConversationId = ref(null)
const messages = ref([])
const inputMessage = ref('')
const loading = ref(false)
const messagesContainer = ref(null)
let currentEventSource = null
let pendingConfirmationToken = null  // 存储待确认的 token

// 标题编辑
const editingTitle = ref(false)
const newTitle = ref('')

// ===== 会话管理 =====
// 加载会话列表
const loadConversations = async () => {
  try {
    const response = await fetch(`/api/ai/conversations?userId=${userId.value}`)
    const result = await response.json()
    if (result.code === 200) {
      conversations.value = result.data
      // 如果没有选中会话且有历史会话，自动选中最新的
      if (!currentConversationId.value && conversations.value.length > 0) {
        currentConversationId.value = conversations.value[0].id
      }
    }
  } catch (error) {
    console.error('加载会话列表失败:', error)
  }
}

// 创建新会话
const createNewConversation = async () => {
  try {
    const response = await fetch('/api/ai/conversations', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ userId: userId.value, title: '新会话' })
    })
    const result = await response.json()
    if (result.code === 200) {
      await loadConversations()
      currentConversationId.value = result.data.conversationId
      messages.value = []
      ElMessage.success('新会话已创建')
    }
  } catch (error) {
    console.error('创建会话失败:', error)
    ElMessage.error('创建会话失败')
  }
}

// 切换会话
const switchConversation = async (conversationId) => {
  if (conversationId === currentConversationId.value) return

  currentConversationId.value = conversationId
  await loadMessages(conversationId)
}

// 加载会话消息
const loadMessages = async (conversationId) => {
  try {
    const response = await fetch(`/api/ai/conversations/${conversationId}/messages`)
    const result = await response.json()
    if (result.code === 200) {
      messages.value = result.data.map(msg => ({
        role: msg.role,
        content: msg.content,
        time: formatTime(new Date(msg.createdAt)),
        needConfirmation: false
      }))
      nextTick(() => scrollToBottom())
    }
  } catch (error) {
    console.error('加载消息失败:', error)
  }
}

// 删除会话
const deleteConversation = async (conversationId) => {
  try {
    await ElMessageBox.confirm('确认删除此会话？', '提示', {
      type: 'warning'
    })

    const response = await fetch(`/api/ai/conversations/${conversationId}`, {
      method: 'DELETE'
    })
    const result = await response.json()
    if (result.code === 200) {
      ElMessage.success('会话已删除')
      await loadConversations()
      if (conversationId === currentConversationId.value) {
        currentConversationId.value = conversations.value[0]?.id || null
        messages.value = []
      }
    }
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除会话失败:', error)
      ElMessage.error('删除会话失败')
    }
  }
}

// 编辑标题
const startEditTitle = () => {
  if (!currentConversationId.value) return
  const conv = conversations.value.find(c => c.id === currentConversationId.value)
  newTitle.value = conv?.title || ''
  editingTitle.value = true
}

const saveTitle = async () => {
  if (!currentConversationId.value || !newTitle.value.trim()) {
    editingTitle.value = false
    return
  }

  try {
    const response = await fetch(`/api/ai/conversations/${currentConversationId.value}`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ title: newTitle.value })
    })
    const result = await response.json()
    if (result.code === 200) {
      await loadConversations()
      ElMessage.success('标题已更新')
    }
  } catch (error) {
    console.error('更新标题失败:', error)
    ElMessage.error('更新标题失败')
  } finally {
    editingTitle.value = false
  }
}

// ===== 对话功能 =====
// 发送消息（fetch + SSE）
const sendMessage = async () => {
  if (!inputMessage.value.trim() || loading.value) return

  // 关闭旧连接
  if (currentEventSource) {
    currentEventSource.close()
    currentEventSource = null
  }

  // 确保有会话
  if (!currentConversationId.value) {
    await createNewConversation()
  }

  const userMessage = inputMessage.value.trim()
  inputMessage.value = ''

  // 添加用户消息
  messages.value.push({
    role: 'user',
    content: userMessage,
    time: formatTime(new Date()),
    needConfirmation: false
  })

  // 创建 AI 回复消息
  const aiMessage = {
    role: 'assistant',
    content: '',
    time: formatTime(new Date()),
    needConfirmation: false,
    tableData: null
  }
  messages.value.push(aiMessage)

  scrollToBottom()
  loading.value = true

  let isFinished = false

  // SSE 流式响应
  const url = `/api/ai/conversations/${currentConversationId.value}/chat/stream?message=${encodeURIComponent(userMessage)}`
  currentEventSource = new EventSource(url)

  // 处理文本事件
  currentEventSource.addEventListener('text', (e) => {
    try {
      const data = JSON.parse(e.data)
      if (data.content) {
        aiMessage.content += data.content
        scrollToBottom()
      }
    } catch (err) {
      console.error('解析 text 事件失败:', err)
    }
  })

  // 处理表格开始事件
  currentEventSource.addEventListener('table_start', (e) => {
    try {
      const data = JSON.parse(e.data)
      if (data.type === 'job_list') {
        aiMessage.tableData = { type: 'job_list', rows: [] }
        scrollToBottom()
      }
    } catch (err) {
      console.error('解析 table_start 事件失败:', err)
    }
  })

  // 处理表格行事件
  currentEventSource.addEventListener('table_row', (e) => {
    try {
      const data = JSON.parse(e.data)
      if (aiMessage.tableData && data.type === 'job_list' && data.row) {
        aiMessage.tableData.rows.push(data.row)
        scrollToBottom()
      }
    } catch (err) {
      console.error('解析 table_row 事件失败:', err)
    }
  })

  // 处理二次确认事件
  currentEventSource.addEventListener('confirmation_required', (e) => {
    try {
      const data = JSON.parse(e.data)
      console.log('收到二次确认请求:', data)

      // 存储 token
      pendingConfirmationToken = data.confirmationToken

      // 设置 AI 消息需要确认
      aiMessage.needConfirmation = true
      aiMessage.confirmationMessage = data.message || '此操作需要确认，请选择是否继续'
      aiMessage.confirmationToolName = data.toolName

      // 自动添加提示文本
      if (!aiMessage.content) {
        aiMessage.content = `⚠️ 检测到危险操作：${data.toolName}\n\n${data.message}`
      }

      scrollToBottom()
    } catch (err) {
      console.error('解析 confirmation_required 事件失败:', err)
    }
  })

  // 处理流式响应结束
  currentEventSource.addEventListener('done', () => {
    isFinished = true
    loading.value = false
    if (currentEventSource) {
      currentEventSource.close()
      currentEventSource = null
    }
    scrollToBottom()
    // 刷新会话列表（更新时间）
    loadConversations()
  })

  // 处理错误
  currentEventSource.onerror = (err) => {
    if (currentEventSource && currentEventSource.readyState === EventSource.CLOSED) {
      if (!isFinished) {
        ElMessage.warning('响应中断，请重试')
      }
    } else {
      console.error('SSE 连接错误:', err)
      if (aiMessage.content === '') {
        aiMessage.content = '抱歉，连接失败，请稍后重试。'
      }
    }
    loading.value = false
    if (currentEventSource) {
      currentEventSource.close()
      currentEventSource = null
    }
    scrollToBottom()
  }
}

// 设置快捷消息
const setQuickMessage = (message) => {
  inputMessage.value = message
}

// 处理确认
const handleConfirm = async (msg) => {
  if (!pendingConfirmationToken) {
    ElMessage.error('确认令牌已过期，请重新操作')
    return
  }

  // 隐藏确认按钮
  msg.needConfirmation = false

  // 添加用户确认消息
  messages.value.push({
    role: 'user',
    content: '✅ 已确认执行',
    time: formatTime(new Date()),
    needConfirmation: false
  })

  // 创建 AI 回复消息
  const aiMessage = {
    role: 'assistant',
    content: '',
    time: formatTime(new Date()),
    needConfirmation: false,
    tableData: null
  }
  messages.value.push(aiMessage)

  scrollToBottom()
  loading.value = true

  let isFinished = false

  // 使用 fetch 而不是 EventSource，因为需要自定义 headers
  const token = pendingConfirmationToken
  pendingConfirmationToken = null  // 清除 token

  try {
    const response = await fetch(
      `/api/ai/conversations/${currentConversationId.value}/chat/stream?message=${encodeURIComponent(msg.content || '继续执行')}`,
      {
        method: 'GET',
        headers: {
          'X-Confirmation-Token': token
        }
      }
    )

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`)
    }

    const reader = response.body.getReader()
    const decoder = new TextDecoder()

    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      const chunk = decoder.decode(value)
      const lines = chunk.split('\n')

      for (const line of lines) {
        if (line.startsWith('data:')) {
          const data = line.substring(5).trim()
          if (!data) continue

          try {
            const parsed = JSON.parse(data)

            // 根据事件类型处理
            if (line.includes('event: text')) {
              if (parsed.content) {
                aiMessage.content += parsed.content
                scrollToBottom()
              }
            } else if (line.includes('event: done')) {
              isFinished = true
              loading.value = false
              loadConversations()
            } else if (line.includes('event: error')) {
              aiMessage.content += `\n\n❌ 错误：${parsed.message}`
              loading.value = false
            }
          } catch (e) {
            console.error('解析 SSE 数据失败:', e, data)
          }
        }
      }
    }
  } catch (error) {
    console.error('确认请求失败:', error)
    aiMessage.content = `❌ 执行失败：${error.message}`
    ElMessage.error('操作执行失败')
  } finally {
    loading.value = false
    scrollToBottom()
  }
}

// 处理取消
const handleCancel = (msg) => {
  // 清除 token
  pendingConfirmationToken = null

  // 隐藏确认按钮
  msg.needConfirmation = false

  messages.value.push({
    role: 'user',
    content: '取消',
    time: formatTime(new Date()),
    needConfirmation: false
  })

  messages.value.push({
    role: 'assistant',
    content: '已取消操作。',
    time: formatTime(new Date()),
    needConfirmation: false
  })

  scrollToBottom()
}

// 清空消息（不删除会话）
const clearMessages = async () => {
  try {
    await ElMessageBox.confirm('清空当前会话消息？', '提示', {
      type: 'warning'
    })
    messages.value = []
  } catch (error) {
    // 用户取消
  }
}

// 格式化消息
const formatMessage = (content) => {
  return content
    .replace(/\n/g, '<br>')
    .replace(/```(.*?)```/gs, '<pre><code>$1</code></pre>')
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
    .replace(/__(.*?)__/g, '<strong>$1</strong>')
}

// 格式化时间
const formatTime = (date) => {
  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')
  return `${hours}:${minutes}`
}

// 格式化会话时间
const formatConvTime = (dateStr) => {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  const now = new Date()
  const diffMs = now - date
  const diffMins = Math.floor(diffMs / 60000)

  if (diffMins < 1) return '刚刚'
  if (diffMins < 60) return `${diffMins} 分钟前`
  if (diffMins < 1440) return `${Math.floor(diffMins / 60)} 小时前`
  return date.toLocaleDateString('zh-CN', { month: 'numeric', day: 'numeric' })
}

// 滚动到底部
const scrollToBottom = () => {
  nextTick(() => {
    if (messagesContainer.value) {
      messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
    }
  })
}

// 初始化
onMounted(() => {
  loadConversations()
})

// 监听会话切换，加载消息
watch(currentConversationId, (newId) => {
  if (newId) {
    loadMessages(newId)
  }
})
</script>

<style scoped>
.ai-assistant-container {
  display: flex;
  gap: 20px;
  padding: 20px;
  height: calc(100vh - 100px);
}

.conversation-sidebar {
  width: 280px;
  height: 100%;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.sidebar-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
}

.conversation-list {
  flex: 1;
  overflow-y: auto;
  margin-top: 10px;
}

.conversation-item {
  padding: 12px;
  margin-bottom: 8px;
  border-radius: 8px;
  cursor: pointer;
  transition: background-color 0.2s;
  border: 1px solid #e8e8e8;
}

.conversation-item:hover {
  background-color: #f5f7fa;
}

.conversation-item.active {
  background-color: #ecf5ff;
  border-color: #409eff;
}

.conv-title {
  font-size: 14px;
  font-weight: 500;
  margin-bottom: 6px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conv-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.conv-time {
  font-size: 12px;
  color: #909399;
}

.chat-card {
  flex: 1;
  height: 100%;
  display: flex;
  flex-direction: column;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 16px;
  font-weight: 600;
}

.card-header span {
  display: flex;
  align-items: center;
  gap: 8px;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.usage-tips {
  margin-bottom: 20px;
}

.tips-content {
  font-size: 14px;
  line-height: 1.6;
}

.tips-content ul {
  margin: 10px 0;
  padding-left: 20px;
}

.tips-content li {
  margin: 5px 0;
}

.warning-text {
  color: #e6a23c;
  font-weight: 500;
  margin-top: 10px;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 20px 0;
  max-height: calc(100vh - 400px);
}

.message-item {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
  animation: fadeIn 0.3s ease-in;
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.message-item.user {
  flex-direction: row-reverse;
}

.message-avatar {
  flex-shrink: 0;
}

.message-content {
  flex: 1;
  max-width: 80%;
}

.message-item.user .message-content {
  text-align: right;
}

.message-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
  font-size: 12px;
  color: #909399;
}

.message-item.user .message-header {
  flex-direction: row-reverse;
}

.message-role {
  font-weight: 500;
}

.message-text {
  background-color: #f5f7fa;
  padding: 12px 16px;
  border-radius: 8px;
  line-height: 1.6;
  word-wrap: break-word;
}

.message-item.user .message-text {
  background-color: #409eff;
  color: white;
}

.message-text:deep(pre) {
  background-color: #2d2d2d;
  color: #f8f8f2;
  padding: 12px;
  border-radius: 4px;
  overflow-x: auto;
  margin: 8px 0;
}

.message-text:deep(code) {
  background-color: rgba(0, 0, 0, 0.05);
  padding: 2px 6px;
  border-radius: 3px;
  font-family: 'Courier New', monospace;
}

.message-text:deep(strong) {
  font-weight: 600;
}

.confirmation-actions {
  margin-top: 12px;
  display: flex;
  gap: 8px;
}

.chat-input-area {
  margin-top: 20px;
  border-top: 1px solid #e8e8e8;
  padding-top: 20px;
}

.input-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 12px;
}

.quick-actions {
  display: flex;
  gap: 8px;
}

.send-actions {
  display: flex;
  gap: 8px;
}
</style>

