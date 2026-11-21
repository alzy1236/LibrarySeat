// 验证短信模块已彻底移除 + 原有注册/登录流程正常
const BASE = 'http://127.0.0.1:8080/api'

let pass = 0, fail = 0
const ok = (c, m) => { if (c) { pass++; console.log('PASS ', m) } else { fail++; console.log('FAIL ', m) } }

const api = async (path, { method = 'GET', body } = {}) => {
  const res = await fetch(BASE + path, {
    method,
    headers: { 'Content-Type': 'application/json' },
    body: body ? JSON.stringify(body) : undefined
  })
  let json = null
  try { json = await res.json() } catch (e) {}
  return { status: res.status, json }
}

const run = async () => {
  // 先拿管理员 token：带上 token 才能越过登录拦截器，验证路由本身是否还存在
  const adm = (await api('/auth/login', { method: 'POST', body: { username: 'admin', password: '123456' } })).json.data.token
  const authPost = async (path, body) => {
    const res = await fetch(BASE + path, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + adm },
      body: JSON.stringify(body)
    })
    let json = null
    try { json = await res.json() } catch (e) {}
    return { status: res.status, json }
  }

  // 1. 短信接口应已不存在（带 token 请求 -> 404，说明路由被删除）
  const s1 = await authPost('/auth/sms-code', { phone: '13800000001', scene: 'REGISTER' })
  ok(s1.status === 404 || s1.json?.code === 404,
    `发送验证码接口已移除 -> HTTP ${s1.status} ${s1.json?.message || ''}`)

  const s2 = await authPost('/auth/reset-password', { phone: '13800000001', code: '1234', newPassword: 'abc123456' })
  ok(s2.status === 404 || s2.json?.code === 404,
    `重置密码接口已移除 -> HTTP ${s2.status} ${s2.json?.message || ''}`)

  // 未带 token 时应被登录拦截器拦下（不再是公开接口）
  const s1b = await api('/auth/sms-code', { method: 'POST', body: { phone: '13800000001', scene: 'REGISTER' } })
  ok(s1b.json?.code === 401, `未登录访问返回 401（已移出白名单） -> ${s1b.json?.message}`)

  // 2. 注册恢复为「无需验证码」
  const username = 'plain' + String(Date.now()).slice(-6)
  const r1 = await api('/auth/register', {
    method: 'POST',
    body: { username, realName: '普通注册', password: '123456' }
  })
  ok(r1.json?.code === 200, `不带手机号/验证码也能注册 -> ${r1.json?.message}`)

  // 3. 新账号可登录
  const l1 = await api('/auth/login', { method: 'POST', body: { username, password: '123456' } })
  ok(l1.json?.code === 200 && !!l1.json?.data?.token, `新账号可登录 -> ${l1.json?.data?.userInfo?.realName}`)

  // 4. 演示账号仍正常
  const l2 = await api('/auth/login', { method: 'POST', body: { username: '20230001', password: '123456' } })
  ok(l2.json?.code === 200, `演示学生登录正常 -> ${l2.json?.data?.userInfo?.realName}`)

  // 5. 后台短信测试接口已移除
  const t = await fetch(BASE + '/admin/sms/test', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + adm },
    body: JSON.stringify({ phone: '13800000001' })
  })
  ok(t.status === 404 || t.status === 405, `后台短信测试接口已移除 -> HTTP ${t.status}`)

  // 6. 系统参数里不应再有 sms.*
  const cfg = await (await fetch(BASE + '/admin/configs', { headers: { Authorization: 'Bearer ' + adm } })).json()
  const smsKeys = (cfg.data || []).filter((c) => c.configKey.startsWith('sms.'))
  ok(smsKeys.length === 0, `配置项已无 sms.*（剩余 ${smsKeys.length} 条）`)

  console.log(`\n结果：${pass} 通过 / ${fail} 失败`)
  console.log('清理测试账号：' + username)
}

run().catch((e) => { console.error('ERROR', e); process.exit(1) })