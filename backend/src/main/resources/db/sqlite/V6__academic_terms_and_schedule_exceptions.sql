CREATE TABLE academic_terms (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  term_code TEXT NOT NULL UNIQUE,
  term_name TEXT NOT NULL,
  start_date DATE NOT NULL,
  end_date DATE NOT NULL,
  status TEXT NOT NULL DEFAULT 'DRAFT'
    CHECK (status IN ('DRAFT', 'ACTIVE', 'SETTLING', 'SEALED')),
  legacy INTEGER NOT NULL DEFAULT 0 CHECK (legacy IN (0, 1)),
  settling_started_at DATETIME,
  settling_started_by INTEGER REFERENCES users (id) ON DELETE SET NULL DEFERRABLE INITIALLY DEFERRED,
  sealed_at DATETIME,
  sealed_by INTEGER REFERENCES users (id) ON DELETE SET NULL DEFERRABLE INITIALLY DEFERRED,
  reopened_at DATETIME,
  reopened_by INTEGER REFERENCES users (id) ON DELETE SET NULL DEFERRABLE INITIALLY DEFERRED,
  reopen_reason TEXT,
  created_by INTEGER REFERENCES users (id) ON DELETE SET NULL DEFERRABLE INITIALLY DEFERRED,
  updated_by INTEGER REFERENCES users (id) ON DELETE SET NULL DEFERRABLE INITIALLY DEFERRED,
  created_at DATETIME NOT NULL DEFAULT (datetime('now', 'localtime')),
  updated_at DATETIME NOT NULL DEFAULT (datetime('now', 'localtime')),
  CHECK (date(start_date) <= date(end_date))
);

CREATE INDEX idx_academic_terms_dates ON academic_terms (start_date, end_date);
CREATE INDEX idx_academic_terms_status ON academic_terms (status);
CREATE UNIQUE INDEX idx_academic_terms_single_active
ON academic_terms ((1)) WHERE status = 'ACTIVE';

WITH business_dates(value) AS (
  SELECT duty_date FROM attendance_records
  UNION ALL
  SELECT training_date FROM training_sessions
  UNION ALL
  SELECT date(received_at) FROM repair_cases
)
INSERT INTO academic_terms (
  term_code, term_name, start_date, end_date, status, legacy,
  settling_started_at, created_at, updated_at
)
SELECT
  '__legacy__',
  '历史学期',
  COALESCE(MIN(date(value)), date('now', 'localtime')),
  COALESCE(MAX(date(value)), date('now', 'localtime')),
  'SETTLING',
  1,
  datetime('now', 'localtime'),
  datetime('now', 'localtime'),
  datetime('now', 'localtime')
FROM business_dates
HAVING COUNT(value) > 0;

ALTER TABLE attendance_records ADD COLUMN term_id INTEGER
  REFERENCES academic_terms (id) ON DELETE RESTRICT DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE training_sessions ADD COLUMN term_id INTEGER
  REFERENCES academic_terms (id) ON DELETE RESTRICT DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE duty_schedule_slots ADD COLUMN term_id INTEGER
  REFERENCES academic_terms (id) ON DELETE RESTRICT DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE repair_cases ADD COLUMN term_id INTEGER
  REFERENCES academic_terms (id) ON DELETE RESTRICT DEFERRABLE INITIALLY DEFERRED;

UPDATE attendance_records
SET term_id = (SELECT id FROM academic_terms WHERE term_code = '__legacy__')
WHERE term_id IS NULL;

UPDATE training_sessions
SET term_id = (SELECT id FROM academic_terms WHERE term_code = '__legacy__')
WHERE term_id IS NULL;

UPDATE duty_schedule_slots
SET term_id = (SELECT id FROM academic_terms WHERE term_code = '__legacy__')
WHERE term_id IS NULL;

UPDATE repair_cases
SET term_id = (SELECT id FROM academic_terms WHERE term_code = '__legacy__')
WHERE term_id IS NULL;

CREATE INDEX idx_attendance_term_date ON attendance_records (term_id, duty_date);
CREATE INDEX idx_training_term_date ON training_sessions (term_id, training_date);
CREATE INDEX idx_schedule_term_weekday ON duty_schedule_slots (term_id, weekday, status, enabled);
CREATE INDEX idx_repair_term_status ON repair_cases (term_id, status, deleted_at);

CREATE TABLE term_settlements (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  term_id INTEGER NOT NULL REFERENCES academic_terms (id) ON DELETE RESTRICT DEFERRABLE INITIALLY DEFERRED,
  version INTEGER NOT NULL CHECK (version > 0),
  status TEXT NOT NULL DEFAULT 'PREVIEW' CHECK (status IN ('PREVIEW', 'SEALED', 'SUPERSEDED')),
  summary_json TEXT NOT NULL,
  source_digest TEXT NOT NULL,
  prepared_at DATETIME NOT NULL DEFAULT (datetime('now', 'localtime')),
  prepared_by INTEGER REFERENCES users (id) ON DELETE SET NULL DEFERRABLE INITIALLY DEFERRED,
  sealed_at DATETIME,
  sealed_by INTEGER REFERENCES users (id) ON DELETE SET NULL DEFERRABLE INITIALLY DEFERRED,
  superseded_at DATETIME,
  reopen_reason TEXT,
  UNIQUE (term_id, version)
);

