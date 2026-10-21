<template>
  <div class="datasource-edit-container">
    <el-card class="edit-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span>编辑数据源</span>
        </div>
      </template>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="130px"
        v-loading="loading"
      >
        <el-form-item label="数据源名称" prop="sourceName">
          <el-input v-model="form.sourceName" placeholder="数据源名称" clearable />
        </el-form-item>

        <el-form-item label="驱动" prop="driver">
          <el-select v-model="form.driver" placeholder="请选择" clearable style="width: 100%">
            <el-option
              v-for="item in driverOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="数据源URL" prop="jdbcUrl">
          <el-input v-model="form.jdbcUrl" placeholder="数据源URL" clearable />
        </el-form-item>

        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="用户名" clearable />
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" placeholder="密码" show-password clearable />
        </el-form-item>

        <el-form-item label="数据源类型" prop="sourceType">
          <el-select v-model="form.sourceType" placeholder="请选择" style="width: 100%">
            <el-option
              v-for="item in sourceTypeOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="数据源配置信息" prop="configInfo">
          <el-select v-model="form.configInfo" placeholder="请选择" style="width: 100%">
            <el-option
              v-for="item in configOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :loading="testing" @click="handleTestConnection">
            测试连接
          </el-button>
          <el-button @click="handleCancel">取消</el-button>
          <el-button type="primary" :loading="saving" @click="handleSave">
            保存
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { datasourceApi } from '@/api'

const route = useRoute()
const router = useRouter()

const formRef = ref(null)
const loading = ref(false)
const testing = ref(false)
const saving = ref(false)

const driverOptions = [
  { label: 'com.mysql.cj.jdbc.Driver', value: 'com.mysql.cj.jdbc.Driver' },
  { label: 'com.ibm.db2.jcc.DB2Driver', value: 'com.ibm.db2.jcc.DB2Driver' },
  { label: 'oracle.jdbc.OracleDriver', value: 'oracle.jdbc.OracleDriver' },
  { label: 'org.apache.hive.jdbc.HiveDriver', value: 'org.apache.hive.jdbc.HiveDriver' },
  { label: 'org.postgresql.Driver', value: 'org.postgresql.Driver' },
  { label: 'org.apache.hadoop.hbase.client.Connection', value: 'org.apache.hadoop.hbase.client.Connection' }
]

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

const configOptions = [
  { label: 'hbase', value: 'hbase' },
  { label: 'hbase-secure', value: 'hbase-secure' },
  { label: 'hbase-default', value: 'hbase-default' }
]

const form = reactive({
  sourceCode: '',
  sourceName: '',
  driver: '',
  jdbcUrl: '',
  username: '',
  password: '',
  sourceType: 'HBASE',
  configInfo: 'hbase'
})

const rules = {
  sourceName: [{ required: true, message: '请输入数据源名称', trigger: 'blur' }],
  sourceType: [{ required: true, message: '请选择数据源类型', trigger: 'change' }]
}

// 加载详情
const loadDetail = async () => {
  const id = route.params.id
  if (!id) return

  loading.value = true
  try {
    const data = await datasourceApi.getDatasourceById(id)
    Object.assign(form, {
      sourceCode: data.sourceCode,
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
  } finally {
    loading.value = false
  }
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
    const success = await datasourceApi.testConnectionByParams({ ...form })
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
    await datasourceApi.updateDatasource(route.params.id, { ...form })
    ElMessage.success('保存成功')
    router.push('/datasource')
  } catch (error) {
    console.error('保存数据源失败', error)
  } finally {
    saving.value = false
  }
}

// 取消
const handleCancel = () => {
  router.push('/datasource')
}

onMounted(() => {
  loadDetail()
})
</script>

<style scoped>
.datasource-edit-container {
  padding: 20px;
  min-height: 100%;
  box-sizing: border-box;
}

.edit-card {
  width: 100%;
}

.card-header {
  font-weight: 600;
}
</style>
