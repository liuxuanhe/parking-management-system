<template>
  <div class="owner-audit-container">
    <h2 style="margin-bottom: 16px">业主审核</h2>

    <!-- 状态标签页 -->
    <a-tabs v-model:activeKey="activeTab" @change="handleTabChange">
      <a-tab-pane key="pending" tab="待审核" />
      <a-tab-pane key="approved" tab="已通过" />
      <a-tab-pane key="rejected" tab="已驳回" />
      <a-tab-pane key="" tab="全部" />
    </a-tabs>

    <!-- 批量操作栏 -->
    <div class="batch-actions" v-if="activeTab === 'pending'">
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

    <!-- 业主列表表格 -->
    <a-table
      :columns="columns"
      :data-source="ownerList"
      :loading="tableLoading"
      :pagination="pagination"
      :row-selection="activeTab === 'pending' ? rowSelection : undefined"
      row-key="ownerId"
      @change="handleTableChange"
    >
      <!-- 手机号列：脱敏显示 -->
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'phoneNumber'">
          {{ maskPhone(record.phoneNumber) }}
        </template>

        <!-- 状态列 -->
        <template v-else-if="column.dataIndex === 'status'">
          <a-tag :color="statusColorMap[record.status]">
            {{ statusTextMap[record.status] }}
          </a-tag>
        </template>

        <!-- 注册时间列 -->
        <template v-else-if="column.dataIndex === 'createTime'">
          {{ record.createTime }}
        </template>

        <!-- 操作列 -->
        <template v-else-if="column.dataIndex === 'action'">
          <a-space>
            <template v-if="record.status === 'pending'">
              <a-button type="link" size="small" @click="handleApprove(record)">
                通过
              </a-button>
              <a-button type="link" danger size="small" @click="openRejectModal(record)">
                驳回
              </a-button>
            </template>
            <!-- 注销按钮：仅 Super_Admin 可见 -->
            <a-button
              v-if="authStore.role === 'super_admin' && record.status === 'approved'"
              type="link"
              danger
              size="small"
              @click="openDisableModal(record)"
            >
              注销
            </a-button>
          </a-space>
        </template>
      </template>
    </a-table>

    <!-- 驳回原因对话框 -->
    <a-modal
      v-model:visible="rejectModalVisible"
      title="驳回业主申请"
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
      title="批量驳回业主申请"
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

    <!-- 注销业主对话框（Super_Admin 专属） -->
    <a-modal
      v-model:visible="disableModalVisible"
      title="注销业主账号"
      @ok="handleDisableConfirm"
      :confirmLoading="disableLoading"
      okText="确认注销"
      okType="danger"
      cancelText="取消"
    >
      <p style="margin-bottom: 8px; color: #f5222d">
        注意：注销后该业主将无法登录，且关联的车辆数据将被清理。
      </p>
      <p style="margin-bottom: 8px">请填写注销原因：</p>
      <a-textarea
        v-model:value="disableReason"
        placeholder="请输入注销原因"
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
import { getOwnerList, auditOwner, batchAuditOwners, disableOwner } from '@/api/owner'

const authStore = useAuthStore()

// ========== 状态映射 ==========
const statusTextMap = {
  pending: '待审核',
  approved: '已通过',
  rejected: '已驳回'
}
const statusColorMap = {
  pending: 'orange',
  approved: 'green',
  rejected: 'red'
}

// ========== 表格列定义 ==========
const columns = [
  { title: '手机号', dataIndex: 'phoneNumber', width: 140 },
  { title: '房屋号', dataIndex: 'houseNo', width: 120 },
  { title: '身份证后4位', dataIndex: 'idCardLast4', width: 120 },
  { title: '注册时间', dataIndex: 'createTime', width: 180 },
  { title: '状态', dataIndex: 'status', width: 100 },
  { title: '操作', dataIndex: 'action', width: 140, fixed: 'right' }
]

