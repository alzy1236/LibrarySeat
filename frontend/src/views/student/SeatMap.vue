<template>
  <div class="page seat-page" v-loading="loading">
    <!-- 顶部 -->
    <div class="seat-head">
      <button class="back-chip" @click="router.push('/student/rooms')">
        <el-icon><ArrowLeft /></el-icon>
        返回楼层
      </button>

      <div class="head-main">
        <h1>
          {{ floorLabel }}
          <span class="room-name">{{ room?.name || '—' }}</span>
        </h1>
        <div class="meta-row">
          <span class="m-chip">共 <b>{{ seats.length }}</b> 座</span>
          <span class="m-chip m-ok">空闲 <b>{{ available }}</b></span>
          <span class="m-chip" :class="room?.roomType === 'LONG' ? 'm-long' : 'm-short'">
            {{ room?.roomType === 'LONG' ? '固定座位层' : '普通座位层' }}
          </span>
        </div>
      </div>

      <el-button class="refresh-btn" circle :icon="Refresh" @click="load" />
    </div>

    <!-- 规则提示 -->
    <div class="hint" :class="room?.roomType === 'LONG' ? 'hint-long' : 'hint-short'">
      <el-icon><InfoFilled /></el-icon>
      <span>
        <template v-if="room?.roomType === 'LONG'">
          固定座位层：只能选本人已审核通过的座位，每日 08:30 前签到、21:00—24:00 签退，月出勤不足 20 天将取消座位。
        </template>
        <template v-else>
          普通座位层：禁止存放个人物品，当天 24:00 前带走随身物品并离场（服务器时间 {{ clock.time }}）。
        </template>
      </span>
    </div>

    <!-- 今日已有预约 -->
    <div v-if="todayActive" class="today-bar">
      <el-icon><Location /></el-icon>
      <span>今日已预约 <b>{{ todayActive.roomName }} · {{ todayActive.seatNo }}</b>（{{ todayActive.statusName }}）</span>
      <el-button size="small" text type="primary" @click="router.push('/student/home')">查看考勤</el-button>
    </div>

    <!-- 座位图 -->
    <div class="card seat-card">
      <div class="legend-row">
        <span class="lg"><i class="lg-free"></i>空闲可选</span>
        <span class="lg"><i class="lg-wait"></i>已预约</span>
        <span class="lg"><i class="lg-taken"></i>使用中</span>
        <span class="lg"><i class="lg-fixed"></i>固定座位</span>
        <span class="lg"><i class="lg-off"></i>停用维修</span>
      </div>

      <div class="desk-room">
        <div v-for="(rowSeats, rowIdx) in seatRows" :key="rowIdx" class="desk-row">
          <el-tooltip
            v-for="seat in rowSeats"
            :key="seat.id"
            placement="top"
            :show-after="120"
            :content="tooltip(seat)"
          >
            <div
              class="desk"
              :class="[deskClass(seat), { 'desk-mine': isMineSeat(seat), 'desk-picked': selected && selected.id === seat.id }]"
              @click="pick(seat)"
            >
              <div class="desk-no">{{ shortNo(seat.seatNo) }}</div>
              <div class="desk-state">
                <el-icon v-if="seat.hasPower === 1" class="bolt"><Lightning /></el-icon>
                {{ stateText(seat) }}
              </div>
            </div>
          </el-tooltip>
        </div>

        <div class="desk-end">—— 第 {{ floorNum }} 层 共 {{ seats.length }} 座 ——</div>
      </div>
    </div>

    <!-- 底部操作条 -->
    <div class="checkout-bar">
      <div class="co-left">
        <div class="co-item">
          <span class="co-label">已选座位</span>
          <b class="co-seat">{{ selected ? selected.seatNo : '未选择' }}</b>
        </div>
        <div class="co-item">
          <span class="co-label">服务器时间</span>
          <b class="co-time mono">{{ clock.time }}</b>
        </div>
      </div>
      <el-button type="primary" size="large" class="co-btn" :disabled="!selected" :loading="submitting" @click="submit">
        {{ selected ? `确认预约 · ${shortNo(selected.seatNo)}` : '请选择座位' }}
      </el-button>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, InfoFilled, Lightning, Location, Refresh } from '@element-plus/icons-vue'
import { studentRoomDetail, reserveSeat, getMyLongTerm } from '@/api'
import { fetchTodayActive, isActive } from '@/utils/today'
import { useClockStore } from '@/stores/clock'

const route = useRoute()
const router = useRouter()
const clock = useClockStore()

const loading = ref(false)
const submitting = ref(false)
const room = ref(null)
const seats = ref([])
const available = ref(0)
const todayActive = ref(null)
const selected = ref(null)
const binding = ref(null)

