// 手机号验证码：注册 + 忘记密码 UI 端到端测试（含截图）
import { spawn } from 'node:child_process'
import { mkdirSync, rmSync, writeFileSync } from 'node:fs'
import { setTimeout as sleep } from 'node:timers/promises'

const EDGE = 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'
const PROFILE = 'C:/Users/Administrator/AppData/Local/Temp/ls-edge-sms'
const OUT = 'C:/Users/Administrator/Desktop/LibrarySeat/docs/screenshots'
const PORT = 9397
const BASE = 'http://127.0.0.1:8080'

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
  mkdirSync(OUT, { recursive: true })
  rmSync(PROFILE, { recursive: true, force: true })
  const edge = spawn(EDGE, ['--headless=new', `--remote-debugging-port=${PORT}`, `--user-data-dir=${PROFILE}`, '--no-first-run', '--disable-gpu', '--window-size=1440,1100', 'about:blank'], { stdio: 'ignore' })
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
  await cdp.send('Emulation.setDeviceMetricsOverride', { width: 1440, height: 1100, deviceScaleFactor: 1, mobile: false })

  const setInput = (sel, val) => `(() => {
    const el = document.querySelector('${sel}');
    if (!el) return false;
    el.value = '${val}';
    el.dispatchEvent(new Event('input', { bubbles: true }));
    return true;
  })()`

  const clickBtn = (text) => `(() => {
    const want = '${text}'.replace(/\\s+/g, '');
    const b = Array.from(document.querySelectorAll('button')).find(x => x.innerText.replace(/\\s+/g,'').includes(want));
    if (b) { b.click(); return true } return false
  })()`

  const toasts = `(() => Array.from(document.querySelectorAll('.el-message')).map(e=>e.innerText.replace(/\\s+/g,'')).join(' | '))()`

  try {
    // ================= 注册 =================
    await cdp.send('Page.navigate', { url: BASE + '/register' })
    await sleep(2600)

    const stamp = String(Date.now()).slice(-6)
    const username = 'ui' + stamp
    const phone = '138' + String(Date.now()).slice(-8)

    await cdp.eval(setInput('input[placeholder*="2023"]', username))
    await cdp.eval(setInput('input[placeholder*="姓名"]', '界面测试'))
    await cdp.eval(setInput('input[placeholder*="验证码、找回密码"]', phone))
    await sleep(400)

    // 点获取验证码
    const clicked = await cdp.eval(clickBtn('获取验证码'))
    ok(clicked, '注册页点击「获取验证码」')
    await sleep(2200)

    const tip = await cdp.eval(`(() => { const e = document.querySelector('.mock-tip'); return e ? e.innerText.replace(/\\s+/g,'') : '' })()`)
    ok(/\d{4}/.test(tip), `演示模式提示验证码 -> ${tip}`)

    const autoCode = await cdp.eval(`(() => {
      const inputs = Array.from(document.querySelectorAll('input'));
      const t = inputs.find(i => i.placeholder === '4 位数字');
      return t ? t.value : ''
    })()`)
    ok(/^\d{4}$/.test(autoCode), `验证码已自动填入 -> ${autoCode}`)
    await cdp.shot('24-register-sms')

    // 填密码并提交
    await cdp.eval(setInput('input[placeholder="至少 6 位"]', 'abc123456'))
    await cdp.eval(setInput('input[placeholder="再输一次"]', 'abc123456'))
    await sleep(300)
    await cdp.eval(clickBtn('注 册'))
    await sleep(2600)
    const t1 = await cdp.eval(toasts)
    ok(/注册成功/.test(t1 || ''), `注册成功 -> ${t1}`)

    // ================= 忘记密码 =================
    await cdp.send('Page.navigate', { url: BASE + '/login' })
    await sleep(2400)
    await cdp.eval(`(() => { const a = Array.from(document.querySelectorAll('a')).find(x=>x.innerText.includes('忘记密码')); if (a) a.click(); return !!a })()`)
    await sleep(1200)
    const dlgOpen = await cdp.eval(`(() => !!document.querySelector('.el-dialog'))()`)
    ok(dlgOpen, '找回密码弹窗已打开')

    // 用刚注册的账号手机号重置
    await cdp.eval(`(() => {
      const inputs = Array.from(document.querySelectorAll('.el-dialog input'));
      inputs[0].value = '${phone}'; inputs[0].dispatchEvent(new Event('input',{bubbles:true}));
      return true
    })()`)
    await sleep(300)
    await cdp.eval(`(() => {
      const b = Array.from(document.querySelectorAll('.el-dialog button')).find(x => x.innerText.includes('获取验证码'));
      if (b) b.click(); return !!b
    })()`)
    await sleep(2200)
    const tip2 = await cdp.eval(`(() => { const e = document.querySelector('.el-dialog .mock-tip'); return e ? e.innerText.replace(/\\s+/g,'') : '' })()`)
    ok(/\d{4}/.test(tip2), `弹窗内演示验证码提示 -> ${tip2}`)

    const code2 = await cdp.eval(`(() => {
      const inputs = Array.from(document.querySelectorAll('.el-dialog input'));
      const t = inputs.find(i => i.placeholder === '4 位数字');
      return t ? t.value : ''
    })()`)
    ok(/^\d{4}$/.test(code2), `弹窗验证码已自动填入 -> ${code2}`)
    await cdp.shot('25-forgot-password')

    // 设置新密码并提交
    await cdp.eval(`(() => {
      const inputs = Array.from(document.querySelectorAll('.el-dialog input'));
      const pwd = inputs.filter(i => i.type === 'password');
      pwd[0].value = 'new123456'; pwd[0].dispatchEvent(new Event('input',{bubbles:true}));
      pwd[1].value = 'new123456'; pwd[1].dispatchEvent(new Event('input',{bubbles:true}));
      return pwd.length
    })()`)
    await sleep(300)
    await cdp.eval(`(() => {
      const b = Array.from(document.querySelectorAll('.el-dialog button')).find(x => x.innerText.includes('重置密码'));
      if (b) b.click(); return !!b
    })()`)
    await sleep(2600)
    const t2 = await cdp.eval(toasts)
    ok(/密码已重置/.test(t2 || ''), `密码重置成功 -> ${t2}`)

    // 用新密码登录
    await cdp.eval(`(() => {
      const el = document.querySelectorAll('input')[0];
      el.value = '${username}';
      el.dispatchEvent(new Event('input', { bubbles: true }));
      return el.value
    })()`)
    await cdp.eval(setInput('input[type="password"]', 'new123456'))
    await sleep(300)
    await cdp.eval(clickBtn('登 录'))
    await sleep(3000)
    const path = await cdp.eval('location.pathname')
    ok(path.startsWith('/student'), `新密码登录成功并进入学生端 -> ${path}`)
    await cdp.shot('26-reset-login-ok')

    console.log(`\n结果：${pass} 通过 / ${fail} 失败`)
    console.log('测试账号：' + username + ' / new123456')
  } finally {
    cdp.ws.close(); edge.kill(); await sleep(900)
    try { rmSync(PROFILE, { recursive: true, force: true, maxRetries: 3, retryDelay: 400 }) } catch (e) {}
  }
}
run()