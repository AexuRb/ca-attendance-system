<template>
  <div class="app-shell">
    <aside class="side-panel">
      <div class="brand-block">
        <div class="brand-mark">
          <img src="/brand/ca-logo-white.png" alt="计协会徽" />
        </div>
        <div>
          <p class="eyebrow">Computer Association</p>
          <h1>值班签到台</h1>
          <p class="brand-code">#include &lt;the.world&gt;</p>
        </div>
      </div>

      <nav class="rail-nav" aria-label="主导航">
        <button :class="{ active: view === 'kiosk' }" @click="view = 'kiosk'">
          <ScanLine :size="18" />
          <span>签到台</span>
        </button>
        <button :class="{ active: view === 'dashboard' }" @click="openDashboard">
          <LayoutDashboard :size="18" />
          <span>后台</span>
        </button>
      </nav>

      <div class="operator-strip">
        <div class="signal-dot" :class="{ online: healthOk }"></div>
        <span>{{ healthOk ? '服务在线' : '服务未连接' }}</span>
      </div>
    </aside>

    <main class="main-surface">
      <section v-if="view === 'kiosk'" class="kiosk-grid">
        <div class="terminal-panel branded-panel">
          <img class="panel-watermark" src="/brand/ca-logo-white.png" alt="" aria-hidden="true" />
          <div class="panel-title">
            <ClipboardCheck :size="20" />
            <span>公开签到/签退</span>
          </div>
          <form class="lookup-form" @submit.prevent="lookupMember">
            <label for="studentNo">学号或姓名</label>
            <div class="input-row">
              <input id="studentNo" v-model.trim="studentNo" placeholder="输入学号或姓名" />
              <button type="submit" class="icon-button" title="查询">
                <Search :size="20" />
              </button>
            </div>
          </form>

          <div v-if="!lookupResult" class="kiosk-idle">
            <div class="idle-mark">
              <ScanLine :size="24" />
            </div>
            <div>
              <p class="eyebrow">CA DUTY DESK</p>
              <strong>等待输入</strong>
            </div>
          </div>

          <div v-if="lookupCandidates.length" class="candidate-zone">
            <p class="eyebrow">同名成员</p>
            <h2>请选择自己的学号</h2>
            <p>{{ lookupResult.message }}</p>
            <div class="candidate-list">
              <button
                v-for="candidate in lookupCandidates"
                :key="candidate.studentNo"
                type="button"
                class="candidate-card"
                :disabled="busy"
                @click="selectLookupCandidate(candidate)"
              >
                <strong>{{ candidate.name }}</strong>
                <span class="mono">{{ candidate.studentNo }}</span>
                <small>{{ [candidate.grade, candidate.major].filter(Boolean).join(' · ') || '未填写年级/学院' }}</small>
              </button>
            </div>
          </div>

          <div v-if="lookupResult && !lookupCandidates.length" class="confirm-zone">
            <div class="member-confirm" :class="{ blocked: !lookupResult.dutyDay || !lookupResult.exists }">
              <p class="eyebrow">确认信息</p>
              <h2>{{ lookupResult.name || '未找到成员' }}</h2>
              <p>{{ lookupResult.message }}</p>
              <div class="status-pills">
                <span v-if="lookupResult.exists">{{ lookupResult.action === 'CHECK_OUT' ? '本次为签退' : '本次为签到' }}</span>
                <span>{{ lookupResult.dutyDay ? '今日可值班' : '今日非值班日' }}</span>
              </div>
            </div>
            <button class="primary-action" :disabled="!lookupResult.exists || !lookupResult.dutyDay || busy" @click="submitAttendance">
              <CheckCircle2 :size="19" />
              <span>确认{{ lookupResult.action === 'CHECK_OUT' ? '签退' : '签到' }}</span>
            </button>
          </div>
        </div>

        <div class="side-summary">
          <div class="today-card">
            <p class="eyebrow">Today</p>
            <strong>{{ todayText }}</strong>
            <span>{{ weekdayText }}</span>
          </div>
          <div class="rule-list">
            <div><BadgeCheck :size="18" /> 输入学号或姓名后显示确认</div>
            <div><Clock3 :size="18" /> 同一天允许多次值班</div>
            <div><ShieldCheck :size="18" /> 成员和部长记录需要审核</div>
          </div>
        </div>
      </section>

      <section v-else class="dashboard" :class="[{ 'login-dashboard': !currentUser }, dashboardRoleClass]">
        <header class="dashboard-header">
          <div class="dashboard-title">
            <p class="eyebrow">{{ currentUser ? roleDashboard.eyebrow : 'Management Console' }}</p>
            <h2>{{ currentUser ? roleDashboard.headerTitle : '协会值班后台' }}</h2>
            <span>{{ currentUser ? roleDashboard.headerMeta : 'CUGB CA · #include <the.world>' }}</span>
          </div>
          <div v-if="currentUser" class="user-chip">
            <UserRound :size="17" />
            <span>{{ currentUser.name }} · {{ roleLabel(currentUser.role) }}</span>
            <button class="ghost-button" @click="logout">退出</button>
          </div>
        </header>

        <div v-if="!currentUser" class="login-panel">
          <div class="login-brand">
            <img src="/brand/ca-logo-white.png" alt="计协会徽" />
            <div>
              <strong>CUGB CA</strong>
              <span>Computer Association</span>
            </div>
          </div>
          <form @submit.prevent="login">
            <label>账号/学号</label>
            <input v-model.trim="loginForm.studentNo" placeholder="管理员账号或成员学号" />
            <label>密码</label>
            <input v-model="loginForm.password" type="password" placeholder="密码" />
            <button class="primary-action" type="submit" :disabled="busy">
              <LogIn :size="18" />
              <span>登录后台</span>
            </button>
          </form>
        </div>

        <div v-else class="workspace">
          <div class="role-command">
            <div class="role-command-main">
              <div class="role-command-mark">
                <component :is="roleDashboard.icon" :size="28" />
              </div>
              <div class="role-command-copy">
                <p class="eyebrow">{{ roleDashboard.eyebrow }}</p>
                <h3>{{ roleDashboard.title }}</h3>
                <span>{{ roleDashboard.subtitle }}</span>
              </div>
            </div>
            <div class="role-command-metrics">
              <div v-for="metric in roleMetrics" :key="metric.label" class="role-metric">
                <span>{{ metric.label }}</span>
                <strong>{{ metric.value }}</strong>
              </div>
            </div>
            <div class="role-quick-actions">
              <button v-for="action in roleActions" :key="action.label" class="ghost-button" @click="runRoleAction(action)">
                <component :is="action.icon" :size="16" />
                <span>{{ action.label }}</span>
              </button>
            </div>
          </div>

          <div v-if="availableTabs.length > 1" class="tab-row">
            <button v-for="tab in availableTabs" :key="tab.id" :class="{ active: activeTab === tab.id }" @click="selectTab(tab.id)">
              <component :is="tab.icon" :size="17" />
              <span>{{ tab.label }}</span>
            </button>
          </div>

          <section v-if="activeTab === 'overview'" class="work-section tab-overview">
            <div class="section-head">
              <h3>今日概览</h3>
              <button class="ghost-button" @click="loadOverview"><RefreshCw :size="16" />刷新</button>
            </div>
            <div class="overview-command">
              <div class="overview-hero">
                <div class="overview-hero-mark">
                  <CalendarDays :size="28" />
                </div>
                <div class="overview-hero-copy">
                  <p class="eyebrow">CA DUTY DESK</p>
                  <h3>{{ todayText }} · {{ weekdayText }}</h3>
                  <span>当前值班日：{{ overviewDutyDaysText }}</span>
                </div>
                <div class="overview-hero-number" :class="{ alert: overview.dashboard.todayOpenCount > 0 }">
                  <span>今日未签退</span>
                  <strong>{{ overview.dashboard.todayOpenCount }}</strong>
                </div>
              </div>

              <div class="overview-signal-board">
                <div class="overview-signal urgent">
                  <ListChecks :size="18" />
                  <span>全部待审核</span>
                  <strong>{{ overview.pendingCount }}</strong>
                </div>
                <div class="overview-signal">
                  <ClipboardList :size="18" />
                  <span>今日签到记录</span>
                  <strong>{{ overview.dashboard.todayRecordCount }}</strong>
                </div>
                <div class="overview-signal">
                  <Clock3 :size="18" />
                  <span>今日有效时长</span>
                  <strong>{{ overview.dashboard.todayValidHours }} h</strong>
                </div>
                <div class="overview-signal">
                  <Gauge :size="18" />
                  <span>本周有效时长</span>
                  <strong>{{ overview.dashboard.weekValidHours }} h</strong>
                </div>
              </div>
            </div>

            <div class="overview-grid">
              <div class="overview-metric">
                <span>本年有效时长</span>
                <strong>{{ overview.dashboard.yearValidHours }} h</strong>
              </div>
              <div class="overview-metric">
                <span>本年有效次数</span>
                <strong>{{ overview.dashboard.yearValidCount }}</strong>
              </div>
              <div class="overview-metric">
                <span>今日待审核</span>
                <strong>{{ overview.dashboard.todayPendingCount }}</strong>
              </div>
              <div class="overview-metric wide">
                <span>年度排行样本</span>
                <strong>{{ overview.topRows.length }} 人</strong>
              </div>
            </div>

            <div class="overview-layout">
              <div class="overview-panel">
                <div class="subsection-head">
                  <h4>今日未签退</h4>
                  <span>{{ openRecords.length }} 条</span>
                </div>
                <div class="table-wrap compact-table overview-table-wrap">
                  <table>
                    <thead>
                      <tr><th>姓名</th><th>学号</th><th>签到时间</th></tr>
                    </thead>
                    <tbody>
                      <tr v-for="item in openRecords" :key="item.id">
                        <td>{{ item.name }}</td>
                        <td class="mono">{{ item.studentNo }}</td>
                        <td>{{ timeText(item.checkInTime) }}</td>
                      </tr>
                      <tr v-if="openRecords.length === 0"><td colspan="3" class="empty">今日暂无未签退记录</td></tr>
                    </tbody>
                  </table>
                </div>
              </div>

              <div class="overview-panel">
                <div class="subsection-head">
                  <h4>本年排行</h4>
                </div>
                <div class="table-wrap compact-table overview-table-wrap">
                  <table>
                    <thead>
                      <tr><th>姓名</th><th>学号</th><th>次数</th><th>时长</th></tr>
                    </thead>
                    <tbody>
                      <tr v-for="row in overview.topRows" :key="row.userId">
                        <td>{{ row.name }}</td>
                        <td class="mono">{{ row.studentNo }}</td>
                        <td>{{ row.dutyCount }}</td>
                        <td>{{ row.totalHours }} h</td>
                      </tr>
                      <tr v-if="overview.topRows.length === 0"><td colspan="4" class="empty">暂无有效统计</td></tr>
                    </tbody>
                  </table>
                </div>
              </div>
            </div>
          </section>

          <section v-if="activeTab === 'reviews'" class="work-section tab-reviews">
            <div class="section-head">
              <h3>待审核记录</h3>
              <button class="ghost-button" @click="loadPending"><RefreshCw :size="16" />刷新</button>
            </div>
            <div v-if="pendingRecords.length" class="review-bulk-actions">
              <button class="ghost-button" @click="bulkReview('CHECK_IN')">全部通过签到</button>
              <button class="ghost-button" @click="bulkReview('CHECK_OUT')">全部通过签退</button>
              <button class="ghost-button" @click="bulkReview('ALL')">全部通过</button>
            </div>
            <div v-if="pendingRecords.length === 0" class="empty-state">
              <ListChecks :size="34" />
              <strong>当前没有待审核记录</strong>
              <span>成员或部长提交后，需要处理的签到/签退会出现在这里。</span>
              <button class="ghost-button" @click="loadPending"><RefreshCw :size="16" />再检查一次</button>
            </div>
            <div v-else class="table-wrap">
              <table>
                <thead>
                  <tr><th>姓名</th><th>学号</th><th>日期</th><th>签到</th><th>签退</th><th>操作</th></tr>
                </thead>
                <tbody>
                  <tr v-for="item in pendingRecords" :key="item.id">
                    <td>{{ item.name }}</td>
                    <td class="mono">{{ item.studentNo }}</td>
                    <td>{{ item.dutyDate }}</td>
                    <td>{{ statusText(item.checkInStatus) }}</td>
                    <td>{{ statusText(item.checkOutStatus) }}</td>
                    <td class="actions">
                      <button v-if="item.checkInStatus === 'PENDING'" @click="review(item.id, 'CHECK_IN', 'APPROVE')">通过签到</button>
                      <button v-if="item.checkInStatus === 'PENDING'" class="danger" @click="review(item.id, 'CHECK_IN', 'REJECT')">驳回签到</button>
                      <button v-if="item.checkOutStatus === 'PENDING'" @click="review(item.id, 'CHECK_OUT', 'APPROVE')">通过签退</button>
                      <button v-if="item.checkOutStatus === 'PENDING'" class="danger" @click="review(item.id, 'CHECK_OUT', 'REJECT')">驳回签退</button>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </section>

          <section v-if="activeTab === 'records'" class="work-section tab-records">
            <div class="section-head">
              <h3>签到记录</h3>
              <div class="section-actions">
                <button v-if="canAddAttendanceRecords" class="ghost-button" :class="{ active: showCreateAttendanceRecord }" @click="showCreateAttendanceRecord = !showCreateAttendanceRecord">
                  <ClipboardCheck :size="16" />新增记录
                </button>
                <button class="ghost-button" @click="loadAttendanceRecords"><RefreshCw :size="16" />刷新</button>
              </div>
            </div>
            <div v-if="showCreateAttendanceRecord" class="inline-form-block record-action-panel">
              <div class="subsection-head">
                <h4>新增签到记录</h4>
                <span>会长和管理员可补录，新增后自动计算有效时长</span>
              </div>
              <form class="record-create-form" @submit.prevent="createAttendanceRecord">
                <input v-model.trim="manualRecord.studentNo" inputmode="numeric" placeholder="成员学号" />
                <input v-model="manualRecord.checkInTime" type="datetime-local" />
                <input v-model="manualRecord.checkOutTime" type="datetime-local" />
                <input v-model.trim="manualRecord.reason" placeholder="补录原因" />
                <button class="primary-action" type="submit" :disabled="busy">
                  <ClipboardCheck :size="18" />
                  <span>添加记录</span>
                </button>
              </form>
            </div>
            <div class="filters record-filters">
              <input v-model.trim="recordFilters.keyword" placeholder="学号或姓名" @keyup.enter="loadAttendanceRecords" />
              <select v-model="recordFilters.status">
                <option value="">全部状态</option>
                <option v-for="item in effectiveStatusOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
              </select>
              <input v-model="recordFilters.from" type="date" />
              <input v-model="recordFilters.to" type="date" />
              <button class="ghost-button" @click="loadAttendanceRecords">查询</button>
            </div>
            <div class="record-summary-strip">
              <div><span>记录数</span><strong>{{ attendanceRecords.length }}</strong></div>
              <div><span>有效时长</span><strong>{{ attendanceRecordHours }} h</strong></div>
              <div><span>待审核项</span><strong>{{ attendanceRecordPendingCount }}</strong></div>
            </div>
            <div class="table-wrap records-table-wrap">
              <table class="records-table">
                <thead>
                  <tr>
                    <th class="record-action-column">操作</th>
                    <th>日期</th>
                    <th>姓名</th>
                    <th>学号</th>
                    <th>签到</th>
                    <th>签退</th>
                    <th>签到审核</th>
                    <th>签退审核</th>
                    <th>状态</th>
                    <th>有效时长</th>
                    <th>来源</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="item in attendanceRecords" :key="item.id">
                    <td class="actions record-action-column">
                      <button v-if="canDeleteAttendanceRecords" class="danger" @click="deleteAttendanceRecord(item)"><Trash2 :size="14" />删除</button>
                      <span v-else class="muted-cell">-</span>
                    </td>
                    <td>{{ item.dutyDate }}</td>
                    <td>{{ item.name }}</td>
                    <td class="mono">{{ item.studentNo }}</td>
                    <td>{{ timeText(item.checkInTime) }}</td>
                    <td>{{ timeText(item.checkOutTime) }}</td>
                    <td>{{ statusText(item.checkInStatus) }}</td>
                    <td>{{ statusText(item.checkOutStatus) }}</td>
                    <td><span class="status-badge" :class="item.effectiveStatus?.toLowerCase()">{{ effectiveStatusText(item.effectiveStatus) }}</span></td>
                    <td>{{ item.validHours }} h</td>
                    <td>{{ sourceText(item.source) }}</td>
                  </tr>
                  <tr v-if="attendanceRecords.length === 0"><td colspan="11" class="empty">暂无签到记录</td></tr>
                </tbody>
              </table>
            </div>
          </section>

          <section v-if="activeTab === 'members'" class="work-section tab-members">
            <div class="section-head">
              <h3>成员管理</h3>
              <button v-if="canManageUsers" class="ghost-button" @click="loadUsers"><RefreshCw :size="16" />刷新</button>
            </div>
            <div class="member-action-strip">
              <button class="ghost-button" :class="{ active: showCreateMember }" @click="showCreateMember = !showCreateMember">
                <UserPlus :size="17" />
                <span>新增成员</span>
              </button>
              <button v-if="canImportMembers" class="ghost-button" :class="{ active: showImportMembers }" @click="showImportMembers = !showImportMembers">
                <Upload :size="17" />
                <span>批量导入</span>
              </button>
            </div>
            <div v-if="showCreateMember" class="inline-form-block member-action-panel">
              <div class="subsection-head">
                <h4>新增成员</h4>
              </div>
              <form class="create-member-form" @submit.prevent="createMember">
                <input v-model.trim="newMember.studentNo" inputmode="numeric" placeholder="学号" />
                <input v-model.trim="newMember.name" placeholder="姓名" />
                <input v-model.trim="newMember.phone" inputmode="tel" placeholder="手机号" />
                <input v-model.trim="newMember.major" placeholder="学院" />
                <select v-model="newMember.grade">
                  <option value="">年级</option>
                  <option v-for="grade in profileGradeOptions" :key="grade" :value="grade">{{ grade }}</option>
                </select>
                <input v-model.trim="newMember.qq" placeholder="QQ" />
                <button class="primary-action" type="submit" :disabled="busy">
                  <UserPlus :size="18" />
                  <span>新增成员</span>
                </button>
              </form>
            </div>
            <div v-if="canImportMembers && showImportMembers" class="inline-form-block import-members-block member-action-panel">
              <div class="subsection-head">
                <h4>批量导入成员</h4>
              </div>
              <form class="import-member-form" @submit.prevent="importMembers">
                <input ref="importInput" class="file-input" type="file" accept=".xlsx,.xls" @change="pickImportFile" />
                <button class="primary-action" type="submit" :disabled="busy || !importFile">
                  <Upload :size="18" />
                  <span>导入成员</span>
                </button>
                <a class="ghost-button template-download" href="/templates/member-import-template.xlsx" download="成员批量导入模板.xlsx">
                  <Download :size="16" />
                  <span>下载模板</span>
                </a>
              </form>
              <p v-if="importResult" class="import-result">
                新增 {{ importResult.created }}，更新 {{ importResult.updated }}，跳过 {{ importResult.skipped }}
              </p>
              <ul v-if="importResult?.errors?.length" class="import-errors">
                <li v-for="item in importResult.errors" :key="item">{{ item }}</li>
              </ul>
            </div>
            <div v-if="canManageUsers" class="filters member-filters">
              <input class="member-search" v-model.trim="userQuery" placeholder="按姓名/学号/手机号/学院搜索" @keyup.enter="loadUsers(1)" />
              <select class="role-select" v-model="roleFilter" @change="loadUsers(1)">
                <option value="">全部职位</option>
                <option value="MEMBER">成员</option>
                <option value="MINISTER">部长</option>
                <option value="PRESIDENT">会长</option>
                <option value="ADMIN">管理员</option>
              </select>
              <select class="grade-select" v-model="gradeFilter" @change="loadUsers(1)">
                <option value="">全部年级</option>
                <option v-for="grade in gradeOptions" :key="grade" :value="grade">{{ grade }}</option>
              </select>
              <button class="ghost-button" @click="loadUsers(1)">搜索</button>
              <div class="bulk-actions">
                <button class="ghost-button bulk-enable-button" :disabled="busy || userTotal === 0" @click="bulkUpdateUserStatus('ACTIVE')">
                  <Power :size="16" />
                  <span>全部启用</span>
                </button>
                <button class="ghost-button bulk-disable-button" :disabled="busy || userTotal === 0" @click="bulkUpdateUserStatus('DISABLED')">
                  <PowerOff :size="16" />
                  <span>全部停用</span>
                </button>
              </div>
            </div>
            <div v-if="canManageUsers" class="table-wrap member-table-wrap">
              <table class="member-table">
                <thead>
                  <tr><th>姓名</th><th>学号</th><th>状态</th><th>手机号</th><th>学院</th><th>年级</th><th>操作</th><th>角色</th></tr>
                </thead>
                <tbody>
                  <tr v-for="user in users" :key="user.id">
                    <td>{{ user.name }}</td>
                    <td class="mono">{{ user.studentNo }}</td>
                    <td>
                      <span class="user-status" :class="{ disabled: user.status !== 'ACTIVE' }">
                        {{ user.status === 'ACTIVE' ? '启用' : '停用' }}
                      </span>
                    </td>
                    <td>{{ user.phone || '-' }}</td>
                    <td>{{ user.major || '-' }}</td>
                    <td>{{ user.grade || '-' }}</td>
                    <td class="actions actions-cell">
                      <button :disabled="!canEditUser(user)" @click="toggleUser(user)">{{ user.status === 'ACTIVE' ? '停用' : '启用' }}</button>
                      <button :disabled="!canEditUser(user)" @click="resetPassword(user)">重置密码</button>
                      <button v-if="currentUser.role === 'ADMIN'" class="danger" :disabled="!canDeleteUser(user)" @click="deleteUser(user)">删除</button>
                    </td>
                    <td class="role-cell">
                      <select :value="user.role" :disabled="!canEditUser(user)" @change="updateUserRole(user, $event.target.value)">
                        <option value="MEMBER">成员</option>
                        <option value="MINISTER">部长</option>
                        <option value="PRESIDENT">会长</option>
                        <option value="ADMIN" :disabled="currentUser.role !== 'ADMIN'">管理员</option>
                      </select>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
            <div v-if="canManageUsers" class="pagination-bar">
              <button class="ghost-button" :disabled="userPage <= 1 || busy" @click="changeUserPage(-1)">上一页</button>
              <span>第 {{ userPage }} / {{ userTotalPages }} 页，共 {{ userTotal }} 人</span>
              <select class="page-size-select" v-model.number="userPageSize" @change="loadUsers(1)">
                <option :value="10">10 条/页</option>
                <option :value="20">20 条/页</option>
                <option :value="50">50 条/页</option>
                <option :value="100">100 条/页</option>
              </select>
              <button class="ghost-button" :disabled="userPage >= userTotalPages || busy" @click="changeUserPage(1)">下一页</button>
            </div>
          </section>

          <section v-if="activeTab === 'stats'" class="work-section tab-stats">
            <div class="section-head">
              <h3>统计与导出</h3>
              <button v-if="canExport" class="ghost-button" @click="exportExcel"><Download :size="16" />导出</button>
            </div>
            <div class="range-presets" aria-label="快捷时间范围">
              <button
                v-for="preset in statsPresets"
                :key="preset.id"
                class="ghost-button"
                :class="{ active: statsPreset === preset.id }"
                @click="applyStatsPreset(preset.id)"
              >
                {{ preset.label }}
              </button>
            </div>
            <div class="filters stats-filters">
              <input v-model="range.from" type="date" @change="statsPreset = 'custom'" />
              <input v-model="range.to" type="date" @change="statsPreset = 'custom'" />
              <button class="ghost-button" @click="loadStats">查询</button>
            </div>
            <div class="stat-grid">
              <div><span>总人数</span><strong>{{ stats.length }}</strong></div>
              <div><span>总时长</span><strong>{{ totalHours }}</strong></div>
              <div><span>总次数</span><strong>{{ totalCount }}</strong></div>
            </div>
            <div v-if="statsPreset === 'week'" class="weekly-detail-panel">
              <div class="subsection-head">
                <h4><CalendarDays :size="17" />本周值班日明细</h4>
                <span>{{ range.from }} 至 {{ range.to }}</span>
              </div>
              <div class="table-wrap weekly-matrix-wrap">
                <table class="weekly-matrix-table">
                  <thead>
                    <tr>
                      <th>值班人员</th>
                      <th v-for="day in weeklyDetail.days" :key="day.dutyDate">
                        <span class="matrix-day-title">{{ day.weekdayName }}</span>
                        <small>{{ day.dutyDate }}</small>
                      </th>
                      <th v-if="weeklyDetail.days.length === 0">暂无值班日</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="user in weeklyDetail.users" :key="user.userId">
                      <td class="matrix-person-cell">
                        <strong>{{ user.name }}</strong>
                        <span>{{ user.studentNo }}</span>
                      </td>
                      <td
                        v-for="day in weeklyDetail.days"
                        :key="`${day.dutyDate}-${user.userId}`"
                        class="matrix-hour"
                        :class="{ filled: weeklyCell(day.dutyDate, user.userId) > 0 }"
                      >
                        {{ weeklyCell(day.dutyDate, user.userId) }} h
                      </td>
                      <td v-if="weeklyDetail.days.length === 0" class="matrix-hour">0 h</td>
                    </tr>
                    <tr v-if="weeklyDetail.users.length === 0">
                      <td :colspan="Math.max(2, weeklyDetail.days.length + 1)" class="empty">本周暂无值班人员</td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>
            <div class="table-wrap">
              <table>
                <thead><tr><th>排行</th><th>姓名</th><th>学号</th><th>年级</th><th>次数</th><th>总时长</th></tr></thead>
                <tbody>
                  <tr v-for="(row, index) in stats" :key="row.userId">
                    <td class="mono">{{ index + 1 }}</td>
                    <td>{{ row.name }}</td>
                    <td class="mono">{{ row.studentNo }}</td>
                    <td>{{ row.grade || '-' }}</td>
                    <td>{{ row.dutyCount }}</td>
                    <td>{{ row.totalHours }} h</td>
                  </tr>
                  <tr v-if="stats.length === 0"><td colspan="6" class="empty">暂无有效统计</td></tr>
                </tbody>
              </table>
            </div>
          </section>

          <section v-if="activeTab === 'maintenance'" class="work-section tab-maintenance">
            <div class="section-head">
              <h3>系统维护</h3>
              <div class="section-actions">
                <button class="ghost-button" @click="loadBackups"><RefreshCw :size="16" />刷新</button>
                <button class="primary-action" @click="createBackup" :disabled="busy">
                  <Save :size="17" />
                  <span>一键备份</span>
                </button>
              </div>
            </div>
            <div class="maintenance-grid">
              <div class="maintenance-panel">
                <div class="subsection-head">
                  <h4>数据备份</h4>
                  <span>成员、签到记录、日志和值班星期</span>
                </div>
                <div class="table-wrap">
                  <table class="backup-table">
                    <thead>
                      <tr><th>文件名</th><th>生成时间</th><th>大小</th><th>操作</th></tr>
                    </thead>
                    <tbody>
                      <tr v-for="item in backups" :key="item.filename">
                        <td class="mono">{{ item.filename }}</td>
                        <td>{{ timeText(item.createdAt) }}</td>
                        <td>{{ bytesText(item.size) }}</td>
                        <td class="actions">
                          <button @click="downloadBackup(item)"><Download :size="14" />下载</button>
                          <button v-if="canDeleteBackups" class="danger" @click="deleteBackup(item)"><Trash2 :size="14" />删除</button>
                        </td>
                      </tr>
                      <tr v-if="backups.length === 0"><td colspan="4" class="empty">暂无备份文件</td></tr>
                    </tbody>
                  </table>
                </div>
              </div>
            </div>
          </section>

          <section v-if="activeTab === 'settings'" class="work-section tab-settings">
            <div class="section-head">
              <h3>值班星期</h3>
              <button class="ghost-button" @click="saveWeekdays"><Save :size="16" />保存</button>
            </div>
            <div class="weekday-grid">
              <label v-for="day in weekdays" :key="day.weekday" :class="{ selected: day.enabled }">
                <input v-model="day.enabled" type="checkbox" />
                <CalendarDays :size="18" />
                <span>{{ day.weekday_name }}</span>
              </label>
            </div>
          </section>

          <section v-if="activeTab === 'logs'" class="work-section tab-logs">
            <div class="section-head">
              <h3>操作日志</h3>
              <div class="section-actions">
                <button class="ghost-button" @click="exportOperationLogs"><Download :size="16" />导出日志</button>
                <button class="ghost-button danger-button" @click="clearOperationLogs"><Trash2 :size="16" />清空日志</button>
                <button class="ghost-button" @click="loadOperationLogs(logPage)"><RefreshCw :size="16" />刷新</button>
              </div>
            </div>
            <div class="filters log-filters">
              <input class="log-search" v-model.trim="logFilters.keyword" placeholder="按操作人/学号/原因搜索" @keyup.enter="loadOperationLogs(1)" />
              <select class="log-action-select" v-model="logFilters.actionType" @change="loadOperationLogs(1)">
                <option value="">全部操作</option>
                <option v-for="item in logActionOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
              </select>
              <input v-model="logFilters.from" type="date" />
              <input v-model="logFilters.to" type="date" />
              <button class="ghost-button" @click="loadOperationLogs(1)">查询</button>
            </div>
            <div class="table-wrap">
              <table class="log-table">
                <thead>
                  <tr><th>时间</th><th>操作人</th><th>操作</th><th>对象</th><th>原因</th><th>详情</th></tr>
                </thead>
                <tbody>
                  <tr v-for="item in operationLogs" :key="item.id">
                    <td class="mono">{{ timeText(item.createdAt) }}</td>
                    <td>{{ operatorText(item) }}</td>
                    <td>{{ logActionLabel(item.actionType) }}</td>
                    <td class="mono">{{ targetText(item) }}</td>
                    <td class="log-reason">{{ item.reason || '-' }}</td>
                    <td class="actions">
                      <button @click="selectedLog = selectedLog?.id === item.id ? null : item">详情</button>
                    </td>
                  </tr>
                  <tr v-if="operationLogs.length === 0"><td colspan="6" class="empty">暂无操作日志</td></tr>
                </tbody>
              </table>
            </div>
            <div class="pagination-bar">
              <button class="ghost-button" :disabled="logPage <= 1 || busy" @click="changeOperationLogPage(-1)">上一页</button>
              <span>第 {{ logPage }} / {{ logTotalPages }} 页，共 {{ logTotal }} 条</span>
              <button class="ghost-button" :disabled="logPage >= logTotalPages || busy" @click="changeOperationLogPage(1)">下一页</button>
            </div>
            <div v-if="selectedLog" class="log-detail-panel">
              <div class="subsection-head">
                <h4>{{ logActionLabel(selectedLog.actionType) }} · {{ timeText(selectedLog.createdAt) }}</h4>
                <button class="ghost-button" @click="selectedLog = null">收起</button>
              </div>
              <div class="log-detail-grid">
                <div>
                  <h5>修改前</h5>
                  <pre>{{ prettyLogData(selectedLog.beforeData) }}</pre>
                </div>
                <div>
                  <h5>修改后</h5>
                  <pre>{{ prettyLogData(selectedLog.afterData) }}</pre>
                </div>
              </div>
            </div>
          </section>

          <section v-if="activeTab === 'profile'" class="work-section tab-profile">
            <div class="section-head"><h3>个人中心</h3></div>
            <div class="profile-grid">
              <div class="profile-card">
                <div class="subsection-head">
                  <h4>个人资料</h4>
                </div>
                <form class="profile-form" @submit.prevent="saveProfile">
                  <label>手机号</label>
                  <input v-model.trim="profile.phone" />
                  <label>学院</label>
                  <input v-model.trim="profile.major" />
                  <label>年级</label>
                  <select v-model="profile.grade">
                    <option value="">未填写</option>
                    <option v-for="grade in profileGradeOptions" :key="grade" :value="grade">{{ grade }}</option>
                  </select>
                  <label>QQ</label>
                  <input v-model.trim="profile.qq" />
                  <button class="primary-action" type="submit"><Save :size="18" /><span>保存资料</span></button>
                </form>

                <div class="profile-divider"></div>
                <div class="subsection-head">
                  <h4>修改密码</h4>
                </div>
                <form class="profile-form password-form" @submit.prevent="changePassword">
                  <label>原密码</label>
                  <input v-model="passwordForm.oldPassword" type="password" autocomplete="current-password" />
                  <label>新密码</label>
                  <input v-model="passwordForm.newPassword" type="password" autocomplete="new-password" />
                  <label>确认新密码</label>
                  <input v-model="passwordForm.confirmPassword" type="password" autocomplete="new-password" />
                  <button class="primary-action" type="submit"><Save :size="18" /><span>修改密码</span></button>
                </form>
              </div>

              <div class="records-card">
                <div class="subsection-head">
                  <h4>我的值班记录</h4>
                  <button class="ghost-button" @click="loadMyRecords"><RefreshCw :size="16" />刷新</button>
                </div>
                <div class="filters">
                  <input v-model="myRecordRange.from" type="date" />
                  <input v-model="myRecordRange.to" type="date" />
                  <button class="ghost-button" @click="loadMyRecords">查询</button>
                </div>
                <div class="mini-summary">
                  <div><span>记录数</span><strong>{{ myRecordCount }}</strong></div>
                  <div><span>有效时长</span><strong>{{ myRecordHours }} h</strong></div>
                </div>
                <div class="table-wrap compact-table">
                  <table>
                    <thead>
                      <tr><th>日期</th><th>签到</th><th>签退</th><th>审核</th><th>状态</th><th>有效时长</th></tr>
                    </thead>
                    <tbody>
                      <tr v-for="item in myRecords" :key="item.id">
                        <td>{{ item.dutyDate }}</td>
                        <td>{{ timeText(item.checkInTime) }}</td>
                        <td>{{ timeText(item.checkOutTime) }}</td>
                        <td>{{ statusText(item.checkInStatus) }} / {{ statusText(item.checkOutStatus) }}</td>
                        <td><span class="status-badge" :class="item.effectiveStatus?.toLowerCase()">{{ effectiveStatusText(item.effectiveStatus) }}</span></td>
                        <td>{{ item.validHours }} h</td>
                      </tr>
                      <tr v-if="myRecords.length === 0"><td colspan="6" class="empty">暂无值班记录</td></tr>
                    </tbody>
                  </table>
                </div>
              </div>
            </div>
          </section>
        </div>
      </section>

      <div v-if="toast.message" class="toast" :class="toast.type">{{ toast.message }}</div>
    </main>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import {
  BadgeCheck,
  CalendarDays,
  CheckCircle2,
  ClipboardCheck,
  ClipboardList,
  Clock3,
  Download,
  Gauge,
  History,
  LayoutDashboard,
  ListChecks,
  LogIn,
  Power,
  PowerOff,
  RefreshCw,
  Save,
  ScanLine,
  Search,
  ShieldCheck,
  SlidersHorizontal,
  Trash2,
  Upload,
  UserPlus,
  UserRound,
  UsersRound
} from '@lucide/vue'
import { api, del, post, put, setToken } from './api.js'

