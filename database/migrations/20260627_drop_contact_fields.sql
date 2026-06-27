USE ca_attendance;

SET @schema_name = DATABASE();

SET @drop_contact_fields_sql = (
  SELECT IF(
    COUNT(*) = 0,
    'SELECT ''users contact fields already removed'' AS message',
    CONCAT(
      'ALTER TABLE users ',
      GROUP_CONCAT(
        CONCAT('DROP COLUMN ', COLUMN_NAME)
        ORDER BY FIELD(COLUMN_NAME, 'phone', 'major', 'qq')
        SEPARATOR ', '
      )
    )
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @schema_name
    AND TABLE_NAME = 'users'
    AND COLUMN_NAME IN ('phone', 'major', 'qq')
);

PREPARE drop_contact_fields_stmt FROM @drop_contact_fields_sql;
EXECUTE drop_contact_fields_stmt;
DEALLOCATE PREPARE drop_contact_fields_stmt;
