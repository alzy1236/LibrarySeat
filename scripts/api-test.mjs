// API smoke test for LibrarySeat backend
const BASE = process.env.BASE || 'http://127.0.0.1:8080'

async function call(method, path, { token, body } = {}) {
  const headers = { 'Content-Type': 'application/json' }
  if (token) headers.Authorization = `Bearer ${token}`
  const res = await fetch(BASE + path, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body)
  })
  const text = await res.text()
  let json
  try { json = JSON.parse(text) } catch { json = { raw: text } }
  return { status: res.status, ...json }
}

function ok(cond, msg) {
  console.log(`${cond ? 'PASS' : 'FAIL'}  ${msg}`)
  if (!cond) { globalThis.FAILED = (globalThis.FAILED || 0) + 1 }
  return cond
}

const run = async () => {
  // 1. 免登录接口
  const st = await call('GET', '/api/system/server-time')
  ok(st.code === 200 && st.data.serverTime, `system/server-time -> ${st.data?.serverTime}`)
  const clock = await call('GET', '/api/system/clock')
  ok(clock.code === 200, `system/clock 签到窗口 ${clock.data?.checkInOpen}-${clock.data?.checkInDeadline} cnt=${clock.data?.canCheckIn} cout=${clock.data?.canCheckOut}`)
  const notices = await call('GET', '/api/public/notices')
  ok(notices.code === 200 && notices.data.length === 2, `public/notices -> ${notices.data?.length} 条`)

  // 2. 鉴权
  const noAuth = await call('GET', '/api/admin/statistics/overview')
  ok(noAuth.code === 401, `未登录取管理接口 -> ${noAuth.code} ${noAuth.message}`)
  const badPwd = await call('POST', '/api/auth/login', { body: { username: 'admin', password: 'wrong' } })
  ok(badPwd.code === 401, `错误密码 -> ${badPwd.code} ${badPwd.message}`)
  const roleMismatch = await call('POST', '/api/auth/login', { body: { username: 'admin', password: '123456', role: 'STUDENT' } })
  ok(roleMismatch.code === 401, `角色不匹配 -> ${roleMismatch.code} ${roleMismatch.message}`)

  // 3. 管理员登录
  const admin = await call('POST', '/api/auth/login', { body: { username: 'admin', password: '123456', role: 'ADMIN' } })
  ok(admin.code === 200 && admin.data.userInfo.role === 'ADMIN', `管理员登录 -> redirect ${admin.data?.redirect}`)
  ok(!('password' in (admin.data?.userInfo || {})), '登录返回不包含密码字段')
  const at = admin.data.token

  const info = await call('GET', '/api/auth/info', { token: at })
  ok(info.code === 200 && info.data.realName === '系统管理员', `auth/info -> ${info.data?.realName}`)

  const overview = await call('GET', '/api/admin/statistics/overview', { token: at })
  ok(overview.code === 200, `admin/statistics/overview -> 今日预约 ${overview.data?.todayReserved} 在座 ${overview.data?.todaySeated} 使用率 ${overview.data?.usageRate}%`)
  ok(overview.data.rooms.length === 5, `自习室统计 ${overview.data.rooms.length} 间`)

  const charts = await call('GET', '/api/admin/statistics/charts?days=7', { token: at })
  ok(charts.code === 200 && charts.data.trend.labels.length === 7, `charts 趋势 ${charts.data?.trend?.labels?.join(',')}`)
  ok(charts.data.roomUsage.length === 5, `charts 自习室使用率 ${charts.data.roomUsage.length}`)
  ok(charts.data.hourly.labels.length === 24, `charts 时段分布 ${charts.data.hourly.labels.length} 个时段`)

  const dist = await call('GET', '/api/admin/statistics/distribution', { token: at })
  ok(dist.code === 200, `状态分布 ${JSON.stringify(dist.data?.distribution)}`)

  const roomTrend = await call('GET', '/api/admin/statistics/room-trend?days=7', { token: at })
  ok(roomTrend.code === 200 && roomTrend.data.series.length === 5, `自习室签到趋势系列 ${roomTrend.data?.series?.length}`)

  const users = await call('GET', '/api/admin/users?pageSize=5&role=STUDENT', { token: at })
  // 演示数据初始 20 名学生，用户自助注册会增加，这里只校验"至少 20 且分页生效"
  ok(
    users.code === 200 && users.data.total >= 20 && users.data.records.length <= 5,
    `用户分页 total=${users.data?.total} 本页=${users.data?.records?.length}`
  )

  const resList = await call('GET', '/api/admin/reservations?pageSize=5', { token: at })
  ok(resList.code === 200, `预约记录分页 total=${resList.data?.total}`)

  const lt = await call('GET', '/api/admin/long-term', { token: at })
  ok(lt.code === 200, `长期固定座位 ${lt.data?.length} 个: ` + lt.data.map(x => `${x.userName}/${x.seatNo}`).join(','))

  const assess = await call('POST', '/api/admin/assessment/run?dryRun=true', { token: at })
  ok(assess.code === 200, `长期座位考核预览 ${JSON.stringify(assess.data)}`)

  const cfg = await call('GET', '/api/admin/config', { token: at })
  ok(cfg.code === 200 && cfg.data['checkin.deadline'].value === '08:30', `系统参数 checkin.deadline=${cfg.data['checkin.deadline']?.value}`)

  // 4. 学生登录
  const stu = await call('POST', '/api/auth/login', { body: { username: '20230001', password: '123456' } })
  ok(stu.code === 200 && stu.data.userInfo.role === 'STUDENT', `学生登录 -> redirect ${stu.data?.redirect}`)
  const stt = stu.data.token

  const forbidden = await call('GET', '/api/admin/statistics/overview', { token: stt })
  ok(forbidden.code === 403, `学生访问管理接口 -> ${forbidden.code} ${forbidden.message}`)

  const today = await call('GET', '/api/student/today', { token: stt })
  ok(today.code === 200, `学生今日预约 -> ${today.data?.statusName} ${today.data?.seatNo} ${today.data?.roomName}`)

  const rooms = await call('GET', '/api/student/rooms', { token: stt })
  ok(rooms.code === 200, `学生自习室列表 ${rooms.data?.length} 间`)

  const room3 = await call('GET', '/api/student/rooms/3', { token: stt })
  ok(room3.code === 200, `短期自习室座位图 ${room3.data?.seats?.length} 个, 可用 ${room3.data?.available}`)
  console.log('   座位状态样例:', room3.data.seats.slice(0, 8).map(s => `${s.seatNo}:${s.seatState}`).join(' '))

  const room1 = await call('GET', '/api/student/rooms/1', { token: stt })
  console.log('   长期自习室座位样例:', room1.data.seats.slice(0, 6).map(s => `${s.seatNo}:${s.seatState}${s.longTermOwner ? '(' + s.longTermOwner + ')' : ''}`).join(' '))

  const mine = await call('GET', '/api/student/long-term/mine', { token: stt })
  ok(mine.code === 200, `我的长期座位 -> ${mine.data?.binding?.seatNo || "(无)"} 本月签到 ${mine.data?.calendar?.checkInDays || 0} 天`)

  const clock2 = await call('GET', '/api/student/clock', { token: stt })
  ok(clock2.code === 200, `学生实时时钟 -> ${clock2.data?.serverTime} 签到窗口 ${clock2.data?.checkInOpen}-${clock2.data?.checkInDeadline}`)
  const activeToday = await call('GET', '/api/student/today/active', { token: stt })
  const todayAll = await call('GET', '/api/student/today', { token: stt })
  ok(todayAll.code === 200, `今日预约(含已结束记录) -> ${todayAll.data ? todayAll.data.roomName + ' ' + todayAll.data.seatNo + ' ' + todayAll.data.statusName : '无'}`)
  ok(activeToday.code === 200, `今日生效预约 -> ${activeToday.data ? activeToday.data.statusName : '无'}`)

  // 5. 选座规则
  const res30 = await call('GET', '/api/student/reservations?pageSize=5', { token: stt })
  ok(res30.code === 200, `学生选座记录 total=${res30.data?.total}`)

  // 今天已有预约 -> 再选座应失败
  const dup = await call('POST', '/api/student/reserve?seatId=10', { token: stt })
  console.log(`   重复选座 -> ${dup.code} ${dup.message}`)

  // 用没有预约的学生测试选座（选择普通座位层，避免受到固定座位层规则影响）
  const s2 = await call('POST', '/api/auth/login', { body: { username: '20230015', password: '123456' } })
  const t15 = s2.data.token
  const t15today = await call('GET', '/api/student/today/active', { token: t15 })
  console.log(`   20230015 今日生效预约: ${t15today.data ? t15today.data.statusName : '无'}`)
  if (!t15today.data) {
    const normal = await call('GET', '/api/student/rooms/1', { token: t15 })
    const pick = normal.data.seats.find((s) => s.seatState === 'AVAILABLE')
    const r = await call('POST', `/api/student/reserve?seatId=${pick.id}`, { token: t15 })
    ok(r.code === 200, `选座 ${pick.seatNo}（1 层普通座位）-> ${r.code} ${r.message} 状态=${r.data?.status}`)
    const dup2 = await call('POST', `/api/student/reserve?seatId=${pick.id + 1}`, { token: t15 })
    ok(dup2.code !== 200, `同一学生二次选座被拦截 -> ${dup2.message}`)
    if (r.code === 200) {
      const cancel = await call('POST', `/api/student/cancel?reservationId=${r.data.id}`, { token: t15 })
      ok(cancel.code === 200, `取消预约 -> ${cancel.message}`)
    }
  }

  // 6. 签到签退窗口
  if (today.data) {
    const ci = await call('POST', `/api/student/checkin?reservationId=${today.data.id}`, { token: stt })
    console.log(`   重复签到 -> ${ci.code} ${ci.message}`)
    const co = await call('POST', `/api/student/checkout?reservationId=${today.data.id}`, { token: stt })
    console.log(`   签退(当前 ${clock2.data.time}) -> ${co.code} ${co.message}`)
  }

  console.log(`\nAPI 测试完成, 失败项: ${globalThis.FAILED || 0}`)
  if (globalThis.FAILED) process.exitCode = 2
}

run().catch(e => { console.error('TEST ERROR', e); process.exit(1) })


