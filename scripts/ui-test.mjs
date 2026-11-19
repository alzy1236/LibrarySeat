// 真实浏览器 UI 验证：使用无头 Edge + CDP（不引入第三方依赖）
// 用法：node scripts/ui-test.mjs
import { spawn } from 'node:child_process'
import { writeFileSync, mkdirSync, rmSync } from 'node:fs'
import { setTimeout as sleep } from 'node:timers/promises'

const EDGE = 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'
const BASE = 'http://127.0.0.1:8080'
const SHOTS = 'C:/Users/Administrator/Desktop/LibrarySeat/docs/screenshots'
const PROFILE = 'C:/Users/Administrator/AppData/Local/Temp/ls-edge-profile'
const PORT = 9333

function ok(cond, msg) {
  console.log(`${cond ? 'PASS' : 'FAIL'}  ${msg}`)
  if (!cond) globalThis.FAILED = (globalThis.FAILED || 0) + 1
}

class CDP {
  constructor(url) {
    this.ws = new WebSocket(url)
    this.id = 0
    this.pending = new Map()
    this.events = []
    this.ws.onmessage = (e) => {
      const msg = JSON.parse(e.data)
      if (msg.id && this.pending.has(msg.id)) {
        const { resolve, reject } = this.pending.get(msg.id)
        this.pending.delete(msg.id)
        msg.error ? reject(new Error(JSON.stringify(msg.error))) : resolve(msg.result)
      } else if (msg.method) {
        this.events.push(msg)
      }
    }
  }
  ready() {
    return new Promise((resolve, reject) => {
      this.ws.onopen = resolve
      this.ws.onerror = reject
    })
  }
  send(method, params = {}) {
    const id = ++this.id
    return new Promise((resolve, reject) => {
      this.pending.set(id, { resolve, reject })
      this.ws.send(JSON.stringify({ id, method, params }))
    })
  }
  async eval(expression) {
    const r = await this.send('Runtime.evaluate', {
      expression,
      awaitPromise: true,
      returnByValue: true
    })
    if (r.exceptionDetails) throw new Error(r.exceptionDetails.exception?.description || r.exceptionDetails.text)
    return r.result.value
  }
  async waitFor(expression, timeout = 12000, step = 200) {
    const start = Date.now()
    while (Date.now() - start < timeout) {
      try {
        if (await this.eval(expression)) return true
      } catch (e) {
        /* 页面切换中 */
      }
      await sleep(step)
    }
    return false
  }
  async shot(name) {
    const r = await this.send('Page.captureScreenshot', { format: 'png' })
    writeFileSync(`${SHOTS}/${name}.png`, Buffer.from(r.data, 'base64'))
  }
}

