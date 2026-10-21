<template>
  <div class="datasource-list-container">
    <!-- 搜索区域 -->
    <el-card class="search-card" shadow="never">
      <el-form :model="searchForm" :inline="true">
        <el-form-item label="数据源名称">
          <el-input
            v-model="searchForm.keyword"
            placeholder="请输入数据源编码或名称"
            clearable
            style="width: 220px"
            @keyup.enter="handleSearch"
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
            新增数据源
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表格区域 -->
    <el-card class="table-card" shadow="never">
      <el-table :data="tableData" v-loading="loading" stripe border style="width: 100%">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="sourceCode" label="数据源编码" width="160" show-overflow-tooltip />
        <el-table-column prop="sourceName" label="数据源名称" width="160" />
        <el-table-column prop="sourceType" label="数据源类型" width="120">
          <template #default="{ row }">
            <el-tag type="success" effect="light">{{ row.sourceType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="driver" label="驱动" min-width="200" show-overflow-tooltip />
        <el-table-column prop="jdbcUrl" label="数据源URL" min-width="220" show-overflow-tooltip />
        <el-table-column prop="configInfo" label="配置信息" width="120" />
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="handleEdit(row)">
              编辑
            </el-button>
            <el-button link type="primary" size="small" @click="handleTest(row)">
              测试连接
            </el-button>
            <el-button link type="danger" size="small" @click="handleDelete(row)">
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

    <!-- 新增/编辑弹窗 -->
    <DatasourceDialog
      v-model="dialogVisible"
      :datasource-id="editId"
      @success="getList"
    />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus } from '@element-plus/icons-vue'
import { datasourceApi } from '@/api'
import DatasourceDialog from './DatasourceDialog.vue'

const router = useRouter()

// 搜索表单
const searchForm = reactive({
  keyword: ''
})

// 分页
const pagination = reactive({
  pageNum: 1,
  pageSize: 10,
  total: 0
})

const tableData = ref([])
const loading = ref(false)

// 弹窗
const dialogVisible = ref(false)
const editId = ref(null)

// 获取列表
const getList = async () => {
  loading.value = true
  try {
    const res = await datasourceApi.getDatasourcePage({
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize,
      keyword: searchForm.keyword || undefined
    })
    tableData.value = res.records || []
    pagination.total = res.total || 0
  } catch (error) {
    console.error('获取数据源列表失败', error)
  } finally {
    loading.value = false
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
  handleSearch()
}

// 新增
const handleCreate = () => {
  editId.value = null
  dialogVisible.value = true
}

// 编辑：跳转到编辑页面
const handleEdit = (row) => {
  router.push(`/datasource/edit/${row.id}`)
}

// 测试连接
const handleTest = async (row) => {
  const loadingMsg = ElMessage({ message: '正在测试连接...', duration: 0 })
  try {
    const success = await datasourceApi.testConnection(row.id)
    loadingMsg.close()
    if (success) {
      ElMessage.success('连接成功')
    } else {
      ElMessage.error('连接失败，请检查配置信息')
    }
  } catch (error) {
    loadingMsg.close()
    ElMessage.error('连接失败：' + (error.message || '未知错误'))
  }
}

// 删除
const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除数据源"${row.sourceName}"吗？此操作不可恢复！`,
      '删除确认',
      { type: 'warning' }
    )
    await datasourceApi.deleteDatasource(row.id)
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

onMounted(() => {
  getList()
})
</script>

<style scoped>
.datasource-list-container {
  padding: 20px;
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
