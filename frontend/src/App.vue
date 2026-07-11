<template>
  <div
    class="app-shell"
    :class="{
      'admin-fullscreen': view === 'dashboard' && currentUser,
      'login-fullscreen': view === 'dashboard' && !currentUser,
      'kiosk-fullscreen': view === 'kiosk'
    }"
  >
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
      <section v-if="view === 'kiosk'" class="kiosk-portal">
        <header class="kiosk-portal-header">
          <div class="kiosk-portal-brand">
            <span><img src="/brand/ca-logo-white.png" alt="计协会徽" /></span>
            <div>
              <strong>计算机协会</strong>
              <small>值班签到台</small>
            </div>
          </div>
          <div class="kiosk-live-clock" aria-label="当前时间">
            <span>{{ kioskDateText }}</span>
            <strong>{{ kioskTimeText }}</strong>
          </div>
          <div class="kiosk-portal-tools">
            <span class="kiosk-health" :class="{ online: healthOk }">
              <i aria-hidden="true"></i>{{ healthOk ? '服务正常' : '服务未连接' }}
            </span>
            <button class="kiosk-admin-button" type="button" title="进入后台" aria-label="进入后台" @click="openDashboard">
              <LayoutDashboard :size="19" />
            </button>
          </div>
        </header>

        <main class="kiosk-portal-main">
          <section class="kiosk-checkin-stage" :class="{ success: attendanceSuccess }">
            <div class="kiosk-stage-heading">
              <div>
                <p class="eyebrow">Duty Checkpoint</p>
                <h1>{{ attendanceSuccess ? `${attendanceSuccess.actionLabel}成功` : '签到 / 签退' }}</h1>
              </div>
              <span v-if="!attendanceSuccess">{{ kioskCurrentPeriodText }}</span>
            </div>

            <Transition name="kiosk-state" mode="out-in">
              <div v-if="attendanceSuccess" key="success" class="kiosk-success-state">
                <span class="kiosk-success-ring"><CheckCircle2 :size="48" /></span>
                <div>
                  <p>{{ attendanceSuccess.actionLabel }}完成</p>
                  <strong>{{ attendanceSuccess.name }}</strong>
                  <span>{{ attendanceSuccess.message }}</span>
                </div>
                <button class="kiosk-next-button" type="button" @click="resetKiosk">
                  <ScanLine :size="19" />下一位
                </button>
              </div>

              <div v-else key="lookup" class="kiosk-lookup-stage">
                <form class="lookup-form kiosk-lookup-form" @submit.prevent="lookupMember">
                  <label for="studentNo">学号或姓名</label>
                  <div class="input-row">
                    <ScanLine class="kiosk-input-icon" :size="22" aria-hidden="true" />
                    <input id="studentNo" v-model.trim="studentNo" autocomplete="off" placeholder="输入学号或姓名" />
                    <button type="submit" class="kiosk-search-button" :disabled="busy">
                      <Search :size="20" />
                      <span>查询</span>
                    </button>
                    <span v-if="busy" class="kiosk-search-scan" aria-hidden="true"></span>
                  </div>
                </form>

                <div v-if="lookupCandidates.length" class="candidate-zone kiosk-choice-box">
                  <div class="kiosk-result-heading">
                    <div>
                      <p class="eyebrow">同名成员</p>
                      <h2>选择自己的学号</h2>
                    </div>
                    <span>{{ lookupCandidates.length }} 人</span>
                  </div>
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

                <div v-if="lookupResult && !lookupCandidates.length" class="confirm-zone kiosk-confirm-box">
                  <div class="member-confirm" :class="{ blocked: !lookupResult.exists }">
                    <div>
                      <p class="eyebrow">查询结果</p>
                      <h2>{{ lookupResult.name || '未找到成员' }}</h2>
                      <p>{{ lookupResult.message }}</p>
                    </div>
                    <div class="status-pills">
                      <span v-if="lookupResult.exists">{{ lookupResult.action === 'CHECK_OUT' ? '本次签退' : '本次签到' }}</span>
                      <span>{{ lookupResult.dutyDay ? '今日值班日' : '非值班时段' }}</span>
                    </div>
                  </div>
                  <button class="kiosk-confirm-button" :disabled="!lookupResult.exists || busy" @click="submitAttendance">
                    <CheckCircle2 :size="20" />
                    <span>确认{{ lookupResult.action === 'CHECK_OUT' ? '签退' : '签到' }}</span>
                  </button>
                </div>
              </div>
            </Transition>
          </section>

          <section class="kiosk-duty-track">
            <header class="kiosk-track-head">
              <div>
                <p class="eyebrow">Today Schedule</p>
                <h2>今日部长排班</h2>
              </div>
              <button type="button" title="刷新排班" aria-label="刷新排班" @click="loadPublicSchedules">
                <RefreshCw :size="17" />
              </button>
            </header>

            <div v-if="todayPeriodSummary.length" class="kiosk-track-grid">
              <article
                v-for="period in todayPeriodSummary"
                :key="period.key"
                class="kiosk-track-period"
                :class="{ active: period.active, missing: period.missing }"
                :style="{ '--period-progress': `${dutyPeriodProgress(period)}%` }"
              >
                <div class="kiosk-track-time">
                  <strong>{{ period.timeText }}</strong>
                  <span>{{ period.count }} 人</span>
                </div>
                <div class="kiosk-track-members" :class="{ empty: !period.people.length }">
                  <span v-for="person in period.people" :key="person.key">{{ person.name }}</span>
                  <em v-if="!period.people.length">待安排部长</em>
                </div>
                <span v-if="period.active" class="kiosk-period-progress" aria-hidden="true"></span>
              </article>
            </div>
            <div v-else class="kiosk-track-empty">
              <CalendarDays :size="22" />
              <strong>{{ kioskEmptyBoardText }}</strong>
            </div>
          </section>

          <section class="kiosk-week-board" aria-label="本周值班概览">
            <div v-for="day in kioskWeekSummary" :key="day.weekday" :class="{ today: day.isToday }">
              <strong>{{ day.name }}</strong>
              <span>{{ day.count ? `${day.count} 个时段` : '暂无排班' }}</span>
              <i v-if="day.isToday">今日</i>
            </div>
          </section>
        </main>
      </section>

      <section
        v-else
        class="dashboard"
        :class="[
          { 'login-dashboard': !currentUser, 'logged-in-dashboard': currentUser },
          dashboardRoleClass,
          currentUser ? `active-module-${activeTab}` : ''
        ]"
      >
        <header class="dashboard-header" :class="{ 'admin-topbar': currentUser }">
          <template v-if="currentUser">
            <div class="admin-brand-lockup">
              <span class="admin-brand-symbol">
                <img src="/brand/ca-logo-white.png" alt="计协会徽" />
              </span>
              <span class="admin-brand-copy">
                <strong>计算机协会</strong>
                <small>本地离线管理后台</small>
              </span>
            </div>

            <nav class="admin-primary-nav" aria-label="后台一级导航">
              <button
                v-for="group in adminNavGroups"
                :key="group.id"
                type="button"
                :class="{ active: activeAdminGroup?.id === group.id }"
                :aria-current="activeAdminGroup?.id === group.id ? 'page' : undefined"
                @click="selectAdminGroup(group)"
              >
                <component :is="group.icon" :size="18" />
                <span>{{ group.label }}</span>
              </button>
            </nav>

            <div class="user-chip">
              <span class="admin-user-avatar"><UserRound :size="17" /></span>
              <span>{{ currentUser.name }} · {{ roleLabel(currentUser.role) }}</span>
              <button class="ghost-button" type="button" title="退出后台" @click="logout">
                <Power :size="15" />
                <span>退出</span>
              </button>
            </div>
          </template>
          <template v-else>
            <div class="login-topbar-brand">
              <span><img src="/brand/ca-logo-white.png" alt="计协会徽" /></span>
              <div>
                <strong>计算机协会</strong>
                <small>本地离线管理后台</small>
              </div>
            </div>
            <div class="login-topbar-clock">
              <span>{{ kioskDateText }}</span>
              <strong>{{ kioskTimeText }}</strong>
            </div>
            <div class="login-topbar-tools">
              <span class="login-health" :class="{ online: healthOk }">
                <i aria-hidden="true"></i>{{ healthOk ? '服务正常' : '服务未连接' }}
              </span>
              <button type="button" title="返回签到台" aria-label="返回签到台" @click="returnToKiosk">
                <ScanLine :size="19" />
              </button>
            </div>
          </template>
        </header>

        <div v-if="!currentUser" class="login-page">
          <section class="login-access-panel" :class="{ error: loginError, verified: loginVerified, 'setup-mode': setupRequired }">
            <aside class="login-identity-panel">
              <div class="login-identity-logo">
                <img src="/brand/ca-logo-white.png" alt="计协会徽" />
              </div>
              <div class="login-identity-copy">
                <p class="eyebrow">Computer Association</p>
                <h1>{{ setupRequired ? '系统首次初始化' : '本地离线后台' }}</h1>
                <span>{{ setupRequired ? '建立本机数据库与首位管理员' : '值班、成员与协会事务管理' }}</span>
              </div>
              <div class="login-identity-status" :class="{ online: healthOk }">
                <i aria-hidden="true"></i>
                <div>
                  <span>本机服务</span>
                  <strong>{{ healthOk ? '连接正常' : '暂未连接' }}</strong>
                </div>
              </div>
              <span class="login-identity-scan" aria-hidden="true"></span>
            </aside>

            <section class="login-form-panel">
              <Transition name="login-state" mode="out-in">
                <div v-if="loginVerified" key="verified" class="login-verified-state">
                  <span><CheckCircle2 :size="48" /></span>
                  <p>Access Granted</p>
                  <h2>身份验证通过</h2>
                  <small>正在进入后台</small>
                  <i aria-hidden="true"></i>
                </div>

                <form v-else-if="setupRequired" key="setup" class="setup-form" :aria-busy="busy" @submit.prevent="initializeSystem">
                  <div class="login-form-heading">
                    <p class="eyebrow">First Run Setup</p>
                    <h2>创建管理员</h2>
                    <span>数据将保存在本机应用根目录</span>
                  </div>

                  <label for="setupAccount">管理员账号</label>
                  <div class="login-field">
                    <UserRound :size="19" aria-hidden="true" />
                    <input
                      id="setupAccount"
                      v-model.trim="setupForm.account"
                      autocomplete="username"
                      maxlength="32"
                      placeholder="4-32 位字母、数字、下划线或短横线"
                    />
                    <span class="login-field-scan" aria-hidden="true"></span>
                  </div>

                  <label for="setupName">管理员姓名</label>
                  <div class="login-field">
                    <BadgeCheck :size="19" aria-hidden="true" />
                    <input
                      id="setupName"
                      v-model.trim="setupForm.name"
                      autocomplete="name"
                      maxlength="64"
                      placeholder="输入管理员姓名"
                    />
                    <span class="login-field-scan" aria-hidden="true"></span>
                  </div>

                  <label for="setupPassword">设置密码</label>
                  <div class="login-field login-password-field">
                    <KeyRound :size="19" aria-hidden="true" />
                    <input
                      id="setupPassword"
                      v-model="setupForm.password"
                      :type="showSetupPassword ? 'text' : 'password'"
                      autocomplete="new-password"
                      maxlength="64"
                      placeholder="至少 6 位"
                    />
                    <button
                      type="button"
                      :title="showSetupPassword ? '隐藏密码' : '显示密码'"
                      :aria-label="showSetupPassword ? '隐藏密码' : '显示密码'"
                      @click="showSetupPassword = !showSetupPassword"
                    >
                      <EyeOff v-if="showSetupPassword" :size="18" />
                      <Eye v-else :size="18" />
                    </button>
                    <span class="login-field-scan" aria-hidden="true"></span>
                  </div>

                  <label for="setupPasswordConfirm">确认密码</label>
                  <div class="login-field">
                    <ShieldCheck :size="19" aria-hidden="true" />
                    <input
                      id="setupPasswordConfirm"
                      v-model="setupForm.confirmPassword"
                      :type="showSetupPassword ? 'text' : 'password'"
                      autocomplete="new-password"
                      maxlength="64"
                      placeholder="再次输入密码"
                    />
                    <span class="login-field-scan" aria-hidden="true"></span>
                  </div>

                  <button class="login-submit-button" type="submit" :disabled="busy || !setupFormReady">
                    <ShieldCheck :size="19" />
                    <span>{{ busy ? '正在初始化' : '完成初始化' }}</span>
                    <i v-if="busy" aria-hidden="true"></i>
                  </button>
                </form>

                <form v-else key="form" :aria-busy="busy" @submit.prevent="login">
                  <div class="login-form-heading">
                    <p class="eyebrow">Identity Verification</p>
                    <h2>后台身份验证</h2>
                    <span>使用协会后台账号登录</span>
                  </div>

                  <label for="loginStudentNo">账号 / 学号</label>
                  <div class="login-field">
                    <UserRound :size="19" aria-hidden="true" />
                    <input
                      id="loginStudentNo"
                      v-model.trim="loginForm.studentNo"
                      autocomplete="username"
                      placeholder="输入后台账号或学号"
                    />
                    <span class="login-field-scan" aria-hidden="true"></span>
                  </div>

                  <label for="loginPassword">密码</label>
                  <div class="login-field login-password-field">
                    <KeyRound :size="19" aria-hidden="true" />
                    <input
                      id="loginPassword"
                      v-model="loginForm.password"
                      :type="showLoginPassword ? 'text' : 'password'"
                      autocomplete="current-password"
                      placeholder="输入密码"
                    />
                    <button
                      type="button"
                      :title="showLoginPassword ? '隐藏密码' : '显示密码'"
                      :aria-label="showLoginPassword ? '隐藏密码' : '显示密码'"
                      @click="showLoginPassword = !showLoginPassword"
                    >
                      <EyeOff v-if="showLoginPassword" :size="18" />
                      <Eye v-else :size="18" />
                    </button>
                    <span class="login-field-scan" aria-hidden="true"></span>
                  </div>

                  <label class="login-remember-row">
                    <input v-model="rememberLogin" type="checkbox" />
                    <span>记住账号</span>
                    <small>不会保存密码</small>
                  </label>

                  <button class="login-submit-button" type="submit" :disabled="busy || !loginForm.studentNo || !loginForm.password">
                    <LogIn :size="19" />
                    <span>{{ busy ? '正在验证' : '登录后台' }}</span>
                    <i v-if="busy" aria-hidden="true"></i>
                  </button>

                </form>
              </Transition>
            </section>
          </section>
        </div>

        <div v-else class="workspace admin-ledger-workspace admin-workbench-workspace">
          <div class="admin-ledger-shell admin-workbench-shell">
            <div class="admin-subnav">
              <div class="admin-subnav-date">
                <CalendarDays :size="17" />
                <span>{{ todayText }} · {{ weekdayText }}</span>
              </div>
              <nav aria-label="当前模块页面">
                <button
                  v-for="tab in activeAdminGroupTabs"
                  :key="tab.id"
                  :class="{ active: activeTab === tab.id }"
                  :aria-current="activeTab === tab.id ? 'page' : undefined"
                  type="button"
                  @click="selectTab(tab.id)"
                >
                  <component :is="tab.icon" :size="16" />
                  <span>{{ tab.label }}</span>
                  <small v-if="adminTabBadge(tab.id)">{{ adminTabBadge(tab.id) }}</small>
                </button>
              </nav>
              <div class="admin-service-state" :class="{ online: healthOk }">
                <span aria-hidden="true"></span>
                {{ healthOk ? '本机服务正常' : '本机服务未连接' }}
              </div>
            </div>

            <div class="admin-ledger-main admin-workbench-main">
              <div class="admin-contextbar">
                <div class="admin-context-heading">
                  <span class="admin-context-icon"><component :is="activeTabInfo.icon" :size="21" /></span>
                  <div>
                  <p class="eyebrow">{{ roleDashboard.eyebrow }}</p>
                  <h3>{{ activeTabInfo.label }}</h3>
                  <span>{{ activeTabDescription }}</span>
                  </div>
                </div>
                <div class="admin-context-stats" aria-label="后台关键状态">
                  <div v-for="item in adminContextStats" :key="item.label" :class="`tone-${item.tone || 'steady'}`">
                    <span>{{ item.label }}</span>
                    <strong>{{ item.value }}</strong>
                  </div>
                </div>
              </div>

              <div :key="activeTab" class="admin-tab-stage">
          <section v-if="activeTab === 'overview'" class="work-section tab-overview">
            <div class="admin-home-layout">
              <section class="admin-duty-board" aria-labelledby="admin-duty-title">
                <div class="subsection-head">
                  <div>
                    <h4 id="admin-duty-title"><CalendarDays :size="17" />今日部长排班</h4>
                    <span>按后台设置的值班时段自动分组</span>
                  </div>
                  <button class="ghost-button" @click="selectTab('schedules')"><Plus :size="16" />安排排班</button>
                </div>
                <div v-if="todayPeriodSummary.length" class="admin-duty-timeline">
                  <article
                    v-for="period in todayPeriodSummary"
                    :key="period.key"
                    class="admin-duty-period"
                    :class="{ active: period.active, missing: period.missing }"
                  >
                    <time>{{ period.startTime }}</time>
                    <span class="admin-duty-node" aria-hidden="true"></span>
                    <div class="admin-duty-card">
                      <div class="admin-duty-card-head">
                        <strong>{{ period.timeText }}</strong>
                        <span>{{ period.count }} 人</span>
                      </div>
                      <div class="admin-duty-members" :class="{ empty: !period.people.length }">
                        <span v-for="person in period.people" :key="person.key">{{ person.name }}</span>
                        <em v-if="!period.people.length">待安排部长</em>
                      </div>
                    </div>
                  </article>
                </div>
                <div v-else class="admin-duty-empty">
                  <Clock3 :size="22" />
                  <strong>{{ kioskEmptyBoardText }}</strong>
                  <span>保存值班时间段和排班后，这里会和签到台同步显示。</span>
                </div>
              </section>

            </div>

            <div class="admin-quick-row" aria-label="快捷操作">
              <button
                v-for="action in adminQuickActions"
                :key="`${action.label}-${action.tab || action.view}`"
                type="button"
                class="admin-quick-action"
                @click="runRoleAction(action)"
              >
                <component :is="action.icon" :size="18" />
                <span>{{ action.label }}</span>
              </button>
            </div>

            <div class="overview-layout admin-home-bottom">
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
                  <h4>今日值班记录</h4>
                  <span>{{ todayRecords.length }} 条</span>
                </div>
                <div class="table-wrap compact-table overview-table-wrap">
                  <table>
                    <thead>
                      <tr><th>姓名</th><th>签到</th><th>签退</th><th>状态</th><th>时长</th></tr>
                    </thead>
                    <tbody>
                      <tr v-for="row in todayRecords" :key="row.id">
                        <td>{{ row.name }}</td>
                        <td>{{ timeText(row.checkInTime) }}</td>
                        <td>{{ timeText(row.checkOutTime) }}</td>
                        <td><span class="status-badge" :class="row.effectiveStatus?.toLowerCase()">{{ effectiveStatusText(row.effectiveStatus) }}</span></td>
                        <td>{{ formatHours(row.validHours) }} h</td>
                      </tr>
                      <tr v-if="todayRecords.length === 0"><td colspan="5" class="empty">今日暂无值班记录</td></tr>
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
              <div><span>有效时长</span><strong>{{ formatHours(attendanceRecordHours) }} h</strong></div>
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
              <div><span>总时长</span><strong>{{ formatHours(totalHours) }}</strong></div>
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
                        {{ formatHours(weeklyCell(day.dutyDate, user.userId)) }} h
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
                    <td>{{ formatHours(row.totalHours) }} h</td>
                  </tr>
                  <tr v-if="stats.length === 0"><td colspan="6" class="empty">暂无有效统计</td></tr>
                </tbody>
              </table>
            </div>
          </section>

          <TrainingPanel
            v-if="activeTab === 'trainings'"
            :current-user="currentUser"
            @notify="notify($event.message, $event.type)"
          />

          <SchedulePanel
            v-if="activeTab === 'schedules'"
            :current-user="currentUser"
            @notify="notify($event.message, $event.type)"
          />

          <RepairPanel
            v-if="activeTab === 'repairs'"
            :current-user="currentUser"
            @notify="notify($event.message, $event.type)"
          />

          <DataCenterPanel
            v-if="activeTab === 'data'"
            :summary="dataCenterSummary"
            :backups="backups"
            :busy="busy"
            :restore-file="restoreFile"
            :can-delete-backups="canDeleteBackups"
            :can-restore-backups="canRestoreBackups"
            :can-view-logs="canViewLogs"
            @refresh="loadDataCenter"
            @create-backup="createBackup"
            @download-backup="downloadBackup"
            @delete-backup="deleteBackup"
            @select-restore-file="selectRestoreFile"
            @restore-backup="restoreBackup"
            @notify="notify($event.message, $event.type)"
          />

          <section v-if="activeTab === 'settings'" class="work-section tab-settings">
            <div class="section-head">
              <div>
                <h3>值班设置</h3>
                <span>控制签到台显示的值班星期和值班时间段</span>
              </div>
            </div>

            <div class="settings-stack">
              <section class="settings-card">
                <div class="settings-card-head">
                  <div>
                    <h4>值班星期</h4>
                    <span>关闭某天后，当天记录仍可测试提交，但不会计入有效值班</span>
                  </div>
                  <button class="ghost-button" @click="saveWeekdays"><Save :size="16" />保存星期</button>
                </div>
                <div class="weekday-grid">
                  <label v-for="day in weekdays" :key="day.weekday" :class="{ selected: day.enabled }">
                    <input v-model="day.enabled" type="checkbox" />
                    <CalendarDays :size="18" />
                    <span>{{ day.weekday_name }}</span>
                  </label>
                </div>
              </section>

              <section class="settings-card">
                <div class="settings-card-head">
                  <div>
                    <h4>值班时间段</h4>
                    <span>签到台会按后台保存的值班时间段统计部长人数</span>
                  </div>
                  <div class="settings-card-actions">
                    <button class="ghost-button" @click="addDutyPeriod"><Plus :size="16" />新增时间段</button>
                    <button class="ghost-button" @click="saveDutyPeriods"><Save :size="16" />保存时间段</button>
                  </div>
                </div>
                <div class="duty-period-list">
                  <div v-if="dutyPeriodDrafts.length === 0" class="duty-period-empty">
                    <Clock3 :size="18" />
                    <strong>暂无值班时间段</strong>
                  </div>
                  <article v-for="(period, index) in dutyPeriodDrafts" :key="`${period.startTime}-${period.endTime}-${index}`" class="duty-period-row">
                    <input v-model="period.startTime" type="time" />
                    <span>至</span>
                    <input v-model="period.endTime" type="time" />
                    <button class="ghost-button danger-button" type="button" @click="removeDutyPeriod(index)">删除</button>
                  </article>
                </div>
              </section>
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

          <ProfilePanel
            v-if="activeTab === 'profile'"
            :profile="profile"
            :password-form="passwordForm"
            :my-record-range="myRecordRange"
            :my-records="myRecords"
            :my-record-count="myRecordCount"
            :my-record-hours="myRecordHours"
            :grade-options="profileGradeOptions"
            @save-profile="saveProfile"
            @change-password="changePassword"
            @load-my-records="loadMyRecords"
          />
              </div>
            </div>
          </div>
        </div>
      </section>

      <Transition name="toast-pop">
        <div v-if="toast.message" class="toast" :class="toast.type" role="status" aria-live="polite">{{ toast.message }}</div>
      </Transition>
    </main>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import {
  BadgeCheck,
  CalendarDays,
  CheckCircle2,
  ClipboardCheck,
  ClipboardList,
  Clock3,
  Database,
  Download,
  Eye,
  EyeOff,
  Gauge,
  GraduationCap,
  History,
  LayoutDashboard,
  ListChecks,
  LogIn,
  Power,
  PowerOff,
  Plus,
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
  UsersRound,
  Wrench,
  X
} from '@lucide/vue'
import { api, del, post, put, setToken } from './api.js'
import DataCenterPanel from './components/DataCenterPanel.vue'
import ProfilePanel from './components/ProfilePanel.vue'
import RepairPanel from './components/RepairPanel.vue'
import SchedulePanel from './components/SchedulePanel.vue'
import TrainingPanel from './components/TrainingPanel.vue'

