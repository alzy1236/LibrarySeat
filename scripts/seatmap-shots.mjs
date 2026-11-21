// 座位页（桌面 + 手机）与公告弹窗截图
import { spawn } from 'node:child_process'
import { mkdirSync, rmSync, writeFileSync } from 'node:fs'
import { setTimeout as sleep } from 'node:timers/promises'

const EDGE = 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'
const PROFILE = 'C:/Users/Administrator/AppData/Local/Temp/ls-edge-ui4'
const OUT = 'C:/Users/Administrator/Desktop/LibrarySeat/docs/screenshots'
const PORT = 9401
const BASE = 'http://127.0.0.1:8080'

class CDP {
  constructor(url) {
    this.ws = new WebSocket(url); this.id = 0; this.p = new Map()
    this.ws.onmessage = (e) => {
      const m = JSON.parse(e.data)
      if (m.id && this.p.has(m.id)) {
        const { resolve, reject } = this.p.get(m.id)
        this.p.delete(m.id)
        m.error ? reject(new Error(JSON.stringify(m.error))) : resolve(m.result)
      }
    }
  }
  ready() { return new Promise((r, j) => { this.ws.onopen = r; this.ws.onerror = j }) }
  send(m, p = {}) { const id = ++this.id; return new Promise((res, rej) => { this.p.set(id, { resolve: res, reject: rej }); this.ws.send(JSON.stringify({ id, method: m, params: p })) }) }
  async eval(e) { const r = await this.send('Runtime.evaluate', { expression: e, awaitPromise: true, returnByValue: true }); return r.result?.value }
  async shot(n) { const { data } = await this.send('Page.captureScreenshot', { format: 'png' }); writeFileSync(`${OUT}/${n}.png`, Buffer.from(data, 'base64')); console.log('shot:', n) }
}

const login = async (cdp, username) => {
  await cdp.send('Page.navigate', { url: BASE + '/login' })
  await sleep(2400)
  await cdp.eval(`(() => {
    const i = document.querySelectorAll('.form-card input')
    i[0].value='${username}'; i[0].dispatchEvent(new Event('input',{bubbles:true}))
    i[1].value='123456'; i[1].dispatchEvent(new Event('input',{bubbles:true}))
    Array.from(document.querySelectorAll('button')).find(b=>b.innerText.replace(/\\s+/g,'').includes('登录')).click()
    return true
  })()`)
  await sleep(3000)
}

const run = async () => {
  mkdirSync(OUT, { recursive: true })
  rmSync(PROFILE, { recursive: true, force: true })
  const edge = spawn(EDGE, ['--headless=new', `--remote-debugging-port=${PORT}`, `--user-data-dir=${PROFILE}`, '--no-first-run', '--disable-gpu', '--window-size=1440,1000', 'about:blank'], { stdio: 'ignore' })
  let ws = null
  for (let i = 0; i < 40; i++) {
    try {
      const l = await (await fetch(`http://127.0.0.1:${PORT}/json/list`)).json()
      const p = l.find((t) => t.type === 'page')
      if (p) { ws = p.webSocketDebuggerUrl; break }
    } catch (e) {}
    await sleep(250)
  }
  const cdp = new CDP(ws)
  await cdp.ready()
  await cdp.send('Page.enable'); await cdp.send('Runtime.enable')

  try {
    // ---------- 桌面：座位页 ----------
    await cdp.send('Emulation.setDeviceMetricsOverride', { width: 1440, height: 1000, deviceScaleFactor: 1, mobile: false })
    await login(cdp, '20230001')
    await cdp.send('Page.navigate', { url: BASE + '/student/rooms/3' })
    await sleep(3200)
    await cdp.shot('27-seatmap-desktop')

    const board = await cdp.eval("!!document.querySelector('.desk-board')")
    const legend = await cdp.eval("document.querySelector('.legend-row')?.innerText.replace(/\\s+/g,'') || ''")
    const states = await cdp.eval("Array.from(document.querySelectorAll('.desk-state')).map(e=>e.innerText.trim()).filter((v,i,a)=>a.indexOf(v)===i)")
    console.log('讲台存在:', board, '| 图例:', legend, '| 出现的状态文案:', JSON.stringify(states))

    // ---------- 桌面：公告弹窗 ----------
    await cdp.send('Page.navigate', { url: BASE + '/student/home' })
    await sleep(3000)
    await cdp.eval(`(() => { const r = document.querySelector('.notice-row'); if (r) r.click(); return !!r })()`)
    await sleep(1200)
    const lines = await cdp.eval("document.querySelectorAll('.notice-lines p').length")
    const firstLines = await cdp.eval("Array.from(document.querySelectorAll('.notice-lines p')).slice(0,3).map(e=>e.innerText)")
    console.log('公告行数:', lines)
    console.log('前 3 行:', JSON.stringify(firstLines))
    await cdp.shot('28-notice-lines')

    // ---------- 手机：座位页 ----------
    await cdp.send('Emulation.setDeviceMetricsOverride', { width: 390, height: 844, deviceScaleFactor: 3, mobile: true })
    await cdp.send('Page.navigate', { url: BASE + '/student/rooms/3' })
    await sleep(3200)
    await cdp.shot('29-seatmap-mobile')

    // 手机：公告
    await cdp.send('Page.navigate', { url: BASE + '/student/home' })
    await sleep(3000)
    await cdp.eval(`(() => { const r = document.querySelector('.notice-row'); if (r) r.click(); return !!r })()`)
    await sleep(1200)
    await cdp.shot('30-notice-mobile')
  } finally {
    cdp.ws.close(); edge.kill(); await sleep(900)
    try { rmSync(PROFILE, { recursive: true, force: true, maxRetries: 3, retryDelay: 400 }) } catch (e) {}
  }
}
run()