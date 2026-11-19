<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1>预约记录管理</h1>
        <div class="sub">查看全部选座 / 签到 / 签退记录，可代签退释放座位 · 短期自习室超时清场记录可筛选</div>
      </div>
      <el-button :icon="Refresh" @click="load">刷新</el-button>
    </div>

    <div class="card">
      <div class="toolbar">
        <el-input v-model="query.keyword" placeholder="学生姓名 / 学号 / 座位号 / 自习室" clearable style="width: 240px" :prefix-icon="Search" @keyup.enter="search" />
        <el-select v-model="query.roomId" placeholder="全部自习室" clearable style="width: 190px">
          <el-option v-for="r in rooms" :key="r.id" :label="r.name" :value="r.id" />
        </el-select>
        <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 130px">
          <el-option label="待签到" value="WAITING" />
          <el-option label="使用中" value="SEATED" />
          <el-option label="已离开" value="LEFT" />
          <el-option label="已取消" value="CANCELLED" />
          <el-option label="超时失效" value="TIMEOUT" />
        </el-select>
        <el-date-picker v-model="dateRange" type="daterange" range-separator="至" start-placeholder="开始日期" end-placeholder="结束日期" value-format="YYYY-MM-DD" style="width: 260px" />
        <el-checkbox v-model="onlyOverstay" label="仅看超时未清场" border @change="search" />
        <el-button type="primary" :icon="Search" @click="search">查询</el-button>
        <el-button :icon="RefreshLeft" @click="reset">重置</el-button>
      </div>

      <el-table v-loading="loading" :data="records" stripe border size="small" empty-text="暂无预约记录">
        <el-table-column prop="reserveDate" label="日期" width="110" />
        <el-table-column prop="userName" label="学生" width="90" />
        <el-table-column prop="studentNo" label="学号" width="110" />
        <el-table-column prop="college" label="学院" min-width="120" show-overflow-tooltip />
        <el-table-column label="自习室" min-width="190">
          <template #default="{ row }">
            <el-tag :class="row.roomType === 'LONG' ? 'tag-long' : 'tag-short'" effect="plain" size="small">
              {{ row.roomType === 'LONG' ? '长期' : '短期' }}
            </el-tag>
            <span style="margin-left: 6px">{{ row.roomName }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="seatNo" label="座位" width="90" />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" effect="dark" size="small">{{ row.statusName }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="签到时间" width="160">
          <template #default="{ row }">
            <span v-if="row.checkInTime">{{ row.checkInTime.slice(11) }}</span>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="签退时间" width="160">
          <template #default="{ row }">
            <span v-if="row.checkOutTime">{{ row.checkOutTime.slice(11) }}</span>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="备注" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tag v-if="row.overstay === 1" type="danger" size="small" effect="plain">超时未清场</el-tag>
            <span v-else class="muted">{{ row.cancelReason || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right" align="center">
          <template #default="{ row }">
            <el-button v-if="row.status === 'SEATED'" link type="warning" @click="release(row)">代签退</el-button>
            <el-button v-if="row.status === 'WAITING'" link type="danger" @click="cancel(row)">取消</el-button>
            <span v-if="row.status !== 'SEATED' && row.status !== 'WAITING'" class="muted">—</span>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="query.pageNum"
          v-model:page-size="query.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @current-change="load"
          @size-change="load"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, RefreshLeft } from '@element-plus/icons-vue'
import { adminReservations, adminRooms, releaseReservation, adminCancelReservation } from '@/api'

const loading = ref(false)
const records = ref([])
const total = ref(0)
const rooms = ref([])
const dateRange = ref(null)
const onlyOverstay = ref(false)
const query = reactive({ pageNum: 1, pageSize: 10, keyword: '', roomId: null, status: '' })

function statusType(status) {
  return { WAITING: 'warning', SEATED: 'success', LEFT: 'info', CANCELLED: 'info', TIMEOUT: 'danger' }[status] || 'info'
}

async function load() {
  loading.value = true
  try {
    const res = await adminReservations({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      keyword: query.keyword || undefined,
      roomId: query.roomId || undefined,
      status: query.status || undefined,
      startDate: dateRange.value?.[0],
      endDate: dateRange.value?.[1],
      overstay: onlyOverstay.value ? 1 : undefined
    })
    records.value = res.data.records || []
    total.value = res.data.total || 0
  } finally {
    loading.value = false
  }
}

function search() {
  query.pageNum = 1
  load()
}

function reset() {
  query.keyword = ''
  query.roomId = null
  query.status = ''
  dateRange.value = null
  onlyOverstay.value = false
  search()
}

async function release(row) {
  try {
    await ElMessageBox.confirm(
      `确认代 ${row.userName} 对座位 ${row.seatNo} 执行签退并释放吗？`,
      '代签退确认',
      { type: 'warning', confirmButtonText: '确认签退' }
    )
    await releaseReservation(row.id, '管理员代签退')
    ElMessage.success('已代签退并释放座位')
    load()
  } catch (e) {
    /* ignore */
  }
}

async function cancel(row) {
  try {
    await ElMessageBox.confirm(`确认取消 ${row.userName} 的预约吗？`, '取消预约', { type: 'warning' })
    await adminCancelReservation(row.id)
    ElMessage.success('已取消')
    load()
  } catch (e) {
    /* ignore */
  }
}

onMounted(async () => {
  load()
  try {
    const res = await adminRooms()
    rooms.value = res.data || []
  } catch (e) {
    /* ignore */
  }
})
</script>

