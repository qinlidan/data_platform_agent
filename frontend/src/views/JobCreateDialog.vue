<template>
  <el-dialog
    v-model="visible"
    :title="isEdit ? '编辑作业' : '新建作业'"
    width="900px"
    :close-on-click-modal="false"
    @close="handleClose"
  >
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
          <el-form-item label="作业编码" prop="jobCode">
            <el-input
              v-model="paramForm.jobCode"
              placeholder="英文字母数字下划线，如：sync_user_data"
              :disabled="isEdit"
            />
          </el-form-item>

          <el-form-item label="作业名称" prop="jobName">
            <el-input
              v-model="paramForm.jobName"
              placeholder="请输入作业名称"
            />
          </el-form-item>

          <el-form-item label="作业类型" prop="jobType">
            <el-select
              v-model="paramForm.jobType"
              placeholder="请选择"
              style="width: 100%"
              :disabled="isEdit"
            >
              <el-option label="DB2HIVE - 数据库到Hive" value="DB2HIVE" />
              <el-option label="HIVE2HIVE - Hive到Hive" value="HIVE2HIVE" />
            </el-select>
          </el-form-item>

          <el-form-item
            label="源数据源"
            prop="sourceDatasourceId"
            v-if="paramForm.jobType === 'DB2HIVE'"
          >
            <el-select
              v-model="paramForm.sourceDatasourceId"
              placeholder="请选择数据源"
              style="width: 100%"
              @change="handleDatasourceChange"
            >
              <el-option
                v-for="ds in datasourceList"
                :key="ds.id"
                :label="`${ds.sourceName} (${ds.sourceCode})`"
                :value="ds.id"
              />
            </el-select>
          </el-form-item>

          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="源数据库" prop="sourceDatabase">
                <el-select
                  v-model="paramForm.sourceDatabase"
                  placeholder="请选择数据库"
                  style="width: 100%"
                  :loading="databaseLoading"
                  :disabled="paramForm.jobType === 'HIVE2HIVE' || !paramForm.sourceDatasourceId"
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
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="源表名" prop="sourceTable">
                <el-select
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
              </el-form-item>
            </el-col>
          </el-row>

          <!-- 源表字段展示 -->
          <el-form-item v-if="sourceTableFields.length > 0" label="源表字段">
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
                <el-input
                  v-model="paramForm.targetDatabase"
                  placeholder="请输入目标Hive数据库"
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="目标表名" prop="targetTable">
                <el-input
                  v-model="paramForm.targetTable"
                  placeholder="请输入目标Hive表名"
                />
              </el-form-item>
            </el-col>
          </el-row>

          <el-form-item label="执行引擎" prop="engineType">
            <el-radio-group v-model="paramForm.engineType">
              <el-radio value="HIVE">Hive</el-radio>
              <el-radio value="SPARK">Spark</el-radio>
            </el-radio-group>
          </el-form-item>

          <el-form-item label="作业描述">
            <el-input
              v-model="paramForm.description"
              type="textarea"
              :rows="3"
              placeholder="请输入作业描述"
            />
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
            <template #default="{ row, $index }">
              <el-input
                v-model="row.fieldName"
                placeholder="字段英文名"
                size="small"
              />
            </template>
          </el-table-column>
          <el-table-column label="字段中文名" width="200">
            <template #default="{ row, $index }">
              <el-input
                v-model="row.fieldComment"
                placeholder="字段中文名"
                size="small"
              />
            </template>
          </el-table-column>
          <el-table-column label="字段类型" width="150">
            <template #default="{ row, $index }">
              <el-input
                v-model="row.fieldType"
                placeholder="如：string, int"
                size="small"
              />
            </template>
          </el-table-column>
          <el-table-column label="是否主键" width="100">
            <template #default="{ row, $index }">
              <el-checkbox v-model="row.isPrimaryKey" />
            </template>
          </el-table-column>
          <el-table-column label="是否分区键" width="100">
            <template #default="{ row, $index }">
              <el-checkbox v-model="row.isPartitionKey" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="80" fixed="right">
            <template #default="{ row, $index }">
              <el-button
                link
                type="danger"
                size="small"
                @click="handleDeleteField($index)"
              >
                删除
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <template #footer>
      <div class="dialog-footer">
        <el-button @click="handleClose">取消</el-button>
        <el-button v-if="currentStep > 0" @click="handlePrev">上一步</el-button>
        <el-button
          v-if="currentStep < 2"
          type="primary"
          @click="handleNext"
        >
          下一步
        </el-button>
        <el-button
          v-if="currentStep === 2"
          type="success"
          @click="handlePublish"
          :loading="publishing"
        >
          <el-icon><Check /></el-icon>
          保存并发布
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, watch, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { Check, Operation, Plus, MagicStick } from '@element-plus/icons-vue'
import { jobApi, jobVersionApi, fieldMetadataApi, datasourceApi, metadataApi, aiApi } from '@/api'

const props = defineProps({
  modelValue: Boolean,
  jobId: Number
})

const emit = defineEmits(['update:modelValue', 'success'])

const visible = ref(false)
const isEdit = ref(false)
const currentStep = ref(0)

