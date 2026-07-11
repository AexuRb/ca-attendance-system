<template>
  <section class="work-section tab-members">
    <div class="section-head">
      <h3>成员管理</h3>
      <button class="ghost-button" :disabled="busy" @click="loadUsers"><RefreshCw :size="16" />刷新</button>
    </div>

    <div class="member-action-strip">
      <button class="ghost-button" :class="{ active: showCreateMember }" @click="toggleCreateMemberForm">
        <UserPlus :size="17" /><span>新增成员</span>
      </button>
      <button class="ghost-button" :class="{ active: showImportMembers }" @click="showImportMembers = !showImportMembers">
        <Upload :size="17" /><span>批量导入</span>
      </button>
    </div>

    <div v-if="showCreateMember" class="inline-form-block member-action-panel">
      <div class="subsection-head"><h4>新增成员</h4></div>
      <form class="create-member-form" novalidate @input="setDirty('form', true)" @change="setDirty('form', true)" @submit.prevent="createMember">
        <label for="newMemberStudentNo"><span>学号</span><input id="newMemberStudentNo" v-model.trim="newMember.studentNo" name="studentNo" inputmode="numeric" autocomplete="off" :aria-invalid="Boolean(errors.studentNo)" required /><small v-if="errors.studentNo" class="field-error">{{ errors.studentNo }}</small></label>
        <label for="newMemberName"><span>姓名</span><input id="newMemberName" v-model.trim="newMember.name" name="name" autocomplete="name" :aria-invalid="Boolean(errors.name)" required /><small v-if="errors.name" class="field-error">{{ errors.name }}</small></label>
        <label for="newMemberPhone"><span>手机号</span><input id="newMemberPhone" v-model.trim="newMember.phone" name="phone" inputmode="tel" autocomplete="tel" /></label>
        <label for="newMemberMajor"><span>学院</span><input id="newMemberMajor" v-model.trim="newMember.major" name="major" autocomplete="organization" /></label>
        <label for="newMemberGrade"><span>年级</span><select id="newMemberGrade" v-model="newMember.grade" name="grade">
          <option value="">年级</option>
          <option v-for="grade in profileGradeOptions" :key="grade" :value="grade">{{ grade }}</option>
        </select></label>
        <label for="newMemberQq"><span>QQ</span><input id="newMemberQq" v-model.trim="newMember.qq" name="qq" inputmode="numeric" autocomplete="off" /></label>
        <button class="primary-action" type="submit" :disabled="busy"><UserPlus :size="18" /><span>新增成员</span></button>
        <button class="ghost-button" type="button" @click="cancelCreateMember">取消</button>
      </form>
    </div>

    <div v-if="showImportMembers" class="inline-form-block import-members-block member-action-panel">
      <div class="subsection-head"><h4>批量导入成员</h4></div>
      <form class="import-member-form" @submit.prevent="importMembers">
        <label class="file-field" for="memberImportFile"><span>Excel 文件</span><input id="memberImportFile" ref="importInput" class="file-input" name="memberImportFile" type="file" accept=".xlsx,.xls" @change="pickImportFile" /></label>
        <button class="primary-action" type="submit" :disabled="busy || !importFile"><Upload :size="18" /><span>导入成员</span></button>
        <a class="ghost-button template-download" href="/templates/member-import-template.xlsx" download="成员批量导入模板.xlsx"><Download :size="16" /><span>下载模板</span></a>
      </form>
      <p v-if="importResult" class="import-result">新增 {{ importResult.created }}，更新 {{ importResult.updated }}，跳过 {{ importResult.skipped }}</p>
      <ul v-if="importResult?.errors?.length" class="import-errors"><li v-for="item in importResult.errors" :key="item">{{ item }}</li></ul>
    </div>

    <div class="filters member-filters">
      <label class="filter-field member-search-field" for="memberKeyword"><span>关键词</span><input id="memberKeyword" class="member-search" v-model.trim="query.keyword" name="keyword" autocomplete="off" placeholder="姓名、学号、手机号或学院" @keyup.enter="loadUsers(1)" /></label>
      <label class="filter-field" for="memberRole"><span>职位</span><select id="memberRole" class="role-select" v-model="query.role" name="role" @change="loadUsers(1)">
        <option value="">全部职位</option><option value="MEMBER">成员</option><option value="MINISTER">部长</option><option value="PRESIDENT">会长</option><option value="ADMIN">管理员</option>
      </select></label>
      <label class="filter-field" for="memberGrade"><span>年级</span><select id="memberGrade" class="grade-select" v-model="query.grade" name="grade" @change="loadUsers(1)">
        <option value="">全部年级</option><option v-for="grade in gradeOptions" :key="grade" :value="grade">{{ grade }}</option>
      </select></label>
      <button class="ghost-button" @click="loadUsers(1)">搜索</button>
      <div class="bulk-actions">
        <button class="ghost-button bulk-enable-button" :disabled="busy || total === 0" @click="bulkUpdateStatus('ACTIVE')"><Power :size="16" /><span>全部启用</span></button>
        <button class="ghost-button bulk-disable-button" :disabled="busy || total === 0" @click="bulkUpdateStatus('DISABLED')"><PowerOff :size="16" /><span>全部停用</span></button>
      </div>
    </div>

    <div class="table-wrap member-table-wrap">
      <table class="member-table">
        <thead><tr><th>姓名</th><th>学号</th><th>状态</th><th>手机号</th><th>学院</th><th>年级</th><th>操作</th><th>角色</th></tr></thead>
        <tbody>
          <tr v-for="user in users" :key="user.id">
            <td>{{ user.name }}</td><td class="mono">{{ user.studentNo }}</td>
            <td><span class="user-status" :class="{ disabled: user.status !== 'ACTIVE' }">{{ user.status === 'ACTIVE' ? '启用' : '停用' }}</span></td>
            <td>{{ user.phone || '-' }}</td><td>{{ user.major || '-' }}</td><td>{{ user.grade || '-' }}</td>
            <td class="actions actions-cell">
              <button :disabled="!canEditUser(user)" @click="toggleUser(user)">{{ user.status === 'ACTIVE' ? '停用' : '启用' }}</button>
              <button :disabled="!canEditUser(user)" @click="resetPassword(user)">重置密码</button>
              <button v-if="currentUser.role === 'ADMIN'" class="danger" :disabled="!canDeleteUser(user)" @click="deleteUser(user)">删除</button>
            </td>
            <td class="role-cell"><select :value="user.role" :aria-label="`修改 ${user.name} 的角色`" :disabled="!canEditUser(user)" @change="updateUserRole(user, $event.target.value)">
              <option value="MEMBER">成员</option><option value="MINISTER">部长</option><option value="PRESIDENT">会长</option><option value="ADMIN" :disabled="currentUser.role !== 'ADMIN'">管理员</option>
            </select></td>
          </tr>
          <tr v-if="users.length === 0"><td colspan="8" class="empty">暂无符合条件的成员</td></tr>
        </tbody>
      </table>
    </div>

    <div class="pagination-bar">
      <button class="ghost-button" :disabled="page <= 1 || busy" @click="changePage(-1)">上一页</button>
      <span>第 {{ page }} / {{ totalPages }} 页，共 {{ total }} 人</span>
      <label class="page-size-field"><span>每页</span><select class="page-size-select" v-model.number="pageSize" aria-label="每页显示人数" @change="loadUsers(1)">
        <option :value="10">10 条/页</option><option :value="20">20 条/页</option><option :value="50">50 条/页</option><option :value="100">100 条/页</option>
      </select></label>
      <button class="ghost-button" :disabled="page >= totalPages || busy" @click="changePage(1)">下一页</button>
    </div>
  </section>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Download, Power, PowerOff, RefreshCw, Upload, UserPlus } from '@lucide/vue'