const view = ref('kiosk')
const activeTab = ref('overview')
const healthOk = ref(false)
const busy = ref(false)
const studentNo = ref('')
const lookupResult = ref(null)
const currentUser = ref(null)
const pendingRecords = ref([])
const attendanceRecords = ref([])
const openRecords = ref([])
const users = ref([])
const userTotal = ref(0)
const userPage = ref(1)
const userPageSize = ref(20)
const userQuery = ref('')
const roleFilter = ref('')
const gradeFilter = ref('')
const gradeOptions = ref([])
const showCreateMember = ref(false)
const showImportMembers = ref(false)
const showCreateAttendanceRecord = ref(false)
const stats = ref([])
const weeklyDetail = ref(emptyWeeklyDetail())
const statsPreset = ref('custom')
const myRecords = ref([])
const weekdays = ref([])
const today = new Date()
const todayValue = formatLocalDate(today)
const range = reactive({
  from: `${today.getFullYear()}-01-01`,
  to: todayValue
})
const recordFilters = reactive({
  keyword: '',
  status: '',
  from: `${today.getFullYear()}-01-01`,
  to: todayValue
})
const myRecordRange = reactive({
  from: `${today.getFullYear()}-01-01`,
  to: todayValue
})
const loginForm = reactive({ studentNo: '', password: '' })
const profile = reactive({ phone: '', major: '', grade: '', qq: '' })
const passwordForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })
const newMember = reactive({ studentNo: '', name: '', phone: '', major: '', grade: '', qq: '' })
const manualRecord = reactive({ studentNo: '', checkInTime: '', checkOutTime: '', reason: '' })
const importInput = ref(null)
const importFile = ref(null)
const importResult = ref(null)
const operationLogs = ref([])
const backups = ref([])
const logTotal = ref(0)
const logPage = ref(1)
const selectedLog = ref(null)
const overview = reactive({
  pendingCount: 0,
  totalHours: 0,
  totalCount: 0,
  dutyDays: [],
  topRows: [],
  dashboard: {
    todayRecordCount: 0,
    todayOpenCount: 0,
    todayPendingCount: 0,
    todayValidHours: 0,
    weekValidHours: 0,
    yearValidHours: 0,
    yearValidCount: 0
  }
})
const toast = reactive({ message: '', type: 'info' })
const logPageSize = 20

