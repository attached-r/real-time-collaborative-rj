// vite.config.ts
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'  // 用于路径解析

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [vue()],


  // 关键修复：polyfill global 为 window
  define: {
    global: 'window'
  },

  // 路径别名配置（@ 指向 src 目录）
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
      // 如果以后需要其他别名，也可以加
      // 'components': fileURLToPath(new URL('./src/components', import.meta.url)),
    }
  },

  // 开发服务器配置
  server: {
    // 关闭 HMR 错误覆盖层（红屏警告），开发时更清爽
    hmr: {
      overlay: false  // 关闭浏览器全屏红屏错误提示（只在控制台显示）
    },

    // 代理配置（前后端联调必备）
    proxy: {
      // 所有 /api 开头的请求转发到后端 Spring Boot
      '/api': {
        target: 'http://localhost:8081',     // 你的后端地址
        changeOrigin: true,// 修改 Origin 头，避免 CORS
        secure: false,           // 忽略 HTTPS 证书问题（本地开发常用）
        // 关键：手动转发所有请求头（包括 Authorization）
        configure: (proxy, _options) => {
          proxy.on('proxyReq', (proxyReq, req) => {
            // 转发所有原始请求头
            Object.keys(req.headers).forEach(key => {
              const value = req.headers[key]
              if (value) {
                proxyReq.setHeader(key, value as string | string[])
              }
            })
            // 额外打印确认
            if (req.headers.authorization) {
              console.log('Vite proxy 已转发 Authorization header:', req.headers.authorization.substring(0, 20) + '...')
            } else {
              console.log('Vite proxy 未发现 Authorization header')
            }
          })
        },
        rewrite: (path) => path
        // 如果后端路由也带 /api，就改成 rewrite: (path) => path
      },

      // WebSocket 代理（明天实时编辑用）
      '/ws': {
        target: 'http://localhost:8081',
        ws: true,                            // 启用 WebSocket 代理
        changeOrigin: true
      }
    }
  },

})
