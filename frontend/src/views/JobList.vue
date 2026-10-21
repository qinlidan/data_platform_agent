<template>
  <div class="job-list-layout">
    <!-- 左侧：作业列表 -->
    <div class="job-list-container">
      <!-- 搜索区域 -->
      <el-card class="search-card" shadow="never">
      <el-form :model="searchForm" :inline="true">
        <el-form-item label="作业名称">
          <el-input
            v-model="searchForm.keyword"
            placeholder="请输入作业编码或名称"
            clearable
            style="width: 200px"
          />
        </el-form-item>

        <el-form-item label="作业状态">
          <el-select v-model="searchForm.status" placeholder="请选择" clearable style="width: 150px">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="已发布" value="PUBLISHED" />
            <el-option label="已归档" value="ARCHIVED" />
          </el-select>
        </el-form-item>

        <el-form-item label="作业类型">
          <el-select v-model="searchForm.jobType" placeholder="请选择" clearable style="width: 150px">
            <el-option label="DB2HIVE" value="DB2HIVE" />
            <el-option label="HIVE2HIVE" value="HIVE2HIVE" />
          </el-select>
        </el-form-item>

        <el-form-item label="源数据源">
          <el-select v-model="searchForm.sourceDatasourceId" placeholder="请选择" clearable style="width: 150px">
            <el-option
              v-for="ds in datasourceList"
              :key="ds.id"
              :label="ds.sourceName"
              :value="ds.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="源库名">
          <el-input
            v-model="searchForm.sourceDatabase"
            placeholder="请输入源库名"
            clearable
            style="width: 150px"
          />
        </el-form-item>

        <el-form-item label="源表名">
          <el-input
            v-model="searchForm.sourceTable"
            placeholder="请输入源表名"
            clearable
            style="width: 150px"
          />
        </el-form-item>

        <el-form-item label="作业频率">
          <el-input
            v-model="searchForm.scheduleFrequency"
            placeholder="请输入作业频率"
            clearable
            style="width: 150px"
          />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" @click="handleSearch">
            <el-icon><Search /></el-icon>
            搜索
          </el-button>
          <el-button @click="handleReset">
            <el-icon><Refresh /></el-icon>
            重置
          </el-button>
          <el-button type="primary" @click="handleCreate">
            <el-icon><Plus /></el-icon>
            新建作业
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表格区域 -->
    <el-card class="table-card" shadow="never">
      <el-table
        :data="tableData"
        v-loading="loading"
        stripe
        border
        style="width: 100%"
      >
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="jobCode" label="作业编码" width="180" />
        <el-table-column prop="jobName" label="作业名称" width="200" />
        <el-table-column prop="jobType" label="作业类型" width="120">
          <template #default="{ row }">
            <el-tag :type="row.jobType === 'DB2HIVE' ? 'primary' : 'success'">
              {{ row.jobType }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="数据源" width="250">
          <template #default="{ row }">
            <div class="source-info">
              <div>{{ row.sourceDatabase }}.{{ row.sourceTable }}</div>
              <el-icon><Right /></el-icon>
              <div>{{ row.targetDatabase }}.{{ row.targetTable }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="engineType" label="执行引擎" width="100" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag
              :type="
                row.status === 'DRAFT' ? 'info' :
                row.status === 'PUBLISHED' ? 'success' : 'warning'
              "
            >
              {{ statusMap[row.status] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="currentVersion" label="当前版本" width="100" />
        <el-table-column prop="publishedVersion" label="发布版本" width="100" />
        <el-table-column prop="createTime" label="创建时间" width="180" />
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              size="small"
              @click="handleEdit(row)"
            >
              编辑
            </el-button>
            <el-button
              link
              type="success"
              size="small"
              @click="handlePublish(row)"
              :disabled="row.status === 'PUBLISHED'"
            >
              发布
            </el-button>
            <el-button
              link
              type="warning"
              size="small"
              @click="handleArchive(row)"
              :disabled="row.status !== 'PUBLISHED'"
            >
              归档
            </el-button>
            <el-button
              link
              type="danger"
              size="small"
              @click="handleDelete(row)"
              :disabled="row.status === 'PUBLISHED'"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination">
        <el-pagination
          v-model:current-page="pagination.pageNum"
          v-model:page-size="pagination.pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="pagination.total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </el-card>
    </div>

    <!-- 右侧：AI 智能助手 -->
    <div class="ai-assistant-panel">
      <el-card class="chat-card" shadow="never">
        <template #header>
          <div class="card-header">
            <span><el-icon><ChatDotRound /></el-icon> AI 智能助手</span>
            <el-tag type="success" size="small">Beta</el-tag>
          </div>
        </template>

        <!-- 使用说明 -->
        <el-alert
          v-if="messages.length === 0"
          title="AI 助手"
          type="info"
          :closable="false"
          class="usage-tips"
        >
          <div class="tips-content">
            <p style="font-size: 12px; margin: 0;">💡 用自然语言创建和管理作业</p>
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
              <el-avatar v-if="msg.role === 'user'" :size="32">
                <el-icon><User /></el-icon>
              </el-avatar>
              <el-avatar v-else :size="32" style="background-color: #409eff;">
                <el-icon><ChatDotRound /></el-icon>
              </el-avatar>
            </div>

            <div class="message-content">
              <!-- 作业列表表格渲染 -->
              <div v-if="msg.isJobList && msg.jobListData" class="job-list-table">
                <div class="job-list-summary">{{ msg.jobListData.summary }}</div>
                <el-table :data="msg.jobListData.data" size="small" stripe border style="margin-top: 8px;">
                  <el-table-column prop="jobCode" label="作业编码" width="140" />
                  <el-table-column prop="jobName" label="作业名称" width="120" />
                  <el-table-column prop="jobType" label="类型" width="90">
                    <template #default="{ row }">
                      <el-tag :type="row.jobType === 'DB2HIVE' ? 'primary' : 'success'" size="small">
                        {{ row.jobType }}
                      </el-tag>
                    </template>
                  </el-table-column>
                  <el-table-column label="源 → 目标" min-width="180">
                    <template #default="{ row }">
                      <div style="font-size: 12px;">
                        {{ row.sourceDatabase }}.{{ row.sourceTable }} → {{ row.targetDatabase }}.{{ row.targetTable }}
                      </div>
                    </template>
                  </el-table-column>
                  <el-table-column prop="status" label="状态" width="80">
                    <template #default="{ row }">
                      <el-tag
                        :type="row.status === 'DRAFT' ? 'info' : row.status === 'PUBLISHED' ? 'success' : 'warning'"
                        size="small"
                      >
                        {{ statusMap[row.status] || row.status }}
                      </el-tag>
                    </template>
                  </el-table-column>
                </el-table>
              </div>
              <!-- 普通文本消息 -->
              <div v-else class="message-text" v-html="formatMessage(msg.content)"></div>
            </div>
          </div>

          <!-- 加载中 -->
          <div v-if="aiLoading" class="message-item assistant">
            <div class="message-avatar">
              <el-avatar :size="32" style="background-color: #409eff;">
                <el-icon><ChatDotRound /></el-icon>
              </el-avatar>
            </div>
            <div class="message-content">
              <el-skeleton :rows="1" animated />
            </div>
          </div>
        </div>

        <!-- 输入框 -->
        <div class="chat-input-area">
          <el-input
            v-model="inputMessage"
            type="textarea"
            :rows="2"
            placeholder="输入需求，如：创建DB2到Hive作业..."
            @keydown.ctrl.enter="sendMessage"
            :disabled="aiLoading"
          />
          <div class="input-actions">
            <el-button size="small" @click="clearMessages" :disabled="aiLoading || messages.length === 0">
              清空
            </el-button>
            <el-button type="primary" size="small" @click="sendMessage" :loading="aiLoading" :disabled="!inputMessage.trim()">
              发送
            </el-button>
          </div>
        </div>
      </el-card>
    </div>

    <!-- 作业类型选择对话框 -->
    <JobTypeDialog
      v-model="jobTypeDialogVisible"
      @confirm="handleJobTypeConfirm"
    />

    <!-- 编辑对话框 -->
    <JobCreateDialog
      v-model="createDialogVisible"
      :job-id="editJobId"
      @success="handleSuccess"
    />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus, Right, ChatDotRound, User } from '@element-plus/icons-vue'
import { jobApi } from '@/api'
import request from '@/utils/request'
import JobCreateDialog from './JobCreateDialog.vue'
import JobTypeDialog from './JobTypeDialog.vue'

// 搜索表单
const searchForm = reactive({
  keyword: '',
  status: '',
  jobType: '',
  sourceDatasourceId: null,
  sourceDatabase: '',
  sourceTable: '',
  scheduleFrequency: ''
})

// 分页
const pagination = reactive({
  pageNum: 1,
  pageSize: 10,
  total: 0
})

// 表格数据
const router = useRouter()

const tableData = ref([])
const loading = ref(false)
const datasourceList = ref([])

// 对话框
const createDialogVisible = ref(false)
const jobTypeDialogVisible = ref(false)
const editJobId = ref(null)

// 状态映射
const statusMap = {
  DRAFT: '草稿',
  PUBLISHED: '已发布',
  ARCHIVED: '已归档'
}

// 获取列表
const getList = async () => {
  loading.value = true
  try {
    const res = await jobApi.getJobList({
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize,
      keyword: searchForm.keyword || undefined,
      status: searchForm.status || undefined,
      jobType: searchForm.jobType || undefined,
      sourceDatasourceId: searchForm.sourceDatasourceId || undefined,
      sourceDatabase: searchForm.sourceDatabase || undefined,
      sourceTable: searchForm.sourceTable || undefined,
      scheduleFrequency: searchForm.scheduleFrequency || undefined
    })

    tableData.value = res.records
    pagination.total = res.total
  } catch (error) {
    console.error('获取列表失败', error)
  } finally {
    loading.value = false
  }
}

// 加载数据源列表
const loadDatasources = async () => {
  try {
    const res = await request.get('/datasources/all')
    datasourceList.value = res.data || []
  } catch (error) {
    console.error('加载数据源失败', error)
  }
}

// 搜索
const handleSearch = () => {
  pagination.pageNum = 1
  getList()
}

// 重置
const handleReset = () => {
  searchForm.keyword = ''
  searchForm.status = ''
  searchForm.jobType = ''
  searchForm.sourceDatasourceId = null
  searchForm.sourceDatabase = ''
  searchForm.sourceTable = ''
  searchForm.scheduleFrequency = ''
  handleSearch()
}

// 新建
const handleCreate = () => {
  jobTypeDialogVisible.value = true
}

// 选择作业类型后进入新建页面
const handleJobTypeConfirm = ({ jobType, sourceDatasourceId }) => {
  const query = { jobType }
  if (sourceDatasourceId) {
    query.sourceDatasourceId = sourceDatasourceId
  }
  router.push({ path: '/job/create', query })
}

// 编辑
const handleEdit = (row) => {
  editJobId.value = row.id
  createDialogVisible.value = true
}

// 发布
const handlePublish = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确定要发布作业"${row.jobName}"的版本 ${row.currentVersion} 吗？`,
      '发布确认',
      {
        type: 'warning'
      }
    )

    await jobApi.publishJob(row.id, row.currentVersion)
    ElMessage.success('发布成功')
    getList()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('发布失败', error)
    }
  }
}

// 归档
const handleArchive = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确定要归档作业"${row.jobName}"吗？归档后作业将停止运行。`,
      '归档确认',
      {
        type: 'warning'
      }
    )

    await jobApi.archiveJob(row.id)
    ElMessage.success('归档成功')
    getList()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('归档失败', error)
    }
  }
}

