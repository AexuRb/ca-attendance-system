CREATE TABLE users (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  student_no TEXT NOT NULL UNIQUE,
  name TEXT NOT NULL,
  password_hash TEXT NOT NULL,
  role TEXT NOT NULL DEFAULT 'MEMBER' CHECK (role IN ('MEMBER', 'MINISTER', 'PRESIDENT', 'ADMIN')),
  status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'DISABLED')),
  phone TEXT,
  major TEXT,
  grade TEXT,
  qq TEXT,
  must_change_password INTEGER NOT NULL DEFAULT 1 CHECK (must_change_password IN (0, 1)),
  last_login_at DATETIME,
  disabled_at DATETIME,
  disabled_by INTEGER REFERENCES users (id) ON DELETE SET NULL DEFERRABLE INITIALLY DEFERRED,
  created_by INTEGER REFERENCES users (id) ON DELETE SET NULL DEFERRABLE INITIALLY DEFERRED,
  updated_by INTEGER REFERENCES users (id) ON DELETE SET NULL DEFERRABLE INITIALLY DEFERRED,
  created_at DATETIME NOT NULL DEFAULT (datetime('now', 'localtime')),
  updated_at DATETIME NOT NULL DEFAULT (datetime('now', 'localtime'))
);

CREATE INDEX idx_users_role_status ON users (role, status);
CREATE INDEX idx_users_name ON users (name);
CREATE INDEX idx_users_disabled_by ON users (disabled_by);
CREATE INDEX idx_users_created_by ON users (created_by);
CREATE INDEX idx_users_updated_by ON users (updated_by);

CREATE TABLE duty_weekday_settings (
  weekday INTEGER PRIMARY KEY CHECK (weekday BETWEEN 1 AND 7),
  weekday_name TEXT NOT NULL,
  enabled INTEGER NOT NULL DEFAULT 1 CHECK (enabled IN (0, 1)),
  updated_by INTEGER REFERENCES users (id) ON DELETE SET NULL DEFERRABLE INITIALLY DEFERRED,
  created_at DATETIME NOT NULL DEFAULT (datetime('now', 'localtime')),
  updated_at DATETIME NOT NULL DEFAULT (datetime('now', 'localtime'))
);

CREATE INDEX idx_duty_weekday_enabled ON duty_weekday_settings (enabled);
CREATE INDEX idx_duty_weekday_updated_by ON duty_weekday_settings (updated_by);

INSERT INTO duty_weekday_settings (weekday, weekday_name, enabled) VALUES
  (1, '星期一', 1),
  (2, '星期二', 1),
  (3, '星期三', 1),
  (4, '星期四', 1),
  (5, '星期五', 1),
  (6, '星期六', 0),
  (7, '星期日', 0)
ON CONFLICT (weekday) DO NOTHING;

CREATE TABLE attendance_records (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id INTEGER NOT NULL REFERENCES users (id) ON DELETE RESTRICT DEFERRABLE INITIALLY DEFERRED,
  student_no_snapshot TEXT NOT NULL,
  name_snapshot TEXT NOT NULL,
  duty_date DATE NOT NULL,
  duty_weekday INTEGER NOT NULL CHECK (duty_weekday BETWEEN 1 AND 7),
  is_duty_day INTEGER NOT NULL DEFAULT 1 CHECK (is_duty_day IN (0, 1)),
  check_in_time DATETIME NOT NULL,
  check_out_time DATETIME,
  check_in_status TEXT NOT NULL DEFAULT 'PENDING' CHECK (check_in_status IN ('NOT_SUBMITTED', 'PENDING', 'APPROVED', 'REJECTED', 'AUTO_APPROVED')),
  check_out_status TEXT NOT NULL DEFAULT 'NOT_SUBMITTED' CHECK (check_out_status IN ('NOT_SUBMITTED', 'PENDING', 'APPROVED', 'REJECTED', 'AUTO_APPROVED')),
  check_in_reviewed_by INTEGER REFERENCES users (id) ON DELETE SET NULL DEFERRABLE INITIALLY DEFERRED,
  check_out_reviewed_by INTEGER REFERENCES users (id) ON DELETE SET NULL DEFERRABLE INITIALLY DEFERRED,
  check_in_reviewed_at DATETIME,
  check_out_reviewed_at DATETIME,
  check_in_reject_reason TEXT,
  check_out_reject_reason TEXT,
  duration_minutes INTEGER NOT NULL DEFAULT 0 CHECK (duration_minutes >= 0),
  valid_hours INTEGER NOT NULL DEFAULT 0 CHECK (valid_hours >= 0),
  effective_status TEXT NOT NULL DEFAULT 'PENDING' CHECK (effective_status IN ('PENDING', 'VALID', 'INVALID', 'INCOMPLETE')),
  source TEXT NOT NULL DEFAULT 'PUBLIC' CHECK (source IN ('PUBLIC', 'ADMIN_MANUAL')),
  manual_reason TEXT,
  created_by INTEGER REFERENCES users (id) ON DELETE SET NULL DEFERRABLE INITIALLY DEFERRED,
  updated_by INTEGER REFERENCES users (id) ON DELETE SET NULL DEFERRABLE INITIALLY DEFERRED,
  created_at DATETIME NOT NULL DEFAULT (datetime('now', 'localtime')),
  updated_at DATETIME NOT NULL DEFAULT (datetime('now', 'localtime'))
);