const roomId = computed(() => Number(route.params.id))
const floorNum = computed(() => {
  const m = (room.value?.location || '').match(/(\d)\s*层/)
  return m ? m[1] : '—'
})
const floorLabel = computed(() => {
  const m = (room.value?.location || '').match(/(\d)\s*层/)
  return m ? `第 ${m[1]} 层` : '选择座位'
})
const seatRows = computed(() => {
  const rows = {}
  seats.value.forEach((s) => {
    const r = s.rowNo || 1
    if (!rows[r]) rows[r] = []
    rows[r].push(s)
  })
  return Object.keys(rows)
    .sort((a, b) => a - b)
    .map((k) => rows[k].sort((a, b) => (a.colNo || 0) - (b.colNo || 0)))
})

function shortNo(seatNo) {
  if (!seatNo) return '--'
  const p = seatNo.split('-')
  return p.length > 1 ? p[1] : seatNo
}

function isMineSeat(seat) {
  return !!(binding.value && binding.value.seatId === seat.id)
}

function deskClass(seat) {
  if (seat.status === 0) return 'desk-off'
  const map = {
    AVAILABLE: 'desk-free',
    RESERVED: 'desk-wait',
    SEATED: 'desk-taken',
    OVERDUE: 'desk-overdue',
    LOCKED: 'desk-fixed'
  }
  return map[seat.seatState] || 'desk-free'
}

function stateText(seat) {
  if (seat.status === 0) return '维修'
  const map = { AVAILABLE: '空闲', RESERVED: '已预约', SEATED: '使用中', OVERDUE: '超时', LOCKED: '固定' }
  return map[seat.seatState] || '空闲'
}

function tooltip(seat) {
  const power = seat.hasPower === 1 ? ' · 有电源' : ''
  if (seat.seatState === 'LOCKED') {
    return `${seat.seatNo}${power} · ${isMineSeat(seat) ? '我的固定座位' : '他人固定座位'}`
  }
  return `${seat.seatNo}${power} · ${stateText(seat)}`
}

function pick(seat) {
  if (seat.status === 0) {
    ElMessage.warning(`${seat.seatNo} 正在维修，暂不可选`)
    return
  }
  if (seat.seatState === 'AVAILABLE') {
    selected.value = seat
    return
  }
  if (seat.seatState === 'LOCKED' && isMineSeat(seat)) {
    selected.value = seat
    return
  }
  const tips = {
    RESERVED: `${seat.seatNo} 今日已被预约`,
    SEATED: `${seat.seatNo} 正在使用中`,
    OVERDUE: `${seat.seatNo} 使用者超时未清场，请联系管理员`,
    LOCKED: `${seat.seatNo} 已被其他同学固定`
  }
  ElMessage.warning(tips[seat.seatState] || '该座位不可选')
}

async function load() {
  loading.value = true
  try {
    const [d, t, lt] = await Promise.all([
      studentRoomDetail(roomId.value),
      fetchTodayActive(),
      getMyLongTerm().catch(() => ({ data: {} }))
    ])
    room.value = d.data.room
    seats.value = d.data.seats
    available.value = d.data.available
    todayActive.value = t
    binding.value = lt.data?.binding || null
    selected.value = null
  } finally {
    loading.value = false
  }
}

async function submit() {
  if (!selected.value) return
  if (isActive(todayActive.value)) {
    ElMessage.warning('今日已预约座位，每天只能预约一个座位')
    return
  }
  if (room.value?.roomType === 'LONG') {
    if (!binding.value) {
      try {
        await ElMessageBox.confirm('固定座位层只对已审核通过的学生开放，是否前往申请固定座位？', '尚未拥有固定座位', {
          type: 'warning',
          confirmButtonText: '去申请',
          cancelButtonText: '取消'
        })
        router.push('/student/long-term')
      } catch (e) {}
      return
    }
    if (binding.value.seatId !== selected.value.id) {
      ElMessage.warning(`您的固定座位是 ${binding.value.seatNo}，请选择该座位`)
      return
    }
  }
  submitting.value = true
  try {
    await ElMessageBox.confirm(
      `确认预约 ${room.value.name} 的座位 ${selected.value.seatNo}？（服务器时间 ${clock.time}）`,
      '确认预约',
      { type: 'info', confirmButtonText: '确认预约', cancelButtonText: '再想想' }
    )
    await reserveSeat(selected.value.id)
    ElMessage.success(`预约成功：${selected.value.seatNo}`)
    router.push('/student/home')
  } catch (e) {
    if (e?.code) load()
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.seat-page {
  padding-bottom: 96px;
}

/* ---------- 顶部 ---------- */
.seat-head {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 14px;
}

.back-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 30px;
  padding: 0 12px 0 9px;
  border-radius: 999px;
  border: 1px solid var(--c-line);
  background: var(--c-surface);
  color: var(--c-text-2);
  font-size: 12px;
  cursor: pointer;
  transition: all 0.15s ease;
  flex-shrink: 0;
  margin-top: 2px;
}

.back-chip:hover {
  color: var(--brand);
  border-color: var(--brand-border);
  background: var(--brand-soft);
}

.head-main {
  flex: 1;
  min-width: 0;
}

.head-main h1 {
  margin: 0;
  font-size: 19px;
  font-weight: 700;
  letter-spacing: -0.01em;
  color: var(--c-text);
  display: flex;
  align-items: baseline;
  gap: 8px;
  flex-wrap: wrap;
}

.room-name {
  font-size: 12.5px;
  font-weight: 500;
  color: var(--c-text-3);
}

.meta-row {
  display: flex;
  gap: 6px;
  margin-top: 8px;
  flex-wrap: wrap;
}

.m-chip {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  height: 22px;
  padding: 0 9px;
  border-radius: 999px;
  background: var(--gray-100);
  color: var(--gray-600);
  font-size: 11.5px;
}

.m-chip b {
  color: var(--gray-800);
  font-weight: 600;
}

.m-ok {
  background: #ecfdf5;
  color: #047857;
}

.m-ok b {
  color: #047857;
}

.m-long {
  background: var(--brand-soft);
  color: var(--brand-hover);
}

.m-short {
  background: #fffbeb;
  color: #92400e;
}

.refresh-btn {
  flex-shrink: 0;
}

/* ---------- 提示 ---------- */
.hint {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  border-radius: var(--radius);
  padding: 11px 14px;
  font-size: 12.5px;
  line-height: 1.7;
  margin-bottom: 12px;
}

.hint-long {
  background: var(--brand-soft);
  border: 1px solid var(--brand-border);
  color: #134e4a;
}

.hint-short {
  background: #fffbeb;
  border: 1px solid #fde68a;
  color: #92400e;
}

.today-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  background: var(--c-surface);
  border: 1px dashed var(--c-line-strong);
  border-radius: var(--radius);
  padding: 9px 14px;
  font-size: 12.5px;
  color: var(--c-text-2);
  margin-bottom: 12px;
  flex-wrap: wrap;
}

