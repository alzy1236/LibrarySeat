<template>
  <router-view />
</template>

<script setup>
import { onMounted, onUnmounted } from 'vue'
import { useClockStore } from '@/stores/clock'

// 全局实时时钟：应用启动即连接服务器时间通道
const clock = useClockStore()

onMounted(() => clock.connect())
onUnmounted(() => clock.disconnect())
</script>