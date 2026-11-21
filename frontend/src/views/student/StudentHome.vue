<template>
  <div class="page home">
    <!-- 顶部状态行：问候 + 时钟 -->
    <section class="top-row">
      <div class="hero">
        <div class="hero-main">
          <div class="hero-tag">你好，{{ auth.displayName }}</div>
          <h1>今天也要认真学习</h1>
          <p>{{ auth.user?.college || '—' }} · {{ auth.user?.major || '—' }}</p>
        </div>
        <div class="hero-clock">
          <div class="hc-label"><i :class="{ off: !clock.wsConnected }"></i>服务器时间</div>
          <div class="clock-big mono">{{ clock.time }}</div>
          <div class="hc-day">{{ clock.today }} {{ clock.weekday }}</div>
        </div>
      </div>

      <div class="today-card" v-loading="loading">
        <div class="tc-head">
          <span>今日座位</span>
          <button class="ghost-btn" @click="loadAll" title="刷新"><el-icon><Refresh /></el-icon></button>
        </div>

        <template v-if="today">
          <div class="tc-seat mono">{{ today.seatNo }}</div>
          <div class="tc-room">{{ today.roomName }}</div>
          <div class="tc-tags">
            <span class="pill" :class="today.roomType === 'LONG' ? 'pill-long' : 'pill-short'">
              {{ today.roomType === 'LONG' ? '固定座位层' : '普通座位层' }}
            </span>
            <span class="pill" :class="statusPill(today.status)">{{ today.statusName }}</span>
          </div>
          <div class="tc-times">
            <div><span>签到</span><b class="mono">{{ timeOf(today.checkInTime) || '--:--' }}</b></div>
            <div><span>签退</span><b class="mono">{{ timeOf(today.checkOutTime) || '--:--' }}</b></div>
          </div>
        </template>
        <div v-else class="tc-empty">
          <el-icon><Coffee /></el-icon>
          <p>今天还没有预约座位</p>
          <el-button size="small" type="primary" @click="router.push('/student/rooms')">去预约</el-button>
        </div>
      </div>
    </section>

    <!-- 时段状态条 -->
    <section class="strip">
      <div class="strip-item">
        <span>签到时段</span>
        <b class="mono">{{ clock.rules.checkInOpen }} — {{ clock.rules.checkInDeadline }}</b>
        <em v-if="checkInOpenNow" class="live">进行中</em>
      </div>
      <div class="strip-div"></div>
      <div class="strip-item">
        <span>签退时段</span>
        <b class="mono">{{ clock.rules.checkOutStart }} — {{ clock.rules.checkOutEnd }}</b>
        <em v-if="checkOutOpenNow" class="live">进行中</em>
      </div>
      <div class="strip-div"></div>
      <div class="strip-item">
        <span>距 24:00 清场</span>
        <b class="mono countdown">{{ fmtDuration(countdown.midnight) }}</b>
      </div>
    </section>

    <div v-if="today && today.roomType === 'SHORT' && today.status === 'SEATED'" class="notice-box">
      <el-icon><Warning /></el-icon>
      <span>
        普通座位层禁止存放个人物品，请于今日 24:00 前带走随身物品并离场（剩余
        <b>{{ fmtDuration(countdown.midnight) }}</b>）。
      </span>
    </div>

    <!-- 签到 / 签退 -->
    <section class="action-card" v-loading="loading">
      <div class="card-head">
        <h3><span class="ico"><el-icon><AlarmClock /></el-icon></span>签到 / 签退</h3>
        <span class="desc">{{ actionTip }}</span>
      </div>

      <div class="ac-body">
        <div class="ac-buttons">
          <button class="big-btn signin" :class="{ ready: checkInEnabled, locked: checkInLocked }" @click="onCheckIn">
            <el-icon><CircleCheck /></el-icon>
            <span>{{ checkInLabel }}</span>
            <small>{{ checkInHint }}</small>
          </button>
          <button class="big-btn signout" :class="{ ready: checkOutEnabled, locked: checkOutLocked }" @click="onCheckOut">
            <el-icon><SwitchButton /></el-icon>
            <span>{{ checkOutLabel }}</span>
            <small>{{ checkOutHint }}</small>
          </button>
        </div>

        <div class="flow">
          <div class="flow-step" :class="{ done: !!today }">
            <div class="fs-dot"></div>
            <div class="fs-name">预约</div>
            <div class="fs-time mono">{{ timeOf(today?.createTime) || '—' }}</div>
          </div>
          <div class="flow-line" :class="{ on: !!today?.checkInTime }"></div>
          <div class="flow-step" :class="{ done: !!today?.checkInTime }">
            <div class="fs-dot"></div>
            <div class="fs-name">签到</div>
            <div class="fs-time mono">{{ timeOf(today?.checkInTime) || '待签到' }}</div>
          </div>
          <div class="flow-line" :class="{ on: !!today?.checkOutTime }"></div>
          <div class="flow-step" :class="{ done: !!today?.checkOutTime }">
            <div class="fs-dot"></div>
            <div class="fs-name">签退</div>
            <div class="fs-time mono">{{ timeOf(today?.checkOutTime) || '待签退' }}</div>
          </div>
        </div>
      </div>

      <div class="ac-note">
        <el-icon><Clock /></el-icon>
        所有时间以服务器为准，修改本机时间不会影响考勤记录
      </div>
    </section>

    <!-- 底部信息 -->
    <section class="grid-bottom">
      <div class="col-left">
        <div class="card">
          <div class="card-head">
            <h3><span class="ico"><el-icon><Collection /></el-icon></span>固定座位</h3>
            <el-button link type="primary" @click="router.push('/student/long-term')">查看详情</el-button>
          </div>

          <template v-if="longTerm.binding">
            <div class="fx-main">
              <div class="fx-seat mono">{{ longTerm.binding.seatNo }}</div>
              <div class="fx-info">
                <b>{{ longTerm.binding.roomName }}</b>
                <span class="muted">审核通过于 {{ dateOf(longTerm.binding.bindTime) }}</span>
              </div>
            </div>
            <div class="fx-bar"><i :style="{ width: monthPercent + '%' }"></i></div>
            <div class="fx-stat">
              <span>本月有效出勤 <b>{{ longTerm.calendar?.bothDays || 0 }}</b> 天</span>
              <span>考核要求 <b>{{ longTerm.requiredDays }}</b> 天</span>
            </div>
          </template>

          <div v-else-if="longTerm.pending" class="fx-pending">
            <el-icon><Clock /></el-icon>
            <div>
              <b>申请审核中 · {{ longTerm.pending.seatNo }}</b>
              <p>{{ longTerm.pending.roomName }} · 已提交 {{ dateOf(longTerm.pending.applyTime) }}</p>
            </div>
          </div>

          <div v-else class="fx-empty">
            <el-icon><Collection /></el-icon>
            <p>尚未申请固定座位</p>
            <el-button size="small" type="primary" plain @click="router.push('/student/long-term')">去申请</el-button>
          </div>
        </div>

        <div class="card grow">
          <div class="card-head">
            <h3><span class="ico"><el-icon><Bell /></el-icon></span>公告</h3>
            <span class="desc">共 {{ notices.length }} 条</span>
          </div>
          <div v-if="notices.length === 0" class="empty-inline">暂无公告</div>
          <div v-for="n in notices.slice(0, 6)" :key="n.id" class="notice-row" @click="showNotice(n)">
            <div class="nr-body">
              <b>{{ n.title }}</b>
              <span class="muted">{{ dateOf(n.createTime) }}</span>
            </div>
            <el-icon class="nr-arrow"><ArrowRight /></el-icon>
          </div>
        </div>
      </div>

      <div class="card cal-card">
        <div class="card-head">
          <h3><span class="ico"><el-icon><Calendar /></el-icon></span>本月考勤</h3>
          <span class="desc">已打卡 {{ longTerm.calendar?.bothDays || 0 }} 天</span>
        </div>
        <div class="cal">
          <div class="cal-head" v-for="w in ['一', '二', '三', '四', '五', '六', '日']" :key="w">{{ w }}</div>
          <div v-for="b in calendarBlanks" :key="'b' + b" class="cal-cell blank"></div>
          <div
            v-for="d in calendarDays"
            :key="d.day"
            class="cal-cell"
            :class="{ checked: d.checked, today: d.isToday, future: d.future }"
          >
            {{ d.day }}
          </div>
        </div>
        <div class="cal-legend">
          <span><i class="lg-checked"></i>已打卡</span>
          <span><i class="lg-today"></i>今天</span>
          <span class="cal-sum">本月有效出勤 <b>{{ longTerm.calendar?.bothDays || 0 }}</b> / {{ longTerm.requiredDays }} 天</span>
        </div>
      </div>
    </section>
  </div>

  <!-- 公告详情：一句一行 -->
  <el-dialog
    v-model="noticeVisible"
    :title="noticeTitle"
    width="520px"
    class="notice-dialog"
    top="7vh"
    append-to-body
  >
    <div class="notice-lines">
      <p v-for="(line, i) in noticeLines" :key="i">{{ line }}</p>
    </div>
    <template #footer>
      <el-button type="primary" @click="noticeVisible = false">知道了</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { useClockStore } from '@/stores/clock'
