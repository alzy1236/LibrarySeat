<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1>楼层与座位</h1>
        <div class="sub">自习楼共 1—5 层 · 维护楼层类型、座位数量与开放状态</div>
      </div>
      <el-button type="primary" :icon="Plus" @click="openDialog()">新增楼层</el-button>
    </div>

    <!-- 顶部汇总条 -->
    <div class="summary">
      <div class="sum-item">
        <span>楼层</span><b>{{ rooms.length }}</b>
      </div>
      <div class="sum-item">
        <span>总座位</span><b>{{ sum('capacity') }}</b>
      </div>
      <div class="sum-item">
        <span>今日预约</span><b class="c-indigo">{{ sum('reservedCount') }}</b>
      </div>
      <div class="sum-item">
        <span>在座</span><b class="c-emerald">{{ sum('seatedCount') }}</b>
      </div>
      <div class="sum-item">
        <span>剩余</span><b class="c-amber">{{ sum('availableCount') }}</b>
      </div>
      <div class="sum-filter">
        <el-input v-model="keyword" placeholder="搜索楼层" clearable :prefix-icon="Search" style="width: 190px" @keyup.enter="load" @clear="load" />
        <el-select v-model="roomType" placeholder="全部类型" clearable style="width: 132px" @change="load">
          <el-option label="固定座位层" value="LONG" />
          <el-option label="普通座位层" value="SHORT" />
        </el-select>
        <el-select v-model="status" placeholder="全部状态" clearable style="width: 118px" @change="load">
          <el-option label="开放" :value="1" />
          <el-option label="停用" :value="0" />
        </el-select>
        <el-button :icon="Refresh" @click="reset">重置</el-button>
      </div>
    </div>

    <div v-loading="loading" class="floor-grid">
      <div v-for="room in rooms" :key="room.id" class="floor-card" :class="{ off: room.status === 0 }">
        <div class="fc-head">
          <div class="fc-floor">{{ floorNo(room) }}<small>F</small></div>
          <div class="fc-title">
            <b>{{ room.name }}</b>
            <span class="muted">{{ room.location }}</span>
          </div>
        </div>

        <div class="fc-badge">
          <span class="pill" :class="room.roomType === 'LONG' ? 'pill-long' : 'pill-short'">
            {{ room.roomTypeName === '长期' ? '固定座位层' : '普通座位层' }}
          </span>
          <span class="pill" :class="room.status === 1 ? 'pill-open' : 'pill-off'">
            {{ room.status === 1 ? '开放中' : '已停用' }}
          </span>
        </div>

        <div class="fc-stats">
          <div><b>{{ room.capacity }}</b><span>座位</span></div>
          <div><b>{{ room.reservedCount }}</b><span>预约</span></div>
          <div><b>{{ room.seatedCount }}</b><span>在座</span></div>
          <div><b>{{ room.availableCount }}</b><span>剩余</span></div>
        </div>

        <div class="fc-progress">
          <div class="fp-bar"><i :style="{ width: rate(room) + '%' }"></i></div>
          <span class="fp-rate">{{ rate(room) }}%</span>
        </div>

        <div class="fc-foot">
          <span class="muted"><el-icon><Clock /></el-icon>{{ room.openStart }} - {{ room.openEnd }}</span>
          <span class="muted">{{ seatLayout(room) }}</span>
        </div>

        <div class="fc-actions">
          <el-button size="small" type="primary" plain @click="router.push(`/admin/rooms/${room.id}/seats`)">座位管理</el-button>
          <el-button size="small" @click="openDialog(room)">编辑</el-button>
          <el-button size="small" @click="openGenerate(room)">生成座位</el-button>
          <el-button size="small" type="danger" plain @click="remove(room)">删除</el-button>
        </div>
      </div>

      <div v-if="!loading && rooms.length === 0" class="empty-card">暂无楼层数据</div>
    </div>

    <!-- 新增 / 编辑 -->
    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑楼层' : '新增楼层'" width="560px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="96px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="如：三层 · 固定座位自习室" />
        </el-form-item>
        <el-form-item label="类型" prop="roomType">
          <el-radio-group v-model="form.roomType">
            <el-radio-button label="LONG">固定座位层</el-radio-button>
            <el-radio-button label="SHORT">普通座位层</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="位置">
          <el-input v-model="form.location" placeholder="如：自习楼 3 层" />
        </el-form-item>
        <el-form-item label="座位数" prop="capacity">
          <el-input-number v-model="form.capacity" :min="1" :max="200" />
          <span class="muted" style="margin-left: 10px">每行 6 个自动排版</span>
        </el-form-item>
        <el-form-item v-if="form.id" label="重建座位">
          <el-switch v-model="rebuildSeats" active-text="按新座位数清空重建" />
        </el-form-item>
        <el-form-item label="开放时间">
          <el-time-select v-model="form.openStart" start="00:00" step="00:30" end="23:30" style="width: 130px" />
          <span style="margin: 0 8px">-</span>
          <el-time-select v-model="form.openEnd" start="00:30" step="00:30" end="23:59" style="width: 130px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :label="1">开放</el-radio>
            <el-radio :label="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="form.description" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="genVisible" title="生成座位" width="420px">
      <div class="muted" style="margin-bottom: 12px">楼层：<b>{{ genRoom?.name }}</b>（当前 {{ genRoom?.capacity }} 个座位）</div>
      <el-form label-width="90px">
        <el-form-item label="新增数量">
          <el-input-number v-model="genCount" :min="1" :max="200" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="genVisible = false">取消</el-button>
        <el-button type="primary" @click="doGenerate">确定生成</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search, Refresh } from '@element-plus/icons-vue'
