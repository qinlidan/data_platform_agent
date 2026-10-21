import { createRouter, createWebHistory } from 'vue-router'
import Layout from '@/components/Layout.vue'

const routes = [
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
        path: '/ai',
        name: 'AiAssistant',
        component: () => import('@/views/AiAssistant.vue'),
        meta: { title: 'AI 智能助手' }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
