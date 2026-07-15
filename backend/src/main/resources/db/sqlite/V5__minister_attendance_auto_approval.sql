UPDATE attendance_records
SET check_in_status = CASE WHEN check_in_status = 'PENDING' THEN 'AUTO_APPROVED' ELSE check_in_status END,
    check_out_status = CASE WHEN check_out_status = 'PENDING' THEN 'AUTO_APPROVED' ELSE check_out_status END,
    updated_at = datetime('now', 'localtime')
WHERE user_id IN (SELECT id FROM users WHERE role = 'MINISTER')
  AND (check_in_status = 'PENDING' OR check_out_status = 'PENDING');

UPDATE public_attendance_submissions
SET review_status = 'AUTO_APPROVED'
WHERE review_status = 'PENDING'
  AND record_id IN (
    SELECT attendance_records.id
    FROM attendance_records
    JOIN users ON users.id = attendance_records.user_id
    WHERE users.role = 'MINISTER'
  );

UPDATE attendance_records
SET duration_minutes = CASE
      WHEN check_in_status = 'REJECTED' OR check_out_status = 'REJECTED'
        OR is_duty_day = 0 OR within_duty_period = 0
        OR check_out_time IS NULL OR check_out_status = 'NOT_SUBMITTED'
        OR check_in_status NOT IN ('APPROVED', 'AUTO_APPROVED')
        OR check_out_status NOT IN ('APPROVED', 'AUTO_APPROVED')
        OR strftime('%s', check_out_time) <= strftime('%s', check_in_time)
      THEN 0
      ELSE CAST((strftime('%s', check_out_time) - strftime('%s', check_in_time)) / 60 AS INTEGER)
    END,
    valid_hours = CASE
      WHEN check_in_status IN ('APPROVED', 'AUTO_APPROVED')
        AND check_out_status IN ('APPROVED', 'AUTO_APPROVED')
        AND is_duty_day = 1 AND within_duty_period = 1
        AND check_out_time IS NOT NULL
        AND strftime('%s', check_out_time) > strftime('%s', check_in_time)
      THEN CAST(((strftime('%s', check_out_time) - strftime('%s', check_in_time)) / 60 + 30) / 60 AS INTEGER)
      ELSE 0
    END,
    effective_status = CASE
      WHEN check_in_status = 'REJECTED' OR check_out_status = 'REJECTED'
        OR is_duty_day = 0 OR within_duty_period = 0 THEN 'INVALID'
      WHEN check_out_time IS NULL OR check_out_status = 'NOT_SUBMITTED' THEN 'INCOMPLETE'
      WHEN check_in_status IN ('APPROVED', 'AUTO_APPROVED')
        AND check_out_status IN ('APPROVED', 'AUTO_APPROVED')
        AND strftime('%s', check_out_time) > strftime('%s', check_in_time) THEN 'VALID'
      WHEN strftime('%s', check_out_time) <= strftime('%s', check_in_time) THEN 'INVALID'
      ELSE 'PENDING'
    END,
    updated_at = datetime('now', 'localtime')
WHERE user_id IN (SELECT id FROM users WHERE role = 'MINISTER');
