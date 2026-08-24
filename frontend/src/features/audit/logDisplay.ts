export interface AuditTarget {
  targetType?: string;
  targetId?: number;
}

export interface AuditDiffRow {
  key: string;
  label: string;
  before: string;
  after: string;
}

const actionLabels: Record<string, string> = {
  CREATE: "新增",
  UPDATE: "修改",
  DELETE: "删除",
  LOGIN: "登录",
  RESTORE: "恢复",
  EXPORT: "导出",
  CREATE_USER: "新增成员",
  IMPORT_USERS: "导入成员",
  UPDATE_USER: "修改成员",
  UPDATE_PROFILE: "修改个人资料",
  RESET_PASSWORD: "重置密码",
  DELETE_USER: "删除成员",
  BULK_UPDATE_USER_STATUS: "批量修改成员状态",
  CREATE_DUTY_SCHEDULE: "新增排班",
  UPDATE_DUTY_SCHEDULE: "修改排班",
  ARCHIVE_DUTY_SCHEDULE: "归档排班",
  IMPORT_DUTY_SCHEDULES: "导入排班",
  UPDATE_DUTY_WEEKDAYS: "调整值班星期",
  UPDATE_DUTY_PERIODS: "调整值班时间段",
  UPDATE_ATTENDANCE_POLICY: "调整有效时长规则",
  REVIEW_ATTENDANCE: "审核值班记录",
  BULK_REVIEW_ATTENDANCE: "批量审核值班记录",
  MANUAL_CREATE_ATTENDANCE: "补录值班记录",
  MANUAL_UPDATE_ATTENDANCE: "修改值班记录",
  DELETE_ATTENDANCE_RECORD: "删除值班记录",
  CREATE_TRAINING: "新增培训",
  UPDATE_TRAINING: "修改培训",
  ARCHIVE_TRAINING: "归档培训",
  CREATE_TRAINING_PARTICIPANT: "新增培训参与记录",
  UPDATE_TRAINING_PARTICIPANT: "修改培训参与记录",
  DELETE_TRAINING_PARTICIPANT: "删除培训参与记录",
  IMPORT_TRAINING_PARTICIPANTS: "导入培训名单",
  CREATE_REPAIR_CASE: "新增维修事务",
  UPDATE_REPAIR_CASE: "修改维修事务",
  DELETE_REPAIR_CASE: "维修事务移入回收站",
  RESTORE_REPAIR_CASE: "恢复维修事务",
  PURGE_REPAIR_CASE: "彻底删除维修事务",
  EXPORT_CUSTOM_DATA: "自定义导出数据",
  EXPORT_DATA: "导出业务数据",
  ATTENDANCE_STATS: "导出值班统计",
  REPAIR_CASES: "导出维修事务",
  TRAINING_SESSION: "导出培训名单",
  TRAINING_SUMMARY: "导出培训统计",
  REMOTE_LOGIN_SUCCESS: "远程登录成功",
  REMOTE_LOGIN_FAILURE: "远程登录失败",
  REMOTE_LOGIN_LOCKED: "远程登录锁定",
  LOCAL_LOGIN_SUCCESS: "本机登录成功",
  LOCAL_LOGIN_FAILURE: "本机登录失败",
  LOCAL_LOGIN_LOCKED: "本机登录锁定",
  SEED_DEMO_DATA: "初始化演示数据",
};

const targetLabels: Record<string, string> = {
  users: "成员账号",
  attendance_records: "值班记录",
  duty_schedule_slots: "固定排班",
  duty_weekday_settings: "值班星期",
  app_settings: "系统设置",
  training_sessions: "培训场次",
  training_participants: "培训参与记录",
  repair_cases: "维修事务",
  system: "系统",
  authentication: "登录认证",
  remote_auth: "远程登录",
  custom_export: "数据导出",
  data_exports: "数据导出",
};

