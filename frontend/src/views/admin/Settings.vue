<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1>系统设置</h1>
        <div class="sub">签到 / 签退时段、月度考核天数等参数，保存后立即生效</div>
      </div>
      <div class="head-tools">
        <el-button :icon="Refresh" @click="load">刷新</el-button>
        <el-button type="primary" :loading="saving" :icon="Check" @click="save">保存设置</el-button>
      </div>
    </div>

    <div class="setting-grid">
      <!-- 左列：考勤规则 + 定时任务 -->
      <div class="col">
        <section class="card" v-loading="loading">
          <div class="card-head">
            <h3><span class="ico"><el-icon><Clock /></el-icon></span>考勤时间</h3>
            <span class="desc">固定座位层签到 / 签退窗口</span>
          </div>

          <div class="field">
            <div class="field-label">
              <b>签到截止时间</b>
              <span>固定座位层学生需在此时间前完成签到</span>
            </div>
            <el-time-picker v-model="times.checkinDeadline" format="HH:mm" value-format="HH:mm" placeholder="08:30" style="width: 140px" />
          </div>

          <div class="field">
            <div class="field-label">
              <b>可签到起始时间</b>
              <span>早于此时间不能签到</span>
            </div>
            <el-time-picker v-model="times.checkinOpen" format="HH:mm" value-format="HH:mm" style="width: 140px" />
          </div>

          <div class="field">
            <div class="field-label">
              <b>签退时间段</b>
              <span>在该时段内签退才计入有效出勤</span>
            </div>
            <div class="range">
              <el-time-picker v-model="times.checkoutStart" format="HH:mm" value-format="HH:mm" style="width: 118px" />
              <span class="dash">至</span>
              <el-time-picker v-model="times.checkoutEnd" format="HH:mm" value-format="HH:mm" style="width: 118px" />
            </div>
          </div>

          <div class="field">
            <div class="field-label">
              <b>普通座位清场时间</b>
              <span>普通座位层须在此时前带走物品离场</span>
            </div>
            <el-input v-model="values['short.clear.deadline']" style="width: 140px">
              <template #append>点</template>
            </el-input>
          </div>

          <div class="field">
            <div class="field-label">
              <b>每月有效出勤天数</b>
              <span>低于该天数将在次月 1 日取消固定座位</span>
            </div>
            <el-input-number v-model="minDays" :min="1" :max="31" />
          </div>

          <div class="preview">
            <span>当前规则预览</span>
            <b class="mono">{{ times.checkinOpen }} — {{ times.checkinDeadline }} 签到 · {{ times.checkoutStart }} — {{ times.checkoutEnd }} 签退 · 出勤需满 {{ minDays }} 天</b>
          </div>
        </section>

        <section class="card">
          <div class="card-head">
            <h3><span class="ico"><el-icon><Calendar /></el-icon></span>定时任务</h3>
            <span class="desc">随应用启动自动运行</span>
          </div>
          <ul class="task-list">
            <li>
              <div class="tk-time">每月 1 日 00:00</div>
              <div class="tk-text">开放当月固定座位申请名额</div>
            </li>
            <li>
              <div class="tk-time">每月 1 日 02:00</div>
              <div class="tk-text">考核上一月出勤，不足 {{ minDays }} 天取消固定座位</div>
            </li>
            <li>
              <div class="tk-time">每日 00:05</div>
              <div class="tk-text">检查普通座位层 24:00 未清场记录</div>
            </li>
            <li>
              <div class="tk-time">每分钟</div>
              <div class="tk-text">固定座位层超时未签到的预约自动失效</div>
            </li>
            <li>
              <div class="tk-time">每秒</div>
              <div class="tk-text">通过 WebSocket 广播服务器时间</div>
            </li>
          </ul>
        </section>
      </div>

      <!-- 右列：服务器时间 + 系统信息 -->
      <div class="col">
        <section class="card">
          <div class="card-head">
            <h3><span class="ico"><el-icon><Timer /></el-icon></span>服务器时间</h3>
            <span class="pill" :class="clock.wsConnected ? 'pill-long' : 'pill-short'">
              {{ clock.wsConnected ? '实时同步' : '轮询同步' }}
            </span>
          </div>
          <div class="clock-box">
            <div class="cb-time mono">{{ clock.time }}</div>
            <div class="cb-date">{{ clock.today }} {{ clock.weekday }}</div>
          </div>
          <div class="clock-note">
            所有签到 / 签退记录均以服务器时间为准，修改本机时间不会影响考勤结果。
          </div>
        </section>

        <section class="card">
          <div class="card-head">
            <h3><span class="ico"><el-icon><Monitor /></el-icon></span>系统信息</h3>
          </div>
          <div class="sys-list">
            <div class="sys-row"><span>系统名称</span><b>{{ info.name || '—' }}</b></div>
            <div class="sys-row"><span>版本号</span><b class="mono">{{ info.version || '—' }}</b></div>
            <div class="sys-row"><span>运行环境</span><b class="mono">Java {{ info.javaVersion || '—' }}</b></div>
            <div class="sys-row"><span>操作系统</span><b>{{ info.os || '—' }}</b></div>
            <div class="sys-row"><span>时区</span><b class="mono">{{ info.timeZone || '—' }}</b></div>
            <div class="sys-row"><span>运行时长</span><b class="mono">{{ uptimeText }}</b></div>
            <div class="sys-row"><span>内存占用</span><b class="mono">{{ info.memory || '—' }}</b></div>
            <div class="sys-row">
              <span>在线连接</span>
              <b class="ok">{{ info.wsClients ?? 0 }} 个</b>
            </div>
          </div>
        </section>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, Check } from '@element-plus/icons-vue'
