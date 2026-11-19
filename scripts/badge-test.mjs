// 验证：审核页点「通过 / 拒绝」后，侧边栏红点实时减一
import { spawn, execFileSync } from 'node:child_process'
import { rmSync } from 'node:fs'
import { setTimeout as sleep } from 'node:timers/promises'

const MYSQL = 'C:/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe'
const EDGE = 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'
const PROFILE = 'C:/Users/Administrator/AppData/Local/Temp/ls-edge-badge'
const PORT = 9381
const BASE = 'http://127.0.0.1:8080'

function sql(statement) {
  return execFileSync(
    MYSQL,
    ['-uroot', '--password=123456', '-h', '127.0.0.1', '-P3306', '--default-character-set=utf8mb4', '-N', '-e', `use library_seat; ${statement}`],
    { encoding: 'utf8', stdio: ['pipe', 'pipe', 'ignore'] }
  ).trim()
}

/**
 * 让待审核申请至少有 2 条（保证测试可重复执行）
 * 若不足，把已审核过的申请重置为待审核状态
 */
function ensurePending() {
  let count = Number(sql("select count(*) from long_term_seat where status='PENDING'") || 0)
  if (count >= 2) return count
  const need = 2 - count
  sql(`update long_term_seat set status='PENDING', bind_time=null, review_time=null, review_remark=null
       where status in ('REJECTED','CANCELLED','ACTIVE') order by id limit ${need}`)
  return Number(sql("select count(*) from long_term_seat where status='PENDING'") || 0)
}

function ok(c, m) {
  console.log(`${c ? 'PASS' : 'FAIL'}  ${m}`)
  if (!c) globalThis.FAILED = (globalThis.FAILED || 0) + 1
}

class CDP {
  constructor(url) {
    this.ws = new WebSocket(url)
    this.id = 0
    this.pending = new Map()
    this.ws.onmessage = (e) => {
      const msg = JSON.parse(e.data)
      if (msg.id && this.pending.has(msg.id)) {
        const { resolve, reject } = this.pending.get(msg.id)
        this.pending.delete(msg.id)
        msg.error ? reject(new Error(JSON.stringify(msg.error))) : resolve(msg.result)
      }
    }
  }
  ready() { return new Promise((res, rej) => { this.ws.onopen = res; this.ws.onerror = rej }) }
  send(method, params = {}) {
    const id = ++this.id
    return new Promise((res, rej) => { this.pending.set(id, { resolve: res, reject: rej }); this.ws.send(JSON.stringify({ id, method, params })) })
  }
  async eval(expr) {
    const r = await this.send('Runtime.evaluate', { expression: expr, awaitPromise: true, returnByValue: true })
    if (r.exceptionDetails) return 'EXC: ' + (r.exceptionDetails.exception?.description || r.exceptionDetails.text)
    return r.result.value
  }
}

/** 读取侧边栏红点数字（无红点返回 0） */
const badgeNum = (cdp) =>
  cdp.eval(`(() => {
    const el = document.querySelector('.badge')
    if (!el) return 0
    return Number(el.innerText.trim()) || 0
  })()`)

