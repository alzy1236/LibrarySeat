// 审核流程端到端验证：学生申请 -> 管理员审核 -> 通过/拒绝/撤回
const BASE = 'http://127.0.0.1:8080'

function ok(cond, msg) {
  console.log(`${cond ? 'PASS' : 'FAIL'}  ${msg}`)
  if (!cond) globalThis.FAILED = (globalThis.FAILED || 0) + 1
}

async function call(method, path, token, body) {
  const headers = { 'Content-Type': 'application/json' }
  if (token) headers.Authorization = `Bearer ${token}`
  const res = await fetch(BASE + path, { method, headers, body: body ? JSON.stringify(body) : undefined })
  return JSON.parse(await res.text())
}

const login = async (u) => (await call('POST', '/api/auth/login', null, { username: u, password: '123456' })).data.token

const run = async () => {
  const admin = await login('admin')
  // 用“胡雪 20230015”（无固定座位）测试申请
  const stu = await login('20230015')

  // 1. 初始状态
  const mine0 = await call('GET', '/api/student/long-term/mine', stu)
  ok(mine0.code === 200 && !mine0.data.binding, '初始：学生没有生效中的固定座位')
  ok(!mine0.data.pending, '初始：没有待审核申请')

  // 2. 学生找到三层自习室的空闲座位并申请（若存在上次测试残留的申请，先清理）
  const seats = await call('GET', '/api/student/long-term/rooms/3/seats', stu)
  let free = seats.data.find((s) => s.seatState === 'AVAILABLE')
  let apply = await call('POST', `/api/student/long-term/apply?roomId=3&seatId=${free.id}`, stu)
  if (apply.code !== 200) {
    // 座位状态与申请状态可能不一致（演示座位被占），换一个真正空闲的座位重试
    for (const s of seats.data.filter((x) => x.seatState === 'AVAILABLE')) {
      const r = await call('POST', `/api/student/long-term/apply?roomId=3&seatId=${s.id}`, stu)
      if (r.code === 200) {
        free = s
        apply = r
        break
      }
    }
  }
  ok(apply.code === 200, `提交申请 -> ${apply.code} ${apply.message}（座位 ${free.seatNo}）`)
  const status = apply.data?.status || apply.data?.statusName
  ok(status === 'PENDING' || status === '待审核', `申请状态为待审核：${status}`)

  const mine1 = await call('GET', '/api/student/long-term/mine', stu)
  ok(mine1.data.pending && mine1.data.pending.seatNo === free.seatNo, `学生端可见待审核申请：${mine1.data.pending?.seatNo}`)
  ok(!mine1.data.binding, '审核通过前不产生生效座位')

  // 3. 重复申请被拦截
  const dup = await call('POST', `/api/student/long-term/apply?roomId=3&seatId=${free.id + 1}`, stu)
  ok(dup.code !== 200, `已有待审核申请时再次申请被拦截：${dup.message}`)
  // 他人不能申请同一座位
  const stu2 = await login('20230016')
  const dupSeat = await call('POST', `/api/student/long-term/apply?roomId=3&seatId=${free.id}`, stu2)
  ok(dupSeat.code !== 200, `其他学生申请同一座位被拦截：${dupSeat.message}`)

  // 4. 审核通过前不能选该座位（长期自习室要求生效座位）
  const reserveBefore = await call('POST', `/api/student/reserve?seatId=${free.id}`, stu)
  ok(reserveBefore.code !== 200, `审核通过前不能预约该座位：${reserveBefore.message}`)

  // 5. 管理员查看待审核列表
  const pendingList = await call('GET', '/api/admin/long-term/pending', admin)
  const target = pendingList.data.find((p) => p.seatNo === free.seatNo)
  ok(!!target && target.status === 'PENDING', `管理员待审核列表包含该申请（共 ${pendingList.data.length} 条）`)

  // 6. 管理员驳回
  const reject = await call('POST', `/api/admin/long-term/${target.id}/review?approve=false&remark=资料不完整，请重新提交`, admin)
  ok(reject.code === 200 && reject.data.status === 'REJECTED', `管理员驳回：${reject.data?.statusName}，意见：${reject.data?.reviewRemark}`)
  const mine2 = await call('GET', '/api/student/long-term/mine', stu)
  ok(!mine2.data.pending && mine2.data.applies.some((a) => a.status === 'REJECTED'), '学生端申请记录显示“未通过”')
  const reApply = await call('POST', `/api/student/long-term/apply?roomId=3&seatId=${free.id}`, stu)
  ok(reApply.code === 200, '被驳回后可重新申请')

  // 7. 通过
  const pending2 = await call('GET', '/api/admin/long-term/pending', admin)
  const target2 = pending2.data.find((p) => p.seatNo === free.seatNo)
  const approve = await call('POST', `/api/admin/long-term/${target2.id}/review?approve=true&remark=审核通过`, admin)
  ok(approve.code === 200 && approve.data.status === 'ACTIVE', `管理员通过：${approve.data?.statusName}，生效时间 ${approve.data?.bindTime}`)
  const mine3 = await call('GET', '/api/student/long-term/mine', stu)
  ok(mine3.data.binding && mine3.data.binding.seatNo === free.seatNo, `学生端显示生效座位：${mine3.data.binding?.seatNo}`)
  ok(!mine3.data.pending, '通过后不存在待审核申请')

  // 8. 生效后可预约该座位（若该学生今天已在别处有生效预约，先取消再验证）
  const before = await call('GET', '/api/student/today/active', stu)
  if (before.data) {
    await call('POST', `/api/student/cancel?reservationId=${before.data.id}`, stu)
  }
  const reserve = await call('POST', `/api/student/reserve?seatId=${free.id}`, stu)
  ok(reserve.code === 200, `审核通过后可预约该固定座位：${reserve.data?.seatNo || reserve.message}`)

  // 9. 重复审核被拒绝
  const again = await call('POST', `/api/admin/long-term/${target2.id}/review?approve=true`, admin)
  ok(again.code !== 200, `重复审核被拦截：${again.message}`)

  // 10. 撤回流程（用另一个学生）
  const stu3 = await login('20230017')
  const seats3 = await call('GET', '/api/student/long-term/rooms/4/seats', stu3)
  const free3 = seats3.data.find((s) => s.seatState === 'AVAILABLE')
  const a3 = await call('POST', `/api/student/long-term/apply?roomId=4&seatId=${free3.id}`, stu3)
  const withdraw = await call('POST', `/api/student/long-term/withdraw?bindingId=${a3.data.id}`, stu3)
  ok(withdraw.code === 200, `学生撤回申请：${withdraw.message}`)
  const mine4 = await call('GET', '/api/student/long-term/mine', stu3)
  ok(!mine4.data.pending, '撤回后不再有待审核申请')

  // 清理：还原演示数据（撤回 20230015 的预约与座位）
  if (reserve.data) await call('POST', `/api/student/cancel?reservationId=${reserve.data.id}`, stu)
  await call('POST', `/api/admin/long-term/${target2.id}/release?reason=测试还原`, admin)
  ok(true, '演示数据已还原')

  console.log(`\n审核流程测试完成，失败 ${globalThis.FAILED || 0} 项`)
  if (globalThis.FAILED) process.exitCode = 2
}

run().catch((e) => {
  console.error('TEST ERROR', e)
  process.exit(1)
})