import { adminConfigs, updateConfigs, getSystemInfo } from '@/api'
import { useClockStore } from '@/stores/clock'

const clock = useClockStore()
const loading = ref(false)
const saving = ref(false)
const values = reactive({})
const minDays = ref(20)
const info = reactive({})
const times = reactive({ checkinDeadline: '', checkinOpen: '', checkoutStart: '', checkoutEnd: '' })

const uptimeText = computed(() => {
  const s = Number(info.uptimeSeconds || 0)
  if (!s) return '—'
  const d = Math.floor(s / 86400)
  const h = Math.floor((s % 86400) / 3600)
  const m = Math.floor((s % 3600) / 60)
  if (d > 0) return `${d} 天 ${h} 小时`
  if (h > 0) return `${h} 小时 ${m} 分`
  return `${m} 分钟`
})

async function load() {
  loading.value = true
  try {
    const [cfg, sys] = await Promise.all([adminConfigs(), getSystemInfo().catch(() => ({ data: {} }))])
    const list = cfg.data || []
    const pick = (key, def) => {
      const item = list.find((c) => c.configKey === key)
      return item ? item.configValue : def
    }
    times.checkinDeadline = pick('checkin.deadline', '08:30')
    times.checkinOpen = pick('checkin.early.open', '07:00')
    times.checkoutStart = pick('checkout.start', '21:00')
    times.checkoutEnd = pick('checkout.end', '23:59')
    values['short.clear.deadline'] = pick('short.clear.deadline', '24:00')
    minDays.value = Number(pick('long.min.days', 20))
    Object.assign(info, sys.data || {})
  } finally {
    loading.value = false
  }
}

async function save() {
  saving.value = true
  try {
    await updateConfigs({
      'checkin.deadline': times.checkinDeadline,
      'checkin.early.open': times.checkinOpen,
      'checkout.start': times.checkoutStart,
      'checkout.end': times.checkoutEnd,
      'short.clear.deadline': values['short.clear.deadline'],
      'long.min.days': String(minDays.value)
    })
    ElMessage.success('设置已保存，立即生效')
    clock.rulesLoaded = false
    await clock.pollOnce()
  } catch (e) {
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.head-tools {
  display: flex;
  gap: 10px;
}

.setting-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.25fr) minmax(0, 1fr);
  gap: 16px;
  align-items: stretch;
}

