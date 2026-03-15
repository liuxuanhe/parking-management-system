<template>
  <view class="vehicle-list-container">
    <view class="vehicle-card" v-for="item in vehicleList" :key="item.id">
      <view class="card-header">
        <text class="plate-number">{{ item.carNumber }}</text>
        <view class="status-tag" :class="item.status">
          {{ statusText(item.status) }}
        </view>
      </view>
      <view class="card-body">
        <text class="info-item" v-if="item.brand">品牌：{{ item.brand }}</text>
        <text class="info-item" v-if="item.model">型号：{{ item.model }}</text>
        <text class="info-item" v-if="item.color">颜色：{{ item.color }}</text>
      </view>
      <view class="card-footer">
        <button
          v-if="item.status !== 'primary'"
          class="action-btn delete-btn"
          size="mini"
          @click="handleDelete(item)"
        >
          删除
        </button>
      </view>
    </view>

    <view v-if="vehicleList.length === 0 && !loading" class="empty-tip">
      <text>暂无车牌，请添加</text>
    </view>

    <!-- 添加按钮 -->
    <view class="add-section">
      <button class="add-btn" @click="goAdd">+ 添加车牌</button>
    </view>
  </view>
</template>

<script>
import { getVehicleList, deleteVehicle } from '@/api/vehicle'

export default {
  data() {
    return {
      vehicleList: [],
      loading: false
    }
  },
  onShow() {
    this.loadList()
  },
  methods: {
    /** 加载车牌列表 */
    async loadList() {
      this.loading = true
      try {
        const data = await getVehicleList()
        this.vehicleList = data || []
      } catch (e) {
        // 错误已在 request 中处理
      } finally {
        this.loading = false
      }
    },

    /** 状态文本 */
    statusText(status) {
      const map = {
        primary: 'Primary',
        normal: '普通',
        disabled: '已禁用'
      }
      return map[status] || status
    },

    /** 删除车牌 */
    handleDelete(item) {
      uni.showModal({
        title: '确认删除',
        content: `确定要删除车牌 ${item.carNumber} 吗？`,
        success: async (res) => {
          if (res.confirm) {
            try {
              await deleteVehicle(item.id)
              uni.showToast({ title: '删除成功', icon: 'success' })
              this.loadList()
            } catch (e) {
              // 错误已在 request 中处理
            }
          }
        }
      })
    },

    /** 跳转添加页 */
    goAdd() {
      uni.navigateTo({ url: '/pages/vehicles/add' })
    }
  }
}
</script>

<style scoped>
.vehicle-list-container {
  min-height: 100vh;
  padding: 24rpx;
  background: #f5f5f5;
}
.vehicle-card {
  background: #fff;
  border-radius: 16rpx;
  padding: 32rpx;
  margin-bottom: 24rpx;
  box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.05);
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16rpx;
}
.plate-number {
  font-size: 36rpx;
  font-weight: bold;
  color: #333;
}
.status-tag {
  font-size: 24rpx;
  padding: 4rpx 16rpx;
  border-radius: 8rpx;
}
.status-tag.primary {
  background: #e6f7ff;
  color: #1890ff;
}
.status-tag.normal {
  background: #f0f0f0;
  color: #666;
}
.status-tag.disabled {
  background: #fff1f0;
  color: #ff4d4f;
}
.card-body {
  margin-bottom: 16rpx;
}
.info-item {
  display: block;
  font-size: 26rpx;
  color: #666;
  margin-bottom: 8rpx;
}
.card-footer {
  display: flex;
  justify-content: flex-end;
}
.action-btn {
  font-size: 24rpx;
  padding: 0 24rpx;
  height: 56rpx;
  line-height: 56rpx;
  border-radius: 8rpx;
}
.delete-btn {
  background: #fff;
  color: #ff4d4f;
  border: 1rpx solid #ff4d4f;
}
.empty-tip {
  text-align: center;
  padding: 80rpx 0;
  color: #999;
  font-size: 28rpx;
}
.add-section {
  margin-top: 24rpx;
}
.add-btn {
  width: 100%;
  height: 88rpx;
  line-height: 88rpx;
  background: #1890ff;
  color: #fff;
  font-size: 30rpx;
  border-radius: 12rpx;
}
</style>
