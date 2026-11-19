// 验证：刷新页面保持登录；重启项目后回到登录页
import { spawn } from 'node:child_process'
import { rmSync } from 'node:fs'
import { setTimeout as sleep } from 'node:timers/promises'

const EDGE = 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'
const PROFILE = 'C:/Users/Administrator/AppData/Local/Temp/ls-edge-refresh'
const PORT = 9361
const BASE = 'http://127.0.0.1:8080'

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

async function connect() {
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
  return cdp
}

const login = async (cdp, user) => {
  await cdp.eval(`(() => {
    const inputs = document.querySelectorAll('.form-card input')
    inputs[0].value = '${user}'; inputs[0].dispatchEvent(new Event('input', { bubbles: true }))
    inputs[1].value = '123456'; inputs[1].dispatchEvent(new Event('input', { bubbles: true }))
    Array.from(document.querySelectorAll('button')).find(b => b.innerText.includes('登 录') || b.innerText.includes('登')).click()
    return true
  })()`)
  await sleep(3000)
}

const run = async () => {
  rmSync(PROFILE, { recursive: true, force: true })
  const edge = spawn(EDGE, ['--headless=new', `--remote-debugging-port=${PORT}`, `--user-data-dir=${PROFILE}`, '--no-first-run', '--disable-gpu', 'about:blank'], { stdio: 'ignore' })
  const cdp = await connect()
  try {
    // 1) 首次打开 -> 登录页
    await cdp.send('Page.navigate', { url: BASE + '/login' })
    await sleep(2600)
    ok((await cdp.eval('location.pathname')) === '/login', '首次打开进入登录页')

    // 2) 管理员登录
    await login(cdp, 'admin')
    const p1 = await cdp.eval('location.pathname')
    ok(p1 === '/admin/dashboard', `登录后进入 ${p1}`)

    // 3) 刷新页面 -> 保持登录，不回登录页
    await cdp.send('Page.reload')
    await sleep(3000)
    const p2 = await cdp.eval('location.pathname')
    ok(p2 === '/admin/dashboard', `刷新页面后仍停留在 ${p2}（不回登录页）`)

    // 4) 刷新深层页面同样保持
    await cdp.send('Page.navigate', { url: BASE + '/admin/long-term' })
    await sleep(2600)
    await cdp.send('Page.reload')
    await sleep(3000)
    const p3 = await cdp.eval('location.pathname')
    ok(p3 === '/admin/long-term', `刷新深层页面后仍停留在 ${p3}`)

    // 5) 记录当前启动标识
    const stampBefore = await cdp.eval("document.querySelector('meta[name=ls-boot]')?.content")
    console.log('     当前启动标识:', stampBefore)
    ok(!!stampBefore, '页面包含启动标识 meta')

    // 6) 模拟“重启项目”：换一个启动标识（等同后端重新启动）
    await cdp.eval(`(() => {
      const m = document.querySelector('meta[name=ls-boot]')
      m.setAttribute('content', 'SIMULATED-RESTART')
      return true
    })()`)
    await cdp.eval("localStorage.setItem('ls_boot', 'SIMULATED-RESTART')")
    // 让下次加载看到不同标识
    const before = await cdp.eval("localStorage.getItem('ls_boot')")
    await cdp.eval("localStorage.setItem('ls_boot', 'OLD-BOOT')")
    await cdp.send('Page.reload')
    await sleep(3000)
    const p4 = await cdp.eval('location.pathname')
    ok(p4 === '/login', `启动标识变化（项目重启）后回到登录页：${p4}`)
  } finally {
    cdp.ws.close()
    edge.kill()
    await sleep(1200)
    try { rmSync(PROFILE, { recursive: true, force: true, maxRetries: 3, retryDelay: 500 }) } catch (e) {}
    console.log(`\n验证完成，失败 ${globalThis.FAILED || 0} 项`)
    if (globalThis.FAILED) process.exitCode = 2
  }
}
run()