import { adminRooms, addRoom, updateRoom, deleteRoom, generateSeats } from '@/api'

const router = useRouter()
const loading = ref(false)
const saving = ref(false)
const rooms = ref([])
const keyword = ref('')
const roomType = ref('')
const status = ref(null)
const dialogVisible = ref(false)
const formRef = ref(null)
const rebuildSeats = ref(false)
const genVisible = ref(false)
const genRoom = ref(null)
const genCount = ref(6)

const defaultForm = () => ({
  id: null,
  name: '',
  roomType: 'SHORT',
  location: '',
  capacity: 24,
  openStart: '00:00',
  openEnd: '23:59',
  description: '',
  status: 1
})
const form = reactive(defaultForm())

const formRules = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  roomType: [{ required: true, message: '请选择类型', trigger: 'change' }],
  capacity: [{ required: true, message: '请输入座位数', trigger: 'blur' }]
}

function floorNo(room) {
  const m = (room.location || room.name || '').match(/(\d)\s*层/)
  return m ? m[1] : room.id
}

function rate(room) {
  if (!room.capacity) return 0
  return Math.round((room.reservedCount / room.capacity) * 100)
}

function seatLayout(room) {
  const rows = Math.ceil((room.capacity || 0) / 6)
  return `${rows} 排 × 6 列`
}

function sum(field) {
  return rooms.value.reduce((s, r) => s + Number(r[field] || 0), 0)
}

async function load() {
  loading.value = true
  try {
    const res = await adminRooms({
      keyword: keyword.value || undefined,
      roomType: roomType.value || undefined,
      status: status.value === null || status.value === '' ? undefined : status.value
    })
    rooms.value = (res.data || []).sort((a, b) => floorNo(a) - floorNo(b))
  } finally {
    loading.value = false
  }
}

function reset() {
  keyword.value = ''
  roomType.value = ''
  status.value = null
  load()
}

function openDialog(row) {
  Object.assign(form, defaultForm(), row ? { ...row } : {})
  rebuildSeats.value = false
  dialogVisible.value = true
}

async function save() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    if (form.id) {
      await updateRoom({ ...form, rebuildSeats: rebuildSeats.value ? 1 : 0 })
      ElMessage.success('楼层已更新')
    } else {
      await addRoom(form)
      ElMessage.success('楼层已新增，座位已自动生成')
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
    await ElMessageBox.confirm(`确定删除「${row.name}」及其全部座位吗？`, '删除确认', { type: 'warning' })
    await deleteRoom(row.id)
    ElMessage.success('已删除')
    load()
  } catch (e) {}
}

