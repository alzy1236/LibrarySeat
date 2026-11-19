// 截图关键页面（新配色检查）
import { spawn } from 'node:child_process'
import { mkdirSync, rmSync } from 'node:fs'
import { setTimeout as sleep } from 'node:timers/promises'

const EDGE = 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'
const PROFILE = 'C:/Users/Administrator/AppData/Local/Temp/ls-edge-shots'
const OUT = 'C:/Users/Administrator/Desktop/LibrarySeat/docs/screenshots'
const PORT = 9371
const BASE = 'http://127.0.0.1:8080'

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
    return r.result?.value
  }
  async shot(name) {
    const { data } = await this.send('Page.captureScreenshot', { format: 'png' })
    const { writeFileSync } = await import('node:fs')
    writeFileSync(`${OUT}/${name}.png`, Buffer.from(data, 'base64'))
    console.log('shot:', name)
  }
}

const login = async (cdp, user) => {
  await cdp.eval(`(() => {
    const inputs = document.querySelectorAll('.form-card input')
    inputs[0].value = '${user}'; inputs[0].dispatchEvent(new Event('input', { bubbles: true }))
    inputs[1].value = '123456'; inputs[1].dispatchEvent(new Event('input', { bubbles: true }))
    Array.from(document.querySelectorAll('button')).find(b => b.innerText.includes('登')).click()
    return true
  })()`)
  await sleep(3000)
}

const run = async () => {
  mkdirSync(OUT, { recursive: true })
  rmSync(PROFILE, { recursive: true, force: true })
  const edge = spawn(EDGE, ['--headless=new', `--remote-debugging-port=${PORT}`, `--user-data-dir=${PROFILE}`, '--no-first-run', '--disable-gpu', '--window-size=1720,1000', 'about:blank'], { stdio: 'ignore' })
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
  await cdp.send('Emulation.setDeviceMetricsOverride', { width: 1720, height: 1000, deviceScaleFactor: 1, mobile: false })
  try {
    await cdp.send('Page.navigate', { url: BASE + '/login' })
    await sleep(2600)
    await cdp.shot('01-login')

    // 学生端
    await login(cdp, '20230001')
    await sleep(1200)
    await cdp.shot('04-student-home')

    const nav = async (path, name, wait = 2000) => {
      await cdp.eval(`(() => { const t = Array.from(document.querySelectorAll('.nav-item')).find(a => a.getAttribute('href') === '${path}'); if (t) t.click(); return !!t })()`)
      await sleep(wait)
      if (name) await cdp.shot(name)
    }
    await nav('/student/profile', '08-student-profile')
    await nav('/student/rooms', '05-student-rooms', 2500)
    await cdp.eval(`(() => { const f = Array.from(document.querySelectorAll('.floor')).find(x => x.querySelector('.floor-no span')?.innerText === '3'); if (f) f.click(); return true })()`)
    await sleep(2500)
    await cdp.shot('06-student-seatmap')
    await nav('/student/long-term', '07-student-longterm', 2500)
    await nav('/student/records', '09-student-records', 2500)

    // 管理端
    await cdp.eval(`(() => { const me = document.querySelector('.me'); if (me) me.dispatchEvent(new MouseEvent('click', { bubbles: true })); return true })()`)
    await sleep(600)
    await cdp.eval(`(() => { const it = Array.from(document.querySelectorAll('.el-dropdown-menu__item')).find(i => i.innerText.includes('退出登录')); if (it) it.click(); return true })()`)
    await sleep(700)
    await cdp.eval(`(() => { const b = Array.from(document.querySelectorAll('.el-message-box__btns button')).find(x => x.innerText.includes('退出')); if (b) b.click(); return true })()`)
    await sleep(2500)
    await login(cdp, 'admin')
    await sleep(2500)
    await cdp.shot('02-admin-dashboard')
    await nav('/admin/rooms', '03-admin-rooms', 2500)
    await nav('/admin/reviews', '03-admin-reviews', 2500)
    await nav('/admin/long-term', '03-admin-longterm', 2500)
    await nav('/admin/settings', '03-admin-settings', 2500)
    await nav('/admin/profile', '10-admin-profile', 2000)
  } finally {
    cdp.ws.close()
    edge.kill()
    await sleep(1200)
    try { rmSync(PROFILE, { recursive: true, force: true, maxRetries: 3, retryDelay: 500 }) } catch (e) {}
  }
}
run()
