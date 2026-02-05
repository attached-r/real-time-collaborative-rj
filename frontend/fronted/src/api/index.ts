// src/api/index.ts
import axios from 'axios'

const api = axios.create({
  baseURL: 'http://localhost:8080',  // 后端地址
  timeout: 10000,
})

console.log('api 拦截器文件已加载')  // 文件顶部加这行

// 请求拦截：自动加 token
api.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
    console.log('已设置 Authorization header')
  } else {
    console.log('无 token，不设置 header')
  }
  return config
},error =>{
  console.error('【Axios 请求拦截器】错误:', error)
  return Promise.reject(error)
})

// 响应拦截：401/403 自动跳登录
api.interceptors.response.use(
  response => response,
  error => {
    if (error.response?.status === 401 || error.response?.status === 403) {
      localStorage.removeItem('token')
      window.location.href = '/login'
    }
    return Promise.reject(error)
  }
)

export default api
