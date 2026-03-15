<template>
  <div class="zombie-vehicle-container">
    <h2 style="margin-bottom: 16px">僵尸车辆处理</h2>

    <!-- 状态标签页筛选 -->
    <a-tabs v-model:activeKey="activeTab" @change="handleTabChange">
      <a-tab-pane key="unhandled" tab="未处理" />
      <a-tab-pane key="contacted" tab="已联系" />
      <a-tab-pane key="resolved" tab="已解决" />
      <a-tab-pane key="ignored" tab="已忽略" />
      <a-tab-pane key="" tab="全部" />
    </a-tabs>

    <!-- 僵尸车辆列表表格 -->
    <a-table
      :columns="columns"
      :data-source="zombieList"
      :loading="tableLoading"
      :pagination="pagination"
      row-key="id"
      @change="handleTableChange"
    >
      <template #bodyCell="{ column, record }">
        <!-- 连续天数列：超过14天红色，超过7天橙色 -->
        <template v-if="column.dataIndex === 'continuousDays'">
          <span :style="{ color: getDaysColor(record.continuousDays), fontWeight: 'bold' }">
            {{ record.continuousDays }} 天
          </span>
        </template>

        <!-- 状态列 -->
        <template v-else-if="column.dataIndex === 'status'">
          <a-tag :color="statusColorMap[record.status]">
            {{ statusTextMap[record.status] }}
          </a-tag>
        </template>

        <!-- 操作列：仅 unhandled 状态显示处理按钮 -->
        <template v-else-if="column.dataIndex === 'action'">
          <a-button
            v-if="record.status === 'unhandled'"
            type="link"
            size="small"
            @click="openHandleModal(record)"
          >
            处理
          </a-button>
          <span v-else style="color: #999">—</span>
        </template>
      </template>
    </a-table>

    <!-- 处理对话框 -->
    <a-modal
      v-model:open="handleModalVisible"
      title="处理僵尸车辆"
      @ok="handleConfirm"
      :confirmLoading="handleLoading"
      okText="确认处理"
      cancelText="取消"
    >
      <div v-if="currentRecord" style="margin-bottom: 16px">
        <p>车牌号：<strong>{{ currentRecord.carNumber }}</strong></p>
        <p>入场时间：{{ currentRecord.enterTime }}</p>
        <p>连续在场：<strong>{{ currentRecord.continuousDays }}</strong> 天</p>
      </div>

      <a-form layout="vertical">
        <a-form-item label="处理方式" required>
          <a-radio-group v-model:value="handleForm.handleType">
            <a-radio value="contacted">已联系车主</a-radio>
            <a-radio value="resolved">已解决</a-radio>
            <a-radio value="ignored">忽略</a-radio>
          </a-radio-group>
        </a-form-item>

        <a-form-item label="处理记录" required>
          <a-textarea
            v-model:value="handleForm.handleRecord"
            :placeholder="handleRecordPlaceholder"
            :rows="4"
            :maxlength="500"
            showCount
          />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { message } from 'ant-design-vue'
import { useAuthStore } from '@/stores/auth'
import { getZombieVehicleList, handleZombieVehicle } from '@/api/zombie'

const authStore = useAuthStore()

// ========== 状态映射 ==========
const statusTextMap = {
  unhandled: '未处理',
  contacted: '已联系',
  resolved: '已解决',
  ignored: '已忽略'
}
const statusColorMap = {
  unhandled: 'red',
  contacted: 'orange',
  resolved: 'green',
  ignored: 'gray'
}

// ========== 表格列定义 ==========
const columns = [
  { title: '车牌号', dataIndex: 'carNumber', width: 140 },
  { title: '房屋号', dataIndex: 'houseNo', width: 120 },
  { title: '入场时间', dataIndex: 'enterTime', width: 180 },
  { title: '连续天数', dataIndex: 'continuousDays', width: 110 },
  { title: '状态', dataIndex: 'status', width: 100 },
  { title: '处理时间', dataIndex: 'handleTime', width: 180 },
  { title: '操作', dataIndex: 'action', width: 100, fixed: 'right' }
]

// ========== 数据与加载状态 ==========
const activeTab = ref('unhandled')
const zombieList = ref([])
const tableLoading = ref(false)

const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
  showSizeChanger: true,
  showTotal: (total) => `共 ${total} 条`
})

// ========== 连续天数颜色 ==========
function getDaysColor(days) {
  if (days >= 14) return '#f5222d'
  if (days >= 7) return '#fa8c16'
  return 'inherit'
}

// ========== 数据加载 ==========
async function fetchZombieList() {
  tableLoading.value = true
  try {
    const params = {
      communityId: authStore.communityId,
      page: pagination.current,
      pageSize: pagination.pageSize
    }
    // 仅在选择了具体状态时传 status 参数
    if (activeTab.value) {
      params.status = activeTab.value
    }
    const data = await getZombieVehicleList(params)
    // 兼容后端返回数组或分页对象
    if (Array.isArray(data)) {
      zombieList.value = data
      pagination.total = data.length
    } else {
      zombieList.value = data.records || []
      pagination.total = data.total || 0
    }
  } catch (err) {
    // 错误已在 request.js 拦截器中统一提示
  } finally {
    tableLoading.value = false
  }
}

/** 标签页切换 */
function handleTabChange() {
  pagination.current = 1
  fetchZombieList()
}

/** 表格分页变化 */
function handleTableChange(pag) {
  pagination.current = pag.current
  pagination.pageSize = pag.pageSize
  fetchZombieList()
}

// ========== 处理对话框 ==========
const handleModalVisible = ref(false)
const handleLoading = ref(false)
const currentRecord = ref(null)

const handleForm = reactive({
  handleType: 'contacted',
  handleRecord: ''
})

/** 处理记录占位文本，根据处理方式动态变化 */
const handleRecordPlaceholder = computed(() => {
  const map = {
    contacted: '请填写联系记录，如联系方式、沟通内容等',
    resolved: '请填写解决方案，如车辆已移走等',
    ignored: '请填写忽略原因'
  }
  return map[handleForm.handleType] || '请填写处理记录'
})

/** 打开处理对话框 */
function openHandleModal(record) {
  currentRecord.value = record
  handleForm.handleType = 'contacted'
  handleForm.handleRecord = ''
  handleModalVisible.value = true
}

/** 确认处理 */
async function handleConfirm() {
  if (!handleForm.handleRecord.trim()) {
    message.warning('请填写处理记录')
    return
  }
  handleLoading.value = true
  try {
    await handleZombieVehicle(currentRecord.value.id, {
      handleType: handleForm.handleType,
      handleRecord: handleForm.handleRecord.trim()
    })
    message.success('处理成功')
    handleModalVisible.value = false
    fetchZombieList()
  } catch (err) {
    // 错误已在 request.js 拦截器中统一提示
  } finally {
    handleLoading.value = false
  }
}

// ========== 初始化加载 ==========
fetchZombieList()
</script>

<style scoped>
.zombie-vehicle-container {
  padding: 0;
}
</style>
