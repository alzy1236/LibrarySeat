<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1>预约座位</h1>
        <div class="sub">自习楼 1—5 层 · 每名学生每天可预约一个座位</div>
      </div>
      <el-button :icon="Refresh" @click="load">刷新</el-button>
    </div>

    <div class="toolbar">
      <el-radio-group v-model="roomType" @change="load">
        <el-radio-button label="">全部楼层</el-radio-button>
        <el-radio-button label="LONG">固定座位层</el-radio-button>
        <el-radio-button label="SHORT">普通座位层</el-radio-button>
      </el-radio-group>
      <el-input v-model="keyword" placeholder="搜索楼层" clearable style="width: 220px" :prefix-icon="Search" />
      <div v-if="today" class="today-tip">
        <el-icon><Location /></el-icon>
        今日已预约 <b>{{ today.roomName }} · {{ today.seatNo }}</b>
      </div>
    </div>

    <div v-loading="loading" class="floor-list">
      <div
        v-for="room in filteredRooms"
        :key="room.id"
        class="floor"
        :class="{ long: room.roomType === 'LONG' }"
        @click="goRoom(room)"
      >
        <div class="floor-no">
          <span>{{ floorNo(room) }}</span>
          <small>层</small>
        </div>

        <div class="floor-body">
          <div class="floor-top">
            <b>{{ room.name }}</b>
            <span class="pill" :class="room.roomType === 'LONG' ? 'pill-long' : 'pill-short'">
              {{ room.roomType === 'LONG' ? '固定座位' : '普通座位' }}
            </span>
          </div>
          <p class="floor-desc">{{ room.description }}</p>
          <div class="floor-meta">
            <span><el-icon><Clock /></el-icon>{{ room.openStart }} - {{ room.openEnd }}</span>
            <span><el-icon><Monitor /></el-icon>{{ room.capacity }} 个座位</span>
            <span><el-icon><Location /></el-icon>{{ room.location }}</span>
          </div>
        </div>

        <div class="floor-stat">
          <div class="num">{{ room.availableCount }}</div>
          <div class="lbl">剩余座位</div>
          <div class="bar">
            <i :style="{ width: usage(room) + '%' }"></i>
          </div>
          <div class="rate">已用 {{ usage(room) }}% · 在座 {{ room.seatedCount }}</div>
        </div>

        <div class="floor-go">
          <el-icon><ArrowRight /></el-icon>
        </div>
      </div>

      <el-empty v-if="!loading && filteredRooms.length === 0" description="没有符合条件的楼层" />
    </div>

    <div class="rule-grid">
      <div class="rule-card long-rule">
        <div class="rc-title"><el-icon><Collection /></el-icon>固定座位层（3F / 4F）</div>
        <p>可存放学习用品；须持有审核通过的固定座位。每日 08:30 前签到，21:00—24:00 签退；每月有效出勤不足 20 天将被取消座位。</p>
      </div>
      <div class="rule-card short-rule">
        <div class="rc-title"><el-icon><Box /></el-icon>普通座位层（1F / 2F / 5F）</div>
        <p>禁止存放个人物品，每天 24:00 前必须带走随身物品并离场，超时未签退将记录违规。</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Refresh, Search } from '@element-plus/icons-vue'
import { studentRooms } from '@/api'
import { fetchTodayActive } from '@/utils/today'

const router = useRouter()
const loading = ref(false)
const rooms = ref([])
const roomType = ref('')
const keyword = ref('')
const today = ref(null)

const filteredRooms = computed(() =>
  rooms.value
    .filter((r) => !keyword.value || r.name.includes(keyword.value) || (r.location || '').includes(keyword.value))
    .sort((a, b) => floorNo(a) - floorNo(b))
)

function floorNo(room) {
  const m = (room.location || room.name || '').match(/(\d)\s*层/)
  return m ? Number(m[1]) : 0
}

function usage(room) {
  if (!room.capacity) return 0
  return Math.round((room.reservedCount / room.capacity) * 100)
}

async function load() {
  loading.value = true
  try {
    const [r, t] = await Promise.all([studentRooms(roomType.value || undefined), fetchTodayActive()])
    rooms.value = r.data || []
    today.value = t
  } finally {
    loading.value = false
  }
}

function goRoom(room) {
  router.push(`/student/rooms/${room.id}`)
}

onMounted(load)
</script>

<style scoped>
.today-tip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--c-text-2);
  margin-left: auto;
  background: var(--brand-soft);
  border: 1px solid var(--brand-border);
  border-radius: 999px;
  padding: 6px 14px;
}

.floor-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-height: 160px;
}

.floor {
  display: grid;
  grid-template-columns: 88px minmax(0, 1fr) 180px 40px;
  gap: 20px;
  align-items: center;
  background: var(--c-surface);
  border: 1px solid var(--c-line);
  border-radius: var(--radius);
  padding: 18px 20px;
  cursor: pointer;
  transition: all 0.16s ease;
}

.floor:hover {
  border-color: var(--brand);
  box-shadow: var(--shadow);
  transform: translateX(2px);
}

.floor-no {
  width: 72px;
  height: 72px;
  border-radius: 16px;
  background: var(--c-surface-2);
  border: 1px solid var(--c-line);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: var(--c-accent);
}

.floor.long .floor-no {
  background: var(--brand-soft);
  border-color: var(--brand-border);
  color: var(--brand);
}

.floor-no span {
  font-size: 26px;
  font-weight: 800;
  line-height: 1;
}

.floor-no small {
  font-size: 11px;
  margin-top: 2px;
  opacity: 0.7;
}

.floor-top {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.floor-top b {
  font-size: 16px;
}

.floor-desc {
  margin: 6px 0 10px;
  font-size: 13px;
  color: var(--c-text-2);
  line-height: 1.6;
}

.floor-meta {
  display: flex;
  gap: 18px;
  flex-wrap: wrap;
  font-size: 12px;
  color: var(--c-text-3);
}

.floor-meta span {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.floor-stat {
  text-align: right;
}

.floor-stat .num {
  font-size: 26px;
  font-weight: 800;
  color: var(--brand);
  line-height: 1;
}

.floor-stat .lbl {
  font-size: 11px;
  color: var(--c-text-3);
  margin: 4px 0 8px;
}

.floor-stat .bar {
  height: 6px;
  border-radius: 999px;
  background: var(--c-line);
  overflow: hidden;
}

.floor-stat .bar i {
  display: block;
  height: 100%;
  background: var(--brand);
  border-radius: 999px;
}

.floor-stat .rate {
  font-size: 11px;
  color: var(--c-text-3);
  margin-top: 6px;
}

.floor-go {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: var(--c-surface-2);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--c-text-3);
}

.floor:hover .floor-go {
  background: var(--brand);
  color: #fff;
}

.rule-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  margin-top: 20px;
}

.rule-card {
  border-radius: var(--radius);
  padding: 16px 18px;
  font-size: 13px;
  line-height: 1.7;
}

.rule-card.long-rule {
  background: var(--brand-soft);
  border: 1px solid var(--brand-border);
  color: #134e4a;
}

.rule-card.short-rule {
  background: #fffbeb;
  border: 1px solid #fde68a;
  color: #92400e;
}

.rc-title {
  font-weight: 700;
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 6px;
}

.rule-card p {
  margin: 0;
  opacity: 0.9;
}

@media (max-width: 1100px) {
  .floor {
    grid-template-columns: 70px minmax(0, 1fr) 120px;
  }
  .floor-go {
    display: none;
  }
  .rule-grid {
    grid-template-columns: 1fr;
  }
}
</style>