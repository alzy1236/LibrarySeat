// 前端静态资源 + SPA 回退检查
const BASE = 'http://127.0.0.1:8080'

function ok(cond, msg) {
  console.log(`${cond ? 'PASS' : 'FAIL'}  ${msg}`)
  if (!cond) globalThis.FAILED = (globalThis.FAILED || 0) + 1
}

const run = async () => {
  const root = await fetch(BASE + '/')
  const rootHtml = await root.text()
  ok(root.status === 200 && rootHtml.includes('<div id="app">'), `GET / -> ${root.status}, 返回前端页面 (${rootHtml.length} 字节)`)
  ok(rootHtml.includes('自习室座位预约管理系统'), '页面 title 正确')

  const login = await fetch(BASE + '/login')
  const loginHtml = await login.text()
  ok(login.status === 200 && loginHtml.includes('<div id="app">'), `GET /login -> ${login.status} (直接访问登录页路径也能加载)`)

  const deep = await fetch(BASE + '/student/home')
  ok(deep.status === 200, `GET /student/home (刷新页面) -> ${deep.status}`)

  const assetMatch = rootHtml.match(/src="(\/assets\/index-[^"]+\.js)"/)
  ok(!!assetMatch, `入口脚本: ${assetMatch?.[1]}`)
  if (assetMatch) {
    const js = await fetch(BASE + assetMatch[1])
    ok(js.status === 200, `入口 JS -> ${js.status}`)
  }
  const cssMatch = rootHtml.match(/href="(\/assets\/index-[^"]+\.css)"/)
  if (cssMatch) {
    const css = await fetch(BASE + cssMatch[1])
    ok(css.status === 200, `入口 CSS -> ${css.status}`)
  }

  const favicon = await fetch(BASE + '/favicon.svg')
  ok(favicon.status === 200, `favicon -> ${favicon.status}`)

  // 接口仍正常
  const time = await fetch(BASE + '/api/system/server-time')
  const timeJson = await time.json()
  ok(timeJson.code === 200, `API 正常: ${timeJson.data.serverTime}`)

  console.log(`\n静态资源检查完成，失败 ${globalThis.FAILED || 0} 项`)
  if (globalThis.FAILED) process.exitCode = 2
}

run().catch((e) => {
  console.error('ERROR', e)
  process.exit(1)
})