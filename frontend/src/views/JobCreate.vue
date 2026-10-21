<template>
  <div class="job-create-container">
    <el-card class="create-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span>新建作业</span>
          <el-tag :type="jobType === 'DB2HIVE' ? 'primary' : 'success'">{{ jobType }}</el-tag>
        </div>
      </template>

      <el-steps :active="currentStep" finish-status="success" align-center>
        <el-step title="参数配置" />
        <el-step title="SQL配置" />
        <el-step title="字段元数据" />
      </el-steps>

      <div class="step-content">
        <!-- Step 1: 参数配置 -->
        <div v-show="currentStep === 0" class="step-panel">
          <el-form
            ref="paramFormRef"
            :model="paramForm"
            :rules="paramRules"
            label-width="120px"
          >
            <el-form-item label="作业英文名" prop="jobCode">
              <el-input
                v-model="paramForm.jobCode"
                placeholder="请输入作业英文名"
              />
            </el-form-item>

            <el-form-item label="作业名称" prop="jobName">
              <el-input v-model="paramForm.jobName" placeholder="请输入作业名称" />
            </el-form-item>

            <el-form-item label="作业描述">
              <el-input
                v-model="paramForm.description"
                type="textarea"
                :rows="3"
                placeholder="请输入作业描述"
              />
            </el-form-item>

            <el-form-item label="作业类型">
              <el-tag :type="jobType === 'DB2HIVE' ? 'primary' : 'success'">{{ jobType }}</el-tag>
            </el-form-item>

            <el-form-item v-if="jobType === 'DB2HIVE'" label="源数据源">
              <el-input :model-value="datasourceName" disabled />
            </el-form-item>

            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="源数据库" prop="sourceDatabase">
                  <el-select
                    v-if="jobType === 'DB2HIVE'"
                    v-model="paramForm.sourceDatabase"
                    placeholder="请选择数据库"
                    style="width: 100%"
                    :loading="databaseLoading"
                    filterable
                    @change="handleDatabaseChange"
                  >
                    <el-option
                      v-for="db in databaseList"
                      :key="db"
                      :label="db"
                      :value="db"
                    />
                  </el-select>
                  <el-input
                    v-else
                    v-model="paramForm.sourceDatabase"
                    placeholder="请输入Hive源数据库"
                  />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="源表名" prop="sourceTable">
                  <el-select
                    v-if="jobType === 'DB2HIVE'"
                    v-model="paramForm.sourceTable"
                    placeholder="请选择表"
                    style="width: 100%"
                    :loading="tableLoading"
                    :disabled="!paramForm.sourceDatabase"
                    filterable
                    allow-create
                    default-first-option
                    @change="handleTableChange"
                  >
                    <el-option
                      v-for="table in tableList"
                      :key="table.tableName"
                      :label="table.tableName"
                      :value="table.tableName"
                    />
                  </el-select>
                  <el-input
                    v-else
                    v-model="paramForm.sourceTable"
                    placeholder="请输入Hive源表名"
                  />
                </el-form-item>
              </el-col>
            </el-row>

            <!-- 源表字段展示 -->
            <el-form-item v-if="jobType === 'DB2HIVE' && sourceTableFields.length > 0" label="源表字段">
              <el-table :data="sourceTableFields" border size="small" max-height="300">
                <el-table-column prop="fieldName" label="字段名" width="150" />
                <el-table-column prop="fieldType" label="字段类型" width="120" />
                <el-table-column prop="isPrimaryKey" label="是否主键" width="100" align="center">
                  <template #default="{ row }">
                    <el-tag v-if="row.isPrimaryKey" type="danger" size="small">主键</el-tag>
                    <span v-else>-</span>
                  </template>
                </el-table-column>
                <el-table-column prop="fieldComment" label="中文描述" show-overflow-tooltip />
              </el-table>
            </el-form-item>

            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="目标数据库" prop="targetDatabase">
                  <el-input v-model="paramForm.targetDatabase" placeholder="请输入目标Hive数据库" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="目标表名" prop="targetTable">
                  <el-input v-model="paramForm.targetTable" placeholder="自动等于作业英文名" disabled />
                </el-form-item>
              </el-col>
            </el-row>

            <el-form-item label="执行引擎" prop="engineType">
              <el-radio-group v-model="paramForm.engineType">
                <el-radio value="HIVE">Hive</el-radio>
                <el-radio value="SPARK">Spark</el-radio>
              </el-radio-group>
            </el-form-item>

            <el-form-item label="调度频率">
              <el-select v-model="paramForm.scheduleFrequency" placeholder="请选择调度频率" clearable style="width: 100%">
                <el-option label="每日" value="DAILY" />
                <el-option label="每月" value="MONTHLY" />
                <el-option label="每小时" value="HOURLY" />
                <el-option label="每周" value="WEEKLY" />
              </el-select>
            </el-form-item>
          </el-form>
        </div>

        <!-- Step 2: SQL配置 -->
        <div v-show="currentStep === 1" class="step-panel">
          <el-form label-width="120px">
            <el-form-item label="SQL内容">
              <el-input
                v-model="sqlForm.sqlContent"
                type="textarea"
                :rows="15"
                placeholder="请输入SQL语句"
                style="font-family: 'Consolas', monospace"
              />
            </el-form-item>

            <el-form-item label="Spark配置">
              <el-input
                v-model="sqlForm.sparkConfig"
                type="textarea"
                :rows="5"
                placeholder="请输入Spark配置（可选，JSON格式）"
              />
            </el-form-item>

            <el-form-item>
              <el-button type="primary" @click="handleCheckSyntax" :loading="checking">
                <el-icon><Check /></el-icon>
                语法检查
              </el-button>
              <el-button type="success" @click="handleParseSql" :loading="parsing">
                <el-icon><Operation /></el-icon>
                解析字段
              </el-button>
            </el-form-item>

            <el-form-item v-if="syntaxResult">
              <el-alert
                :type="syntaxResult.valid ? 'success' : 'error'"
                :title="syntaxResult.valid ? '语法检查通过' : '语法检查失败'"
                :description="syntaxResult.message"
                show-icon
                :closable="false"
              />
            </el-form-item>
          </el-form>
        </div>

        <!-- Step 3: 字段元数据 -->
        <div v-show="currentStep === 2" class="step-panel">
          <div class="field-header">
            <el-button type="primary" size="small" @click="handleAddField">
              <el-icon><Plus /></el-icon>
              添加字段
            </el-button>
            <el-button type="success" size="small" @click="handleAutoFill">
              <el-icon><MagicStick /></el-icon>
              AI智能填充
            </el-button>
          </div>

          <el-table :data="fieldList" border stripe style="width: 100%">
            <el-table-column type="index" label="序号" width="60" />
            <el-table-column label="字段英文名" width="200">
              <template #default="{ row }">
                <el-input v-model="row.fieldName" placeholder="字段英文名" size="small" />
              </template>
            </el-table-column>
            <el-table-column label="字段中文名" width="200">
              <template #default="{ row }">
                <el-input v-model="row.fieldComment" placeholder="字段中文名" size="small" />
              </template>
            </el-table-column>
            <el-table-column label="字段类型" width="150">
              <template #default="{ row }">
                <el-input v-model="row.fieldType" placeholder="如：string, int" size="small" />
              </template>
            </el-table-column>
            <el-table-column label="是否主键" width="100">
              <template #default="{ row }">
                <el-checkbox v-model="row.isPrimaryKey" />
              </template>
            </el-table-column>
            <el-table-column label="是否分区键" width="100">
              <template #default="{ row }">
                <el-checkbox v-model="row.isPartitionKey" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="80" fixed="right">
              <template #default="{ row, $index }">
                <el-button link type="danger" size="small" @click="handleDeleteField($index)">
                  删除
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>

      <div class="step-footer">
        <el-button @click="handleCancel">取消</el-button>
        <el-button v-if="currentStep > 0" @click="handlePrev">上一步</el-button>
        <el-button v-if="currentStep < 2" type="primary" @click="handleNext">下一步</el-button>
        <el-button v-if="currentStep === 2" type="success" @click="handlePublish" :loading="publishing">
          <el-icon><Check /></el-icon>
          保存并发布
        </el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Check, Operation, Plus, MagicStick } from '@element-plus/icons-vue'