const tabs = [
  { id: 'overview', label: '概览', icon: Gauge, roles: ['MINISTER', 'PRESIDENT', 'ADMIN'] },
  { id: 'reviews', label: '审核', icon: ListChecks, roles: ['MINISTER', 'PRESIDENT', 'ADMIN'] },
  { id: 'records', label: '记录', icon: ClipboardList, roles: ['PRESIDENT', 'ADMIN'] },
  { id: 'members', label: '成员', icon: UsersRound, roles: ['PRESIDENT', 'ADMIN'] },
  { id: 'stats', label: '统计', icon: LayoutDashboard, roles: ['MINISTER', 'PRESIDENT', 'ADMIN'] },
  { id: 'maintenance', label: '维护', icon: Save, roles: ['PRESIDENT', 'ADMIN'] },
  { id: 'settings', label: '设置', icon: SlidersHorizontal, roles: ['PRESIDENT', 'ADMIN'] },
  { id: 'logs', label: '日志', icon: History, roles: ['ADMIN'] },
  { id: 'profile', label: '资料', icon: UserRound, roles: ['MEMBER', 'MINISTER', 'PRESIDENT', 'ADMIN'] }
]

const logActionOptions = [
  { value: 'CREATE_USER', label: '新增成员' },
  { value: 'IMPORT_USERS', label: '批量导入成员' },
  { value: 'UPDATE_USER', label: '修改成员信息' },
  { value: 'RESET_PASSWORD', label: '重置密码' },
  { value: 'DELETE_USER', label: '删除成员' },
  { value: 'BULK_UPDATE_USER_STATUS', label: '批量启停账号' },
  { value: 'REVIEW_ATTENDANCE', label: '审核签到记录' },
  { value: 'MANUAL_CREATE_ATTENDANCE', label: '新增签到记录' },
  { value: 'DELETE_ATTENDANCE_RECORD', label: '删除签到记录' },
  { value: 'UPDATE_DUTY_WEEKDAYS', label: '调整值班星期' },
  { value: 'MANUAL_UPDATE_ATTENDANCE', label: '手动修改记录' }
]

