<template>
  <div class="job-list-layout">
    <!-- 左侧：作业列表 -->
    <div class="job-list-container">
      <!-- 搜索区域 -->
      <el-card class="search-card" shadow="never">
        <el-form :model="searchForm" label-width="auto">
          <el-row :gutter="16">
            <el-col :span="6">
              <el-form-item label="作业状态">
                <el-select v-model="searchForm.status" placeholder="请选择" clearable>
                  <el-option label="草稿" value="DRAFT" />
                  <el-option label="已发布" value="PUBLISHED" />
                </el-select>
              </el-form-item>
            </el-col>

            <el-col :span="6">
              <el-form-item label="作业类型">
                <el-select v-model="searchForm.jobType" placeholder="请选择" clearable>
                  <el-option label="DB2HIVE" value="DB2HIVE" />
                  <el-option label="HIVE2HIVE" value="HIVE2HIVE" />
                </el-select>
              </el-form-item>
            </el-col>

            <el-col :span="6">
              <el-form-item label="源数据源">
                <el-select v-model="searchForm.sourceDatasourceId" placeholder="请选择" clearable>
                  <el-option
                    v-for="ds in datasourceList"
                    :key="ds.id"
                    :label="ds.sourceName"
                    :value="ds.id"
                  />
                </el-select>
              </el-form-item>
            </el-col>

            <el-col :span="6">
              <el-form-item label="作业频率">
                <el-select v-model="searchForm.scheduleFrequency" placeholder="请选择" clearable>
                  <el-option label="日" value="DAILY" />
                  <el-option label="周" value="WEEKLY" />
                  <el-option label="月" value="MONTHLY" />
                  <el-option label="年" value="YEARLY" />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>

          <el-row :gutter="16">
            <el-col :span="6">
              <el-form-item label="源库名">
                <el-input
                  v-model="searchForm.sourceDatabase"
                  placeholder="请输入源库名"
                  clearable
                />
              </el-form-item>
            </el-col>

            <el-col :span="6">
              <el-form-item label="源表名">
                <el-input
                  v-model="searchForm.sourceTable"
                  placeholder="请输入源表名"
                  clearable
                />
              </el-form-item>
            </el-col>

            <el-col :span="12">
              <el-form-item label=" " class="button-group">
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
            </el-col>
          </el-row>
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
    <keep-alive>
      <AIAssistantPanel :on-refresh="getList" />
    </keep-alive>

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

<script>
export default {
  name: 'JobList'
}
</script>

<script setup>
import { ref, reactive, onMounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus, Right } from '@element-plus/icons-vue'
import { jobApi } from '@/api'
import request from '@/utils/request'
import JobCreateDialog from './JobCreateDialog.vue'
import JobTypeDialog from './JobTypeDialog.vue'
import AIAssistantPanel from '@/components/AIAssistantPanel.vue'

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
})

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

.search-card {
  margin-bottom: 16px;
}

.search-card .el-select,
.search-card .el-input {
  width: 100%;
}

.search-card .button-group {
  text-align: right;
}

.pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