import { checkIn, checkOut, getMyLongTerm, getNotices } from '@/api'
import { fetchToday } from '@/utils/today'

const router = useRouter()
const auth = useAuthStore()
const clock = useClockStore()

const loading = ref(false)
const acting = ref(false)
const today = ref(null)
const longTerm = ref({ binding: null, pending: null, requiredDays: 20, calendar: {} })
const notices = ref([])
const countdown = reactive({ midnight: 0 })

/* 公告弹窗 */
const noticeVisible = ref(false)
const noticeTitle = ref('')
const noticeLines = ref([])

/* ---------------- 时间工具 ---------------- */
function computeCountdown() {
  const d = new Date(Date.now() + clock.offset)
  const end = new Date(d)
  end.setHours(24, 0, 0, 0)
  countdown.midnight = Math.max(0, Math.floor((end - d) / 1000))
}

function fmtDuration(sec) {
  if (!sec || sec <= 0) return '00:00:00'
  const h = Math.floor(sec / 3600)
  const m = Math.floor((sec % 3600) / 60)
  const s = sec % 60
  return [h, m, s].map((v) => String(v).padStart(2, '0')).join(':')
}

function timeOf(dt) {
  return dt && dt.length >= 19 ? dt.slice(11, 19) : ''
}

function dateOf(dt) {
  return dt && dt.length >= 10 ? dt.slice(0, 10) : '—'
}