import { jobApi, jobVersionApi, fieldMetadataApi, datasourceApi, metadataApi, aiApi } from '@/api'

const route = useRoute()
const router = useRouter()

// 从路由参数读取作业类型与源数据源（只读）
const jobType = route.query.jobType || 'DB2HIVE'
const sourceDatasourceId = route.query.sourceDatasourceId ? Number(route.query.sourceDatasourceId) : null

const currentStep = ref(0)

// Step 1: 参数表单
const paramFormRef = ref(null)
const paramForm = reactive({
  jobCode: '',
  jobName: '',
  jobType,
  sourceDatasourceId,
  sourceDatabase: '',
  sourceTable: '',
  targetDatabase: '',
  targetTable: '',
  engineType: 'HIVE',
  description: '',
  scheduleFrequency: ''
})

const paramRules = {
  jobCode: [{ required: true, message: '请输入作业编码', trigger: 'blur' }],
  jobName: [{ required: true, message: '请输入作业名称', trigger: 'blur' }],
  sourceDatabase: [{ required: true, message: '请输入源数据库', trigger: 'blur' }],
  sourceTable: [{ required: true, message: '请输入源表名', trigger: 'blur' }],
  targetDatabase: [{ required: true, message: '请输入目标数据库', trigger: 'blur' }],
  targetTable: [{ required: true, message: '请输入目标表名', trigger: 'blur' }]
}

