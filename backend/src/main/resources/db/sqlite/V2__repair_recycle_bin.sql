ALTER TABLE repair_cases ADD COLUMN deleted_at DATETIME;
ALTER TABLE repair_cases ADD COLUMN deleted_by INTEGER REFERENCES users (id) ON DELETE SET NULL DEFERRABLE INITIALLY DEFERRED;

CREATE INDEX idx_repair_cases_deleted_at ON repair_cases (deleted_at);
CREATE INDEX idx_repair_cases_deleted_by ON repair_cases (deleted_by);