const fieldLabels: Record<string, string> = {
  id: "编号",
  userId: "成员编号",
  studentNo: "学号",
  name: "姓名",
  role: "角色",
  status: "状态",
  phone: "手机号",
  qq: "QQ",
  major: "学院",
  grade: "年级",
  weekday: "星期",
  weekdayName: "星期",
  dutyDate: "值班日期",
  trainingDate: "培训日期",
  startTime: "开始时间",
  endTime: "结束时间",
  checkInTime: "签到时间",
  checkOutTime: "签退时间",
  checkInStatus: "签到审核",
  checkOutStatus: "签退审核",
  effectiveStatus: "有效状态",
  exportType: "导出类型",
  exportLabel: "导出内容",
  operatorRole: "操作人角色",
  filters: "筛选条件",
  rows: "数据行数",
  filename: "文件名",
  details: "导出详情",
  fields: "导出字段",
  sessionRows: "培训场次行数",
  memberRows: "成员统计行数",
  from: "开始日期",
  to: "结束日期",
  keyword: "关键词",
  actionType: "操作类型",
  source: "数据源",
  validHours: "有效时长",
  durationMinutes: "分钟数",
  durationHours: "时长",
  requireDutyDay: "强制值班日",
  requireDutyPeriod: "强制值班时段",
  title: "排班名称",
  location: "地点",
  note: "备注",
  enabled: "是否启用",
  assignees: "值班人员",
  ownerName: "联系人",
  ownerPhone: "联系电话",
  deviceType: "设备类型",
  deviceBrand: "设备品牌",
  deviceModel: "具体型号",
  accessories: "附件",
  faultDescription: "故障描述",
  serviceDescription: "维修说明",
  agreementType: "协议类型",
  handlerName: "负责人",
  receivedAt: "受理时间",
  completedAt: "完成时间",
  remark: "备注",
  createdByName: "创建人",
  updatedByName: "最后修改人",
  createdAt: "创建时间",
  updatedAt: "更新时间",
  deleted: "是否删除",
  mustChangePassword: "下次登录修改密码",
};

const valueLabels: Record<string, string> = {
  ACTIVE: "启用",
  DISABLED: "停用",
  MEMBER: "成员",
  MINISTER: "部长",
  PRESIDENT: "会长",
  ADMIN: "管理员",
  VALID: "有效",
  INVALID: "无效",
  PENDING: "待审核",
  APPROVED: "已通过",
  REJECTED: "已驳回",
  INCOMPLETE: "未签退",
  REPAIRING: "进行中",
  COMPLETED: "已完成",
  CANCELED: "已取消",
  ARCHIVED: "已归档",
  REPAIR: "维修协议",
  PERSONAL_DEVICE: "维修协议",
  DISCLAIMER: "免责协议",
  PUBLIC_DEVICE: "免责协议",
  ATTENDANCE_STATS: "值班统计",
  TRAINING_SESSION: "培训名单",
  TRAINING_SUMMARY: "培训统计",
  REPAIR_CASES: "维修事务",
  OPERATION_LOGS: "操作日志",
  CUSTOM_MEMBERS: "自定义导出成员",
  CUSTOM_ATTENDANCE: "自定义导出值班记录",
  CUSTOM_TRAINING: "自定义导出培训记录",
  CUSTOM_TRAININGS: "自定义导出培训记录",
  CUSTOM_SCHEDULE: "自定义导出部长排班",
  CUSTOM_REPAIRS: "自定义导出维修事务",
  CUSTOM_LOGS: "自定义导出操作日志",
};

export function auditActionLabel(value?: string) {
  if (!value) return "未知操作";
  if (value.startsWith("CUSTOM_")) return "自定义导出";
  return actionLabels[value] || "业务操作";
}

export function auditTargetLabel(target: AuditTarget) {
  const label = targetLabels[target.targetType || ""] || target.targetType || "系统";
  return `${label}${target.targetId ? ` #${target.targetId}` : ""}`;
}

