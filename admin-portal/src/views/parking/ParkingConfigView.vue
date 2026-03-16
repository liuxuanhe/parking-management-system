<template>
  <div class="parking-config-container">
    <h2 style="margin-bottom: 16px">车位配置</h2>

    <!-- 车位概览统计卡片 -->
    <a-card title="车位概览" :loading="configLoading" style="margin-bottom: 24px">
      <a-row :gutter="24">
        <a-col :span="6">
          <a-statistic title="总车位数" :value="configData.totalSpaces || 0" suffix="个" />
        </a-col>
        <a-col :span="6">
          <a-statistic
            title="当前在场车辆数"
            :value="currentOccupied"
            suffix="辆"
            :value-style="{ color: occupiedColor }"
          />
        </a-col>
        <a-col :span="6">
          <a-statistic
            title="可用车位数"
            :value="availableSpaces"
            suffix="个"
            :value-style="{ color: availableColor }"
          />
        </a-col>
        <a-col :span="6">
          <a-statistic
            title="Visitor 可开放车位数"
            :value="visitorAvailableSpaces"
            suffix="个"
          />
        </a-col>
      </a-row>
    </a-card>

    <!-- 配置详情卡片 -->
    <a-card :loading="configLoading">
      <template #title>
        <span>配置详情</span>
      </template>
      <template #extra>
        <a-button type="primary" @click="openEditModal">修改配置</a-button>
      </template>
      <a-descriptions bordered :column="2">
        <a-descriptions-item label="总车位数">
          {{ configData.totalSpaces || 0 }} 个
        </a-descriptions-item>
        <a-descriptions-item label="预留车位数">
          {{ configData.reservedSpaces || 0 }} 个
        </a-descriptions-item>
        <a-descriptions-item label="月度配额（Monthly_Quota）">
          {{ configData.visitorQuotaHours || 0 }} 小时
        </a-descriptions-item>
        <a-descriptions-item label="单次时长限制">
          {{ configData.visitorSingleDurationHours || 0 }} 小时
        </a-descriptions-item>
        <a-descriptions-item label="Visitor 激活窗口">
          {{ configData.visitorActivationWindowHours || 0 }} 小时
        </a-descriptions-item>
        <a-descriptions-item label="僵尸车辆阈值">
          {{ configData.zombieVehicleThresholdDays || 0 }} 天
        </a-descriptions-item>
        <a-descriptions-item label="版本号">
          {{ configData.version || '-' }}
        </a-descriptions-item>
        <a-descriptions-item label="最后更新时间">
          {{ configData.updateTime || '-' }}
        </a-descriptions-item>
      </a-descriptions>
    </a-card>

    <!-- 修改配置对话框 -->
    <a-modal
      v-model:visible="editModalVisible"
      title="修改车位配置"
      @ok="handleEditConfirm"
      :confirmLoading="editLoading"
      okText="确认修改"
      cancelText="取消"
      :maskClosable="false"
    >
      <a-form
        :model="editForm"
        :label-col="{ span: 8 }"
        :wrapper-col="{ span: 14 }"
        style="margin-top: 16px"
      >
        <a-form-item label="总车位数" required>
          <a-input-number
            v-model:value="editForm.totalSpaces"
            :min="0"
            :max="99999"
            style="width: 100%"
            placeholder="请输入总车位数"
          />
        </a-form-item>
        <a-form-item label="月度配额（小时）">
          <a-input-number
            v-model:value="editForm.visitorQuotaHours"
            :min="0"
            :max="9999"
            style="width: 100%"
            placeholder="默认 72 小时"
          />
        </a-form-item>
        <a-form-item label="单次时长限制（小时）">
          <a-input-number
            v-model:value="editForm.visitorSingleDurationHours"
            :min="0"
            :max="9999"
            style="width: 100%"
            placeholder="默认 24 小时"
          />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { message } from 'ant-design-vue'
import { useAuthStore } from '@/stores/auth'
import { getParkingConfig, updateParkingConfig } from '@/api/parking'

const authStore = useAuthStore()

// ========== 配置数据 ==========
const configData = ref({})
const configLoading = ref(false)

/** 当前在场车辆数（总车位 - 可用车位） */
const currentOccupied = computed(() => {
  const total = configData.value.totalSpaces || 0
  const available = availableSpaces.value
  return total - available
})

/** 可用车位数 */
const availableSpaces = computed(() => {
  // 后端返回的 totalSpaces 减去在场车辆数
  // 如果后端直接返回了相关字段则使用，否则默认为总车位数
  const total = configData.value.totalSpaces || 0
  const reserved = configData.value.reservedSpaces || 0
  return total - reserved >= 0 ? total - reserved : 0
})

/** Visitor 可开放车位数 */
const visitorAvailableSpaces = computed(() => {
  return availableSpaces.value
})

/** 在场车辆数颜色：超过 80% 显示红色 */
const occupiedColor = computed(() => {
  const total = configData.value.totalSpaces || 1
  const ratio = currentOccupied.value / total
  if (ratio >= 0.9) return '#cf1322'
  if (ratio >= 0.7) return '#faad14'
  return '#3f8600'
})

/** 可用车位颜色：低于 20% 显示红色 */
const availableColor = computed(() => {
  const total = configData.value.totalSpaces || 1
  const ratio = availableSpaces.value / total
  if (ratio <= 0.1) return '#cf1322'
  if (ratio <= 0.3) return '#faad14'
  return '#3f8600'
})

// ========== 加载配置 ==========
async function fetchConfig() {
  configLoading.value = true
  try {
    const data = await getParkingConfig({ communityId: authStore.communityId })
    configData.value = data || {}
  } catch (err) {
    // 错误已在 request.js 拦截器中统一提示
  } finally {
    configLoading.value = false
  }
}

// ========== 修改配置对话框 ==========
const editModalVisible = ref(false)
const editLoading = ref(false)
const editForm = ref({
  totalSpaces: 0,
  visitorQuotaHours: 72,
  visitorSingleDurationHours: 24
})

/** 打开修改配置对话框，回填当前值 */
function openEditModal() {
  editForm.value = {
    totalSpaces: configData.value.totalSpaces || 0,
    visitorQuotaHours: configData.value.visitorQuotaHours || 72,
    visitorSingleDurationHours: configData.value.visitorSingleDurationHours || 24
  }
  editModalVisible.value = true
}

/** 确认修改配置 */
async function handleEditConfirm() {
  // 表单校验
  if (editForm.value.totalSpaces === null || editForm.value.totalSpaces === undefined) {
    message.warning('请输入总车位数')
    return
  }

  editLoading.value = true
  try {
    await updateParkingConfig({
      communityId: authStore.communityId,
      totalSpaces: editForm.value.totalSpaces,
      visitorQuotaHours: editForm.value.visitorQuotaHours,
      visitorSingleDurationHours: editForm.value.visitorSingleDurationHours,
      version: configData.value.version || 1
    })
    message.success('配置修改成功')
    editModalVisible.value = false
    // 刷新配置数据
    fetchConfig()
  } catch (err) {
    // PARKING_9002: 新车位数小于当前在场车辆数
    // 错误已在 request.js 拦截器中统一提示
  } finally {
    editLoading.value = false
  }
}

// ========== 初始化加载 ==========
fetchConfig()
</script>

<style scoped>
.parking-config-container {
  padding: 0;
}
</style>