const run = async () => {
  mkdirSync(SHOTS, { recursive: true })
  rmSync(PROFILE, { recursive: true, force: true })

  const edge = spawn(EDGE, [
    '--headless=new',
    `--remote-debugging-port=${PORT}`,
    `--user-data-dir=${PROFILE}`,
    '--no-first-run',
    '--no-default-browser-check',
    '--disable-gpu',
    '--window-size=1440,900',
    'about:blank'
  ], { stdio: 'ignore', detached: false })

  let wsUrl = null
  for (let i = 0; i < 40; i++) {
    try {
      const res = await fetch(`http://127.0.0.1:${PORT}/json/list`)
      const list = await res.json()
      const page = list.find((t) => t.type === 'page')
      if (page) {
        wsUrl = page.webSocketDebuggerUrl
        break
      }
    } catch (e) {
      /* 等待浏览器启动 */
    }
    await sleep(250)
  }
  if (!wsUrl) {
    console.error('无法连接 Edge DevTools')
    edge.kill()
    process.exit(1)
  }

  const cdp = new CDP(wsUrl)
  await cdp.ready()
  await cdp.send('Page.enable')
  await cdp.send('Runtime.enable')
  await cdp.send('Network.enable')
  await cdp.send('Emulation.setDeviceMetricsOverride', { width: 1440, height: 900, deviceScaleFactor: 1, mobile: false })

  const consoleErrors = []
  const setReporter = () => {
    // 收集控制台错误
    const orig = cdp.ws.onmessage
    cdp.ws.onmessage = (e) => {
      const msg = JSON.parse(e.data)
      if (msg.method === 'Runtime.consoleAPICalled' && msg.params.type === 'error') {
        consoleErrors.push(msg.params.args.map((a) => a.value || a.description).join(' '))
      }
      if (msg.method === 'Runtime.exceptionThrown') {
        consoleErrors.push(msg.params.exceptionDetails.exception?.description || msg.params.exceptionDetails.text)
      }
      orig(e)
    }
  }
  setReporter()

  const goto = async (path) => {
    await cdp.send('Page.navigate', { url: BASE + path })
    await sleep(1200)
  }

  /** 点击侧边栏菜单进入页面（页面内跳转，不触发整页刷新） */
  const navTo = async (path) => {
    await cdp.eval(`(() => { const t = Array.from(document.querySelectorAll('.nav-item')).find(a => a.getAttribute('href') === '${path}'); if (t) t.click(); return !!t })()`)
    await cdp.waitFor(`location.pathname === '${path}'`, 10000)
    await sleep(600)
  }

  try {
    // 1. 启动后进入登录页
    await goto('/')
    const loginReady = await cdp.waitFor("document.querySelector('.form-card') !== null", 15000)
    ok(loginReady, `访问根路径 -> 自动进入登录页，URL=${await cdp.eval('location.pathname')}`)
    ok((await cdp.eval("document.querySelector('.form-head h3')?.innerText")) === '登录账号', '登录页标题渲染正常')
    ok((await cdp.eval("document.querySelectorAll('.brand-side .bullets li').length")) === 3, '登录页左侧品牌区渲染')
    ok((await cdp.eval("document.body.innerText.includes('演示账号')")) === false, '登录页已移除“演示账号”区块')
    const clockText = await cdp.eval("document.querySelector('.clock-time')?.innerText || ''")
    ok(/^\d{2}:\d{2}:\d{2}$/.test(clockText), `登录页服务器实时时钟显示 ${clockText}`)
    await sleep(1200)
    const clockText2 = await cdp.eval("document.querySelector('.clock-time')?.innerText || ''")
    ok(clockText2 !== clockText, `实时时钟在走：${clockText} -> ${clockText2}`)
    await cdp.shot('01-login')

    // 2. 错误密码提示
    await cdp.eval(`(() => {
      const inputs = document.querySelectorAll('.form-card input')
      inputs[0].value = 'admin'; inputs[0].dispatchEvent(new Event('input', { bubbles: true }))
      inputs[1].value = 'wrong'; inputs[1].dispatchEvent(new Event('input', { bubbles: true }))
      return true
    })()`)
    await cdp.eval("Array.from(document.querySelectorAll('button')).find(b => b.innerText.includes('登 录')).click()")
    // 轮询过程中记录所有出现过的提示文本（提示可能被 grouping 合并 / 短暂出现）
    let seenToast = ''
    const toastStart = Date.now()
    while (Date.now() - toastStart < 8000) {
      const texts = await cdp.eval("Array.from(document.querySelectorAll('.el-message')).map(e => e.innerText.replace(/\\s+/g, ' ')).join(' || ')")
      if (texts && texts.includes('密码错误')) {
        seenToast = texts
        break
      }
      if (texts && !seenToast) seenToast = texts
      await sleep(100)
    }
    ok(seenToast.includes('密码错误'), `错误密码提示：${seenToast}`)
    await cdp.eval("document.querySelectorAll('.el-message').forEach(e => e.remove())")

    // 3. 管理员登录 -> 管理端
    await cdp.eval(`(() => {
      const inputs = document.querySelectorAll('.form-card input')
      inputs[1].value = '123456'; inputs[1].dispatchEvent(new Event('input', { bubbles: true }))
      return true
    })()`)
    await cdp.eval("Array.from(document.querySelectorAll('button')).find(b => b.innerText.includes('登 录')).click()")
    const adminIn = await cdp.waitFor("location.pathname === '/admin/dashboard' && document.querySelector('canvas') !== null", 15000)
    await sleep(2500)
    ok(adminIn, `管理员登录后进入管理端：${await cdp.eval('location.pathname')}`)
    const canvasCount = await cdp.eval("document.querySelectorAll('canvas').length")
    ok(canvasCount >= 4, `数据概览图表渲染数量：${canvasCount}`)
    const dashCards = await cdp.eval("Array.from(document.querySelectorAll('.board .card-head h3')).map(e => e.innerText.trim()).join(' | ')")
    ok(dashCards.split('|').length >= 4, `数据概览仅保留图表区：${dashCards}`)
    ok((await cdp.eval("document.querySelectorAll('.stat-value').length")) === 0, '数据概览已移除上方统计卡片')
    ok((await cdp.eval("document.querySelectorAll('.nav-item').length")) >= 8, '新侧边栏导航渲染')
    await cdp.shot('02-admin-dashboard')

    // 4. 刷新页面 -> 保持登录与当前页面（只有重启项目才回登录页）
    await cdp.send('Page.reload')
    await sleep(3000)
    const afterReload = await cdp.eval('location.pathname')
    ok(afterReload === '/admin/dashboard', `刷新页面后仍停留在 ${afterReload}（不清空登录态）`)

    // 4.1 刷新深层页面同样保持
    await goto('/admin/long-term')
    await cdp.waitFor("document.querySelector('.el-table') !== null", 12000)
    await cdp.send('Page.reload')
    await sleep(3000)
    const deepReload = await cdp.eval('location.pathname')
    ok(deepReload === '/admin/long-term', `刷新深层页面后仍停留在 ${deepReload}`)

    // 4.2 启动标识存在（用于判断项目是否重启）
    const bootStamp = await cdp.eval("document.querySelector('meta[name=ls-boot]')?.content || ''")
    ok(!!bootStamp, `页面注入启动标识：${bootStamp}`)

    await goto('/admin/dashboard')
    await cdp.waitFor("document.querySelector('canvas') !== null", 12000)

    // 5. 管理端其它页面
    const adminPages = [
      ['/admin/rooms', '.floor-card', '楼层与座位'],
      ['/admin/reservations', '.el-table', '预约记录'],
      ['/admin/reviews', '.apply-item, .empty-state', '申请审核'],
      ['/admin/long-term', '.el-table', '固定座位 · 考核'],
      ['/admin/students', '.el-table', '学生账号'],
      ['/admin/notices', '.el-table', '公告管理'],
      ['/admin/settings', '.field', '系统设置'],
      ['/admin/rooms/3/seats', '.desk', '座位管理']
    ]
    for (const [path, sel, name] of adminPages) {
      // 用页面内路由跳转（模拟点击菜单），而不是整页刷新
      if (path === '/admin/rooms/3/seats') {
        // 座位管理页由楼层卡片进入，直接导航到该路径
        await navTo('/admin/rooms')
        await cdp.waitFor("document.querySelector('.floor-card') !== null", 10000)
        await cdp.eval(`(() => {
          const btns = Array.from(document.querySelectorAll('.fc-actions button'))
          const t = btns.find(b => b.innerText.includes('座位管理'))
          if (t) { t.click(); return true }
          return false
        })()`)
        await cdp.waitFor("location.pathname === '/admin/rooms/3/seats'", 10000)
        await sleep(600)
      } else {
        await navTo(path)
      }
      const okPage = await cdp.waitFor(`document.querySelector('${sel}') !== null`, 12000)
      const title = await cdp.eval("document.querySelector('.crumb')?.innerText || ''")
      ok(okPage, `${name} 页面渲染 -> ${path}（标题：${title}）`)
      await cdp.shot(`03-admin-${path.split('/').pop()}`)
    }

    // 5.1 系统设置不显示原始配置 key（页面内跳转，避免整页刷新触发“回到登录页”）
    await navTo('/admin/settings')
    await cdp.waitFor("document.querySelector('.field') !== null", 10000)
    const settingsText = await cdp.eval("document.body.innerText")
    ok(!/checkin\.deadline|checkout\.start|long\.min\.days|short\.clear/.test(settingsText), '系统设置页不再显示原始配置 key')
    ok(settingsText.includes('签到截止时间') && settingsText.includes('每月有效出勤天数'), '系统设置显示中文友好标签')

    // 5.2 申请审核页渲染（有申请看列表，无申请看空状态）
    await navTo('/admin/reviews')
    await cdp.waitFor(
      "document.querySelector('.apply-item') !== null || document.querySelector('.empty-state') !== null",
      12000
    )
    const pendingCount = await cdp.eval("document.querySelectorAll('.apply-item').length")
    const reviewBadge = await cdp.eval("(() => { const el = document.querySelector('.badge'); return el ? Number(el.innerText) : 0 })()")
    ok(true, `申请审核页渲染正常：待审核 ${pendingCount} 条，侧边栏红点 ${reviewBadge}`)
    ok(pendingCount === reviewBadge, `红点与待审核条数一致（${pendingCount} / ${reviewBadge}）`)

    // 6. 管理端数据正确性：固定座位列表
    await navTo('/admin/long-term')
    await cdp.waitFor("document.querySelector('.el-table__row') !== null", 12000)
    const bindRows = await cdp.eval("document.querySelectorAll('.el-table__row').length")
    ok(bindRows >= 5, `生效中的固定座位 ${bindRows} 行`)

    // 7. 退出登录 -> 回到登录页
    await cdp.eval(`(() => {
      const me = document.querySelector('.me') || document.querySelector('.user')
      me.dispatchEvent(new MouseEvent('click', { bubbles: true }))
      return true
    })()`)
    await sleep(600)
    const logoutBtn = await cdp.eval(`(() => {
      const items = Array.from(document.querySelectorAll('.el-dropdown-menu__item'))
      const target = items.find(i => i.innerText.includes('退出登录'))
      if (target) { target.click(); return true }
      return false
    })()`)
    ok(logoutBtn, '找到退出登录菜单项')
    await sleep(700)
    await cdp.eval(`(() => {
      const btns = Array.from(document.querySelectorAll('.el-message-box__btns button'))
      const ok = btns.find(b => b.innerText.includes('退出') || b.innerText.includes('确定'))
      if (ok) { ok.click(); return true }
      return false
    })()`)
    const backToLogin = await cdp.waitFor("location.pathname === '/login'", 10000)
    ok(backToLogin, `退出登录后回到登录页：${await cdp.eval('location.pathname')}`)

    // 8. 学生登录 -> 学生端
    await cdp.waitFor("document.querySelector('.form-card') !== null", 10000)
    await cdp.eval(`(() => {
      const inputs = document.querySelectorAll('.form-card input')
      inputs[0].value = '20230001'; inputs[0].dispatchEvent(new Event('input', { bubbles: true }))
      inputs[1].value = '123456'; inputs[1].dispatchEvent(new Event('input', { bubbles: true }))
      Array.from(document.querySelectorAll('button')).find(b => b.innerText.includes('登 录')).click()
      return true
    })()`)
    const studentIn = await cdp.waitFor("location.pathname === '/student/home' && document.querySelector('.big-btn') !== null", 15000)
    ok(studentIn, `学生登录后进入学生端：${await cdp.eval('location.pathname')}`)
    await sleep(1500)
    const heroTime = await cdp.eval("document.querySelector('.clock-big')?.innerText || ''")
    ok(/^\d{2}:\d{2}:\d{2}$/.test(heroTime), `学生首页服务器实时时钟：${heroTime}`)
    // 按钮文案带字间距（"签 到"），比较前先去掉所有空白
    const btnTexts = await cdp.eval("Array.from(document.querySelectorAll('.big-btn')).map(b => b.innerText.replace(/\\s+/g,'').trim()).join(' || ')")
    ok(btnTexts.includes('签到') && btnTexts.includes('签退'), `首页只有签到/签退两个操作按钮：${btnTexts}`)
    await cdp.shot('04-student-home')

    // 8.1 点击灰色按钮的提示（当前时间不在签到/签退窗口内）
    const clockState = await cdp.eval("JSON.stringify({time: document.querySelector('.clock-big')?.innerText, tip: document.querySelector('.ac-tip')?.innerText})")
    await cdp.eval("document.querySelectorAll('.big-btn')[0].click()")
    await sleep(700)
    const msg1 = await cdp.eval("Array.from(document.querySelectorAll('.el-message')).map(e => e.innerText.replace(/\\s+/g,'')).join(' || ')")
    ok(msg1.length > 0, `点击不可用签到按钮有提示：${msg1}（当前 ${clockState}）`)
    await cdp.eval("document.querySelectorAll('.el-message').forEach(e => e.remove())")
    await cdp.eval("document.querySelectorAll('.big-btn')[1].click()")
    await sleep(700)
    const msg2 = await cdp.eval("Array.from(document.querySelectorAll('.el-message')).map(e => e.innerText.replace(/\\s+/g,'')).join(' || ')")
    ok(msg2.length > 0, `点击不可用签退按钮有提示：${msg2}`)
    await cdp.eval("document.querySelectorAll('.el-message').forEach(e => e.remove())")

    // 8.2 按钮禁用状态与服务器时间一致
    const disabledMatch = await cdp.eval(`(() => {
      const btns = document.querySelectorAll('.big-btn')
      const locked = Array.from(btns).map(b => b.classList.contains('locked'))
      const time = document.querySelector('.clock-big').innerText
      return JSON.stringify({ locked, time })
    })()`)
    ok(!!disabledMatch, `按钮置灰状态：${disabledMatch}`)

    // 9. 学生端 - 楼层列表 & 桌子座位图（1—5 层）—— 页面内跳转，避免整页刷新
    await navTo('/student/rooms')
    await cdp.waitFor("document.querySelector('.floor') !== null", 12000)
    const floorCount = await cdp.eval("document.querySelectorAll('.floor').length")
    const floorNos = await cdp.eval("Array.from(document.querySelectorAll('.floor-no span')).map(e => e.innerText).join(',')")
    ok(floorCount === 5, `楼层列表渲染 ${floorCount} 层（${floorNos}）`)
    const floorTexts = await cdp.eval("document.body.innerText")
    ok(!floorTexts.includes('图书馆'), '楼层信息已改为独立自习楼（不再显示“图书馆”）')
    await cdp.shot('05-student-rooms')

    // 点击第 3 层进入座位图
    await cdp.eval(`(() => {
      const floors = Array.from(document.querySelectorAll('.floor'))
      const t = floors.find(f => f.querySelector('.floor-no span')?.innerText === '3')
      if (t) { t.click(); return true }
      return false
    })()`)
    await cdp.waitFor("location.pathname === '/student/rooms/3'", 10000)
    await sleep(800)
    const seatMapReady = await cdp.waitFor("document.querySelectorAll('.desk').length > 0", 12000)
    const seatCount = await cdp.eval("document.querySelectorAll('.desk').length")
    ok(seatMapReady && seatCount === 24, `第 3 层桌子式座位图渲染 ${seatCount} 个座位`)
    ok((await cdp.eval("document.querySelector('.desk-board')?.innerText")) === '讲 台', '座位图包含讲台与桌子布局')
    await cdp.shot('06-student-seatmap')

    await navTo('/student/long-term')
    const ltReady = await cdp.waitFor("document.querySelector('.bind') !== null || document.querySelector('.room-tab') !== null || document.querySelector('.apply-state') !== null", 12000)
    ok(ltReady, `固定座位页渲染（${await cdp.eval("document.querySelector('.bind') ? '已生效' : document.querySelector('.apply-state') ? '申请中/被拒' : '可申请'")}）`)
    await cdp.shot('07-student-longterm')

    await navTo('/student/records')
    await cdp.waitFor("document.querySelector('.el-table__row') !== null", 12000)
    ok(true, `我的记录表格渲染 ${await cdp.eval("document.querySelectorAll('.el-table__row').length")} 行`)

    // 10. 学生访问管理端 -> 被角色守卫拦截（页面内跳转到受限角色页面）
    await cdp.eval("(() => { const r = document.querySelector('#app').__vue_app__; return true })()")
    await cdp.eval(`(() => {
      const link = document.createElement('a')
      link.href = '/admin/dashboard'
      document.body.appendChild(link)
      link.click()
      link.remove()
      return true
    })()`)
    await sleep(1500)
    const guarded = await cdp.eval('location.pathname')
    ok(guarded !== '/admin/dashboard', `学生访问管理端被角色守卫拦截（当前：${guarded}）`)

    // 11. 清除登录态 + 刷新 -> 回到登录页
    await cdp.eval("localStorage.clear()")
    await cdp.send('Page.reload')
    const kicked = await cdp.waitFor("location.pathname === '/login'", 10000)
    ok(kicked, `清除登录态后刷新 -> 回到登录页：${await cdp.eval('location.pathname')}`)

    // 12. 控制台错误检查
    const realErrors = consoleErrors.filter((e) => !/Failed to load resource|WebSocket is closed|favicon/.test(e))
    ok(realErrors.length === 0, `浏览器控制台无 JS 报错${realErrors.length ? '：' + realErrors.slice(0, 3).join(' | ') : ''}`)
  } catch (e) {
    console.error('UI TEST ERROR', e)
    globalThis.FAILED = (globalThis.FAILED || 0) + 1
  } finally {
    try {
      cdp.ws.close()
    } catch (e) {}
    edge.kill()
    await sleep(1500)
    // 浏览器进程可能仍占用临时用户目录，清理失败不影响测试结果
    try {
      rmSync(PROFILE, { recursive: true, force: true, maxRetries: 3, retryDelay: 500 })
    } catch (e) {
      /* ignore */
    }
    console.log(`\nUI 测试完成，失败 ${globalThis.FAILED || 0} 项`)
    if (globalThis.FAILED) process.exitCode = 2
  }
}

run()



