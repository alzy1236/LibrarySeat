<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1>固定座位 · 月度考核</h1>
        <div class="sub">
          固定座位层每月 1 号零点自动开放申请；每月 1 号凌晨 2 点考核上一月：有效出勤（签到且签退）不足
          {{ requiredDays }} 天自动取消座位
        </div>
      </div>
      <el-button :icon="Refresh" @click="loadAll">刷新</el-button>
    </div>

    <el-row :gutter="16">
      <el-col :xs="24" :lg="9">
        <div class="card">
          <div class="card-head">
            <h3><el-icon><DataLine /></el-icon> 执行月度考核</h3>
          </div>
          <el-form label-width="90px">
            <el-form-item label="考核月份">
              <el-date-picker
                v-model="assessMonth"
                type="month"
                format="YYYY-MM"
                value-format="YYYY-MM"
                placeholder="默认上月"
                style="width: 100%"
              />
            </el-form-item>
            <el-form-item>
              <el-button @click="runAssess(true)" :loading="previewing">预览考核结果</el-button>
              <el-button type="danger" plain @click="runAssess(false)" :loading="assessing">执行考核并取消座位</el-button>
            </el-form-item>
          </el-form>

          <el-result v-if="summary" :icon="summary.unqualified > 0 ? 'warning' : 'success'" :title="`${summary.month} 考核结果`">
            <template #sub-title>
              <div class="sum-row">
                <span>参与考核 <b>{{ summary.total }}</b> 人</span>
                <span class="text-success">合格 <b>{{ summary.qualified }}</b> 人</span>
                <span class="text-danger">不合格 <b>{{ summary.unqualified }}</b> 人</span>
                <span>已取消座位 <b>{{ summary.cancelled }}</b> 人</span>
              </div>
              <div class="muted" style="margin-top: 6px">{{ summary.rule }}</div>
            </template>
          </el-result>

          <el-alert
            type="info"
            :closable="false"
            show-icon
            title="考核规则说明"
            description="以“同一日期内既有签到又有签退”的天数计为有效出勤天数；预览不会修改数据，执行后将立即取消不合格学生的固定座位。"
          />
        </div>
      </el-col>

      <el-col :xs="24" :lg="15">
        <div class="card">
          <div class="card-head">
            <h3><el-icon><UserFilled /></el-icon> 当前使用中的固定座位</h3>
            <el-input v-model="keyword" placeholder="搜索学生 / 学号 / 座位" clearable style="width: 220px" :prefix-icon="Search" @input="loadBindings" />
          </div>
          <el-table v-loading="loadingBind" :data="bindings" stripe border size="small" empty-text="暂无使用中的固定座位" max-height="420">
            <el-table-column label="学生" width="110">
              <template #default="{ row }">
                <div class="stu-name">{{ row.userName }}</div>
                <div class="muted" style="font-size: 11px">{{ row.studentNo }}</div>
              </template>
            </el-table-column>
            <el-table-column label="座位" min-width="150">
              <template #default="{ row }">
                <div>{{ row.seatNo }} <span class="muted">{{ row.roomName?.replace('自习室', '') }}</span></div>
              </template>
            </el-table-column>
            <el-table-column label="本月出勤" width="120" align="center">
              <template #default="{ row }">
                <div :class="row.monthCheckInDays >= requiredDays ? 'text-success' : ''">
                  签到 {{ row.monthCheckInDays }} 天
                </div>
                <div class="muted" style="font-size: 11px">签退 {{ row.monthCheckOutDays }} 天</div>
              </template>
            </el-table-column>
            <el-table-column label="进度" width="120">
              <template #default="{ row }">
                <el-progress
                  :percentage="Math.min(100, Math.round((row.monthCheckInDays / requiredDays) * 100))"
                  :stroke-width="10"
                  :status="row.monthCheckInDays >= requiredDays ? 'success' : undefined"
                />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="90" align="center" fixed="right">
              <template #default="{ row }">
                <el-button link type="danger" @click="release(row)">释放</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-col>
    </el-row>

    <div class="card">
      <div class="card-head">
        <h3><el-icon><Tickets /></el-icon> 考核记录</h3>
        <div class="toolbar" style="margin: 0">
          <el-date-picker v-model="filterMonth" type="month" format="YYYY-MM" value-format="YYYY-MM" placeholder="考核月份" style="width: 150px" clearable @change="loadAssessments" />
          <el-select v-model="filterQualified" placeholder="全部结果" clearable style="width: 130px" @change="loadAssessments">
            <el-option label="合格" :value="1" />
            <el-option label="不合格" :value="0" />
          </el-select>
          <el-input v-model="filterKeyword" placeholder="学生 / 学号" clearable style="width: 160px" @keyup.enter="loadAssessments" />
          <el-button type="primary" @click="loadAssessments">查询</el-button>
        </div>
      </div>
      <el-table v-loading="loadingAssess" :data="assessments" stripe border size="small" empty-text="暂无考核记录">
        <el-table-column prop="assessMonth" label="考核月份" width="110" />
        <el-table-column prop="userName" label="学生" width="90" />
        <el-table-column prop="studentNo" label="学号" width="110" />
        <el-table-column prop="roomName" label="自习室" min-width="160" show-overflow-tooltip />
        <el-table-column prop="seatNo" label="座位" width="90" />
        <el-table-column prop="checkinDays" label="签到天数" width="100" align="center" />
        <el-table-column prop="checkoutDays" label="签退天数" width="100" align="center" />
        <el-table-column prop="requiredDays" label="要求" width="80" align="center" />
        <el-table-column label="结果" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.qualified === 1 ? 'success' : 'danger'" effect="plain" size="small">
              {{ row.qualified === 1 ? '合格' : '不合格' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="取消座位" width="100" align="center">
          <template #default="{ row }">
            <span :class="row.cancelledBinding === 1 ? 'text-danger' : 'muted'">{{ row.cancelledBinding === 1 ? '已取消' : '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="200" show-overflow-tooltip />
      </el-table>
      <div class="pager">
        <el-pagination
          v-model:current-page="assessPage.pageNum"
          v-model:page-size="assessPage.pageSize"
          :total="assessTotal"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          background
          @current-change="loadAssessments"
          @size-change="loadAssessments"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import { adminLongTerm, releaseLongTerm, runAssessment, adminAssessments, adminConfigs } from '@/api'

const loadingBind = ref(false)
const loadingAssess = ref(false)
const previewing = ref(false)
const assessing = ref(false)
const bindings = ref([])
const keyword = ref('')
const assessMonth = ref('')
const summary = ref(null)
const requiredDays = ref(20)

const assessments = ref([])
const assessTotal = ref(0)
const assessPage = reactive({ pageNum: 1, pageSize: 10 })
const filterMonth = ref('')
const filterQualified = ref(null)
const filterKeyword = ref('')

async function loadBindings() {
  loadingBind.value = true
  try {
    const res = await adminLongTerm({ keyword: keyword.value || undefined })
    bindings.value = res.data || []
  } finally {
    loadingBind.value = false
  }
}

async function loadAssessments() {
  loadingAssess.value = true
  try {
    const res = await adminAssessments({
      pageNum: assessPage.pageNum,
      pageSize: assessPage.pageSize,
      assessMonth: filterMonth.value || undefined,
      qualified: filterQualified.value === null || filterQualified.value === '' ? undefined : filterQualified.value,
      keyword: filterKeyword.value || undefined
    })
    assessments.value = res.data.records || []
    assessTotal.value = res.data.total || 0
  } finally {
    loadingAssess.value = false
  }
}

async function loadConfig() {
  try {
    const res = await adminConfigs()
    const cfg = res.data.find((c) => c.configKey === 'long.min.days')
    requiredDays.value = Number(cfg?.configValue || 20)
  } catch (e) {
    /* ignore */
  }
}

async function runAssess(dryRun) {
  if (!dryRun) {
    try {
      await ElMessageBox.confirm(
        '执行考核将立即取消本月不达标的固定座位，且不可恢复，确定继续吗？',
        '执行月度考核',
        { type: 'warning', confirmButtonText: '确定执行', cancelButtonText: '取消' }
      )
    } catch (e) {
      return
    }
  }
  dryRun ? (previewing.value = true) : (assessing.value = true)
  try {
    const res = await runAssessment(assessMonth.value || undefined, dryRun)
    summary.value = res.data
    ElMessage.success(dryRun ? '预览完成（未修改数据）' : `考核完成，取消 ${res.data.cancelled} 个座位`)
    if (!dryRun) {
      loadBindings()
      loadAssessments()
    }
  } catch (e) {
    /* ignore */
  } finally {
    previewing.value = false
    assessing.value = false
  }
}

async function release(row) {
  try {
    await ElMessageBox.confirm(`确定释放 ${row.userName} 的固定座位 ${row.seatNo} 吗？`, '释放固定座位', {
      type: 'warning',
      confirmButtonText: '确定释放'
    })
    await releaseLongTerm(row.id, '管理员释放')
    ElMessage.success('已释放固定座位')
    loadBindings()
  } catch (e) {
    /* ignore */
  }
}

function loadAll() {
  loadBindings()
  loadAssessments()
}

onMounted(() => {
  loadConfig()
  loadAll()
})
</script>

<style scoped>
.sum-row {
  display: flex;
  gap: 18px;
  justify-content: center;
  flex-wrap: wrap;
  font-size: 14px;
}

.stu-name {
  font-weight: 600;
  color: #1f2937;
}
</style>

