<template>
  <div class="page" v-loading="loading">
    <div class="page-head">
      <div>
        <h1>{{ floorLabel }}</h1>
        <div class="sub">
          {{ room?.name || '—' }} · 共 {{ seats.length }} 个座位 · 剩余 {{ available }} 个
          <span class="pill" v-if="room" :class="room.roomType === 'LONG' ? 'pill-long' : 'pill-short'" style="margin-left: 8px">
            {{ room.roomType === 'LONG' ? '固定座位层' : '普通座位层' }}
          </span>
        </div>
      </div>
      <div class="head-actions">
        <el-button :icon="Back" @click="router.push('/student/rooms')">返回楼层</el-button>
        <el-button :icon="Refresh" @click="load">刷新</el-button>
      </div>
    </div>

    <div class="hint" :class="room?.roomType === 'LONG' ? 'hint-long' : 'hint-short'">
      <el-icon><InfoFilled /></el-icon>
      <template v-if="room?.roomType === 'LONG'">
        本层为固定座位层，只能选择本人已审核通过的固定座位；每日 08:30 前签到、21:00—24:00 签退，每月有效出勤不足 20 天取消座位。
      </template>
      <template v-else>
        本层为普通座位层，禁止存放个人物品，当天 24:00 前须带走随身物品并离场（服务器时间 {{ clock.time }}）。
      </template>
    </div>

    <div v-if="todayActive" class="today-bar">
      <el-icon><Location /></el-icon>
      今日已预约 <b>{{ todayActive.roomName }} · {{ todayActive.seatNo }}</b>（{{ todayActive.statusName }}）
      <el-button size="small" text type="primary" @click="router.push('/student/home')">查看考勤</el-button>
    </div>

    <div class="card">
      <div class="card-head">
        <h3><span class="ico"><el-icon><Grid /></el-icon></span>座位图 · 桌子样式</h3>
        <div class="desk-legend">
          <span><i class="lg-free"></i>空闲可选</span>
          <span><i class="lg-wait"></i>已预约待签到</span>
          <span><i class="lg-taken"></i>使用中</span>
          <span><i class="lg-fixed"></i>固定座位</span>
          <span><i class="lg-off"></i>停用维修</span>
        </div>
      </div>

      <div class="desk-room">
        <div class="desk-board">讲 台</div>

        <div v-for="(rowSeats, rowIdx) in seatRows" :key="rowIdx" class="desk-row">
          <el-tooltip
            v-for="seat in rowSeats"
            :key="seat.id"
            placement="top"
            :show-after="120"
            :content="tooltip(seat)"
          >
            <div class="desk" :class="[deskClass(seat), { 'desk-mine': isMineSeat(seat), 'desk-picked': selected && selected.id === seat.id }]" @click="pick(seat)">
              <div class="desk-no">{{ shortNo(seat.seatNo) }}</div>
              <div class="desk-state">
                <el-icon v-if="seat.hasPower === 1" class="bolt"><Lightning /></el-icon>
                {{ stateText(seat) }}
              </div>
            </div>
          </el-tooltip>
        </div>

        <div class="desk-end">
          <span>—— 本层为 {{ room?.name }} ——</span>
        </div>
      </div>
    </div>

    <div class="checkout-bar">
      <div class="co-left">
        <div>
          <span class="muted">已选座位</span>
          <b class="co-seat">{{ selected ? selected.seatNo : '未选择' }}</b>
        </div>
        <div>
          <span class="muted">服务器时间</span>
          <b class="mono">{{ clock.time }}</b>
        </div>
      </div>
      <el-button type="primary" size="large" :disabled="!selected" :loading="submitting" @click="submit">
        确认预约{{ selected ? ' · ' + shortNo(selected.seatNo) : '' }}
      </el-button>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Back, Refresh } from '@element-plus/icons-vue'
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
  const map = { AVAILABLE: '空闲', RESERVED: '待签到', SEATED: '使用中', OVERDUE: '超时', LOCKED: '固定' }
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
    RESERVED: `${seat.seatNo} 今日已被预约（待签到）`,
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
.head-actions {
  display: flex;
  gap: 10px;
}

.hint {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  border-radius: var(--radius);
  padding: 12px 16px;
  font-size: 13px;
  line-height: 1.7;
  margin-bottom: 14px;
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
  padding: 10px 16px;
  font-size: 13px;
  color: var(--c-text-2);
  margin-bottom: 14px;
}

.desk-legend i.lg-free {
  background: #ecfdf5;
  border-color: #a7f3d0;
}

.desk-legend i.lg-wait {
  background: #fffbeb;
  border-color: #fde68a;
}

.desk-legend i.lg-taken {
  background: #fef2f2;
  border-color: #fecaca;
}

.desk-legend i.lg-fixed {
  background: #eff6ff;
  border-color: #bfdbfe;
}

.desk-legend i.lg-off {
  background: #f5f5f4;
  border-color: #e7e5e4;
}

.bolt {
  font-size: 10px;
}

.desk-end {
  text-align: center;
  font-size: 11px;
  color: var(--c-text-3);
  letter-spacing: 2px;
  margin-top: 6px;
}

.checkout-bar {
  position: sticky;
  bottom: 16px;
  margin-top: 18px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(8px);
  border: 1px solid var(--c-line);
  border-radius: var(--radius);
  padding: 14px 20px;
  box-shadow: var(--shadow);
}

.co-left {
  display: flex;
  gap: 36px;
}

.co-left span {
  display: block;
  font-size: 11px;
}

.co-left b {
  font-size: 16px;
  color: var(--c-text);
}

.co-seat {
  color: var(--brand) !important;
}
</style>