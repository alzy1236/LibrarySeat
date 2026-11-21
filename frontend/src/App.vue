<template>
  <!-- 全局中文语言包（Element Plus 组件按需引入后，语言需在这里注入） -->
  <el-config-provider :locale="zhCn">
    <router-view />
  </el-config-provider>
</template>

<script setup>
import { onMounted, onUnmounted } from 'vue'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import { useClockStore } from '@/stores/clock'

// 全局实时时钟：应用启动即连接服务器时间通道
const clock = useClockStore()

onMounted(() => clock.connect())
onUnmounted(() => clock.disconnect())
</script>