const effectiveStatusOptions = [
  { value: 'VALID', label: '有效' },
  { value: 'PENDING', label: '待审核' },
  { value: 'INCOMPLETE', label: '未签退' },
  { value: 'INVALID', label: '无效' }
]

const statsPresets = [
  { id: 'week', label: '本周' },
  { id: 'month', label: '本月' },
  { id: 'schoolYear', label: '本学年' }
]

const availableTabs = computed(() => currentUser.value ? tabs.filter(t => t.roles.includes(currentUser.value.role)) : [])
const canExport = computed(() => ['PRESIDENT', 'ADMIN'].includes(currentUser.value?.role))
const canManageUsers = computed(() => ['PRESIDENT', 'ADMIN'].includes(currentUser.value?.role))
const canImportMembers = computed(() => ['PRESIDENT', 'ADMIN'].includes(currentUser.value?.role))
const canAddAttendanceRecords = computed(() => ['PRESIDENT', 'ADMIN'].includes(currentUser.value?.role))
const canDeleteAttendanceRecords = computed(() => currentUser.value?.role === 'ADMIN')
const canViewLogs = computed(() => currentUser.value?.role === 'ADMIN')
const canBackupData = computed(() => ['PRESIDENT', 'ADMIN'].includes(currentUser.value?.role))
const canDeleteBackups = computed(() => currentUser.value?.role === 'ADMIN')
const lookupCandidates = computed(() => lookupResult.value?.matches || [])
const totalHours = computed(() => stats.value.reduce((sum, row) => sum + Number(row.totalHours || 0), 0))
const totalCount = computed(() => stats.value.reduce((sum, row) => sum + Number(row.dutyCount || 0), 0))
const attendanceRecordHours = computed(() => attendanceRecords.value.reduce((sum, row) => sum + Number(row.validHours || 0), 0))
const attendanceRecordPendingCount = computed(() => attendanceRecords.value.filter(row =>
  row.checkInStatus === 'PENDING' || row.checkOutStatus === 'PENDING'
).length)
const myRecordHours = computed(() => myRecords.value.reduce((sum, row) => sum + Number(row.validHours || 0), 0))
const myRecordCount = computed(() => myRecords.value.length)
const profileGradeOptions = Array.from({ length: 2057 - 2007 + 1 }, (_, index) => `${2007 + index}级`)
const userTotalPages = computed(() => Math.max(1, Math.ceil(userTotal.value / userPageSize.value)))
const logTotalPages = computed(() => Math.max(1, Math.ceil(logTotal.value / logPageSize)))
const overviewDutyDaysText = computed(() => overview.dutyDays.length ? overview.dutyDays.join('、') : '未设置')
const todayText = computed(() => today.toLocaleDateString('zh-CN', { month: 'long', day: 'numeric' }))
const weekdayText = computed(() => today.toLocaleDateString('zh-CN', { weekday: 'long' }))
const dashboardRoleClass = computed(() => currentUser.value ? `role-${String(currentUser.value.role).toLowerCase()}` : '')
const roleDashboard = computed(() => {
  const role = currentUser.value?.role
  return {
    MEMBER: {
      eyebrow: 'Member Desk',
      headerTitle: '个人值班中心',
      headerMeta: `${currentUser.value?.name || '成员'} · ${todayText.value} ${weekdayText.value}`,
      title: '我的值班记录',
      subtitle: '值班记录、个人资料和密码维护',
      icon: UserRound
    },
    MINISTER: {
      eyebrow: 'Review Desk',
      headerTitle: '部长审核台',
      headerMeta: `待审核 ${overview.pendingCount} 条 · 本周 ${overview.dashboard.weekValidHours} h`,
      title: '审核与统计',
      subtitle: '待审核记录、今日值班和阶段统计',
      icon: ListChecks
    },
    PRESIDENT: {
      eyebrow: 'President Desk',
      headerTitle: '会长工作台',
      headerMeta: `今日 ${overview.dashboard.todayRecordCount} 条 · 未签退 ${overview.dashboard.todayOpenCount} 条`,
      title: '成员和值班统筹',
      subtitle: '成员管理、签到记录、统计和值班星期',
      icon: ShieldCheck
    },
    ADMIN: {
      eyebrow: 'Admin Console',
      headerTitle: '管理员控制台',
      headerMeta: `维护 · 日志 · 数据记录`,
      title: '系统维护与数据控制',
      subtitle: '签到记录、成员数据、日志和备份维护',
      icon: SlidersHorizontal
    }
  }[role] || {
    eyebrow: 'Management Console',
    headerTitle: '协会值班后台',
    headerMeta: 'CUGB CA · #include <the.world>',
    title: '值班后台',
    subtitle: '协会值班管理',
    icon: Gauge
  }
})
const roleMetrics = computed(() => {
  const role = currentUser.value?.role
  if (role === 'MEMBER') {
    return [
      { label: '我的记录', value: myRecordCount.value },
      { label: '有效时长', value: `${myRecordHours.value} h` },
      { label: '年级', value: profile.grade || '-' }
    ]
  }
  if (role === 'MINISTER') {
    return [
      { label: '待审核', value: overview.pendingCount },
      { label: '今日记录', value: overview.dashboard.todayRecordCount },
      { label: '本周时长', value: `${overview.dashboard.weekValidHours} h` }
    ]
  }
  if (role === 'PRESIDENT') {
    return [
      { label: '今日未签退', value: overview.dashboard.todayOpenCount },
      { label: '今日待审核', value: overview.dashboard.todayPendingCount },
      { label: '本年时长', value: `${overview.dashboard.yearValidHours} h` }
    ]
  }
  if (role === 'ADMIN') {
    return [
      { label: '今日记录', value: overview.dashboard.todayRecordCount },
      { label: '待审核', value: overview.pendingCount },
      { label: '未签退', value: overview.dashboard.todayOpenCount }
    ]
  }
  return []
})
const roleActions = computed(() => {
  const role = currentUser.value?.role
  const actions = {
    MEMBER: [
      { label: '个人中心', tab: 'profile', icon: UserRound },
      { label: '签到台', view: 'kiosk', icon: ScanLine }
    ],
    MINISTER: [
      { label: '待审核', tab: 'reviews', icon: ListChecks },
      { label: '统计', tab: 'stats', icon: LayoutDashboard },
      { label: '个人中心', tab: 'profile', icon: UserRound }
    ],
    PRESIDENT: [
      { label: '成员', tab: 'members', icon: UsersRound },
      { label: '记录', tab: 'records', icon: ClipboardList },
      { label: '统计', tab: 'stats', icon: LayoutDashboard },
      { label: '值班星期', tab: 'settings', icon: SlidersHorizontal }
    ],
    ADMIN: [
      { label: '记录', tab: 'records', icon: ClipboardList },
      { label: '成员', tab: 'members', icon: UsersRound },
      { label: '日志', tab: 'logs', icon: History },
      { label: '维护', tab: 'maintenance', icon: Save }
    ]
  }[role] || []
  const allowed = new Set(availableTabs.value.map(tab => tab.id))
  return actions.filter(action => action.view || allowed.has(action.tab))
})
const logFilters = reactive({
  keyword: '',
  actionType: '',
  from: `${today.getFullYear()}-01-01`,
  to: todayValue
})