const view = ref('kiosk')
const activeTab = ref('overview')
const healthOk = ref(false)
const busy = ref(false)
const studentNo = ref('')
const lookupResult = ref(null)
const attendanceSuccess = ref(null)
const liveNow = ref(new Date())
const currentUser = ref(null)
const pendingRecords = ref([])
const attendanceRecords = ref([])
const openRecords = ref([])
const todayRecords = ref([])
const todaySchedule = ref([])
const weekSchedule = ref([])
const dutyPeriods = ref([])
const publicDutyWeekdays = ref([])
const dutyPeriodDrafts = ref([])
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
const myTrainingHours = ref(0)
const myTrainingCount = ref(0)
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
const setupForm = reactive({ account: '', name: '', password: '', confirmPassword: '' })
const setupRequired = ref(false)
const showLoginPassword = ref(false)
const showSetupPassword = ref(false)
const rememberLogin = ref(true)
const loginVerified = ref(false)
const loginError = ref(false)
const profile = reactive({ phone: '', major: '', grade: '', qq: '' })
const passwordForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })
const newMember = reactive({ studentNo: '', name: '', phone: '', major: '', grade: '', qq: '' })
const manualRecord = reactive({ studentNo: '', checkInTime: '', checkOutTime: '', reason: '' })
const importInput = ref(null)
const importFile = ref(null)
const importResult = ref(null)
const restoreFile = ref(null)
const operationLogs = ref([])
const backups = ref([])
const dataCenterSummary = ref(null)
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
const loginAccountStorageKey = 'ca-attendance-remembered-account'
let loginErrorTimer = null

