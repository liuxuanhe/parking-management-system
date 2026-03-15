<template>
  <a-layout style="min-height: 100vh">
    <a-layout-header style="background: #001529; padding: 0 24px; display: flex; align-items: center; justify-content: space-between">
      <div style="color: #fff; font-size: 18px; font-weight: bold">
        停车场管理后台
      </div>
      <a-button type="link" style="color: #fff" @click="handleLogout">
        退出登录
      </a-button>
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
import { ref, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { TeamOutlined, CarOutlined, UserSwitchOutlined, SettingOutlined, BarChartOutlined, WarningOutlined, FileSearchOutlined } from '@ant-design/icons-vue'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

/** 侧边栏折叠状态 */
const collapsed = ref(false)

/** 当前选中的菜单项 */
const selectedKeys = ref([route.path])

/** 当前展开的子菜单 */
const openKeys = ref(['owner'])

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
</script>