// 目标表名 = 作业英文名（自动同步）
watch(() => paramForm.jobCode, (val) => {
  paramForm.targetTable = val
})

// Step 2: SQL表单
const sqlForm = reactive({
  sqlContent: '',
  sparkConfig: '',
  changeLog: ''
})

const syntaxResult = ref(null)
const checking = ref(false)
const parsing = ref(false)

// Step 3: 字段列表
const fieldList = ref([])
const createdJobId = ref(null)
const createdVersionId = ref(null)

// 数据源名称（只读展示）
const datasourceName = ref('')

// 库列表、表列表、源表字段（三级联动，仅 DB2HIVE）
const databaseList = ref([])
const tableList = ref([])
const sourceTableFields = ref([])
const databaseLoading = ref(false)
const tableLoading = ref(false)

// 加载数据源名称
const loadDatasourceName = async () => {
  try {
    const list = await datasourceApi.getAllDatasources()
    const ds = list.find(d => d.id === sourceDatasourceId)
    if (ds) {
      datasourceName.value = `${ds.sourceName} (${ds.sourceCode})`
    }
  } catch (error) {
    console.error('加载数据源失败', error)
  }
}

// 加载库列表
const loadDatabases = async () => {
  if (!sourceDatasourceId) return

  databaseLoading.value = true
  try {
    databaseList.value = await metadataApi.getDatabases(sourceDatasourceId)
  } catch (error) {
    console.error('加载库列表失败', error)
    ElMessage.error('加载库列表失败')
  } finally {
    databaseLoading.value = false
  }
}

// 加载表列表
const loadTables = async () => {
  if (!sourceDatasourceId || !paramForm.sourceDatabase) {
    tableList.value = []
    return
  }

  tableLoading.value = true
  try {
    tableList.value = await metadataApi.getTables(sourceDatasourceId, paramForm.sourceDatabase)
  } catch (error) {
    console.error('加载表列表失败', error)
    ElMessage.error('加载表列表失败')
  } finally {
    tableLoading.value = false
  }
}

// 库变化时清空表并加载表
const handleDatabaseChange = () => {
  paramForm.sourceTable = ''
  tableList.value = []
  sourceTableFields.value = []
  loadTables()
}

// 表变化时展示字段
const handleTableChange = (tableName) => {
  const table = tableList.value.find(t => t.tableName === tableName)
  if (table && table.fields) {
    sourceTableFields.value = table.fields
  } else {
    sourceTableFields.value = []
  }
}

