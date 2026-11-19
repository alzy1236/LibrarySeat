import { defineStore } from 'pinia'
import { login as loginApi, logout as logoutApi, getInfo } from '@/api'
import { TOKEN_KEY, USER_KEY } from '@/api/request'

function readUser() {
  try {
    return JSON.parse(localStorage.getItem(USER_KEY) || 'null')
  } catch (e) {
    return null
  }
}

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem(TOKEN_KEY) || '',
    user: readUser()
  }),
  getters: {
    isLogin: (state) => !!state.token,
    isAdmin: (state) => state.user?.role === 'ADMIN',
    isStudent: (state) => state.user?.role === 'STUDENT',
    displayName: (state) => state.user?.realName || state.user?.username || '',
    roleName: (state) => (state.user?.role === 'ADMIN' ? '管理员' : '学生')
  },
  actions: {
    async login(form) {
      const res = await loginApi(form)
      const data = res.data
      this.token = data.token
      this.user = data.userInfo
      localStorage.setItem(TOKEN_KEY, data.token)
      localStorage.setItem(USER_KEY, JSON.stringify(data.userInfo))
      return data
    },
    async refresh() {
      if (!this.token) return null
      const res = await getInfo()
      this.user = res.data
      localStorage.setItem(USER_KEY, JSON.stringify(res.data))
      return res.data
    },
    async logout() {
      try {
        await logoutApi()
      } catch (e) {
        // 忽略退出接口异常，本地状态照常清理
      }
      this.clear()
    },
    clear() {
      this.token = ''
      this.user = null
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(USER_KEY)
    }
  }
})