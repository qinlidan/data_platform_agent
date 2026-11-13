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
      <div class="header-right">
        <el-dropdown @command="handleUserCommand">
          <div class="user-info">
            <el-avatar :size="32" style="background-color: #fff; color: #667eea;">
              <el-icon><User /></el-icon>
            </el-avatar>
            <span class="username">{{ username }}</span>
            <el-tag v-if="role === 'guest'" type="info" size="small" style="margin-left: 8px;">游客</el-tag>
            <el-tag v-if="role === 'admin'" type="success" size="small" style="margin-left: 8px;">管理员</el-tag>
          </div>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="logout">
                <el-icon><SwitchButton /></el-icon>
                退出登录
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </el-header>

    <!-- 主体：内容在左，目录在右 -->
    <div class="layout-body">
      <el-aside class="app-aside" width="220px">
        <el-menu
          :default-active="activeMenu"
          mode="vertical"
          :ellipsis="false"
          @select="handleMenuSelect"
          class="sidebar-menu"
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
            <el-tag type="success" size="small" class="menu-badge">Beta</el-tag>
          </el-menu-item>
          <el-menu-item index="/knowledge">
            <el-icon><Reading /></el-icon>
            <span>知识库管理</span>
          </el-menu-item>
          <el-menu-item index="/evaluation">
            <el-icon><DataAnalysis /></el-icon>
            <span>检索评估</span>
          </el-menu-item>
          <el-menu-item index="/confidence/calibrate">
            <el-icon><Histogram /></el-icon>
            <span>阈值标定</span>
          </el-menu-item>
        </el-menu>
      </el-aside>

      <el-main class="app-main">
        <router-view v-slot="{ Component }">
          <keep-alive :include="['JobList', 'AIAssistant']">
            <component :is="Component" />
          </keep-alive>
        </router-view>
      </el-main>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Platform, List, Connection, ChatDotRound, Reading, DataAnalysis, Histogram, User, SwitchButton } from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()

const username = ref('')
const role = ref('')

// 加载用户信息
onMounted(() => {
  username.value = localStorage.getItem('username') || '未知用户'
  role.value = localStorage.getItem('role') || ''
})

const activeMenu = computed(() => route.path)

const handleMenuSelect = (index) => {
  if (index !== route.path) {
    router.push(index)
  }
}

// 用户操作菜单
const handleUserCommand = async (command) => {
  if (command === 'logout') {
    try {
      await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      })

      // 清除本地存储
      localStorage.removeItem('token')
      localStorage.removeItem('role')
      localStorage.removeItem('username')

      ElMessage.success('已退出登录')
      router.push('/login')
    } catch (error) {
      // 用户取消操作
    }
  }
}
</script>

<style scoped>
.layout-container {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background-color: #f5f7fa;
}

.app-header {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-bottom: none;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  height: 64px;
}

.header-left {
  display: flex;
  align-items: center;
}

.header-right {
  display: flex;
  align-items: center;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  padding: 8px 12px;
  border-radius: 8px;
  transition: background-color 0.3s;
}

.user-info:hover {
  background-color: rgba(255, 255, 255, 0.1);
}

.username {
  color: #ffffff;
  font-size: 14px;
  font-weight: 500;
}

.app-title {
  font-size: 22px;
  font-weight: 600;
  color: #ffffff;
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 0;
  letter-spacing: 0.5px;
}

.app-title .el-icon {
  font-size: 28px;
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
  width: 220px;
  flex-shrink: 0;
  background-color: #ffffff;
  border-right: 1px solid #e4e7ed;
  display: flex;
  flex-direction: column;
  box-shadow: 2px 0 8px rgba(0, 0, 0, 0.04);
}

/* 菜单样式优化 */
.sidebar-menu {
  border-right: none;
  padding: 12px 8px;
}

.sidebar-menu .el-menu-item {
  height: 48px;
  line-height: 48px;
  margin: 4px 0;
  border-radius: 8px;
  transition: all 0.3s ease;
  font-size: 14px;
  font-weight: 500;
  color: #606266;
}

.sidebar-menu .el-menu-item .el-icon {
  font-size: 18px;
  margin-right: 12px;
  transition: all 0.3s ease;
}

.sidebar-menu .el-menu-item:hover {
  background-color: #f5f7fa;
  color: #409eff;
  transform: translateX(2px);
}

.sidebar-menu .el-menu-item.is-active {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: #ffffff;
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.3);
}

.sidebar-menu .el-menu-item.is-active .el-icon {
  color: #ffffff;
}

/* Beta 徽章样式 */
.menu-badge {
  margin-left: 8px;
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 600;
}
</style>
