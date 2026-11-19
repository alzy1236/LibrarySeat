<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1>公告管理</h1>
        <div class="sub">发布自习室使用须知、考核通知等公告，学生端首页可见</div>
      </div>
      <el-button type="primary" :icon="Plus" @click="openDialog()">发布公告</el-button>
    </div>

    <div class="card">
      <div class="toolbar">
        <el-input v-model="keyword" placeholder="搜索公告标题" clearable style="width: 240px" :prefix-icon="Search" @keyup.enter="load" />
        <el-button type="primary" @click="load">查询</el-button>
      </div>

      <el-table v-loading="loading" :data="notices" stripe border size="small" empty-text="暂无公告">
        <el-table-column prop="id" label="ID" width="70" align="center" />
        <el-table-column prop="title" label="标题" min-width="220" />
        <el-table-column prop="content" label="内容" min-width="320" show-overflow-tooltip />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ row.status === 1 ? '已发布' : '已下线' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="发布时间" width="170" />
        <el-table-column label="操作" width="200" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
            <el-button link :type="row.status === 1 ? 'warning' : 'success'" @click="toggle(row)">
              {{ row.status === 1 ? '下线' : '发布' }}
            </el-button>
            <el-button link type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑公告' : '发布公告'" width="620px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="70px">
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" maxlength="100" show-word-limit />
        </el-form-item>
        <el-form-item label="内容" prop="content">
          <el-input v-model="form.content" type="textarea" :rows="8" maxlength="2000" show-word-limit />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :label="1">立即发布</el-radio>
            <el-radio :label="0">暂不发布</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import { adminNotices, addNotice, updateNotice, deleteNotice } from '@/api'

const loading = ref(false)
const saving = ref(false)
const notices = ref([])
const keyword = ref('')
const dialogVisible = ref(false)
const formRef = ref(null)

const form = reactive({ id: null, title: '', content: '', status: 1 })
const rules = {
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  content: [{ required: true, message: '请输入公告内容', trigger: 'blur' }]
}

async function load() {
  loading.value = true
  try {
    const res = await adminNotices({ keyword: keyword.value || undefined })
    notices.value = res.data || []
  } finally {
    loading.value = false
  }
}

function openDialog(row) {
  Object.assign(form, { id: row?.id || null, title: row?.title || '', content: row?.content || '', status: row?.status ?? 1 })
  dialogVisible.value = true
}

async function save() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    if (form.id) {
      await updateNotice({ ...form })
      ElMessage.success('公告已更新')
    } else {
      await addNotice({ ...form })
      ElMessage.success('公告已发布')
    }
    dialogVisible.value = false
    load()
  } catch (e) {
    /* ignore */
  } finally {
    saving.value = false
  }
}

async function toggle(row) {
  try {
    await updateNotice({ ...row, status: row.status === 1 ? 0 : 1 })
    ElMessage.success(row.status === 1 ? '公告已下线' : '公告已发布')
    load()
  } catch (e) {
    /* ignore */
  }
}

async function remove(row) {
  try {
    await ElMessageBox.confirm(`确定删除公告「${row.title}」吗？`, '删除确认', { type: 'warning' })
    await deleteNotice(row.id)
    ElMessage.success('已删除')
    load()
  } catch (e) {
    /* ignore */
  }
}

onMounted(load)
</script>

