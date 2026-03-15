<template>
  <div class="ip-whitelist-container">
    <h2 style="margin-bottom: 16px">IP 白名单管理</h2>

    <!-- 操作栏 -->
    <div style="margin-bottom: 16px">
      <a-button type="primary" @click="showAddModal">添加 IP</a-button>
    </div>

    <!-- 白名单列表表格 -->
    <a-table
      :columns="columns"
      :data-source="whitelistData"
      :loading="tableLoading"
      row-key="id"
    >
      <template #bodyCell="{ column, record }">
        <!-- 操作类型列 -->
        <template v-if="column.dataIndex === 'operationType'">
          <a-tag color="blue">{{ record.operationType }}</a-tag>
        </template>
        <!-- 操作列 -->
        <template v-else-if="column.dataIndex === 'action'">
          <a-button type="link" danger size="small" @click="handleDelete(record)">
            删除
          </a-button>
        </template>
      </template>
    </a-table>

    <!-- 添加 IP 对话框 -->
    <a-modal
      v-model:open="addModalVisible"
      title="添加 IP 白名单"
      @ok="handleAddConfirm"
      :confirmLoading="addLoading"
      okText="确认添加"
      cancelText="取消"
    >
      <a-form :model="addForm" layout="vertical">
        <a-form-item label="IP 地址" required>
          <a-input v-model:value="addForm.ipAddress" placeholder="请输入 IP 地址，如 192.168.1.100" />
        </a-form-item>
        <a-form-item label="操作类型" required>
          <a-select v-model:value="addForm.operationType" placeholder="请选择操作类型">
            <a-select-option value="EXPORT">数据导出</a-select-option>
            <a-select-option value="HIGH_RISK">高危操作</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="描述">
          <a-input v-model:value="addForm.description" placeholder="请输入描述（可选）" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { useAuthStore } from '@/stores/auth'
import { getIpWhitelistList, addIpWhitelist, deleteIpWhitelist } from '@/api/ipWhitelist'

const authStore = useAuthStore()

// ========== 表格列定义 ==========
const columns = [
  { title: 'IP 地址', dataIndex: 'ipAddress', width: 180 },
  { title: '操作类型', dataIndex: 'operationType', width: 120 },
  { title: '描述', dataIndex: 'description', width: 200 },
  { title: '创建时间', dataIndex: 'createTime', width: 180 },
  { title: '操作', dataIndex: 'action', width: 100, fixed: 'right' }
]

// ========== 数据 ==========
const whitelistData = ref([])
const tableLoading = ref(false)

// ========== 添加表单 ==========
const addModalVisible = ref(false)
const addLoading = ref(false)
const addForm = ref({ ipAddress: '', operationType: undefined, description: '' })

/** 加载白名单列表 */
async function fetchWhitelist() {
  tableLoading.value = true
  try {
    const data = await getIpWhitelistList()
    whitelistData.value = Array.isArray(data) ? data : []
  } catch (err) {
    // 错误已在 request.js 拦截器中统一提示
  } finally {
    tableLoading.value = false
  }
}

/** 打开添加对话框 */
function showAddModal() {
  addForm.value = { ipAddress: '', operationType: undefined, description: '' }
  addModalVisible.value = true
}

/** 确认添加 */
async function handleAddConfirm() {
  const { ipAddress, operationType, description } = addForm.value
  if (!ipAddress || !operationType) {
    message.warning('请填写 IP 地址和操作类型')
    return
  }
  addLoading.value = true
  try {
    await addIpWhitelist({
      communityId: authStore.communityId,
      ipAddress,
      operationType,
      description,
      adminId: authStore.adminId
    })
    message.success('添加成功')
    addModalVisible.value = false
    fetchWhitelist()
  } catch (err) {
    // 错误已在 request.js 拦截器中统一提示
  } finally {
    addLoading.value = false
  }
}

/** 删除白名单 */
function handleDelete(record) {
  Modal.confirm({
    title: '确认删除',
    content: `确定删除 IP「${record.ipAddress}」？`,
    okText: '确认删除',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      try {
        await deleteIpWhitelist(record.id)
        message.success('删除成功')
        fetchWhitelist()
      } catch (err) {
        // 错误已在 request.js 拦截器中统一提示
      }
    }
  })
}

onMounted(() => {
  fetchWhitelist()
})
</script>

<style scoped>
.ip-whitelist-container {
  padding: 0;
}
</style>
