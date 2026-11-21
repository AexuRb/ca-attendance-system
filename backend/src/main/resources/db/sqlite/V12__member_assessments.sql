-- Double semicolons separate top-level statements and leave trigger bodies intact.
ALTER TABLE users ADD COLUMN disassembly_assessment TEXT NOT NULL DEFAULT 'NOT_PASSED'
    CHECK (disassembly_assessment IN ('PASSED', 'NOT_PASSED'));;
ALTER TABLE users ADD COLUMN os_reinstall_assessment TEXT NOT NULL DEFAULT 'NOT_PASSED'
    CHECK (os_reinstall_assessment IN ('PASSED', 'NOT_PASSED'));;

UPDATE users
SET disassembly_assessment = 'PASSED', os_reinstall_assessment = 'PASSED'
WHERE role IN ('MINISTER', 'PRESIDENT', 'ADMIN');;

CREATE TRIGGER users_pass_assessments_on_insert
AFTER INSERT ON users
WHEN NEW.role IN ('MINISTER', 'PRESIDENT', 'ADMIN')
    AND (NEW.disassembly_assessment <> 'PASSED' OR NEW.os_reinstall_assessment <> 'PASSED')
BEGIN
    UPDATE users SET disassembly_assessment = 'PASSED', os_reinstall_assessment = 'PASSED'
    WHERE id = NEW.id;
END;;

CREATE TRIGGER users_pass_assessments_on_appointment
AFTER UPDATE OF role ON users
WHEN NEW.role IN ('MINISTER', 'PRESIDENT', 'ADMIN')
    AND (NEW.disassembly_assessment <> 'PASSED' OR NEW.os_reinstall_assessment <> 'PASSED')
BEGIN
    UPDATE users SET disassembly_assessment = 'PASSED', os_reinstall_assessment = 'PASSED'
    WHERE id = NEW.id;
END;;

CREATE TRIGGER users_lock_assessments_for_officers
BEFORE UPDATE OF disassembly_assessment, os_reinstall_assessment ON users
WHEN NEW.role IN ('MINISTER', 'PRESIDENT', 'ADMIN')
    AND (NEW.disassembly_assessment <> 'PASSED' OR NEW.os_reinstall_assessment <> 'PASSED')
BEGIN
    SELECT RAISE(ABORT, 'Officer assessments must remain passed');
END;;
