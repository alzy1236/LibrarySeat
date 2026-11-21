<template>
  <div class="login-page">
    <div class="login-shell">
      <!-- 左侧品牌区 -->
      <aside class="brand-side">
        <div class="brand">
          <div class="brand-mark">
            <el-icon><Reading /></el-icon>
          </div>
          <div class="brand-name">
            <b>自习室座位预约</b>
            <span>自习楼 1—5 层</span>
          </div>
        </div>

        <ul class="bullets">
          <li><span class="dot-num">01</span>在线选座，座位与桌子一目了然</li>
          <li><span class="dot-num">02</span>固定座位在线申请，管理员审核后生效</li>
          <li><span class="dot-num">03</span>签到签退以服务器时间为准</li>
        </ul>

        <div class="clock-cardZ">
          <div class="clock-top">
            <span>服务器时间</span>
            <span class="sync" :class="{ off: !clock.wsConnected }">
              <i></i>{{ clock.wsConnected ? '实时同步' : '同步中' }}
            </span>
          </div>
          <div class="clock-time mono">{{ clock.time }}</div>
          <div class="clock-date">{{ clock.today }} {{ clock.weekday }}</div>
          <div class="clock-window">
            <div>
              <span>签到</span>
              <b class="mono">{{ clock.rules.checkInOpen }}–{{ clock.rules.checkInDeadline }}</b>
            </div>
            <div>
              <span>签退</span>
              <b class="mono">{{ clock.rules.checkOutStart }}–{{ clock.rules.checkOutEnd }}</b>
            </div>
          </div>
        </div>
      </aside>

      <!-- 右侧登录表单 -->
      <section class="form-side">
        <div class="form-card">
          <div class="form-head">
            <h3>登录账号</h3>
            <p>使用学号或管理员账号登录，系统会自动进入对应界面</p>
          </div>

          <el-form ref="formRef" :model="form" :rules="rules" size="large" @keyup.enter="submit">
            <el-form-item prop="username">
              <el-input v-model="form.username" placeholder="账号（学号 / 管理员账号）" clearable>
                <template #prefix><el-icon><User /></el-icon></template>
              </el-input>
            </el-form-item>
            <el-form-item prop="password">
              <el-input v-model="form.password" type="password" placeholder="密码" show-password clearable>
                <template #prefix><el-icon><Lock /></el-icon></template>
              </el-input>
            </el-form-item>
            <div class="form-row">
              <el-checkbox v-model="remember">记住账号</el-checkbox>
              <a class="link" @click="openReset">忘记密码？</a>
            </div>
            <el-button type="primary" class="submit-btn" :loading="loading" @click="submit">登 录</el-button>
            <div class="register-row">
              还没有账号？
              <a class="link strong" @click="goRegister">立即注册</a>
            </div>
          </el-form>

          <div class="form-foot">登录即表示同意遵守自习室使用规定 · 签到签退以服务器时间为准</div>
        </div>
      </section>
    </div>

    <!-- 忘记密码：手机验证码自助重置 -->
    <el-dialog v-model="resetVisible" title="找回密码" width="420px" append-to-body destroy-on-close>
      <div class="reset-tip">请输入注册时绑定的手机号，验证通过后可直接设置新密码，无需联系管理员。</div>
      <el-form ref="resetFormRef" :model="resetForm" :rules="resetRules" label-position="top">
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="resetForm.phone" placeholder="11 位手机号" maxlength="11" />
        </el-form-item>
        <el-form-item label="短信验证码" prop="code">
          <div class="code-row">
            <el-input v-model="resetForm.code" placeholder="4 位数字" maxlength="4" />
            <el-button :disabled="countdown > 0" :loading="sending" @click="sendResetCode">
              {{ countdown > 0 ? countdown + ' 秒后重发' : '获取验证码' }}
            </el-button>
          </div>
        </el-form-item>
        <div v-if="mockTip" class="mock-tip">{{ mockTip }}</div>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="resetForm.newPassword" type="password" placeholder="至少 6 位" show-password />
        </el-form-item>
        <el-form-item label="确认新密码" prop="confirmPassword">
          <el-input v-model="resetForm.confirmPassword" type="password" placeholder="再输一次" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resetVisible = false">取消</el-button>
        <el-button type="primary" :loading="resetting" @click="submitReset">重置密码</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { useClockStore } from '@/stores/clock'
import { sendSmsCode, resetPassword } from '@/api'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const clock = useClockStore()

const formRef = ref(null)
const loading = ref(false)
const remember = ref(true)
const form = reactive({
  username: localStorage.getItem('ls_remember_username') || '',
  password: ''
})
const rules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

