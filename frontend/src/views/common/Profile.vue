<template>
  <div class="page profile-page">
    <div class="page-head">
      <div>
        <h1>个人中心</h1>
        <div class="sub">查看个人资料、修改登录密码、核对服务器时间</div>
      </div>
    </div>

    <!-- 顶部身份条 -->
    <section class="hero">
      <div class="hero-avatar">{{ auth.displayName.slice(0, 1) }}</div>
      <div class="hero-main">
        <div class="hero-name">
          {{ auth.user?.realName }}
          <span class="tag" :class="auth.isAdmin ? 'tag-admin' : 'tag-student'">{{ auth.roleName }}</span>
        </div>
        <div class="hero-meta">
          <span><el-icon><User /></el-icon>{{ auth.user?.username }}</span>
          <span v-if="auth.user?.college"><el-icon><School /></el-icon>{{ auth.user.college }}</span>
          <span v-if="auth.user?.major"><el-icon><Reading /></el-icon>{{ auth.user.major }}</span>
          <span v-if="auth.user?.phone"><el-icon><Phone /></el-icon>{{ auth.user.phone }}</span>
        </div>
      </div>
      <div class="hero-clock">
        <div class="hc-label"><i :class="{ off: !clock.wsConnected }"></i>服务器时间</div>
        <div class="hc-time mono">{{ clock.time }}</div>
        <div class="hc-date">{{ clock.today }} {{ clock.weekday }}</div>
      </div>
    </section>

    <!-- 数据概览条：填满横向空间 -->
    <section class="stat-strip">
      <div v-for="s in stats" :key="s.label" class="ss-item">
        <div class="ss-icon" :class="s.tone"><el-icon><component :is="s.icon" /></el-icon></div>
        <div class="ss-body">
          <div class="ss-value">{{ s.value }}<small v-if="s.unit">{{ s.unit }}</small></div>
          <div class="ss-label">{{ s.label }}</div>
        </div>
      </div>
    </section>

    <!-- 两栏：左（资料 + 规则） 右（改密 + 安全） -->
    <div class="grid">
      <div class="col">
        <section class="card">
          <div class="card-head">
            <h3><span class="ico"><el-icon><User /></el-icon></span>基本资料</h3>
            <span class="desc">账号信息不可自行修改</span>
          </div>
          <div class="info-list">
            <div class="info-row" v-for="item in infoRows" :key="item.label">
              <span class="ir-label">{{ item.label }}</span>
              <span class="ir-value" :class="{ mono: item.mono }">
                <span v-if="item.tag" class="tag" :class="item.tagClass">{{ item.value }}</span>
                <template v-else>{{ item.value }}</template>
              </span>
            </div>
          </div>
        </section>

        <section class="card">
          <div class="card-head">
            <h3><span class="ico"><el-icon><Document /></el-icon></span>使用规则</h3>
            <span class="desc">自习楼 1—5 层</span>
          </div>
          <ul class="rules">
            <li v-for="(r, i) in rules" :key="i">{{ r }}</li>
            <li v-if="!rules.length" class="empty-inline">暂无规则</li>
          </ul>
        </section>
      </div>

      <div class="col">
        <section class="card">
          <div class="card-head">
            <h3><span class="ico"><el-icon><Lock /></el-icon></span>修改密码</h3>
            <span class="desc">修改成功后需要重新登录</span>
          </div>
          <el-form ref="formRef" :model="form" :rules="rules2" label-position="top" class="pwd-form">
            <el-form-item label="原密码" prop="oldPassword">
              <el-input v-model="form.oldPassword" type="password" show-password placeholder="请输入原密码" size="large" />
            </el-form-item>
            <el-form-item label="新密码" prop="newPassword">
              <el-input v-model="form.newPassword" type="password" show-password placeholder="至少 6 位字符" size="large" />
            </el-form-item>
            <el-form-item label="确认密码" prop="confirm">
              <el-input v-model="form.confirm" type="password" show-password placeholder="请再次输入新密码" size="large" />
            </el-form-item>
            <el-button type="primary" size="large" class="pwd-btn" :loading="saving" @click="submit">
              保存并重新登录
            </el-button>
          </el-form>
        </section>

        <section class="card">
          <div class="card-head">
            <h3><span class="ico"><el-icon><InfoFilled /></el-icon></span>账号安全</h3>
          </div>
          <div class="security">
            <div class="sec-row">
              <span>登录账号</span>
              <b class="mono">{{ auth.user?.username }}</b>
            </div>
            <div class="sec-row">
              <span>账号角色</span>
              <b>{{ auth.roleName }}</b>
            </div>
            <div class="sec-row">
              <span>最近登录</span>
              <b class="mono">{{ clock.today }} {{ clock.time.slice(0, 5) }}</b>
            </div>
            <div class="sec-row">
              <span>连接状态</span>
              <b class="ok">{{ clock.wsConnected ? 'WebSocket 实时同步' : 'HTTP 轮询同步' }}</b>
            </div>
          </div>
          <div class="sec-tip">
            <el-icon><Warning /></el-icon>
            <span>忘记原密码请联系管理员在「学生账号」中重置为 123456</span>
          </div>
        </section>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { useClockStore } from '@/stores/clock'
