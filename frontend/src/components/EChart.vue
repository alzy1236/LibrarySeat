<template>
  <div ref="el" :style="{ width: '100%', height: height }"></div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref, watch, nextTick } from 'vue'
import * as echarts from 'echarts'

const props = defineProps({
  option: { type: Object, required: true },
  height: { type: String, default: '320px' },
  loading: { type: Boolean, default: false }
})

const el = ref(null)
let chart = null

function render() {
  if (!el.value) return
  if (!chart) {
    chart = echarts.init(el.value)
  }
  chart.setOption(props.option, true)
}

function resize() {
  chart && chart.resize()
}

onMounted(async () => {
  await nextTick()
  render()
  window.addEventListener('resize', resize)
})

watch(
  () => props.option,
  async () => {
    await nextTick()
    render()
  },
  { deep: true }
)

watch(
  () => props.loading,
  (v) => {
    if (!chart) return
    v ? chart.showLoading({ text: '加载中...' }) : chart.hideLoading()
  }
)

onBeforeUnmount(() => {
  window.removeEventListener('resize', resize)
  chart && chart.dispose()
  chart = null
})
</script>