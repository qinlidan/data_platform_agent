<template>
  <div class="job-list-layout">
    <!-- 左侧：作业列表 -->
    <div class="job-list-container">
      <!-- 搜索区域 -->
      <el-card class="search-card" shadow="never">
      <el-form :model="searchForm" :inline="true">
        <el-form-item label="作业状态">
          <el-select v-model="searchForm.status" placeholder="请选择" clearable style="width: 150px">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="已发布" value="PUBLISHED" />
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
          <el-select v-model="searchForm.scheduleFrequency" placeholder="请选择" clearable style="width: 150px">
            <el-option label="日" value="DAILY" />
            <el-option label="周" value="WEEKLY" />
            <el-option label="月" value="MONTHLY" />
            <el-option label="年" value="YEARLY" />
          </el-select>
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
        <el-table-column prop="jobName" label="作业名称" width="180" />
        <el-table-column prop="jobCnName" label="作业中文名" width="200" />
        <el-table-column prop="jobType" label="作业类型" width="120">
          <template #default="{ row }">
            <el-tag :type="row.jobType === 'DB2HIVE' ? 'primary' : 'success'">
              {{ row.jobType }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="源数据源" width="150">
          <template #default="{ row }">
            {{ getDatasourceName(row.sourceDatasourceId) }}
          </template>
        </el-table-column>
        <el-table-column prop="sourceDatabase" label="源库名" width="150" />
        <el-table-column prop="sourceTable" label="源表名" width="150" />
        <el-table-column prop="targetDatabase" label="目标库名" width="150" />
        <el-table-column prop="scheduleFrequency" label="调度频率" width="100">
          <template #default="{ row }">
            {{ frequencyMap[row.scheduleFrequency] || '-' }}
          </template>
        </el-table-column>
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
        <el-table-column label="当前版本" width="100">
          <template #default="{ row }">
            {{ row.currentVersion ? 'v' + row.currentVersion : '-' }}
          </template>
        </el-table-column>
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
              <el-avatar v-if="msg.role === 'user'" :size="32">
                <el-icon><User /></el-icon>
              </el-avatar>
              <el-avatar v-else :size="32" style="background-color: #409eff;">
                <el-icon><ChatDotRound /></el-icon>
              </el-avatar>
            </div>

            <div class="message-content">
              <!-- 工具调用徽章 -->
              <div v-if="msg.toolCalls && msg.toolCalls.length > 0" class="tool-badges">
                <el-tag
                  v-for="(tool, idx) in msg.toolCalls"
                  :key="idx"
                  :type="tool.status === 'running' ? 'warning' : tool.status === 'completed' ? 'success' : 'danger'"
                  size="small"
                  effect="plain"
                  style="margin-right: 6px; margin-bottom: 6px;"
                >
                  <el-icon style="vertical-align: middle; margin-right: 4px;">
                    <Tools />
                  </el-icon>
                  {{ getToolLabel(tool.toolName) }}
                </el-tag>
              </div>

              <!-- 作业列表表格渲染 -->
              <div v-if="msg.isJobList && msg.jobListData" class="job-list-table">
                <div class="job-list-summary">{{ msg.jobListData.summary }}</div>
                <el-table :data="msg.jobListData.data" size="small" stripe border style="margin-top: 8px;">
                  <el-table-column prop="jobName" label="作业英文名" width="140" />
                  <el-table-column prop="jobCnName" label="作业中文名" width="120" />
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
                        {{ row.sourceTable }} → {{ row.targetTable }}
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
              <div v-else class="message-text" v-html="formatMessageWithCitations(msg)"></div>

              <!-- 参考来源：引用溯源 -->
              <div v-if="msg.citations && msg.citations.length > 0" class="citation-list">
                <div class="citation-title">参考来源</div>
                <div
                  v-for="c in msg.citations"
                  :key="c.index"
                  :class="['citation-item', { open: msg.citeOpen === c.index }]"
                >
                  <div class="citation-head" @click="toggleCite(msg, c.index)">
                    <span class="citation-index">[{{ c.index }}]</span>
                    <span class="citation-path">{{ shortSectionPath(c.sectionPath) || '参考资料 ' + c.index }}</span>
                    <span class="citation-toggle">{{ msg.citeOpen === c.index ? '收起' : '展开原文' }}</span>
                  </div>
                  <!-- 原文展示 -->
                  <div class="citation-body">{{ c.text }}</div>
                </div>
              </div>

              <!-- 确认按钮 -->
              <div v-if="msg.needConfirmation" class="confirmation-actions">
                <el-alert type="warning" :closable="false" style="margin-top: 12px; margin-bottom: 12px">
                  <template #title>
                    <strong>⚠️ 危险操作需要确认</strong>
                  </template>
                  {{ msg.confirmationMessage }}
                </el-alert>
                <el-button type="danger" size="small" @click="handleConfirm(msg)">
                  <el-icon><Check /></el-icon>
                  确认删除
                </el-button>
                <el-button size="small" @click="handleCancel(msg)">
                  <el-icon><Close /></el-icon>
                  取消
                </el-button>
              </div>
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
      :job-version-id="editJobId"
      @success="handleSuccess"
    />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus, Right, ChatDotRound, User, Tools, Check, Close } from '@element-plus/icons-vue'
