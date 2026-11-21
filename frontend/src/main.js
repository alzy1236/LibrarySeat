import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import 'element-plus/dist/index.css'
// Leaflet 地图样式（必须引入，否则瓦片没有绝对定位，会按普通图片流式排列）
import 'leaflet/dist/leaflet.css'

import App from './App.vue'
import router from './router'
import './styles/global.css'

const app = createApp(App)

// 注册 Element Plus 全部图标，模板中可直接使用 <el-icon><User /></el-icon>
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: zhCn })
app.mount('#app')