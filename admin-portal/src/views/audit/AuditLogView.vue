<template>
  <div class="audit-log-container">
    <h2 style="margin-bottom: 16px">审计日志查询</h2>

    <!-- 操作日志 / 访问日志 标签页 -->
    <a-tabs v-model:activeKey="activeTab" @change="handleTabChange">
      <!-- ========== 操作日志 Tab ========== -->
      <a-tab-pane key="operation" tab="操作日志">
        <!-- 筛选区域 -->
        <a-form layout="inline" style="margin-bottom: 16px">
          <a-form-item label="操作类型">
            <a-select
              v-model:value="operationFilter.operationType"
              placeholder="请选择操作类型"
              allow-clear
              style="width: 160px"
            >
              <a-select-option value="CREATE">创建</a-select-option>
              <a-select-option value="UPDATE">更新</a-select-option>
              <a-select-option value="DELETE">删除</a-select-option>
              <a-select-option value="AUDIT">审核</a-select-option>
              <a-select-option value="EXPORT">导出</a-select-option>
              <a-select-option value="LOGIN">登录</a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item label="操作人">
            <a-input
              v-model:value="operationFilter.operatorName"
              placeholder="请输入操作人"
              allow-clear
              style="width: 150px"
            />
          </a-form-item>
          <a-form-item label="时间范围">
            <a-range-picker
              v-model:value="operationFilter.timeRange"
              show-time
              format="YYYY-MM-DD HH:mm:ss"
              :placeholder="['开始时间', '结束时间']"
            />
          </a-form-item>
          <a-form-item>
            <a-button type="primary" @click="fetchOperationLogs">查询</a-button>
            <a-button style="margin-left: 8px" @click="resetOperationFilter">重置</a-button>
            <a-button v-if="authStore.role === 'super_admin'" style="margin-left: 8px" @click="handleExport">导出</a-button>
          </a-form-item>
        </a-form>

        <!-- 操作日志表格 -->
        <a-table
          :columns="operationColumns"
          :data-source="operationLogs"
          :loading="operationLoading"
          :pagination="operationPagination"
          row-key="id"
          :expandable="{ expandedRowRender: operationExpandRender }"
          @change="handleOperationTableChange"
        >
          <template #bodyCell="{ column, record }">
            <!-- 操作类型列：使用标签展示 -->
            <template v-if="column.dataIndex === 'operationType'">
              <a-tag :color="operationTypeColor(record.operationType)">
                {{ operationTypeText(record.operationType) }}
              </a-tag>
            </template>
            <!-- 操作结果列 -->
            <template v-else-if="column.dataIndex === 'operationResult'">
              <a-tag :color="record.operationResult === 'SUCCESS' ? 'green' : 'red'">
                {{ record.operationResult === 'SUCCESS' ? '成功' : '失败' }}
              </a-tag>
            </template>
          </template>

          <!-- 展开行：显示 before/after 值对比 -->
          <template #expandedRowRender="{ record }">
            <div style="padding: 8px 16px">
              <a-descriptions :column="1" bordered size="small">
                <a-descriptions-item label="操作目标">
                  {{ record.targetType }} (ID: {{ record.targetId }})
                </a-descriptions-item>
                <a-descriptions-item label="变更前 (Before)">
                  <pre style="margin: 0; white-space: pre-wrap; word-break: break-all">{{ formatJson(record.beforeValue) }}</pre>
                </a-descriptions-item>
                <a-descriptions-item label="变更后 (After)">
                  <pre style="margin: 0; white-space: pre-wrap; word-break: break-all">{{ formatJson(record.afterValue) }}</pre>
                </a-descriptions-item>
                <a-descriptions-item v-if="record.errorMessage" label="错误信息">
                  <span style="color: #f5222d">{{ record.errorMessage }}</span>
                </a-descriptions-item>
              </a-descriptions>
            </div>
          </template>
        </a-table>
      </a-tab-pane>

      <!-- ========== 访问日志 Tab ========== -->
      <a-tab-pane key="access" tab="访问日志">
        <!-- 筛选区域 -->
        <a-form layout="inline" style="margin-bottom: 16px">
          <a-form-item label="请求路径">
            <a-input
              v-model:value="accessFilter.apiPath"
              placeholder="请输入请求路径"
              allow-clear
              style="width: 200px"
            />
          </a-form-item>
          <a-form-item label="IP 地址">
            <a-input
              v-model:value="accessFilter.userIp"
              placeholder="请输入 IP 地址"
              allow-clear
              style="width: 150px"
            />
          </a-form-item>
          <a-form-item label="时间范围">
            <a-range-picker
              v-model:value="accessFilter.timeRange"
              show-time
              format="YYYY-MM-DD HH:mm:ss"
              :placeholder="['开始时间', '结束时间']"
            />
          </a-form-item>
          <a-form-item>
            <a-button type="primary" @click="fetchAccessLogs">查询</a-button>
            <a-button style="margin-left: 8px" @click="resetAccessFilter">重置</a-button>
          </a-form-item>
        </a-form>

        <!-- 访问日志表格 -->
        <a-table
          :columns="accessColumns"
          :data-source="accessLogs"
          :loading="accessLoading"
          :pagination="accessPagination"
          row-key="id"
          @change="handleAccessTableChange"
        >
          <template #bodyCell="{ column, record }">
            <!-- 请求方法列 -->
            <template v-if="column.dataIndex === 'httpMethod'">
              <a-tag :color="httpMethodColor(record.httpMethod)">
                {{ record.httpMethod }}
              </a-tag>
            </template>
            <!-- 响应状态码列 -->
            <template v-else-if="column.dataIndex === 'responseCode'">
              <a-tag :color="record.responseCode >= 200 && record.responseCode < 300 ? 'green' : 'red'">
                {{ record.responseCode }}
              </a-tag>
            </template>
            <!-- 响应时间列 -->
            <template v-else-if="column.dataIndex === 'responseTime'">
              <span :style="{ color: record.responseTime > 1000 ? '#f5222d' : 'inherit' }">
                {{ record.responseTime }} ms
              </span>
            </template>
          </template>
        </a-table>
      </a-tab-pane>
    </a-tabs>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { message } from 'ant-design-vue'
