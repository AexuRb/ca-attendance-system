UPDATE training_participants
SET attendance_status = 'PRESENT'
WHERE attendance_status <> 'PRESENT';

DROP INDEX IF EXISTS idx_training_participants_status;
