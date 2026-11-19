import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

// 前端通过 /api 代理到 SpringBoot 后端（开发模式）
// 打包时输出到 backend/src/main/resources/static，由 SpringBoot 直接托管，启动后自动进入登录页
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    port: 5173,
    host: '0.0.0.0',
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true,
        ws: true
      }
    }
  },
  build: {
    // 本地打包：输出到后端的 static 目录，SpringBoot 直接托管（start.bat 用这套）
    // Vercel 等云平台：输出到标准 dist 目录，否则平台找不到产物会报 STATIC_BUILD_NO_OUT_DIR
    outDir: process.env.VERCEL ? 'dist' : '../backend/src/main/resources/static',
    emptyOutDir: true,
    chunkSizeWarningLimit: 1500
  }
})