// Step 1: 参数表单
const paramFormRef = ref(null)
const paramForm = reactive({
  jobCode: '',
  jobName: '',
  jobType: 'DB2HIVE',
  sourceDatasourceId: null,
  sourceDatabase: '',
  sourceTable: '',
  targetDatabase: '',
  targetTable: '',
  engineType: 'HIVE',
  description: ''
})

const paramRules = {
  jobCode: [{ required: true, message: '请输入作业编码', trigger: 'blur' }],
  jobName: [{ required: true, message: '请输入作业名称', trigger: 'blur' }],
  jobType: [{ required: true, message: '请选择作业类型', trigger: 'change' }],
  sourceDatabase: [{ required: true, message: '请选择源数据库', trigger: 'change' }],
  sourceTable: [{ required: true, message: '请选择源表名', trigger: 'change' }],
  targetDatabase: [{ required: true, message: '请输入目标数据库', trigger: 'blur' }],
  targetTable: [{ required: true, message: '请输入目标表名', trigger: 'blur' }]
}

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

// 数据源列表
const datasourceList = ref([])

// 库列表、表列表、源表字段（三级联动）
const databaseList = ref([])
const tableList = ref([])
const sourceTableFields = ref([])
const databaseLoading = ref(false)
const tableLoading = ref(false)

// 加载数据源
const loadDatasources = async () => {
  try {
    const res = await datasourceApi.getAllDatasources()
    datasourceList.value = res
  } catch (error) {
    console.error('加载数据源失败', error)
  }
}

// 加载库列表
const loadDatabases = async () => {
  if (!paramForm.sourceDatasourceId) {
    databaseList.value = []
    return
  }

  databaseLoading.value = true
  try {
    const res = await metadataApi.getDatabases(paramForm.sourceDatasourceId)
    databaseList.value = res
  } catch (error) {
    console.error('加载库列表失败', error)
    ElMessage.error('加载库列表失败')
  } finally {
    databaseLoading.value = false
  }
}

// 加载表列表
const loadTables = async () => {
  if (!paramForm.sourceDatasourceId || !paramForm.sourceDatabase) {
    tableList.value = []
    return
  }

  tableLoading.value = true
  try {
    const res = await metadataApi.getTables(paramForm.sourceDatasourceId, paramForm.sourceDatabase)
    tableList.value = res
  } catch (error) {
    console.error('加载表列表失败', error)
    ElMessage.error('加载表列表失败')
  } finally {
    tableLoading.value = false
  }
}

// 数据源变化时清空下游并加载库
const handleDatasourceChange = () => {
  paramForm.sourceDatabase = ''
  paramForm.sourceTable = ''
  databaseList.value = []
  tableList.value = []
  sourceTableFields.value = []
  loadDatabases()
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

// 监听对话框显示
watch(() => props.modelValue, (val) => {
  visible.value = val
  if (val) {
    loadDatasources()
    if (props.jobId) {
      isEdit.value = true
      loadJobDetail()
    } else {
      isEdit.value = false
      resetForm()
    }
  }
})

watch(visible, (val) => {
  emit('update:modelValue', val)
})

// 加载作业详情
const loadJobDetail = async () => {
  // TODO: 实现编辑功能
}

// 重置表单
const resetForm = () => {
  currentStep.value = 0
  Object.assign(paramForm, {
    jobCode: '',
    jobName: '',
    jobType: 'DB2HIVE',
    sourceDatasourceId: null,
    sourceDatabase: '',
    sourceTable: '',
    targetDatabase: '',
    targetTable: '',
    engineType: 'HIVE',
    description: ''
  })
  sqlForm.sqlContent = ''
  sqlForm.sparkConfig = ''
  sqlForm.changeLog = ''
  fieldList.value = []
  syntaxResult.value = null
  createdJobId.value = null
  createdVersionId.value = null

  // 清空三级联动数据
  databaseList.value = []
  tableList.value = []
  sourceTableFields.value = []
}

// 下一步
const handleNext = async () => {
  if (currentStep.value === 0) {
    // 验证参数表单
    const valid = await paramFormRef.value.validate().catch(() => false)
    if (!valid) return

    // 创建作业
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
    // 验证SQL
    if (!sqlForm.sqlContent.trim()) {
      ElMessage.warning('请输入SQL语句')
      return
    }

    if (!syntaxResult.value || !syntaxResult.value.valid) {
      ElMessage.warning('请先进行语法检查并确保通过')
      return
    }

    // 保存SQL配置
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
    syntaxResult.value = {
      valid: false,
      message: '语法检查服务异常'
    }
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

    // 转换为表格数据
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

  // 验证字段
  for (const field of fieldList.value) {
    if (!field.fieldName || !field.fieldComment) {
      ElMessage.warning('请完善所有字段的英文名和中文名')
      return
    }
  }

  publishing.value = true
  try {
    // 保存字段元数据
    await fieldMetadataApi.saveFields(createdVersionId.value, fieldList.value)

    // 发布版本
    await jobVersionApi.publishVersion(createdVersionId.value)

    ElMessage.success('作业发布成功！')
    emit('success')
    handleClose()
  } catch (error) {
    console.error('发布失败', error)
  } finally {
    publishing.value = false
  }
}

// 关闭对话框
const handleClose = () => {
  visible.value = false
  resetForm()
}
</script>

<style scoped>
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

.dialog-footer {
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
