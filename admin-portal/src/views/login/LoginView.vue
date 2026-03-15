<template>
  <div class="login-container">
    <div class="login-card">
      <div class="login-header">
        <h1>停车场管理后台</h1>
        <p>物业管理系统登录</p>
      </div>

      <!-- 登录表单 -->
      <a-form
        ref="loginFormRef"
        :model="loginForm"
        :rules="loginRules"
        layout="vertical"
        @finish="handleLogin"
      >
        <a-form-item label="用户名" name="username">
          <a-input
            v-model:value="loginForm.username"
            placeholder="请输入用户名"
            size="large"
            :prefix="h(UserOutlined)"
          />
        </a-form-item>

        <a-form-item label="密码" name="password">
          <a-input-password
            v-model:value="loginForm.password"
            placeholder="请输入密码"
            size="large"
            :prefix="h(LockOutlined)"
            @pressEnter="handleLogin"
          />
        </a-form-item>

        <a-form-item>
          <a-button
            type="primary"
            html-type="submit"
            size="large"
            block
            :loading="loginLoading"
          >
            登录
          </a-button>
        </a-form-item>
      </a-form>
    </div>

    <!-- 首次登录强制修改密码对话框 -->
    <a-modal
      v-model:visible="changePasswordVisible"
      title="首次登录 — 请修改初始密码"
      :closable="false"
      :maskClosable="false"
      :footer="null"
    >
      <a-alert
        message="系统检测到您使用的是初始密码，为保障账号安全，请立即修改密码。"
        type="warning"
        show-icon
        style="margin-bottom: 16px"
      />

      <a-form
        ref="changePwdFormRef"
        :model="changePwdForm"
        :rules="changePwdRules"
        layout="vertical"
        @finish="handleChangePassword"
      >
        <a-form-item label="当前密码" name="oldPassword">
          <a-input-password
            v-model:value="changePwdForm.oldPassword"
            placeholder="请输入当前密码"
          />
        </a-form-item>

        <a-form-item label="新密码" name="newPassword">
          <a-input-password
            v-model:value="changePwdForm.newPassword"
            placeholder="至少8位，包含大小写字母、数字、特殊字符"
          />
        </a-form-item>

        <a-form-item label="确认新密码" name="confirmPassword">
          <a-input-password
            v-model:value="changePwdForm.confirmPassword"
            placeholder="请再次输入新密码"
          />
        </a-form-item>

        <!-- 密码强度提示 -->
        <div class="password-strength" v-if="changePwdForm.newPassword">
          <span>密码强度：</span>
          <a-progress
            :percent="passwordStrengthPercent"
            :status="passwordStrengthStatus"
            :show-info="false"
            size="small"
            style="width: 120px; display: inline-block; margin-left: 8px"
          />
          <span :style="{ color: passwordStrengthColor, marginLeft: '8px' }">
            {{ passwordStrengthText }}
          </span>
        </div>

        <a-form-item style="margin-top: 16px">
          <a-button
            type="primary"
            html-type="submit"
            block
            :loading="changePwdLoading"
          >
            确认修改
          </a-button>
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, computed, h } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { message } from 'ant-design-vue'
import { UserOutlined, LockOutlined } from '@ant-design/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { login, changePassword } from '@/api/auth'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

// ========== 登录表单 ==========
const loginFormRef = ref(null)
const loginLoading = ref(false)
const loginForm = ref({
  username: '',
  password: ''
})

const loginRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