// ========== 数据与加载状态 ==========
const activeTab = ref('pending')
const ownerList = ref([])
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
async function fetchOwnerList() {
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
    const data = await getOwnerList(params)
    ownerList.value = data.records || []
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
  fetchOwnerList()
}

/** 表格分页变化 */
function handleTableChange(pag) {
  pagination.current = pag.current
  pagination.pageSize = pag.pageSize
  fetchOwnerList()
}

// ========== 单个审核：通过 ==========
function handleApprove(record) {
  Modal.confirm({
    title: '确认通过',
    content: `确定通过业主 ${maskPhone(record.phoneNumber)}（房屋号 ${record.houseNo}）的注册申请？`,
    okText: '确认通过',
    cancelText: '取消',
    onOk: async () => {
      try {
        await auditOwner(record.ownerId, { action: 'approve' })
        message.success('审核通过成功')
        fetchOwnerList()
      } catch (err) {
        // 错误已在 request.js 拦截器中统一提示
      }
    }
  })
}

// ========== 单个审核：驳回 ==========
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
    await auditOwner(currentRejectRecord.ownerId, {
      action: 'reject',
      rejectReason: rejectReason.value.trim()
    })
    message.success('已驳回该申请')
    rejectModalVisible.value = false
    fetchOwnerList()
  } catch (err) {
    // 错误已在 request.js 拦截器中统一提示
  } finally {
    rejectLoading.value = false
  }
}

// ========== 批量审核：通过 ==========
function handleBatchApprove() {
  Modal.confirm({
    title: '批量通过',
    content: `确定批量通过选中的 ${selectedRowKeys.value.length} 条申请？`,
    okText: '确认通过',
    cancelText: '取消',
    onOk: async () => {
      try {
        const result = await batchAuditOwners({
          ownerIds: selectedRowKeys.value,
          action: 'approve'
        }, { adminId: authStore.adminId, communityId: authStore.communityId })
        message.success(`批量通过完成：成功 ${result.successCount} 条，失败 ${result.failCount} 条`)
        selectedRowKeys.value = []
        fetchOwnerList()
      } catch (err) {
        // 错误已在 request.js 拦截器中统一提示
      }
    }
  })
}

// ========== 批量审核：驳回 ==========
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
    const result = await batchAuditOwners({
      ownerIds: selectedRowKeys.value,
      action: 'reject',
      rejectReason: batchRejectReason.value.trim()
    }, { adminId: authStore.adminId, communityId: authStore.communityId })
    message.success(`批量驳回完成：成功 ${result.successCount} 条，失败 ${result.failCount} 条`)
    batchRejectModalVisible.value = false
    selectedRowKeys.value = []
    fetchOwnerList()
  } catch (err) {
    // 错误已在 request.js 拦截器中统一提示
  } finally {
    batchRejectLoading.value = false
  }
}

// ========== 注销业主（Super_Admin 专属） ==========
const disableModalVisible = ref(false)
const disableReason = ref('')
const disableLoading = ref(false)
let currentDisableRecord = null

function openDisableModal(record) {
  currentDisableRecord = record
  disableReason.value = ''
  disableModalVisible.value = true
}

async function handleDisableConfirm() {
  if (!disableReason.value.trim()) {
    message.warning('请填写注销原因')
    return
  }
  disableLoading.value = true
  try {
    await disableOwner(currentDisableRecord.ownerId, {
      reason: disableReason.value.trim()
    })
    message.success('业主账号已注销')
    disableModalVisible.value = false
    fetchOwnerList()
  } catch (err) {
    // 错误已在 request.js 拦截器中统一提示
  } finally {
    disableLoading.value = false
  }
}

// ========== 初始化加载 ==========
fetchOwnerList()
</script>

<style scoped>
.owner-audit-container {
  padding: 0;
}

.batch-actions {
  margin-bottom: 16px;
}
</style>
