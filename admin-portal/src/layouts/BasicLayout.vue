<template>
  <a-layout style="min-height: 100vh">
    <a-layout-header style="background: #001529; padding: 0 24px; display: flex; align-items: center; justify-content: space-between">
      <div style="color: #fff; font-size: 18px; font-weight: bold">
        停车场管理后台
      </div>
      <div style="display: flex; align-items: center; gap: 16px">
        <!-- 角色标识 -->
        <a-tag :color="authStore.role === 'super_admin' ? 'red' : 'blue'">
          {{ authStore.role === 'super_admin' ? '超级管理员' : '物业管理员' }}
        </a-tag>

        <!-- 小区切换（Super_Admin 专属） -->
        <template v-if="authStore.role === 'super_admin'">
          <a-select
            v-model:value="currentCommunityId"
            style="width: 200px"
            placeholder="选择小区"
            @change="handleSwitchCommunity"
          >
            <a-select-option
              v-for="c in communityList"
              :key="c.id"
              :value="c.id"
            >
              {{ c.communityName }}
            </a-select-option>
          </a-select>
        </template>
        <!-- Property_Admin 固定显示小区名称 -->
        <template v-else>
          <span style="color: #fff">{{ currentCommunityName }}</span>
        </template>

        <a-button type="link" style="color: #fff" @click="handleLogout">
          退出登录
        </a-button>
      </div>
    </a-layout-header>
    <a-layout>
      <!-- 侧边栏菜单 -->
      <a-layout-sider
        v-model:collapsed="collapsed"
        collapsible
        theme="light"
        :width="200"
      >
        <a-menu
          v-model:selectedKeys="selectedKeys"
          v-model:openKeys="openKeys"
          mode="inline"
          style="height: 100%; border-right: 0"
          @click="handleMenuClick"
        >
          <!-- 业主管理 -->
          <a-sub-menu key="owner">
            <template #icon><TeamOutlined /></template>
            <template #title>业主管理</template>
            <a-menu-item key="/owners/audit">业主审核</a-menu-item>
          </a-sub-menu>

          <!-- 车辆管理 -->
          <a-sub-menu key="vehicle">
            <template #icon><CarOutlined /></template>
            <template #title>车辆管理</template>
            <a-menu-item key="/vehicles">车辆列表</a-menu-item>
          </a-sub-menu>

          <!-- Visitor 管理 -->
          <a-sub-menu key="visitor">
            <template #icon><UserSwitchOutlined /></template>
            <template #title>Visitor 管理</template>
            <a-menu-item key="/visitors/audit">Visitor 审批</a-menu-item>
          </a-sub-menu>

          <!-- 车位管理 -->
          <a-sub-menu key="parking">
            <template #icon><SettingOutlined /></template>
            <template #title>车位管理</template>
            <a-menu-item key="/parking/config">车位配置</a-menu-item>
          </a-sub-menu>

          <!-- 数据报表 -->
          <a-sub-menu key="report">
            <template #icon><BarChartOutlined /></template>
            <template #title>数据报表</template>
            <a-menu-item key="/reports">报表分析</a-menu-item>
          </a-sub-menu>

          <!-- 僵尸车辆 -->
          <a-sub-menu key="zombie">
            <template #icon><WarningOutlined /></template>
            <template #title>僵尸车辆</template>
            <a-menu-item key="/zombie-vehicles">僵尸车辆处理</a-menu-item>
          </a-sub-menu>

          <!-- 审计日志 -->
          <a-sub-menu key="audit">
            <template #icon><FileSearchOutlined /></template>
            <template #title>审计日志</template>
            <a-menu-item key="/audit/logs">日志查询</a-menu-item>
          </a-sub-menu>

          <!-- Super_Admin 专属：管理员管理 -->
          <a-sub-menu v-if="authStore.role === 'super_admin'" key="admin-manage">
            <template #icon><UserOutlined /></template>
            <template #title>管理员管理</template>
            <a-menu-item key="/admins">管理员列表</a-menu-item>
          </a-sub-menu>

          <!-- Super_Admin 专属：IP 白名单 -->
          <a-sub-menu v-if="authStore.role === 'super_admin'" key="ip-whitelist">
            <template #icon><SafetyOutlined /></template>
            <template #title>IP 白名单</template>
            <a-menu-item key="/ip-whitelist">白名单配置</a-menu-item>
          </a-sub-menu>
        </a-menu>
      </a-layout-sider>

      <!-- 主内容区 -->
      <a-layout-content style="padding: 24px; background: #fff; margin: 0">
        <router-view />
      </a-layout-content>
    </a-layout>
  </a-layout>
</template>

<script setup>
import { ref, watch, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import {
  TeamOutlined, CarOutlined, UserSwitchOutlined, SettingOutlined,
  BarChartOutlined, WarningOutlined, FileSearchOutlined,
  UserOutlined, SafetyOutlined
} from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { useAuthStore } from '@/stores/auth'
import { getCommunityList, switchCommunity } from '@/api/community'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

/** 侧边栏折叠状态 */
const collapsed = ref(false)

/** 当前选中的菜单项 */
const selectedKeys = ref([route.path])

/** 当前展开的子菜单 */
const openKeys = ref(['owner'])

/** 小区列表 */
const communityList = ref([])

/** 当前操作小区ID */
const currentCommunityId = ref(authStore.communityId)

/** 当前小区名称（Property_Admin 使用） */
const currentCommunityName = ref('')

/** 监听路由变化，同步菜单选中状态 */
watch(
  () => route.path,
  (path) => {
    selectedKeys.value = [path]
  }
)

/** 菜单点击跳转 */
function handleMenuClick({ key }) {
  router.push(key)
}

/** 退出登录 */
function handleLogout() {
  authStore.logout()
  router.push('/login')
}

/** 加载小区列表 */
async function loadCommunities() {
  try {
    const data = await getCommunityList()
    communityList.value = Array.isArray(data) ? data : []
    // 设置当前小区名称
    const current = communityList.value.find(c => c.id === authStore.communityId)
    if (current) {
      currentCommunityName.value = current.communityName
    }
  } catch (err) {
    // 错误已在 request.js 拦截器中统一提示
  }
}

/** 切换小区（Super_Admin 专属） */
async function handleSwitchCommunity(communityId) {
  try {
    const data = await switchCommunity(communityId)
    // 更新 token 和 communityId
    authStore.setLoginInfo({
      accessToken: data.accessToken,
      refreshToken: authStore.refreshToken,
      role: authStore.role,
      communityId: communityId,
      adminId: authStore.adminId
    })
    const target = communityList.value.find(c => c.id === communityId)
    message.success(`已切换到：${target ? target.communityName : communityId}`)
    // 刷新当前页面数据
    router.go(0)
  } catch (err) {
    // 恢复选择
    currentCommunityId.value = authStore.communityId
  }
}

onMounted(() => {
  loadCommunities()
})
</script>
