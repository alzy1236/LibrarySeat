import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

/**
 * 路由配置
 * - 未登录访问任何业务页面 -> 重定向到 /login（项目启动后默认进入登录页）
 * - 学生访问 /admin/** 或管理员访问 /student/** -> 跳转到各自首页
 */
const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/LoginView.vue'),
    meta: { title: '登录', public: true }
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/login/RegisterView.vue'),
    meta: { title: '注册账号', public: true }
  },
  {
    path: '/',
    redirect: '/login'
  },
  {
    path: '/student',
    component: () => import('@/layouts/StudentLayout.vue'),
    meta: { role: 'STUDENT' },
    redirect: '/student/home',
    children: [
      { path: 'home', name: 'StudentHome', component: () => import('@/views/student/StudentHome.vue'), meta: { title: '首页' } },
      { path: 'rooms', name: 'StudentRooms', component: () => import('@/views/student/RoomList.vue'), meta: { title: '自习室预约' } },
      { path: 'rooms/:id', name: 'StudentSeatMap', component: () => import('@/views/student/SeatMap.vue'), meta: { title: '选择座位' } },
      { path: 'long-term', name: 'StudentLongTerm', component: () => import('@/views/student/LongTerm.vue'), meta: { title: '固定座位' } },
      { path: 'records', name: 'StudentRecords', component: () => import('@/views/student/MyRecords.vue'), meta: { title: '我的选座记录' } },
      { path: 'profile', name: 'StudentProfile', component: () => import('@/views/common/Profile.vue'), meta: { title: '个人中心' } }
    ]
  },
  {
    path: '/admin',
    component: () => import('@/layouts/AdminLayout.vue'),
    meta: { role: 'ADMIN' },
    redirect: '/admin/dashboard',
    children: [
      { path: 'dashboard', name: 'AdminDashboard', component: () => import('@/views/admin/Dashboard.vue'), meta: { title: '数据概览' } },
      { path: 'rooms', name: 'AdminRooms', component: () => import('@/views/admin/RoomManage.vue'), meta: { title: '自习室管理' } },
      { path: 'rooms/:id/seats', name: 'AdminSeats', component: () => import('@/views/admin/SeatManage.vue'), meta: { title: '座位管理' } },
      { path: 'reservations', name: 'AdminReservations', component: () => import('@/views/admin/ReservationManage.vue'), meta: { title: '预约记录' } },
      { path: 'reviews', name: 'AdminReviews', component: () => import('@/views/admin/ReviewManage.vue'), meta: { title: '申请审核' } },
      { path: 'long-term', name: 'AdminLongTerm', component: () => import('@/views/admin/LongTermManage.vue'), meta: { title: '固定座位 · 月度考核' } },
      { path: 'students', name: 'AdminUsers', component: () => import('@/views/admin/UserManage.vue'), meta: { title: '学生账号管理' } },
      { path: 'locations', name: 'AdminLocations', component: () => import('@/views/admin/LocationManage.vue'), meta: { title: '签到点管理' } },
      { path: 'notices', name: 'AdminNotices', component: () => import('@/views/admin/NoticeManage.vue'), meta: { title: '公告管理' } },
      { path: 'settings', name: 'AdminSettings', component: () => import('@/views/admin/Settings.vue'), meta: { title: '系统设置' } },
      { path: 'profile', name: 'AdminProfile', component: () => import('@/views/common/Profile.vue'), meta: { title: '个人中心' } }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/login'
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 })
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  document.title = to.meta.title ? `${to.meta.title} - 自习室座位预约管理系统` : '自习室座位预约管理系统'

  // ---------------- 仅“重启项目后首次打开”回到登录页 ----------------
  // 后端每次启动会生成新的启动标识，前端把上次看到过的标识存在 localStorage；
  // 标识变化（项目重启过）或本地没有登录态时，才回到登录页。
  // 普通刷新页面（F5）不会回登录页，保持当前页面与登录状态。
  if (!window.__lsEntryChecked) {
    window.__lsEntryChecked = true
    const bootStamp = document.querySelector('meta[name="ls-boot"]')?.getAttribute('content') || ''
    const lastBoot = localStorage.getItem('ls_boot')
    const restarted = !!bootStamp && bootStamp !== lastBoot
    if (bootStamp) {
      localStorage.setItem('ls_boot', bootStamp)
    }
    if (restarted || !auth.isLogin) {
      auth.clear()
      // 登录页与注册页都允许直接停留，其余页面回到登录页
      if (to.path !== '/login' && to.path !== '/register') {
        return { path: '/login', replace: true }
      }
      return true
    }
  }

  if (to.meta.public) return true

  // 未登录：一律回到登录页
  if (!auth.isLogin) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  // 角色校验：账号类型决定进入的管理端 / 学生端
  const needRole = to.matched.find((r) => r.meta.role)?.meta.role
  if (needRole && auth.user?.role !== needRole) {
    return auth.isAdmin ? '/admin/dashboard' : '/student/home'
  }
  return true
})

export default router