watch(() => clock.time, computeCountdown, { immediate: true })
onMounted(() => {
  computeCountdown()
})

/* ---------------- 时段判断（全部基于服务器时间） ---------------- */
const nowTime = computed(() => clock.time)
const checkInOpenNow = computed(() => nowTime.value >= clock.rules.checkInOpen && nowTime.value <= clock.rules.checkInDeadline)
const checkOutOpenNow = computed(() => nowTime.value >= clock.rules.checkOutStart && nowTime.value <= clock.rules.checkOutEnd)

/** 今天是长期自习室且处于“待签到”状态 */
const isLongWaiting = computed(() => !!today.value && today.value.roomType === 'LONG' && today.value.status === 'WAITING')
/** 今天处于“使用中”状态 */
const isSeated = computed(() => !!today.value && today.value.status === 'SEATED')
const isLongSeated = computed(() => isSeated.value && today.value.roomType === 'LONG')

/** 签到按钮状态 */
const checkInEnabled = computed(() => isLongWaiting.value && checkInOpenNow.value)
const checkInLocked = computed(() => !isLongWaiting.value || !checkInOpenNow.value)
const checkInLabel = computed(() => {
  if (today.value?.checkInTime) return '已签到'
  if (isLongWaiting.value && nowTime.value > clock.rules.checkInDeadline) return '今日签到已错过'
  if (isLongWaiting.value && nowTime.value < clock.rules.checkInOpen) return '签到未开始'
  return '签 到'
})
const checkInHint = computed(() => {
  if (today.value?.checkInTime) return `签到于 ${timeOf(today.value.checkInTime)}`
  if (!today.value) return '尚未预约座位'
  if (today.value.roomType === 'SHORT') return '短期自习室无需签到'
  if (nowTime.value > clock.rules.checkInDeadline) return `已超过 ${clock.rules.checkInDeadline} 截止时间`
  if (nowTime.value < clock.rules.checkInOpen) return `${clock.rules.checkInOpen} 开放签到`
  return `截止 ${clock.rules.checkInDeadline}`
})

