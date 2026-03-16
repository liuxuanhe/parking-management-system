<template>
  <div class="visitor-audit-container">
    <h2 style="margin-bottom: 16px">Visitor 审批</h2>

    <!-- 状态标签页 -->
    <a-tabs v-model:activeKey="activeTab" @change="handleTabChange">
      <a-tab-pane key="submitted" tab="待审批" />
      <a-tab-pane key="approved_pending_activation" tab="已通过" />
      <a-tab-pane key="rejected" tab="已驳回" />
      <a-tab-pane key="" tab="全部" />
    </a-tabs>

    <!-- 批量操作栏：仅 submitted 标签页显示 -->
    <div class="batch-actions" v-if="activeTab === 'submitted'">
      <a-space>
        <a-button
          type="primary"
          :disabled="selectedRowKeys.length === 0"
          @click="handleBatchApprove"
        >
          批量通过 ({{ selectedRowKeys.length }})
        </a-button>
        <a-button
          danger
          :disabled="selectedRowKeys.length === 0"
          @click="handleBatchReject"
        >
          批量驳回 ({{ selectedRowKeys.length }})
        </a-button>
      </a-space>
    </div>

    <!-- Visitor 申请列表表格 -->
    <a-table
      :columns="columns"
      :data-source="visitorList"
      :loading="tableLoading"
      :pagination="pagination"
      :row-selection="activeTab === 'submitted' ? rowSelection : undefined"
      row-key="visitorId"
      @change="handleTableChange"
    >
      <template #bodyCell="{ column, record }">
        <!-- 业主手机号列：脱敏显示 -->
        <template v-if="column.dataIndex === 'ownerPhone'">
          {{ maskPhone(record.ownerPhone) }}
        </template>

        <!-- 状态列 -->
        <template v-else-if="column.dataIndex === 'status'">
          <a-tag :color="statusColorMap[record.status]">
            {{ statusTextMap[record.status] }}
          </a-tag>
        </template>

        <!-- 申请时间列 -->
        <template v-else-if="column.dataIndex === 'createTime'">
          {{ record.createTime }}
        </template>

        <!-- 操作列 -->
        <template v-else-if="column.dataIndex === 'action'">
          <a-space v-if="record.status === 'submitted'">
            <a-button type="link" size="small" @click="handleApprove(record)">
              通过
            </a-button>
            <a-button type="link" danger size="small" @click="openRejectModal(record)">
              驳回
            </a-button>
          </a-space>
          <span v-else style="color: #999">—</span>
        </template>
      </template>
    </a-table>

    <!-- 驳回原因对话框 -->
    <a-modal
      v-model:visible="rejectModalVisible"
      title="驳回 Visitor 申请"
      @ok="handleRejectConfirm"
      :confirmLoading="rejectLoading"
      okText="确认驳回"
      cancelText="取消"
    >
      <p style="margin-bottom: 8px">
        请填写驳回原因（将通知业主）：
      </p>
      <a-textarea
        v-model:value="rejectReason"
        placeholder="请输入驳回原因"
        :rows="4"
        :maxlength="200"
        showCount
      />
    </a-modal>

    <!-- 批量驳回原因对话框 -->
    <a-modal
      v-model:visible="batchRejectModalVisible"
      title="批量驳回 Visitor 申请"
      @ok="handleBatchRejectConfirm"
      :confirmLoading="batchRejectLoading"
      okText="确认驳回"
      cancelText="取消"
    >
      <p style="margin-bottom: 8px">
        即将驳回 <strong>{{ selectedRowKeys.length }}</strong> 条申请，请填写驳回原因：
      </p>
      <a-textarea
        v-model:value="batchRejectReason"
        placeholder="请输入驳回原因"
        :rows="4"
        :maxlength="200"
        showCount
      />
    </a-modal>
  </div>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { useAuthStore } from '@/stores/auth'
import { getVisitorList, auditVisitor, batchAuditVisitors } from '@/api/visitor'

const authStore = useAuthStore()

// ========== 状态映射 ==========
const statusTextMap = {
  submitted: '待审批',
  approved_pending_activation: '已通过待激活',
  activated: '已激活',
  rejected: '已驳回',
  canceled_no_entry: '未入场取消',
  expired: '已过期'
}
const statusColorMap = {
  submitted: 'orange',
  approved_pending_activation: 'cyan',
  activated: 'green',
  rejected: 'red',
  canceled_no_entry: 'gray',
  expired: 'gray'
}

// ========== 表格列定义 ==========
const columns = [
  { title: '车牌号', dataIndex: 'carNumber', width: 140 },
  { title: '房屋号', dataIndex: 'houseNo', width: 120 },
  { title: '业主手机号', dataIndex: 'ownerPhone', width: 140 },
  { title: '申请原因', dataIndex: 'applyReason', width: 200, ellipsis: true },
  { title: '申请时间', dataIndex: 'createTime', width: 180 },
  { title: '状态', dataIndex: 'status', width: 130 },
  { title: '操作', dataIndex: 'action', width: 140, fixed: 'right' }
]

