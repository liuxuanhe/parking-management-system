/**
 * 保持性属性测试 — Property 2: Preservation
 *
 * 目标：验证非首次登录行为不变。
 * 当 mustChangePassword=false 时：
 *   - setLoginInfo 被调用（isLoggedIn 为 true）
 *   - router.push 跳转到正确的目标页面
 *   - changePasswordVisible 为 false
 *
 * 在未修复代码上运行应通过（确认基线行为已被捕获）。
 * 在修复后运行也应通过（确认无回归）。
 *
 * Validates: Requirements 3.1, 3.2, 3.3
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from '@/stores/auth'
import { login } from '@/api/auth'

// 模拟 login API：默认返回 mustChangePassword=false 的响应
vi.mock('@/api/auth', () => ({
  login: vi.fn().mockResolvedValue({
    accessToken: 'normal-token',
    refreshToken: 'normal-refresh-token',
    mustChangePassword: false,
    adminId: 2,
    role: 'PROPERTY_ADMIN',
    communityId: 200
  }),
  changePassword: vi.fn().mockResolvedValue(undefined)
}))

// 模拟 vue-router
const mockPush = vi.fn()
const mockRouteQuery = { redirect: undefined }
vi.mock('vue-router', () => ({
  useRouter: () => ({
    push: mockPush
  }),
  useRoute: () => ({
    query: mockRouteQuery
  })
}))

// 模拟 ant-design-vue 的 message
vi.mock('ant-design-vue', () => ({
  message: {
    success: vi.fn(),
    warning: vi.fn(),
    error: vi.fn()
  }
}))

// 模拟 @ant-design/icons-vue
vi.mock('@ant-design/icons-vue', () => ({
  UserOutlined: { template: '<span />' },
  LockOutlined: { template: '<span />' }
}))


// 通用的 Ant Design Vue 组件 stub 配置
const antStubs = {
  'a-form': {
    template: '<form @submit.prevent="$emit(\'finish\')"><slot /></form>',
    emits: ['finish']
  },
  'a-form-item': { template: '<div><slot /></div>' },
  'a-input': { template: '<input />' },
  'a-input-password': { template: '<input type="password" />' },
  'a-button': { template: '<button type="submit"><slot /></button>' },
  'a-modal': { template: '<div v-if="visible"><slot /></div>', props: ['visible'] },
  'a-alert': { template: '<div />' },
  'a-progress': { template: '<div />' }
}

describe('保持性属性测试：mustChangePassword=false 时的正常登录行为', () => {
  let pinia
  let authStore

  beforeEach(() => {
    // 每个测试前重置状态
    vi.clearAllMocks()
    localStorage.clear()
    pinia = createPinia()
    setActivePinia(pinia)
    authStore = useAuthStore()
    // 重置路由 query
    mockRouteQuery.redirect = undefined
  })

  /**
   * 属性测试：mustChangePassword=false 时，setLoginInfo 被调用，isLoggedIn 为 true。
   *
   * 验证非首次登录时，登录态被正确设置。
   *
   * **Validates: Requirements 3.1**
   */
  it('mustChangePassword=false 时，isLoggedIn 应为 true（setLoginInfo 被调用）', async () => {
    const { default: LoginView } = await import('@/views/login/LoginView.vue')

    const wrapper = mount(LoginView, {
      global: {
        plugins: [pinia],
        stubs: antStubs
      }
    })

    // 触发表单提交（调用 handleLogin）
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    // 核心断言：mustChangePassword=false 时，setLoginInfo 被调用，isLoggedIn 为 true
    expect(authStore.isLoggedIn).toBe(true)
    // 验证 accessToken 被正确保存
    expect(authStore.accessToken).toBe('normal-token')

    wrapper.unmount()
  })

  /**
   * 属性测试：mustChangePassword=false 时，router.push 跳转到默认目标页面 '/'。
   *
   * 验证非首次登录时，路由跳转正常执行。
   *
   * **Validates: Requirements 3.1**
   */
  it('mustChangePassword=false 时，router.push 应跳转到默认目标页面', async () => {
    const { default: LoginView } = await import('@/views/login/LoginView.vue')

    const wrapper = mount(LoginView, {
      global: {
        plugins: [pinia],
        stubs: antStubs
      }
    })

    // 触发表单提交
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    // 核心断言：router.push 被调用，跳转到默认目标页面 '/'
    expect(mockPush).toHaveBeenCalledTimes(1)
    expect(mockPush).toHaveBeenCalledWith('/')

    wrapper.unmount()
  })

  /**
   * 属性测试：mustChangePassword=false 时，带 redirect 参数的路由跳转。
   *
   * 验证非首次登录时，如果 URL 中有 redirect 参数，跳转到指定页面。
   *
   * **Validates: Requirements 3.1**
   */
  it('mustChangePassword=false 时，router.push 应跳转到 redirect 指定的页面', async () => {
    // 设置 redirect 参数
    mockRouteQuery.redirect = '/vehicles'

    const { default: LoginView } = await import('@/views/login/LoginView.vue')

    const wrapper = mount(LoginView, {
      global: {
        plugins: [pinia],
        stubs: antStubs
      }
    })

    // 触发表单提交
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    // 核心断言：router.push 跳转到 redirect 指定的页面
    expect(mockPush).toHaveBeenCalledTimes(1)
    expect(mockPush).toHaveBeenCalledWith('/vehicles')

    wrapper.unmount()
  })

  /**
   * 属性测试：mustChangePassword=false 时，changePasswordVisible 保持为 false。
   *
   * 验证非首次登录时，密码修改对话框不会显示。
   *
   * **Validates: Requirements 3.2, 3.3**
   */
  it('mustChangePassword=false 时，changePasswordVisible 应为 false（密码修改对话框不显示）', async () => {
    const { default: LoginView } = await import('@/views/login/LoginView.vue')

    const wrapper = mount(LoginView, {
      global: {
        plugins: [pinia],
        stubs: antStubs
      }
    })

    // 触发表单提交
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    // 核心断言：密码修改对话框不应显示
    // 通过检查 modal stub 的 open prop 来验证 changePasswordVisible 为 false
    // 由于 a-modal stub 使用 v-if="open"，当 open=false 时不渲染内容
    const modalContent = wrapper.find('a-modal')
    // modal 存在但 open=false，所以内部内容不会渲染
    // 我们通过检查 DOM 中不包含密码修改相关的表单来验证
    const allForms = wrapper.findAll('form')
    // 只应有一个登录表单，不应有密码修改表单
    expect(allForms.length).toBe(1)

    wrapper.unmount()
  })

  /**
   * 属性测试：mustChangePassword=false 时，完整的登录信息被正确保存。
   *
   * 验证 setLoginInfo 被调用时，所有字段都被正确写入 store。
   *
   * **Validates: Requirements 3.1**
   */
  it('mustChangePassword=false 时，完整的登录信息应被正确保存到 store', async () => {
    const { default: LoginView } = await import('@/views/login/LoginView.vue')

    const wrapper = mount(LoginView, {
      global: {
        plugins: [pinia],
        stubs: antStubs
      }
    })

    // 触发表单提交
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    // 核心断言：所有登录信息字段被正确保存
    expect(authStore.accessToken).toBe('normal-token')
    expect(authStore.refreshToken).toBe('normal-refresh-token')
    expect(authStore.role).toBe('PROPERTY_ADMIN')
    expect(authStore.communityId).toBe(200)
    expect(authStore.adminId).toBe(2)

    wrapper.unmount()
  })
})
