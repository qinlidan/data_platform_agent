import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import Layout from '@/components/Layout.vue'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { title: '登录', requiresAuth: false }
  },
  {
    path: '/',
    component: Layout,
    redirect: '/job/list',
    children: [
      {
        path: '/job/list',
        name: 'JobList',
        component: () => import('@/views/JobList.vue'),
        meta: { title: '作业列表' }
      },
      {
        path: '/job/create',
        name: 'JobCreate',
        component: () => import('@/views/JobCreate.vue'),
        meta: { title: '新建作业' }
      },
      {
        path: '/datasource',
        name: 'DatasourceList',
        component: () => import('@/views/DatasourceList.vue'),
        meta: { title: '数据源列表' }
      },
      {
        path: '/datasource/edit/:id',
        name: 'DatasourceEdit',
        component: () => import('@/views/DatasourceEdit.vue'),
        meta: { title: '编辑数据源' }
      },
      {
        path: '/knowledge',
        name: 'KnowledgeManagement',
        component: () => import('@/views/KnowledgeManagement.vue'),
        meta: { title: '知识库管理' }
      },
      {
        path: '/evaluation',
        name: 'RetrievalEvaluation',
        component: () => import('@/views/RetrievalEvaluation.vue'),
        meta: { title: '检索评估' }
      },
      {
        path: '/confidence/calibrate',
        name: 'ConfidenceCalibration',
        component: () => import('@/views/ConfidenceCalibration.vue'),
        meta: { title: '置信度阈值标定' }
      },
      {
        path: '/ai',
        name: 'AIAssistant',
        component: () => import('@/views/AIAssistant.vue'),
        meta: { title: 'AI 助手' }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 全局前置守卫：鉴权检查
router.beforeEach((to, from, next) => {
  // 设置页面标题
  if (to.meta.title) {
    document.title = `${to.meta.title} - 数据平台`
  }

  // 跳过所有鉴权，直接访问
  next()
})

export default router