// ========== 数据与加载状态 ==========
const activeTab = ref('submitted')
const visitorList = ref([])
const tableLoading = ref(false)
const selectedRowKeys = ref([])

const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
  showSizeChanger: true,
  showTotal: (total) => `共 ${total} 条`
})

/** 行选择配置 */
const rowSelection = computed(() => ({
  selectedRowKeys: selectedRowKeys.value,
  onChange: (keys) => {
    selectedRowKeys.value = keys
  }
}))

// ========== 手机号脱敏 ==========
function maskPhone(phone) {
  if (!phone || phone.length < 7) return phone
  return phone.substring(0, 3) + '****' + phone.substring(phone.length - 4)
}

// ========== 数据加载 ==========
async function fetchVisitorList() {
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
    const data = await getVisitorList(params)
    visitorList.value = data.records || []
    pagination.total = data.total || 0
  } catch (err) {
    // 错误已在 request.js 拦截器中统一提示
  } finally {
    tableLoading.value = false
  }
}

/** 标签页切换 */
function handleTabChange() {
  pagination.current = 1
  selectedRowKeys.value = []
  fetchVisitorList()
}

/** 表格分页变化 */
function handleTableChange(pag) {
  pagination.current = pag.current
  pagination.pageSize = pag.pageSize
  fetchVisitorList()
}

// ========== 单个审批：通过 ==========
function handleApprove(record) {
  Modal.confirm({
    title: '确认通过',
    content: `确定通过车牌 ${record.carNumber}（房屋号 ${record.houseNo}）的 Visitor 申请？`,
    okText: '确认通过',
    cancelText: '取消',
    onOk: async () => {
      try {
        await auditVisitor(record.visitorId, { action: 'approve' }, { adminId: authStore.adminId, communityId: authStore.communityId })
        message.success('审批通过成功')
        fetchVisitorList()
      } catch (err) {
        // 错误已在 request.js 拦截器中统一提示
      }
    }
  })
}

// ========== 单个审批：驳回 ==========
const rejectModalVisible = ref(false)
const rejectReason = ref('')
const rejectLoading = ref(false)
let currentRejectRecord = null

function openRejectModal(record) {
  currentRejectRecord = record
  rejectReason.value = ''
  rejectModalVisible.value = true
}

async function handleRejectConfirm() {
  if (!rejectReason.value.trim()) {
    message.warning('请填写驳回原因')
    return
  }
  rejectLoading.value = true
  try {
    await auditVisitor(currentRejectRecord.visitorId, {
      action: 'reject',
      rejectReason: rejectReason.value.trim()
    }, { adminId: authStore.adminId, communityId: authStore.communityId })
    message.success('已驳回该申请')
    rejectModalVisible.value = false
    fetchVisitorList()
  } catch (err) {
    // 错误已在 request.js 拦截器中统一提示
  } finally {
    rejectLoading.value = false
  }
}

// ========== 批量审批：通过 ==========
function handleBatchApprove() {
  Modal.confirm({
    title: '批量通过',
    content: `确定批量通过选中的 ${selectedRowKeys.value.length} 条 Visitor 申请？`,
    okText: '确认通过',
    cancelText: '取消',
    onOk: async () => {
      try {
        const result = await batchAuditVisitors({
          visitorIds: selectedRowKeys.value,
          action: 'approve'
        }, { adminId: authStore.adminId, communityId: authStore.communityId })
        message.success(`批量通过完成：成功 ${result.successCount} 条，失败 ${result.failCount} 条`)
        selectedRowKeys.value = []
        fetchVisitorList()
      } catch (err) {
        // 错误已在 request.js 拦截器中统一提示
      }
    }
  })
}

// ========== 批量审批：驳回 ==========
const batchRejectModalVisible = ref(false)
const batchRejectReason = ref('')
const batchRejectLoading = ref(false)

function handleBatchReject() {
  batchRejectReason.value = ''
  batchRejectModalVisible.value = true
}

async function handleBatchRejectConfirm() {
  if (!batchRejectReason.value.trim()) {
    message.warning('请填写驳回原因')
    return
  }
  batchRejectLoading.value = true
  try {
    const result = await batchAuditVisitors({
      visitorIds: selectedRowKeys.value,
      action: 'reject',
      rejectReason: batchRejectReason.value.trim()
    }, { adminId: authStore.adminId, communityId: authStore.communityId })
    message.success(`批量驳回完成：成功 ${result.successCount} 条，失败 ${result.failCount} 条`)
    batchRejectModalVisible.value = false
    selectedRowKeys.value = []
    fetchVisitorList()
  } catch (err) {
    // 错误已在 request.js 拦截器中统一提示
  } finally {
    batchRejectLoading.value = false
  }
}

// ========== 初始化加载 ==========
fetchVisitorList()
</script>

<style scoped>
.visitor-audit-container {
  padding: 0;
}

.batch-actions {
  margin-bottom: 16px;
}
</style>