import { changePassword, getRules, getMyLongTerm, getMyReservations, adminOverview, adminPendingLongTerm } from '@/api'

const router = useRouter()
const auth = useAuthStore()
const clock = useClockStore()

const formRef = ref(null)
const saving = ref(false)
const rules = ref([])
const stat = reactive({
  totalReservations: 0,
  monthDays: 0,
  checkInDays: 0,
  seatNo: '',
  roomCount: 0,
  studentCount: 0,
  todayReserved: 0,
  pendingReview: 0
})

const form = reactive({ oldPassword: '', newPassword: '', confirm: '' })

const rules2 = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, message: '密码长度不能少于 6 位', trigger: 'blur' }
  ],
  confirm: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (rule, value, cb) => (value === form.newPassword ? cb() : cb(new Error('两次输入的密码不一致'))),
      trigger: 'blur'
    }
  ]
}

const infoRows = computed(() => {
  const u = auth.user || {}
  return [
    { label: '登录账号', value: u.username || '—', mono: true },
    { label: '学号 / 工号', value: u.studentNo || u.username || '—', mono: true },
    { label: '所属学院', value: u.college || '—' },
    { label: '专业班级', value: u.major || '—' },
    { label: '联系电话', value: u.phone || '—', mono: true },
    {
      label: '账号状态',
      value: u.status === 1 ? '正常' : '已禁用',
      tag: true,
      tagClass: u.status === 1 ? 'tag-ok' : 'tag-bad'
    },
    { label: '注册时间', value: u.createTime || '—', mono: true }
  ]
})

const stats = computed(() => {
  if (auth.isAdmin) {
    return [
      { label: '楼层数量', value: stat.roomCount, icon: 'OfficeBuilding', tone: 't-indigo' },
      { label: '学生总数', value: stat.studentCount, icon: 'UserFilled', tone: 't-emerald' },
      { label: '今日预约', value: stat.todayReserved, icon: 'Tickets', tone: 't-amber' },
      { label: '待审核申请', value: stat.pendingReview, icon: 'Checked', tone: 't-rose' }
    ]
  }
  return [
    { label: '累计预约', value: stat.totalReservations, unit: ' 次', icon: 'Tickets', tone: 't-indigo' },
    { label: '本月有效出勤', value: stat.monthDays, unit: ' 天', icon: 'Calendar', tone: 't-emerald' },
    { label: '本月签到', value: stat.checkInDays, unit: ' 天', icon: 'CircleCheck', tone: 't-amber' },
    { label: '我的固定座位', value: stat.seatNo || '未申请', icon: 'Collection', tone: 't-rose' }
  ]
})

async function loadRules() {
  try {
    const res = await getRules()
    rules.value = Array.isArray(res.data) ? res.data : []
  } catch (e) {}
}

async function loadStats() {
  if (auth.isAdmin) {
    try {
      const [ov, pd] = await Promise.all([adminOverview(), adminPendingLongTerm().catch(() => ({ data: [] }))])
      const d = ov.data || {}
      stat.roomCount = d.roomCount ?? 0
      stat.studentCount = d.studentCount ?? 0
      stat.todayReserved = d.todayReserved ?? 0
      stat.pendingReview = (pd.data || []).length
    } catch (e) {}
    return
  }
  try {
    const [res, lt] = await Promise.all([
      getMyReservations({ pageNum: 1, pageSize: 1 }),
      getMyLongTerm().catch(() => ({ data: {} }))
    ])
    stat.totalReservations = res.data?.total ?? 0
    const cal = lt.data?.calendar || {}
    stat.monthDays = cal.bothDays ?? 0
    stat.checkInDays = cal.checkInDays ?? 0
    stat.seatNo = lt.data?.binding?.seatNo || ''
  } catch (e) {}
}

async function submit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    await changePassword({ oldPassword: form.oldPassword, newPassword: form.newPassword })
    ElMessage.success('密码修改成功，请重新登录')
    await auth.logout()
    router.replace('/login')
  } catch (e) {
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  loadRules()
  loadStats()
})
</script>

<style scoped>
/* 占满可用宽度，避免宽屏右侧大片留白 */
.profile-page {
  width: 100%;
}

