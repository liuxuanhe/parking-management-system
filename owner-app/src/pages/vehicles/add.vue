<template>
  <view class="add-vehicle-container">
    <view class="form-section">
      <view class="input-group">
        <text class="label">车牌号</text>
        <input
          v-model="form.carNumber"
          type="text"
          placeholder="例如：京A12345"
          class="input"
        />
      </view>

      <view class="input-group">
        <text class="label">品牌（选填）</text>
        <input
          v-model="form.brand"
          type="text"
          placeholder="例如：丰田"
          class="input"
        />
      </view>

      <view class="input-group">
        <text class="label">型号（选填）</text>
        <input
          v-model="form.model"
          type="text"
          placeholder="例如：卡罗拉"
          class="input"
        />
      </view>

      <view class="input-group">
        <text class="label">颜色（选填）</text>
        <input
          v-model="form.color"
          type="text"
          placeholder="例如：白色"
          class="input"
        />
      </view>

      <button class="submit-btn" :loading="loading" @click="handleSubmit">
        添加车牌
      </button>
    </view>
  </view>
</template>

<script>
import { addVehicle } from '@/api/vehicle'

export default {
  data() {
    return {
      form: {
        carNumber: '',
        brand: '',
        model: '',
        color: ''
      },
      loading: false
    }
  },
  methods: {
    async handleSubmit() {
      if (!this.form.carNumber) {
        uni.showToast({ title: '请输入车牌号', icon: 'none' })
        return
      }

      this.loading = true
      try {
        await addVehicle(this.form)
        uni.showToast({ title: '添加成功', icon: 'success' })
        setTimeout(() => {
          uni.navigateBack()
        }, 500)
      } catch (e) {
        // 错误已在 request 中处理
      } finally {
        this.loading = false
      }
    }
  }
}
</script>

<style scoped>
.add-vehicle-container {
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
</style>
