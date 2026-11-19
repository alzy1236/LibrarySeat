import { defineStore } from 'pinia'
import { ref } from 'vue'
import { adminPendingLongTerm } from '@/api'

/**
 * 侧边栏「申请审核」红点计数
 *
 * - 进入管理端时拉取一次；
 * - 审核通过 / 拒绝一条后立即减一（无需刷新页面）；
 * - 同时定时轮询，其他管理员操作也能较快同步。
 */
export const useBadgeStore = defineStore('badge', () => {
  const pending = ref(0)
  let timer = null

  /** 从后端重新拉取待审核数量 */
  async function refresh() {
    try {
      const res = await adminPendingLongTerm()
      pending.value = (res.data || []).length
    } catch (e) {
      /* 未登录 / 网络异常时保持原值 */
    }
  }

  /** 直接设置（页面加载完列表后可精确同步） */
  function setPending(n) {
    pending.value = Math.max(0, Number(n) || 0)
  }

  /** 审核一条后减一 */
  function decPending(n = 1) {
    pending.value = Math.max(0, pending.value - n)
  }

  function startPolling(ms = 15000) {
    stopPolling()
    timer = setInterval(refresh, ms)
  }

  function stopPolling() {
    if (timer) {
      clearInterval(timer)
      timer = null
    }
  }

  return { pending, refresh, setPending, decPending, startPolling, stopPolling }
})