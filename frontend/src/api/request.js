import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'

export const TOKEN_KEY = 'ls_token'
export const USER_KEY = 'ls_user'

const service = axios.create({
  baseURL: '/api',
  timeout: 20000
})

// 请求拦截：携带 JWT
service.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem(TOKEN_KEY)
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

let redirecting = false
let lastToast = { message: '', at: 0 }

// 统一提示：短时间内的相同提示只显示一次，避免与页面自身提示重复
function toast(type, message) {
  const now = Date.now()
  if (lastToast.message === message && now - lastToast.at < 1200) return
  lastToast = { message, at: now }
  ElMessage({ type, message, grouping: true, duration: 3000 })
}

// 响应拦截：统一处理业务码 / 登录失效 / 权限不足
service.interceptors.response.use(
  (response) => {
    const res = response.data
    if (res && typeof res.code !== 'undefined') {
      if (res.code === 200) {
        return res
      }
      if (res.code === 401) {
        handleUnauthorized(res.message)
        return Promise.reject(new Error(res.message || '登录已失效'))
      }
      if (res.code === 403) {
        toast('warning', res.message || '无权限访问')
        return Promise.reject(new Error(res.message))
      }
      toast('error', res.message || '操作失败')
      return Promise.reject(new Error(res.message || '操作失败'))
    }
    return res
  },
  (error) => {
    const status = error.response?.status
    if (status === 401) {
      handleUnauthorized()
    } else if (status === 404) {
      toast('error', '接口不存在，请确认后端服务已启动')
    } else if (error.code === 'ECONNABORTED') {
      toast('error', '请求超时，请稍后重试')
    } else {
      toast('error', '网络异常，请检查后端服务是否启动')
    }
    return Promise.reject(error)
  }
)

function handleUnauthorized(message) {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
  if (redirecting) return
  redirecting = true
  const current = router.currentRoute.value
  if (current.path !== '/login') {
    ElMessage.warning(message || '登录已失效，请重新登录')
    router.replace({ path: '/login', query: { redirect: current.fullPath } })
  }
  setTimeout(() => (redirecting = false), 1000)
}

export default service