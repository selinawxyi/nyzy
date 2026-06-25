import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/login', component: () => import('../views/Login.vue'), meta: { public: true } },
  {
    path: '/',
    component: () => import('../layout/MainLayout.vue'),
    redirect: '/abandon',
    children: [
      { path: 'abandon', name: 'abandon', component: () => import('../views/abandon/AbandonList.vue'), meta: { title: '撂荒管理', nav: 'abandon' } },
      { path: 'planting', name: 'planting', component: () => import('../views/cultivation/PlantingList.vue'), meta: { title: '种植记录', nav: 'plant' } },
      { path: 'quality', name: 'quality', component: () => import('../views/cultivation/QualityList.vue'), meta: { title: '耕地质量', nav: 'quality' } },
      { path: 'map', name: 'map', component: () => import('../views/map/MapOverview.vue'), meta: { title: '一张图', nav: 'map' } },
      { path: 'parcel', name: 'parcel', component: () => import('../views/land/ParcelList.vue'), meta: { title: '确权地块', nav: 'parcel' } },
      { path: 'water', name: 'water', component: () => import('../views/resource/WaterList.vue'), meta: { title: '水利设施', nav: 'facility' } },
      { path: 'support', name: 'support', component: () => import('../views/resource/SupportList.vue'), meta: { title: '配套设施', nav: 'facility' } },
      { path: 'facility-category', name: 'facilityCategory', component: () => import('../views/resource/CategoryManage.vue'), meta: { title: '设施分类', nav: 'facility' } },
      { path: 'analysis', name: 'analysis', component: () => import('../views/analysis/AnalysisDashboard.vue'), meta: { title: '种植动态分析', nav: 'analysis' } },
      { path: 'spatial-query', name: 'spatialQuery', component: () => import('../views/map/SpatialQuery.vue'), meta: { title: '数字化地图服务(空间范围查询)', nav: 'analysis' } },
      { path: 'recycle', name: 'recycle', component: () => import('../views/system/RecycleBin.vue'), meta: { title: '回收站', nav: 'user' } },
      { path: 'users', name: 'users', component: () => import('../views/system/UserManage.vue'), meta: { title: '用户管理', nav: 'user' } },
      { path: 'audit-center', name: 'auditCenter', component: () => import('../views/system/AuditCenter.vue'), meta: { title: '待审核中心', nav: 'user' } },
      { path: 'audit-log', name: 'auditLog', component: () => import('../views/system/AuditLog.vue'), meta: { title: '审计日志', nav: 'user' } }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('token')
  if (to.meta.public) return next()
  if (!token) return next('/login')
  next()
})

export default router
