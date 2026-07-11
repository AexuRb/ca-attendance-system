CREATE TABLE public_attendance_submissions (
  request_id TEXT PRIMARY KEY,
  student_no TEXT NOT NULL,
  record_id INTEGER NOT NULL,
  action TEXT NOT NULL CHECK (action IN ('CHECK_IN', 'CHECK_OUT')),
  name TEXT NOT NULL,
  submitted_at DATETIME NOT NULL,
  review_status TEXT NOT NULL,
  message TEXT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT (datetime('now', 'localtime'))
);

CREATE INDEX idx_public_attendance_submissions_created_at
ON public_attendance_submissions (created_at);
