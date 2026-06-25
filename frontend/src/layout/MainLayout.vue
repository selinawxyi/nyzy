<template>
  <div class="layout">
    <!-- 左侧深色图标导航 -->
    <aside class="sidebar">
      <div class="sidebar-logo">农</div>
      <nav class="sidebar-nav">
        <div
          v-for="item in navItems"
          :key="item.key"
          class="nav-item"
          :class="{ active: item.key === activeNav }"
          @click="onNav(item)"
        >
          <el-icon :size="20"><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
        </div>
      </nav>
    </aside>

    <!-- 二级菜单 -->
    <div class="submenu">
      <div class="submenu-title">
        <el-icon><component :is="activeGroup.icon" /></el-icon><span>{{ activeGroup.label }}</span>
      </div>
      <div
        v-for="sub in activeGroup.children"
        :key="sub.route || sub.label"
        class="submenu-item"
        :class="{ active: sub.route === route.name, disabled: !sub.route }"
        @click="sub.route && router.push({ name: sub.route, query: sub.query || {} })"
      >
        {{ sub.label }}
      </div>
    </div>

    <!-- 主区 -->
    <div class="main">
      <header class="topbar">
        <el-breadcrumb separator="/">
          <el-breadcrumb-item>{{ activeGroup.label }}</el-breadcrumb-item>
          <el-breadcrumb-item>{{ route.meta.title }}</el-breadcrumb-item>
        </el-breadcrumb>
        <div class="topbar-right">
          <el-popover placement="bottom-end" :width="340" trigger="click" @show="loadNotifications">
            <template #reference>
              <el-badge :value="unreadCount" :hidden="unreadCount === 0" :max="99" class="bell">
                <el-icon :size="18"><Bell /></el-icon>
              </el-badge>
            </template>
            <div class="notif-head">
              <span>通知</span>
              <el-button v-if="unreadCount" link type="primary" size="small" @click="readAll">全部已读</el-button>
            </div>
            <el-scrollbar max-height="320px">
              <el-empty v-if="!notifications.length" description="暂无通知" :image-size="50" />
              <div v-for="n in notifications" :key="n.id" class="notif-item" :class="{ unread: !n.isRead }" @click="readOne(n)">
                <div class="notif-title">
                  <span class="dot" v-if="!n.isRead"></span>{{ n.title }}
                </div>
                <div class="notif-content">{{ n.content }}</div>
                <div class="notif-time">{{ n.createdAt }}</div>
              </div>
            </el-scrollbar>
          </el-popover>
          <el-dropdown @command="onCommand">
            <span class="user">
              <el-avatar :size="28" style="background:#2e9e5b">{{ avatarText }}</el-avatar>
              <span class="username">{{ auth.user?.nickname || auth.user?.username }}</span>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item disabled>角色：{{ roleText }}</el-dropdown-item>
                <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <main class="content">
        <router-view />
      </main>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, onBeforeUnmount, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  HomeFilled, Location, Grid, OfficeBuilding, Crop, Warning, TrendCharts, Medal, User, Bell, ArrowDown
} from '@element-plus/icons-vue'
import { useAuthStore } from '../store/auth'
import { notificationApi } from '../api'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

const navItems = [
  { key: 'home', label: '首页', icon: HomeFilled },
  { key: 'map', label: '地图', icon: Location },
  { key: 'parcel', label: '地块', icon: Grid },
  { key: 'facility', label: '设施', icon: OfficeBuilding },
  { key: 'plant', label: '种植', icon: Crop },
  { key: 'abandon', label: '撂荒', icon: Warning },
  { key: 'analysis', label: '分析', icon: TrendCharts },
  { key: 'quality', label: '质量', icon: Medal },
  { key: 'user', label: '用户', icon: User }
]

// 每个导航分组的二级菜单 (route 为空表示规划中)
const navGroups = {
  map: { label: '一张图', icon: Location, children: [
    { label: '农业资源一张图', route: 'map' }
  ] },
  parcel: { label: '土地资源', icon: Grid, children: [
    { label: '确权地块', route: 'parcel' }
  ] },
  abandon: { label: '撂荒', icon: Warning, children: [
    { label: '撂荒管理', route: 'abandon' },
    { label: '待审核列表', route: 'auditCenter', query: { bizType: 'abandon' } }
  ] },
  facility: { label: '农业资源', icon: OfficeBuilding, children: [
    { label: '水利设施', route: 'water' },
    { label: '配套设施', route: 'support' },
    { label: '设施分类', route: 'facilityCategory' }
  ] },
  plant: { label: '耕地利用', icon: Crop, children: [
    { label: '种植记录', route: 'planting' }
  ] },
  analysis: { label: '种植动态分析', icon: TrendCharts, children: [
    { label: '数据分析', route: 'analysis' },
    { label: '数字化地图服务', route: 'spatialQuery' }
  ] },
  user: { label: '系统管理', icon: User, children: [
    { label: '待审核中心', route: 'auditCenter' },
    { label: '用户管理', route: 'users' },
    { label: '审计日志', route: 'auditLog' },
    { label: '回收站', route: 'recycle' }
  ] },
  quality: { label: '耕地质量', icon: Medal, children: [
    { label: '质量评定列表', route: 'quality' }
  ] }
}

