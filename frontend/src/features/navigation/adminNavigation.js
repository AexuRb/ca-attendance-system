import {
  CalendarDays,
  ClipboardList,
  Database,
  Gauge,
  GraduationCap,
  History,
  LayoutDashboard,
  ListChecks,
  SlidersHorizontal,
  UserRound,
  UsersRound,
  Wrench
} from '@lucide/vue'

export const adminTabs = [
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

export const adminTabDescriptions = {
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

export const adminNavBlueprint = [
  { id: 'duty', label: '值班', icon: Gauge, tabs: ['overview', 'reviews', 'records', 'stats', 'schedules'] },
  { id: 'people', label: '人员', icon: UsersRound, tabs: ['members', 'profile'] },
  { id: 'affairs', label: '事务', icon: Wrench, tabs: ['trainings', 'repairs'] },
  { id: 'system', label: '系统', icon: SlidersHorizontal, tabs: ['data', 'settings', 'logs'] }
]

export function tabsForRole(role) {
  return role ? adminTabs.filter(tab => tab.roles.includes(role)) : []
}

export function roleLabel(role) {
  return { MEMBER: '成员', MINISTER: '部长', PRESIDENT: '会长', ADMIN: '管理员' }[role] || role
}
