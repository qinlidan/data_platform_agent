<template>
  <el-dialog
    v-model="visible"
    title="选择作业类型"
    width="480px"
    :close-on-click-modal="false"
    @close="handleClose"
  >
    <el-form label-width="90px">
      <el-form-item label="作业类型">
        <el-radio-group v-model="jobType">
          <el-radio value="DB2HIVE">DB2HIVE（数据库到Hive）</el-radio>
          <el-radio value="HIVE2HIVE">HIVE2HIVE（Hive到Hive）</el-radio>
        </el-radio-group>
      </el-form-item>

      <el-form-item v-if="jobType === 'DB2HIVE'" label="源数据源">
        <el-select
          v-model="sourceDatasourceId"
          placeholder="请选择数据源"
          style="width: 100%"
        >
          <el-option
            v-for="ds in datasourceList"
            :key="ds.id"
            :label="`${ds.sourceName} (${ds.sourceCode})`"
            :value="ds.id"
          />
        </el-select>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" @click="handleNext">下一步</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { datasourceApi } from '@/api'

const props = defineProps({
  modelValue: Boolean
})

const emit = defineEmits(['update:modelValue', 'confirm'])

const visible = ref(false)
const jobType = ref('DB2HIVE')
const sourceDatasourceId = ref(null)
const datasourceList = ref([])

watch(() => props.modelValue, (val) => {
  visible.value = val
  if (val) {
    reset()
    loadDatasources()
  }
})

watch(visible, (val) => {
  emit('update:modelValue', val)
})

const reset = () => {
  jobType.value = 'DB2HIVE'
  sourceDatasourceId.value = null
}

const loadDatasources = async () => {
  try {
    datasourceList.value = await datasourceApi.getAllDatasources()
  } catch (error) {
    console.error('加载数据源失败', error)
  }
}

const handleNext = () => {
  if (jobType.value === 'DB2HIVE' && !sourceDatasourceId.value) {
    ElMessage.warning('请选择源数据源')
    return
  }

  emit('confirm', {
    jobType: jobType.value,
    sourceDatasourceId: jobType.value === 'DB2HIVE' ? sourceDatasourceId.value : null
  })
  handleClose()
}

const handleClose = () => {
  visible.value = false
}
</script>