.col {
  display: flex;
  flex-direction: column;
  gap: 16px;
  align-items: stretch;
}

/* 每栏最后一张卡撑满剩余高度，两栏底部对齐 */
.col > .card:last-child {
  flex: 1;
  display: flex;
  flex-direction: column;
}

/* 列表在撑满的卡片内均匀分布，避免卡片底部留白 */
.task-list,
.sys-list {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: space-around;
}

.col .card {
  margin-top: 0;
}

/* 字段行 */
.field {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 13px 0;
  border-bottom: 1px dashed var(--c-line);
}

.field-label b {
  display: block;
  font-size: 13.5px;
}

.field-label span {
  font-size: 12px;
  color: var(--c-text-3);
}

.range {
  display: flex;
  align-items: center;
  gap: 8px;
}

.dash {
  color: var(--c-text-3);
  font-size: 12px;
}

.preview {
  margin-top: 14px;
  background: var(--brand-soft);
  border-radius: var(--radius-sm);
  padding: 11px 14px;
  font-size: 12px;
  color: var(--brand-hover);
}

.preview span {
  display: block;
  color: var(--c-text-3);
  margin-bottom: 3px;
}

.preview b {
  font-weight: 700;
  line-height: 1.6;
}

/* 定时任务 */
.task-list {
  list-style: none;
  margin: 0;
  padding: 0;
}

.task-list li {
  display: flex;
  align-items: baseline;
  gap: 14px;
  padding: 10px 0;
  border-bottom: 1px dashed var(--c-line);
}

.task-list li:last-child {
  border-bottom: none;
}

.tk-time {
  width: 108px;
  flex-shrink: 0;
  font-size: 12.5px;
  font-weight: 700;
  color: var(--brand);
}

.tk-text {
  font-size: 12.5px;
  color: var(--c-text-2);
  line-height: 1.6;
}

/* 服务器时间 */
.clock-box {
  text-align: center;
  padding: 6px 0 2px;
}

.cb-time {
  font-size: 40px;
  font-weight: 800;
  color: var(--brand);
  letter-spacing: 2px;
  line-height: 1.15;
}

.cb-date {
  font-size: 12.5px;
  color: var(--c-text-3);
  margin-top: 2px;
}

.clock-note {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px dashed var(--c-line);
  font-size: 12px;
  color: var(--c-text-3);
  line-height: 1.7;
}

/* 系统信息 */
.sys-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 9px 0;
  border-bottom: 1px dashed var(--c-line);
  font-size: 12.5px;
}

.sys-row:last-child {
  border-bottom: none;
}

.sys-row span {
  color: var(--c-text-3);
  flex-shrink: 0;
}

.sys-row b {
  font-weight: 700;
  text-align: right;
  word-break: break-all;
}

.sys-row b.ok {
  color: var(--c-success);
}

@media (max-width: 1100px) {
  .setting-grid {
    grid-template-columns: 1fr;
  }
}

/* ---------- 手机：字段行改为上下堆叠，避免标签被挤断行 ---------- */
@media (max-width: 640px) {
  .field {
    flex-direction: column;
    align-items: stretch;
    gap: 8px;
    padding: 12px 0;
  }

  .field-label b {
    font-size: 13px;
  }

  .field-label span {
    display: block;
    margin-top: 2px;
  }

  .field > .el-input,
  .field > .el-input-number,
  .field > .el-select,
  .field > .el-date-editor {
    width: 100% !important;
  }

  .range {
    width: 100%;
    gap: 6px;
  }

  .range .el-input {
    flex: 1;
    min-width: 0;
  }

  .preview {
    font-size: 11.5px;
    line-height: 1.7;
  }
}
</style>
