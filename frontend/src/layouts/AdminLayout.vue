<template>
  <div class="shell">
    <!-- 移动端遮罩 -->
    <div v-if="mobileOpen" class="side-mask" @click="mobileOpen = false"></div>

    <aside class="side" :class="{ mini: collapsed, open: mobileOpen }">
      <div class="brand">
        <div class="brand-mark">
          <el-icon><Reading /></el-icon>
        </div>
        <div v-show="!collapsed" class="brand-text">
          <b>管理控制台</b>
          <span>自习楼 1—5 层</span>
        </div>
      </div>

      <div v-show="!collapsed" class="group">运营</div>
      <nav class="nav">
        <router-link
          v-for="item in menus.slice(0, 5)"
          :key="item.path"
          :to="item.path"
          class="nav-item"
          :class="{ active: isActive(item.path) }"
          @click="mobileOpen = false"
        >
          <el-icon><component :is="item.icon" /></el-icon>
          <span v-show="!collapsed">{{ item.label }}</span>
          <i v-if="item.key === 'pending' && badge.pending" class="badge">{{ badge.pending }}</i>
        </router-link>
      </nav>

      <div v-show="!collapsed" class="group">系统</div>
      <nav class="nav">
        <router-link
          v-for="item in menus.slice(5)"
          :key="item.path"
          :to="item.path"
          class="nav-item"
          :class="{ active: isActive(item.path) }"
          @click="mobileOpen = false"
        >
          <el-icon><component :is="item.icon" /></el-icon>
          <span v-show="!collapsed">{{ item.label }}</span>
        </router-link>
      </nav>

      <div v-show="!collapsed" class="side-foot">
        <div class="sf-label">服务器时间</div>
        <div class="sf-time mono">{{ clock.time }}</div>
        <div class="sf-date">{{ clock.today }}</div>
      </div>
    </aside>

    <div class="main">
      <header class="topbar">
        <div class="tb-left">
          <button class="collapse" @click="toggleSide" :title="isMobile ? '打开菜单' : '收起侧栏'">
            <el-icon><component :is="collapsed && !isMobile ? 'Expand' : 'Fold'" /></el-icon>
          </button>
          <div class="crumb">{{ route.meta.title }}</div>
        </div>

        <div class="tb-right">
          <div class="metrics">
            <span>今日预约 <b>{{ overview?.todayReserved ?? '-' }}</b></span>
            <i></i>
            <span>在座 <b>{{ overview?.todaySeated ?? '-' }}</b></span>
          </div>
          <button v-if="badge.pending" class="bell" @click="router.push('/admin/reviews')" title="待审核申请">
            <el-icon><Bell /></el-icon>
            <i class="bell-dot">{{ badge.pending }}</i>
          </button>
          <el-dropdown trigger="click" @command="onCommand">
            <div class="me">
              <div class="me-avatar">{{ auth.displayName.slice(0, 1) }}</div>
              <div class="me-text">
                <b>{{ auth.displayName }}</b>
                <span>管理员</span>
              </div>
              <el-icon class="me-caret"><CaretBottom /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile"><el-icon><Setting /></el-icon>个人中心</el-dropdown-item>
                <el-dropdown-item command="logout" divided><el-icon><SwitchButton /></el-icon>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <main class="content">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" :key="route.path" />
          </transition>
        </router-view>
      </main>
    </div>
  </div>
</template>