/* 顶部身份条 */
.hero {
  display: flex;
  align-items: center;
  gap: 18px;
  background: #ffffff;
  border: 1px solid var(--c-line);
  border-radius: var(--radius);
  padding: 18px 22px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.hero-avatar {
  width: 60px;
  height: 60px;
  border-radius: 18px;
  background: var(--brand);
  color: #fff;
  font-size: 25px;
  font-weight: 800;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  box-shadow: 0 12px 22px -14px rgba(79, 70, 229, 0.9);
}

.hero-main {
  flex: 1;
  min-width: 220px;
}

.hero-name {
  font-size: 21px;
  font-weight: 800;
  display: flex;
  align-items: center;
  gap: 10px;
}

.hero-meta {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
  margin-top: 8px;
  font-size: 12.5px;
  color: var(--c-text-2);
}

.hero-meta span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.hero-clock {
  text-align: right;
  padding-left: 20px;
  border-left: 1px dashed var(--c-line-strong);
}

.hc-label {
  font-size: 11px;
  color: var(--c-text-3);
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 5px;
}

.hc-label i {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #10b981;
}

.hc-label i.off {
  background: #f59e0b;
}

.hc-time {
  font-size: 26px;
  font-weight: 800;
  color: var(--brand);
  line-height: 1.2;
}

.hc-date {
  font-size: 11.5px;
  color: var(--c-text-3);
}

/* 数据概览条 */
.stat-strip {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
  margin-bottom: 16px;
}

.ss-item {
  display: flex;
  align-items: center;
  gap: 12px;
  background: var(--c-surface);
  border: 1px solid var(--c-line);
  border-radius: var(--radius);
  padding: 16px 18px;
}

.ss-icon {
  width: 42px;
  height: 42px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 19px;
  flex-shrink: 0;
}

.t-indigo {
  background: var(--brand-soft);
  color: var(--brand);
}

.t-emerald {
  background: var(--c-success-100);
  color: #047857;
}

.t-amber {
  background: var(--c-accent-100);
  color: #c2410c;
}

.t-rose {
  background: var(--c-danger-100);
  color: #be123c;
}

.ss-value {
  font-size: 21px;
  font-weight: 800;
  line-height: 1.2;
  color: var(--c-text);
}

.ss-value small {
  font-size: 12px;
  font-weight: 600;
  color: var(--c-text-3);
}

.ss-label {
  font-size: 12px;
  color: var(--c-text-3);
  margin-top: 2px;
}

/* 两栏 */
.grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
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
.rules,
.security {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: space-around;
}

.col .card {
  margin-top: 0;
}

/* 资料行 */
.info-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 11px 2px;
  border-bottom: 1px dashed var(--c-line);
  font-size: 13.5px;
}

.info-row:last-child {
  border-bottom: none;
}

.ir-label {
  color: var(--c-text-3);
  flex-shrink: 0;
}

.ir-value {
  color: var(--c-text);
  font-weight: 600;
  text-align: right;
  word-break: break-all;
}

/* 规则 */
.rules {
  margin: 0;
  padding: 0;
  list-style: none;
  font-size: 12.5px;
  line-height: 1.9;
  color: var(--c-text-2);
}

.rules li {
  margin-bottom: 4px;
  padding-left: 2px;
}

/* 标签 */
.tag {
  display: inline-flex;
  align-items: center;
  padding: 1px 10px;
  border-radius: 999px;
  font-size: 11.5px;
  font-weight: 700;
  line-height: 19px;
}

.tag-student {
  background: var(--brand-border);
  color: var(--brand-hover);
}

.tag-admin {
  background: var(--c-accent-100);
  color: #c2410c;
}

.tag-ok {
  background: var(--c-success-100);
  color: #047857;
}

.tag-bad {
  background: var(--c-danger-100);
  color: #be123c;
}

/* 表单 */
.pwd-form :deep(.el-form-item) {
  margin-bottom: 16px;
}

.pwd-form :deep(.el-form-item__label) {
  font-weight: 600;
  color: var(--c-text-2);
  padding-bottom: 4px;
}

.pwd-btn {
  width: 100%;
  margin-top: 4px;
}

/* 安全信息 */
.sec-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 10px 2px;
  border-bottom: 1px dashed var(--c-line);
  font-size: 13px;
}

.sec-row:last-of-type {
  border-bottom: none;
}

.sec-row span {
  color: var(--c-text-3);
}

.sec-row b {
  font-weight: 700;
}

.sec-row b.ok {
  color: var(--c-success);
}

.sec-tip {
  display: flex;
  align-items: flex-start;
  gap: 7px;
  margin-top: 12px;
  font-size: 12px;
  color: var(--c-text-3);
  background: var(--c-surface-2);
  border-radius: var(--radius-sm);
  padding: 10px 12px;
  line-height: 1.6;
}

@media (max-width: 1100px) {
  .stat-strip {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .grid {
    grid-template-columns: 1fr;
  }
  .hero-clock {
    border-left: none;
    padding-left: 0;
    text-align: left;
  }
  .hc-label {
    justify-content: flex-start;
  }
}
</style>
