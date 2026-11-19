<template>
  <div class="page" v-loading="loading">
    <div class="page-head">
      <div>
        <h1>座位管理</h1>
        <div class="sub">
          {{ room?.name || '—' }} · 共 {{ seats.length }} 个座位 · 今日预约 {{ reservedCount }} · 在座 {{ seatedCount }}
        </div>
      </div>
      <div class="head-actions">
        <el-button :icon="Back" @click="router.push('/admin/rooms')">返回楼层</el-button>
        <el-button :icon="Refresh" @click="load">刷新</el-button>
        <el-button type="primary" :icon="Plus" @click="openDialog()">新增座位</el-button>
      </div>
    </div>

    <div class="card">
      <div class="card-head">
        <h3><span class="ico"><el-icon><Grid /></el-icon></span>座位图（点击座位可编辑 / 停用）</h3>
        <div class="desk-legend">
          <span><i class="lg-free"></i>空闲</span>
          <span><i class="lg-wait"></i>待签到</span>
          <span><i class="lg-taken"></i>使用中</span>
          <span><i class="lg-fixed"></i>固定座位</span>
          <span><i class="lg-off"></i>停用</span>
        </div>
      </div>

      <div class="desk-room">
        <div class="desk-board">讲 台</div>
        <div v-for="(rowSeats, i) in seatRows" :key="i" class="desk-row">
          <el-tooltip v-for="seat in rowSeats" :key="seat.id" placement="top" :content="tooltip(seat)" :show-after="100">
            <div class="desk" :class="deskClass(seat)" @click="openDialog(seat)">
              <div class="desk-no">{{ shortNo(seat.seatNo) }}</div>
              <div class="desk-state">
                <el-icon v-if="seat.hasPower === 1"><Lightning /></el-icon>
                {{ stateText(seat) }}
              </div>
            </div>
          </el-tooltip>
        </div>
      </div>
    </div>

    <div class="card">
      <div class="card-head">
        <h3><span class="ico"><el-icon><List /></el-icon></span>座位列表</h3>
      </div>
      <el-table :data="seats" size="small" stripe border max-height="420" empty-text="暂无座位">
        <el-table-column prop="seatNo" label="座位号" width="110" />
        <el-table-column prop="rowNo" label="行" width="70" align="center" />
        <el-table-column prop="colNo" label="列" width="70" align="center" />
        <el-table-column label="电源" width="80" align="center">
          <template #default="{ row }">
            <el-icon v-if="row.hasPower === 1" color="#f59e0b"><Lightning /></el-icon>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="今日状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="stateTagType(row)" size="small" effect="plain">{{ stateText(row) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="使用人" width="120">
          <template #default="{ row }">{{ row.occupantName || row.longTermOwner || '—' }}</template>
        </el-table-column>
        <el-table-column label="启用" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ row.status === 1 ? '可用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
            <el-button link type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑座位' : '新增座位'" width="460px" destroy-on-close>
      <el-form label-width="90px">
        <el-form-item label="座位号">
          <el-input v-model="form.seatNo" placeholder="留空自动生成" />
        </el-form-item>
        <el-form-item label="行 / 列">
          <el-input-number v-model="form.rowNo" :min="1" :max="50" />
          <el-input-number v-model="form.colNo" :min="1" :max="20" style="margin-left: 10px" />
        </el-form-item>
        <el-form-item label="电源">
          <el-switch v-model="form.hasPower" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="可用" inactive-text="停用（维修）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Back, Refresh, Plus } from '@element-plus/icons-vue'
import { adminSeats, adminRooms, addSeat, updateSeat, deleteSeat } from '@/api'

const route = useRoute()
const router = useRouter()
const roomId = computed(() => Number(route.params.id))
const loading = ref(false)
const saving = ref(false)
const seats = ref([])
const room = ref(null)
const dialogVisible = ref(false)

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

const reservedCount = computed(() => seats.value.filter((s) => ['RESERVED', 'SEATED', 'OVERDUE'].includes(s.seatState)).length)
const seatedCount = computed(() => seats.value.filter((s) => s.seatState === 'SEATED').length)

const form = reactive({ id: null, roomId: null, seatNo: '', rowNo: 1, colNo: 1, hasPower: 0, status: 1 })

function shortNo(seatNo) {
  if (!seatNo) return '--'
  const p = seatNo.split('-')
  return p.length > 1 ? p[1] : seatNo
}

function deskClass(seat) {
  if (seat.status === 0) return 'desk-off'
  const map = { AVAILABLE: 'desk-free', RESERVED: 'desk-wait', SEATED: 'desk-taken', LOCKED: 'desk-fixed', OVERDUE: 'desk-overdue' }
  return map[seat.seatState] || 'desk-free'
}

function stateText(seat) {
  if (seat.status === 0) return '停用'
  const map = { AVAILABLE: '空闲', RESERVED: '待签到', SEATED: '使用中', LOCKED: '固定', OVERDUE: '超时' }
  return map[seat.seatState] || '空闲'
}

function stateTagType(seat) {
  if (seat.status === 0) return 'info'
  return { AVAILABLE: 'success', RESERVED: 'warning', SEATED: 'danger', LOCKED: 'primary', OVERDUE: 'warning' }[seat.seatState] || 'info'
}

function tooltip(seat) {
  const parts = [seat.seatNo, `第 ${seat.rowNo} 排第 ${seat.colNo} 列`, seat.hasPower ? '带电源' : '无电源', stateText(seat)]
  if (seat.occupantName) parts.push(`使用人：${seat.occupantName}`)
  if (seat.longTermOwner) parts.push(`固定人：${seat.longTermOwner}`)
  return parts.join(' · ')
}

async function load() {
  loading.value = true
  try {
    const [s, rooms] = await Promise.all([adminSeats(roomId.value), adminRooms()])
    seats.value = s.data || []
    room.value = (rooms.data || []).find((r) => r.id === roomId.value) || null
  } finally {
    loading.value = false
  }
}

function openDialog(row) {
  Object.assign(form, {
    id: row?.id || null,
    roomId: roomId.value,
    seatNo: row?.seatNo || '',
    rowNo: row?.rowNo || 1,
    colNo: row?.colNo || 1,
    hasPower: row?.hasPower ?? 0,
    status: row?.status ?? 1
  })
  dialogVisible.value = true
}

async function save() {
  saving.value = true
  try {
    if (form.id) {
      await updateSeat({ ...form })
      ElMessage.success('座位已更新')
    } else {
      await addSeat({ ...form })
      ElMessage.success('座位已新增')
    }
    dialogVisible.value = false
    load()
  } catch (e) {
  } finally {
    saving.value = false
  }
}

async function remove(row) {
  try {
    await ElMessageBox.confirm(`确定删除座位 ${row.seatNo} 吗？`, '删除确认', { type: 'warning' })
    await deleteSeat(row.id)
    ElMessage.success('已删除')
    load()
  } catch (e) {}
}

onMounted(load)
</script>

<style scoped>
.head-actions {
  display: flex;
  gap: 10px;
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
</style>