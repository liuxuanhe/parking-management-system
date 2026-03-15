<template>
  <view class="quota-container">
    <!-- 配额概览卡片 -->
    <view class="quota-overview" v-if="quota">
      <text class="overview-title">{{ currentMonth }} 月度 Visitor 配额</text>

      <!-- 环形进度 -->
      <view class="progress-section">
        <view class="progress-ring">
          <text class="progress-value">{{ quotaPercent }}%</text>
          <text class="progress-label">已使用</text>
        </view>
      </view>

      <!-- 进度条 -->
      <view class="bar-wrapper">
        <view
          class="bar-fill"
          :class="{ warning: quotaPercent >= 83, danger: quotaPercent >= 100 }"
          :style="{ width: Math.min(100, quotaPercent) + '%' }"
        ></view>
      </view>

      <!-- 详细数据 -->
      <view class="detail-grid">
        <view class="detail-item">
          <text class="detail-value">{{ quota.usedHours || 0 }}</text>
          <text class="detail-label">已使用（小时）</text>
        </view>
        <view class="detail-item">
          <text class="detail-value">{{ quota.remainingHours || 0 }}</text>
          <text class="detail-label">剩余（小时）</text>
        </view>
        <view class="detail-item">
          <text class="detail-value">{{ quota.totalHours || 72 }}</text>
          <text class="detail-label">总配额（小时）</text>
        </view>
      </view>
    </view>

    <!-- 超限提醒 -->
    <view class="alert-card" v-if="quota && quota.usedHours >= 60">
      <text class="alert-icon">⚠️</text>
      <text class="alert-text" v-if="quota.usedHours >= 72">
        本月配额已用完，无法继续申请 Visitor 权限
      </text>
      <text class="alert-text" v-else>
        本月配额即将用完，剩余 {{ quota.remainingHours }} 小时
      </text>
    </view>

    <!-- 说明 -->
    <view class="info-card">
      <text class="info-title">配额说明</text>
      <view class="info-list">
        <text class="info-item">• 每个房屋号每月 Visitor 配额为 72 小时</text>
        <text class="info-item">• 配额按自然月重置，每月1日自动恢复</text>
        <text class="info-item">• 配额使用量 = 所有 Visitor 车辆实际在场停放时长之和</text>
        <text class="info-item">• 配额超过 60 小时时系统会发送提醒通知</text>
      </view>
    </view>

    <!-- 快捷入口 -->
    <button class="apply-btn" @click="goApply">申请 Visitor 权限</button>
  </view>
</template>

<script>
import { getQuota } from '@/api/visitor'

export default {
  data() {
    return {
      quota: null,
      loading: false
    }
  },
  computed: {
    currentMonth() {
      const now = new Date()
      return `${now.getFullYear()}年${now.getMonth() + 1}`
    },
    quotaPercent() {
      if (!this.quota || !this.quota.totalHours) return 0
      return Math.round((this.quota.usedHours / this.quota.totalHours) * 100)
    }
  },
  onShow() {
    this.loadQuota()
  },
  methods: {
    async loadQuota() {
      this.loading = true
      try {
        this.quota = await getQuota()
      } catch (e) {
        // 错误已在 request 中处理
      } finally {
        this.loading = false
      }
    },
    goApply() {
      uni.navigateTo({ url: '/pages/visitor/apply' })
    }
  }
}
</script>

<style scoped>
.quota-container {
  min-height: 100vh;
  padding: 24rpx;
  background: #f5f5f5;
}
.quota-overview {
  background: #fff;
  border-radius: 16rpx;
  padding: 40rpx 32rpx;
  margin-bottom: 24rpx;
}
.overview-title {
  display: block;
  font-size: 30rpx;
  font-weight: bold;
  color: #333;
  text-align: center;
  margin-bottom: 32rpx;
}
.progress-section {
  display: flex;
  justify-content: center;
  margin-bottom: 32rpx;
}
.progress-ring {
  width: 200rpx;
  height: 200rpx;
  border-radius: 50%;
  background: #e6f7ff;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}
.progress-value {
  font-size: 48rpx;
  font-weight: bold;
  color: #1890ff;
}
.progress-label {
  font-size: 24rpx;
  color: #999;
  margin-top: 4rpx;
}
.bar-wrapper {
  height: 16rpx;
  background: #f0f0f0;
  border-radius: 8rpx;
  overflow: hidden;
  margin-bottom: 32rpx;
}
.bar-fill {
  height: 100%;
  background: #1890ff;
  border-radius: 8rpx;
  transition: width 0.3s;
}
.bar-fill.warning {
  background: #faad14;
}
.bar-fill.danger {
  background: #ff4d4f;
}
.detail-grid {
  display: flex;
  justify-content: space-around;
}
.detail-item {
  text-align: center;
}
.detail-value {
  display: block;
  font-size: 36rpx;
  font-weight: bold;
  color: #333;
}
.detail-label {
  display: block;
  font-size: 24rpx;
  color: #999;
  margin-top: 8rpx;
}
.alert-card {
  background: #fffbe6;
  border: 1rpx solid #ffe58f;
  border-radius: 12rpx;
  padding: 24rpx;
  margin-bottom: 24rpx;
  display: flex;
  align-items: center;
  gap: 12rpx;
}
.alert-icon {
  font-size: 32rpx;
}
.alert-text {
  font-size: 26rpx;
  color: #d48806;
  flex: 1;
}
.info-card {
  background: #fff;
  border-radius: 16rpx;
  padding: 32rpx;
  margin-bottom: 24rpx;
}
.info-title {
  display: block;
  font-size: 28rpx;
  font-weight: bold;
  color: #333;
  margin-bottom: 16rpx;
}
.info-list {
  padding-left: 4rpx;
}
.info-item {
  display: block;
  font-size: 26rpx;
  color: #666;
  line-height: 1.8;
}
.apply-btn {
  width: 100%;
  height: 96rpx;
  line-height: 96rpx;
  background: #1890ff;
  color: #fff;
  font-size: 32rpx;
  border-radius: 12rpx;
}
</style>