import { api, del, post, put } from '../api.js'
import { compactQuery, queryOneOf, queryPositiveInt, queryText } from '../features/navigation/queryState.js'
import { requestConfirmation } from '../shared/confirm.js'

const props = defineProps({ currentUser: { type: Object, required: true } })
const emit = defineEmits(['notify', 'dirty-change'])
const route = useRoute()
const router = useRouter()

const busy = ref(false)
const users = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const gradeOptions = ref([])
const showCreateMember = ref(false)
const showImportMembers = ref(false)
const importInput = ref(null)
const importFile = ref(null)
const importResult = ref(null)
const dirtyScopes = ref(new Set())
const lastAppliedRoute = ref('')
const query = reactive({ keyword: '', role: '', grade: '' })
const newMember = reactive(emptyMember())
const errors = reactive({ studentNo: '', name: '' })

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)))
const profileGradeOptions = Array.from({ length: 2057 - 2007 + 1 }, (_, index) => `${2007 + index}级`)

onMounted(async () => {
  hydrateRouteQuery()
  await Promise.all([loadGradeOptions(), loadUsers(page.value)])
})

watch(() => route.fullPath, async fullPath => {
  if (!route.path.endsWith('/members') || fullPath === lastAppliedRoute.value) return
  hydrateRouteQuery()
  await loadUsers(page.value, false)
})

