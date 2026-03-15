<template>
  <view class="login-container">
    <view class="logo-section">
      <text class="app-title">停车场管理</text>
      <text class="app-subtitle">业主端</text>
    </view>

    <view class="form-section">
      <view class="input-group">
        <text class="label">手机号</text>
        <input
          v-model="form.phoneNumber"
          type="number"
          maxlength="11"
          placeholder="请输入手机号"
          class="input"
        />
      </view>

      <view class="input-group">
        <text class="label">验证码</text>
        <view class="code-row">
          <input
            v-model="form.verificationCode"
            type="number"
            maxlength="6"
            placeholder="请输入验证码"
            class="input code-input"
          />
          <button
            class="code-btn"
            :disabled="codeCooldown > 0"
            @click="handleSendCode"
          >
            {{ codeCooldown > 0 ? `${codeCooldown}s` : '获取验证码' }}
          </button>
        </view>
      </view>

      <button class="submit-btn" :loading="loading" @click="handleLogin">
        登录
      </button>

      <view class="link-row">
        <text class="link" @click="goRegister">还没有账号？去注册</text>
      </view>
    </view>
  </view>
</template>

<script>
import { sendVerificationCode, login } from '@/api/auth'

export default {
  data() {
    return {
      form: {
        phoneNumber: '',
        verificationCode: ''
      },
      loading: false,
      codeCooldown: 0,
      timer: null
    }
  },
  beforeUnmount() {
    if (this.timer) clearInterval(this.timer)
  },
  methods: {
    /** 发送验证码 */
    async handleSendCode() {
      if (!this.form.phoneNumber || this.form.phoneNumber.length !== 11) {
        uni.showToast({ title: '请输入正确的手机号', icon: 'none' })
        return
      }
      try {
        await sendVerificationCode(this.form.phoneNumber)
        uni.showToast({ title: '验证码已发送', icon: 'success' })
        // 开始倒计时
        this.codeCooldown = 60
        this.timer = setInterval(() => {
          this.codeCooldown--
          if (this.codeCooldown <= 0) {
            clearInterval(this.timer)
            this.timer = null
          }
        }, 1000)
      } catch (e) {
        // 错误已在 request 中处理
      }
    },

    /** 登录 */
    async handleLogin() {
      if (!this.form.phoneNumber || this.form.phoneNumber.length !== 11) {
        uni.showToast({ title: '请输入正确的手机号', icon: 'none' })
        return
      }
      if (!this.form.verificationCode || this.form.verificationCode.length !== 6) {
        uni.showToast({ title: '请输入6位验证码', icon: 'none' })
        return
      }

      this.loading = true
      try {
        const result = await login(this.form)
        // 存储 Token
        uni.setStorageSync('accessToken', result.accessToken)
        uni.setStorageSync('refreshToken', result.refreshToken)
        uni.setStorageSync('userInfo', JSON.stringify(result))
        uni.showToast({ title: '登录成功', icon: 'success' })
        // 跳转首页
        setTimeout(() => {
          uni.switchTab({ url: '/pages/index/index' })
        }, 500)
      } catch (e) {
        // 错误已在 request 中处理
      } finally {
        this.loading = false
      }
    },

    /** 跳转注册页 */
    goRegister() {
      uni.navigateTo({ url: '/pages/register/register' })
    }
  }
}
</script>

<style scoped>
.login-container {
  min-height: 100vh;
  padding: 60rpx 40rpx;
  background: #fff;
}
.logo-section {
  text-align: center;
  margin-bottom: 80rpx;
  padding-top: 80rpx;
}
.app-title {
  display: block;
  font-size: 48rpx;
  font-weight: bold;
  color: #1890ff;
}
.app-subtitle {
  display: block;
  font-size: 28rpx;
  color: #999;
  margin-top: 12rpx;
}
.form-section {
  padding: 0 20rpx;
}
.input-group {
  margin-bottom: 40rpx;
}
.label {
  display: block;
  font-size: 28rpx;
  color: #333;
  margin-bottom: 16rpx;
}
.input {
  width: 100%;
  height: 88rpx;
  border: 1rpx solid #ddd;
  border-radius: 12rpx;
  padding: 0 24rpx;
  font-size: 30rpx;
  box-sizing: border-box;
}
.code-row {
  display: flex;
  align-items: center;
  gap: 20rpx;
}
.code-input {
  flex: 1;
}
.code-btn {
  width: 220rpx;
  height: 88rpx;
  line-height: 88rpx;
  font-size: 26rpx;
  background: #1890ff;
  color: #fff;
  border-radius: 12rpx;
  text-align: center;
  padding: 0;
}
.code-btn[disabled] {
  background: #ccc;
}
.submit-btn {
  width: 100%;
  height: 96rpx;
  line-height: 96rpx;
  background: #1890ff;
  color: #fff;
  font-size: 32rpx;
  border-radius: 12rpx;
  margin-top: 40rpx;
}
.link-row {
  text-align: center;
  margin-top: 32rpx;
}
.link {
  font-size: 28rpx;
  color: #1890ff;
}
</style>
