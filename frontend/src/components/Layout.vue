<template>
  <div class="layout-container">
    <!-- 顶部栏 -->
    <el-header class="app-header">
      <div class="header-left">
        <h1 class="app-title">
          <el-icon><Platform /></el-icon>
          数据平台
        </h1>
      </div>
    </el-header>

    <!-- 主体：内容在左，目录在右 -->
    <div class="layout-body">
      <el-aside class="app-aside" width="200px">
        <el-menu
          :default-active="activeMenu"
          mode="vertical"
          :ellipsis="false"
          @select="handleMenuSelect"
        >
          <el-menu-item index="/job/list">
            <el-icon><List /></el-icon>
            <span>作业管理</span>
          </el-menu-item>
          <el-menu-item index="/datasource">
            <el-icon><Connection /></el-icon>
            <span>数据源</span>
          </el-menu-item>
          <el-menu-item index="/ai">
            <el-icon><ChatDotRound /></el-icon>
            <span>AI 助手</span>
            <el-tag type="success" size="small" style="margin-left: 6px;">Beta</el-tag>
          </el-menu-item>
        </el-menu>
      </el-aside>

      <el-main class="app-main">
        <router-view />
      </el-main>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { Platform, List, Connection, ChatDotRound } from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()

const activeMenu = computed(() => route.path)

const handleMenuSelect = (index) => {
  if (index !== route.path) {
    router.push(index)
  }
}
</script>

<style scoped>
.layout-container {
  height: 100vh;
  display: flex;
  flex-direction: column;
}

.app-header {
  background-color: #fff;
  border-bottom: 1px solid #e4e7ed;
  display: flex;
  align-items: center;
  padding: 0 24px;
  height: 60px;
}

.header-left {
  display: flex;
  align-items: center;
}

.app-title {
  font-size: 20px;
  font-weight: 600;
  color: #303133;
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 0;
}

.layout-body {
  flex: 1;
  display: flex;
  overflow: hidden;
}

.app-main {
  flex: 1;
  background-color: #f5f7fa;
  padding: 0;
  overflow-y: auto;
}

.app-aside {
  width: 200px;
  flex-shrink: 0;
  background-color: #fff;
  border-right: 1px solid #e4e7ed;
  display: flex;
  flex-direction: column;
}
</style>
