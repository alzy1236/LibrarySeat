// 学生端签到定位 UI 测试：用 CDP 模拟 GPS，验证围栏拦截与放行
import { spawn } from 'node:child_process'
import { rmSync, writeFileSync } from 'node:fs'
import { setTimeout as sleep } from 'node:timers/promises'

const EDGE = 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'
const PROFILE = 'C:/Users/Administrator/AppData/Local/Temp/ls-edge-geoui'
const OUT = 'C:/Users/Administrator/Desktop/LibrarySeat/docs/screenshots'
const PORT = 9387
const BASE = 'http://127.0.0.1:8080'
const ORIGIN = BASE

const NEAR = { lat: 39.90451, lng: 116.40781 }   // 签到点附近 ~10m
const FAR = { lat: 39.9900, lng: 116.4700 }      // 约 10km 外

let pass = 0, fail = 0
const ok = (c, m) => { if (c) { pass++; console.log('PASS ', m) } else { fail++; console.log('FAIL ', m) } }

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

const run = async () => {
  rmSync(PROFILE, { recursive: true, force: true })
  const edge = spawn(EDGE, ['--headless=new', `--remote-debugging-port=${PORT}`, `--user-data-dir=${PROFILE}`, '--no-first-run', '--disable-gpu', '--window-size=1440,980', 'about:blank'], { stdio: 'ignore' })
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
  await cdp.send('Emulation.setDeviceMetricsOverride', { width: 1440, height: 980, deviceScaleFactor: 1, mobile: false })
  await cdp.send('Browser.grantPermissions', { origin: ORIGIN, permissions: ['geolocation'] })

  const setGeo = (p) => cdp.send('Emulation.setGeolocationOverride', { latitude: p.lat, longitude: p.lng, accuracy: 8 })

  try {
    // 登录学生
    await setGeo(NEAR)
    await cdp.send('Page.navigate', { url: BASE + '/login' })
    await sleep(2600)
    await cdp.eval(`(() => {
      const i = document.querySelectorAll('.form-card input')
      i[0].value='20230001'; i[0].dispatchEvent(new Event('input',{bubbles:true}))
      i[1].value='123456'; i[1].dispatchEvent(new Event('input',{bubbles:true}))
      Array.from(document.querySelectorAll('button')).find(b=>b.innerText.includes('登 录')).click()
      return true
    })()`)
    await sleep(3200)
    ok(await cdp.eval(`location.pathname === '/student/home'`), '学生进入首页')

    // ---- 场景 1：远处定位，应被拒绝 ----
    await setGeo(FAR)
    await cdp.eval(`(() => { const b = document.querySelectorAll('.big-btn')[0]; if (b) b.click(); return !!b })()`)
    await sleep(3500) // 等定位 + 确认框
    // 点确认签到
    await cdp.eval(`(() => {
      const btn = Array.from(document.querySelectorAll('.el-message-box__btns button')).find(x => x.innerText.includes('确认签到'))
      if (btn) btn.click(); return !!btn
    })()`)
    await sleep(3000)
    const msgFar = await cdp.eval(`(() => Array.from(document.querySelectorAll('.el-message')).map(e=>e.innerText.replace(/\\s+/g,'')).join(' | '))()`)
    ok(/超出允许范围/.test(msgFar || ''), `远处签到被拒绝 -> ${msgFar}`)
    await cdp.shot('22-student-geo-far')

    // ---- 场景 2：范围内定位，应成功 ----
    await cdp.eval(`(() => { document.querySelectorAll('.el-message').forEach(e=>e.remove()); return true })()`)
    await setGeo(NEAR)
    await cdp.eval(`(() => { const b = document.querySelectorAll('.big-btn')[0]; if (b) b.click(); return !!b })()`)
    await sleep(3500)
    await cdp.eval(`(() => {
      const btn = Array.from(document.querySelectorAll('.el-message-box__btns button')).find(x => x.innerText.includes('确认签到'))
      if (btn) btn.click(); return !!btn
    })()`)
    await sleep(3200)
    const msgNear = await cdp.eval(`(() => Array.from(document.querySelectorAll('.el-message')).map(e=>e.innerText.replace(/\\s+/g,'')).join(' | '))()`)
    ok(/签到成功/.test(msgNear || ''), `范围内签到成功 -> ${msgNear}`)
    await cdp.shot('23-student-geo-near')

    console.log(`\n结果：${pass} 通过 / ${fail} 失败`)
  } finally {
    cdp.ws.close(); edge.kill(); await sleep(900)
    try { rmSync(PROFILE, { recursive: true, force: true, maxRetries: 3, retryDelay: 400 }) } catch (e) {}
  }
}
run()