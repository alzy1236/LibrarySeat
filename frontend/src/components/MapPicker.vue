<template>
  <div class="map-picker">
    <div ref="mapEl" class="map-canvas"></div>

    <div class="map-tools">
      <el-button size="small" :icon="Aim" :loading="locating" @click="useMyLocation">使用我的当前位置</el-button>
      <el-button size="small" :icon="Refresh" @click="resetView">回到选中的点</el-button>
      <span class="hint">在地图上点击即可设置签到点</span>
    </div>
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import L from 'leaflet'
import { ElMessage } from 'element-plus'
import { Aim, Refresh } from '@element-plus/icons-vue'

const props = defineProps({
  lat: { type: Number, default: null },
  lng: { type: Number, default: null },
  radius: { type: Number, default: 100 }
})
const emit = defineEmits(['update:lat', 'update:lng', 'picked'])

const mapEl = ref(null)
const locating = ref(false)
let map = null
let marker = null
let circle = null
let ro = null

/* ============================================================
 * 坐标系转换
 * 高德瓦片使用 GCJ-02（火星坐标），而浏览器 Geolocation / GPS 是 WGS-84。
 * 存库统一用 WGS-84，只在"地图显示"这一层做转换，避免几十~几百米偏移。
 * ============================================================ */
const PI = Math.PI
const A = 6378245.0
const EE = 0.00669342162296594323

function outOfChina(lat, lng) {
  return lng < 72.004 || lng > 137.8347 || lat < 0.8293 || lat > 55.8271
}

function transformLat(x, y) {
  let ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * Math.sqrt(Math.abs(x))
  ret += ((20.0 * Math.sin(6.0 * x * PI) + 20.0 * Math.sin(2.0 * x * PI)) * 2.0) / 3.0
  ret += ((20.0 * Math.sin(y * PI) + 40.0 * Math.sin((y / 3.0) * PI)) * 2.0) / 3.0
  ret += ((160.0 * Math.sin((y / 12.0) * PI) + 320 * Math.sin((y * PI) / 30.0)) * 2.0) / 3.0
  return ret
}

function transformLng(x, y) {
  let ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * Math.sqrt(Math.abs(x))
  ret += ((20.0 * Math.sin(6.0 * x * PI) + 20.0 * Math.sin(2.0 * x * PI)) * 2.0) / 3.0
  ret += ((20.0 * Math.sin(x * PI) + 40.0 * Math.sin((x / 3.0) * PI)) * 2.0) / 3.0
  ret += ((150.0 * Math.sin((x / 12.0) * PI) + 300.0 * Math.sin((x / 30.0) * PI)) * 2.0) / 3.0
  return ret
}

/** WGS-84 -> GCJ-02（用于在地图上显示） */
function wgs2gcj(lat, lng) {
  if (outOfChina(lat, lng)) return [lat, lng]
  let dLat = transformLat(lng - 105.0, lat - 35.0)
  let dLng = transformLng(lng - 105.0, lat - 35.0)
  const radLat = (lat / 180.0) * PI
  let magic = Math.sin(radLat)
  magic = 1 - EE * magic * magic
  const sqrtMagic = Math.sqrt(magic)
  dLat = (dLat * 180.0) / (((A * (1 - EE)) / (magic * sqrtMagic)) * PI)
  dLng = (dLng * 180.0) / ((A / sqrtMagic) * Math.cos(radLat) * PI)
  return [lat + dLat, lng + dLng]
}

/** GCJ-02 -> WGS-84（用于把地图点击位置还原成真实坐标） */
function gcj2wgs(lat, lng) {
  if (outOfChina(lat, lng)) return [lat, lng]
  const [gLat, gLng] = wgs2gcj(lat, lng)
  return [lat * 2 - gLat, lng * 2 - gLng]
}

/* ============================================================ */
const TILE_URL =
  'https://webrd0{s}.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&x={x}&y={y}&z={z}'
const TILE_ATTR = '&copy; 高德地图'

/** props 的 WGS 坐标 -> 地图上的 GCJ 坐标 */
function toMapPoint(lat, lng) {
  return wgs2gcj(Number(lat), Number(lng))
}

function setPoint(lat, lng, emitEvent = true) {
  if (!map) return
  const [mLan, mLng] = toMapPoint(lat, lng)
  if (marker) {
    marker.setLatLng([mLan, mLng])
  } else {
    marker = L.circleMarker([mLan, mLng], {
      radius: 8,
      color: '#2563eb',
      weight: 3,
      fillColor: '#2563eb',
      fillOpacity: 0.9
    }).addTo(map)
  }
  if (circle) {
    circle.setLatLng([mLan, mLng]).setRadius(props.radius || 100)
  } else {
    circle = L.circle([mLan, mLng], {
      radius: props.radius || 100,
      color: '#2563eb',
      weight: 1.5,
      fillColor: '#2563eb',
      fillOpacity: 0.12
    }).addTo(map)
  }
  if (emitEvent) {
    emit('update:lat', Number(Number(lat).toFixed(7)))
    emit('update:lng', Number(Number(lng).toFixed(7)))
    emit('picked', { lat: Number(Number(lat).toFixed(7)), lng: Number(Number(lng).toFixed(7)) })
  }
}