const tabs = [
  { id: 'overview', label: '今日', icon: Gauge, roles: ['MINISTER', 'PRESIDENT', 'ADMIN'] },
  { id: 'reviews', label: '审核', icon: ListChecks, roles: ['MINISTER', 'PRESIDENT', 'ADMIN'] },
  { id: 'records', label: '记录', icon: ClipboardList, roles: ['PRESIDENT', 'ADMIN'] },
  { id: 'members', label: '成员', icon: UsersRound, roles: ['PRESIDENT', 'ADMIN'] },
  { id: 'stats', label: '统计', icon: LayoutDashboard, roles: ['MINISTER', 'PRESIDENT', 'ADMIN'] },
  { id: 'trainings', label: '培训', icon: GraduationCap, roles: ['PRESIDENT', 'ADMIN'] },
  { id: 'schedules', label: '排班', icon: CalendarDays, roles: ['PRESIDENT', 'ADMIN'] },
  { id: 'repairs', label: '维修', icon: Wrench, roles: ['MINISTER', 'PRESIDENT', 'ADMIN'] },
  { id: 'data', label: '数据', icon: Database, roles: ['PRESIDENT', 'ADMIN'] },
  { id: 'settings', label: '设置', icon: SlidersHorizontal, roles: ['PRESIDENT', 'ADMIN'] },
  { id: 'logs', label: '日志', icon: History, roles: ['ADMIN'] },
  { id: 'profile', label: '个人', icon: UserRound, roles: ['MEMBER', 'MINISTER', 'PRESIDENT', 'ADMIN'] }
]

