import { defineStore } from 'pinia'
import { getServerTime, getClock } from '@/api'

/**
 * 实时时钟 Store（“实时工具”）
 *
 * 原理：
 * 1. 通过 WebSocket 订阅后端每秒推送的服务器时间帧，计算“服务器时间 - 本地单调时钟”的偏差；
 * 2. 界面显示 = 本地高精度计时器 + 偏差，即使本机系统时间被修改，显示的时间仍与服务器一致；
 * 3. 所有签到 / 签退判定以服务器窗口为准（接口二次校验），前端只做提示。
 */
const WEEKDAYS = ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六']

function pad(n) {
  return String(n).padStart(2, '0')
}

export const useClockStore = defineStore('clock', {
  state: () => ({
    wsConnected: false,
    synced: false,
    /** 服务器时间 - 本地 performance.now() */
    offset: 0,
    today: '',
    time: '00:00:00',
    weekday: '',
    lastSyncAt: '',
    connectedOnce: false,
    rules: {
      checkInOpen: '07:00',
      checkInDeadline: '08:30',
      checkOutStart: '21:00',
      checkOutEnd: '23:59'
    },
    rulesLoaded: false,
    _ws: null,
    _timer: null,
    _reconnectTimer: null,
    _retry: 0
  }),
  getters: {
    /** 当前服务器时间的 Date 对象 */
    serverDate: (state) => () => new Date(Date.now() + state.offsetTimestamp),
    offsetTimestamp: (state) => 0
  },
  actions: {
    connect() {
      if (this._ws && (this._ws.readyState === 0 || this._ws.readyState === 1)) return
      const proto = location.protocol === 'https:' ? 'wss:' : 'ws:'
      const url = `${proto}//${location.host}/api/ws/time`
      try {
        const ws = new WebSocket(url)
        this._ws = ws
        ws.onopen = () => {
          this.wsConnected = true
          this.connectedOnce = true
          this._retry = 0
          ws.send('sync')
        }
        ws.onmessage = (evt) => {
          try {
            const frame = JSON.parse(evt.data)
            this.applyFrame(frame)
          } catch (e) {
            // 忽略非 JSON 帧
          }
        }
        ws.onclose = () => {
          this.wsConnected = false
          this.scheduleReconnect()
        }
        ws.onerror = () => {
          this.wsConnected = false
        }
      } catch (e) {
        this.scheduleReconnect()
      }
      // 本地渲染循环（无网络时仍显示上一次同步的时间）
      this.startTicker()
      // 备用校时：WebSocket 不可用时每 30 秒轮询一次
      this.pollOnce()
      clearInterval(this._pollTimer)
      this._pollTimer = setInterval(() => this.pollOnce(), 30000)
    },
    scheduleReconnect() {
      clearTimeout(this._reconnectTimer)
      const delay = Math.min(30000, 1500 * Math.pow(1.6, Math.min(this._retry++, 6)))
      this._reconnectTimer = setTimeout(() => this.connect(), delay)
    },
    applyFrame(frame) {
      if (!frame || !frame.timestamp) return
      const serverTs = Number(frame.timestamp)
      const localNow = Date.now()
      // 只使用偏差绝对值较小的样本，降低网络抖动影响
      const newOffset = serverTs - localNow
      if (!this.synced || Math.abs(newOffset) < 400 || Math.abs(newOffset) < Math.abs(this.offset)) {
        this.offset = newOffset
      }
      this.today = frame.date
      this.weekday = frame.weekday || WEEKDAYS[new Date(serverTs).getDay()]
      this.time = frame.time
      this.synced = true
      this.lastSyncAt = frame.serverTime
    },
    async pollOnce() {
      try {
        const res = await getServerTime()
        this.applyFrame({ ...res.data, timestamp: res.data.serverTimestamp })
      } catch (e) {
        // 登录页也可访问该接口；失败时保持上次同步结果
      }
      if (!this.rulesLoaded) {
        try {
          const res = await getClock()
          const d = res.data
          this.rules = {
            checkInOpen: d.checkInOpen,
            checkInDeadline: d.checkInDeadline,
            checkOutStart: d.checkOutStart,
            checkOutEnd: d.checkOutEnd
          }
          this.rulesLoaded = true
        } catch (e) {
          // ignore
        }
      }
    },
    startTicker() {
      if (this._timer) return
      this._timer = setInterval(() => {
        const d = new Date(Date.now() + this.offset)
        this.time = `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
        const dateStr = `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
        if (dateStr !== this.today) {
          this.today = dateStr
          this.weekday = WEEKDAYS[d.getDay()]
          this.pollOnce()
        }
      }, 250)
    },
    disconnect() {
      clearInterval(this._timer)
      clearInterval(this._pollTimer)
      clearTimeout(this._reconnectTimer)
      this._timer = null
      if (this._ws) {
        this._ws.onclose = null
        this._ws.close()
        this._ws = null
      }
      this.wsConnected = false
    }
  }
})