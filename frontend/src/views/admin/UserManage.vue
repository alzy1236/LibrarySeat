<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1>学生账号管理</h1>
        <div class="sub">维护学生 / 管理员账号，禁用后无法登录 · 默认密码 123456</div>
      </div>
      <el-button type="primary" :icon="Plus" @click="openDialog()">新增账号</el-button>
    </div>

    <div class="card">
      <div class="toolbar">
        <el-input v-model="query.keyword" placeholder="姓名 / 学号 / 学院" clearable style="width: 220px" :prefix-icon="Search" @keyup.enter="search" />
        <el-select v-model="query.role" placeholder="全部角色" clearable style="width: 130px">
          <el-option label="学生" value="STUDENT" />
          <el-option label="管理员" value="ADMIN" />
        </el-select>
        <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 130px">
          <el-option label="正常" :value="1" />
          <el-option label="禁用" :value="0" />
        </el-select>
        <el-button type="primary" :icon="Search" @click="search">查询</el-button>
        <el-button :icon="RefreshLeft" @click="reset">重置</el-button>
      </div>

      <el-table v-loading="loading" :data="users" stripe border size="small" empty-text="暂无账号">
        <el-table-column prop="username" label="登录账号" width="120" />
        <el-table-column prop="realName" label="姓名" width="100" />
        <el-table-column label="角色" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.role === 'ADMIN' ? 'danger' : 'primary'" effect="plain" size="small">{{ row.roleName }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="studentNo" label="学号" width="120" />
        <el-table-column prop="college" label="学院" min-width="130" show-overflow-tooltip />
        <el-table-column prop="major" label="专业班级" min-width="150" show-overflow-tooltip />
        <el-table-column prop="phone" label="电话" width="130" />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-switch
              :model-value="row.status"
              :active-value="1"
              :inactive-value="0"
              :disabled="row.role === 'ADMIN'"
              @change="(v) => toggle(row, v)"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="210" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
            <el-button link type="warning" @click="resetPwd(row)">重置密码</el-button>
            <el-button link type="danger" :disabled="row.role === 'ADMIN'" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="query.pageNum"
          v-model:page-size="query.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @current-change="load"
          @size-change="load"
        />
      </div>
    </div>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑账号' : '新增账号'" width="540px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="登录账号" prop="username">
          <el-input v-model="form.username" placeholder="学号或工号" />
        </el-form-item>
        <el-form-item label="姓名" prop="realName">
          <el-input v-model="form.realName" placeholder="真实姓名" />
        </el-form-item>
        <el-form-item label="密码" :prop="form.id ? '' : 'password'">
          <el-input v-model="form.password" type="password" show-password :placeholder="form.id ? '留空表示不修改' : '默认 123456'" />
        </el-form-item>
        <el-form-item label="角色">
          <el-radio-group v-model="form.role">
            <el-radio label="STUDENT">学生</el-radio>
            <el-radio label="ADMIN">管理员</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="学号">
          <el-input v-model="form.studentNo" placeholder="默认与账号一致" />
        </el-form-item>
        <el-form-item label="学院">
          <el-input v-model="form.college" />
        </el-form-item>
        <el-form-item label="专业班级">
          <el-input v-model="form.major" />
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input v-model="form.phone" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="正常" inactive-text="禁用" />
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
import { Plus, Search, RefreshLeft } from '@element-plus/icons-vue'
import { adminUsers, addUser, updateUser, deleteUser, resetUserPassword, toggleUserStatus } from '@/api'

const loading = ref(false)
const saving = ref(false)
const users = ref([])
const total = ref(0)
const dialogVisible = ref(false)
const formRef = ref(null)
const query = reactive({ pageNum: 1, pageSize: 10, keyword: '', role: '', status: null })

const defaultForm = () => ({
  id: null,
  username: '',
  realName: '',
  password: '',
  role: 'STUDENT',
  studentNo: '',
  college: '',
  major: '',
  phone: '',
  status: 1
})
const form = reactive(defaultForm())
const rules = {
  username: [{ required: true, message: '请输入登录账号', trigger: 'blur' }],
  realName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function load() {
  loading.value = true
  try {
    const res = await adminUsers({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      keyword: query.keyword || undefined,
      role: query.role || undefined,
      status: query.status === null || query.status === '' ? undefined : query.status
    })
    users.value = res.data.records || []
    total.value = res.data.total || 0
  } finally {
    loading.value = false
  }
}

function search() {
  query.pageNum = 1
  load()
}

function reset() {
  query.keyword = ''
  query.role = ''
  query.status = null
  search()
}

function openDialog(row) {
  Object.assign(form, defaultForm(), row ? { ...row, password: '' } : {})
  dialogVisible.value = true
}

async function save() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    if (form.id) {
      await updateUser({ ...form, password: form.password || null })
      ElMessage.success('账号已更新')
    } else {
      await addUser({ ...form })
      ElMessage.success('账号已新增')
    }
    dialogVisible.value = false
    load()
  } catch (e) {
    /* ignore */
  } finally {
    saving.value = false
  }
}

async function toggle(row, value) {
  try {
    await toggleUserStatus(row.id, value)
    row.status = value
    ElMessage.success(value === 1 ? '账号已启用' : '账号已禁用')
  } catch (e) {
    load()
  }
}

async function resetPwd(row) {
  try {
    await ElMessageBox.confirm(`确定将 ${row.realName}（${row.username}）的密码重置为 123456 吗？`, '重置密码', { type: 'warning' })
    await resetUserPassword(row.id)
    ElMessage.success('密码已重置为 123456')
  } catch (e) {
    /* ignore */
  }
}

async function remove(row) {
  try {
    await ElMessageBox.confirm(`确定删除账号 ${row.username} 吗？`, '删除确认', { type: 'warning' })
    await deleteUser(row.id)
    ElMessage.success('已删除')
    load()
  } catch (e) {
    /* ignore */
  }
}

onMounted(load)
</script>

