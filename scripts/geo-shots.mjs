// 签到点管理页面截图（内嵌编辑器 + 地图）
import { spawn } from 'node:child_process'
import { mkdirSync, rmSync, writeFileSync } from 'node:fs'
import { setTimeout as sleep } from 'node:timers/promises'

const EDGE = 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'
const PROFILE = 'C:/Users/Administrator/AppData/Local/Temp/ls-edge-geo2'
const OUT = 'C:/Users/Administrator/Desktop/LibrarySeat/docs/screenshots'
const PORT = 9385
const BASE = process.env.BASE || 'http://127.0.0.1:8080'

class CDP {
  constructor(url) {
    this.ws = new WebSocket(url); this.id = 0; this.pending = new Map()
    this.ws.onmessage = (e) => {
      const m = JSON.parse(e.data)
      if (m.id && this.pending.has(m.id)) {
        const { resolve, reject } = this.pending.get(m.id)
        this.pending.delete(m.id)
        m.error ? reject(new Error(JSON.stringify(m.error))) : resolve(m.result)
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

const run = async () => {
  mkdirSync(OUT, { recursive: true })
  rmSync(PROFILE, { recursive: true, force: true })
  const edge = spawn(EDGE, ['--headless=new', `--remote-debugging-port=${PORT}`, `--user-data-dir=${PROFILE}`, '--no-first-run', '--disable-gpu', '--window-size=1440,980', 'about:blank'], { stdio: 'ignore' })
  let wsUrl = null
  for (let i = 0; i < 40; i++) {
    try {
      const list = await (await fetch(`http://127.0.0.1:${PORT}/json/list`)).json()
      const p = list.find((t) => t.type === 'page')
      if (p) { wsUrl = p.webSocketDebuggerUrl; break }
    } catch (e) {}
    await sleep(250)
  }
  const cdp = new CDP(wsUrl)
  await cdp.ready()
  await cdp.send('Page.enable'); await cdp.send('Runtime.enable')
  await cdp.send('Emulation.setDeviceMetricsOverride', { width: 1440, height: 1180, deviceScaleFactor: 1, mobile: false })
  try {
    await cdp.send('Page.navigate', { url: BASE + '/login' })
    await sleep(2600)
    await cdp.eval(`(() => {
      const i = document.querySelectorAll('.form-card input')
      i[0].value='admin'; i[0].dispatchEvent(new Event('input',{bubbles:true}))
      i[1].value='123456'; i[1].dispatchEvent(new Event('input',{bubbles:true}))
      Array.from(document.querySelectorAll('button')).find(b=>b.innerText.includes('登 录')).click()
      return true
    })()`)
    await sleep(3200)

    await cdp.eval(`(() => {
      const a = Array.from(document.querySelectorAll('.nav-item')).find(x => x.getAttribute('href') === '/admin/locations')
      if (a) a.click(); return !!a
    })()`)
    await sleep(2400)
    await cdp.shot('20-admin-locations')

    // 打开编辑（内嵌编辑器）
    await cdp.eval(`(() => {
      const b = Array.from(document.querySelectorAll('button')).find(x => x.innerText.trim() === '编辑')
      if (b) b.click(); return !!b
    })()`)
    await sleep(4500)
    await cdp.shot('21-admin-location-map')

    const diag = await cdp.eval(`(() => {
      const m = window.__mapPicker?.getMap?.()
      const el = document.querySelector('.map-canvas')
      const r = el ? el.getBoundingClientRect() : null
      const tiles = Array.from(document.querySelectorAll('.leaflet-tile'))
      return {
        container: r ? { w: Math.round(r.width), h: Math.round(r.height) } : null,
        mapSize: m ? { x: m.getSize().x, y: m.getSize().y } : null,
        tiles: tiles.length,
        loaded: tiles.filter(t => t.className.includes('loaded')).length,
        rows: [...new Set(tiles.map(t => Math.round(t.getBoundingClientRect().y)))].length
      }
    })()`)
    console.log('diag:', JSON.stringify(diag))
  } finally {
    cdp.ws.close(); edge.kill(); await sleep(1000)
    try { rmSync(PROFILE, { recursive: true, force: true, maxRetries: 3, retryDelay: 500 }) } catch (e) {}
  }
}
run()