export function buildAuditDiff(beforeData?: string, afterData?: string) {
  const before = parseRecord(beforeData);
  const after = parseRecord(afterData);
  const keys = [...new Set([...Object.keys(before), ...Object.keys(after)])];
  const hiddenFields = new Set([
    "id",
    "createdByName",
    "updatedByName",
    "createdAt",
    "updatedAt",
  ]);
  const hasWeekdayName = "weekdayName" in before || "weekdayName" in after;

  return keys
    .filter((key) => !hiddenFields.has(key))
    .filter((key) => !(key === "weekday" && hasWeekdayName))
    .map<AuditDiffRow>((key) => ({
      key,
      label: fieldLabels[key] || key,
      before: formatAuditValue(before[key], key),
      after: formatAuditValue(after[key], key),
    }))
    .filter((row) => row.before !== row.after);
}

function parseRecord(value?: string): Record<string, unknown> {
  if (!value) return {};
  try {
    const parsed: unknown = JSON.parse(value);
    if (parsed && typeof parsed === "object" && !Array.isArray(parsed)) {
      return parsed as Record<string, unknown>;
    }
    return { value: parsed };
  } catch {
    return { value };
  }
}

function formatAuditValue(value: unknown, key: string): string {
  if (value == null || value === "") return "—";
  if (typeof value === "boolean") return value ? "是" : "否";
  if (typeof value === "string") return valueLabels[value] || value;
  if (typeof value === "number") return String(value);

  if (Array.isArray(value)) {
    if (isNumberArray(value)) {
      if (isDateTimeField(key) && value.length >= 3) return dateTimeArray(value);
      if (isDateField(key) && value.length >= 3) return dateArray(value);
      if (isTimeField(key) && value.length >= 2) return timeArray(value);
    }

    const names = value
      .map((item) =>
        item && typeof item === "object" && "name" in item
          ? String((item as { name?: unknown }).name || "")
          : "",
      )
      .filter(Boolean);
    if (names.length === value.length && names.length) return names.join("、");
    if (key === "fields") {
      return value
        .map((item) => typeof item === "string" ? fieldLabels[item] || item : compactValue(item))
        .join("、") || "—";
    }
    return value.map((item) => compactValue(item)).join("、") || "—";
  }

  if (typeof value === "object") return formatAuditObject(value as Record<string, unknown>);
  return compactValue(value);
}

function isNumberArray(value: unknown[]): value is number[] {
  return value.every((item) => typeof item === "number");
}

function isTimeField(key: string) {
  return /(?:start|end|checkIn|checkOut)Time$/i.test(key);
}

function isDateTimeField(key: string) {
  return /(?:created|updated|received|completed)At$/i.test(key);
}

function isDateField(key: string) {
  return /Date$/i.test(key);
}

function timeArray(value: number[]) {
  const [hours = 0, minutes = 0] = value;
  return `${pad(hours)}:${pad(minutes)}`;
}

function dateTimeArray(value: number[]) {
  const date = dateArray(value);
  if (value.length < 5) return date;
  const hours = value[3] ?? 0;
  const minutes = value[4] ?? 0;
  return `${date} ${pad(hours)}:${pad(minutes)}`;
}

function dateArray(value: number[]) {
  const [year = 0, month = 0, day = 0] = value;
  return `${year}-${pad(month)}-${pad(day)}`;
}

function pad(value: number) {
  return String(value).padStart(2, "0");
}

function compactValue(value: unknown) {
  if (value == null) return "—";
  if (typeof value === "string") return valueLabels[value] || value;
  if (typeof value === "boolean") return value ? "是" : "否";
  if (typeof value === "object") return JSON.stringify(value);
  return String(value);
}

function formatAuditObject(value: Record<string, unknown>) {
  const entries = Object.entries(value);
  if (!entries.length) return "无";
  return entries
    .map(([key, item]) => `${fieldLabels[key] || key}：${formatAuditValue(item, key)}`)
    .join("；");
}
