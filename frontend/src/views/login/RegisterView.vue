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
            <span>学生账号自助注册</span>
          </div>
        </div>

        <ul class="bullets">
          <li><span class="dot-num">01</span>用学号注册，注册后即可预约座位</li>
          <li><span class="dot-num">02</span>固定座位需提交申请，管理员审核后生效</li>
          <li><span class="dot-num">03</span>每天签到不得晚于 08:30，签退时间 21:00 起</li>
        </ul>

        <div class="clock-card">
          <div class="clock-top">
            <span>服务器时间</span>
            <span class="sync" :class="{ off: !clock.wsConnected }">
              <i></i>{{ clock.wsConnected ? '实时同步' : '同步中' }}
            </span>
          </div>
          <div class="clock-time mono">{{ clock.time }}</div>
          <div class="clock-date">{{ clock.today }} {{ clock.weekday }}</div>
        </div>
      </aside>

      <!-- 右侧注册表单 -->
      <section class="form-side">
        <div class="form-card">
          <div class="form-head">
            <h3>注册学生账号</h3>
            <p>填写以下信息完成注册，带 <b class="req">*</b> 为必填项</p>
          </div>

          <el-form ref="formRef" :model="form" :rules="rules" size="large" label-position="top" @keyup.enter="submit">
            <el-form-item label="学号（登录账号）" prop="username">
              <el-input v-model="form.username" placeholder="例如 20230021" clearable maxlength="20">
                <template #prefix><el-icon><User /></el-icon></template>
              </el-input>
            </el-form-item>

            <el-form-item label="姓名" prop="realName">
              <el-input v-model="form.realName" placeholder="请输入真实姓名" clearable maxlength="20">
                <template #prefix><el-icon><Avatar /></el-icon></template>
              </el-input>
            </el-form-item>

            <div class="grid-2">
              <el-form-item label="学院" prop="college">
                <el-input v-model="form.college" placeholder="选填" clearable maxlength="30" />
              </el-form-item>
              <el-form-item label="专业" prop="major">
                <el-input v-model="form.major" placeholder="选填" clearable maxlength="30" />
              </el-form-item>
            </div>

            <el-form-item label="手机号" prop="phone">
              <el-input v-model="form.phone" placeholder="用于接收验证码、找回密码" clearable maxlength="11">
                <template #prefix><el-icon><Iphone /></el-icon></template>
              </el-input>
            </el-form-item>

            <el-form-item label="短信验证码" prop="smsCode">
              <div class="code-row">
                <el-input v-model="form.smsCode" placeholder="4 位数字" maxlength="4">
                  <template #prefix><el-icon><Message /></el-icon></template>
                </el-input>
                <el-button :disabled="countdown > 0" :loading="sending" @click="sendCode">
                  {{ countdown > 0 ? countdown + ' 秒后重发' : '获取验证码' }}
                </el-button>
              </div>
            </el-form-item>

            <div v-if="mockTip" class="mock-tip">{{ mockTip }}</div>

            <div class="grid-2">
              <el-form-item label="密码" prop="password">
                <el-input v-model="form.password" type="password" placeholder="至少 6 位" show-password />
              </el-form-item>
              <el-form-item label="确认密码" prop="confirm">
                <el-input v-model="form.confirm" type="password" placeholder="再输一次" show-password />
              </el-form-item>
            </div>

            <el-button type="primary" class="submit-btn" :loading="loading" @click="submit">注 册</el-button>
          </el-form>

          <div class="form-foot">
            已有账号？
            <a class="link" @click="goLogin">返回登录</a>
            <span class="sep">·</span>
            注册即表示同意遵守自习室使用规定
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { register, sendSmsCode } from '@/api'
import { useClockStore } from '@/stores/clock'

const router = useRouter()
const clock = useClockStore()

const formRef = ref(null)
const loading = ref(false)
const sending = ref(false)
const countdown = ref(0)
const mockTip = ref('')
let timer = null

const form = reactive({
  username: '',
  realName: '',
  college: '',
  major: '',
  phone: '',
  smsCode: '',
  password: '',
  confirm: ''
})

const validateConfirm = (rule, value, callback) => {
  if (!value) return callback(new Error('请再次输入密码'))
  if (value !== form.password) return callback(new Error('两次输入的密码不一致'))
  callback()
}