/** 签退按钮状态 */
const checkOutEnabled = computed(() => isSeated.value && (!isLongSeated.value || checkOutOpenNow.value))
const checkOutLocked = computed(() => !checkOutEnabled.value)
const checkOutLabel = computed(() => {
  if (today.value?.checkOutTime) return '已签退'
  if (isLongSeated.value && !checkOutOpenNow.value) return '签退时间未到'
  return '签 退'
})
const checkOutHint = computed(() => {
  if (today.value?.checkOutTime) return `签退于 ${timeOf(today.value.checkOutTime)}`
  if (!today.value) return '尚未预约座位'
  if (today.value.status === 'WAITING') {
    return today.value.roomType === 'LONG' ? '需先完成签到' : '无需签到，可直接签退'
  }
  if (isSeated.value && today.value.roomType === 'SHORT') return '24:00 前请及时签退清场'
  if (isLongSeated.value && !checkOutOpenNow.value) {
    return nowTime.value < clock.rules.checkOutStart
      ? `${clock.rules.checkOutStart} 起可签退`
      : `签退时段 ${clock.rules.checkOutStart}—${clock.rules.checkOutEnd}`
  }
  return `建议 ${clock.rules.checkOutStart} 后签退`
})

const actionTip = computed(() => {
  if (!today.value) return '今天还没有预约座位，预约成功后可在此签到 / 签退。'
  if (today.value.status === 'LEFT') return `今天的座位已在 ${timeOf(today.value.checkOutTime)} 签退，祝学习愉快。`
  if (today.value.status === 'CANCELLED') return '今天的预约已取消，如有需要可重新预约。'
  if (today.value.status === 'TIMEOUT') return '今天未在截止时间前签到，预约已失效。'
  if (isLongWaiting.value && nowTime.value > clock.rules.checkInDeadline) return '已错过今日签到时间，本次预约将失效。'
  if (isLongWaiting.value) return `请在 ${clock.rules.checkInDeadline} 前完成签到，避免影响本月考核。`
  if (isSeated.value && today.value.roomType === 'SHORT') return '短期自习室禁止存放个人物品，请于 24:00 前签退离场。'
  if (isSeated.value) return '离座时请记得签退，签退才计入本月有效出勤。'
  return ''
})

/* ---------------- 定位（签到点围栏） ---------------- */
const locating = ref(false)

/**
 * 获取浏览器当前位置（WGS-84）
 * @returns {Promise<{lat:number, lng:number}|null>} 失败时返回 null（错误已提示）
 */
function getPosition() {
  return new Promise((resolve) => {
    if (!navigator.geolocation) {
      ElMessage.error('当前浏览器不支持定位，无法完成操作')
      return resolve(null)
    }
    locating.value = true
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        locating.value = false
        resolve({ lat: pos.coords.latitude, lng: pos.coords.longitude })
      },
      (err) => {
        locating.value = false
        const msg =
          err.code === 1
            ? '定位权限被拒绝，请在浏览器地址栏允许定位后重试'
            : err.code === 2
              ? '无法获取位置信息，请检查定位服务或网络'
              : '定位超时，请到窗边或室外重试'
        ElMessage.error(msg)
        resolve(null)
      },
      { enableHighAccuracy: true, timeout: 15000, maximumAge: 0 }
    )
  })
}

/* ---------------- 签到 / 签退 ---------------- */
function onCheckIn() {
  if (acting.value || locating.value) return
  if (!checkInEnabled.value) {
    if (!today.value) {
      ElMessage.warning('今天还没有预约座位，请先预约')
    } else if (today.value.checkInTime) {
      ElMessage.info(`今日已签到（${timeOf(today.value.checkInTime)}）`)
    } else if (today.value.roomType !== 'LONG') {
      ElMessage.info('短期自习室无需签到，预约后即可使用')
    } else if (nowTime.value > clock.rules.checkInDeadline) {
      ElMessage.error('已经错过今日签到时间（截止 ' + clock.rules.checkInDeadline + '）')
    } else if (nowTime.value < clock.rules.checkInOpen) {
      ElMessage.warning(`签到通道 ${clock.rules.checkInOpen} 开放，请稍后再试`)
    } else {
      ElMessage.warning('当前状态无需签到')
    }
    return
  }
  doCheckIn()
}