onMounted(() => {
  clock.connect()
})

onBeforeUnmount(() => clearInterval(timer))

/* ---------------- 找回密码（手机验证码） ---------------- */
const resetVisible = ref(false)
const resetFormRef = ref(null)
const resetting = ref(false)
const sending = ref(false)
const countdown = ref(0)
const mockTip = ref('')
let timer = null

const resetForm = reactive({
  phone: '',
  code: '',
  newPassword: '',
  confirmPassword: ''
})

const validateConfirmPwd = (rule, value, cb) => {
  if (!value) return cb(new Error('请再次输入新密码'))
  if (value !== resetForm.newPassword) return cb(new Error('两次输入的密码不一致'))
  cb()
}

const resetRules = {
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }
  ],
  code: [
    { required: true, message: '请输入验证码', trigger: 'blur' },
    { pattern: /^\d{4}$/, message: '验证码为 4 位数字', trigger: 'blur' }
  ],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码长度 6-32 位', trigger: 'blur' }
  ],
  confirmPassword: [{ required: true, validator: validateConfirmPwd, trigger: 'blur' }]
}

function startCountdown(seconds) {
  countdown.value = seconds
  clearInterval(timer)
  timer = setInterval(() => {
    countdown.value -= 1
    if (countdown.value <= 0) clearInterval(timer)
  }, 1000)
}

function openReset() {
  resetForm.phone = ''
  resetForm.code = ''
  resetForm.newPassword = ''
  resetForm.confirmPassword = ''
  mockTip.value = ''
  resetVisible.value = true
}

async function sendResetCode() {
  if (sending.value || countdown.value > 0) return
  if (!/^1[3-9]\d{9}$/.test(resetForm.phone)) {
    ElMessage.warning('请先填写正确的 11 位手机号')
    return
  }
  sending.value = true
  try {
    const res = await sendSmsCode(resetForm.phone.trim(), 'RESET')
    const data = res.data || {}
    startCountdown(data.resendSeconds || 60)
    if (data.mock) {
      mockTip.value = data.tip || `演示模式：验证码为 ${data.code}`
      resetForm.code = data.code || ''
      ElMessage.success('验证码已生成（演示模式，已自动填入）')
    } else {
      mockTip.value = ''
      ElMessage.success('验证码已发送，请查收短信')
    }
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    sending.value = false
  }
}

async function submitReset() {
  if (!resetFormRef.value) return
  const valid = await resetFormRef.value.validate().catch(() => false)
  if (!valid) return
  resetting.value = true
  try {
    await resetPassword({
      phone: resetForm.phone.trim(),
      code: resetForm.code.trim(),
      newPassword: resetForm.newPassword
    })
    ElMessage.success('密码已重置，请使用学号 / 管理员账号和新密码登录')
    resetVisible.value = false
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    resetting.value = false
  }
}

function goRegister() {
  router.replace('/register')
}

async function submit() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    const data = await auth.login({ username: form.username.trim(), password: form.password })
    if (remember.value) {
      localStorage.setItem('ls_remember_username', form.username.trim())
    } else {
      localStorage.removeItem('ls_remember_username')
    }
    ElMessage.success({
      message: `欢迎，${data.userInfo.realName}（${data.userInfo.role === 'ADMIN' ? '管理员' : '学生'}）`,
      duration: 1500
    })
    // 账号类型决定跳转：管理员 -> 管理端，学生 -> 学生端
    const redirect = route.query.redirect
    const target = data.redirect
    const prefix = target.startsWith('/admin') ? '/admin' : '/student'
    if (redirect && typeof redirect === 'string' && redirect.startsWith(prefix)) {
      router.replace(redirect)
    } else {
      router.replace(target)
    }
  } catch (e) {
    const msg = e?.message || ''
    if (msg && !msg.includes('登录已失效')) {
      ElMessage.closeAll()
      ElMessage.error(msg)
    }
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px 24px;
  background: var(--gray-50);
  background-image: radial-gradient(circle at 1px 1px, var(--gray-200) 1px, transparent 0);
  background-size: 22px 22px;
}

.login-shell {
  width: 100%;
  max-width: 920px;
  display: grid;
  grid-template-columns: 1.05fr 1fr;
  background: #fff;
  border: 1px solid var(--c-line);
  border-radius: var(--radius-lg);
  overflow: hidden;
  box-shadow: 0 1px 2px rgba(16, 24, 40, 0.04), 0 16px 40px -24px rgba(16, 24, 40, 0.18);
}

