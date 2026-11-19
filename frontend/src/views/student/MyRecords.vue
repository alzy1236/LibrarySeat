<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1>我的选座记录</h1>
        <div class="sub">全部预约、签到、签退与取消记录（每天可预约一个座位）</div>
      </div>
    </div>

    <div class="card">
      <div class="toolbar">
        <el-select v-model="query.status" placeholder="预约状态" clearable style="width: 150px">
          <el-option label="待签到" value="WAITING" />
          <el-option label="使用中" value="SEATED" />
          <el-option label="已离开" value="LEFT" />
          <el-option label="已取消" value="CANCELLED" />
          <el-option label="超时失效" value="TIMEOUT" />
        </el-select>
        <el-select v-model="query.roomId" placeholder="自习室" clearable style="width: 200px">
          <el-option v-for="r in rooms" :key="r.id" :label="r.name" :value="r.id" />
        </el-select>
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          value-format="YYYY-MM-DD"
          style="width: 260px"
        />
        <el-button type="primary" :icon="Search" @click="search">查询</el-button>
        <el-button :icon="RefreshLeft" @click="reset">重置</el-button>
      </div>

      <el-table v-loading="loading" :data="records" stripe border size="small" empty-text="暂无选座记录">
        <el-table-column prop="reserveDate" label="预约日期" width="120" />
        <el-table-column label="自习室" min-width="180">
          <template #default="{ row }">
            <el-tag :class="row.roomType === 'LONG' ? 'tag-long' : 'tag-short'" effect="plain" size="small">
              {{ row.roomType === 'LONG' ? '长期' : '短期' }}
            </el-tag>
            <span style="margin-left: 6px">{{ row.roomName }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="seatNo" label="座位号" width="100" />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" effect="dark" size="small">{{ row.statusName }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="预约时间" width="170">
          <template #default="{ row }">{{ row.createTime || '—' }}</template>
        </el-table-column>
        <el-table-column label="签到时间" width="170">
          <template #default="{ row }">{{ row.checkInTime || '—' }}</template>
        </el-table-column>
        <el-table-column label="签退时间" width="170">
          <template #default="{ row }">{{ row.checkOutTime || '—' }}</template>
        </el-table-column>
        <el-table-column label="备注" min-width="160">
          <template #default="{ row }">
            <span v-if="row.overstay === 1" class="text-danger">超时未清场</span>
            <span v-else>{{ row.cancelReason || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="110" fixed="right" align="center">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'WAITING'"
              link
              type="danger"
              @click="cancel(row)"
            >
              取消预约
            </el-button>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="query.pageNum"
          v-model:page-size="query.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
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
import { Search, RefreshLeft } from '@element-plus/icons-vue'
import { getMyReservations, studentRooms, cancelReserve } from '@/api'

const loading = ref(false)
const records = ref([])
const total = ref(0)
const rooms = ref([])
const dateRange = ref(null)
const query = reactive({ pageNum: 1, pageSize: 10, status: '', roomId: null })

function statusType(status) {
  return { WAITING: 'warning', SEATED: 'success', LEFT: 'info', CANCELLED: 'info', TIMEOUT: 'danger' }[status] || 'info'
}

async function load() {
  loading.value = true
  try {
    const params = {
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      status: query.status || undefined,
      roomId: query.roomId || undefined,
      startDate: dateRange.value?.[0],
      endDate: dateRange.value?.[1]
    }
    const res = await getMyReservations(params)
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
  query.status = ''
  query.roomId = null
  dateRange.value = null
  query.pageNum = 1
  load()
}

async function cancel(row) {
  try {
    await ElMessageBox.confirm('确定取消该预约吗？', '取消预约', { type: 'warning' })
    await cancelReserve(row.id)
    ElMessage.success('已取消预约')
    load()
  } catch (e) {
    /* ignore */
  }
}

onMounted(async () => {
  load()
  try {
    const res = await studentRooms()
    rooms.value = res.data || []
  } catch (e) {
    /* ignore */
  }
})
</script>