const activeNav = computed(() => route.meta.nav || 'abandon')
const activeGroup = computed(() => navGroups[activeNav.value] || navGroups.abandon)

const avatarText = computed(() => (auth.user?.nickname || auth.user?.username || 'U').charAt(0))
const roleText = computed(() => ({ admin: '系统管理员', operator: '运营人员', gridman: '网格员' }[auth.user?.role] || '-'))

const onNav = (item) => {
  const group = navGroups[item.key]
  const target = group?.children.find((c) => c.route)
  if (target) router.push({ name: target.route })
  else ElMessage.info(`「${item.label}」模块开发中`)
}

const onCommand = (cmd) => {
  if (cmd === 'logout') {
    auth.logout()
    router.push('/login')
  }
}

// ---- 通知 ----
const unreadCount = ref(0)
const notifications = ref([])
let pollTimer = null
const loadUnread = async () => {
  try { unreadCount.value = (await notificationApi.unreadCount()).count } catch (e) {}
}
const loadNotifications = async () => {
  const data = await notificationApi.list({ page: 1, size: 20 })
  notifications.value = data.list
}
const readOne = async (n) => {
  if (!n.isRead) {
    await notificationApi.read(n.id)
    n.isRead = 1
    loadUnread()
  }
  if (n.bizType) {
    const route = { abandon: 'abandon', abandon_task: 'abandon', water: 'water', support: 'support', parcel: 'parcel' }[n.bizType]
    if (route) router.push({ name: route })
  }
}
const readAll = async () => {
  await notificationApi.readAll()
  notifications.value.forEach((n) => (n.isRead = 1))
  unreadCount.value = 0
}

onMounted(() => {
  if (!auth.user) auth.fetchMe().catch(() => {})
  loadUnread()
  pollTimer = setInterval(loadUnread, 30000)   // 30s 轮询未读数
})
onBeforeUnmount(() => { if (pollTimer) clearInterval(pollTimer) })
</script>

<style scoped>
.layout { display: flex; height: 100%; }
.sidebar {
  width: 64px; background: #1f2937; display: flex; flex-direction: column;
  align-items: center; flex-shrink: 0;
}
.sidebar-logo {
  width: 40px; height: 40px; margin: 14px 0; background: #2e9e5b; color: #fff;
  border-radius: 8px; font-size: 22px; font-weight: 700;
  display: flex; align-items: center; justify-content: center;
}
.sidebar-nav { width: 100%; }
.nav-item {
  display: flex; flex-direction: column; align-items: center; gap: 4px;
  padding: 12px 0; color: #9ca3af; font-size: 12px; cursor: pointer; transition: all 0.2s;
}
.nav-item:hover { color: #fff; background: rgba(255, 255, 255, 0.06); }
.nav-item.active { color: #fff; background: #2e9e5b; }
.submenu {
  width: 180px; background: #fff; border-right: 1px solid #ebeef5; flex-shrink: 0; padding-top: 8px;
}
.submenu-title {
  display: flex; align-items: center; gap: 6px; padding: 14px 18px;
  font-weight: 600; color: #1f2d3d; border-bottom: 1px solid #f0f2f5;
}
.submenu-item { padding: 12px 18px; font-size: 14px; color: #606266; cursor: pointer; }
.submenu-item:hover:not(.disabled) { color: #2e9e5b; }
.submenu-item.active { color: #2e9e5b; background: #ecf7f0; border-right: 2px solid #2e9e5b; }
.submenu-item.disabled { color: #c0c4cc; cursor: not-allowed; }
.main { flex: 1; display: flex; flex-direction: column; min-width: 0; }
.topbar {
  height: 56px; background: #fff; border-bottom: 1px solid #ebeef5;
  display: flex; align-items: center; justify-content: space-between; padding: 0 20px;
}
.topbar-right { display: flex; align-items: center; gap: 20px; }
.bell { cursor: pointer; color: #606266; }
.notif-head { display: flex; align-items: center; justify-content: space-between; padding-bottom: 8px; border-bottom: 1px solid #f0f2f5; font-weight: 600; }
.notif-item { padding: 10px 4px; border-bottom: 1px dashed #ebeef5; cursor: pointer; }
.notif-item:hover { background: #f5f7fa; }
.notif-item.unread .notif-title { font-weight: 600; }
.notif-title { font-size: 13px; color: #303133; display: flex; align-items: center; gap: 6px; }
.notif-title .dot { width: 7px; height: 7px; border-radius: 50%; background: #f56c6c; display: inline-block; }
.notif-content { font-size: 12px; color: #606266; margin: 3px 0; }
.notif-time { font-size: 11px; color: #c0c4cc; }
.user { display: flex; align-items: center; gap: 8px; cursor: pointer; outline: none; }
.username { font-size: 14px; color: #303133; }
.content { flex: 1; overflow: auto; padding: 16px; }
</style>