async function doCheckIn() {
  const pos = await getPosition()
  if (!pos) return
  try {
    await ElMessageBox.confirm(
      `将以服务器时间 ${clock.time} 签到，签到时需要开启定位。确认吗？`,
      '签到确认',
      { type: 'info', confirmButtonText: '确认签到', cancelButtonText: '取消' }
    )
  } catch (e) {
    return
  }
  acting.value = true
  try {
    const res = await checkIn(today.value.id, pos.lat, pos.lng)
    today.value = { ...today.value, ...res.data }
    ElMessage.success(`签到成功：${timeOf(res.data.checkInTime)}`)
  } catch (e) {
    loadAll()
  } finally {
    acting.value = false
  }
}

function onCheckOut() {
  if (acting.value || locating.value) return
  if (!checkOutEnabled.value) {
    if (!today.value) {
      ElMessage.warning('今天还没有预约座位')
    } else if (today.value.status === 'LEFT') {
      ElMessage.info('今天已经签退过了')
    } else if (today.value.status === 'WAITING') {
      ElMessage.warning(today.value.roomType === 'LONG' ? '请先完成签到再签退' : '当前无签到记录')
    } else if (isLongSeated.value && !checkOutOpenNow.value) {
      ElMessage.warning(`签退时间还没到，请在 ${clock.rules.checkOutStart}—${clock.rules.checkOutEnd} 之间签退`)
    } else {
      ElMessage.warning('当前状态无需签退')
    }
    return
  }
  doCheckOut()
}

async function doCheckOut() {
  const pos = await getPosition()
  if (!pos) return
  try {
    await ElMessageBox.confirm(
      today.value.roomType === 'SHORT'
        ? '确认签退并离场？请记得带走随身物品。'
        : '确认签退？签退后才计入本月有效出勤。',
      '签退确认',
      { type: 'warning', confirmButtonText: '确认签退', cancelButtonText: '取消' }
    )
  } catch (e) {
    return
  }
  acting.value = true
  try {
    const res = await checkOut(today.value.id, pos.lat, pos.lng)
    today.value = { ...today.value, ...res.data }
    ElMessage.success(`签退成功：${timeOf(res.data.checkOutTime)}`)
  } catch (e) {
    loadAll()
  } finally {
    acting.value = false
  }
}

/* ---------------- 数据加载 ---------------- */
function statusPill(status) {
  return (
    {
      WAITING: 'pill-wait',
      SEATED: 'pill-seated',
      LEFT: 'pill-left',
      CANCELLED: 'pill-left',
      TIMEOUT: 'pill-timeout'
    }[status] || 'pill-left'
  )
}

async function loadAll() {
  loading.value = true
  try {
    const [t, lt, n] = await Promise.all([
      fetchToday(),
      getMyLongTerm().catch(() => ({ data: {} })),
      getNotices(5).catch(() => ({ data: [] }))
    ])
    today.value = t
    longTerm.value = lt.data || { binding: null, pending: null, requiredDays: 20, calendar: {} }
    notices.value = n.data || []
  } finally {
    loading.value = false
  }
}

const monthPercent = computed(() => {
  const need = longTerm.value.requiredDays || 20
  const got = longTerm.value.calendar?.bothDays || 0
  return Math.min(100, Math.round((got / need) * 100))
})

const calendarDays = computed(() => {
  const [y, m] = (clock.today || new Date().toISOString().slice(0, 10)).split('-').map(Number)
  const total = new Date(y, m, 0).getDate()
  const checkedSet = new Set(longTerm.value.calendar?.checkInDates || [])
  const todayDay = new Date().getDate()
  const days = []
  for (let i = 1; i <= total; i++) {
    const str = `${y}-${String(m).padStart(2, '0')}-${String(i).padStart(2, '0')}`
    days.push({ day: i, checked: checkedSet.has(str), isToday: i === todayDay, future: i > todayDay })
  }
  return days
})

const calendarBlanks = computed(() => {
  const [y, m] = (clock.today || new Date().toISOString().slice(0, 10)).split('-').map(Number)
  const first = new Date(y, m - 1, 1).getDay()
  return (first + 6) % 7
})

function showNotice(n) {
  noticeTitle.value = n.title || '公告'
  noticeLines.value = splitSentences(n.content)
  noticeVisible.value = true
}