<script setup>
import { onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { useClockStore } from '@/stores/clock'
import { useBadgeStore } from '@/stores/badge'
import { adminOverview } from '@/api'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const clock = useClockStore()
const badge = useBadgeStore()
const collapsed = ref(false)
const overview = ref(null)

/* ---------- 移动端侧边栏抽屉 ---------- */
const mobileOpen = ref(false)
const isMobile = ref(false)
const MQ = '(max-width: 900px)'
let mql = null

function syncIsMobile(e) {
  isMobile.value = e.matches
  if (!e.matches) mobileOpen.value = false
}

function toggleSide() {
  if (isMobile.value) {
    mobileOpen.value = !mobileOpen.value
  } else {
    collapsed.value = !collapsed.value
  }
}

const menus = [
  { path: '/admin/dashboard', label: '数据概览', icon: 'DataLine' },
  { path: '/admin/rooms', label: '楼层与座位', icon: 'OfficeBuilding' },
  { path: '/admin/reservations', label: '预约记录', icon: 'Tickets' },
  { path: '/admin/reviews', label: '申请审核', icon: 'Checked', key: 'pending' },
  { path: '/admin/long-term', label: '固定座位 · 考核', icon: 'Collection' },
  { path: '/admin/students', label: '学生账号', icon: 'UserFilled' },
  { path: '/admin/notices', label: '公告管理', icon: 'Bell' },
  { path: '/admin/settings', label: '系统设置', icon: 'Setting' },
  { path: '/admin/profile', label: '个人中心', icon: 'Avatar' }
]

function isActive(path) {
  if (path === '/admin/rooms') return route.path.startsWith('/admin/rooms')
  return route.path === path
}

async function loadHeader() {
  try {
    const ov = await adminOverview()
    overview.value = ov.data
  } catch (e) {}
  badge.refresh()
}

function onCommand(cmd) {
  if (cmd === 'logout') {
    ElMessageBox.confirm('确定退出登录吗？', '提示', { type: 'warning', confirmButtonText: '退出', cancelButtonText: '取消' })
      .then(async () => {
        await auth.logout()
        ElMessage.success('已退出登录')
        router.replace('/login')
      })
      .catch(() => {})
  } else if (cmd === 'profile') {
    router.push('/admin/profile')
  }
}

onMounted(() => {
  loadHeader()
  badge.refresh()
  badge.startPolling(15000)
  mql = window.matchMedia(MQ)
  isMobile.value = mql.matches
  if (mql.addEventListener) mql.addEventListener('change', syncIsMobile)
  else mql.addListener(syncIsMobile)
})

onUnmounted(() => {
  badge.stopPolling()
  if (mql) {
    if (mql.removeEventListener) mql.removeEventListener('change', syncIsMobile)
    else mql.removeListener(syncIsMobile)
  }
})
</script>

<style scoped>
.shell {
  display: flex;
  min-height: 100vh;
  background: var(--c-bg);
}

.side {
  width: var(--sidebar-w);
  flex-shrink: 0;
  background: #fff;
  border-right: 1px solid var(--c-line);
  display: flex;
  flex-direction: column;
  padding: 12px 10px;
  position: sticky;
  top: 0;
  height: 100vh;
  transition: width 0.18s ease;
  overflow: hidden;
  z-index: 60;
}

/* 移动端遮罩 */
.side-mask {
  position: fixed;
  inset: 0;
  background: rgba(16, 24, 40, 0.42);
  z-index: 55;
  animation: mask-in 0.16s ease;
}

@keyframes mask-in {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

.side.mini {
  width: 64px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 4px 6px 14px;
  margin-bottom: 2px;
  border-bottom: 1px solid var(--c-line);
}

.brand-mark {
  width: 28px;
  height: 28px;
  border-radius: var(--radius-sm);
  background: var(--gray-900);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 15px;
  flex-shrink: 0;
}

.brand-text b {
  display: block;
  font-size: 13px;
  font-weight: 600;
  color: var(--gray-900);
  line-height: 1.35;
}

.brand-text span {
  font-size: 11px;
  color: var(--gray-400);
}

.group {
  font-size: 10.5px;
  font-weight: 500;
  color: var(--gray-400);
  letter-spacing: 0.04em;
  padding: 12px 9px 4px;
}

.nav {
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.nav-item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 9px;
  height: 32px;
  padding: 0 9px;
  border-radius: var(--radius-sm);
  color: var(--gray-600);
  font-size: 13px;
  font-weight: 500;
  white-space: nowrap;
  transition: background 0.12s, color 0.12s;
}

.nav-item:hover {
  background: var(--gray-100);
  color: var(--gray-900);
}

.nav-item.active {
  background: var(--gray-100);
  color: var(--gray-900);
  font-weight: 600;
}

.nav-item.active .el-icon {
  color: var(--brand);
}

.nav-item .el-icon {
  font-size: 15px;
  color: var(--gray-400);
  flex-shrink: 0;
}

.badge {
  margin-left: auto;
  font-style: normal;
  font-size: 10.5px;
  font-weight: 600;
  background: var(--red);
  color: #fff;
  border-radius: 999px;
  min-width: 17px;
  height: 17px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0 5px;
}

.side-foot {
  margin-top: auto;
  padding: 10px;
  border-radius: var(--radius);
  background: var(--gray-50);
  border: 1px solid var(--c-line);
}

.sf-label {
  font-size: 10.5px;
  color: var(--gray-400);
}

.sf-time {
  font-size: 17px;
  font-weight: 600;
  color: var(--gray-900);
  line-height: 1.3;
}

.sf-date {
  font-size: 10.5px;
  color: var(--gray-400);
}

.main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.topbar {
  height: var(--header-h);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: saturate(180%) blur(8px);
  border-bottom: 1px solid var(--c-line);
  position: sticky;
  top: 0;
  z-index: 20;
}

.tb-left {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.collapse {
  width: 28px;
  height: 28px;
  border-radius: var(--radius-sm);
  border: 1px solid transparent;
  background: transparent;
  color: var(--gray-500);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 15px;
}

.collapse:hover {
  background: var(--gray-100);
  color: var(--gray-900);
}

.crumb {
  font-size: 13px;
  font-weight: 600;
  color: var(--gray-900);
}

.tb-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.metrics {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--gray-500);
  background: var(--gray-50);
  border: 1px solid var(--c-line);
  border-radius: var(--radius-sm);
  padding: 4px 10px;
}

.metrics b {
  color: var(--gray-900);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.metrics i {
  width: 1px;
  height: 12px;
  background: var(--gray-200);
}

.bell {
  position: relative;
  width: 28px;
  height: 28px;
  border-radius: var(--radius-sm);
  border: 1px solid transparent;
  background: transparent;
  color: var(--gray-500);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 15px;
}

.bell:hover {
  background: var(--gray-100);
  color: var(--gray-900);
}

.bell-dot {
  position: absolute;
  top: -3px;
  right: -4px;
  font-style: normal;
  background: var(--red);
  color: #fff;
  font-size: 9.5px;
  font-weight: 600;
  border-radius: 999px;
  min-width: 15px;
  height: 15px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0 4px;
  border: 1.5px solid #fff;
}

.me {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 3px 6px 3px 4px;
  border-radius: var(--radius-sm);
  outline: none;
}

.me:hover {
  background: var(--gray-100);
}

.me-avatar {
  width: 26px;
  height: 26px;
  border-radius: var(--radius-sm);
  background: var(--gray-900);
  color: #fff;
  font-size: 12px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
}

.me-text b {
  display: block;
  font-size: 12.5px;
  font-weight: 600;
  line-height: 1.3;
}

.me-text span {
  font-size: 11px;
  color: var(--gray-400);
}

.me-caret {
  font-size: 11px;
  color: var(--gray-400);
}

.content {
  flex: 1;
  min-width: 0;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.14s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

/* ==================== 移动端：侧栏变抽屉 ==================== */
@media (max-width: 900px) {
  .side {
    position: fixed;
    top: 0;
    left: 0;
    height: 100vh;
    width: 232px;
    transform: translateX(-100%);
    transition: transform 0.22s cubic-bezier(0.4, 0, 0.2, 1);
    border-right: 1px solid var(--c-line);
  }

  .side.mini {
    width: 232px;
  }

  .side.open {
    transform: translateX(0);
    box-shadow: 0 12px 32px rgba(16, 24, 40, 0.18);
  }

  .main {
    width: 100%;
  }

  .topbar {
    padding: 0 12px;
  }

  .me-text {
    display: none;
  }

  .metrics {
    display: none;
  }

  .crumb {
    max-width: 42vw;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

@media (max-width: 640px) {
  .topbar {
    padding: 0 10px;
    gap: 6px;
  }

  .tb-right {
    gap: 6px;
  }

  .crumb {
    max-width: 38vw;
  }
}
</style>