onMounted(async () => {
  await checkHealth()
})

async function checkHealth() {
  try {
    await api('/api/health')
    healthOk.value = true
  } catch {
    healthOk.value = false
  }
}

async function lookupMember() {
  if (!studentNo.value) return notify('请输入学号或姓名', 'warn')
  await run(async () => {
    lookupResult.value = await api(`/api/public/attendance/lookup?query=${encodeURIComponent(studentNo.value)}`)
  })
}

async function selectLookupCandidate(candidate) {
  studentNo.value = candidate.studentNo
  await lookupMember()
}

async function submitAttendance() {
  await run(async () => {
    const submitStudentNo = lookupResult.value?.studentNo || studentNo.value
    if (!submitStudentNo) {
      notify('请先查询并确认成员', 'warn')
      return
    }
    const res = await post('/api/public/attendance/submit', { studentNo: submitStudentNo })
    notify(res.message, 'success')
    lookupResult.value = null
    studentNo.value = ''
  })
}

function openDashboard() {
  view.value = 'dashboard'
}

async function login() {
  await run(async () => {
    const res = await post('/api/auth/login', loginForm)
    setToken(res.token)
    currentUser.value = res
    profile.phone = ''
    profile.major = ''
    profile.grade = ''
    profile.qq = ''
    clearPasswordForm()
    notify('已登录后台', 'success')
    selectTab(availableTabs.value[0]?.id || 'profile')
  })
}

