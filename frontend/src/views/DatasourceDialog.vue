<template>
  <el-dialog
    v-model="visible"
    :title="isEdit ? '编辑数据源' : '新增数据源'"
    width="560px"
    :close-on-click-modal="false"
    @close="handleClose"
  >
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="130px"
      label-position="right"
    >
      <el-form-item label="数据源名称" prop="sourceName">
        <el-input
          v-model="form.sourceName"
          placeholder="数据源名称"
          clearable
        />
      </el-form-item>

      <el-form-item label="驱动" prop="driver">
        <el-select
          v-model="form.driver"
          placeholder="请选择"
          clearable
          style="width: 100%"
        >
          <el-option
            v-for="item in driverOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>

      <el-form-item label="数据源URL" prop="jdbcUrl">
        <el-input
          v-model="form.jdbcUrl"
          placeholder="数据源URL"
          clearable
        />
      </el-form-item>

      <el-form-item label="用户名" prop="username">
        <el-input
          v-model="form.username"
          placeholder="用户名"
          clearable
        />
      </el-form-item>

      <el-form-item label="密码" prop="password">
        <el-input
          v-model="form.password"
          type="password"
          placeholder="密码"
          show-password
          clearable
        />
      </el-form-item>

      <el-form-item label="数据源类型" prop="sourceType">
        <el-select
          v-model="form.sourceType"
          placeholder="请选择"
          style="width: 100%"
        >
          <el-option
            v-for="item in sourceTypeOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>

      <el-form-item label="数据源配置信息" prop="configInfo">
        <el-select
          v-model="form.configInfo"
          placeholder="请选择"
          style="width: 100%"
        >
          <el-option
            v-for="item in configOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
    </el-form>

    <template #footer>
      <div class="dialog-footer">
        <el-button type="primary" :loading="testing" @click="handleTestConnection">
          测试连接
        </el-button>
        <el-button @click="handleClose">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">
          保存
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { datasourceApi } from '@/api'

const props = defineProps({
  modelValue: Boolean,
  datasourceId: Number
})

const emit = defineEmits(['update:modelValue', 'success'])

const visible = ref(false)
const isEdit = ref(false)
const testing = ref(false)
const saving = ref(false)
const formRef = ref(null)

// 驱动下拉选项
const driverOptions = [
  { label: 'com.mysql.cj.jdbc.Driver', value: 'com.mysql.cj.jdbc.Driver' },
  { label: 'com.ibm.db2.jcc.DB2Driver', value: 'com.ibm.db2.jcc.DB2Driver' },
  { label: 'oracle.jdbc.OracleDriver', value: 'oracle.jdbc.OracleDriver' },
  { label: 'org.apache.hive.jdbc.HiveDriver', value: 'org.apache.hive.jdbc.HiveDriver' },
  { label: 'org.postgresql.Driver', value: 'org.postgresql.Driver' },
  { label: 'org.apache.hadoop.hbase.client.Connection', value: 'org.apache.hadoop.hbase.client.Connection' }
]

// 数据源类型下拉选项（与后端 DataSourceType 枚举 code 对应）
const sourceTypeOptions = [
  { label: 'HBase', value: 'HBASE' },
  { label: 'MySQL', value: 'MYSQL' },
  { label: 'Hive', value: 'HIVE' },
  { label: 'Oracle', value: 'ORACLE' },
  { label: 'DB2', value: 'DB2' },
  { label: 'PostgreSQL', value: 'POSTGRESQL' },
  { label: 'SQL Server', value: 'SQLSERVER' },
  { label: 'ClickHouse', value: 'CLICKHOUSE' }
]

// 数据源配置信息下拉选项
const configOptions = [
  { label: 'hbase', value: 'hbase' },
  { label: 'hbase-secure', value: 'hbase-secure' },
  { label: 'hbase-default', value: 'hbase-default' }
]

// 表单默认值
const createDefaultForm = () => ({
  sourceName: 'hbase1',
  driver: '',
  jdbcUrl: '',
  username: '',
  password: '',
  sourceType: 'HBASE',
  configInfo: 'hbase'
})

const form = reactive(createDefaultForm())

// 校验规则：数据源名称、数据源类型必填
const rules = {
  sourceName: [{ required: true, message: '请输入数据源名称', trigger: 'blur' }],
  sourceType: [{ required: true, message: '请选择数据源类型', trigger: 'change' }]
}

// 监听弹窗显示
watch(
  () => props.modelValue,
  (val) => {
    visible.value = val
    if (val) {
      if (props.datasourceId) {
        isEdit.value = true
        loadDetail(props.datasourceId)
      } else {
        isEdit.value = false
        resetForm()
      }
    }
  }
)

watch(visible, (val) => {
  emit('update:modelValue', val)
})

// 加载数据源详情（编辑场景）
const loadDetail = async (id) => {
  try {
    const data = await datasourceApi.getDatasourceById(id)
    Object.assign(form, {
      sourceName: data.sourceName,
      driver: data.driver,
      jdbcUrl: data.jdbcUrl,
      username: data.username,
      password: data.password,
      sourceType: data.sourceType,
      configInfo: data.configInfo
    })
  } catch (error) {
    console.error('加载数据源详情失败', error)
  }
}

// 重置表单
const resetForm = () => {
  Object.assign(form, createDefaultForm())
  formRef.value?.clearValidate()
}

// 生成数据源编码（后端必填，表单未提供，按名称自动生成）
const generateSourceCode = () => {
  const base = (form.sourceName || form.sourceType || 'DS')
    .replace(/[^a-zA-Z0-9]/g, '')
    .toUpperCase()
  return `${base || 'DS'}_${Date.now()}`
}

// 测试连接
const handleTestConnection = async () => {
  try {
    await formRef.value.validate()
  } catch (error) {
    ElMessage.warning('请先完善必填信息')
    return
  }

  testing.value = true
  try {
    const success = await datasourceApi.testConnectionByParams({
      sourceCode: generateSourceCode(),
      sourceName: form.sourceName,
      sourceType: form.sourceType,
      driver: form.driver,
      jdbcUrl: form.jdbcUrl,
      configInfo: form.configInfo,
      username: form.username,
      password: form.password
    })

    if (success) {
      ElMessage.success('连接成功')
    } else {
      ElMessage.error('连接失败，请检查配置信息')
    }
  } catch (error) {
    ElMessage.error('连接失败：' + (error.message || '未知错误'))
  } finally {
    testing.value = false
  }
}

// 保存
const handleSave = async () => {
  try {
    await formRef.value.validate()
  } catch (error) {
    ElMessage.warning('请完善必填信息')
    return
  }

  saving.value = true
  try {
    const payload = {
      sourceCode: generateSourceCode(),
      sourceName: form.sourceName,
      sourceType: form.sourceType,
      driver: form.driver,
      jdbcUrl: form.jdbcUrl,
      configInfo: form.configInfo,
      username: form.username,
      password: form.password
    }

    if (isEdit.value) {
      await datasourceApi.updateDatasource(props.datasourceId, payload)
    } else {
      await datasourceApi.createDatasource(payload)
    }

    ElMessage.success(isEdit.value ? '修改成功' : '保存成功')
    emit('success')
    handleClose()
  } catch (error) {
    console.error('保存数据源失败', error)
  } finally {
    saving.value = false
  }
}

// 关闭弹窗（X 与取消行为一致：关闭并清空）
const handleClose = () => {
  visible.value = false
  resetForm()
}
</script>

<style scoped>
.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
</style>