CREATE INDEX idx_attendance_user_date ON attendance_records (user_id, duty_date);
CREATE INDEX idx_attendance_duty_date ON attendance_records (duty_date);
CREATE INDEX idx_attendance_student_no_date ON attendance_records (student_no_snapshot, duty_date);
CREATE INDEX idx_attendance_status ON attendance_records (check_in_status, check_out_status, effective_status);
CREATE INDEX idx_attendance_open_record ON attendance_records (user_id, check_out_time);
CREATE INDEX idx_attendance_check_in_reviewed_by ON attendance_records (check_in_reviewed_by);
CREATE INDEX idx_attendance_check_out_reviewed_by ON attendance_records (check_out_reviewed_by);

CREATE TABLE operation_logs (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  operator_user_id INTEGER REFERENCES users (id) ON DELETE SET NULL DEFERRABLE INITIALLY DEFERRED,
  operator_student_no TEXT,
  operator_name TEXT,
  action_type TEXT NOT NULL,
  target_type TEXT NOT NULL,
  target_id INTEGER,
  before_data TEXT,
  after_data TEXT,
  reason TEXT,
  ip_address TEXT,
  user_agent TEXT,
  created_at DATETIME NOT NULL DEFAULT (datetime('now', 'localtime'))
);

CREATE INDEX idx_operation_logs_operator ON operation_logs (operator_user_id);
CREATE INDEX idx_operation_logs_action_type ON operation_logs (action_type);
CREATE INDEX idx_operation_logs_target ON operation_logs (target_type, target_id);
CREATE INDEX idx_operation_logs_created_at ON operation_logs (created_at);

CREATE TABLE app_settings (
  setting_key TEXT PRIMARY KEY,
  setting_value TEXT NOT NULL,
  description TEXT,
  updated_by INTEGER REFERENCES users (id) ON DELETE SET NULL DEFERRABLE INITIALLY DEFERRED,
  created_at DATETIME NOT NULL DEFAULT (datetime('now', 'localtime')),
  updated_at DATETIME NOT NULL DEFAULT (datetime('now', 'localtime'))
);

CREATE INDEX idx_app_settings_updated_by ON app_settings (updated_by);

CREATE TABLE training_sessions (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  title TEXT NOT NULL,
  training_date DATE NOT NULL,
  start_time TIME,
  end_time TIME,
  location TEXT,
  speaker TEXT,
  description TEXT,
  status TEXT NOT NULL DEFAULT 'PLANNED' CHECK (status IN ('PLANNED', 'COMPLETED', 'CANCELED', 'ARCHIVED')),
  created_by INTEGER REFERENCES users (id) ON DELETE SET NULL DEFERRABLE INITIALLY DEFERRED,
  updated_by INTEGER REFERENCES users (id) ON DELETE SET NULL DEFERRABLE INITIALLY DEFERRED,
  created_at DATETIME NOT NULL DEFAULT (datetime('now', 'localtime')),
  updated_at DATETIME NOT NULL DEFAULT (datetime('now', 'localtime'))
);

CREATE INDEX idx_training_sessions_date_status ON training_sessions (training_date, status);
CREATE INDEX idx_training_sessions_created_by ON training_sessions (created_by);
CREATE INDEX idx_training_sessions_updated_by ON training_sessions (updated_by);

CREATE TABLE training_participants (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  session_id INTEGER NOT NULL REFERENCES training_sessions (id) ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED,
  user_id INTEGER REFERENCES users (id) ON DELETE SET NULL DEFERRABLE INITIALLY DEFERRED,
  student_no_snapshot TEXT NOT NULL,
  name_snapshot TEXT NOT NULL,
  attendance_status TEXT NOT NULL DEFAULT 'PRESENT' CHECK (attendance_status IN ('PRESENT', 'ABSENT', 'LEAVE')),
  duration_hours NUMERIC NOT NULL DEFAULT 0 CHECK (duration_hours >= 0),
  remark TEXT,
  source TEXT NOT NULL DEFAULT 'MANUAL' CHECK (source IN ('MANUAL', 'IMPORT')),
  created_by INTEGER REFERENCES users (id) ON DELETE SET NULL DEFERRABLE INITIALLY DEFERRED,
  updated_by INTEGER REFERENCES users (id) ON DELETE SET NULL DEFERRABLE INITIALLY DEFERRED,
  created_at DATETIME NOT NULL DEFAULT (datetime('now', 'localtime')),
  updated_at DATETIME NOT NULL DEFAULT (datetime('now', 'localtime')),
  UNIQUE (session_id, student_no_snapshot)
);