/* ---------- 左侧 ---------- */
.brand-side {
  background: var(--gray-50);
  border-right: 1px solid var(--c-line);
  padding: 32px 30px;
  display: flex;
  flex-direction: column;
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
}

.brand-mark {
  width: 34px;
  height: 34px;
  border-radius: var(--radius);
  background: var(--brand);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  flex-shrink: 0;
}

.brand-name b {
  display: block;
  font-size: 14px;
  font-weight: 650;
  color: var(--gray-900);
  letter-spacing: -0.01em;
  line-height: 1.35;
}

.brand-name span {
  font-size: 11.5px;
  color: var(--gray-400);
}

.bullets {
  list-style: none;
  margin: 30px 0 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.bullets li {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  font-size: 12.5px;
  line-height: 1.6;
  color: var(--gray-600);
}

.dot-num {
  flex-shrink: 0;
  font-size: 10px;
  font-weight: 600;
  color: var(--brand);
  background: var(--brand-soft);
  border: 1px solid var(--brand-border);
  border-radius: 4px;
  padding: 0 5px;
  line-height: 17px;
  margin-top: 1px;
  font-variant-numeric: tabular-nums;
}

/* 时钟 */
.clock-card {
  margin-top: auto;
  padding: 16px 18px;
  background: #fff;
  border: 1px solid var(--c-line);
  border-radius: var(--radius);
}

.clock-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 11.5px;
  color: var(--gray-400);
}

.sync {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: var(--green);
  font-weight: 500;
}

.sync i {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--green);
}

.sync.off {
  color: var(--amber);
}

.sync.off i {
  background: var(--amber);
}

.clock-time {
  font-size: 34px;
  font-weight: 600;
  letter-spacing: -0.02em;
  color: var(--gray-900);
  line-height: 1.2;
  margin-top: 6px;
  font-variant-numeric: tabular-nums;
}

.clock-date {
  font-size: 11.5px;
  color: var(--gray-400);
}

.clock-window {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid var(--gray-200);
}

.clock-window span {
  display: block;
  font-size: 11px;
  color: var(--gray-400);
}

.clock-window b {
  font-size: 12.5px;
  font-weight: 600;
  color: var(--gray-700);
}

/* ---------- 右侧 ---------- */
.form-side {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px 36px;
}

.form-card {
  width: 100%;
  max-width: 320px;
}

.form-head h3 {
  margin: 0;
  font-size: 19px;
  font-weight: 650;
  letter-spacing: -0.02em;
  color: var(--gray-900);
}

.form-head p {
  margin: 6px 0 22px;
  font-size: 12.5px;
  color: var(--gray-500);
  line-height: 1.6;
}

.form-card :deep(.el-form-item) {
  margin-bottom: 14px;
}

.form-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: -2px 0 18px;
  font-size: 12.5px;
}

/* ---------- 找回密码弹窗 ---------- */
.reset-tip {
  margin-bottom: 14px;
  padding: 8px 11px;
  border-radius: var(--radius-sm);
  background: var(--gray-50);
  border: 1px solid var(--gray-200);
  color: var(--gray-600);
  font-size: 11.5px;
  line-height: 1.65;
}

.code-row {
  display: flex;
  gap: 8px;
  width: 100%;
}

.code-row .el-input {
  flex: 1;
}

.mock-tip {
  margin: -6px 0 12px;
  padding: 7px 10px;
  border-radius: var(--radius-sm);
  background: #fffbeb;
  border: 1px solid #fde68a;
  color: #92400e;
  font-size: 11.5px;
  line-height: 1.6;
}

.link {
  color: var(--brand);
  cursor: pointer;
  font-size: 12.5px;
}

.link:hover {
  color: var(--brand-hover);
  text-decoration: underline;
}

.submit-btn {
  width: 100%;
  height: 38px;
  font-size: 13.5px;
  font-weight: 600;
  letter-spacing: 0.04em;
}

.register-row {
  margin-top: 14px;
  text-align: center;
  font-size: 12.5px;
  color: var(--gray-500);
}

.link.strong {
  font-weight: 600;
}

.form-foot {
  margin-top: 20px;
  padding-top: 14px;
  border-top: 1px solid var(--gray-200);
  font-size: 11.5px;
  color: var(--gray-400);
  line-height: 1.6;
}

@media (max-width: 820px) {
  .login-shell {
    grid-template-columns: 1fr;
    max-width: 420px;
  }
  .brand-side {
    border-right: none;
    border-bottom: 1px solid var(--c-line);
    padding: 24px;
  }
  .bullets {
    margin-top: 18px;
  }
  .clock-card {
    margin-top: 18px;
  }
  .form-side {
    padding: 28px 24px 32px;
  }
}
</style>