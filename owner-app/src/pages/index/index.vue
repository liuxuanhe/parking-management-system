<template>
  <view class="index-container">
    <view class="header">
      <text class="welcome">欢迎回来</text>
      <text class="community">{{ userInfo.communityName || '未知小区' }}</text>
      <text class="house">房屋号：{{ userInfo.houseNo || '--' }}</text>
    </view>

    <view class="menu-grid">
      <view class="menu-item" @click="navigateTo('/pages/vehicles/list')">
        <text class="menu-icon">🚗</text>
        <text class="menu-text">车牌管理</text>
      </view>
      <view class="menu-item" @click="navigateTo('/pages/vehicles/primary')">
        <text class="menu-icon">⭐</text>
        <text class="menu-text">Primary 设置</text>
      </view>
      <view class="menu-item" @click="navigateTo('/pages/visitor/apply')">
        <text class="menu-icon">🎫</text>
        <text class="menu-text">Visitor 申请</text>
      </view>
      <view class="menu-item" @click="navigateTo('/pages/quota/index')">
        <text class="menu-icon">📊</text>
        <text class="menu-text">月度配额</text>
      </view>
    </view>

    <view class="logout-section">
      <button class="logout-btn" @click="handleLogout">退出登录</button>
    </view>
  </view>
</template>

<script>
export default {
  data() {
    return {
      userInfo: {}
    }
  },
  onShow() {
    this.loadUserInfo()
  },
  methods: {
    loadUserInfo() {
      try {
        const info = uni.getStorageSync('userInfo')
        if (info) {
          this.userInfo = JSON.parse(info)
        }
      } catch (e) {
        // 忽略解析错误
      }
    },
    navigateTo(url) {
      uni.navigateTo({ url })
    },
    handleLogout() {
      uni.showModal({
        title: '确认退出',
        content: '确定要退出登录吗？',
        success: (res) => {
          if (res.confirm) {
            uni.removeStorageSync('accessToken')
            uni.removeStorageSync('refreshToken')
            uni.removeStorageSync('userInfo')
            uni.reLaunch({ url: '/pages/login/login' })
          }
        }
      })
    }
  }
}
</script>

<style scoped>
.index-container {
  min-height: 100vh;
  padding: 40rpx;
  background: #f5f5f5;
}
.header {
  background: #1890ff;
  border-radius: 16rpx;
  padding: 40rpx;
  color: #fff;
  margin-bottom: 40rpx;
}
.welcome {
  display: block;
  font-size: 36rpx;
  font-weight: bold;
}
.community {
  display: block;
  font-size: 28rpx;
  margin-top: 12rpx;
  opacity: 0.9;
}
.house {
  display: block;
  font-size: 26rpx;
  margin-top: 8rpx;
  opacity: 0.8;
}
.menu-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 24rpx;
}
.menu-item {
  width: calc(50% - 12rpx);
  background: #fff;
  border-radius: 16rpx;
  padding: 40rpx 0;
  text-align: center;
  box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.05);
}
.menu-icon {
  display: block;
  font-size: 56rpx;
  margin-bottom: 16rpx;
}
.menu-text {
  display: block;
  font-size: 28rpx;
  color: #333;
}
.logout-section {
  margin-top: 60rpx;
}
.logout-btn {
  width: 100%;
  height: 88rpx;
  line-height: 88rpx;
  background: #fff;
  color: #ff4d4f;
  font-size: 30rpx;
  border-radius: 12rpx;
  border: 1rpx solid #ff4d4f;
}
</style>