const adminTabDescriptions = {
  overview: '今日排班、待处理事项和关键值班状态集中在这里。',
  reviews: '处理成员提交的签到和签退审核，优先清掉待办。',
  records: '查询、补录和修正值班记录，适合做月度核对。',
  members: '维护协会成员账号、角色、状态和批量导入。',
  stats: '按时间范围汇总值班时长、次数和明细。',
  trainings: '管理培训场次、参与名单和计入值班时长的培训记录。',
  schedules: '按已设置的值班时间段安排部长值班。',
  repairs: '登记维修工单，预览协议并跟踪处理状态。',
  data: '集中完成模板下载、统计导出、备份恢复和换届交接。',
  settings: '配置值班星期和值班时间段。',
  logs: '查看后台关键操作记录，用于追溯和交接。',
  profile: '维护个人资料、密码和自己的值班记录。'
}

const adminNavBlueprint = [
  { id: 'duty', label: '值班', icon: Gauge, tabs: ['overview', 'reviews', 'records', 'stats', 'schedules'] },
  { id: 'people', label: '人员', icon: UsersRound, tabs: ['members', 'profile'] },
  { id: 'affairs', label: '事务', icon: Wrench, tabs: ['trainings', 'repairs'] },
  { id: 'system', label: '系统', icon: SlidersHorizontal, tabs: ['data', 'settings', 'logs'] }
]