// 删除
const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除作业"${row.jobName}"吗？此操作不可恢复！`,
      '删除确认',
      {
        type: 'error'
      }
    )

    await jobApi.deleteJob(row.id)
    ElMessage.success('删除成功')
    getList()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除失败', error)
    }
  }
}

// 分页
const handleSizeChange = (val) => {
  pagination.pageSize = val
  getList()
}

const handleCurrentChange = (val) => {
  pagination.pageNum = val
  getList()
}

// 成功回调
const handleSuccess = () => {
  getList()
}

onMounted(() => {
  getList()
  loadDatasources()
})

// ========== AI 助手相关 ==========
const messages = ref([])
const inputMessage = ref('')
const aiLoading = ref(false)
const messagesContainer = ref(null)

let eventSource = null; // 把实例提升到外部，全局保存，每次发送前关闭旧连接
let isFinished = false; // 标记：SSE 是否正常结束（收到 done 事件）

// 发送消息（流式输出）
const sendMessage = async () => {
   // 🔥重点：发送新消息之前，先关闭上一个SSE连接
  if (eventSource) {
    eventSource.close();
    eventSource = null;
  }
  isFinished = false; // 重置标记
  if (!inputMessage.value.trim() || aiLoading.value) return

  const userMessage = inputMessage.value.trim()
  inputMessage.value = ''

  messages.value.push({
    role: 'user',
    content: userMessage
  })

  scrollToBottom()
  aiLoading.value = true

  // 创建临时消息对象（流式追加内容）
  const assistantMessage = {
    role: 'assistant',
    content: '',
    isJobList: false,
    jobListData: null
  }
  messages.value.push(assistantMessage)


  try {
    console.log('🚀 使用 EventSource 建立 SSE 连接')
    // 使用 EventSource 建立 SSE 连接
     eventSource = new EventSource(
      `/api/ai/chat/stream?message=${encodeURIComponent(userMessage)}`
    )

    // ✅ 添加：监听连接打开事件
    eventSource.onopen = (e) => {
      console.log('✅ SSE 连接已建立', e)
    }

    // ✅ 添加：监听默认 message 事件（调试用）
    eventSource.onmessage = (e) => {
      console.warn('⚠️ 收到默认 message 事件（不应该出现）:', e.data)
    }

    // 监听 text 事件：逐字追加文本
    eventSource.addEventListener('text', (e) => {
      console.log('📝 收到 text 分片', new Date().toISOString(), e.data)
      try {
        const data = JSON.parse(e.data)
        assistantMessage.content += data.content || ''

        // 🔥 强制 Vue 立即更新 DOM（使用 nextTick）
        nextTick(() => {
          scrollToBottom()
        })
      } catch (err) {
        console.error('解析 text 事件失败:', err)
      }
    })

    // 监听 done 事件：流结束
    eventSource.addEventListener('done', (e) => {
      console.log('✅ 收到 done 事件，流结束')
      isFinished = true; // ✅ 标记：正常结束
      eventSource.close()
      aiLoading.value = false
      scrollToBottom()
      getList() // 刷新作业列表
    })

    // 监听 table_start 事件：开始接收表格数据
    eventSource.addEventListener('table_start', (e) => {
      try {
        const data = JSON.parse(e.data)
        assistantMessage.isJobList = true
        assistantMessage.jobListData = {
          summary: data.summary || '查询结果',
          data: []
        }
      } catch (err) {
        console.error('解析 table_start 事件失败:', err)
      }
    })

    // 监听 table_row 事件：追加表格行
    eventSource.addEventListener('table_row', (e) => {
      try {
        const row = JSON.parse(e.data)
        if (assistantMessage.jobListData && assistantMessage.jobListData.data) {
          assistantMessage.jobListData.data.push(row)
          scrollToBottom()
        }
      } catch (err) {
        console.error('解析 table_row 事件失败:', err)
      }
    })

    // 监听 error 事件（自定义）
    eventSource.addEventListener('error_event', (e) => {
      try {
        const data = JSON.parse(e.data)
        assistantMessage.content = '❌ ' + (data.message || '处理失败')
        eventSource.close()
        aiLoading.value = false
      } catch (err) {
        console.error('解析 error 事件失败:', err)
      }
    })

eventSource.onerror = (error) => {
  console.error('❌ SSE onerror 回调触发:', error, 'readyState:', eventSource.readyState)

  // ✅ 关键修复：如果已经收到 done 事件，说明是正常结束，不报错
  if (isFinished) {
    console.log("✅ SSE 流式消息接收完毕，后端关闭连接（正常结束）")
    return; // 直接返回，不弹错误提示
  }

  // 如果没有收到 done，才是真异常
  console.warn("⚠️ SSE 异常断开（未收到 done 事件）")
  if (assistantMessage.content.trim() === '' && !assistantMessage.isJobList) {
    assistantMessage.content = '❌ 连接失败，请稍后重试'
  }

  eventSource.close()
  aiLoading.value = false
}

  } catch (error) {
    console.error('发送消息失败:', error)
    aiLoading.value = false
    assistantMessage.content = '❌ 发送失败: ' + error.message
  }
}

// 清空消息
const clearMessages = () => {
  messages.value = []
}

// 格式化消息（支持换行和简单的 Markdown）
const formatMessage = (content) => {
  if (!content) return ''
  return content
    .replace(/\n/g, '<br>')
    .replace(/```(.*?)```/gs, '<pre><code>$1</code></pre>')
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
}

// 滚动到底部
const scrollToBottom = () => {
  nextTick(() => {
    if (messagesContainer.value) {
      messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
    }
  })
}

</script>

<style scoped>
.job-list-layout {
  display: flex;
  gap: 20px;
  padding: 20px;
  height: calc(100vh - 60px);
}

.job-list-container {
  flex: 1;
  min-width: 0;
  overflow-y: auto;
}

.ai-assistant-panel {
  width: 380px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
}

.chat-card {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.chat-card :deep(.el-card__body) {
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: 16px;
  overflow: hidden;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 14px;
  font-weight: 600;
}

.card-header span {
  display: flex;
  align-items: center;
  gap: 6px;
}

.usage-tips {
  margin-bottom: 12px;
  font-size: 12px;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 12px 0;
  margin-bottom: 12px;
}

.message-item {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
  font-size: 13px;
}

.message-item.user {
  flex-direction: row-reverse;
}

.message-avatar {
  flex-shrink: 0;
}

.message-content {
  flex: 1;
  max-width: 75%;
}

.job-list-table {
  padding: 8px;
  background-color: #f4f4f5;
  border-radius: 8px;
}

.job-list-summary {
  font-weight: 500;
  margin-bottom: 8px;
  font-size: 13px;
  color: #303133;
}

.message-text {
  background-color: #f4f4f5;
  padding: 8px 12px;
  border-radius: 8px;
  line-height: 1.5;
  word-break: break-word;
}

.message-item.user .message-text {
  background-color: #409eff;
  color: white;
}

.message-text :deep(code) {
  background-color: rgba(0, 0, 0, 0.1);
  padding: 2px 4px;
  border-radius: 3px;
  font-family: 'Courier New', monospace;
  font-size: 12px;
}

.chat-input-area {
  border-top: 1px solid #dcdfe6;
  padding-top: 12px;
}

.input-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 8px;
}

.search-card {
  margin-bottom: 16px;
}

.source-info {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
}

.pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