import dayjs from 'dayjs'
import { useAuthStore } from '@/stores/auth'
import { getOperationLogs, getAccessLogs, exportAuditLogs } from '@/api/audit'

const authStore = useAuthStore()

// ========== 当前激活的标签页 ==========
const activeTab = ref('operation')

// ========== 操作日志相关 ==========
const operationFilter = reactive({
  operationType: undefined,
  operatorName: '',
  timeRange: []
})

const operationLogs = ref([])
const operationLoading = ref(false)
const operationPagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
  showSizeChanger: true,
  showTotal: (total) => `共 ${total} 条`
})

/** 操作日志表格列定义 */
const operationColumns = [
  { title: '操作时间', dataIndex: 'operationTime', width: 180 },
  { title: '操作人', dataIndex: 'operatorName', width: 120 },
  { title: '操作类型', dataIndex: 'operationType', width: 100 },
  { title: '操作描述', dataIndex: 'targetType', width: 150 },
  { title: 'IP 地址', dataIndex: 'operatorIp', width: 140 },
  { title: '操作结果', dataIndex: 'operationResult', width: 100 }
]

/** 操作类型颜色映射 */
function operationTypeColor(type) {
  const map = {
    CREATE: 'blue',
    UPDATE: 'orange',
    DELETE: 'red',
    AUDIT: 'purple',
    EXPORT: 'cyan',
    LOGIN: 'green'
  }
  return map[type] || 'default'
}

/** 操作类型文本映射 */
function operationTypeText(type) {
  const map = {
    CREATE: '创建',
    UPDATE: '更新',
    DELETE: '删除',
    AUDIT: '审核',
    EXPORT: '导出',
    LOGIN: '登录'
  }
  return map[type] || type
}

/** 格式化 JSON 字符串，便于阅读 */
function formatJson(str) {
  if (!str) return '—'
  try {
    return JSON.stringify(JSON.parse(str), null, 2)
  } catch {
    return str
  }
}

/** 查询操作日志 */
async function fetchOperationLogs() {
  operationLoading.value = true
  try {
    const params = {
      communityId: authStore.communityId
    }
    if (operationFilter.operationType) {
      params.operationType = operationFilter.operationType
    }
    // 按操作人名称筛选（后端 operatorId 参数，此处传名称由后端适配或前端做本地过滤）
    if (operationFilter.timeRange && operationFilter.timeRange.length === 2) {
      params.startTime = dayjs(operationFilter.timeRange[0]).format('YYYY-MM-DD HH:mm:ss')
      params.endTime = dayjs(operationFilter.timeRange[1]).format('YYYY-MM-DD HH:mm:ss')
    }
    const data = await getOperationLogs(params)
    let list = Array.isArray(data) ? data : (data.records || [])
    // 前端按操作人名称过滤
    if (operationFilter.operatorName) {
      list = list.filter(item =>
        item.operatorName && item.operatorName.includes(operationFilter.operatorName)
      )
    }
    operationLogs.value = list
    operationPagination.total = list.length
  } catch (err) {
    // 错误已在 request.js 拦截器中统一提示
  } finally {
    operationLoading.value = false
  }
}