import { jobApi } from '@/api'
import request from '@/utils/request'
import JobCreateDialog from './JobCreateDialog.vue'
import JobTypeDialog from './JobTypeDialog.vue'
import { renderCitationMarkers, shortSectionPath, escapeHtml } from '@/utils/citations'

// 搜索表单
const searchForm = reactive({
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
  PUBLISHED: '已发布'
}

const frequencyMap = {
  DAILY: '日',
  WEEKLY: '周',
  MONTHLY: '月',
  YEARLY: '年'
}

// 根据数据源ID获取数据源名称
const getDatasourceName = (id) => {
  if (!id) return '-'
  const ds = datasourceList.value.find(d => d.id === id)
  return ds ? ds.sourceName : '-'
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
    datasourceList.value = res || []
    console.log('数据源列表:', datasourceList.value)
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
  editJobId.value = row.jobVersionId
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
  // 加载已保存的会话ID
  conversationId.value = loadConversationId()
})

// ========== AI 助手相关 ==========
const messages = ref([])
const inputMessage = ref('')
const aiLoading = ref(false)
const messagesContainer = ref(null)
const conversationId = ref(null) // 存储会话ID

// 从 localStorage 加载 conversationId（带过期检查）
const loadConversationId = () => {
  try {
    const id = localStorage.getItem('ai_conversation_id')
    const expires = localStorage.getItem('ai_conversation_id_expires')

    if (!id || !expires) {
      return null
    }

    // 检查是否过期（7天）
    const expiresTime = parseInt(expires)
    if (Date.now() > expiresTime) {
      // 已过期，清除
      localStorage.removeItem('ai_conversation_id')
      localStorage.removeItem('ai_conversation_id_expires')
      console.log('🔄 会话ID已过期，已清除')
      return null
    }

    console.log('✅ 从 localStorage 加载会话ID:', id)
    return id
  } catch (err) {
    console.error('加载会话ID失败:', err)
    return null
  }
}

// 保存 conversationId 到 localStorage（7天过期）
const saveConversationId = (id) => {
  try {
    const expiresAt = Date.now() + 7 * 24 * 60 * 60 * 1000 // 7天后过期
    localStorage.setItem('ai_conversation_id', id)
    localStorage.setItem('ai_conversation_id_expires', expiresAt.toString())
    console.log('✅ 保存会话ID到 localStorage:', id, '过期时间:', new Date(expiresAt).toLocaleString())
  } catch (err) {
    console.error('保存会话ID失败:', err)
  }
}

// 清除 conversationId
const clearConversationId = () => {
  localStorage.removeItem('ai_conversation_id')
  localStorage.removeItem('ai_conversation_id_expires')
  conversationId.value = null
  console.log('🔄 已清除会话ID')
}

