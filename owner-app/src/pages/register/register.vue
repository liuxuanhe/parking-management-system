<template>
  <view class="register-container">
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

      <view class="input-group">
        <text class="label">小区</text>
        <picker
          :range="communityList"
          range-key="name"
          @change="onCommunityChange"
        >
          <view class="picker-value">
            {{ selectedCommunity ? selectedCommunity.name : '请选择小区' }}
          </view>
        </picker>
      </view>

      <view class="input-group">
        <text class="label">房屋号</text>
        <input
          v-model="form.houseNo"
          type="text"
          placeholder="例如：1-101"
          class="input"
        />
      </view>

      <view class="input-group">
        <text class="label">身份证后4位</text>
        <input
          v-model="form.idCardLast4"
          type="number"
          maxlength="4"
          placeholder="请输入身份证后4位"
          class="input"
        />
      </view>

      <button class="submit-btn" :loading="loading" @click="handleRegister">
        注册
      </button>

      <view class="link-row">
        <text class="link" @click="goLogin">已有账号？去登录</text>
      </view>
    </view>
  </view>
</template>

<script>
import { sendVerificationCode, register } from '@/api/auth'

export default {
  data() {
    return {
      form: {
        phoneNumber: '',
        verificationCode: '',
        communityId: null,
        houseNo: '',
        idCardLast4: ''
      },
      loading: false,
      codeCooldown: 0,
      timer: null,
      // TODO: 从后端接口获取小区列表
      communityList: [
        { id: 1, name: '示例小区A' },
        { id: 2, name: '示例小区B' }
      ],
      selectedCommunity: null
    }
  },
  beforeUnmount() {
    if (this.timer) clearInterval(this.timer)
  },
  methods: {
    /** 选择小区 */
    onCommunityChange(e) {
      const idx = e.detail.value
      this.selectedCommunity = this.communityList[idx]
      this.form.communityId = this.selectedCommunity.id
    },

    /** 发送验证码 */
    async handleSendCode() {
      if (!this.form.phoneNumber || this.form.phoneNumber.length !== 11) {
        uni.showToast({ title: '请输入正确的手机号', icon: 'none' })
        return
      }
      try {
        await sendVerificationCode(this.form.phoneNumber)
        uni.showToast({ title: '验证码已发送', icon: 'success' })
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

    /** 注册 */
    async handleRegister() {
      // 表单验证
      if (!this.form.phoneNumber || this.form.phoneNumber.length !== 11) {
        uni.showToast({ title: '请输入正确的手机号', icon: 'none' })
        return
      }
      if (!this.form.verificationCode || this.form.verificationCode.length !== 6) {
        uni.showToast({ title: '请输入6位验证码', icon: 'none' })
        return
      }
      if (!this.form.communityId) {
        uni.showToast({ title: '请选择小区', icon: 'none' })
        return
      }
      if (!this.form.houseNo) {
        uni.showToast({ title: '请输入房屋号', icon: 'none' })
        return
      }
      if (!this.form.idCardLast4 || this.form.idCardLast4.length !== 4) {
        uni.showToast({ title: '请输入身份证后4位', icon: 'none' })
        return
      }

      this.loading = true
      try {
        await register(this.form)
        uni.showModal({
          title: '注册成功',
          content: '您的账号已提交，请等待物业审核通过后登录。',
          showCancel: false,
          success: () => {
            uni.navigateBack()
          }
        })
      } catch (e) {
        // 错误已在 request 中处理
      } finally {
        this.loading = false
      }
    },

    /** 跳转登录页 */
    goLogin() {
      uni.navigateBack()
    }
  }
}
</script>

<style scoped>
.register-container {
  min-height: 100vh;
  padding: 40rpx;
  background: #fff;
}
.form-section {
  padding: 0 20rpx;
}
.input-group {
  margin-bottom: 36rpx;
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
.picker-value {
  width: 100%;
  height: 88rpx;
  line-height: 88rpx;
  border: 1rpx solid #ddd;
  border-radius: 12rpx;
  padding: 0 24rpx;
  font-size: 30rpx;
  color: #333;
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
