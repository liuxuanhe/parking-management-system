<template>
  <div class="admin-manage-container">
    <h2 style="margin-bottom: 16px">管理员管理</h2>

    <!-- 操作栏 -->
    <div style="margin-bottom: 16px">
      <a-button type="primary" @click="showCreateModal">创建物业管理员</a-button>
    </div>

    <!-- 管理员列表表格 -->
    <a-table
      :columns="columns"
      :data-source="adminList"
      :loading="tableLoading"
      row-key="id"
    >
      <template #bodyCell="{ column, record }">
        <!-- 角色列 -->
        <template v-if="column.dataIndex === 'role'">
          <a-tag :color="record.role === 'super_admin' ? 'red' : 'blue'">
            {{ record.role === 'super_admin' ? '超级管理员' : '物业管理员' }}
          </a-tag>
        </template>
        <!-- 状态列 -->
        <template v-else-if="column.dataIndex === 'status'">
          <a-tag :color="statusColorMap[record.status]">
            {{ statusTextMap[record.status] }}
          </a-tag>
        </template>
        <!-- 操作列 -->
        <template v-else-if="column.dataIndex === 'action'">
          <a-space>
            <a-button
              v-if="record.status === 'locked'"
              type="link"
              size="small"
              @click="handleUnlock(record)"
            >
              解锁
            </a-button>
            <a-button
              type="link"
              size="small"
              @click="handleResetPassword(record)"
            >
              重置密码
            </a-button>
          </a-space>
        </template>
      </template>
    </a-table>

    <!-- 创建管理员对话框 -->
    <a-modal
      v-model:open="createModalVisible"
      title="创建物业管理员"
      @ok="handleCreateConfirm"
      :confirmLoading="createLoading"
      okText="确认创建"
      cancelText="取消"
    >
      <a-form :model="createForm" layout="vertical">
        <a-form-item label="用户名" required>
          <a-input v-model:value="createForm.username" placeholder="请输入用户名" />
        </a-form-item>
        <a-form-item label="姓名" required>
          <a-input v-model:value="createForm.realName" placeholder="请输入姓名" />
        </a-form-item>
        <a-form-item label="手机号" required>
          <a-input v-model:value="createForm.phoneNumber" placeholder="请输入手机号" />
        </a-form-item>
        <a-form-item label="所属小区" required>
          <a-select v-model:value="createForm.communityId" placeholder="请选择小区">
            <a-select-option v-for="c in communityList" :key="c.id" :value="c.id">
              {{ c.communityName }}
            </a-select-option>
          </a-select>
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 创建成功提示（显示初始密码） -->
    <a-modal
      v-model:open="passwordModalVisible"
      title="创建成功"
      :footer="null"
    >
      <a-result status="success" title="管理员创建成功">
        <template #extra>
          <p>初始密码：<strong>{{ createdPassword }}</strong></p>
          <p style="color: #999">请妥善保管，首次登录需修改密码</p>
        </template>
      </a-result>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { getAdminList, createAdmin, unlockAdmin, resetAdminPassword } from '@/api/admin'
import { getCommunityList } from '@/api/community'

// ========== 状态映射 ==========
const statusTextMap = { active: '正常', locked: '已锁定', disabled: '已停用' }
const statusColorMap = { active: 'green', locked: 'red', disabled: 'default' }

// ========== 表格列定义 ==========
const columns = [
  { title: '用户名', dataIndex: 'username', width: 120 },
  { title: '姓名', dataIndex: 'realName', width: 100 },
  { title: '角色', dataIndex: 'role', width: 120 },
  { title: '所属小区', dataIndex: 'communityName', width: 150 },
  { title: '手机号', dataIndex: 'phoneNumber', width: 140 },
  { title: '状态', dataIndex: 'status', width: 100 },
  { title: '最后登录', dataIndex: 'lastLoginTime', width: 180 },
  { title: '操作', dataIndex: 'action', width: 160, fixed: 'right' }
]

// ========== 数据 ==========
const adminList = ref([])
const tableLoading = ref(false)
const communityList = ref([])

// ========== 创建表单 ==========
const createModalVisible = ref(false)
const createLoading = ref(false)
const createForm = ref({ username: '', realName: '', phoneNumber: '', communityId: undefined })

// ========== 创建成功密码展示 ==========
const passwordModalVisible = ref(false)
const createdPassword = ref('')

/** 加载管理员列表 */
async function fetchAdminList() {
  tableLoading.value = true
  try {
    const data = await getAdminList()
    adminList.value = Array.isArray(data) ? data : []
  } catch (err) {
    // 错误已在 request.js 拦截器中统一提示
  } finally {
    tableLoading.value = false
  }
}

/** 加载小区列表（创建表单下拉用） */
async function loadCommunities() {
  try {
    const data = await getCommunityList()
    communityList.value = Array.isArray(data) ? data : []
  } catch (err) {
    // 忽略
  }
}

/** 打开创建对话框 */
function showCreateModal() {
  createForm.value = { username: '', realName: '', phoneNumber: '', communityId: undefined }
  createModalVisible.value = true
}

/** 确认创建 */
async function handleCreateConfirm() {
  const { username, realName, phoneNumber, communityId } = createForm.value
  if (!username || !realName || !phoneNumber || !communityId) {
    message.warning('请填写完整信息')
    return
  }
  createLoading.value = true
  try {
    const data = await createAdmin({
      username, realName, phoneNumber, communityId, role: 'property_admin'
    })
    createModalVisible.value = false
    createdPassword.value = data.initialPassword || ''
    passwordModalVisible.value = true
    fetchAdminList()
  } catch (err) {
    // 错误已在 request.js 拦截器中统一提示
  } finally {
    createLoading.value = false
  }
}

/** 解锁管理员 */
function handleUnlock(record) {
  Modal.confirm({
    title: '确认解锁',
    content: `确定解锁管理员「${record.realName}」的账号？`,
    okText: '确认',
    cancelText: '取消',
    onOk: async () => {
      try {
        await unlockAdmin(record.id)
        message.success('解锁成功')
        fetchAdminList()
      } catch (err) {
        // 错误已在 request.js 拦截器中统一提示
      }
    }
  })
}

/** 重置密码 */
function handleResetPassword(record) {
  Modal.confirm({
    title: '确认重置密码',
    content: `确定重置管理员「${record.realName}」的密码？重置后需首次登录修改。`,
    okText: '确认',
    cancelText: '取消',
    onOk: async () => {
      try {
        const data = await resetAdminPassword(record.id)
        Modal.success({
          title: '密码已重置',
          content: `新密码：${data.newPassword}，请妥善保管。`
        })
        fetchAdminList()
      } catch (err) {
        // 错误已在 request.js 拦截器中统一提示
      }
    }
  })
}

onMounted(() => {
  fetchAdminList()
  loadCommunities()
})
</script>

<style scoped>
.admin-manage-container {
  padding: 0;
}
</style>