let eventSource = null; // 把实例提升到外部，全局保存，每次发送前关闭旧连接
let isFinished = false; // 标记：SSE 是否正常结束（收到 done 事件）
let pendingConfirmationToken = null; // 存储待确认的 token

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
  const assistantMessage = reactive({
    role: 'assistant',
    content: '',
    isJobList: false,
    jobListData: null,
    toolCalls: [], // 新增：记录工具调用轨迹
    needConfirmation: false, // 是否需要确认
    confirmationMessage: '', // 确认提示信息
    confirmationToolName: '' // 需要确认的工具名称
  })
  messages.value.push(assistantMessage)


  try {
    console.log('使用 EventSource 建立 SSE 连接')
    // 构建URL，带上 conversationId（如果有的话）
    let url = `/api/ai/chat/stream?message=${encodeURIComponent(userMessage)}`
    if (conversationId.value) {
      url += `&conversationId=${conversationId.value}`
    }

    // 使用 EventSource 建立 SSE 连接
    eventSource = new EventSource(url)

    //  添加：监听连接打开事件
    eventSource.onopen = (e) => {
      console.log('✅ SSE 连接已建立', e)
    }

    // 添加：监听默认 message 事件（调试用）
    eventSource.onmessage = (e) => {
      console.warn('收到默认 message 事件（不应该出现）:', e.data)
    }

    // 监听 conversation_id 事件：保存会话ID（仅首次对话）
    eventSource.addEventListener('conversation_id', (e) => {
      try {
        const data = JSON.parse(e.data)
        // 🔥 关键：只有当前没有 conversationId 时，才保存后端返回的新ID
        // 如果已经有 conversationId（从 localStorage 加载或上次对话保存），不覆盖
        if (data.conversationId && !conversationId.value) {
          conversationId.value = data.conversationId
          saveConversationId(data.conversationId) // 保存到 localStorage
          console.log('✅ 保存新会话ID:', conversationId.value)
        } else if (conversationId.value) {
          console.log('ℹ️ 已有会话ID，跳过保存:', conversationId.value)
        }
      } catch (err) {
        console.error('解析 conversation_id 事件失败:', err)
      }
    })

    // 监听 text 事件：逐字追加文本
    eventSource.addEventListener('text', (e) => {
      console.log('收到 text 分片', new Date().toISOString(), e.data)
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
      console.log('收到 done 事件，流结束')
      isFinished = true; //  标记：正常结束
      eventSource.close()
      aiLoading.value = false
      scrollToBottom()
      getList() // 刷新作业列表
    })

    // 监听 citations 事件：知识库引用信息
    eventSource.addEventListener('citations', (e) => {
      try {
        const data = JSON.parse(e.data)
        console.log('收到 citations 事件:', data)
        if (data.citations && Array.isArray(data.citations)) {
          assistantMessage.citations = data.citations
          // 强制 Vue 更新
          nextTick(() => {
            scrollToBottom()
          })
        }
      } catch (err) {
        console.error('解析 citations 事件失败:', err)
      }
    })

    // 监听 tool_start 事件：工具调用开始
    eventSource.addEventListener('tool_start', (e) => {
      try {
        const data = JSON.parse(e.data)
        console.log('工具调用开始:', data)

        // 添加工具调用记录
        assistantMessage.toolCalls.push({
          toolName: data.toolName,
          toolId: data.toolId,
          arguments: data.arguments,
          status: 'running', // running | completed | failed
          startTime: data.timestamp
        })

        // 在消息内容前添加工具调用提示
        const toolLabel = getToolLabel(data.toolName)
        assistantMessage.content = `🔧 正在调用：${toolLabel}\n\n`

        nextTick(() => {
          scrollToBottom()
        })
      } catch (err) {
        console.error('解析 tool_start 事件失败:', err)
      }
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
        const data = JSON.parse(e.data)
        if (assistantMessage.jobListData && assistantMessage.jobListData.data && data.row) {
          // 后端返回格式：{"type":"job_list","row":{...}}
          assistantMessage.jobListData.data.push(data.row)
          scrollToBottom()
        }
      } catch (err) {
        console.error('解析 table_row 事件失败:', err)
      }
    })

    // 监听 confirmation_required 事件：需要二次确认
    eventSource.addEventListener('confirmation_required', (e) => {
      try {
        const data = JSON.parse(e.data)
        console.log('🔔 收到二次确认请求:', data)

        // 存储 token
        pendingConfirmationToken = data.confirmationToken

        // 设置消息需要确认
        assistantMessage.needConfirmation = true
        assistantMessage.confirmationMessage = data.message || '此操作需要确认，请选择是否继续'
        assistantMessage.confirmationToolName = data.toolName

        // 添加警告文本
        assistantMessage.content += `\n\n⚠️ ${data.message || '此操作需要二次确认'}\n`

        scrollToBottom()
      } catch (err) {
        console.error('解析 confirmation_required 事件失败:', err)
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
    console.log("SSE 流式消息接收完毕，后端关闭连接（正常结束）")
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
  clearConversationId() // 清空会话ID（同时清除 localStorage）
  console.log('🔄 已清空对话和会话ID')
}

// 工具名称映射（中文描述）
const getToolLabel = (toolName) => {
  const toolLabels = {
    'getJobDetails': '查询作业详情',
    'listRecentJobs': '查询最近作业',
    'searchJobs': '搜索作业',
    'createJob': '创建作业',
    'updateJobConfig': '修改作业',
    'deleteJob': '删除作业',
    'publishJob': '发布作业',
    'listDataSources': '查询数据源',
    'getDataSourceByCode': '查询数据源详情'
  }
  return toolLabels[toolName] || toolName
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

// 格式化消息并渲染引用标记
const formatMessageWithCitations = (msg) => {
  if (!msg || !msg.content) return ''

  // 先格式化普通内容
  let formatted = formatMessage(msg.content)

  // 如果有引用，渲染引用标记
  if (msg.citations && msg.citations.length > 0) {
    formatted = renderCitationMarkers(formatted, msg.citations)
  }

  return formatted
}

// 切换引用原文展开/收起
const toggleCite = (msg, index) => {
  if (msg.citeOpen === index) {
    msg.citeOpen = null
  } else {
    msg.citeOpen = index
  }
}

// 滚动到底部
const scrollToBottom = () => {
  nextTick(() => {
    if (messagesContainer.value) {
      messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
    }
  })
}

// 处理确认操作
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
    content: '✅ 已确认执行'
  })

  // 创建新的 AI 回复消息
  const confirmMessage = reactive({
    role: 'assistant',
    content: '',
    isJobList: false,
    jobListData: null,
    toolCalls: []
  })
  messages.value.push(confirmMessage)

  scrollToBottom()
  aiLoading.value = true

  const token = pendingConfirmationToken
  pendingConfirmationToken = null  // 清除 token

  try {
    // 构建URL，带上 conversationId
    let url = `/api/ai/chat/stream?message=${encodeURIComponent('继续执行删除')}`
    if (conversationId.value) {
      url += `&conversationId=${conversationId.value}`
    }

    // 使用 fetch 发送带 token 的请求
    const response = await fetch(url, {
      method: 'GET',
      headers: {
        'X-Confirmation-Token': token
      }
    })

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`)
    }

    // 手动解析 SSE 流
    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() || ''

      for (const line of lines) {
        if (line.startsWith('event:')) {
          const eventType = line.substring(6).trim()
          continue
        }

        if (line.startsWith('data:')) {
          const data = line.substring(5).trim()
          if (!data) continue

          try {
            const parsed = JSON.parse(data)

            // 根据前一个 event 类型处理
            if (parsed.content) {
              confirmMessage.content += parsed.content
              scrollToBottom()
            }
          } catch (e) {
            console.error('解析 SSE 数据失败:', e, data)
          }
        }
      }
    }

    aiLoading.value = false
    getList() // 刷新作业列表

  } catch (error) {
    console.error('确认请求失败:', error)
    confirmMessage.content = `❌ 执行失败：${error.message}`
    ElMessage.error('操作执行失败')
  } finally {
    aiLoading.value = false
    scrollToBottom()
  }
}

// 处理取消操作
const handleCancel = (msg) => {
  // 清除 token
  pendingConfirmationToken = null

  // 隐藏确认按钮
  msg.needConfirmation = false

  messages.value.push({
    role: 'user',
    content: '❌ 已取消操作'
  })

  messages.value.push({
    role: 'assistant',
    content: '操作已取消。'
  })

  scrollToBottom()
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
  width: 570px;
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
  margin-bottom: 16px;
}

.tips-content {
  font-size: 13px;
  line-height: 1.6;
}

.tips-content p {
  margin: 8px 0;
}

.tips-content ul {
  margin: 8px 0;
  padding-left: 20px;
}

.tips-content li {
  margin: 5px 0;
  color: #606266;
}

.warning-text {
  color: #e6a23c;
  font-weight: 500;
  margin-top: 8px;
  margin-bottom: 0;
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

/* 引用上标样式 */
.message-text :deep(.cite) {
  color: #409eff;
  cursor: help;
  text-decoration: none;
  font-weight: 600;
  margin: 0 1px;
}

.message-text :deep(.cite:hover) {
  color: #66b1ff;
}

/* 引用列表样式 */
.citation-list {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid #e4e7ed;
}

.citation-title {
  font-size: 12px;
  color: #909399;
  margin-bottom: 8px;
  font-weight: 600;
}

.citation-item {
  margin-bottom: 8px;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  overflow: hidden;
  transition: border-color 0.2s;
}

.citation-item:hover {
  border-color: #409eff;
}

.citation-head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  cursor: pointer;
  font-size: 13px;
  background-color: #f9fafb;
  transition: background-color 0.2s;
}

.citation-head:hover {
  background-color: #ecf5ff;
}

.citation-index {
  color: #409eff;
  font-weight: 600;
  flex-shrink: 0;
}

.citation-path {
  flex: 1;
  color: #606266;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.citation-toggle {
  color: #909399;
  font-size: 12px;
  flex-shrink: 0;
}

.citation-body {
  padding: 12px;
  background-color: #fafafa;
  border-top: 1px solid #e4e7ed;
  font-size: 13px;
  line-height: 1.6;
  color: #303133;
  white-space: pre-wrap;
  word-break: break-word;
  display: none;
}

.citation-item.open .citation-body {
  display: block;
}

.tool-badges {
  margin-bottom: 8px;
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

.pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
