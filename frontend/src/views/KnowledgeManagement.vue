<template>
  <div class="knowledge-management-container">
    <el-tabs v-model="activeTab" type="card">
      <!-- Tab 1: 文档管理 -->
      <el-tab-pane label="文档管理" name="documents">
        <el-card shadow="never">
          <el-table :data="documents" v-loading="documentsLoading" stripe border style="width: 100%">
            <el-table-column prop="docId" label="文档ID" width="200" show-overflow-tooltip />
            <el-table-column prop="docType" label="文档类型" width="120">
              <template #default="{ row }">
                <el-tag :type="row.docType === 'FAQ' ? 'success' : 'primary'">{{ row.docType }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="fileName" label="文件名" min-width="200" show-overflow-tooltip />
            <el-table-column prop="chunkCount" label="切块数量" width="120" align="center" />
            <el-table-column prop="importTime" label="导入时间" width="170" />
            <el-table-column label="操作" width="220" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="viewChunks(row)">
                  查看切块
                </el-button>
                <el-button link type="warning" size="small" @click="reimport(row)">
                  重新导入
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <!-- Tab 2: 手工录入 -->
      <el-tab-pane label="手工录入" name="manual">
        <el-card shadow="never">
          <el-form :model="manualForm" label-width="120px">
            <el-form-item label="文档类型">
              <el-select v-model="manualForm.docType" placeholder="请选择文档类型">
                <el-option label="FAQ" value="FAQ" />
                <el-option label="手册" value="manual" />
              </el-select>
            </el-form-item>

            <el-form-item label="Markdown内容">
              <el-input
                v-model="manualForm.content"
                type="textarea"
                :rows="15"
                placeholder="请输入Markdown格式的内容"
              />
            </el-form-item>

            <el-form-item>
              <el-button type="primary" @click="previewSplit" :loading="previewLoading">
                <el-icon><View /></el-icon>
                预览切分
              </el-button>
            </el-form-item>
          </el-form>

          <!-- 预览结果 -->
          <div v-if="previewResults.length > 0" class="preview-section">
            <el-divider content-position="left">切分预览结果</el-divider>

            <div class="preview-stats">
              <el-statistic title="切分块数" :value="previewResults.length" />
              <el-statistic title="重复检测" :value="duplicateCount" suffix="个" />
            </div>

            <el-table ref="previewTableRef" :data="previewResults" border stripe max-height="500" style="margin-top: 20px" @selection-change="handleSelectionChange">
              <el-table-column type="index" label="序号" width="60" />
              <el-table-column type="selection" width="55" />
              <el-table-column label="问题" min-width="250">
                <template #default="{ row }">
                  <div v-if="row.isDuplicate" class="duplicate-marker">
                    <el-tag type="warning" size="small">重复</el-tag>
                  </div>
                  <div class="chunk-questions">{{ row.questions }}</div>
                </template>
              </el-table-column>
              <el-table-column label="答案" min-width="300">
                <template #default="{ row }">
                  <div class="chunk-answer">{{ row.answer }}</div>
                </template>
              </el-table-column>
              <el-table-column prop="sceneType" label="场景类型" width="120" />
            </el-table>

            <div style="margin-top: 20px">
              <el-button type="success" @click="confirmImport" :loading="importLoading">
                <el-icon><Check /></el-icon>
                确认入库
              </el-button>
              <el-button @click="clearPreview">
                <el-icon><Close /></el-icon>
                取消
              </el-button>
            </div>
          </div>
        </el-card>
      </el-tab-pane>

      <!-- Tab 3: 检索自测 -->
      <el-tab-pane label="检索自测" name="search">
        <el-card shadow="never">
          <el-form :model="searchForm" :inline="true">
            <el-form-item label="查询内容">
              <el-input
                v-model="searchForm.query"
                placeholder="请输入查询内容"
                clearable
                style="width: 400px"
                @keyup.enter="doSearch"
              />
            </el-form-item>

            <el-form-item label="文档类型">
              <el-select v-model="searchForm.docType" placeholder="全部" clearable style="width: 150px">
                <el-option label="FAQ" value="FAQ" />
                <el-option label="手册" value="manual" />
              </el-select>
            </el-form-item>

            <el-form-item label="返回数量">
              <el-input-number v-model="searchForm.topK" :min="1" :max="20" style="width: 120px" />
            </el-form-item>

            <el-form-item>
              <el-button type="primary" @click="doSearch" :loading="searchLoading">
                <el-icon><Search /></el-icon>
                检索
              </el-button>
            </el-form-item>
          </el-form>

          <!-- 搜索结果 -->
          <div v-if="searchResults.length > 0" class="search-results">
            <el-divider content-position="left">检索结果 (共 {{ searchResults.length }} 条)</el-divider>

            <div v-for="(result, index) in searchResults" :key="index" class="result-card">
              <el-card shadow="hover">
                <div class="result-header">
                  <span class="result-index">{{ index + 1 }}</span>
                  <span class="result-questions" v-html="highlightKeyword(result.chunkText)"></span>
                  <el-tag type="success" size="small">score: {{ (result.score).toFixed(2) }}</el-tag>
                </div>
                <div class="result-answer" v-html="highlightKeyword(result.answer)"></div>
                <div class="result-meta">
                  <span>ID: {{ result.chunkId }}</span>
                  <span>类型: {{ result.docType }}</span>
                  <span>文档: {{ result.docId }}</span>
                </div>
              </el-card>
            </div>
          </div>

          <el-empty v-else-if="searchTriggered" description="未找到匹配结果" />
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <!-- 查看切块弹窗 -->
    <el-dialog v-model="chunksDialogVisible" title="查看文档切块" width="80%" top="5vh">
      <el-table :data="chunks" v-loading="chunksLoading" border stripe max-height="600">
        <el-table-column type="index" label="序号" width="60" />
        <el-table-column prop="questions" label="问题" min-width="250" show-overflow-tooltip />
        <el-table-column prop="answer" label="答案" min-width="300" show-overflow-tooltip />
        <el-table-column prop="sceneType" label="场景类型" width="120" />
        <el-table-column prop="sectionPath" label="章节路径" width="180" show-overflow-tooltip />
      </el-table>

      <div class="pagination" style="margin-top: 20px">
        <el-pagination
          v-model:current-page="chunksPagination.page"
          v-model:page-size="chunksPagination.size"
          :total="chunksPagination.total"
          layout="total, prev, pager, next"
          @current-change="loadChunks"
        />
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus, View, Check, Close } from '@element-plus/icons-vue'
import axios from 'axios'

