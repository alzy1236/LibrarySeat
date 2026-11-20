/* ============================================================
 * 云端部署专用配置（构建时覆盖到 dist/app-config.js）
 * ------------------------------------------------------------
 * 后端固定地址：Cloudflare 命名隧道 library-seat
 *   api.superyang.pro  ->  http://localhost:8080
 *
 * 该地址永久固定，不会随电脑重启变化。
 * 前提：本机 SpringBoot 在 8080 运行，且 Cloudflared 服务处于运行状态。
 * ============================================================ */
window.__APP_CONFIG__ = {
  apiBase: 'https://api.superyang.pro'
}