const logActionOptions = [
  { value: 'INITIALIZE_SYSTEM', label: '初始化系统' },
  { value: 'CREATE_USER', label: '新增成员' },
  { value: 'IMPORT_USERS', label: '批量导入成员' },
  { value: 'UPDATE_USER', label: '修改成员信息' },
  { value: 'RESET_PASSWORD', label: '重置密码' },
  { value: 'DELETE_USER', label: '删除成员' },
  { value: 'BULK_UPDATE_USER_STATUS', label: '批量启停账号' },
  { value: 'REVIEW_ATTENDANCE', label: '审核签到记录' },
  { value: 'MANUAL_CREATE_ATTENDANCE', label: '新增签到记录' },
  { value: 'DELETE_ATTENDANCE_RECORD', label: '删除签到记录' },
  { value: 'CREATE_TRAINING', label: '新增培训' },
  { value: 'UPDATE_TRAINING', label: '修改培训' },
  { value: 'ARCHIVE_TRAINING', label: '归档培训' },
  { value: 'CREATE_TRAINING_PARTICIPANT', label: '新增培训参与记录' },
  { value: 'UPDATE_TRAINING_PARTICIPANT', label: '修改培训参与记录' },
  { value: 'DELETE_TRAINING_PARTICIPANT', label: '删除培训参与记录' },
  { value: 'IMPORT_TRAINING_PARTICIPANTS', label: '导入培训名单' },
  { value: 'CREATE_DUTY_SCHEDULE', label: '新增排班' },
  { value: 'UPDATE_DUTY_SCHEDULE', label: '修改排班' },
  { value: 'ARCHIVE_DUTY_SCHEDULE', label: '归档排班' },
  { value: 'IMPORT_DUTY_SCHEDULES', label: '批量导入排班' },
  { value: 'UPDATE_DUTY_PERIODS', label: '调整值班时间段' },
  { value: 'CREATE_REPAIR_CASE', label: '新增维修事务' },
  { value: 'UPDATE_REPAIR_CASE', label: '修改维修事务' },
  { value: 'DELETE_REPAIR_CASE', label: '删除维修事务' },
  { value: 'RESTORE_REPAIR_CASE', label: '恢复维修事务' },
  { value: 'PURGE_REPAIR_CASE', label: '永久删除维修事务' },
  { value: 'UPDATE_DUTY_WEEKDAYS', label: '调整值班星期' },
  { value: 'MANUAL_UPDATE_ATTENDANCE', label: '手动修改记录' },
  { value: 'RESTORE_BACKUP', label: '恢复备份' }
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
const activeTabInfo = computed(() => availableTabs.value.find(tab => tab.id === activeTab.value) || availableTabs.value[0] || tabs[0])
const activeTabDescription = computed(() => adminTabDescriptions[activeTabInfo.value?.id] || '管理当前模块的数据和操作。')
const adminNavGroups = computed(() => {
  const tabMap = new Map(availableTabs.value.map(tab => [tab.id, tab]))
  return adminNavBlueprint
    .map(group => ({
      ...group,
      tabs: group.tabs.map(id => tabMap.get(id)).filter(Boolean)
    }))
    .filter(group => group.tabs.length > 0)
})
const activeAdminGroup = computed(() => (
  adminNavGroups.value.find(group => group.tabs.some(tab => tab.id === activeTab.value)) || adminNavGroups.value[0]
))
const activeAdminGroupTabs = computed(() => activeAdminGroup.value?.tabs || [])
const adminContextStats = computed(() => {
  const service = { label: '本机服务', value: healthOk.value ? '在线' : '未连接', tone: healthOk.value ? 'ok' : 'warn' }
  if (currentUser.value?.role === 'MEMBER') {
    return [
      service,
      { label: '我的记录', value: myRecordCount.value, tone: 'steady' },
      { label: '有效时长', value: `${formatHours(myRecordHours.value)} h`, tone: 'steady' },
      { label: '年级', value: profile.grade || '-', tone: 'steady' }
    ]
  }
  return [
    service,
    { label: '待审核', value: overview.pendingCount, tone: overview.pendingCount > 0 ? 'warn' : 'steady' },
    { label: '未签退', value: overview.dashboard.todayOpenCount, tone: overview.dashboard.todayOpenCount > 0 ? 'warn' : 'steady' },
    { label: '本周时长', value: `${formatHours(overview.dashboard.weekValidHours)} h`, tone: 'steady' }
  ]
})
const adminQuickActions = computed(() => roleActions.value.slice(0, 5))
const canExport = computed(() => ['PRESIDENT', 'ADMIN'].includes(currentUser.value?.role))
const canManageUsers = computed(() => ['PRESIDENT', 'ADMIN'].includes(currentUser.value?.role))
const canImportMembers = computed(() => ['PRESIDENT', 'ADMIN'].includes(currentUser.value?.role))
const canAddAttendanceRecords = computed(() => ['PRESIDENT', 'ADMIN'].includes(currentUser.value?.role))
const canDeleteAttendanceRecords = computed(() => ['PRESIDENT', 'ADMIN'].includes(currentUser.value?.role))
const canViewLogs = computed(() => currentUser.value?.role === 'ADMIN')
const canBackupData = computed(() => ['PRESIDENT', 'ADMIN'].includes(currentUser.value?.role))
const canUseDataCenter = computed(() => ['PRESIDENT', 'ADMIN'].includes(currentUser.value?.role))
const canDeleteBackups = computed(() => currentUser.value?.role === 'ADMIN')
const canRestoreBackups = computed(() => currentUser.value?.role === 'ADMIN')
const setupFormReady = computed(() => (
  /^[A-Za-z0-9_-]{4,32}$/.test(setupForm.account) &&
  setupForm.name.length > 0 &&
  setupForm.password.length >= 6 &&
  setupForm.password === setupForm.confirmPassword
))
const lookupCandidates = computed(() => lookupResult.value?.matches || [])
const totalHours = computed(() => stats.value.reduce((sum, row) => sum + Number(row.totalHours || 0), 0))
const totalCount = computed(() => stats.value.reduce((sum, row) => sum + Number(row.dutyCount || 0), 0))
const attendanceRecordHours = computed(() => attendanceRecords.value.reduce((sum, row) => sum + Number(row.validHours || 0), 0))
const attendanceRecordPendingCount = computed(() => attendanceRecords.value.filter(row =>
  row.checkInStatus === 'PENDING' || row.checkOutStatus === 'PENDING'
).length)
const myRecordHours = computed(() => myRecords.value.reduce((sum, row) => sum + Number(row.validHours || 0), 0) + Number(myTrainingHours.value || 0))
const myRecordCount = computed(() => myRecords.value.length + Number(myTrainingCount.value || 0))
const profileGradeOptions = Array.from({ length: 2057 - 2007 + 1 }, (_, index) => `${2007 + index}级`)
const userTotalPages = computed(() => Math.max(1, Math.ceil(userTotal.value / userPageSize.value)))
const logTotalPages = computed(() => Math.max(1, Math.ceil(logTotal.value / logPageSize)))
const overviewDutyDaysText = computed(() => overview.dutyDays.length ? overview.dutyDays.join('、') : '未设置')
const todayText = computed(() => today.toLocaleDateString('zh-CN', { month: 'long', day: 'numeric' }))
const weekdayText = computed(() => today.toLocaleDateString('zh-CN', { weekday: 'long' }))
const todayDayNumber = computed(() => String(today.getDate()).padStart(2, '0'))
const todayMonthText = computed(() => `${today.getFullYear()} 年 ${today.getMonth() + 1} 月`)
const kioskDateText = computed(() => liveNow.value.toLocaleDateString('zh-CN', {
  month: 'long',
  day: 'numeric',
  weekday: 'long'
}))
const kioskTimeText = computed(() => liveNow.value.toLocaleTimeString('zh-CN', {
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
  hour12: false
}))
const allWeekdayOptions = [
  { weekday: 1, name: '周一', fullName: '星期一' },
  { weekday: 2, name: '周二', fullName: '星期二' },
  { weekday: 3, name: '周三', fullName: '星期三' },
  { weekday: 4, name: '周四', fullName: '星期四' },
  { weekday: 5, name: '周五', fullName: '星期五' },
  { weekday: 6, name: '周六', fullName: '星期六' },
  { weekday: 7, name: '周日', fullName: '星期日' }
]
const kioskEnabledWeekdays = computed(() => {
  if (!publicDutyWeekdays.value.length) return allWeekdayOptions
  return publicDutyWeekdays.value
    .map(row => {
      const weekday = Number(row.weekday)
      const fallback = allWeekdayOptions.find(day => day.weekday === weekday)
      return {
        weekday,
        name: shortWeekdayName(row.weekday_name || row.weekdayName || fallback?.fullName),
        fullName: row.weekday_name || row.weekdayName || fallback?.fullName || `星期${weekday}`,
        enabled: row.enabled === true || row.enabled === 1 || row.enabled === '1' || row.enabled === 'true'
      }
    })
    .filter(day => day.weekday >= 1 && day.weekday <= 7 && day.enabled)
})
const todayWeekdayValue = today.getDay() || 7
const hasDutyPeriodSettings = computed(() => dutyPeriodsForDisplay().length > 0)
const todayIsConfiguredDutyDay = computed(() => kioskEnabledWeekdays.value.some(day => day.weekday === todayWeekdayValue))
const todayPeriodSummary = computed(() => {
  liveNow.value
  return todayIsConfiguredDutyDay.value ? periodSummaryForSlots(todaySchedule.value) : []
})
const kioskCurrentPeriodText = computed(() => {
  const activePeriod = todayPeriodSummary.value.find(period => period.active)
  if (activePeriod) return `当前时段 ${activePeriod.timeText}`
  if (!todayIsConfiguredDutyDay.value) return '今日非值班日'
  return '当前无值班时段'
})
const kioskEmptyBoardText = computed(() => {
  if (!todayIsConfiguredDutyDay.value) return '今日非值班日'
  if (!hasDutyPeriodSettings.value) return '请先在后台设置值班时间段'
  return '今日暂无部长排班'
})
const kioskWeekSummary = computed(() => {
  return kioskEnabledWeekdays.value.map(day => {
    const weekday = day.weekday
    const daySlots = weekSchedule.value.filter(slot => Number(slot.weekday) === weekday)
    const dayPeriods = periodSummaryForSlots(daySlots)
    return {
      weekday,
      name: day.name,
      count: dayPeriods.filter(period => period.count > 0).length,
      isToday: weekday === todayWeekdayValue
    }
  })
})
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
      headerMeta: `待审核 ${overview.pendingCount} 条 · 本周 ${formatHours(overview.dashboard.weekValidHours)} h`,
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
      { label: '有效时长', value: `${formatHours(myRecordHours.value)} h` },
      { label: '年级', value: profile.grade || '-' }
    ]
  }
  if (role === 'MINISTER') {
    return [
      { label: '待审核', value: overview.pendingCount },
      { label: '今日记录', value: overview.dashboard.todayRecordCount },
      { label: '本周时长', value: `${formatHours(overview.dashboard.weekValidHours)} h` }
    ]
  }
  if (role === 'PRESIDENT') {
    return [
      { label: '今日未签退', value: overview.dashboard.todayOpenCount },
      { label: '今日待审核', value: overview.dashboard.todayPendingCount },
      { label: '本年时长', value: `${formatHours(overview.dashboard.yearValidHours)} h` }
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
      { label: '培训', tab: 'trainings', icon: GraduationCap },
      { label: '签到台', view: 'kiosk', icon: ScanLine }
    ],
    MINISTER: [
      { label: '待审核', tab: 'reviews', icon: ListChecks },
      { label: '统计', tab: 'stats', icon: LayoutDashboard },
      { label: '培训', tab: 'trainings', icon: GraduationCap },
      { label: '维修', tab: 'repairs', icon: Wrench },
      { label: '个人中心', tab: 'profile', icon: UserRound }
    ],
    PRESIDENT: [
      { label: '成员', tab: 'members', icon: UsersRound },
      { label: '记录', tab: 'records', icon: ClipboardList },
      { label: '培训', tab: 'trainings', icon: GraduationCap },
      { label: '排班', tab: 'schedules', icon: CalendarDays },
      { label: '维修', tab: 'repairs', icon: Wrench },
      { label: '数据中心', tab: 'data', icon: Database },
      { label: '统计', tab: 'stats', icon: LayoutDashboard },
      { label: '值班星期', tab: 'settings', icon: SlidersHorizontal }
    ],
    ADMIN: [
      { label: '记录', tab: 'records', icon: ClipboardList },
      { label: '成员', tab: 'members', icon: UsersRound },
      { label: '培训', tab: 'trainings', icon: GraduationCap },
      { label: '排班', tab: 'schedules', icon: CalendarDays },
      { label: '维修', tab: 'repairs', icon: Wrench },
      { label: '数据中心', tab: 'data', icon: Database },
      { label: '日志', tab: 'logs', icon: History }
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
  loadRememberedLoginAccount()
  kioskClockTimer = window.setInterval(() => {
    liveNow.value = new Date()
  }, 1000)
  await checkHealth()
  if (healthOk.value) await checkSetupStatus()
  if (!setupRequired.value) await loadPublicSchedules()
  overviewRefreshTimer = window.setInterval(refreshVisibleOverview, 30_000)
  document.addEventListener('visibilitychange', refreshVisibleOverview)
  window.addEventListener('focus', refreshVisibleOverview)
})