function logout() {
  setToken('')
  currentUser.value = null
  clearPasswordForm()
  notify('已退出', 'info')
}

async function selectTab(tab) {
  activeTab.value = tab
  if (tab === 'overview') await loadOverview()
  if (tab === 'reviews') await loadPending()
  if (tab === 'records') await loadAttendanceRecords()
  if (tab === 'members') {
    if (canManageUsers.value) {
      await loadGradeOptions()
      await loadUsers(1)
    } else {
      users.value = []
    }
  }
  if (tab === 'stats') await loadStats()
  if (tab === 'maintenance') await loadBackups()
  if (tab === 'settings') await loadWeekdays()
  if (tab === 'logs') await loadOperationLogs(1)
  if (tab === 'profile') {
    await loadMe()
    await loadMyRecords()
  }
}

async function runRoleAction(action) {
  if (action.view) {
    view.value = action.view
    return
  }
  if (action.tab) await selectTab(action.tab)
}

async function loadOverview() {
  await run(async () => {
    const [pending, summary, dutyDays, dashboard, open] = await Promise.all([
      api('/api/attendance/reviews/pending'),
      api(`/api/stats/summary?from=${today.getFullYear()}-01-01&to=${todayValue}`),
      api('/api/settings/weekdays'),
      api(`/api/stats/dashboard?date=${todayValue}`),
      api(`/api/attendance/open?from=${todayValue}&to=${todayValue}`)
    ])
    overview.pendingCount = pending.length
    overview.totalHours = summary.reduce((sum, row) => sum + Number(row.totalHours || 0), 0)
    overview.totalCount = summary.reduce((sum, row) => sum + Number(row.dutyCount || 0), 0)
    overview.dutyDays = dutyDays.filter(day => day.enabled).map(day => day.weekday_name)
    overview.topRows = summary.slice(0, 5)
    Object.assign(overview.dashboard, dashboard)
    openRecords.value = open
  }, false)
}

