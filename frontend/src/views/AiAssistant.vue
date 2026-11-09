<template>
  <div class="ai-assistant-container">
    <el-card class="chat-card">
      <template #header>
        <div class="card-header">
          <span><el-icon><ChatDotRound /></el-icon> AI 智能助手</span>
          <el-tag type="success">Beta</el-tag>
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

            <!-- 表格数据展示（逐行流式渲染）-->
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
              <el-button type="primary" size="small" @click="handleConfirm(msg)">
                确认操作
              </el-button>
              <el-button size="small" @click="handleCancel">
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
          placeholder="输入你的问题或需求，例如：创建一个从 DB2 迁移到 Hive 的作业..."
          @keydown.ctrl.enter="sendMessage"
          :disabled="loading"
        />
        <div class="input-actions">
          <div class="quick-actions">
            <el-button
              text
              size="small"
              @click="setQuickMessage('查看作业列表')"
            >
              查看作业列表
            </el-button>
            <el-button
              text
              size="small"
              @click="setQuickMessage('创建一个新作业')"
            >
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
import { ref, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { ChatDotRound, User } from '@element-plus/icons-vue'

const messages = ref([])
const inputMessage = ref('')
const loading = ref(false)
const messagesContainer = ref(null)
let currentEventSource = null  // 顶层变量，防止重复连接

// ========== 非流式版本（备用，避免 429 频率限制）==========
// const sendMessage_NonStreaming = async () => {
//   if (!inputMessage.value.trim() || loading.value) return
//
//   const userMessage = inputMessage.value.trim()
//   inputMessage.value = ''
//
//   // 添加用户消息
//   messages.value.push({
//     role: 'user',
//     content: userMessage,
//     time: formatTime(new Date()),
//     needConfirmation: false
//   })
//
//   scrollToBottom()
//   loading.value = true
//
//   try {
//     // 调用非流式接口
//     const response = await fetch('/api/ai/chat', {
//       method: 'POST',
//       headers: { 'Content-Type': 'application/json' },
//       body: JSON.stringify({ message: userMessage })
//     })
//
//     const result = await response.json()
//
//     if (result.code === 200 && result.data) {
//       // 添加 AI 响应
//       messages.value.push({
//         role: 'assistant',
//         content: result.data.message || '无响应',
//         time: formatTime(new Date()),
//         needConfirmation: result.data.needConfirmation || false
//       })
//     } else {
//       throw new Error(result.message || '请求失败')
//     }
//
//     scrollToBottom()
//   } catch (error) {
//     console.error('AI 对话失败:', error)
//     ElMessage.error('AI 对话失败: ' + error.message)
//
//     // 添加错误消息
//     messages.value.push({
//       role: 'assistant',
//       content: '抱歉，我遇到了一些问题。请稍后重试。',
//       time: formatTime(new Date()),
//       needConfirmation: false
//     })
//   } finally {
//     loading.value = false
//   }
// }
// // ========== 非流式版本结束 ==========
//
// // ========== SSE 流式版本（当前启用）==========
// const sendMessage = () => {
//   if (!inputMessage.value.trim() || loading.value) return
//
//   // 关闭旧连接
//   if (currentEventSource) {
//     currentEventSource.close()
//     currentEventSource = null
//   }
//
//   const userMessage = inputMessage.value.trim()
//   inputMessage.value = ''
//
//   // 添加用户消息
//   messages.value.push({
//     role: 'user',
//     content: userMessage,
//     time: formatTime(new Date()),
//     needConfirmation: false
//   })
//
//   // 创建 AI 回复消息（内容会逐步累加）
//   const aiMessage = {
//     role: 'assistant',
//     content: '',
//     time: formatTime(new Date()),
//     needConfirmation: false,
//     tableData: null  // 用于存放表格数据
//   }
//   messages.value.push(aiMessage)
//
//   scrollToBottom()
//   loading.value = true
//
//   // SSE 完成标记（区分正常结束和真异常）
//   let isFinished = false
//
//   // 使用 EventSource 接收 SSE 流式响应
//   currentEventSource = new EventSource(`/api/ai/chat/stream?message=${encodeURIComponent(userMessage)}`)
//
//   // 处理文本事件：模型生成的自然语言（SSE 旧代码，已禁用）
//   currentEventSource.addEventListener('text', (e) => {
//     try {
//       const data = JSON.parse(e.data)
//       if (data.content) {
//         aiMessage.content += data.content  // 逐字累加
//         scrollToBottom()
//       }
//     } catch (err) {
//       console.error('解析 text 事件失败:', err, 'Raw data:', e.data)
//     }
//   })
//
//   // 处理表格开始事件
//   currentEventSource.addEventListener('table_start', (e) => {
//     try {
//       const data = JSON.parse(e.data)
//       if (data.type === 'job_list') {
//         // 初始化表格数据结构
//         aiMessage.tableData = {
//           type: 'job_list',
//           rows: []
//         }
//         scrollToBottom()
//       }
//     } catch (err) {
//       console.error('解析 table_start 事件失败:', err)
//     }
//   })
//
//   // 处理表格行事件（核心：逐行追加）
//   currentEventSource.addEventListener('table_row', (e) => {
//     try {
//       const data = JSON.parse(e.data)
//       if (aiMessage.tableData && data.type === 'job_list' && data.row) {
//         aiMessage.tableData.rows.push(data.row)  // 响应式追加
//         scrollToBottom()
//       }
//     } catch (err) {
//       console.error('解析 table_row 事件失败:', err)
//     }
//   })
//
//   // 处理流式响应结束
//   currentEventSource.addEventListener('done', () => {
//     isFinished = true  // 标记正常结束
//     loading.value = false
//     if (currentEventSource) {
//       currentEventSource.close()
//       currentEventSource = null
//     }
//     scrollToBottom()
//   })
//
//   // 处理错误
//   currentEventSource.onerror = (err) => {
//     // readyState=2 是正常关闭，不报错
//     if (currentEventSource && currentEventSource.readyState === EventSource.CLOSED) {
//       console.log('SSE 连接正常关闭')
//       // 如果正常结束（done 已触发），不做任何操作；否则为异常
//       if (!isFinished) {
//         console.warn('SSE 未收到 done 事件即关闭，可能是超时或网络断开')
//         ElMessage.warning('响应中断，请重试')
//       }
//     } else {
//       console.error('SSE 连接错误:', err)
//       if (aiMessage.content === '') {
//         aiMessage.content = '抱歉，连接失败，请稍后重试。'
//       }
//     }
//     loading.value = false
//     if (currentEventSource) {
//       currentEventSource.close()
//       currentEventSource = null
//     }
//     scrollToBottom()
//   }
// }
// // ========== SSE 流式版本结束 ==========
//
//
//
// // 设置快捷消息
// const setQuickMessage = (message) => {
//   inputMessage.value = message
// }
//
// // 处理确认
// const handleConfirm = (msg) => {
//   inputMessage.value = '确认'
//   sendMessage()
// }
//
// // 处理取消
// const handleCancel = () => {
//   messages.value.push({
//     role: 'user',
//     content: '取消',
//     time: formatTime(new Date()),
//     needConfirmation: false
//   })
//
//   messages.value.push({
//     role: 'assistant',
//     content: '已取消操作。',
//     time: formatTime(new Date()),
//     needConfirmation: false
//   })
//
//   scrollToBottom()
// }
//
// // 清空消息
// const clearMessages = () => {
//   messages.value = []
// }
//
// // 格式化消息（支持换行和简单的 Markdown）
// const formatMessage = (content) => {
//   return content
//     .replace(/\n/g, '<br>')
//     .replace(/```(.*?)```/gs, '<pre><code>$1</code></pre>')
//     .replace(/`([^`]+)`/g, '<code>$1</code>')
//     .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
//     .replace(/__(.*?)__/g, '<strong>$1</strong>')
// }
//
// // 格式化时间
// const formatTime = (date) => {
//   const hours = String(date.getHours()).padStart(2, '0')
//   const minutes = String(date.getMinutes()).padStart(2, '0')
//   return `${hours}:${minutes}`
// }
//
// // 滚动到底部
// const scrollToBottom = () => {
//   nextTick(() => {
//     if (messagesContainer.value) {
//       messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
//     }
//   })
// }
</script>

<style scoped>
.ai-assistant-container {
  padding: 20px;
  height: calc(100vh - 100px);
}

.chat-card {
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
  color: #606266;
}

.message-text {
  background-color: #f4f4f5;
  padding: 12px 16px;
  border-radius: 8px;
  line-height: 1.6;
  font-size: 14px;
}

.message-item.user .message-text {
  background-color: #409eff;
  color: white;
}

.message-text :deep(pre) {
  background-color: #282c34;
  color: #abb2bf;
  padding: 12px;
  border-radius: 4px;
  overflow-x: auto;
  margin: 8px 0;
}

.message-text :deep(code) {
  background-color: rgba(0, 0, 0, 0.1);
  padding: 2px 6px;
  border-radius: 3px;
  font-family: 'Courier New', Courier, monospace;
  font-size: 13px;
}

.message-text :deep(strong) {
  font-weight: 600;
}

.confirmation-actions {
  margin-top: 12px;
  display: flex;
  gap: 8px;
}

.chat-input-area {
  margin-top: 20px;
  border-top: 1px solid #dcdfe6;
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