const rules = {
  username: [
    { required: true, message: '请输入学号', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9_-]{4,20}$/, message: '4-20 位字母、数字、下划线或短横线', trigger: 'blur' }
  ],
  realName: [
    { required: true, message: '请输入姓名', trigger: 'blur' },
    { min: 2, max: 20, message: '长度 2-20 个字符', trigger: 'blur' }
  ],
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }
  ],
  smsCode: [
    { required: true, message: '请输入验证码', trigger: 'blur' },
    { pattern: /^\d{4}$/, message: '验证码为 4 位数字', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码长度 6-32 位', trigger: 'blur' }
  ],
  confirm: [{ required: true, validator: validateConfirm, trigger: 'blur' }]
}

function startCountdown(seconds) {
  countdown.value = seconds
  clearInterval(timer)
  timer = setInterval(() => {
    countdown.value -= 1
    if (countdown.value <= 0) clearInterval(timer)
  }, 1000)
}

async function sendCode() {
  if (sending.value || countdown.value > 0) return
  if (!/^1[3-9]\d{9}$/.test(form.phone)) {
    ElMessage.warning('请先填写正确的 11 位手机号')
    return
  }
  sending.value = true
  try {
    const res = await sendSmsCode(form.phone.trim(), 'REGISTER')
    const data = res.data || {}
    startCountdown(data.resendSeconds || 60)
    if (data.mock) {
      mockTip.value = data.tip || `演示模式：验证码为 ${data.code}`
      // 演示模式自动填入，方便操作
      form.smsCode = data.code || form.smsCode
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

onBeforeUnmount(() => clearInterval(timer))

onMounted(() => {
  clock.connect()
})

function goLogin() {
  router.replace('/login')
}

async function submit() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    await register({
      username: form.username.trim(),
      realName: form.realName.trim(),
      studentNo: form.username.trim(),
      college: form.college.trim(),
      major: form.major.trim(),
      phone: form.phone.trim(),
      smsCode: form.smsCode.trim(),
      password: form.password
    })
    ElMessage.success('注册成功，请使用学号登录')
    // 预填登录账号，省得再输一遍
    localStorage.setItem('ls_remember_username', form.username.trim())
    router.replace('/login')
  } catch (e) {
    const msg = e?.message || '注册失败，请稍后重试'
    ElMessage.error(msg)
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
  max-width: 980px;
  display: grid;
  grid-template-columns: 0.9fr 1.1fr;
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
  font-size: 32px;
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

/* ---------- 右侧 ---------- */
.form-side {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 34px 36px;
}

.form-card {
  width: 100%;
  max-width: 400px;
}

.form-head h3 {
  margin: 0;
  font-size: 19px;
  font-weight: 650;
  letter-spacing: -0.02em;
  color: var(--gray-900);
}

.form-head p {
  margin: 6px 0 18px;
  font-size: 12.5px;
  color: var(--gray-500);
  line-height: 1.6;
}

.req {
  color: var(--red);
}

.form-card :deep(.el-form-item) {
  margin-bottom: 12px;
}

.form-card :deep(.el-form-item__label) {
  padding-bottom: 4px;
  font-size: 12px;
  color: var(--gray-600);
  line-height: 1.4;
}

.grid-2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
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
  margin: -4px 0 10px;
  padding: 7px 10px;
  border-radius: var(--radius-sm);
  background: var(--amber-soft, #fffbeb);
  border: 1px solid var(--amber-border, #fde68a);
  color: #92400e;
  font-size: 11.5px;
  line-height: 1.6;
}

.submit-btn {
  width: 100%;
  height: 38px;
  font-size: 13.5px;
  font-weight: 600;
  letter-spacing: 0.04em;
  margin-top: 4px;
}

.form-foot {
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid var(--gray-200);
  font-size: 11.5px;
  color: var(--gray-400);
  line-height: 1.6;
}

.sep {
  margin: 0 6px;
}

.link {
  color: var(--brand);
  cursor: pointer;
}

.link:hover {
  color: var(--brand-hover);
  text-decoration: underline;
}

@media (max-width: 860px) {
  .login-shell {
    grid-template-columns: 1fr;
    max-width: 460px;
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
    padding: 26px 24px 30px;
  }
  .grid-2 {
    grid-template-columns: 1fr;
    gap: 0;
  }
}
</style>