/**
 * 把公告正文拆成一行一句：
 * 1. 先按原生换行拆；2. 再按中英文句末标点（。！？；.!?;）拆。
 */
function splitSentences(text) {
  return String(text || '')
    .replace(/\r/g, '')
    .split(/\n+/)
    .flatMap((line) => line.split(/(?<=[。！？；!?;])/))
    .map((s) => s.trim())
    .filter(Boolean)
}

onMounted(loadAll)
onUnmounted(() => {})
</script>

<style scoped>
.home {
  width: 100%;
}

/* ---------- 顶部 ---------- */
.top-row {
  display: grid;
  grid-template-columns: minmax(0, 1.7fr) minmax(260px, 0.8fr);
  gap: 12px;
  margin-bottom: 12px;
}

.hero {
  position: relative;
  border-radius: var(--radius-lg);
  border: 1px solid var(--c-line);
  background: #fff;
  padding: 20px 22px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  overflow: hidden;
}

.hero::before {
  content: '';
  position: absolute;
  inset: 0 auto 0 0;
  width: 3px;
  background: var(--brand);
}

.hero-tag {
  display: inline-block;
  font-size: 11.5px;
  font-weight: 500;
  color: var(--gray-500);
  background: var(--gray-100);
  border-radius: 4px;
  padding: 1px 7px;
  margin-bottom: 8px;
}

.hero h1 {
  margin: 0;
  font-size: 21px;
  font-weight: 650;
  letter-spacing: -0.02em;
  color: var(--gray-900);
}

.hero p {
  margin: 5px 0 0;
  font-size: 12.5px;
  color: var(--gray-500);
}

.hero-clock {
  text-align: right;
  flex-shrink: 0;
}

.hc-label {
  font-size: 11px;
  color: var(--gray-400);
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 5px;
}

.hc-label i {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--green);
}

.hc-label i.off {
  background: var(--amber);
}

.clock-big {
  font-size: 34px;
  font-weight: 600;
  line-height: 1.15;
  letter-spacing: -0.02em;
  color: var(--gray-900);
  font-variant-numeric: tabular-nums;
}

.hc-day {
  font-size: 11.5px;
  color: var(--gray-400);
}

/* 今日座位 */
.today-card {
  background: #fff;
  border: 1px solid var(--c-line);
  border-radius: var(--radius-lg);
  padding: 14px 16px;
  display: flex;
  flex-direction: column;
}

.tc-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
  color: var(--gray-400);
}