onBeforeUnmount(() => emit('dirty-change', false))

function hydrateRouteQuery() {
  query.keyword = queryText(route.query, 'q', queryText(route.query, 'keyword'))
  query.role = queryOneOf(route.query, 'role', ['', 'MEMBER', 'MINISTER', 'PRESIDENT', 'ADMIN'])
  query.grade = queryText(route.query, 'grade')
  page.value = queryPositiveInt(route.query, 'page', 1)
  pageSize.value = queryPositiveInt(route.query, 'size', 20, [10, 20, 50, 100])
}

async function syncRouteQuery() {
  const location = { path: route.path, query: compactQuery({ q: query.keyword, role: query.role, grade: query.grade, page: page.value, size: pageSize.value }) }
  const resolved = router.resolve(location)
  if (resolved.fullPath === route.fullPath) return
  lastAppliedRoute.value = resolved.fullPath
  await router.replace(location)
}

async function loadUsers(targetPage = page.value, syncRoute = true) {
  await run(async () => {
    const params = new URLSearchParams({ page: String(targetPage), pageSize: String(pageSize.value) })
    if (query.keyword) params.set('keyword', query.keyword)
    if (query.role) params.set('role', query.role)
    if (query.grade) params.set('grade', query.grade)
    const result = await api(`/api/users/page?${params}`)
    users.value = result.items
    total.value = result.total
    page.value = result.page
    if (syncRoute) await syncRouteQuery()
  }, false)
}

async function loadGradeOptions() {
  await run(async () => { gradeOptions.value = await api('/api/users/grades') }, false)
}

async function changePage(delta) {
  const next = Math.min(Math.max(1, page.value + delta), totalPages.value)
  if (next !== page.value) await loadUsers(next)
}

async function createMember() {
  if (!await validateMember()) return
  await run(async () => {
    await post('/api/users', { ...newMember, role: 'MEMBER' })
    cancelCreateMember()
    notify('成员已新增，初始密码为学号后六位', 'success')
    await Promise.all([loadGradeOptions(), loadUsers(1)])
  })
}

function toggleCreateMemberForm() {
  if (showCreateMember.value) cancelCreateMember()
  else showCreateMember.value = true
}

function cancelCreateMember() {
  Object.assign(newMember, emptyMember())
  errors.studentNo = ''
  errors.name = ''
  showCreateMember.value = false
  setDirty('form', false)
}

async function validateMember() {
  errors.studentNo = newMember.studentNo ? '' : '请填写学号'
  errors.name = newMember.name ? '' : '请填写姓名'
  const id = errors.studentNo ? 'newMemberStudentNo' : errors.name ? 'newMemberName' : ''
  if (!id) return true
  await nextTick()
  document.getElementById(id)?.focus()
  notify(errors.studentNo || errors.name, 'warn')
  return false
}

function pickImportFile(event) {
  importFile.value = event.target.files?.[0] || null
  importResult.value = null
  setDirty('import', Boolean(importFile.value))
}