const run = async () => {
  const seeded = ensurePending()
  console.log(`      已准备 ${seeded} 条待审核申请（保证测试可重复）`)
  rmSync(PROFILE, { recursive: true, force: true })
  const edge = spawn(EDGE, ['--headless=new', `--remote-debugging-port=${PORT}`, `--user-data-dir=${PROFILE}`, '--no-first-run', '--disable-gpu', '--window-size=1440,900', 'about:blank'], { stdio: 'ignore' })
  let wsUrl = null
  for (let i = 0; i < 40; i++) {
    try {
      const list = await (await fetch(`http://127.0.0.1:${PORT}/json/list`)).json()
      const page = list.find((t) => t.type === 'page')
      if (page) { wsUrl = page.webSocketDebuggerUrl; break }
    } catch (e) {}
    await sleep(250)
  }
  const cdp = new CDP(wsUrl)
  await cdp.ready()
  await cdp.send('Page.enable')
  await cdp.send('Runtime.enable')
  try {
    // 登录管理员
    await cdp.send('Page.navigate', { url: BASE + '/login' })
    await sleep(2600)
    await cdp.eval(`(() => {
      const inputs = document.querySelectorAll('.form-card input')
      inputs[0].value = 'admin'; inputs[0].dispatchEvent(new Event('input', { bubbles: true }))
      inputs[1].value = '123456'; inputs[1].dispatchEvent(new Event('input', { bubbles: true }))
      Array.from(document.querySelectorAll('button')).find(b => b.innerText.includes('登')).click()
      return true
    })()`)
    await sleep(3200)
    ok((await cdp.eval('location.pathname')) === '/admin/dashboard', '管理员已登录')

    const startBadge = await badgeNum(cdp)
    ok(startBadge > 0, `侧边栏红点初始值 = ${startBadge}`)

    // 进入申请审核
    await cdp.eval(`(() => { const t = Array.from(document.querySelectorAll('.nav-item')).find(a => a.getAttribute('href') === '/admin/reviews'); if (t) t.click(); return !!t })()`)
    await sleep(1800)
    const listCount = await cdp.eval("document.querySelectorAll('.apply-item').length")
    ok(listCount === startBadge, `审核页待审核条数 (${listCount}) 与红点 (${startBadge}) 一致`)

    // 点第一条的「通过」
    await cdp.eval(`(() => {
      const item = document.querySelector('.apply-item')
      const btn = Array.from(item.querySelectorAll('button')).find(b => b.innerText.includes('通过') && !b.innerText.includes('拒绝'))
      btn.click()
      return true
    })()`)
    await sleep(700)
    // 确认对话框
    await cdp.eval(`(() => {
      const b = Array.from(document.querySelectorAll('.el-message-box__btns button')).find(x => x.innerText.includes('通过'))
      if (b) b.click()
      return true
    })()`)
    await sleep(2200)

    const afterBadge = await badgeNum(cdp)
    ok(afterBadge === startBadge - 1, `审核通过一条后红点 ${startBadge} -> ${afterBadge}（减 1）`)

    const afterList = await cdp.eval("document.querySelectorAll('.apply-item').length")
    ok(afterList === listCount - 1, `审核页列表同步移除一条：${listCount} -> ${afterList}`)

    // 再拒绝一条，验证连续减
    if (afterList > 0) {
      await cdp.eval(`(() => {
        const item = document.querySelector('.apply-item')
        const btn = Array.from(item.querySelectorAll('button')).find(b => b.innerText.includes('拒绝'))
        btn.click()
        return true
      })()`)
      await sleep(800)
      await cdp.eval(`(() => {
        const b = Array.from(document.querySelectorAll('.el-message-box__btns button')).find(x => x.innerText.includes('确认拒绝'))
        if (b) b.click()
        return true
      })()`)
      await sleep(2200)
      const b3 = await badgeNum(cdp)
      ok(b3 === afterBadge - 1, `拒绝一条后红点 ${afterBadge} -> ${b3}（再减 1）`)
    }

    // 刷新页面后红点应与后端一致（不会回弹成旧值）
    await cdp.send('Page.reload')
    await sleep(3000)
    const b4 = await badgeNum(cdp)
    ok(b4 === 0 || b4 === startBadge - 2 || b4 === startBadge - 1, `刷新后红点保持后端真实值：${b4}`)
  } finally {
    cdp.ws.close()
    edge.kill()
    await sleep(1200)
    try { rmSync(PROFILE, { recursive: true, force: true, maxRetries: 3, retryDelay: 500 }) } catch (e) {}
    console.log(`\n红点实时性测试完成，失败 ${globalThis.FAILED || 0} 项`)
    if (globalThis.FAILED) process.exitCode = 2
  }
}
run()