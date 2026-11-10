<template>
  <div class="login-container">
    <div class="login-card">
      <div class="login-header">
        <el-icon :size="48" color="#667eea"><Platform /></el-icon>
        <h1>数据研发平台</h1>
        <p class="subtitle">简历项目演示 · 快速体验</p>
      </div>

      <!-- 游客登录 - 暂时隐藏 -->
      <div v-if="false">
        <el-button
          type="primary"
          size="large"
          class="quick-btn guest-btn"
          :loading="guestLoading"
          @click="handleGuestLogin"
        >
          <el-icon><User /></el-icon>
          游客一键体验
        </el-button>
      </div>

      <!-- 管理员登录表单 - 暂时隐藏 -->
      <div v-if="false">
        <el-divider>或</el-divider>

        <el-alert
          type="success"
          :closable="false"
          class="demo-alert"
        >
          <template #title>
            <div class="alert-content">
              <el-icon><InfoFilled /></el-icon>
              <span>演示账号：<strong>admin</strong>，密码：<strong>280976</strong></span>
            </div>
          </template>
        </el-alert>

        <el-form
          ref="loginFormRef"
          :model="loginForm"
          :rules="loginRules"
          class="login-form"
        >
          <el-form-item prop="username">
            <el-input
              v-model="loginForm.username"
              placeholder="请输入用户名"
              size="large"
              prefix-icon="User"
            >
              <template #append>
                <el-button @click="autoFillCredentials">一键填入</el-button>
              </template>
            </el-input>
          </el-form-item>

          <el-form-item prop="password">
            <el-input
              v-model="loginForm.password"
              type="password"
              placeholder="请输入密码"
              size="large"
              prefix-icon="Lock"
              show-password
              @keyup.enter="handleAdminLogin"
            />
          </el-form-item>

          <el-button
            type="success"
            size="large"
            class="quick-btn admin-btn"
            :loading="adminLoading"
            @click="handleAdminLogin"
          >
            <el-icon><Unlock /></el-icon>
            管理员登录
          </el-button>
        </el-form>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Platform, User, Unlock, InfoFilled } from '@element-plus/icons-vue'
import request from '@/utils/request'

const router = useRouter()

// 表单数据
const loginForm = reactive({
  username: '',
  password: ''
})

// 表单验证规则
const loginRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码长度至少6位', trigger: 'blur' }
  ]
}

const loginFormRef = ref(null)
const adminLoading = ref(false)
const guestLoading = ref(false)

// 一键填入演示账号
const autoFillCredentials = () => {
  loginForm.username = 'admin'
  loginForm.password = '280976'
  ElMessage.success('已自动填入演示账号')
}

// 游客登录
const handleGuestLogin = async () => {
  try {
    guestLoading.value = true

    const response = await request.post('/auth/guest-login')

    // 存储 token 和角色（使用后端返回的数据）
    localStorage.setItem('token', response.token)
    localStorage.setItem('role', response.role || 'guest')
    localStorage.setItem('username', response.username || '游客')

    ElMessage.success('欢迎游客体验！')
    router.push('/job/list')
  } catch (error) {
    console.error('游客登录失败:', error)
    ElMessage.error(error.message || '游客登录失败')
  } finally {
    guestLoading.value = false
  }
}

// 管理员登录
const handleAdminLogin = async () => {
  if (!loginFormRef.value) return

  await loginFormRef.value.validate(async (valid) => {
    if (!valid) return

    try {
      adminLoading.value = true

      const response = await request.post('/auth/login', {
        username: loginForm.username,
        password: loginForm.password
      })

      // 存储 token 和角色（使用后端返回的数据）
      localStorage.setItem('token', response.token)
      localStorage.setItem('role', response.role || 'user')
      localStorage.setItem('username', response.username || loginForm.username)

      ElMessage.success('登录成功！')
      router.push('/job/list')
    } catch (error) {
      console.error('管理员登录失败:', error)
      ElMessage.error(error.message || '登录失败，请检查用户名和密码')
    } finally {
      adminLoading.value = false
    }
  })
}
</script>

<style scoped>
.login-container {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  padding: 20px;
}

.login-card {
  width: 100%;
  max-width: 440px;
  background: #ffffff;
  border-radius: 16px;
  padding: 40px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
}

.login-header {
  text-align: center;
  margin-bottom: 32px;
}

.login-header h1 {
  font-size: 28px;
  font-weight: 600;
  color: #303133;
  margin: 16px 0 8px;
}

.subtitle {
  font-size: 14px;
  color: #909399;
  margin: 0;
}

.demo-alert {
  margin-bottom: 24px;
  border-radius: 8px;
}

.alert-content {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
}

.alert-content strong {
  color: #67c23a;
  font-weight: 600;
}

.quick-btn {
  width: 100%;
  height: 48px;
  font-size: 16px;
  font-weight: 500;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.guest-btn {
  margin-bottom: 16px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border: none;
}

.guest-btn:hover {
  opacity: 0.9;
}

.admin-btn {
  margin-top: 8px;
}

.el-divider {
  margin: 24px 0;
  color: #dcdfe6;
}

.login-form {
  margin-bottom: 0;
}

.login-form .el-form-item {
  margin-bottom: 20px;
}

.login-footer {
  margin-top: 24px;
  text-align: center;
  padding-top: 20px;
  border-top: 1px solid #f0f0f0;
}

.login-footer p {
  font-size: 13px;
  color: #909399;
  margin: 0;
}
</style>
