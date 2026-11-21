// 签到点（地理围栏）接口测试
const BASE = 'http://127.0.0.1:8080/api'

let pass = 0
let fail = 0
function ok(cond, msg) {
  if (cond) {
    pass++
    console.log('PASS ', msg)
  } else {
    fail++
    console.log('FAIL ', msg)
  }
}

async function api(path, { method = 'GET', token, body } = {}) {
  const headers = { 'Content-Type': 'application/json' }
  if (token) headers.Authorization = `Bearer ${token}`
  const res = await fetch(BASE + path, {
    method,
    headers,
    body: body ? JSON.stringify(body) : undefined
  })
  return res.json()
}

const login = async (username, password = '123456') => {
  const r = await api('/auth/login', { method: 'POST', body: { username, password } })
  return r.data?.token
}

const run = async () => {
  const adm = await login('admin')
  ok(!!adm, '管理员登录')
  if (!adm) return

  // 1. 修正被 ascii 写坏的中文名称
  const list0 = await api('/admin/locations', { token: adm })
  const broken = (list0.data || []).find((l) => l.roomId === 3)
  if (broken && broken.name.includes('?')) {
    const fix = await api('/admin/locations', {
      method: 'PUT',
      token: adm,
      body: { ...broken, name: '三层固定座位区签到点' }
    })
    ok(fix.code === 200 && fix.data.name === '三层固定座位区签到点', `修复中文名称 -> ${fix.data?.name}`)
  }

  // 2. 学生预约一个长期自习室座位（房间 3）
  const stu = await login('20230001')
  ok(!!stu, '学生 20230001 登录')

  const rooms = await api('/student/rooms?roomType=LONG', { token: stu })
  const room3 = (rooms.data || []).find((r) => r.id === 3)
  ok(!!room3, '找到 3 层长期自习室')

  let reservation = (await api('/student/today', { token: stu })).data
  if (!reservation) {
    const detail = await api('/student/rooms/3', { token: stu })
    const seats = detail.data?.seats || []
    // 长期自习室只能选自己审核通过的固定座位（20230001 -> R2-10 / seat 49）
    const seat =
      seats.find((s) => s.id === 49) ||
      seats.find((s) => s.status === 1 && !s.reserved)
    ok(!!seat, `找到可选座位 ${seat?.seatNo || seat?.id || ''}`)
    if (seat) {
      const rv = await api(`/student/reserve?seatId=${seat.id}`, { method: 'POST', token: stu })
      ok(rv.code === 200, `预约座位 -> ${rv.message}`)
      reservation = rv.data
    }
  }
  ok(!!reservation, `今日预约存在 id=${reservation?.id} 状态=${reservation?.status}`)
  if (!reservation) return

  // 3. 学生端读取签到点
  const loc = await api('/student/checkin-location', { token: stu })
  ok(loc.code === 200 && loc.data && loc.data.roomId === 3, `学生读取到房间专属签到点：${loc.data?.name} 半径 ${loc.data?.radius}m`)

  const en = await api('/student/checkin-location/enabled', { token: stu })
  ok(en.data === true, '定位校验开关为启用')

  // 4. 围栏校验：无坐标
  const r1 = await api(`/student/checkin?reservationId=${reservation.id}`, { method: 'POST', token: stu })
  ok(r1.code === 120, `无定位被拒绝 -> ${r1.code} ${r1.message}`)

  // 5. 围栏校验：远处坐标（约 9 公里外）
  const r2 = await api(
    `/student/checkin?reservationId=${reservation.id}&latitude=39.9900&longitude=116.4700`,
    { method: 'POST', token: stu }
  )
  ok(r2.code === 122 && /超出允许范围/.test(r2.message || ''), `远处被拒绝 -> ${r2.code} ${r2.message}`)

  // 6. 围栏校验：范围内坐标（签到点附近 10 米）
  const r3 = await api(
    `/student/checkin?reservationId=${reservation.id}&latitude=39.90451&longitude=116.40781`,
    { method: 'POST', token: stu }
  )
  ok(r3.code === 200, `范围内签到成功 -> ${r3.code} ${r3.message}`)

  console.log(`\n完成：${pass} 通过 / ${fail} 失败`)
}

run().catch((e) => {
  console.error('ERROR', e)
  process.exit(1)
})