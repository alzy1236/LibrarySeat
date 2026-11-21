import { createApp } from 'vue'
import { createPinia } from 'pinia'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
// 组件样式统一从这里引入（组件本身由 unplugin-vue-components 按需自动导入）
import 'element-plus/dist/index.css'
// Leaflet 地图样式（必须引入，否则瓦片没有绝对定位，会按普通图片流式排列）
import 'leaflet/dist/leaflet.css'

// 图标按需注册：全量引入会多打包约 250KB，实际只用到下面这些
// （新增图标时记得在这里补一行，否则页面会显示空白）
import {
  Aim, AlarmClock, ArrowLeft, ArrowRight, Avatar, Back, Bell, Box,
  Calendar, CaretBottom, Check, Checked, CircleCheck, CircleClose,
  Clock, Coffee, Collection, DataLine, Document, Grid, Histogram,
  InfoFilled, Iphone, Lightning, List, Location, Lock, MapLocation,
  Monitor, Odometer, OfficeBuilding, Phone, PieChart, Plus, Reading,
  Refresh, RefreshLeft, School, Search, Setting, SwitchButton, Tickets,
  Timer, TrendCharts, User, UserFilled, Warning
} from '@element-plus/icons-vue'

import App from './App.vue'
import router from './router'
import './styles/global.css'

const app = createApp(App)

const icons = {
  Aim, AlarmClock, ArrowLeft, ArrowRight, Avatar, Back, Bell, Box,
  Calendar, CaretBottom, Check, Checked, CircleCheck, CircleClose,
  Clock, Coffee, Collection, DataLine, Document, Grid, Histogram,
  InfoFilled, Iphone, Lightning, List, Location, Lock, MapLocation,
  Monitor, Odometer, OfficeBuilding, Phone, PieChart, Plus, Reading,
  Refresh, RefreshLeft, School, Search, Setting, SwitchButton, Tickets,
  Timer, TrendCharts, User, UserFilled, Warning
}
for (const [key, component] of Object.entries(icons)) {
  app.component(key, component)
}

app.use(createPinia())
app.use(router)
// 中文语言包通过 App.vue 里的 el-config-provider 提供
app.mount('#app')