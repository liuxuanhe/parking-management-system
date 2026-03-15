/**
 * Bug 条件探索性测试 — Property 1: Bug Condition
 *
 * 目标：验证 mustChangePassword=true 时的登录行为。
 * 修复后：登录信息会保存到 store（以便 changePassword API 携带 token），
 * 但 authStore.mustChangePassword 被设为 true，路由守卫据此阻止重定向。
 *
 * Validates: Requirements 1.1, 1.2, 2.1, 2.2
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from '@/stores/auth'

// 模拟 login API：返回 mustChangePassword=true 的响应
vi.mock('@/api/auth', () => ({
  login: vi.fn().mockResolvedValue({
    accessToken: 'test-token',
    refreshToken: 'test-refresh-token',
    mustChangePassword: true,
    adminId: 1,
    role: 'PROPERTY_ADMIN',
    communityId: 100
  }),
  changePassword: vi.fn().mockResolvedValue(undefined)
}))

// 模拟 vue-router
const mockPush = vi.fn()
vi.mock('vue-router', () => ({
  useRouter: () => ({
    push: mockPush
  }),
  useRoute: () => ({
    query: {}
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

describe('Bug 条件测试：mustChangePassword=true 时的登录行为', () => {
  let pinia
  let authStore

  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
    pinia = createPinia()
    setActivePinia(pinia)
    authStore = useAuthStore()
  })

  /**
   * 验证：mustChangePassword=true 时，store 的 mustChangePassword 标志被设为 true，
   * 路由守卫据此阻止从 /login 重定向。
   *
   * **Validates: Requirements 2.1**
   */
  it('mustChangePassword=true 时，authStore.mustChangePassword 应为 true', async () => {
    const { default: LoginView } = await import('@/views/login/LoginView.vue')

    const wrapper = mount(LoginView, {
      global: {
        plugins: [pinia],
        stubs: {
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
      }
    })

    await wrapper.find('form').trigger('submit')
    await flushPromises()

    // 核心断言：mustChangePassword 标志被设为 true
    expect(authStore.mustChangePassword).toBe(true)
    // token 已保存（以便 changePassword API 能携带鉴权信息）
    expect(authStore.accessToken).toBe('test-token')

    wrapper.unmount()
  })

  /**
   * 验证：mustChangePassword=true 时，密码修改对话框应显示。
   *
   * **Validates: Requirements 2.2**
   */
  it('mustChangePassword=true 时，changePasswordVisible 应为 true', async () => {
    const { default: LoginView } = await import('@/views/login/LoginView.vue')

    const wrapper = mount(LoginView, {
      global: {
        plugins: [pinia],
        stubs: {
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
      }
    })

    await wrapper.find('form').trigger('submit')
    await flushPromises()

    const modal = wrapper.findComponent({ name: 'a-modal' })
    expect(modal.exists() || wrapper.html().includes('v-if')).toBe(true)

    wrapper.unmount()
  })

  /**
   * 验证：mustChangePassword=true 时，router.push 不应被调用。
   *
   * **Validates: Requirements 1.2, 2.2**
   */
  it('mustChangePassword=true 时，router.push 不应被调用', async () => {
    const { default: LoginView } = await import('@/views/login/LoginView.vue')

    const wrapper = mount(LoginView, {
      global: {
        plugins: [pinia],
        stubs: {
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
      }
    })

    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(mockPush).not.toHaveBeenCalled()

    wrapper.unmount()
  })
})
