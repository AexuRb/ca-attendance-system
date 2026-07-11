ALTER TABLE attendance_records
ADD COLUMN within_duty_period INTEGER NOT NULL DEFAULT 1
CHECK (within_duty_period IN (0, 1));