/* ---------- 座位卡片 ---------- */
.seat-card {
  padding: 14px 14px 16px;
}

.legend-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 14px;
  padding-bottom: 12px;
  margin-bottom: 12px;
  border-bottom: 1px dashed var(--gray-200);
}

.lg {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 11.5px;
  color: var(--c-text-2);
}

.lg i {
  width: 11px;
  height: 11px;
  border-radius: 3px;
  border: 1px solid var(--gray-300);
  display: inline-block;
}

.lg i.lg-free {
  background: #fff;
  border-color: #d6d3d1;
}

.lg i.lg-wait {
  background: var(--amber);
  border-color: var(--amber);
}

.lg i.lg-taken {
  background: var(--red-soft);
  border-color: var(--red-border);
}

.lg i.lg-fixed {
  background: var(--brand-soft);
  border-color: var(--brand-border);
}

.lg i.lg-off {
  background: var(--gray-200);
  border-color: var(--gray-300);
}

/* 座位区域（去掉讲台后更紧凑） */
.desk-room {
  background: linear-gradient(180deg, #fbfbfa 0%, #f7f7f6 100%);
  border: 1px solid var(--gray-200);
  border-radius: var(--radius);
  padding: 16px 14px 14px;
  max-width: 100%;
  margin: 0;
}

.desk-end {
  text-align: center;
  font-size: 10.5px;
  color: var(--c-text-3);
  letter-spacing: 1px;
  margin-top: 4px;
}

.bolt {
  font-size: 10px;
}

/* ---------- 底部操作条 ---------- */
.checkout-bar {
  position: sticky;
  bottom: 12px;
  margin-top: 14px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  background: rgba(255, 255, 255, 0.94);
  backdrop-filter: blur(10px);
  border: 1px solid var(--c-line);
  border-radius: var(--radius);
  padding: 12px 16px;
  box-shadow: 0 6px 20px rgba(16, 24, 40, 0.08);
}

.co-left {
  display: flex;
  gap: 28px;
  min-width: 0;
}

.co-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.co-label {
  font-size: 10.5px;
  color: var(--c-text-3);
}

.co-seat {
  font-size: 17px;
  font-weight: 700;
  color: var(--brand);
  letter-spacing: 0.01em;
  white-space: nowrap;
}

.co-time {
  font-size: 14px;
  color: var(--c-text-2);
  white-space: nowrap;
}

.co-btn {
  flex-shrink: 0;
  min-width: 140px;
}

/* ---------- 手机端 ---------- */
@media (max-width: 700px) {
  .seat-head {
    flex-wrap: wrap;
    gap: 8px;
  }

  .head-main {
    order: 3;
    flex-basis: 100%;
  }

  .head-main h1 {
    font-size: 17px;
  }

  .back-chip {
    order: 1;
  }

  .refresh-btn {
    order: 2;
    margin-left: auto;
  }

  .seat-card {
    padding: 12px 10px 14px;
  }

  .desk-room {
    padding: 12px 8px 10px;
  }

  .legend-row {
    gap: 6px 10px;
  }

  .lg {
    font-size: 10.5px;
  }

  .checkout-bar {
    flex-wrap: wrap;
    padding: 10px 12px;
    gap: 10px;
  }

  .co-left {
    gap: 18px;
    flex: 1;
  }

  .co-seat {
    font-size: 16px;
  }

  .co-btn {
    flex-basis: 100%;
    width: 100%;
    margin-left: 0;
  }
}
</style>