CREATE INDEX idx_training_participants_user ON training_participants (user_id);
CREATE INDEX idx_training_participants_status ON training_participants (attendance_status);
CREATE INDEX idx_training_participants_created_by ON training_participants (created_by);
CREATE INDEX idx_training_participants_updated_by ON training_participants (updated_by);

CREATE TABLE duty_schedule_slots (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  weekday INTEGER NOT NULL CHECK (weekday BETWEEN 1 AND 7),
  start_time TIME,
  end_time TIME,
  title TEXT NOT NULL DEFAULT '值班',
  location TEXT,
  note TEXT,
  enabled INTEGER NOT NULL DEFAULT 1 CHECK (enabled IN (0, 1)),
  status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'ARCHIVED')),
  created_by INTEGER REFERENCES users (id) ON DELETE SET NULL DEFERRABLE INITIALLY DEFERRED,
  updated_by INTEGER REFERENCES users (id) ON DELETE SET NULL DEFERRABLE INITIALLY DEFERRED,
  created_at DATETIME NOT NULL DEFAULT (datetime('now', 'localtime')),
  updated_at DATETIME NOT NULL DEFAULT (datetime('now', 'localtime'))
);

CREATE INDEX idx_duty_schedule_weekday_enabled ON duty_schedule_slots (weekday, enabled, status);
CREATE INDEX idx_duty_schedule_created_by ON duty_schedule_slots (created_by);
CREATE INDEX idx_duty_schedule_updated_by ON duty_schedule_slots (updated_by);

CREATE TABLE duty_schedule_assignees (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  slot_id INTEGER NOT NULL REFERENCES duty_schedule_slots (id) ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED,
  user_id INTEGER REFERENCES users (id) ON DELETE SET NULL DEFERRABLE INITIALLY DEFERRED,
  student_no_snapshot TEXT,
  name_snapshot TEXT NOT NULL,
  sort_order INTEGER NOT NULL DEFAULT 0 CHECK (sort_order >= 0),
  created_at DATETIME NOT NULL DEFAULT (datetime('now', 'localtime'))
);

CREATE INDEX idx_duty_schedule_assignee_slot ON duty_schedule_assignees (slot_id, sort_order);
CREATE INDEX idx_duty_schedule_assignee_user ON duty_schedule_assignees (user_id);

CREATE TABLE repair_cases (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  case_no TEXT NOT NULL UNIQUE,
  agreement_type TEXT NOT NULL DEFAULT 'PERSONAL_DEVICE' CHECK (agreement_type IN ('PERSONAL_DEVICE', 'PUBLIC_DEVICE')),
  owner_name TEXT NOT NULL,
  owner_phone TEXT,
  owner_org TEXT,
  device_type TEXT NOT NULL,
  device_brand TEXT,
  device_model TEXT,
  device_serial TEXT,
  accessories TEXT,
  fault_description TEXT NOT NULL,
  service_description TEXT,
  data_backup_confirmed INTEGER NOT NULL DEFAULT 0 CHECK (data_backup_confirmed IN (0, 1)),
  risk_acknowledged INTEGER NOT NULL DEFAULT 1 CHECK (risk_acknowledged IN (0, 1)),
  privacy_acknowledged INTEGER NOT NULL DEFAULT 1 CHECK (privacy_acknowledged IN (0, 1)),
  status TEXT NOT NULL DEFAULT 'REPAIRING' CHECK (status IN ('REPAIRING', 'COMPLETED', 'CANCELED')),
  received_at DATETIME NOT NULL DEFAULT (datetime('now', 'localtime')),
  completed_at DATETIME,
  handler_user_id INTEGER REFERENCES users (id) ON DELETE SET NULL DEFERRABLE INITIALLY DEFERRED,
  handler_name_snapshot TEXT,
  remark TEXT,
  created_by INTEGER REFERENCES users (id) ON DELETE SET NULL DEFERRABLE INITIALLY DEFERRED,
  updated_by INTEGER REFERENCES users (id) ON DELETE SET NULL DEFERRABLE INITIALLY DEFERRED,
  created_at DATETIME NOT NULL DEFAULT (datetime('now', 'localtime')),
  updated_at DATETIME NOT NULL DEFAULT (datetime('now', 'localtime'))
);

CREATE INDEX idx_repair_cases_status_received ON repair_cases (status, received_at);
CREATE INDEX idx_repair_cases_owner ON repair_cases (owner_name, owner_phone);
CREATE INDEX idx_repair_cases_handler ON repair_cases (handler_user_id);
CREATE INDEX idx_repair_cases_created_by ON repair_cases (created_by);
CREATE INDEX idx_repair_cases_updated_by ON repair_cases (updated_by);
