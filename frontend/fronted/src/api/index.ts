// src/api/index.ts
import axios from 'axios'
import { toast } from 'vue3-toastify'

const api = axios.create({
  // 删掉 baseURL，让 vite proxy 处理路径
  // baseURL: 'http://localhost:8080',  ← 注释或删除这一行
  timeout: 10000,
})

console.log('api 拦截器文件已加载')

// 请求拦截：自动加 token
api.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  if (token) {
    // 强制设置（覆盖任何可能缺失的情况）
    config.headers = config.headers || {}
    config.headers.Authorization = `Bearer ${token}`
    console.log('[拦截器] 已强制添加 token 到请求:', config.url)
  } else {
    console.warn('[拦截器] 无 token，跳过添加:', config.url)
  }
  return config
}, error => {
  console.error('[请求拦截器] 错误:', error)
  return Promise.reject(error)
})


// 只处理http请求
api.interceptors.response.use(
  response => response,
  error => {
    if (error.response?.status === 401) {  // 只处理 401（未登录/token 失效）
      localStorage.removeItem('token')
      window.location.href = '/login'
      console.log('401 自动登出')
    }
    // 403 无权限时不登出，只 toast 提示
    if (error.response?.status === 403) {
      toast.error('无权限访问此文档')
    }
    return Promise.reject(error)
  }
)
export default api
