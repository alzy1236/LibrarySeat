<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1>申请审核</h1>
        <div class="sub">学生提交的固定座位申请，审核通过后座位立即生效</div>
      </div>
      <el-button :icon="Refresh" @click="load">刷新</el-button>
    </div>

    <div class="summary-row">
      <div class="sum-card">
        <span>待审核</span>
        <b>{{ pendings.length }}</b>
      </div>
      <div class="sum-card">
        <span>当前生效固定座位</span>
        <b>{{ activeCount }}</b>
      </div>
      <div class="sum-card">
        <span>楼层</span>
        <b>{{ rooms.length }}</b>
      </div>
    </div>

    <div class="card">
      <div class="card-head">
        <h3><span class="ico"><el-icon><Checked /></el-icon></span>待审核申请</h3>
        <div class="toolbar" style="margin: 0">
          <el-select v-model="roomId" placeholder="全部楼层" clearable style="width: 170px" @change="load">
            <el-option v-for="r in rooms" :key="r.id" :label="r.name" :value="r.id" />
          </el-select>
          <el-input v-model="keyword" placeholder="学生 / 学号 / 座位" clearable style="width: 200px" @keyup.enter="load" @clear="load" />
          <el-button type="primary" @click="load">查询</el-button>
        </div>
      </div>

      <div v-if="pendings.length === 0 && !loading" class="empty-state">
        <el-icon><CircleCheck /></el-icon>
        <p>暂无待审核的申请</p>
      </div>

      <div v-loading="loading" class="apply-list">
        <div v-for="item in pendings" :key="item.id" class="apply-item">
          <div class="ai-avatar">{{ (item.userName || '学').slice(0, 1) }}</div>
          <div class="ai-main">
            <div class="ai-name">
              {{ item.userName }}
              <span class="muted">{{ item.studentNo }}</span>
              <span class="pill pill-long">{{ item.roomName }}</span>
              <span class="pill pill-seat">{{ item.seatNo }}</span>
            </div>
            <div class="ai-meta">
              {{ item.college || '—' }} · 申请于 {{ item.applyTime || '—' }}
              <template v-if="item.phone"> · {{ item.phone }}</template>
            </div>
          </div>
          <div class="ai-actions">
            <el-button type="danger" plain @click="reject(item)">拒绝</el-button>
            <el-button type="primary" @click="approve(item)">通过</el-button>
          </div>
        </div>
      </div>
    </div>

    <div class="card">
      <div class="card-head">
        <h3><span class="ico"><el-icon><Collection /></el-icon></span>已生效的固定座位</h3>
        <span class="desc">共 {{ actives.length }} 人 · 本月签到 / 签退天数</span>
      </div>
      <el-table :data="actives" v-loading="loading" size="small" stripe border empty-text="暂无生效的固定座位">
        <el-table-column label="学生" width="130">
          <template #default="{ row }">
            <b>{{ row.userName }}</b>
            <div class="muted" style="font-size: 11px">{{ row.studentNo }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="roomName" label="楼层" min-width="150" />
        <el-table-column prop="seatNo" label="座位" width="90" />
        <el-table-column label="本月出勤" width="140">
          <template #default="{ row }">
            签到 {{ row.monthCheckInDays ?? 0 }} 天 / 签退 {{ row.monthCheckOutDays ?? 0 }} 天
          </template>
        </el-table-column>
        <el-table-column label="审核通过时间" width="170">
          <template #default="{ row }">{{ row.bindTime || '—' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="110" align="center">
          <template #default="{ row }">
            <el-button link type="danger" @click="release(row)">释放座位</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { adminPendingLongTerm, adminLongTerm, reviewLongTerm, releaseLongTerm, adminRooms } from '@/api'
import { useBadgeStore } from '@/stores/badge'

const badge = useBadgeStore()
const loading = ref(false)
const pendings = ref([])
const actives = ref([])
const activeCount = ref(0)
const rooms = ref([])
const roomId = ref(null)
const keyword = ref('')

async function load(syncBadge = true) {
  loading.value = true
  try {
    const [p, a] = await Promise.all([
      adminPendingLongTerm({ roomId: roomId.value || undefined, keyword: keyword.value || undefined }),
      adminLongTerm({})
    ])
    pendings.value = p.data || []
    actives.value = a.data || []
    activeCount.value = actives.value.length
    // 无筛选条件时，用列表长度精确同步侧边栏红点
    if (syncBadge && !roomId.value && !keyword.value) {
      badge.setPending(pendings.value.length)
    }
  } finally {
    loading.value = false
  }
}

/** 审核完成：本地立即移除该条 + 红点减一，再后台刷新数据 */
function afterReviewed(item) {
  pendings.value = pendings.value.filter((x) => x.id !== item.id)
  badge.decPending(1)
  load(false)
  badge.refresh()
}

async function approve(item) {
  try {
    await ElMessageBox.confirm(
      `确认通过 ${item.userName}（${item.studentNo}）对 ${item.roomName} ${item.seatNo} 的固定座位申请？通过后立即生效。`,
      '通过申请',
      { type: 'success', confirmButtonText: '通过', cancelButtonText: '取消' }
    )
    await reviewLongTerm(item.id, true, '审核通过')
    ElMessage.success('已通过该申请，座位已生效')
    afterReviewed(item)
  } catch (e) {}
}

async function reject(item) {
  try {
    const { value } = await ElMessageBox.prompt('请填写拒绝原因（学生可以看到）', '拒绝申请', {
      type: 'warning',
      confirmButtonText: '确认拒绝',
      cancelButtonText: '取消',
      inputPlaceholder: '例如：本月名额已满 / 资料不完整',
      inputValue: '本月名额已满，请下月再申请'
    })
    await reviewLongTerm(item.id, false, value || '管理员未通过审核')
    ElMessage.success('已拒绝该申请')
    afterReviewed(item)
  } catch (e) {}
}

async function release(row) {
  try {
    await ElMessageBox.confirm(`确定释放 ${row.userName} 的固定座位 ${row.seatNo} 吗？`, '释放座位', { type: 'warning' })
    await releaseLongTerm(row.id, '管理员释放')
    ElMessage.success('已释放')
    load()
  } catch (e) {}
}

onMounted(async () => {
  load()
  try {
    const res = await adminRooms({ roomType: 'LONG' })
    rooms.value = res.data || []
  } catch (e) {}
})
</script>

<style scoped>
.summary-row {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 18px;
}

.sum-card {
  background: var(--c-surface);
  border: 1px solid var(--c-line);
  border-radius: var(--radius);
  padding: 16px 20px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.sum-card span {
  font-size: 13px;
  color: var(--c-text-3);
}

.sum-card b {
  font-size: 26px;
  font-weight: 800;
  color: var(--brand);
}

.empty-state {
  padding: 26px 0;
  text-align: center;
  color: var(--c-text-3);
}

.empty-state .el-icon {
  font-size: 34px;
  color: var(--brand);
}

.empty-state p {
  margin: 8px 0 0;
  font-size: 13px;
}

.apply-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 40px;
}

.apply-item {
  display: flex;
  align-items: center;
  gap: 16px;
  border: 1px solid var(--c-line);
  border-left: 4px solid var(--c-accent);
  border-radius: var(--radius);
  padding: 14px 18px;
  background: var(--c-surface-2);
  transition: box-shadow 0.15s;
}

.apply-item:hover {
  box-shadow: var(--shadow);
}

.ai-avatar {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  background: var(--brand-soft);
  color: var(--brand);
  font-weight: 800;
  font-size: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.ai-main {
  flex: 1;
  min-width: 0;
}

.ai-name {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 700;
  flex-wrap: wrap;
}

.pill-seat {
  background: var(--c-info-100);
  color: #1e40af;
}

.ai-meta {
  margin-top: 6px;
  font-size: 12px;
  color: var(--c-text-3);
}

.ai-actions {
  display: flex;
  gap: 8px;
}

@media (max-width: 900px) {
  .summary-row {
    grid-template-columns: 1fr;
  }
  .apply-item {
    flex-wrap: wrap;
  }
}
</style>