async function importMembers() {
  if (!importFile.value) return notify('请选择 Excel 文件', 'warn')
  const formData = new FormData()
  formData.append('file', importFile.value)
  await run(async () => {
    importResult.value = await api('/api/users/import', { method: 'POST', body: formData })
    importFile.value = null
    setDirty('import', false)
    if (importInput.value) importInput.value.value = ''
    notify(`导入完成：新增 ${importResult.value.created}，更新 ${importResult.value.updated}，跳过 ${importResult.value.skipped}`, importResult.value.skipped ? 'warn' : 'success')
    if (!importResult.value.errors?.length) showImportMembers.value = false
    await Promise.all([loadGradeOptions(), loadUsers(1)])
  })
}

async function updateUserRole(user, role) {
  if (!canEditUser(user)) return notify('只有管理员可以修改管理员账号', 'warn')
  await run(async () => { await put(`/api/users/${user.id}`, { ...user, role, reason: '前端调整角色' }); notify('角色已更新', 'success'); await loadUsers() })
}

async function toggleUser(user) {
  if (!canEditUser(user)) return notify('只有管理员可以修改管理员账号', 'warn')
  await run(async () => {
    await put(`/api/users/${user.id}`, { ...user, status: user.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE', reason: user.status === 'ACTIVE' ? '停用账号' : '启用账号' })
    notify('账号状态已更新', 'success')
    await loadUsers()
  })
}

async function bulkUpdateStatus(status) {
  if (total.value === 0) return notify('筛选后列表暂无成员', 'warn')
  const label = status === 'ACTIVE' ? '启用' : '停用'
  if (!await confirmAction(`确认将当前筛选结果中的 ${total.value} 个账号全部${label}？`, label)) return
  await run(async () => {
    const result = await put('/api/users/bulk-status', { keyword: query.keyword, role: query.role, grade: query.grade, status, reason: `批量${label}筛选后成员` })
    const backup = result.safetyBackup ? `，操作前备份：${result.safetyBackup.filename}` : ''
    notify(`批量${label}完成：更新 ${result.updated}，无变化 ${result.unchanged}，跳过 ${result.skipped}${backup}`, result.skipped ? 'warn' : 'success')
    await loadUsers()
  })
}

async function resetPassword(user) {
  if (!canEditUser(user)) return notify('只有管理员可以修改管理员账号', 'warn')
  if (!await confirmAction(`确认重置 ${user.name} 的密码为学号后六位？`, '重置')) return
  await run(async () => { await post(`/api/users/${user.id}/reset-password`, { reason: '前端重置密码' }); notify('密码已重置', 'success') })
}

async function deleteUser(user) {
  if (!canDeleteUser(user)) return notify('不能删除当前登录账号', 'warn')
  if (!await confirmAction(`确认删除 ${user.name}（${user.studentNo}）？删除后无法恢复。已有值班记录的成员请改为停用账号。`, '删除')) return
  await run(async () => { await del(`/api/users/${user.id}`); notify('成员已删除，删除前已自动备份', 'success'); await Promise.all([loadGradeOptions(), loadUsers()]) })
}

function canEditUser(user) { return props.currentUser.role === 'ADMIN' || user.role !== 'ADMIN' }
function canDeleteUser(user) { return props.currentUser.role === 'ADMIN' && user.id !== props.currentUser.id }
function confirmAction(message, confirmLabel) { return requestConfirmation({ title: '确认成员操作', message, confirmLabel }) }

function setDirty(scope, dirty) {
  const next = new Set(dirtyScopes.value)
  if (dirty) next.add(scope)
  else next.delete(scope)
  dirtyScopes.value = next
  emit('dirty-change', next.size > 0)
}

async function run(action, showError = true) {
  busy.value = true
  try { await action() } catch (error) { if (showError) notify(error.message, 'error') } finally { busy.value = false }
}

function notify(message, type = 'info') { emit('notify', { message, type }) }
function emptyMember() { return { studentNo: '', name: '', phone: '', major: '', grade: '', qq: '' } }
</script>