// 下一步
const handleNext = async () => {
  if (currentStep.value === 0) {
    const valid = await paramFormRef.value.validate().catch(() => false)
    if (!valid) return

    if (!createdJobId.value) {
      try {
        const job = await jobApi.createJob(paramForm)
        createdJobId.value = job.id
        createdVersionId.value = job.currentVersion
        ElMessage.success('作业创建成功')
      } catch (error) {
        return
      }
    }
  } else if (currentStep.value === 1) {
    if (!sqlForm.sqlContent.trim()) {
      ElMessage.warning('请输入SQL语句')
      return
    }
    if (!syntaxResult.value || !syntaxResult.value.valid) {
      ElMessage.warning('请先进行语法检查并确保通过')
      return
    }
    try {
      await jobVersionApi.configureSql(createdJobId.value, {
        sqlContent: sqlForm.sqlContent,
        sparkConfig: sqlForm.sparkConfig,
        changeLog: sqlForm.changeLog || '配置SQL'
      })
      ElMessage.success('SQL配置保存成功')
    } catch (error) {
      return
    }
  }

  currentStep.value++
}

// 上一步
const handlePrev = () => {
  currentStep.value--
}

// 语法检查
const handleCheckSyntax = async () => {
  if (!sqlForm.sqlContent.trim()) {
    ElMessage.warning('请先输入SQL语句')
    return
  }

  checking.value = true
  try {
    const result = await aiApi.checkSqlSyntax(sqlForm.sqlContent)
    syntaxResult.value = result
    if (result.valid) {
      ElMessage.success('语法检查通过')
    } else {
      ElMessage.error('语法检查失败')
    }
  } catch (error) {
    syntaxResult.value = { valid: false, message: '语法检查服务异常' }
  } finally {
    checking.value = false
  }
}

// 解析字段
const handleParseSql = async () => {
  if (!sqlForm.sqlContent.trim()) {
    ElMessage.warning('请先输入SQL语句')
    return
  }

  parsing.value = true
  try {
    const fields = await fieldMetadataApi.parseFieldsFromSql(sqlForm.sqlContent)
    fieldList.value = fields.map(fieldName => ({
      fieldName,
      fieldComment: '',
      fieldType: 'string',
      isPrimaryKey: false,
      isPartitionKey: false
    }))
    ElMessage.success(`解析成功，找到 ${fields.length} 个字段`)
  } catch (error) {
    ElMessage.error('字段解析失败')
  } finally {
    parsing.value = false
  }
}

// 添加字段
const handleAddField = () => {
  fieldList.value.push({
    fieldName: '',
    fieldComment: '',
    fieldType: 'string',
    isPrimaryKey: false,
    isPartitionKey: false
  })
}

// 删除字段
const handleDeleteField = (index) => {
  fieldList.value.splice(index, 1)
}

// AI智能填充
const handleAutoFill = async () => {
  if (!sqlForm.sqlContent.trim()) {
    ElMessage.warning('请先输入SQL语句')
    return
  }
  try {
    const metadata = await aiApi.generateFieldMetadata(sqlForm.sqlContent)
    fieldList.value = metadata
    ElMessage.success('AI填充成功')
  } catch (error) {
    ElMessage.error('AI填充失败')
  }
}

// 保存并发布
const publishing = ref(false)
const handlePublish = async () => {
  if (fieldList.value.length === 0) {
    ElMessage.warning('请至少添加一个字段')
    return
  }
  for (const field of fieldList.value) {
    if (!field.fieldName || !field.fieldComment) {
      ElMessage.warning('请完善所有字段的英文名和中文名')
      return
    }
  }

  publishing.value = true
  try {
    await fieldMetadataApi.saveFields(createdVersionId.value, fieldList.value)
    await jobVersionApi.publishVersion(createdVersionId.value)
    ElMessage.success('作业发布成功！')
    router.push('/job/list')
  } catch (error) {
    console.error('发布失败', error)
  } finally {
    publishing.value = false
  }
}

// 取消
const handleCancel = () => {
  router.push('/job/list')
}

onMounted(() => {
  if (jobType === 'DB2HIVE') {
    loadDatasourceName()
    loadDatabases()
  }
})
</script>

<style scoped>
.job-create-container {
  padding: 20px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 600;
}

.step-content {
  margin: 30px 0;
  min-height: 400px;
}

.step-panel {
  padding: 20px;
}

.field-header {
  margin-bottom: 15px;
  display: flex;
  gap: 10px;
}

.step-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

:deep(.el-step__title) {
  font-size: 14px;
}

:deep(.el-textarea__inner) {
  font-family: 'Consolas', 'Monaco', monospace;
}
</style>
