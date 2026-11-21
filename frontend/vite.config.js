import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import { fileURLToPath, URL } from 'node:url'

// 前端通过 /api 代理到 SpringBoot 后端（开发模式）
// 打包时输出到 backend/src/main/resources/static，由 SpringBoot 直接托管，启动后自动进入登录页
export default defineConfig({
  plugins: [
    vue(),
    // Element Plus 组件按需引入：只打包真正用到的组件，主包体积可减小约 40%
    // importStyle: false —— 组件样式仍由 main.js 里的 element-plus/dist/index.css 统一提供
    Components({
      dts: false,
      resolvers: [ElementPlusResolver({ importStyle: false })]
    })
  ],
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
    // 本地打包：输出到后端 static 目录，SpringBoot 直接托管（start.bat / 打 jar 用这套）
    // 云平台（Vercel / Cloudflare Pages / Netlify / 其它 CI）：输出到标准 dist 目录，
    // 否则平台找不到产物，会报 STATIC_BUILD_NO_OUT_DIR 之类的错误
    outDir: process.env.OUT_DIR || (
      process.env.VERCEL || process.env.CF_PAGES || process.env.NETLIFY || process.env.CI
        ? 'dist'
        : '../backend/src/main/resources/static'
    ),
    emptyOutDir: true,
    chunkSizeWarningLimit: 1500
  }
})