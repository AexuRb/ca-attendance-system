CREATE TABLE IF NOT EXISTS repair_case_sequences (
  sequence_date TEXT PRIMARY KEY,
  last_value INTEGER NOT NULL CHECK (last_value >= 0),
  updated_at DATETIME NOT NULL DEFAULT (datetime('now', 'localtime'))
);

INSERT INTO repair_case_sequences (sequence_date, last_value, updated_at)
SELECT
  substr(case_no, 5, 8),
  MAX(CAST(substr(case_no, 14) AS INTEGER)),
  datetime('now', 'localtime')
FROM repair_cases
WHERE case_no GLOB 'JXWX[0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9]-[0-9]*'
GROUP BY substr(case_no, 5, 8)
ON CONFLICT(sequence_date) DO UPDATE SET
  last_value = MAX(repair_case_sequences.last_value, excluded.last_value),
  updated_at = datetime('now', 'localtime');