function resetView() {
  if (!map) return
  if (props.lat && props.lng) {
    const [mLan, mLng] = toMapPoint(props.lat, props.lng)
    map.setView([mLan, mLng], 17)
    setPoint(props.lat, props.lng, false)
  } else {
    map.setView([39.909, 116.397], 16)
  }
}

function useMyLocation() {
  if (!navigator.geolocation) {
    ElMessage.error('当前浏览器不支持定位')
    return
  }
  locating.value = true
  navigator.geolocation.getCurrentPosition(
    (pos) => {
      locating.value = false
      const lat = pos.coords.latitude
      const lng = pos.coords.longitude
      setPoint(lat, lng)
      const [mLan, mLng] = toMapPoint(lat, lng)
      map.setView([mLan, mLng], 18)
      ElMessage.success(`已取当前位置，精度约 ${Math.round(pos.coords.accuracy || 0)} 米`)
    },
    (err) => {
      locating.value = false
      const msg =
        err.code === 1
          ? '定位被拒绝，请在浏览器地址栏允许定位权限'
          : err.code === 2
            ? '获取不到位置信息，请检查网络或系统定位服务'
            : '定位超时，请重试'
      ElMessage.error(msg)
    },
    { enableHighAccuracy: true, timeout: 15000, maximumAge: 0 }
  )
}

onMounted(() => {
  // 等容器拿到真实尺寸再初始化，避免 Leaflet 按 0/极窄尺寸计算像素原点，
  // 造成"瓦片只出现在一小块区域"的问题
  let tries = 0
  const init = () => {
    if (!mapEl.value || mapEl.value.clientWidth < 40) {
      if (tries++ < 60) requestAnimationFrame(init)
      return
    }
    createMap()
  }
  init()
})

function createMap() {
  const start = props.lat && props.lng ? toMapPoint(props.lat, props.lng) : [39.909, 116.397]
  map = L.map(mapEl.value, {
    zoomControl: true,
    attributionControl: true,
    // 关闭所有动画：嵌入页面/弹窗时 CSS 动画会干扰 Leaflet 的像素原点计算，
    // 导致瓦片错位（表现为只显示一小块/错行）
    zoomAnimation: false,
    fadeAnimation: false,
    markerZoomAnimation: false,
    inertia: false
  }).setView(start, props.lat && props.lng ? 17 : 16)
  L.tileLayer(TILE_URL, { subdomains: '1234', maxZoom: 18, attribution: TILE_ATTR }).addTo(map)

  // 地图点击拿到的是 GCJ-02，统一转回 WGS-84 再存
  map.on('click', (e) => {
    const [wLat, wLng] = gcj2wgs(e.latlng.lat, e.latlng.lng)
    setPoint(wLat, wLng, true)
  })

  if (props.lat && props.lng) setPoint(props.lat, props.lng, false)

  // 容器尺寸后续变化（窗口缩放等）只需让 Leaflet 重算即可
  if (typeof ResizeObserver !== 'undefined') {
    ro = new ResizeObserver(() => {
      if (map) map.invalidateSize(false)
    })
    ro.observe(mapEl.value)
  }

  // 诊断出口（排查瓦片加载问题用，不影响功能）
  window.__mapPicker = { getMap: () => map }
}

watch(
  () => props.radius,
  (v) => {
    if (circle) circle.setRadius(v || 100)
  }
)

watch(
  () => [props.lat, props.lng],
  ([la, ln], [oldLa, oldLn]) => {
    if (la == null || ln == null) return
    if (la === oldLa && ln === oldLn) return
    setPoint(la, ln, false)
  }
)

onBeforeUnmount(() => {
  if (window.__mapPicker) delete window.__mapPicker
  if (ro) {
    ro.disconnect()
    ro = null
  }
  if (map) {
    map.remove()
    map = null
    marker = null
    circle = null
  }
})
</script>

<style scoped>
.map-picker {
  border: 1px solid var(--c-line);
  border-radius: var(--radius);
  overflow: hidden;
  background: #fff;
}

.map-canvas {
  height: 320px;
  width: 100%;
  background: var(--gray-100);
}

.map-tools {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border-top: 1px solid var(--gray-200);
  background: var(--gray-50);
  flex-wrap: wrap;
}

.hint {
  font-size: 11.5px;
  color: var(--gray-400);
  margin-left: auto;
}

.map-canvas :deep(.leaflet-control-attribution) {
  font-size: 10px;
}
</style>