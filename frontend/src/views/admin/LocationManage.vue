<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1>签到点管理</h1>
        <div class="sub">设置允许签到 / 签退的地理位置与半径，学生必须在该范围内才能完成操作</div>
      </div>
      <el-button type="primary" :icon="Plus" @click="openCreate">新增签到点</el-button>
    </div>

    <!-- 编辑器（页面内嵌，避免弹窗动画导致地图错位） -->
    <div v-if="editorVisible" class="card editor">
      <div class="card-head">
        <h3>
          <span class="ico"><el-icon><Location /></el-icon></span>
          {{ form.id ? '编辑签到点' : '新增签到点' }}
        </h3>
        <div class="editor-actions">
          <el-button @click="closeEditor">取消</el-button>
          <el-button type="primary" :loading="saving" @click="save">保存</el-button>
        </div>
      </div>

      <div class="editor-body">
        <el-form ref="formRef" :model="form" :rules="rules" label-width="88px" class="editor-form">
          <el-form-item label="名称" prop="name">
            <el-input v-model="form.name" placeholder="例如：自习楼一层大厅签到处" maxlength="64" />
          </el-form-item>

          <el-form-item label="适用范围">
            <el-select v-model="form.roomId" placeholder="全楼通用" clearable style="width: 100%">
              <el-option label="全楼通用（所有自习室）" :value="null" />
              <el-option v-for="r in rooms" :key="r.id" :label="r.name" :value="r.id" />
            </el-select>
          </el-form-item>

          <el-form-item label="经纬度">
            <div class="coord-row">
              <el-input-number
                v-model="form.latitude"
                :precision="7"
                :step="0.0001"
                :controls="false"
                placeholder="纬度"
                style="width: 130px"
              />
              <el-input-number
                v-model="form.longitude"
                :precision="7"
                :step="0.0001"
                :controls="false"
                placeholder="经度"
                style="width: 130px"
              />
            </div>
          </el-form-item>

          <el-form-item label="允许半径">
            <div class="radius-row">
              <el-slider v-model="form.radius" :min="10" :max="500" :step="10" style="flex: 1" />
              <span class="mono radius-val">{{ form.radius }} 米</span>
            </div>
          </el-form-item>

          <el-form-item label="启用">
            <el-switch v-model="form.enabled" :active-value="1" :inactive-value="0" />
          </el-form-item>

          <el-form-item label="备注">
            <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="255" show-word-limit />
          </el-form-item>
        </el-form>

        <div class="editor-map">
          <MapPicker
            v-model:lat="form.latitude"
            v-model:lng="form.longitude"
            :radius="form.radius"
          />
          <div class="map-hint">
            在地图上点击可直接设置签到点；也可以点「使用我的当前位置」，由管理员站在签到点处自动取点。
          </div>
        </div>
      </div>
    </div>

    <div class="card">
      <div class="card-head">
        <h3><span class="ico"><el-icon><MapLocation /></el-icon></span>签到点列表</h3>
        <div class="toolbar">
          <el-input
            v-model="keyword"
            placeholder="搜索名称 / 自习室"
            clearable
            style="width: 200px"
            @keyup.enter="load"
            @clear="load"
          >
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <el-button :icon="Search" @click="load">查询</el-button>
          <el-button :icon="Refresh" @click="resetFilter">重置</el-button>
        </div>
      </div>

      <el-table :data="rows" v-loading="loading" empty-text="还没有设置签到点，点击右上角新增">
        <el-table-column prop="name" label="签到点名称" min-width="170" />
        <el-table-column label="适用范围" min-width="140">
          <template #default="{ row }">
            <el-tag v-if="!row.roomId" type="info" size="small">全楼通用</el-tag>
            <span v-else>{{ row.roomName || ('房间 #' + row.roomId) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="坐标（WGS-84）" min-width="200">
          <template #default="{ row }">
            <span class="mono">{{ Number(row.latitude).toFixed(6) }}, {{ Number(row.longitude).toFixed(6) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="radius" label="半径" width="90">
          <template #default="{ row }">{{ row.radius }} 米</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-switch :model-value="row.enabled === 1" size="small" @change="(v) => toggle(row, v)" />
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="150" show-overflow-tooltip />
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-alert
      class="tip"
      type="info"
      :closable="false"
      show-icon
      title="坐标统一使用 WGS-84（浏览器定位 / GPS）。地图底图为高德（GCJ-02），本页已自动做坐标转换，请勿手工填入高德取点坐标。"
    />
  </div>
</template>

<script setup>
import { nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search, Refresh, Location, MapLocation } from '@element-plus/icons-vue'
import MapPicker from '@/components/MapPicker.vue'
import {
  adminLocations, addLocation, updateLocation, deleteLocation, toggleLocation, locationRooms
} from '@/api'

const rows = ref([])
const rooms = ref([])
const keyword = ref('')
const loading = ref(false)
const saving = ref(false)
const editorVisible = ref(false)
const formRef = ref(null)

const form = reactive({
  id: null,
  name: '',
  roomId: null,
  latitude: null,
  longitude: null,
  radius: 100,
  enabled: 1,
  remark: ''
})

const rules = {
  name: [{ required: true, message: '请输入签到点名称', trigger: 'blur' }]
}

async function load() {
  loading.value = true
  try {
    const res = await adminLocations(keyword.value || undefined)
    rows.value = res.data || []
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    loading.value = false
  }
}

function resetFilter() {
  keyword.value = ''
  load()
}

function openCreate() {
  Object.assign(form, {
    id: null, name: '', roomId: null, latitude: null, longitude: null,
    radius: 100, enabled: 1, remark: ''
  })
  editorVisible.value = true
  nextTick(() => formRef.value?.clearValidate())
}

function openEdit(row) {
  Object.assign(form, {
    id: row.id,
    name: row.name,
    roomId: row.roomId,
    latitude: Number(row.latitude),
    longitude: Number(row.longitude),
    radius: row.radius,
    enabled: row.enabled,
    remark: row.remark || ''
  })
  editorVisible.value = true
  nextTick(() => formRef.value?.clearValidate())
}

function closeEditor() {
  editorVisible.value = false
}

async function save() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  if (form.latitude == null || form.longitude == null) {
    ElMessage.warning('请先在地图上点击选择签到点位置')
    return
  }
  saving.value = true
  try {
    const payload = { ...form }
    if (payload.id) {
      await updateLocation(payload)
      ElMessage.success('签到点已更新')
    } else {
      delete payload.id
      await addLocation(payload)
      ElMessage.success('签到点已添加')
    }
    editorVisible.value = false
    load()
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    saving.value = false
  }
}

async function remove(row) {
  try {
    await ElMessageBox.confirm(`确定删除「${row.name}」吗？`, '删除签到点', {
      type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消'
    })
  } catch (e) {
    return
  }
  try {
    await deleteLocation(row.id)
    ElMessage.success('已删除')
    load()
  } catch (e) {
    /* 拦截器已提示 */
  }
}

async function toggle(row, val) {
  try {
    await toggleLocation(row.id, val ? 1 : 0)
    row.enabled = val ? 1 : 0
    ElMessage.success(val ? '已启用' : '已停用')
  } catch (e) {
    /* 拦截器已提示 */
  }
}

onMounted(async () => {
  load()
  try {
    const res = await locationRooms()
    rooms.value = res.data || []
  } catch (e) {
    rooms.value = []
  }
})
</script>

<style scoped>
.toolbar {
  display: flex;
  gap: 8px;
  align-items: center;
}

.tip {
  margin-top: 12px;
  border-radius: var(--radius);
}

/* ---------- 编辑器 ---------- */
.editor {
  margin-bottom: 12px;
  border-color: var(--brand-border);
}

.editor-actions {
  display: flex;
  gap: 8px;
}

.editor-body {
  display: grid;
  grid-template-columns: minmax(0, 380px) minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}

.editor-form {
  min-width: 0;
}

.editor-map {
  min-width: 0;
}

.map-hint {
  margin-top: 8px;
  font-size: 11.5px;
  color: var(--gray-400);
  line-height: 1.6;
}

.coord-row {
  display: flex;
  gap: 8px;
}

.radius-row {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
}

.radius-val {
  width: 58px;
  text-align: right;
  font-size: 12.5px;
  color: var(--gray-700);
}

@media (max-width: 1100px) {
  .editor-body {
    grid-template-columns: 1fr;
  }
}
</style>