function openGenerate(row) {
  genRoom.value = row
  genCount.value = 6
  genVisible.value = true
}

async function doGenerate() {
  try {
    const res = await generateSeats(genRoom.value.id, genCount.value)
    ElMessage.success(`已生成 ${res.data} 个座位`)
    genVisible.value = false
    load()
  } catch (e) {}
}

onMounted(load)
</script>

<style scoped>
/* 顶部汇总条 */
.summary {
  display: flex;
  align-items: center;
  gap: 22px;
  background: var(--c-surface);
  border: 1px solid var(--c-line);
  border-radius: var(--radius);
  padding: 12px 18px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.sum-item {
  display: flex;
  align-items: baseline;
  gap: 7px;
}

.sum-item span {
  font-size: 12px;
  color: var(--c-text-3);
}

.sum-item b {
  font-size: 19px;
  font-weight: 800;
  font-variant-numeric: tabular-nums;
}

.c-indigo {
  color: var(--brand);
}

.c-emerald {
  color: var(--c-success);
}

.c-amber {
  color: var(--c-accent);
}

.sum-filter {
  margin-left: auto;
  display: flex;
  gap: 8px;
  align-items: center;
}

/* 楼层卡片：自适应铺满 */
.floor-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 16px;
  min-height: 200px;
}

.floor-card {
  background: var(--c-surface);
  border: 1px solid var(--c-line);
  border-radius: var(--radius);
  padding: 16px 18px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  position: relative;
  transition: box-shadow 0.16s, transform 0.16s;
}

.floor-card:hover {
  box-shadow: var(--shadow);
  transform: translateY(-2px);
}

.floor-card.off {
  opacity: 0.66;
}

.fc-head {
  display: flex;
  align-items: center;
  gap: 12px;
}

.fc-floor {
  width: 46px;
  height: 46px;
  border-radius: 13px;
  background: var(--brand);
  color: #fff;
  font-size: 20px;
  font-weight: 800;
  display: flex;
  align-items: baseline;
  justify-content: center;
  padding-top: 8px;
  flex-shrink: 0;
}

.fc-floor small {
  font-size: 10px;
  margin-left: 1px;
}

.fc-title {
  min-width: 0;
}

.fc-title b {
  display: block;
  font-size: 14.5px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.fc-title span {
  font-size: 11.5px;
}

.fc-badge {
  display: flex;
  gap: 6px;
}

.pill-open {
  background: var(--c-success-100);
  color: #047857;
}

.pill-off {
  background: var(--c-surface-2);
  color: var(--c-text-3);
}

.fc-stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
}

.fc-stats div {
  background: var(--c-surface-2);
  border-radius: 10px;
  padding: 8px 0;
  text-align: center;
}

.fc-stats b {
  display: block;
  font-size: 17px;
  font-weight: 800;
  font-variant-numeric: tabular-nums;
}

.fc-stats span {
  font-size: 11px;
  color: var(--c-text-3);
}

.fc-progress {
  display: flex;
  align-items: center;
  gap: 10px;
}

.fp-bar {
  flex: 1;
  height: 6px;
  border-radius: 999px;
  background: var(--c-line);
  overflow: hidden;
}

.fp-bar i {
  display: block;
  height: 100%;
  border-radius: 999px;
  background: var(--brand);
}

.fp-rate {
  font-size: 11.5px;
  font-weight: 700;
  color: var(--c-text-2);
  min-width: 34px;
  text-align: right;
}

.fc-foot {
  display: flex;
  justify-content: space-between;
  font-size: 11.5px;
}

.fc-foot .el-icon {
  vertical-align: -2px;
  margin-right: 3px;
}

.fc-actions {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  padding-top: 10px;
  border-top: 1px dashed var(--c-line);
}

.empty-card {
  grid-column: 1 / -1;
  text-align: center;
  color: var(--c-text-3);
  font-size: 13px;
  padding: 50px 0;
  background: var(--c-surface);
  border: 1px dashed var(--c-line-strong);
  border-radius: var(--radius);
}

@media (max-width: 900px) {
  .sum-filter {
    margin-left: 0;
    width: 100%;
    flex-wrap: wrap;
  }
}
</style>