async function loadMe() {
  await run(async () => {
    const me = await api('/api/auth/me')
    profile.phone = me.phone || ''
    profile.major = me.major || ''
    profile.grade = me.grade || ''
    profile.qq = me.qq || ''
  }, false)
}

async function saveProfile() {
  await run(async () => {
    await put('/api/me/profile', profile)
    notify('资料已保存', 'success')
  })
}

async function changePassword() {
  if (!passwordForm.oldPassword || !passwordForm.newPassword || !passwordForm.confirmPassword) {
    return notify('请填写完整密码信息', 'warn')
  }
  if (passwordForm.newPassword.length < 6) {
    return notify('新密码至少 6 位', 'warn')
  }
  if (passwordForm.newPassword !== passwordForm.confirmPassword) {
    return notify('两次新密码不一致', 'warn')
  }
  await run(async () => {
    await post('/api/auth/change-password', {
      oldPassword: passwordForm.oldPassword,
      newPassword: passwordForm.newPassword
    })
    clearPasswordForm()
    notify('密码已修改', 'success')
  })
}

function clearPasswordForm() {
  passwordForm.oldPassword = ''
  passwordForm.newPassword = ''
  passwordForm.confirmPassword = ''
}

async function loadMyRecords() {
  await run(async () => {
    myRecords.value = await api(`/api/attendance/me?from=${myRecordRange.from}&to=${myRecordRange.to}`)
  }, false)
}

async function loadPending() {
  await run(async () => {
    pendingRecords.value = await api('/api/attendance/reviews/pending')
  }, false)
}

async function review(id, part, action) {
  const reason = action === 'REJECT' ? window.prompt('请输入驳回原因') : '审核通过'
  if (action === 'REJECT' && !reason) return
  await run(async () => {
    await post(`/api/attendance/${id}/review`, { part, action, reason })
    notify('审核已处理', 'success')
    await loadPending()
  })
}

async function bulkReview(part) {
  if (!pendingRecords.value.length) return notify('当前没有待审核记录', 'warn')
  const label = part === 'CHECK_IN' ? '签到' : part === 'CHECK_OUT' ? '签退' : '签到和签退'
  if (!dangerConfirm(`确认将当前列表中可处理的${label}全部通过？`, '确认')) return
  await run(async () => {
    const result = await post('/api/attendance/reviews/bulk', {
      ids: pendingRecords.value.map(item => item.id),
      part
    })
    notify(`批量审核完成：通过 ${result.reviewed} 项，跳过 ${result.skipped} 条`, result.errors?.length ? 'warn' : 'success')
    await loadPending()
    await loadOverview()
  })
}

async function loadAttendanceRecords() {
  await run(async () => {
    const params = new URLSearchParams()
    params.set('from', recordFilters.from || `${today.getFullYear()}-01-01`)
    params.set('to', recordFilters.to || todayValue)
    if (recordFilters.keyword) params.set('studentNo', recordFilters.keyword)
    if (recordFilters.status) params.set('status', recordFilters.status)
    attendanceRecords.value = await api(`/api/attendance?${params.toString()}`)
  }, false)
}

async function createAttendanceRecord() {
  if (!canAddAttendanceRecords.value) return notify('只有会长或管理员可以添加签到记录', 'warn')
  if (!manualRecord.studentNo || !manualRecord.checkInTime || !manualRecord.reason) {
    return notify('请填写学号、签到时间和补录原因', 'warn')
  }
  if (manualRecord.checkOutTime && manualRecord.checkOutTime <= manualRecord.checkInTime) {
    return notify('签退时间必须晚于签到时间', 'warn')
  }
  await run(async () => {
    await post('/api/attendance/manual', {
      studentNo: manualRecord.studentNo,
      checkInTime: manualRecord.checkInTime,
      checkOutTime: manualRecord.checkOutTime || null,
      reason: manualRecord.reason
    })
    clearManualRecordForm()
    showCreateAttendanceRecord.value = false
    notify('签到记录已添加', 'success')
    await loadAttendanceRecords()
  })
}

function clearManualRecordForm() {
  manualRecord.studentNo = ''
  manualRecord.checkInTime = ''
  manualRecord.checkOutTime = ''
  manualRecord.reason = ''
}

async function deleteAttendanceRecord(item) {
  if (!canDeleteAttendanceRecords.value) return notify('只有管理员可以删除签到记录', 'warn')
  const timeLabel = item.checkInTime ? timeText(item.checkInTime) : item.dutyDate
  if (!dangerConfirm(`确认删除 ${item.name}（${item.studentNo}）在 ${timeLabel} 的签到记录？删除后无法恢复。`, '删除')) return
  await run(async () => {
    await del(`/api/attendance/${item.id}`)
    notify('签到记录已删除', 'success')
    await loadAttendanceRecords()
  })
}

async function loadUsers(page = userPage.value) {
  await run(async () => {
    const params = new URLSearchParams()
    params.set('page', String(page))
    params.set('pageSize', String(userPageSize.value))
    if (userQuery.value) params.set('keyword', userQuery.value)
    if (roleFilter.value) params.set('role', roleFilter.value)
    if (gradeFilter.value) params.set('grade', gradeFilter.value)
    const result = await api(`/api/users/page?${params.toString()}`)
    users.value = result.items
    userTotal.value = result.total
    userPage.value = result.page
  }, false)
}

async function changeUserPage(delta) {
  const next = Math.min(Math.max(1, userPage.value + delta), userTotalPages.value)
  if (next !== userPage.value) await loadUsers(next)
}

async function createMember() {
  if (!newMember.studentNo || !newMember.name) {
    return notify('请填写学号和姓名', 'warn')
  }
  await run(async () => {
    await post('/api/users', { ...newMember, role: 'MEMBER' })
    clearNewMemberForm()
    showCreateMember.value = false
    notify('成员已新增，初始密码为学号后六位', 'success')
    if (canManageUsers.value) {
      await loadGradeOptions()
      await loadUsers(1)
    }
  })
}

function clearNewMemberForm() {
  newMember.studentNo = ''
  newMember.name = ''
  newMember.phone = ''
  newMember.major = ''
  newMember.grade = ''
  newMember.qq = ''
}

function pickImportFile(event) {
  importFile.value = event.target.files?.[0] || null
  importResult.value = null
}

async function importMembers() {
  if (!canImportMembers.value) return notify('只有会长或管理员可以批量导入成员', 'warn')
  if (!importFile.value) return notify('请选择 Excel 文件', 'warn')
  const formData = new FormData()
  formData.append('file', importFile.value)
  await run(async () => {
    const result = await api('/api/users/import', { method: 'POST', body: formData })
    importResult.value = result
    importFile.value = null
    if (importInput.value) importInput.value.value = ''
    notify(`导入完成：新增 ${result.created}，更新 ${result.updated}，跳过 ${result.skipped}`, result.skipped ? 'warn' : 'success')
    if (!result.errors?.length) showImportMembers.value = false
    await loadGradeOptions()
    await loadUsers(1)
  })
}

async function loadGradeOptions() {
  await run(async () => {
    gradeOptions.value = await api('/api/users/grades')
  }, false)
}

async function updateUserRole(user, role) {
  if (!canEditUser(user)) return notify('只有管理员可以修改管理员账号', 'warn')
  await run(async () => {
    await put(`/api/users/${user.id}`, { ...user, role, reason: '前端调整角色' })
    notify('角色已更新', 'success')
    await loadUsers()
  })
}

async function toggleUser(user) {
  if (!canEditUser(user)) return notify('只有管理员可以修改管理员账号', 'warn')
  await run(async () => {
    await put(`/api/users/${user.id}`, {
      ...user,
      status: user.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE',
      reason: user.status === 'ACTIVE' ? '停用账号' : '启用账号'
    })
    notify('账号状态已更新', 'success')
    await loadUsers()
  })
}

async function bulkUpdateUserStatus(status) {
  if (!canManageUsers.value) return notify('无权管理成员', 'warn')
  if (userTotal.value === 0) return notify('筛选后列表暂无成员', 'warn')
  const label = status === 'ACTIVE' ? '启用' : '停用'
  if (!dangerConfirm(`确认将当前筛选结果中的 ${userTotal.value} 个账号全部${label}？`, label)) return
  await run(async () => {
    const result = await put('/api/users/bulk-status', {
      keyword: userQuery.value,
      role: roleFilter.value,
      grade: gradeFilter.value,
      status,
      reason: `批量${label}筛选后成员`
    })
    notify(`批量${label}完成：更新 ${result.updated}，无变化 ${result.unchanged}，跳过 ${result.skipped}`,
      result.skipped ? 'warn' : 'success')
    await loadUsers()
  })
}