.ghost-btn {
  width: 24px;
  height: 24px;
  border-radius: var(--radius-sm);
  border: 1px solid transparent;
  background: transparent;
  color: var(--gray-400);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.ghost-btn:hover {
  background: var(--gray-100);
  color: var(--gray-700);
}

.tc-seat {
  font-size: 26px;
  font-weight: 650;
  color: var(--gray-900);
  letter-spacing: -0.01em;
  margin-top: 6px;
  line-height: 1.15;
  font-variant-numeric: tabular-nums;
}

.tc-room {
  font-size: 12.5px;
  color: var(--gray-500);
  margin-top: 1px;
}

.tc-tags {
  display: flex;
  gap: 5px;
  margin-top: 8px;
  flex-wrap: wrap;
}

.tc-times {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  margin-top: auto;
  padding-top: 10px;
  border-top: 1px solid var(--gray-200);
}

.tc-times span {
  display: block;
  font-size: 10.5px;
  color: var(--gray-400);
}

.tc-times b {
  font-size: 13.5px;
  font-weight: 600;
  color: var(--gray-900);
}

.tc-empty {
  margin: auto;
  text-align: center;
  color: var(--gray-400);
  font-size: 12.5px;
}

.tc-empty .el-icon {
  font-size: 26px;
  color: var(--gray-300);
}

.tc-empty p {
  margin: 6px 0 10px;
}

/* ---------- 时段条 ---------- */
.strip {
  display: flex;
  align-items: center;
  gap: 8px;
  background: #fff;
  border: 1px solid var(--c-line);
  border-radius: var(--radius-lg);
  padding: 10px 18px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.strip-item {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  min-width: 170px;
}

.strip-item span {
  font-size: 11.5px;
  color: var(--gray-400);
  flex-shrink: 0;
}

.strip-item b {
  font-size: 13px;
  font-weight: 600;
  color: var(--gray-900);
}

.strip-item .countdown {
  color: var(--c-accent);
}

.live {
  font-style: normal;
  font-size: 10.5px;
  font-weight: 500;
  color: var(--green);
  background: var(--green-soft);
  border: 1px solid var(--green-border);
  border-radius: 4px;
  padding: 0 5px;
  line-height: 17px;
}

.strip-div {
  width: 1px;
  height: 18px;
  background: var(--gray-200);
}

/* ---------- 操作台 ---------- */
.action-card {
  background: #fff;
  border: 1px solid var(--c-line);
  border-radius: var(--radius-lg);
  padding: 16px 18px;
  margin-bottom: 12px;
}

.action-card .card-head .desc {
  max-width: 60%;
}

.ac-body {
  display: grid;
  grid-template-columns: minmax(0, 1.05fr) minmax(0, 1fr);
  gap: 20px;
  align-items: center;
}

.ac-buttons {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.big-btn {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 3px;
  padding: 14px 16px;
  border-radius: var(--radius);
  border: 1px solid var(--gray-200);
  background: var(--gray-50);
  color: var(--gray-400);
  cursor: pointer;
  font-family: inherit;
  text-align: left;
  transition: all 0.14s;
}

.big-btn .el-icon {
  font-size: 17px;
}

.big-btn span {
  font-size: 15px;
  font-weight: 600;
}

.big-btn small {
  font-size: 11px;
  opacity: 0.9;
}

.big-btn.ready {
  color: #fff;
  border-color: transparent;
}

.big-btn.signin.ready {
  background: var(--brand);
  box-shadow: 0 1px 2px rgba(37, 99, 235, 0.28);
}

.big-btn.signin.ready:hover {
  background: var(--brand-hover);
}

.big-btn.signout.ready {
  background: #b45309;
  box-shadow: 0 1px 2px rgba(180, 83, 9, 0.25);
}

.big-btn.signout.ready:hover {
  background: #92400e;
}

.big-btn.locked {
  cursor: not-allowed;
}

/* 流程 */
.flow {
  display: flex;
  align-items: center;
}

.flow-step {
  text-align: center;
  min-width: 58px;
}

.fs-dot {
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: var(--gray-300);
  margin: 0 auto 6px;
  transition: background 0.2s;
}

.flow-step.done .fs-dot {
  background: var(--brand);
  box-shadow: 0 0 0 3px var(--brand-soft);
}

.fs-name {
  font-size: 12px;
  font-weight: 600;
  color: var(--gray-700);
}

.fs-time {
  font-size: 10.5px;
  color: var(--gray-400);
}

.flow-line {
  flex: 1;
  height: 1px;
  background: var(--gray-200);
  margin-bottom: 24px;
}

.flow-line.on {
  background: var(--brand-border);
}

.ac-note {
  display: flex;
  align-items: center;
  gap: 5px;
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px solid var(--gray-200);
  font-size: 11.5px;
  color: var(--gray-400);
}

/* ---------- 底部 ---------- */
.grid-bottom {
  display: grid;
  grid-template-columns: minmax(0, 1.5fr) minmax(0, 1fr);
  gap: 12px;
  align-items: stretch;
}

.col-left {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-width: 0;
}

.grid-bottom .card {
  margin-top: 0;
}

.grow {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.cal-card {
  display: flex;
  flex-direction: column;
}

.fx-main {
  display: flex;
  align-items: center;
  gap: 12px;
}

.fx-seat {
  font-size: 17px;
  font-weight: 650;
  color: var(--gray-900);
  background: var(--gray-100);
  border-radius: var(--radius-sm);
  padding: 6px 12px;
  font-variant-numeric: tabular-nums;
}

.fx-info b {
  display: block;
  font-size: 12.5px;
  font-weight: 600;
}

.fx-info span {
  font-size: 11.5px;
}

.fx-bar {
  height: 5px;
  border-radius: 999px;
  background: var(--gray-100);
  overflow: hidden;
  margin: 12px 0 7px;
}

.fx-bar i {
  display: block;
  height: 100%;
  border-radius: 999px;
  background: var(--brand);
}

.fx-stat {
  display: flex;
  justify-content: space-between;
  font-size: 11.5px;
  color: var(--gray-400);
}

.fx-stat b {
  color: var(--gray-900);
  font-weight: 600;
}

.fx-pending {
  display: flex;
  gap: 9px;
  align-items: flex-start;
  background: var(--violet-soft);
  border: 1px solid var(--violet-border);
  border-radius: var(--radius);
  padding: 10px 12px;
  color: #6d28d9;
  font-size: 12px;
}

.fx-pending b {
  font-size: 12.5px;
  font-weight: 600;
}

.fx-pending p {
  margin: 2px 0 0;
  font-size: 11.5px;
  opacity: 0.9;
}

.fx-empty {
  text-align: center;
  color: var(--gray-400);
  font-size: 12.5px;
  padding: 12px 0;
}

.fx-empty .el-icon {
  font-size: 22px;
  color: var(--gray-300);
}

.fx-empty p {
  margin: 5px 0 9px;
}

/* 公告 */
.notice-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 2px;
  border-bottom: 1px solid var(--gray-200);
  cursor: pointer;
}

.notice-row:last-child {
  border-bottom: none;
}

.notice-row:hover .nr-body b {
  color: var(--brand);
}

.notice-row:hover .nr-arrow {
  color: var(--brand);
}

.nr-body {
  flex: 1;
  min-width: 0;
}

.nr-body b {
  display: block;
  font-size: 12.5px;
  font-weight: 500;
  color: var(--gray-900);
  line-height: 1.5;
  transition: color 0.12s;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.nr-body span {
  font-size: 11px;
}

.nr-arrow {
  font-size: 12px;
  color: var(--gray-300);
}

/* 日历 */
.cal {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 3px;
}

.cal-head {
  text-align: center;
  font-size: 10.5px;
  color: var(--gray-400);
  padding-bottom: 4px;
}

.cal-cell {
  height: 30px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-sm);
  font-size: 11.5px;
  color: var(--gray-600);
  background: var(--gray-50);
  font-variant-numeric: tabular-nums;
}

.cal-cell.blank {
  background: transparent;
}

.cal-cell.future {
  color: var(--gray-300);
  background: transparent;
}

.cal-cell.checked {
  background: var(--brand);
  color: #fff;
  font-weight: 600;
}

.cal-cell.today {
  box-shadow: inset 0 0 0 1.5px var(--c-accent);
}

.cal-legend {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-top: auto;
  padding-top: 12px;
  border-top: 1px solid var(--gray-200);
  font-size: 11px;
  color: var(--gray-400);
}

.cal-legend span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.cal-legend i {
  width: 10px;
  height: 10px;
  border-radius: 3px;
  display: inline-block;
}

.lg-checked {
  background: var(--brand);
}

.lg-today {
  background: #fff;
  box-shadow: inset 0 0 0 1.5px var(--c-accent);
}

.cal-legend .cal-sum {
  margin-left: auto;
  color: var(--gray-500);
}

.cal-legend .cal-sum b {
  color: var(--gray-900);
  font-weight: 600;
}

@media (max-width: 1180px) {
  .top-row {
    grid-template-columns: 1fr;
  }
  .ac-body {
    grid-template-columns: 1fr;
  }
  .grid-bottom {
    grid-template-columns: 1fr;
  }
}

/* ---------- 手机 ---------- */
@media (max-width: 640px) {
  .hero {
    flex-direction: column;
    align-items: flex-start;
    gap: 10px;
    padding: 14px 16px;
  }

  .hero h1 {
    font-size: 18px;
    line-height: 1.35;
  }

  .hero p {
    font-size: 12px;
  }

  .hero-clock {
    text-align: left;
    width: 100%;
    padding-top: 10px;
    border-top: 1px solid var(--gray-200);
  }

  /* 顶部时钟数字略缩小，避免与日期挤在一行 */
  .hero-clock .clock-big,
  .hero-clock .time {
    font-size: 22px !important;
  }

  /* 时段条：两列改单列，干掉多余分隔线 */
  .strip {
    grid-template-columns: 1fr !important;
    gap: 8px !important;
  }

  /* 签到签退两个大按钮堆叠更易点 */
  .ac-body {
    gap: 10px !important;
  }

  .big-btn {
    min-height: 84px;
  }

  /* 日历格子高度稍微收一点 */
  .cal-cell {
    height: 28px !important;
  }
}
</style>
