// 移动端（手机视口）截图：检查 390x844 下的布局
import { spawn } from 'node:child_process'
import { mkdirSync, rmSync, writeFileSync } from 'node:fs'
import { setTimeout as sleep } from 'node:timers/promises'

const EDGE = 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'
const PROFILE = 'C:/Users/Administrator/AppData/Local/Temp/ls-edge-mobile'
const OUT = 'C:/Users/Administrator/Desktop/LibrarySeat/docs/screenshots/mobile'
const PORT = 9373
const BASE = process.env.BASE || 'http://127.0.0.1:5173'

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
  await sleep(3200)
}

const openDrawer = async (cdp) => {
  await cdp.eval(`(() => { const b = document.querySelector('.collapse'); if (b) b.click(); return !!b })()`)
  await sleep(700)
}

const nav = async (cdp, path, name, wait = 2200) => {
  await openDrawer(cdp)
  await cdp.eval(`(() => { const t = Array.from(document.querySelectorAll('.nav-item')).find(a => a.getAttribute('href') === '${path}'); if (t) t.click(); return !!t })()`)
  await sleep(wait)
  if (name) await cdp.shot(name)
}

const logoutAndLogin = async (cdp, user) => {
  await cdp.eval(`(() => { const me = document.querySelector('.me'); if (me) me.dispatchEvent(new MouseEvent('click', { bubbles: true })); return true })()`)
  await sleep(700)
  await cdp.eval(`(() => { const it = Array.from(document.querySelectorAll('.el-dropdown-menu__item')).find(i => i.innerText.includes('退出登录')); if (it) it.click(); return true })()`)
  await sleep(800)
  await cdp.eval(`(() => { const b = Array.from(document.querySelectorAll('.el-message-box__btns button')).find(x => x.innerText.includes('退出')); if (b) b.click(); return true })()`)
  await sleep(2400)
  await login(cdp, user)
}

const run = async () => {
  mkdirSync(OUT, { recursive: true })
  rmSync(PROFILE, { recursive: true, force: true })
  const edge = spawn(EDGE, ['--headless=new', `--remote-debugging-port=${PORT}`, `--user-data-dir=${PROFILE}`, '--no-first-run', '--disable-gpu', '--window-size=390,844', 'about:blank'], { stdio: 'ignore' })
  let wsUrl = null
  for (let i = 0; i < 40; i++) {
    try {
      const list = await (await fetch(`http://127.0.0.1:${PORT}/json/list`)).json()
      const page = list.find((t) => t.type === 'page')
      if (page) { wsUrl = page.webSocketDebuggerUrl; break }
    } catch (e) {}
    await sleep(250)
  }
  if (!wsUrl) throw new Error('no CDP target')
  const cdp = new CDP(wsUrl)
  await cdp.ready()
  await cdp.send('Page.enable')
  await cdp.send('Runtime.enable')
  await cdp.send('Emulation.setDeviceMetricsOverride', {
    width: 390, height: 844, deviceScaleFactor: 2, mobile: true
  })
  await cdp.send('Emulation.setTouchEmulationEnabled', { enabled: true, maxTouchPoints: 5 })
  try {
    await cdp.send('Page.navigate', { url: BASE + '/login' })
    await sleep(2800)
    await cdp.shot('m01-login')

    await cdp.send('Page.navigate', { url: BASE + '/register' })
    await sleep(2200)
    await cdp.shot('m02-register')

    await cdp.send('Page.navigate', { url: BASE + '/login' })
    await sleep(2000)
    await login(cdp, '20230001')
    await sleep(1200)
    await cdp.shot('m03-student-home')

    await openDrawer(cdp)
    await cdp.shot('m04-drawer-open')
    await cdp.eval(`(() => { document.querySelector('.side-mask')?.click(); return true })()`)
    await sleep(500)

    await nav(cdp, '/student/rooms', 'm05-rooms', 2600)
    await cdp.eval(`(() => { const f = Array.from(document.querySelectorAll('.floor')).find(x => x.querySelector('.floor-no span')?.innerText === '3'); if (f) f.click(); return true })()`)
    await sleep(2600)
    await cdp.shot('m06-seatmap')
    await nav(cdp, '/student/long-term', 'm07-longterm', 2600)
    await nav(cdp, '/student/records', 'm08-records', 2400)
    await nav(cdp, '/student/profile', 'm09-profile', 2200)

    await logoutAndLogin(cdp, 'admin')
    await sleep(2000)
    await cdp.shot('m10-admin-dashboard')
    await nav(cdp, '/admin/rooms', 'm11-admin-rooms', 2600)
    await nav(cdp, '/admin/reviews', 'm12-admin-reviews', 2400)
    await nav(cdp, '/admin/students', 'm13-admin-users', 2400)
    await nav(cdp, '/admin/settings', 'm14-admin-settings', 2400)
    await nav(cdp, '/admin/profile', 'm15-admin-profile', 2000)
  } finally {
    cdp.ws.close()
    edge.kill()
    await sleep(1200)
    try { rmSync(PROFILE, { recursive: true, force: true, maxRetries: 3, retryDelay: 500 }) } catch (e) {}
  }
}
run()