ALTER TABLE attendance_records
ADD COLUMN require_duty_day INTEGER NOT NULL DEFAULT 0
CHECK (require_duty_day IN (0, 1));

ALTER TABLE attendance_records
ADD COLUMN require_duty_period INTEGER NOT NULL DEFAULT 0
CHECK (require_duty_period IN (0, 1));

UPDATE attendance_records
SET duration_minutes = CASE
      WHEN check_in_status = 'REJECTED' OR check_out_status = 'REJECTED'
        OR check_out_time IS NULL OR check_out_status = 'NOT_SUBMITTED'
        OR check_in_status NOT IN ('APPROVED', 'AUTO_APPROVED')
        OR check_out_status NOT IN ('APPROVED', 'AUTO_APPROVED')
        OR check_out_time <= check_in_time
      THEN 0
      ELSE CAST((strftime('%s', check_out_time) - strftime('%s', check_in_time)) / 60 AS INTEGER)
    END,
    valid_hours = CASE
      WHEN check_in_status IN ('APPROVED', 'AUTO_APPROVED')
        AND check_out_status IN ('APPROVED', 'AUTO_APPROVED')
        AND check_out_time IS NOT NULL
        AND check_out_time > check_in_time
      THEN CAST(((strftime('%s', check_out_time) - strftime('%s', check_in_time)) / 60 + 30) / 60 AS INTEGER)
      ELSE 0
    END,
    effective_status = CASE
      WHEN check_in_status = 'REJECTED' OR check_out_status = 'REJECTED' THEN 'INVALID'
      WHEN check_out_time IS NULL OR check_out_status = 'NOT_SUBMITTED' THEN 'INCOMPLETE'
      WHEN check_in_status NOT IN ('APPROVED', 'AUTO_APPROVED')
        OR check_out_status NOT IN ('APPROVED', 'AUTO_APPROVED') THEN 'PENDING'
      WHEN check_out_time <= check_in_time THEN 'INVALID'
      ELSE 'VALID'
    END,
    updated_at = datetime('now', 'localtime');
