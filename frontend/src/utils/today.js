// 集中读取“今日预约”状态的小工具
import { getTodayReservation, getTodayActive } from '@/api'

/** 今日预约（包含已签退 / 已取消的当日记录，供首页展示） */
export async function fetchToday() {
  try {
    const res = await getTodayReservation()
    return res.data || null
  } catch (e) {
    return null
  }
}

/** 今日仍在生效的预约（待签到 / 使用中），用于阻止重复选座 */
export async function fetchTodayActive() {
  try {
    const res = await getTodayActive()
    return res.data || null
  } catch (e) {
    return null
  }
}

/** 是否仍占用今天的预约名额 */
export function isActive(today) {
  return !!today && (today.status === 'WAITING' || today.status === 'SEATED')
}

/** 状态对应的 Element Plus tag 类型 */
export function statusTagType(status) {
  return { WAITING: 'warning', SEATED: 'success', LEFT: 'info', CANCELLED: 'info', TIMEOUT: 'danger' }[status] || 'info'
}