// 业务规则端到端验证：长期座位 20 天考核 + 签到/签退窗口
// 使用：node scripts/rule-test.mjs
import { execFileSync } from 'node:child_process'

const MYSQL = 'C:/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe'
const BASE = 'http://127.0.0.1:8080'

function ok(cond, msg) {
  console.log(`${cond ? 'PASS' : 'FAIL'}  ${msg}`)
  if (!cond) globalThis.FAILED = (globalThis.FAILED || 0) + 1
}

function sql(statement) {
  return execFileSync(
    MYSQL,
    ['-uroot', '--password=123456', '-h', '127.0.0.1', '-P3306', '--default-character-set=utf8mb4', '-N', '-e', `use library_seat; ${statement}`],
    { encoding: 'utf8', stdio: ['pipe', 'pipe', 'ignore'] }
  ).trim()
}

async function call(method, path, token, body) {
  const headers = { 'Content-Type': 'application/json' }
  if (token) headers.Authorization = `Bearer ${token}`
  const res = await fetch(BASE + path, { method, headers, body: body ? JSON.stringify(body) : undefined })
  return JSON.parse(await res.text())
}

const login = async (username) => (await call('POST', '/api/auth/login', null, { username, password: '123456' })).data.token

const now = new Date()
const prev = new Date(now.getFullYear(), now.getMonth() - 1, 1)
const month = `${prev.getFullYear()}-${String(prev.getMonth() + 1).padStart(2, '0')}`
const day = (d) => `${month}-${String(d).padStart(2, '0')}`
const uid = 2 // 20230001

const run = async () => {
  const adminToken = await login('admin')
  const studentToken = await login('20230001')

  const bind = sql(`select id, seat_id, room_id, ifnull(bind_time,'') from long_term_seat where user_id=${uid} and status='ACTIVE' limit 1`).split('\t')
  ok(bind.length === 4 && bind[0], `学生 20230001 当前固定座位绑定 id=${bind[0]} seat=${bind[1]} 申请时间=${bind[3]}`)
  if (!bind[0]) {
    console.error('缺少生效中的固定座位（可能需要先执行 start.bat init 重新导入演示数据）')
    process.exit(3)
  }
  const originBindTime = bind[3]

  // 回拨申请时间：模拟“上个月起就一直持有固定座位”的老用户
  sql(`update long_term_seat set bind_time = '${day(1)} 00:00:00' where id=${bind[0]}`)
  sql(`delete from seat_assessment where assess_month='${month}'`)
  sql(`delete from seat_reservation where user_id=${uid} and reserve_date between '${day(1)}' and '${month}-31'`)

  // 情况一：25 天只有签到、没有签退 -> 不合格（有效出勤 0 天）
  const rows = []
  for (let d = 1; d <= 25; d++) {
    const dt = day(d)
    rows.push(`(${uid}, ${bind[2]}, ${bind[1]}, '${dt}', 'LEFT', '${dt} 08:10:00', NULL, 0, NULL, '${dt} 07:50:00', '${dt} 08:10:00')`)
  }
  sql(`insert into seat_reservation (user_id, room_id, seat_id, reserve_date, status, check_in_time, check_out_time, overstay, cancel_reason, create_time, update_time) values ${rows.join(',')}`)

  const dry1 = await call('POST', `/api/admin/assessment/run?month=${month}&dryRun=true`, adminToken)
  ok(dry1.code === 200 && dry1.data.unqualified === 1 && dry1.data.qualified === 0,
    `规则一：仅签到不签退 25 天 -> 不合格 ${dry1.data?.unqualified} 人（有效出勤为 0 天）`)
  ok(dry1.data.cancelled === 0, '预览模式不会取消座位')

  // 情况二：补足 22 天签退 -> 合格
  sql(`update seat_reservation set check_out_time = concat(reserve_date, ' 21:30:00') where user_id=${uid} and reserve_date between '${day(1)}' and '${day(22)}'`)
  const dry2 = await call('POST', `/api/admin/assessment/run?month=${month}&dryRun=true`, adminToken)
  ok(dry2.data.qualified === 1 && dry2.data.unqualified === 0, `规则二：签到且签退 22 天 -> 合格 ${dry2.data?.qualified} 人`)
  const stillActive = sql(`select status from long_term_seat where id=${bind[0]}`)
  ok(stillActive === 'ACTIVE', '预览不修改数据，固定座位仍为 ACTIVE')

  // 情况三：签退只有 19 天 -> 执行考核后自动取消座位
  sql(`update seat_reservation set check_out_time = NULL where user_id=${uid} and reserve_date between '${day(20)}' and '${day(22)}'`)
  const run3 = await call('POST', `/api/admin/assessment/run?month=${month}&dryRun=false`, adminToken)
  ok(run3.data.cancelled === 1, `规则三：签到且签退 19 天 < 20 天 -> 取消 ${run3.data?.cancelled} 个固定座位`)
  const status = sql(`select status, ifnull(cancel_reason,'') from long_term_seat where id=${bind[0]}`).split('\t')
  ok(status[0] === 'CANCELLED', `固定座位状态 -> ${status[0]}，原因：${status[1]}`)
  const after = await call('GET', '/api/admin/long-term?keyword=20230001', adminToken)
  ok(after.data.length === 0, '被取消的学生不再出现在使用中的固定座位列表')

  const mine = await call('GET', '/api/student/long-term/assessments', studentToken)
  const rec = (mine.data?.records || []).find((r) => r.assessMonth === month)
  ok(!!rec && rec.qualified === 0 && rec.cancelledBinding === 1,
    `学生端考核记录：${month} 签到 ${rec?.checkinDays} 天 / 签退 ${rec?.checkoutDays} 天 / 不合格 / 已取消座位`)

  // 演示数据还原
  sql(`update long_term_seat set status='ACTIVE', cancel_time=NULL, cancel_reason=NULL, bind_time='${originBindTime}' where id=${bind[0]}`)
  sql(`delete from seat_assessment where assess_month='${month}'`)
  sql(`delete from seat_reservation where user_id=${uid} and reserve_date between '${day(1)}' and '${month}-31'`)
  const restored = sql(`select status, bind_time from long_term_seat where id=${bind[0]}`)
  ok(restored.startsWith('ACTIVE'), `演示数据已还原：${restored.replace('\t', ' / ')}`)

  // 签到 / 签退窗口判定
  const clock = await call('GET', '/api/system/clock')
  const d = clock.data
  const nowHM = d.time.slice(0, 5)
  ok(d.canCheckIn === (nowHM >= d.checkInOpen && nowHM <= d.checkInDeadline),
    `签到窗口判定：当前 ${nowHM}，窗口 ${d.checkInOpen}-${d.checkInDeadline}，可签到=${d.canCheckIn}`)
  ok(d.canCheckOut === (nowHM >= d.checkOutStart && nowHM <= d.checkOutEnd),
    `签退窗口判定：窗口 ${d.checkOutStart}-${d.checkOutEnd}，可签退=${d.canCheckOut}`)

  console.log(`\n业务规则测试完成，失败 ${globalThis.FAILED || 0} 项`)
  if (globalThis.FAILED) process.exitCode = 2
}

run().catch((e) => {
  console.error('TEST ERROR', e)
  process.exit(1)
})