let kioskClockTimer = null
let overviewRefreshTimer = null

onBeforeUnmount(() => {
  if (kioskClockTimer) window.clearInterval(kioskClockTimer)
  if (overviewRefreshTimer) window.clearInterval(overviewRefreshTimer)
  if (loginErrorTimer) window.clearTimeout(loginErrorTimer)
  document.removeEventListener('visibilitychange', refreshVisibleOverview)
  window.removeEventListener('focus', refreshVisibleOverview)
})

function refreshVisibleOverview() {
  if (
    document.visibilityState === 'visible' &&
    currentUser.value &&
    activeTab.value === 'overview' &&
    !busy.value
  ) {
    void loadOverview()
  }
}

async function checkHealth() {
  try {
    await api('/api/health')
    healthOk.value = true
  } catch {
    healthOk.value = false
  }
}

async function checkSetupStatus() {
  try {
    const status = await api('/api/setup/status')
    setupRequired.value = !status.initialized
    if (setupRequired.value) view.value = 'dashboard'
  } catch {
    setupRequired.value = false
  }
}

async function loadPublicSchedules() {
  await run(async () => {
    const [todayItems, weekItems, periods, dutyWeekdays] = await Promise.all([
      api('/api/public/schedules/today'),
      api('/api/public/schedules/week'),
      api('/api/public/duty-periods'),
      api('/api/public/duty-weekdays')
    ])
    todaySchedule.value = todayItems
    weekSchedule.value = weekItems
    dutyPeriods.value = normalizeDutyPeriods(periods)
    publicDutyWeekdays.value = dutyWeekdays
  }, false)
}

async function lookupMember() {
  if (!studentNo.value) return notify('请输入学号或姓名', 'warn')
  attendanceSuccess.value = null
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
    const result = lookupResult.value
    const submitStudentNo = lookupResult.value?.studentNo || studentNo.value
    if (!submitStudentNo) {
      notify('请先查询并确认成员', 'warn')
      return
    }
    const res = await post('/api/public/attendance/submit', { studentNo: submitStudentNo })
    attendanceSuccess.value = {
      name: result?.name || submitStudentNo,
      actionLabel: result?.action === 'CHECK_OUT' ? '签退' : '签到',
      message: res.message
    }
    lookupResult.value = null
    studentNo.value = ''
  })
}

function resetKiosk() {
  attendanceSuccess.value = null
  lookupResult.value = null
  studentNo.value = ''
}

