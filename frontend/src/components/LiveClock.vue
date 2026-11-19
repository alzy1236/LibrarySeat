<template>
  <div class="live-clock" :class="{ offline: !clock.wsConnected }" :title="title">
    <span class="dot"></span>
    <span>{{ clock.weekday }} {{ clock.time }}</span>
    <el-tooltip :content="title" placement="bottom">
      <el-icon style="cursor: help"><InfoFilled /></el-icon>
    </el-tooltip>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useClockStore } from '@/stores/clock'

const props = defineProps({
  detail: { type: Boolean, default: false }
})

const clock = useClockStore()

const title = computed(() => {
  const src = clock.wsConnected ? 'WebSocket 实时同步' : clock.synced ? 'HTTP 轮询同步' : '等待同步'
  return `服务器时间（${src}）：${clock.today} ${clock.time}`
})
</script>