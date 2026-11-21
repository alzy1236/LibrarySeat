<template>
  <div class="page" v-loading="loading">
    <div class="page-head">
      <div>
        <h1>固定座位</h1>
        <div class="sub">向管理员提交申请，审核通过后生效 · 每月有效出勤不足 {{ requiredDays }} 天将被取消</div>
      </div>
      <el-button :icon="Refresh" @click="load">刷新</el-button>
    </div>

    <!-- 已通过：展示固定座位 -->
    <section v-if="binding" class="bind">
      <div class="bind-seat">
        <div class="bs-label">我的固定座位</div>
        <div class="bs-no mono">{{ binding.seatNo }}</div>
        <div class="bs-room">{{ binding.roomName }}</div>
        <div class="bs-time">审核通过于 {{ fmt(binding.bindTime) }}</div>
      </div>

      <div class="bind-body">
        <div class="stat-row">
          <div class="stat">
            <div class="n">{{ calendar.checkInDays || 0 }}</div>
            <div class="l">本月签到</div>
          </div>
          <div class="stat">
            <div class="n">{{ calendar.checkOutDays || 0 }}</div>
            <div class="l">本月签退</div>
          </div>
          <div class="stat">
            <div class="n accent">{{ calendar.bothDays || 0 }}</div>
            <div class="l">有效出勤</div>
          </div>
          <div class="stat">
            <div class="n muted-n">{{ requiredDays }}</div>
            <div class="l">考核要求</div>
          </div>
        </div>

        <div class="bar-wrap">
          <div class="bar"><i :style="{ width: percent + '%' }"></i></div>
          <div class="bar-tip">
            <span>有效出勤 = 同一天既签到又签退</span>
            <b>{{ calendar.bothDays || 0 }} / {{ requiredDays }}</b>
          </div>
        </div>

        <div class="bind-actions">
          <el-button @click="router.push('/student/home')">去签到 / 签退</el-button>
          <el-button type="danger" plain @click="giveUp">放弃固定座位</el-button>
        </div>
      </div>
    </section>

    <!-- 待审核 -->
    <section v-else-if="pending" class="apply-state">
      <div class="as-icon pending">
        <el-icon><Clock /></el-icon>
      </div>
      <div class="as-body">
        <b>申请审核中</b>
        <p>
          你申请了 <em>{{ pending.roomName }} · {{ pending.seatNo }}</em>，于 {{ fmt(pending.applyTime) }} 提交，等待管理员审核。
        </p>
        <p class="muted">审核通过后座位自动生效；如填写有误可撤回后重新申请。</p>
      </div>
      <el-button plain @click="withdraw">撤回申请</el-button>
    </section>

    <!-- 被拒绝提示 -->
    <section v-else-if="lastRejected" class="apply-state rejected">
      <div class="as-icon"><el-icon><CircleClose /></el-icon></div>
      <div class="as-body">
        <b>上次申请未通过</b>
        <p>{{ lastRejected.roomName }} · {{ lastRejected.seatNo }} —— {{ lastRejected.reviewRemark || '未填写审核意见' }}</p>
        <p class="muted">处理时间 {{ fmt(lastRejected.reviewTime) }}，可重新选择其他座位申请。</p>
      </div>
    </section>

    <!-- 申请 -->
    <template v-if="!binding && !pending">
      <section class="card">
        <div class="card-head">
          <h3><span class="ico"><el-icon><OfficeBuilding /></el-icon></span>选择楼层</h3>
          <span class="desc">目前仅 3 层 / 4 层提供固定座位</span>
        </div>
        <div class="room-tabs">
          <button
            v-for="r in longRooms"
            :key="r.id"
            class="room-tab"
            :class="{ active: activeRoomId === r.id }"
            @click="selectRoom(r.id)"
          >
            <span class="rt-floor">{{ floorNo(r) }}F</span>
            <span class="rt-text">
              <b>{{ r.name }}</b>
              <small>{{ r.location }} · {{ r.capacity }} 个座位</small>
            </span>
          </button>
        </div>
      </section>

      <section class="card">
        <div class="card-head">
          <h3><span class="ico"><el-icon><Grid /></el-icon></span>选择座位</h3>
          <div class="desk-legend">
            <span><i class="lg-free"></i>可申请</span>
            <span><i class="lg-fixed"></i>已被占用</span>
            <span><i class="lg-pending"></i>审核中</span>
            <span><i class="lg-off"></i>停用</span>
          </div>
        </div>

        <div class="desk-room">
          <div v-for="(rowSeats, i) in seatRows" :key="i" class="desk-row">
            <div
              v-for="seat in rowSeats"
              :key="seat.id"
              class="desk"
              :class="[applyDeskClass(seat), { 'desk-picked': picked && picked.id === seat.id }]"
              @click="pickSeat(seat)"
            >
              <div class="desk-no">{{ shortNo(seat.seatNo) }}</div>
              <div class="desk-state">{{ applyStateText(seat) }}</div>
            </div>
          </div>
        </div>

        <div class="apply-bar">
          <div>
            <span class="muted">已选座位</span>
            <b class="co-seat">{{ picked ? picked.seatNo : '未选择' }}</b>
          </div>
          <el-button type="primary" size="large" :disabled="!picked" :loading="applying" @click="apply">
            提交申请（需管理员审核）
          </el-button>
        </div>
      </section>
    </template>

    <!-- 申请记录 -->
    <section class="card">
      <div class="card-head">
        <h3><span class="ico"><el-icon><Document /></el-icon></span>我的申请记录</h3>
      </div>
      <el-table :data="applies" size="small" stripe border empty-text="暂无申请记录">
        <el-table-column prop="roomName" label="楼层" min-width="150" />
        <el-table-column prop="seatNo" label="座位" width="100" />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="applyTagType(row.status)" effect="plain" size="small">{{ row.statusName }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="申请时间" width="170">
          <template #default="{ row }">{{ fmt(row.applyTime) }}</template>
        </el-table-column>
        <el-table-column label="审核时间" width="170">
          <template #default="{ row }">{{ fmt(row.reviewTime) }}</template>
        </el-table-column>
        <el-table-column label="审核意见" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.reviewRemark || row.cancelReason || '—' }}</template>
        </el-table-column>
      </el-table>
    </section>

    <!-- 考核记录 -->
    <section class="card">
      <div class="card-head">
        <h3><span class="ico"><el-icon><DataLine /></el-icon></span>月度考核记录</h3>
        <span class="desc">每月 1 日考核上一月</span>
      </div>
      <el-table :data="assessments" size="small" stripe border empty-text="暂无考核记录">
        <el-table-column prop="assessMonth" label="月份" width="100" />
        <el-table-column prop="roomName" label="楼层" min-width="150" />
        <el-table-column prop="seatNo" label="座位" width="90" />
        <el-table-column prop="checkinDays" label="签到" width="80" align="center" />
        <el-table-column prop="checkoutDays" label="签退" width="80" align="center" />
        <el-table-column prop="requiredDays" label="要求" width="80" align="center" />
        <el-table-column label="结果" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.qualified === 1 ? 'success' : 'danger'" effect="plain" size="small">
              {{ row.qualified === 1 ? '合格' : '不合格' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip />
      </el-table>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { getMyLongTerm, getLongRooms, getLongRoomSeats, applyLongTerm, cancelLongTerm, getMyAssessments, withdrawLongTerm } from '@/api'

const router = useRouter()
const loading = ref(false)
const applying = ref(false)
const binding = ref(null)
const pending = ref(null)
const applies = ref([])
const calendar = ref({})
const requiredDays = ref(20)
const longRooms = ref([])
const activeRoomId = ref(null)
const seats = ref([])
const picked = ref(null)
const assessments = ref([])

const percent = computed(() => {
  const need = requiredDays.value || 20
  return Math.min(100, Math.round(((calendar.value.bothDays || 0) / need) * 100))
})

const lastRejected = computed(() => applies.value.find((a) => a.status === 'REJECTED'))

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

function floorNo(room) {
  const m = (room.location || room.name || '').match(/(\d)\s*层/)
  return m ? m[1] : room.id
}

function shortNo(seatNo) {
  if (!seatNo) return '--'
  const p = seatNo.split('-')
  return p.length > 1 ? p[1] : seatNo
}

function fmt(dt) {
  return dt && dt.length >= 16 ? dt.slice(0, 16) : '—'
}

function applyTagType(status) {
  return { PENDING: 'warning', ACTIVE: 'success', REJECTED: 'danger', CANCELLED: 'info' }[status] || 'info'
}

function applyDeskClass(seat) {
  if (seat.status === 0) return 'desk-off'
  if (seat.seatState === 'LOCKED') return 'desk-fixed'
  return 'desk-free'
}

function applyStateText(seat) {
  if (seat.status === 0) return '停用'
  if (seat.seatState === 'LOCKED') return '已占用'
  return '可申请'
}

function pickSeat(seat) {
  if (seat.status === 0) {
    ElMessage.warning('该座位停用维修中')
    return
  }
  if (seat.seatState === 'LOCKED') {
    ElMessage.warning('该座位已被占用')
    return
  }
  picked.value = seat
}

async function selectRoom(id) {
  activeRoomId.value = id
  picked.value = null
  const res = await getLongRoomSeats(id)
  seats.value = res.data || []
}

async function load() {
  loading.value = true
  try {
    const [mine, list, records] = await Promise.all([
      getMyLongTerm(),
      getLongRooms().catch(() => ({ data: [] })),
      getMyAssessments().catch(() => ({ data: { records: [] } }))
    ])
    binding.value = mine.data?.binding || null
    pending.value = mine.data?.pending || null
    applies.value = mine.data?.applies || []
    calendar.value = mine.data?.calendar || {}
    requiredDays.value = mine.data?.requiredDays || 20
    longRooms.value = list.data || []
    assessments.value = records.data?.records || []
    if (!binding.value && !pending.value && longRooms.value.length) {
      const keep = longRooms.value.some((r) => r.id === activeRoomId.value)
      await selectRoom(keep ? activeRoomId.value : longRooms.value[0].id)
    }
  } finally {
    loading.value = false
  }
}

async function apply() {
  if (!picked.value) return
  applying.value = true
  try {
    await ElMessageBox.confirm(
      `确认申请 ${picked.value.seatNo}？提交后需等待管理员审核，审核通过后座位生效；每月有效出勤不足 ${requiredDays.value} 天将被取消。`,
      '提交申请',
      { type: 'info', confirmButtonText: '提交申请', cancelButtonText: '取消' }
    )
    await applyLongTerm(activeRoomId.value, picked.value.id)
    ElMessage.success('申请已提交，等待管理员审核')
    await load()
  } catch (e) {
    /* 提示由拦截器处理 */
  } finally {
    applying.value = false
  }
}

async function withdraw() {
  try {
    await ElMessageBox.confirm('确定撤回这次申请吗？', '撤回申请', { type: 'warning' })
    await withdrawLongTerm(pending.value.id)
    ElMessage.success('已撤回申请')
    await load()
  } catch (e) {}
}

async function giveUp() {
  try {
    await ElMessageBox.confirm('放弃后将释放固定座位，其他人可以申请，确定放弃吗？', '放弃固定座位', {
      type: 'warning',
      confirmButtonText: '确定放弃'
    })
    await cancelLongTerm(binding.value.id)
    ElMessage.success('已放弃固定座位')
    await load()
  } catch (e) {}
}

onMounted(load)
</script>

<style scoped>
/* 已生效 */
.bind {
  display: grid;
  grid-template-columns: 240px minmax(0, 1fr);
  gap: 22px;
  background: var(--c-surface);
  border: 1px solid var(--c-line);
  border-radius: var(--radius-lg);
  padding: 22px;
}

.bind-seat {
  border-radius: var(--radius-lg);
  background: var(--gray-50);
  border: 1px solid var(--c-line);
  color: var(--gray-900);
  padding: 20px;
  text-align: center;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.bs-label {
  font-size: 11.5px;
  color: var(--gray-400);
}

.bs-no {
  font-size: 28px;
  font-weight: 650;
  letter-spacing: -0.01em;
  margin: 4px 0 2px;
  font-variant-numeric: tabular-nums;
}

.bs-room {
  font-size: 12.5px;
  color: var(--gray-600);
}

.bs-time {
  font-size: 11px;
  color: var(--gray-400);
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid var(--gray-200);
}

.stat-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}

.stat {
  background: var(--c-surface-2);
  border: 1px solid var(--c-line);
  border-radius: 12px;
  padding: 14px;
  text-align: center;
}

.stat .n {
  font-size: 24px;
  font-weight: 800;
  color: var(--brand);
}

.stat .n.accent {
  color: var(--c-accent);
}

.stat .n.muted-n {
  color: var(--c-text-3);
}

.stat .l {
  font-size: 12px;
  color: var(--c-text-3);
  margin-top: 2px;
}

.bar-wrap {
  margin-top: 18px;
}

.bar {
  height: 10px;
  border-radius: 999px;
  background: var(--c-line);
  overflow: hidden;
}

.bar i {
  display: block;
  height: 100%;
  border-radius: 999px;
  background: var(--brand);
  transition: width 0.4s;
}

.bar-tip {
  display: flex;
  justify-content: space-between;
  margin-top: 8px;
  font-size: 12px;
  color: var(--c-text-3);
}

.bind-actions {
  margin-top: 18px;
  display: flex;
  gap: 10px;
}

/* 待审核 / 被拒绝 */
.apply-state {
  display: flex;
  gap: 18px;
  align-items: center;
  background: #f5f3ff;
  border: 1px solid #ddd6fe;
  border-radius: var(--radius-lg);
  padding: 22px;
}

.as-icon {
  width: 54px;
  height: 54px;
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 26px;
  background: #ede9fe;
  color: #6d28d9;
  flex-shrink: 0;
}

.as-body {
  flex: 1;
}

.as-body b {
  font-size: 16px;
  color: #4c1d95;
}

.as-body p {
  margin: 6px 0 0;
  font-size: 13px;
  color: #5b21b6;
  line-height: 1.6;
}

.as-body em {
  font-style: normal;
  font-weight: 700;
}

.apply-state.rejected {
  background: #fef2f2;
  border-color: #fecaca;
}

.apply-state.rejected .as-icon {
  background: #fee2e2;
  color: #b91c1c;
}

.apply-state.rejected .as-body b {
  color: #991b1b;
}

.apply-state.rejected .as-body p {
  color: #b91c1c;
}

/* 楼层选择 */
.room-tabs {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 12px;
}

.room-tab {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 16px;
  border-radius: var(--radius);
  border: 1.5px solid var(--c-line);
  background: var(--c-surface-2);
  cursor: pointer;
  text-align: left;
  font-family: inherit;
  transition: all 0.15s;
}

.room-tab:hover {
  border-color: var(--brand);
}

.room-tab.active {
  border-color: var(--brand);
  background: var(--brand-soft);
  box-shadow: 0 0 0 3px rgba(15, 118, 110, 0.1);
}

.rt-floor {
  font-size: 20px;
  font-weight: 800;
  color: var(--brand);
  min-width: 44px;
}

.rt-text b {
  display: block;
  font-size: 14px;
}

.rt-text small {
  font-size: 11.5px;
  color: var(--c-text-3);
}

.apply-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-top: 18px;
  padding-top: 16px;
  border-top: 1px dashed var(--c-line);
}

.co-seat {
  color: var(--brand);
  font-size: 16px;
  margin-left: 8px;
}

.desk-legend i.lg-free {
  background: #ecfdf5;
  border-color: #a7f3d0;
}

.desk-legend i.lg-fixed {
  background: #eff6ff;
  border-color: #bfdbfe;
}

.desk-legend i.lg-pending {
  background: #f5f3ff;
  border-color: #ddd6fe;
}

.desk-legend i.lg-off {
  background: #f5f5f4;
  border-color: #e7e5e4;
}

@media (max-width: 1000px) {
  .bind {
    grid-template-columns: 1fr;
  }
  .stat-row {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>