function openDashboard() {
  view.value = 'dashboard'
}

function returnToKiosk() {
  loginVerified.value = false
  loginError.value = false
  showLoginPassword.value = false
  loginForm.password = ''
  view.value = 'kiosk'
}

async function login() {
  busy.value = true
  loginError.value = false
  try {
    const res = await post('/api/auth/login', loginForm)
    setToken(res.token)
    saveRememberedLoginAccount()
    loginVerified.value = true
    await new Promise(resolve => window.setTimeout(resolve, 720))
    currentUser.value = res
    profile.phone = ''
    profile.major = ''
    profile.grade = ''
    profile.qq = ''
    clearPasswordForm()
    loginForm.password = ''
    showLoginPassword.value = false
    loginVerified.value = false
    notify('已登录后台', 'success')
    await selectTab(availableTabs.value[0]?.id || 'profile')
  } catch (error) {
    loginVerified.value = false
    triggerLoginError()
    notify(error.message, 'error')
  } finally {
    busy.value = false
  }
}

async function initializeSystem() {
  if (!setupFormReady.value) {
    if (setupForm.password !== setupForm.confirmPassword) return notify('两次输入的密码不一致', 'warn')
    return notify('请完整填写管理员信息', 'warn')
  }
  busy.value = true
  loginError.value = false
  try {
    const res = await post('/api/setup/initialize', {
      account: setupForm.account,
      name: setupForm.name,
      password: setupForm.password
    })
    setToken(res.token)
    loginVerified.value = true
    await new Promise(resolve => window.setTimeout(resolve, 720))
    currentUser.value = res
    setupRequired.value = false
    loginVerified.value = false
    showSetupPassword.value = false
    setupForm.password = ''
    setupForm.confirmPassword = ''
    loginForm.studentNo = res.studentNo
    notify('系统初始化完成', 'success')
    await loadPublicSchedules()
    await selectTab(availableTabs.value[0]?.id || 'overview')
  } catch (error) {
    loginVerified.value = false
    triggerLoginError()
    notify(error.message, 'error')
  } finally {
    busy.value = false
  }
}

function logout() {
  setToken('')
  currentUser.value = null
  loginForm.password = ''
  showLoginPassword.value = false
  loginVerified.value = false
  clearPasswordForm()
  notify('已退出', 'info')
}

function loadRememberedLoginAccount() {
  try {
    const rememberedAccount = window.localStorage.getItem(loginAccountStorageKey)
    if (rememberedAccount) loginForm.studentNo = rememberedAccount
  } catch {
    // Local storage can be disabled without blocking login.
  }
}

function saveRememberedLoginAccount() {
  try {
    if (rememberLogin.value) {
      window.localStorage.setItem(loginAccountStorageKey, loginForm.studentNo)
    } else {
      window.localStorage.removeItem(loginAccountStorageKey)
    }
  } catch {
    // Local storage can be disabled without blocking login.
  }
}

function triggerLoginError() {
  loginError.value = false
  window.requestAnimationFrame(() => {
    loginError.value = true
    window.clearTimeout(loginErrorTimer)
    loginErrorTimer = window.setTimeout(() => {
      loginError.value = false
    }, 520)
  })
}

async function selectAdminGroup(group) {
  if (!group?.tabs?.length || group.tabs.some(tab => tab.id === activeTab.value)) return
  await selectTab(group.tabs[0].id)
}