/** 重置操作日志筛选条件 */
function resetOperationFilter() {
  operationFilter.operationType = undefined
  operationFilter.operatorName = ''
  operationFilter.timeRange = []
  operationPagination.current = 1
  fetchOperationLogs()
}

/** 操作日志表格分页变化 */
function handleOperationTableChange(pag) {
  operationPagination.current = pag.current
  operationPagination.pageSize = pag.pageSize
}

// ========== 访问日志相关 ==========
const accessFilter = reactive({
  apiPath: '',
  userIp: '',
  timeRange: []
})

const accessLogs = ref([])
const accessLoading = ref(false)
const accessPagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
  showSizeChanger: true,
  showTotal: (total) => `共 ${total} 条`
})

/** 访问日志表格列定义 */
const accessColumns = [
  { title: '访问时间', dataIndex: 'accessTime', width: 180 },
  { title: '请求路径', dataIndex: 'apiPath', width: 220 },
  { title: '请求方法', dataIndex: 'httpMethod', width: 100 },
  { title: 'IP 地址', dataIndex: 'userIp', width: 140 },
  { title: '响应状态码', dataIndex: 'responseCode', width: 110 },
  { title: '响应时间(ms)', dataIndex: 'responseTime', width: 120 }
]

/** HTTP 方法颜色映射 */
function httpMethodColor(method) {
  const map = {
    GET: 'blue',
    POST: 'green',
    PUT: 'orange',
    DELETE: 'red',
    PATCH: 'purple'
  }
  return map[method] || 'default'
}

/** 查询访问日志 */
async function fetchAccessLogs() {
  accessLoading.value = true
  try {
    const params = {
      communityId: authStore.communityId
    }
    if (accessFilter.timeRange && accessFilter.timeRange.length === 2) {
      params.startTime = dayjs(accessFilter.timeRange[0]).format('YYYY-MM-DD HH:mm:ss')
      params.endTime = dayjs(accessFilter.timeRange[1]).format('YYYY-MM-DD HH:mm:ss')
    }
    if (accessFilter.apiPath) {
      params.apiPath = accessFilter.apiPath
    }
    const data = await getAccessLogs(params)
    let list = Array.isArray(data) ? data : (data.records || [])
    // 前端按 IP 地址过滤
    if (accessFilter.userIp) {
      list = list.filter(item =>
        item.userIp && item.userIp.includes(accessFilter.userIp)
      )
    }
    accessLogs.value = list
    accessPagination.total = list.length
  } catch (err) {
    // 错误已在 request.js 拦截器中统一提示
  } finally {
    accessLoading.value = false
  }
}

/** 重置访问日志筛选条件 */
function resetAccessFilter() {
  accessFilter.apiPath = ''
  accessFilter.userIp = ''
  accessFilter.timeRange = []
  accessPagination.current = 1
  fetchAccessLogs()
}

/** 访问日志表格分页变化 */
function handleAccessTableChange(pag) {
  accessPagination.current = pag.current
  accessPagination.pageSize = pag.pageSize
}

// ========== 导出功能 ==========
/** 导出审计日志 */
async function handleExport() {
  try {
    const queryParams = {}
    if (operationFilter.operationType) {
      queryParams.operationType = operationFilter.operationType
    }
    if (operationFilter.timeRange && operationFilter.timeRange.length === 2) {
      queryParams.startTime = dayjs(operationFilter.timeRange[0]).format('YYYY-MM-DD HH:mm:ss')
      queryParams.endTime = dayjs(operationFilter.timeRange[1]).format('YYYY-MM-DD HH:mm:ss')
    }
    await exportAuditLogs({
      communityId: authStore.communityId,
      operatorId: authStore.adminId,
      operatorName: '管理员',
      exportType: 'audit_log',
      queryParams: JSON.stringify(queryParams),
      needRawData: 0
    })
    message.success('导出任务已创建，请稍后查看')
  } catch (err) {
    // 错误已在 request.js 拦截器中统一提示
  }
}

// ========== 标签页切换 ==========
function handleTabChange(key) {
  if (key === 'operation') {
    fetchOperationLogs()
  } else {
    fetchAccessLogs()
  }
}

// ========== 初始化加载 ==========
fetchOperationLogs()
</script>

<style scoped>
.audit-log-container {
  padding: 0;
}
</style>