const activeTab = ref('documents')

// Tab 1: 文档管理
const documents = ref([])
const documentsLoading = ref(false)

const loadDocuments = async () => {
  documentsLoading.value = true
  try {
    const response = await axios.get('/api/knowledge/documents')
    if (response.data.code === 200) {
      documents.value = response.data.data
    }
  } catch (error) {
    ElMessage.error('加载文档列表失败')
  } finally {
    documentsLoading.value = false
  }
}

const viewChunks = (row) => {
  currentDocId.value = row.docId
  chunksPagination.page = 1
  chunksDialogVisible.value = true
  loadChunks()
}

const reimport = async (row) => {
  try {
    await ElMessageBox.confirm(`确认重新导入文档 ${row.fileName}？`, '提示', {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      type: 'warning'
    })

    const response = await axios.post(`/api/knowledge/documents/${row.docId}/reimport`)
    if (response.data.code === 200) {
      ElMessage.success('重新导入成功')
      loadDocuments()
    } else {
      ElMessage.error(response.data.message || '重新导入失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('重新导入失败')
    }
  }
}

// 查看切块弹窗
const chunksDialogVisible = ref(false)
const currentDocId = ref('')
const chunks = ref([])
const chunksLoading = ref(false)
const chunksPagination = reactive({
  page: 1,
  size: 20,
  total: 0
})

const loadChunks = async () => {
  chunksLoading.value = true
  try {
    const response = await axios.get(`/api/knowledge/documents/${currentDocId.value}/chunks`, {
      params: {
        page: chunksPagination.page,
        size: chunksPagination.size
      }
    })
    if (response.data.code === 200) {
      chunks.value = response.data.data.chunks
      chunksPagination.total = response.data.data.total
    }
  } catch (error) {
    ElMessage.error('加载切块失败')
  } finally {
    chunksLoading.value = false
  }
}

// Tab 2: 手工录入
const manualForm = reactive({
  docType: 'FAQ',
  content: ''
})

const previewLoading = ref(false)
const previewResults = ref([])
const previewTableRef = ref(null)
const selectedChunks = ref([])
const importLoading = ref(false)

const duplicateCount = computed(() => {
  return previewResults.value.filter(item => item.isDuplicate).length
})

const handleSelectionChange = (selection) => {
  selectedChunks.value = selection
}

const previewSplit = async () => {
  if (!manualForm.content.trim()) {
    ElMessage.warning('请输入内容')
    return
  }

  previewLoading.value = true
  try {
    const response = await axios.post('/api/knowledge/manual/preview', {
      docType: manualForm.docType,
      markdownText: manualForm.content
    })
    if (response.data.code === 200) {
      previewResults.value = response.data.data.chunks
      ElMessage.success('预览切分完成')

      // 默认全选非重复项
      await nextTick()
      if (previewTableRef.value) {
        previewResults.value.forEach(row => {
          if (!row.isDuplicate) {
            previewTableRef.value.toggleRowSelection(row, true)
          }
        })
      }
    } else {
      ElMessage.error(response.data.message || '预览失败')
    }
  } catch (error) {
    ElMessage.error('预览失败')
  } finally {
    previewLoading.value = false
  }
}

const clearPreview = () => {
  previewResults.value = []
}

const confirmImport = async () => {
  if (selectedChunks.value.length === 0) {
    ElMessage.warning('请至少勾选一条记录入库')
    return
  }

  importLoading.value = true
  try {
    const selectedTempIds = selectedChunks.value.map(chunk => chunk.tempId)
    const response = await axios.post('/api/knowledge/manual/import', {
      docType: manualForm.docType,
      selectedTempIds: selectedTempIds,
      chunks: previewResults.value
    })
    if (response.data.code === 200) {
      ElMessage.success(`成功入库 ${response.data.data.importedCount} 条记录`)
      manualForm.content = ''
      previewResults.value = []
      selectedChunks.value = []
      loadDocuments() // 刷新文档列表
    } else {
      ElMessage.error(response.data.message || '入库失败')
    }
  } catch (error) {
    ElMessage.error('入库失败')
  } finally {
    importLoading.value = false
  }
}

// Tab 3: 检索自测
const searchForm = reactive({
  query: '',
  docType: '',
  topK: 5
})

const searchResults = ref([])
const searchLoading = ref(false)
const searchTriggered = ref(false)

const doSearch = async () => {
  if (!searchForm.query.trim()) {
    ElMessage.warning('请输入查询内容')
    return
  }

  searchLoading.value = true
  searchTriggered.value = true
  try {
    const response = await axios.post('/api/knowledge/search/test', {
      query: searchForm.query,
      docType: searchForm.docType || null,
      topK: searchForm.topK
    })
    if (response.data.code === 200) {
      searchResults.value = response.data.data
    } else {
      ElMessage.error(response.data.message || '检索失败')
    }
  } catch (error) {
    ElMessage.error('检索失败')
  } finally {
    searchLoading.value = false
  }
}

const highlightKeyword = (text) => {
  if (!searchForm.query || !text) return text
  const keyword = searchForm.query.trim()
  const regex = new RegExp(`(${keyword})`, 'gi')
  return text.replace(regex, '<span class="highlight">$1</span>')
}

onMounted(() => {
  loadDocuments()
})
</script>

<style scoped>
.knowledge-management-container {
  padding: 20px;
}

.preview-section {
  margin-top: 30px;
}

.preview-stats {
  display: flex;
  gap: 40px;
  margin-bottom: 20px;
}

.duplicate-marker {
  margin-bottom: 5px;
}

.chunk-questions {
  font-weight: 500;
  color: #303133;
}

.chunk-answer {
  color: #606266;
  font-size: 14px;
}

.search-results {
  margin-top: 20px;
}

.result-card {
  margin-bottom: 15px;
}

.result-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}

.result-index {
  font-size: 18px;
  font-weight: bold;
  color: #409EFF;
  min-width: 30px;
}

.result-questions {
  flex: 1;
  font-size: 16px;
  font-weight: 500;
  color: #303133;
}

.result-answer {
  color: #606266;
  margin-bottom: 10px;
  line-height: 1.6;
}

.result-meta {
  font-size: 12px;
  color: #909399;
  display: flex;
  gap: 15px;
}

:deep(.highlight) {
  background-color: #ffeb3b;
  padding: 2px 4px;
  font-weight: bold;
}

.pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 20px;
}
</style>