function adminTabBadge(tabId) {
  if (tabId === 'reviews' && overview.pendingCount > 0) return overview.pendingCount
  if (tabId === 'overview' && overview.dashboard.todayOpenCount > 0) return overview.dashboard.todayOpenCount
  return ''
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
  if (tab === 'data') await loadDataCenter()
  if (tab === 'settings') await loadDutySettings()
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
    const [pending, summary, dutyDays, dashboard, open, records, todayItems, weekItems, periods, publicWeekdays] = await Promise.all([
      api('/api/attendance/reviews/pending'),
      api(`/api/stats/summary?from=${today.getFullYear()}-01-01&to=${todayValue}`),
      api('/api/settings/weekdays'),
      api(`/api/stats/dashboard?date=${todayValue}`),
      api(`/api/attendance/open?from=${todayValue}&to=${todayValue}`),
      api(`/api/attendance?from=${todayValue}&to=${todayValue}`),
      api('/api/public/schedules/today'),
      api('/api/public/schedules/week'),
      api('/api/public/duty-periods'),
      api('/api/public/duty-weekdays')
    ])
    overview.pendingCount = pending.length
    overview.totalHours = summary.reduce((sum, row) => sum + Number(row.totalHours || 0), 0)
    overview.totalCount = summary.reduce((sum, row) => sum + Number(row.dutyCount || 0), 0)
    overview.dutyDays = dutyDays.filter(day => day.enabled).map(day => day.weekday_name)
    overview.topRows = summary.slice(0, 5)
    Object.assign(overview.dashboard, dashboard)
    openRecords.value = open
    todayRecords.value = records
      .slice()
      .sort((a, b) => String(b.checkInTime || '').localeCompare(String(a.checkInTime || '')))
    todaySchedule.value = todayItems
    weekSchedule.value = weekItems
    dutyPeriods.value = normalizeDutyPeriods(periods)
    publicDutyWeekdays.value = publicWeekdays
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
    const [records, training] = await Promise.all([
      api(`/api/attendance/me?from=${myRecordRange.from}&to=${myRecordRange.to}`),
      api(`/api/trainings/me/hours?from=${myRecordRange.from}&to=${myRecordRange.to}`)
    ])
    myRecords.value = records
    myTrainingHours.value = Number(training.trainingHours || 0)
    myTrainingCount.value = Number(training.trainingCount || 0)
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
  if (!canDeleteAttendanceRecords.value) return notify('只有会长或管理员可以删除签到记录', 'warn')
  const timeLabel = item.checkInTime ? timeText(item.checkInTime) : item.dutyDate
  if (!dangerConfirm(`确认删除 ${item.name}（${item.studentNo}）在 ${timeLabel} 的签到记录？删除后无法恢复。`, '删除')) return
  await run(async () => {
    await del(`/api/attendance/${item.id}`)
    notify('签到记录已删除，删除前已自动备份', 'success')
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
    const backupText = result.safetyBackup ? `，操作前备份：${result.safetyBackup.filename}` : ''
    notify(`批量${label}完成：更新 ${result.updated}，无变化 ${result.unchanged}，跳过 ${result.skipped}${backupText}`,
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
    notify('成员已删除，删除前已自动备份', 'success')
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

async function loadDataCenter() {
  if (!canUseDataCenter.value) return
  await run(async () => {
    const [summary, items] = await Promise.all([
      api('/api/maintenance/summary'),
      api('/api/maintenance/backups')
    ])
    dataCenterSummary.value = summary
    backups.value = items
  }, false)
}

async function createBackup() {
  if (!canBackupData.value) return notify('只有会长或管理员可以备份数据', 'warn')
  await run(async () => {
    await post('/api/maintenance/backups')
    notify('备份已生成', 'success')
    await loadDataCenter()
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
    await loadDataCenter()
  })
}

function selectRestoreFile(event) {
  restoreFile.value = event.target.files?.[0] || null
}

async function restoreBackup() {
  if (!canRestoreBackups.value) return notify('只有管理员可以恢复备份', 'warn')
  if (!restoreFile.value) return notify('请选择备份 zip 文件', 'warn')
  if (!dangerConfirm('恢复会覆盖当前成员、签到记录、日志和值班星期。系统会先自动备份当前数据，恢复成功后需要重新登录。', '恢复')) return

  const formData = new FormData()
  formData.append('file', restoreFile.value)
  await run(async () => {
    const result = await api('/api/maintenance/backups/restore', { method: 'POST', body: formData })
    restoreFile.value = null
    backups.value = []
    setToken('')
    currentUser.value = null
    clearPasswordForm()
    activeTab.value = 'overview'
    notify(`恢复完成，恢复前备份：${result.safetyBackup.filename}`, 'success')
  })
}

async function loadWeekdays() {
  await run(async () => {
    weekdays.value = await api('/api/settings/weekdays')
  }, false)
}

async function loadDutySettings() {
  await run(async () => {
    const [days, periods] = await Promise.all([
      api('/api/settings/weekdays'),
      api('/api/settings/duty-periods')
    ])
    weekdays.value = days
    dutyPeriods.value = normalizeDutyPeriods(periods)
    resetDutyPeriodDrafts()
  }, false)
}

async function saveWeekdays() {
  await run(async () => {
    const enabledWeekdays = weekdays.value.filter(d => d.enabled).map(d => d.weekday)
    await put('/api/settings/weekdays', { enabledWeekdays })
    notify('值班星期已保存', 'success')
  })
}

function resetDutyPeriodDrafts() {
  dutyPeriodDrafts.value = dutyPeriodsForDisplay().map(period => ({
    startTime: period.startTime,
    endTime: period.endTime
  }))
}

function addDutyPeriod() {
  const last = dutyPeriodDrafts.value.at(-1)
  dutyPeriodDrafts.value.push({
    startTime: last?.endTime || '',
    endTime: last?.endTime ? nextPeriodEnd(last.endTime) : ''
  })
}

function removeDutyPeriod(index) {
  dutyPeriodDrafts.value.splice(index, 1)
}

async function saveDutyPeriods() {
  const periods = dutyPeriodDraftsForSave()
  if (!periods) return
  await run(async () => {
    const saved = await put('/api/settings/duty-periods', { periods })
    dutyPeriods.value = normalizeDutyPeriods(saved)
    resetDutyPeriodDrafts()
    await loadPublicSchedules()
    notify('值班时间段已保存', 'success')
  })
}

function dutyPeriodDraftsForSave() {
  const periods = []
  for (const [index, draft] of dutyPeriodDrafts.value.entries()) {
    const startTime = shortTime(draft.startTime)
    const endTime = shortTime(draft.endTime)
    if (!startTime && !endTime) continue
    if (!startTime || !endTime) {
      notify(`第 ${index + 1} 个时间段未填写完整`, 'warn')
      return null
    }
    const start = timeToMinutes(startTime)
    const end = timeToMinutes(endTime)
    if (start == null || end == null) {
      notify(`第 ${index + 1} 个时间段格式不正确`, 'warn')
      return null
    }
    if (end <= start) {
      notify(`第 ${index + 1} 个时间段结束时间必须晚于开始时间`, 'warn')
      return null
    }
    periods.push({ startTime, endTime })
  }
  if (periods.length === 0) {
    notify('请至少填写一个值班时间段', 'warn')
    return null
  }
  return periods
}

function nextPeriodEnd(startTime) {
  const start = timeToMinutes(startTime)
  if (start == null) return ''
  const end = Math.min(start + 120, 23 * 60 + 59)
  const hour = String(Math.floor(end / 60)).padStart(2, '0')
  const minute = String(end % 60).padStart(2, '0')
  return `${hour}:${minute}`
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
    const backupText = result?.safetyBackup ? `，清空前备份：${result.safetyBackup.filename}` : ''
    notify(`已清空 ${result?.deleted || 0} 条日志${backupText}`, 'success')
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

function periodSummaryForSlots(slots) {
  const periods = dutyPeriodsForDisplay()
  const groupedSlots = slotsByAssignedPeriod(slots, periods)
  return periods.map(period => {
    const key = periodKey(period)
    const people = assigneePeopleForSlots(groupedSlots.get(key) || [])
    const timeText = periodTime(period)
    return {
      key,
      startTime: shortTime(period.startTime),
      endTime: shortTime(period.endTime),
      timeText,
      count: people.length,
      people,
      active: isCurrentPeriod(period),
      missing: people.length === 0
    }
  })
}

function dutyPeriodsForDisplay() {
  return normalizeDutyPeriods(dutyPeriods.value)
}

function slotsByAssignedPeriod(slots, periods) {
  const groups = new Map(periods.map(period => [periodKey(period), []]))
  for (const slot of slots || []) {
    const key = assignedPeriodKey(slot, periods)
    if (key && groups.has(key)) {
      groups.get(key).push(slot)
    }
  }
  return groups
}

function assigneePeopleForSlots(slots) {
  const people = new Map()
  for (const slot of slots || []) {
    for (const person of slot.assignees || []) {
      if (!person?.name) continue
      const key = person.studentNo || person.name
      if (!people.has(key)) {
        people.set(key, {
          key,
          name: person.name
        })
      }
    }
  }
  return Array.from(people.values())
}

function assignedPeriodKey(slot, periods) {
  for (const period of periods) {
    const key = periodKey(period)
    if (periodKey(slot) === key) {
      return key
    }
  }
  return null
}

function isCurrentPeriod(period) {
  const start = timeToMinutes(period.startTime)
  const end = timeToMinutes(period.endTime)
  if (start == null || end == null) return false
  const now = liveNow.value.getHours() * 60 + liveNow.value.getMinutes()
  return now >= start && now < end
}

function dutyPeriodProgress(period) {
  const start = timeToMinutes(period.startTime)
  const end = timeToMinutes(period.endTime)
  if (start == null || end == null || end <= start) return 0
  const now = liveNow.value.getHours() * 60 + liveNow.value.getMinutes() + liveNow.value.getSeconds() / 60
  return Math.max(0, Math.min(100, ((now - start) / (end - start)) * 100))
}

function periodTime(period) {
  const start = shortTime(period.startTime)
  const end = shortTime(period.endTime)
  return start && end ? `${start}-${end}` : start || end || '全天'
}

function periodKey(period) {
  return `${shortTime(period.startTime)}-${shortTime(period.endTime)}`
}

function normalizeDutyPeriods(items) {
  return (items || [])
    .map((item, index) => ({
      sortOrder: Number(item.sortOrder ?? index),
      startTime: shortTime(item.startTime),
      endTime: shortTime(item.endTime)
    }))
    .filter(item => item.startTime && item.endTime)
    .sort((a, b) => timeToMinutes(a.startTime) - timeToMinutes(b.startTime) || timeToMinutes(a.endTime) - timeToMinutes(b.endTime))
}

function timeToMinutes(value) {
  if (!value) return null
  const [hour, minute] = String(value).split(':').map(part => Number(part))
  if (!Number.isFinite(hour) || !Number.isFinite(minute)) return null
  return hour * 60 + minute
}

function shortTime(value) {
  return value ? String(value).slice(0, 5) : ''
}

function shortWeekdayName(value) {
  const text = String(value || '')
  return text.startsWith('星期') ? text.replace('星期', '周') : text || '周?'
}

function timeText(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 16)
}

function formatHours(value) {
  const number = Number(value || 0)
  return Number.isInteger(number) ? String(number) : number.toFixed(2).replace(/0+$/, '').replace(/\.$/, '')
}

function formatLocalDate(value) {
  const year = value.getFullYear()
  const month = String(value.getMonth() + 1).padStart(2, '0')
  const day = String(value.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}
</script>
