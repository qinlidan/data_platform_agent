<template>
  <div class="test-container">
    <h2>ECharts 测试页面</h2>
    <button @click="testChart">测试图表渲染</button>
    <div ref="chartRef" style="width: 800px; height: 400px; border: 1px solid #ccc; margin-top: 20px;"></div>
  </div>
</template>

<script setup>
import { ref, nextTick } from 'vue'
import * as echarts from 'echarts'

const chartRef = ref(null)
let chart = null

async function testChart() {
  console.log('testChart called')
  console.log('chartRef.value:', chartRef.value)

  await nextTick()

  if (!chartRef.value) {
    console.error('chartRef is null!')
    return
  }

  if (chart) {
    chart.dispose()
  }

  chart = echarts.init(chartRef.value)
  console.log('chart instance:', chart)

  const option = {
    title: { text: 'ECharts 测试' },
    xAxis: {
      type: 'category',
      data: ['A', 'B', 'C', 'D']
    },
    yAxis: {
      type: 'value'
    },
    series: [{
      data: [10, 20, 15, 25],
      type: 'line'
    }]
  }

  chart.setOption(option)
  console.log('Chart rendered')
}
</script>

<style scoped>
.test-container {
  padding: 20px;
}
</style>
