const issueDefinitions = [
  {
    id: 'pending',
    title: '待审核记录',
    detail: '签到或签退等待处理',
    unit: '条',
    tab: 'reviews',
    countKey: 'pendingCount',
    tone: 'amber'
  },
  {
    id: 'open',
    title: '尚未签退',
    detail: '成员已签到但尚未签退',
    unit: '条',
    tab: 'records',
    countKey: 'openCount',
    tone: 'coral'
  },
  {
    id: 'schedule',
    title: '排班待补充',
    detail: '今日值班时段尚未安排部长',
    unit: '个时段',
    tab: 'schedules',
    countKey: 'missingScheduleCount',
    tone: 'blue'
  },
  {
    id: 'repairs',
    title: '维修进行中',
    detail: '维修事务仍在处理中',
    unit: '件',
    tab: 'repairs',
    countKey: 'ongoingRepairCount',
    tone: 'teal'
  }
]

export function buildTodayIssues(counts = {}, options = {}) {
  const includeSchedule = options.includeSchedule !== false

  return issueDefinitions
    .filter(definition => includeSchedule || definition.id !== 'schedule')
    .map(definition => ({
      ...definition,
      count: normalizeCount(counts[definition.countKey])
    }))
    .filter(issue => issue.count > 0)
}

function normalizeCount(value) {
  const count = Number(value)
  return Number.isFinite(count) ? Math.max(0, Math.trunc(count)) : 0
}