async function resetPassword(user) {
  if (!canEditUser(user)) return notify('只有管理员可以修改管理员账号', 'warn')
  if (!dangerConfirm(`确认重置 ${user.name} 的密码为学号后六位？`, '重置')) return
  await run(async () => {
    await post(`/api/users/${user.id}/reset-password`, { reason: '前端重置密码' })
    notify('密码已重置', 'success')
  })
}

async function deleteUser(user) {
  if (!canDeleteUser(user)) return notify('不能删除当前登录账号', 'warn')
  if (!dangerConfirm(`确认删除 ${user.name}（${user.studentNo}）？删除后无法恢复。已有值班记录的成员请改为停用账号。`, '删除')) return
  await run(async () => {
    await del(`/api/users/${user.id}`)
    notify('成员已删除', 'success')
    await loadGradeOptions()
    await loadUsers()
  })
}

async function loadStats() {
  await run(async () => {
    const summaryUrl = `/api/stats/summary?from=${range.from}&to=${range.to}`
    const weeklyDetailUrl = `/api/stats/weekly-detail?from=${range.from}&to=${range.to}`
    const [summary, detail] = await Promise.all([
      api(summaryUrl),
      statsPreset.value === 'week' ? api(weeklyDetailUrl) : Promise.resolve(emptyWeeklyDetail())
    ])
    stats.value = summary
    weeklyDetail.value = detail
  }, false)
}

function emptyWeeklyDetail() {
  return { days: [], users: [], cells: {} }
}

function weeklyCell(dutyDate, userId) {
  return Number(weeklyDetail.value.cells?.[dutyDate]?.[String(userId)] || 0)
}

async function applyStatsPreset(preset) {
  const now = new Date()
  const from = new Date(now)
  if (preset === 'week') {
    const weekday = now.getDay() || 7
    from.setDate(now.getDate() - weekday + 1)
  } else if (preset === 'month') {
    from.setDate(1)
  } else if (preset === 'schoolYear') {
    const startYear = now.getMonth() >= 8 ? now.getFullYear() : now.getFullYear() - 1
    from.setFullYear(startYear, 8, 1)
  }
  statsPreset.value = preset
  range.from = formatLocalDate(from)
  range.to = formatLocalDate(now)
  await loadStats()
}

async function exportExcel() {
  await run(async () => {
    const blob = await api(`/api/stats/export?from=${range.from}&to=${range.to}`)
    downloadBlob(blob, `值班记录_${range.from}_${range.to}.xlsx`)
  })
}

async function loadBackups() {
  if (!canBackupData.value) return
  await run(async () => {
    backups.value = await api('/api/maintenance/backups')
  }, false)
}

async function createBackup() {
  if (!canBackupData.value) return notify('只有会长或管理员可以备份数据', 'warn')
  await run(async () => {
    await post('/api/maintenance/backups')
    notify('备份已生成', 'success')
    await loadBackups()
  })
}

async function downloadBackup(item) {
  await run(async () => {
    const blob = await api(`/api/maintenance/backups/${encodeURIComponent(item.filename)}`)
    downloadBlob(blob, item.filename)
  })
}

async function deleteBackup(item) {
  if (!canDeleteBackups.value) return notify('只有管理员可以删除备份', 'warn')
  if (!dangerConfirm(`确认删除备份 ${item.filename}？删除后无法恢复。`, '删除')) return
  await run(async () => {
    await del(`/api/maintenance/backups/${encodeURIComponent(item.filename)}`)
    notify('备份已删除', 'success')
    await loadBackups()
  })
}

async function loadWeekdays() {
  await run(async () => {
    weekdays.value = await api('/api/settings/weekdays')
  }, false)
}

async function saveWeekdays() {
  await run(async () => {
    const enabledWeekdays = weekdays.value.filter(d => d.enabled).map(d => d.weekday)
    await put('/api/settings/weekdays', { enabledWeekdays })
    notify('值班星期已保存', 'success')
  })
}

async function loadOperationLogs(page = 1) {
  await run(async () => {
    const params = new URLSearchParams()
    params.set('page', String(page))
    params.set('pageSize', String(logPageSize))
    if (logFilters.keyword) params.set('keyword', logFilters.keyword)
    if (logFilters.actionType) params.set('actionType', logFilters.actionType)
    if (logFilters.from) params.set('from', logFilters.from)
    if (logFilters.to) params.set('to', logFilters.to)
    const result = await api(`/api/logs?${params.toString()}`)
    operationLogs.value = result.items
    logTotal.value = result.total
    logPage.value = result.page
    selectedLog.value = null
  }, false)
}

async function exportOperationLogs() {
  await run(async () => {
    const params = new URLSearchParams()
    if (logFilters.keyword) params.set('keyword', logFilters.keyword)
    if (logFilters.actionType) params.set('actionType', logFilters.actionType)
    if (logFilters.from) params.set('from', logFilters.from)
    if (logFilters.to) params.set('to', logFilters.to)
    const blob = await api(`/api/logs/export?${params.toString()}`)
    downloadBlob(blob, `操作日志_${logFilters.from || '开始'}_${logFilters.to || '结束'}.xlsx`)
  })
}

async function clearOperationLogs() {
  if (!canViewLogs.value) return notify('只有管理员可以清空操作日志', 'warn')
  if (!dangerConfirm('确认清空全部操作日志？建议先导出日志留档。该操作不可恢复。', '清空日志')) return
  await run(async () => {
    const result = await del('/api/logs')
    operationLogs.value = []
    logTotal.value = 0
    logPage.value = 1
    selectedLog.value = null
    notify(`已清空 ${result?.deleted || 0} 条日志`, 'success')
  })
}

async function changeOperationLogPage(delta) {
  const next = Math.min(Math.max(1, logPage.value + delta), logTotalPages.value)
  if (next !== logPage.value) await loadOperationLogs(next)
}

async function run(fn, showError = true) {
  busy.value = true
  try {
    await fn()
  } catch (error) {
    if (showError) notify(error.message, 'error')
  } finally {
    busy.value = false
  }
}

function notify(message, type = 'info') {
  toast.message = message
  toast.type = type
  window.clearTimeout(notify.timer)
  notify.timer = window.setTimeout(() => {
    toast.message = ''
  }, 2800)
}

function roleLabel(role) {
  return { MEMBER: '成员', MINISTER: '部长', PRESIDENT: '会长', ADMIN: '管理员' }[role] || role
}

function logActionLabel(action) {
  return logActionOptions.find(item => item.value === action)?.label || action
}

function operatorText(item) {
  if (!item.operatorName && !item.operatorStudentNo) return '-'
  return item.operatorStudentNo ? `${item.operatorName || '-'}（${item.operatorStudentNo}）` : item.operatorName
}

function targetText(item) {
  return item.targetId ? `${item.targetType}#${item.targetId}` : (item.targetType || '-')
}

function prettyLogData(value) {
  if (!value) return '-'
  try {
    return JSON.stringify(JSON.parse(value), null, 2)
  } catch {
    return String(value)
  }
}

function downloadBlob(blob, filename) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.click()
  URL.revokeObjectURL(url)
}

function dangerConfirm(message, phrase = '确认') {
  const input = window.prompt(`${message}\n\n请输入“${phrase}”继续`)
  return input === phrase
}

function canEditUser(user) {
  return currentUser.value?.role === 'ADMIN' || user.role !== 'ADMIN'
}

function canDeleteUser(user) {
  return currentUser.value?.role === 'ADMIN' && user.id !== currentUser.value?.id
}

function statusText(status) {
  return {
    NOT_SUBMITTED: '未提交',
    PENDING: '待审核',
    APPROVED: '已通过',
    REJECTED: '已驳回',
    AUTO_APPROVED: '自动通过'
  }[status] || status
}

function effectiveStatusText(status) {
  return {
    VALID: '有效',
    PENDING: '待审核',
    INCOMPLETE: '未签退',
    INVALID: '无效'
  }[status] || status
}

function sourceText(source) {
  return {
    PUBLIC: '公开提交',
    ADMIN_MANUAL: '后台手动'
  }[source] || source || '-'
}

function bytesText(value) {
  const size = Number(value || 0)
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

function timeText(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 16)
}

function formatLocalDate(value) {
  const year = value.getFullYear()
  const month = String(value.getMonth() + 1).padStart(2, '0')
  const day = String(value.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}
</script>
