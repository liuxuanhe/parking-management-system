/**
 * 路由配置
 * 包含路由守卫：未登录跳转到 /login，角色校验
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
        meta: { requiresAuth: true, roles: ['super_admin', 'property_admin'] }
      },
      {
        path: '/owners/audit',
        name: 'OwnerAudit',
        component: () => import('@/views/owner/OwnerAuditView.vue'),
        meta: { requiresAuth: true, roles: ['super_admin', 'property_admin'] }
      },
      {
        path: '/vehicles',
        name: 'VehicleList',
        component: () => import('@/views/vehicle/VehicleListView.vue'),
        meta: { requiresAuth: true, roles: ['super_admin', 'property_admin'] }
      },
      {
        path: '/visitors/audit',
        name: 'VisitorAudit',
        component: () => import('@/views/visitor/VisitorAuditView.vue'),
        meta: { requiresAuth: true, roles: ['super_admin', 'property_admin'] }
      },
      {
        path: '/parking/config',
        name: 'ParkingConfig',
        component: () => import('@/views/parking/ParkingConfigView.vue'),
        meta: { requiresAuth: true, roles: ['super_admin', 'property_admin'] }
      },
      {
        path: '/reports',
        name: 'Reports',
        component: () => import('@/views/report/ReportView.vue'),
        meta: { requiresAuth: true, roles: ['super_admin', 'property_admin'] }
      },
      {
        path: '/zombie-vehicles',
        name: 'ZombieVehicles',
        component: () => import('@/views/zombie/ZombieVehicleView.vue'),
        meta: { requiresAuth: true, roles: ['super_admin', 'property_admin'] }
      },
      {
        path: '/audit/logs',
        name: 'AuditLogs',
        component: () => import('@/views/audit/AuditLogView.vue'),
        meta: { requiresAuth: true, roles: ['super_admin', 'property_admin'] }
      },
      {
        path: '/admins',
        name: 'AdminManage',
        component: () => import('@/views/admin/AdminManageView.vue'),
        meta: { requiresAuth: true, roles: ['super_admin'] }
      },
      {
        path: '/ip-whitelist',
        name: 'IpWhitelist',
        component: () => import('@/views/ip-whitelist/IpWhitelistView.vue'),
        meta: { requiresAuth: true, roles: ['super_admin'] }
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
 * 1. 未登录用户访问需要认证的页面时，重定向到 /login
 * 2. 角色校验：如果路由定义了 meta.roles，校验当前用户角色是否在允许列表中
 */
router.beforeEach((to, from, next) => {
  const authStore = useAuthStore()

  if (to.meta.requiresAuth !== false && !authStore.isLoggedIn) {
    next({ path: '/login', query: { redirect: to.fullPath } })
  } else if (to.path === '/login' && authStore.isLoggedIn && !authStore.mustChangePassword) {
    // 已登录用户访问登录页，且不需要修改密码时，重定向到首页
    next({ path: '/' })
  } else if (to.meta.requiresAuth !== false && authStore.isLoggedIn && authStore.mustChangePassword) {
    // 需要修改密码的用户访问其他页面时，重定向回登录页
    next({ path: '/login' })
  } else if (to.meta.roles && authStore.isLoggedIn) {
    // 角色校验：检查当前用户角色是否在允许列表中
    if (to.meta.roles.includes(authStore.role)) {
      next()
    } else {
      // 无权限，重定向到首页
      next({ path: '/' })
    }
  } else {
    next()
  }
})

export default router