// ========== 修改密码表单 ==========
const changePasswordVisible = ref(false)
const changePwdFormRef = ref(null)
const changePwdLoading = ref(false)
const changePwdForm = ref({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

/**
 * 密码强度正则
 * 至少8位，包含大小写字母、数字、特殊字符
 */
const PASSWORD_REGEX = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[!@#$%^&*()_+\-=\[\]{};':"\\|,.<>\/?`~])[A-Za-z\d!@#$%^&*()_+\-=\[\]{};':"\\|,.<>\/?`~]{8,}$/

/** 验证新密码强度 */
function validateNewPassword(_rule, value) {
  if (!value) {
    return Promise.reject('请输入新密码')
  }
  if (value.length < 8) {
    return Promise.reject('密码长度至少8位')
  }
  if (!PASSWORD_REGEX.test(value)) {
    return Promise.reject('密码需包含大小写字母、数字和特殊字符')
  }
  return Promise.resolve()
}

/** 验证确认密码 */
function validateConfirmPassword(_rule, value) {
  if (!value) {
    return Promise.reject('请再次输入新密码')
  }
  if (value !== changePwdForm.value.newPassword) {
    return Promise.reject('两次输入的密码不一致')
  }
  return Promise.resolve()
}

const changePwdRules = {
  oldPassword: [{ required: true, message: '请输入当前密码', trigger: 'blur' }],
  newPassword: [{ required: true, validator: validateNewPassword, trigger: 'blur' }],
  confirmPassword: [{ required: true, validator: validateConfirmPassword, trigger: 'blur' }]
}

// ========== 密码强度指示器 ==========
const passwordStrengthPercent = computed(() => {
  const pwd = changePwdForm.value.newPassword
  if (!pwd) return 0
  let score = 0
  if (pwd.length >= 8) score += 25
  if (/[a-z]/.test(pwd)) score += 25
  if (/[A-Z]/.test(pwd)) score += 25
  if (/\d/.test(pwd)) score += 12.5
  if (/[!@#$%^&*()_+\-=\[\]{};':"\\|,.<>\/?`~]/.test(pwd)) score += 12.5
  return Math.min(score, 100)
})

const passwordStrengthText = computed(() => {
  const p = passwordStrengthPercent.value
  if (p < 50) return '弱'
  if (p < 75) return '中'
  if (p < 100) return '强'
  return '非常强'
})

const passwordStrengthColor = computed(() => {
  const p = passwordStrengthPercent.value
  if (p < 50) return '#ff4d4f'
  if (p < 75) return '#faad14'
  return '#52c41a'
})

const passwordStrengthStatus = computed(() => {
  const p = passwordStrengthPercent.value
  if (p < 50) return 'exception'
  if (p < 100) return 'active'
  return 'success'
})

// ========== 登录处理 ==========

/** 临时保存登录时的密码，用于自动填充修改密码表单 */
let tempLoginPassword = ''

/** 处理登录提交 */
async function handleLogin() {
  loginLoading.value = true
  try {
    const data = await login({
      username: loginForm.value.username,
      password: loginForm.value.password
    })

    // 首次登录强制修改密码：保存登录信息但标记需要修改密码
    if (data.mustChangePassword) {
      authStore.setLoginInfo(data)
      authStore.mustChangePassword = true
      tempLoginPassword = loginForm.value.password
      changePwdForm.value.oldPassword = tempLoginPassword
      changePasswordVisible.value = true
      message.warning('首次登录，请修改初始密码')
      return
    }

    // 非首次登录，正常流程：保存登录信息到 store 并跳转
    authStore.setLoginInfo(data)
    message.success('登录成功')
    const redirect = route.query.redirect || '/'
    router.push(redirect)
  } catch (err) {
    // 错误已在 request.js 拦截器中统一提示
  } finally {
    loginLoading.value = false
  }
}

/** 处理修改密码提交 */
async function handleChangePassword() {
  changePwdLoading.value = true
  try {
    await changePassword({
      oldPassword: changePwdForm.value.oldPassword,
      newPassword: changePwdForm.value.newPassword
    })

    message.success('密码修改成功，请使用新密码重新登录')
    changePasswordVisible.value = false

    // 清除登录态，要求用新密码重新登录
    authStore.logout()
    loginForm.value.password = ''
    tempLoginPassword = ''
  } catch (err) {
    // 错误已在 request.js 拦截器中统一提示
  } finally {
    changePwdLoading.value = false
  }
}
</script>

<style scoped>
.login-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.login-card {
  width: 400px;
  padding: 40px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.15);
}

.login-header {
  text-align: center;
  margin-bottom: 32px;
}

.login-header h1 {
  font-size: 24px;
  color: #1a1a1a;
  margin-bottom: 8px;
}

.login-header p {
  color: #666;
  font-size: 14px;
}

.password-strength {
  display: flex;
  align-items: center;
  font-size: 13px;
  color: #666;
  margin-bottom: 8px;
}
</style>