CREATE INDEX idx_term_settlements_term ON term_settlements (term_id, version DESC);

CREATE TABLE term_member_settlements (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  settlement_id INTEGER NOT NULL REFERENCES term_settlements (id) ON DELETE RESTRICT DEFERRABLE INITIALLY DEFERRED,
  user_id INTEGER REFERENCES users (id) ON DELETE SET NULL DEFERRABLE INITIALLY DEFERRED,
  student_no_snapshot TEXT NOT NULL,
  name_snapshot TEXT NOT NULL,
  role_snapshot TEXT NOT NULL,
  attendance_count INTEGER NOT NULL DEFAULT 0 CHECK (attendance_count >= 0),
  attendance_minutes INTEGER NOT NULL DEFAULT 0 CHECK (attendance_minutes >= 0),
  training_count INTEGER NOT NULL DEFAULT 0 CHECK (training_count >= 0),
  training_minutes INTEGER NOT NULL DEFAULT 0 CHECK (training_minutes >= 0),
  total_minutes INTEGER NOT NULL DEFAULT 0 CHECK (total_minutes >= 0),
  UNIQUE (settlement_id, student_no_snapshot)
);

CREATE INDEX idx_term_member_settlement ON term_member_settlements (settlement_id, total_minutes DESC);

CREATE TABLE duty_schedule_exceptions (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  term_id INTEGER NOT NULL REFERENCES academic_terms (id) ON DELETE RESTRICT DEFERRABLE INITIALLY DEFERRED,
  exception_date DATE NOT NULL,
  exception_type TEXT NOT NULL CHECK (exception_type IN (
    'DAY_CANCELLED', 'PERIOD_CANCELLED', 'TEMPORARY_ADDITION', 'ASSIGNEE_OVERRIDE'
  )),
  source_slot_id INTEGER REFERENCES duty_schedule_slots (id) ON DELETE SET NULL DEFERRABLE INITIALLY DEFERRED,
  start_time TIME,
  end_time TIME,
  title TEXT,
  location TEXT,
  reason TEXT NOT NULL,
  created_by INTEGER REFERENCES users (id) ON DELETE SET NULL DEFERRABLE INITIALLY DEFERRED,
  updated_by INTEGER REFERENCES users (id) ON DELETE SET NULL DEFERRABLE INITIALLY DEFERRED,
  created_at DATETIME NOT NULL DEFAULT (datetime('now', 'localtime')),
  updated_at DATETIME NOT NULL DEFAULT (datetime('now', 'localtime')),
  CHECK (end_time IS NULL OR start_time IS NULL OR time(start_time) < time(end_time))
);

CREATE INDEX idx_schedule_exceptions_date ON duty_schedule_exceptions (term_id, exception_date, exception_type);

CREATE TABLE duty_schedule_exception_assignees (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  exception_id INTEGER NOT NULL REFERENCES duty_schedule_exceptions (id) ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED,
  user_id INTEGER REFERENCES users (id) ON DELETE SET NULL DEFERRABLE INITIALLY DEFERRED,
  student_no_snapshot TEXT,
  name_snapshot TEXT NOT NULL,
  sort_order INTEGER NOT NULL DEFAULT 0 CHECK (sort_order >= 0)
);

CREATE INDEX idx_schedule_exception_assignees
ON duty_schedule_exception_assignees (exception_id, sort_order, id);

CREATE TABLE duty_shift_reassignments (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  term_id INTEGER NOT NULL REFERENCES academic_terms (id) ON DELETE RESTRICT DEFERRABLE INITIALLY DEFERRED,
  duty_date DATE NOT NULL,
  source_slot_id INTEGER REFERENCES duty_schedule_slots (id) ON DELETE SET NULL DEFERRABLE INITIALLY DEFERRED,
  start_time TIME NOT NULL,
  end_time TIME NOT NULL,
  original_user_id INTEGER REFERENCES users (id) ON DELETE SET NULL DEFERRABLE INITIALLY DEFERRED,
  original_student_no_snapshot TEXT,
  original_name_snapshot TEXT NOT NULL,
  replacement_user_id INTEGER REFERENCES users (id) ON DELETE SET NULL DEFERRABLE INITIALLY DEFERRED,
  replacement_student_no_snapshot TEXT,
  replacement_name_snapshot TEXT NOT NULL,
  reason TEXT NOT NULL,
  created_by INTEGER REFERENCES users (id) ON DELETE SET NULL DEFERRABLE INITIALLY DEFERRED,
  updated_by INTEGER REFERENCES users (id) ON DELETE SET NULL DEFERRABLE INITIALLY DEFERRED,
  created_at DATETIME NOT NULL DEFAULT (datetime('now', 'localtime')),
  updated_at DATETIME NOT NULL DEFAULT (datetime('now', 'localtime')),
  CHECK (time(start_time) < time(end_time))
);

CREATE INDEX idx_shift_reassignments_date
ON duty_shift_reassignments (term_id, duty_date, start_time, end_time);
