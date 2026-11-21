// 短信验证码：注册 + 忘记密码重置 接口测试
const BASE = 'http://127.0.0.1:8080/api'

let pass = 0
let fail = 0
const ok = (c, m) => {
  if (c) {
    pass++
    console.log('PASS ', m)
  } else {
    fail++
    console.log('FAIL ', m)
  }
}

async function api(path, { method = 'GET', body, token } = {}) {
  const headers = { 'Content-Type': 'application/json' }
  if (token) headers.Authorization = `Bearer ${token}`
  const res = await fetch(BASE + path, { method, headers, body: body ? JSON.stringify(body) : undefined })
  return res.json()
}

const run = async () => {
  const stamp = String(Date.now()).slice(-6)
  const phone = '139' + String(Date.now()).slice(-8)   // 11 位
  const username = 'test' + stamp

  // ---------- 注册 ----------
  const s1 = await api('/auth/sms-code', { method: 'POST', body: { phone, scene: 'REGISTER' } })
  ok(s1.code === 200 && s1.data?.mock === true, `注册验证码已生成（演示模式）-> ${s1.data?.code}`)
  const code1 = s1.data?.code
  ok(/^\d{4}$/.test(code1 || ''), `验证码为 4 位数字 -> ${code1}`)

  // 60 秒内重复发送被拦截
  const s1b = await api('/auth/sms-code', { method: 'POST', body: { phone, scene: 'REGISTER' } })
  ok(s1b.code !== 200 && /秒后再重新获取/.test(s1b.message || ''), `重发冷却生效 -> ${s1b.message}`)

  // 错验证码
  const badCode = code1 === '0000' ? '1111' : '0000'
  const r0 = await api('/auth/register', {
    method: 'POST',
    body: { username, realName: '测试同学', password: '123456', phone, smsCode: badCode }
  })
  ok(r0.code !== 200 && /验证码不正确/.test(r0.message || ''), `错误验证码被拒绝 -> ${r0.message}`)

  // 正确验证码注册
  const r1 = await api('/auth/register', {
    method: 'POST',
    body: { username, realName: '测试同学', password: '123456', phone, smsCode: code1 }
  })
  ok(r1.code === 200, `注册成功 -> ${r1.message}`)

  // 同一手机号再注册被拒
  const r2 = await api('/auth/register', {
    method: 'POST',
    body: { username: username + 'x', realName: '测试同学2', password: '123456', phone, smsCode: code1 }
  })
  ok(r2.code !== 200, `手机号重复注册被拒 -> ${r2.message}`)

  // 新账号登录
  const l1 = await api('/auth/login', { method: 'POST', body: { username, password: '123456' } })
  ok(l1.code === 200 && !!l1.data?.token, `新账号可登录 -> ${l1.data?.userInfo?.realName}`)

  // 验证码不可重复使用（同一手机号注册场景 60 秒内冷却，且已注册）
  const r3 = await api('/auth/register', {
    method: 'POST',
    body: { username: username + 'y', realName: '测试3', password: '123456', phone, smsCode: code1 }
  })
  ok(r3.code !== 200, `重复注册被拒 -> ${r3.message}`)

  // ---------- 忘记密码 ----------
  // 未注册手机号重置 -> 明确拒绝
  const u1 = await api('/auth/sms-code', { method: 'POST', body: { phone: '13700000000', scene: 'RESET' } })
  ok(u1.code !== 200 && /未绑定/.test(u1.message || ''), `未绑定手机号被拒 -> ${u1.message}`)

  const s2 = await api('/auth/sms-code', { method: 'POST', body: { phone, scene: 'RESET' } })
  ok(s2.code === 200 && s2.data?.mock === true, `重置验证码已生成 -> ${s2.data?.code}`)
  const code2 = s2.data?.code

  // 新密码太短
  const p0 = await api('/auth/reset-password', { method: 'POST', body: { phone, code: code2, newPassword: '123' } })
  ok(p0.code !== 200 && /至少 6 位/.test(p0.message || ''), `弱密码被拒 -> ${p0.message}`)

  // 正常重置
  const p1 = await api('/auth/reset-password', { method: 'POST', body: { phone, code: code2, newPassword: 'newpass888' } })
  ok(p1.code === 200, `密码重置成功 -> ${p1.message}`)

  // 旧密码登录失败
  const l2 = await api('/auth/login', { method: 'POST', body: { username, password: '123456' } })
  ok(l2.code !== 200, `旧密码已失效 -> ${l2.message}`)

  // 新密码登录成功
  const l3 = await api('/auth/login', { method: 'POST', body: { username, password: 'newpass888' } })
  ok(l3.code === 200 && !!l3.data?.token, `新密码可登录 -> 成功`)

  // 重置验证码只能用一次：重复使用应报"请先获取验证码"
  const p2 = await api('/auth/reset-password', { method: 'POST', body: { phone, code: code2, newPassword: 'another999' } })
  ok(p2.code !== 200 && /请先获取验证码/.test(p2.message || ''), `验证码一次性生效 -> ${p2.message}`)

  // 注册场景与重置场景互不影响：注册场景刚用过，重置场景仍可独立发送
  const s3 = await api('/auth/sms-code', { method: 'POST', body: { phone, scene: 'REGISTER' } })
  ok(s3.code !== 200 && /秒后再重新获取/.test(s3.message || ''), `注册场景仍处于 60 秒冷却 -> ${s3.message}`)

  // ---------- 演示账号也支持重置 ----------
  // 注：重复运行测试时可能处于 60 秒冷却期，两种结果都算正常
  const s4 = await api('/auth/sms-code', { method: 'POST', body: { phone: '13800000001', scene: 'RESET' } })
  ok(
    s4.code === 200 || /秒后再重新获取/.test(s4.message || ''),
    `演示学生 20230001 可用手机号找回密码 -> ${s4.code === 200 ? '验证码 ' + s4.data?.code : s4.message}`
  )

  console.log(`\n结果：${pass} 通过 / ${fail} 失败`)
  console.log('测试账号：' + username + ' 手机号：' + phone)
}

run().catch((e) => {
  console.error('ERROR', e)
  process.exit(1)
})