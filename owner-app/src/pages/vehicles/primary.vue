<template>
  <view class="primary-container">
    <view class="tip-section">
      <text class="tip-text">
        选择一辆车设为 Primary 车辆，享受自动入场权限。
        每个房屋号最多1辆 Primary 车辆。
      </text>
    </view>

    <view class="vehicle-list">
      <view
        v-for="item in vehicleList"
        :key="item.vehicleId"
        class="vehicle-item"
        :class="{ selected: selectedId === item.vehicleId, current: item.status === 'primary' }"
        @click="selectVehicle(item)"
      >
        <view class="item-left">
          <text class="plate">{{ item.carNumber }}</text>
          <text class="status" v-if="item.status === 'primary'">当前 Primary</text>
        </view>
        <view class="item-right">
          <text v-if="selectedId === item.vehicleId" class="check-icon">✓</text>
        </view>
      </view>
    </view>

    <view v-if="vehicleList.length === 0 && !loading" class="empty-tip">
      <text>暂无车牌，请先添加车牌</text>
    </view>

    <button
      class="submit-btn"
      :disabled="!selectedId || selectedId === currentPrimaryId"
      :loading="submitting"
      @click="handleSetPrimary"
    >
      设为 Primary 车辆
    </button>
  </view>
</template>

<script>
import { getVehicleList, setPrimary } from '@/api/vehicle'

export default {
  data() {
    return {
      vehicleList: [],
      selectedId: null,
      currentPrimaryId: null,
      loading: false,
      submitting: false
    }
  },
  onShow() {
    this.loadList()
  },
  methods: {
    async loadList() {
      this.loading = true
      try {
        const data = await getVehicleList()
        const list = data?.records || data?.vehicles || []
        this.vehicleList = list.filter(v => v.status !== 'disabled' && v.status !== 'deleted')
        // 找到当前 Primary 车辆
        const primary = this.vehicleList.find(v => v.status === 'primary')
        if (primary) {
          this.currentPrimaryId = primary.vehicleId
          this.selectedId = primary.vehicleId
        }
      } catch (e) {
        // 错误已在 request 中处理
      } finally {
        this.loading = false
      }
    },

    selectVehicle(item) {
      this.selectedId = item.vehicleId
    },

    handleSetPrimary() {
      if (!this.selectedId || this.selectedId === this.currentPrimaryId) return

      const selected = this.vehicleList.find(v => v.vehicleId === this.selectedId)
      uni.showModal({
        title: '二次确认',
        content: `确定将 ${selected.carNumber} 设为 Primary 车辆吗？设置后原 Primary 车辆将变为普通车辆。`,
        success: async (res) => {
          if (res.confirm) {
            this.submitting = true
            try {
              await setPrimary(this.selectedId, { confirmed: true })
              uni.showToast({ title: '设置成功', icon: 'success' })
              this.loadList()
            } catch (e) {
              // 错误已在 request 中处理
            } finally {
              this.submitting = false
            }
          }
        }
      })
    }
  }
}
</script>

<style scoped>
.primary-container {
  min-height: 100vh;
  padding: 24rpx;
  background: #f5f5f5;
}
.tip-section {
  background: #e6f7ff;
  border-radius: 12rpx;
  padding: 24rpx;
  margin-bottom: 24rpx;
}
.tip-text {
  font-size: 26rpx;
  color: #1890ff;
  line-height: 1.6;
}
.vehicle-list {
  margin-bottom: 40rpx;
}
.vehicle-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #fff;
  border-radius: 12rpx;
  padding: 32rpx;
  margin-bottom: 16rpx;
  border: 2rpx solid transparent;
}
.vehicle-item.selected {
  border-color: #1890ff;
  background: #f0f8ff;
}
.vehicle-item.current {
  border-color: #52c41a;
}
.item-left {
  flex: 1;
}
.plate {
  display: block;
  font-size: 32rpx;
  font-weight: bold;
  color: #333;
}
.status {
  display: block;
  font-size: 24rpx;
  color: #52c41a;
  margin-top: 8rpx;
}
.check-icon {
  font-size: 36rpx;
  color: #1890ff;
  font-weight: bold;
}
.empty-tip {
  text-align: center;
  padding: 80rpx 0;
  color: #999;
  font-size: 28rpx;
}
.submit-btn {
  width: 100%;
  height: 96rpx;
  line-height: 96rpx;
  background: #1890ff;
  color: #fff;
  font-size: 32rpx;
  border-radius: 12rpx;
}
.submit-btn[disabled] {
  background: #ccc;
}
</style>
