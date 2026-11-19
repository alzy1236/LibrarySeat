// WebSocket 实时时钟 + 接口 404 检查（Node 24 内置 WebSocket）
const BASE = 'http://127.0.0.1:8080'
const WS = 'ws://127.0.0.1:8080/api/ws/time'

function ok(cond, msg) {
  console.log(`${cond ? 'PASS' : 'FAIL'}  ${msg}`)
  if (!cond) globalThis.FAILED = (globalThis.FAILED || 0) + 1
}

const frames = []
const ws = new WebSocket(WS)
ws.onopen = () => ws.send('sync')
ws.onmessage = (e) => {
  try {
    frames.push(JSON.parse(e.data))
  } catch {}
}

setTimeout(async () => {
  ws.close()
  ok(frames.length >= 2, `收到 ${frames.length} 帧服务器时间（1 秒推送 1 次）`)
  const f = frames[frames.length - 1]
  ok(!!f.serverTime && !!f.weekday, `帧内容: ${f?.serverTime} ${f?.weekday}`)
  const seqs = frames.map((x) => x.seq)
  ok(seqs.every((v, i) => i === 0 || v > seqs[i - 1]), '时间帧序号递增（实时推送）')
  const drift = frames.length ? Math.abs(Date.now() - frames[frames.length - 1].timestamp) : 99999
  ok(drift < 3000, `服务器与本地时钟偏差 ${drift}ms`)

  // 不存在的接口不能回退到前端页面
  const r404 = await fetch(BASE + '/api/not-exists')
  const t404 = await r404.text()
  ok(!t404.includes('<div id="app">'), `/api/not-exists -> ${r404.status}，未被 SPA 回退`)
  // 不存在的静态资源
  const r404s = await fetch(BASE + '/assets/none.js')
  ok(r404s.status === 404, `/assets/none.js -> ${r404s.status}`)

  console.log(`\nWebSocket 检查完成，失败 ${globalThis.FAILED || 0} 项`)
  if (globalThis.FAILED) process.exitCode = 2
}, 4500)

ws.onerror = (e) => console.error('WS ERROR', e?.message || e)