/**
 * 路由配置
 * 包含路由守卫：未登录跳转到 /login
 */
import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/LoginView.vue'),
    meta: { requiresAuth: false }
  },
  {
    path: '/',
    component: () => import('@/layouts/BasicLayout.vue'),
    meta: { requiresAuth: true },
    children: [
      {
        path: '',
        name: 'Dashboard',
        component: () => import('@/views/login/LoginView.vue'),
        meta: { requiresAuth: true }
      },
      {
        path: '/owners/audit',
        name: 'OwnerAudit',
        component: () => import('@/views/owner/OwnerAuditView.vue'),
        meta: { requiresAuth: true }
      },
      {
        path: '/vehicles',
        name: 'VehicleList',
        component: () => import('@/views/vehicle/VehicleListView.vue'),
        meta: { requiresAuth: true }
      },
      {
        path: '/visitors/audit',
        name: 'VisitorAudit',
        component: () => import('@/views/visitor/VisitorAuditView.vue'),
        meta: { requiresAuth: true }
      },
      {
        path: '/parking/config',
        name: 'ParkingConfig',
        component: () => import('@/views/parking/ParkingConfigView.vue'),
        meta: { requiresAuth: true }
      },
      {
        path: '/reports',
        name: 'Reports',
        component: () => import('@/views/report/ReportView.vue'),
        meta: { requiresAuth: true }
      },
      {
        path: '/zombie-vehicles',
        name: 'ZombieVehicles',
        component: () => import('@/views/zombie/ZombieVehicleView.vue'),
        meta: { requiresAuth: true }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

/**
 * 全局路由守卫
 * 未登录用户访问需要认证的页面时，重定向到 /login
 */
router.beforeEach((to, from, next) => {
  const authStore = useAuthStore()

  if (to.meta.requiresAuth !== false && !authStore.isLoggedIn) {
    next({ path: '/login', query: { redirect: to.fullPath } })
  } else if (to.path === '/login' && authStore.isLoggedIn) {
    // 已登录用户访问登录页，重定向到首页
    next({ path: '/' })
  } else {
    next()
  }
})

export default router
